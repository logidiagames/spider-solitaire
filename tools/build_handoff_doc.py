from __future__ import annotations

import hashlib
import os
from pathlib import Path
from typing import Iterable, Sequence

from docx import Document
from docx.enum.section import WD_ORIENT
from docx.enum.style import WD_STYLE_TYPE
from docx.enum.table import WD_ALIGN_VERTICAL, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "FreeCell-Technical-Handoff-v0.4.4.docx"
ZIP_PATH = ROOT / "FreeCell-Complete-Source-v0.4.4.zip"

GREEN = "165B36"
DARK_GREEN = "0B3B25"
PALE_GREEN = "EAF3ED"
PALE_GRAY = "F4F5F4"
MID_GRAY = "D9D9D9"
WOOD = "6B4329"
WHITE = "FFFFFF"
BLACK = "000000"
RED = "A33B34"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def set_cell_shading(cell, fill: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_margins(cell, top=100, start=110, bottom=100, end=110) -> None:
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for margin, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tc_mar.find(qn(f"w:{margin}"))
        if node is None:
            node = OxmlElement(f"w:{margin}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_table_borders(table, color=MID_GRAY, size="6") -> None:
    table_pr = table._tbl.tblPr
    borders = table_pr.find(qn("w:tblBorders"))
    if borders is None:
        borders = OxmlElement("w:tblBorders")
        table_pr.append(borders)
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        tag = borders.find(qn(f"w:{edge}"))
        if tag is None:
            tag = OxmlElement(f"w:{edge}")
            borders.append(tag)
        tag.set(qn("w:val"), "single")
        tag.set(qn("w:sz"), size)
        tag.set(qn("w:space"), "0")
        tag.set(qn("w:color"), color)


def set_repeat_table_header(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    tbl_header = OxmlElement("w:tblHeader")
    tbl_header.set(qn("w:val"), "true")
    tr_pr.append(tbl_header)


def set_repeat_header(paragraph) -> None:
    p_pr = paragraph._p.get_or_add_pPr()
    keep = OxmlElement("w:keepNext")
    p_pr.append(keep)


def add_page_number(paragraph) -> None:
    paragraph.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    run = paragraph.add_run()
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = "PAGE"
    fld_separate = OxmlElement("w:fldChar")
    fld_separate.set(qn("w:fldCharType"), "separate")
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    run._r.extend([fld_begin, instr, fld_separate, fld_end])


def add_table(doc: Document, headers: Sequence[str], rows: Iterable[Sequence[str]], widths=None):
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    set_table_borders(table)
    header = table.rows[0]
    set_repeat_table_header(header)
    for index, text in enumerate(headers):
        cell = header.cells[index]
        set_cell_shading(cell, DARK_GREEN)
        set_cell_margins(cell)
        cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        run = p.add_run(str(text))
        run.bold = True
        run.font.color.rgb = RGBColor(255, 255, 255)
        run.font.size = Pt(8.5)
    for row_index, values in enumerate(rows):
        row = table.add_row()
        for index, value in enumerate(values):
            cell = row.cells[index]
            set_cell_margins(cell)
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            if row_index % 2:
                set_cell_shading(cell, PALE_GREEN)
            p = cell.paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            run = p.add_run(str(value))
            run.font.size = Pt(8.3)
    if widths:
        for row in table.rows:
            for index, width in enumerate(widths):
                row.cells[index].width = Inches(width)
    after = doc.add_paragraph()
    after.paragraph_format.space_after = Pt(2)
    return table


def add_bullets(doc: Document, items: Sequence[str], level: int = 0) -> None:
    for item in items:
        paragraph = doc.add_paragraph(style="List Bullet" if level == 0 else "List Bullet 2")
        paragraph.add_run(item)


def add_numbered(doc: Document, items: Sequence[str]) -> None:
    for item in items:
        paragraph = doc.add_paragraph(style="List Number")
        paragraph.add_run(item)


def add_code_block(doc: Document, text: str, font_size: float = 7.2) -> None:
    # Split very large files into manageable Word paragraphs while preserving the exact text.
    lines = text.splitlines(keepends=True)
    if not lines:
        lines = [""]
    for start in range(0, len(lines), 110):
        block = "".join(lines[start:start + 110])
        paragraph = doc.add_paragraph(style="Code")
        run = paragraph.add_run(block)
        run.font.size = Pt(font_size)


def add_heading(doc: Document, text: str, level: int = 1):
    paragraph = doc.add_heading(text, level=level)
    set_repeat_header(paragraph)
    return paragraph


def add_para(doc: Document, text: str, bold_lead: str | None = None) -> None:
    paragraph = doc.add_paragraph()
    if bold_lead and text.startswith(bold_lead):
        paragraph.add_run(bold_lead).bold = True
        paragraph.add_run(text[len(bold_lead):])
    else:
        paragraph.add_run(text)


def all_source_files() -> list[Path]:
    fixed = [
        ROOT / ".gitignore",
        ROOT / "README.md",
        ROOT / "build.gradle.kts",
        ROOT / "settings.gradle.kts",
        ROOT / "gradle.properties",
        ROOT / "gradlew",
        ROOT / "gradlew.bat",
        ROOT / "gradle/wrapper/gradle-wrapper.properties",
        ROOT / "share/index.html",
        ROOT / "signing/README.md",
    ]
    discovered = []
    for base in (ROOT / "app", ROOT / "docs", ROOT / "tools"):
        for path in base.rglob("*"):
            if not path.is_file() or "build" in path.parts:
                continue
            if path.name in {"google-services.json", "local.properties"}:
                continue
            if path.suffix.lower() in {".java", ".kt", ".kts", ".html", ".xml", ".json", ".md", ".mjs", ".py", ".pro", ".properties"}:
                discovered.append(path)
    unique = {path.resolve() for path in fixed + discovered if path.exists()}
    return sorted(unique, key=lambda p: str(p.relative_to(ROOT)).lower())


def binary_resource_files() -> list[Path]:
    roots = [
        ROOT / "gradle/wrapper/gradle-wrapper.jar",
        ROOT / "screenshots",
        ROOT / "play-store-screenshots-final",
        ROOT / "play-store-screenshots-no-statusbar",
        ROOT / "play-store-screenshots-preview",
    ]
    values: list[Path] = []
    for item in roots:
        if item.is_file():
            values.append(item)
        elif item.is_dir():
            values.extend(path for path in item.rglob("*") if path.is_file())
    return sorted(values, key=lambda p: str(p.relative_to(ROOT)).lower())


def configure_document() -> Document:
    doc = Document()
    section = doc.sections[0]
    section.page_width = Inches(8.5)
    section.page_height = Inches(11)
    section.top_margin = Inches(0.7)
    section.bottom_margin = Inches(0.65)
    section.left_margin = Inches(0.75)
    section.right_margin = Inches(0.75)

    styles = doc.styles
    normal = styles["Normal"]
    normal.font.name = "Aptos"
    normal.font.size = Pt(10.5)
    normal.font.color.rgb = RGBColor(0, 0, 0)
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.line_spacing = 1.08

    title = styles["Title"]
    title.font.name = "Aptos Display"
    title.font.size = Pt(30)
    title.font.bold = True
    title.font.color.rgb = RGBColor(0, 0, 0)
    title.paragraph_format.space_after = Pt(8)

    for level, size in ((1, 18), (2, 14), (3, 11.5)):
        style = styles[f"Heading {level}"]
        style.font.name = "Aptos Display"
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = RGBColor(0, 0, 0)
        style.paragraph_format.space_before = Pt(12 if level == 1 else 9)
        style.paragraph_format.space_after = Pt(5)
        style.paragraph_format.keep_with_next = True

    if "Code" not in styles:
        code = styles.add_style("Code", WD_STYLE_TYPE.PARAGRAPH)
    else:
        code = styles["Code"]
    code.font.name = "Consolas"
    code.font.size = Pt(7.2)
    code.font.color.rgb = RGBColor(25, 25, 25)
    code.paragraph_format.left_indent = Inches(0.12)
    code.paragraph_format.right_indent = Inches(0.05)
    code.paragraph_format.space_before = Pt(2)
    code.paragraph_format.space_after = Pt(2)
    code.paragraph_format.line_spacing_rule = WD_LINE_SPACING.SINGLE
    p_pr = code.element.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), PALE_GRAY)
    p_pr.append(shd)

    header = section.header.paragraphs[0]
    header.text = "FreeCell Technical Handoff"
    header.alignment = WD_ALIGN_PARAGRAPH.LEFT
    header.runs[0].font.name = "Aptos"
    header.runs[0].font.size = Pt(8.5)
    header.runs[0].font.color.rgb = RGBColor(65, 65, 65)
    footer = section.footer.paragraphs[0]
    footer.add_run("Nexus Play Labs FreeCell  •  ")
    add_page_number(footer)
    for run in footer.runs:
        run.font.name = "Aptos"
        run.font.size = Pt(8)
        run.font.color.rgb = RGBColor(80, 80, 80)
    return doc


def build_document() -> None:
    doc = configure_document()

    title = doc.add_paragraph(style="Title")
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    title.add_run("FreeCell Complete Technical Handoff")
    subtitle = doc.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    subtitle_run = subtitle.add_run("Product specification architecture rules configuration and complete source listing")
    subtitle_run.bold = True
    subtitle_run.font.size = Pt(13)
    subtitle_run.font.color.rgb = RGBColor(60, 60, 60)
    meta = doc.add_paragraph()
    meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
    meta.add_run("Version 0.4.4  •  Package com.nexusplaylabss.freecell  •  22 October 2026")
    meta.runs[0].font.size = Pt(10)
    meta.runs[0].font.color.rgb = RGBColor(85, 85, 85)

    cover_image = ROOT / "screenshots/01-game-board.png"
    if cover_image.exists():
        picture = doc.add_picture(str(cover_image), width=Inches(2.72))
        picture.alignment = WD_ALIGN_PARAGRAPH.CENTER
    purpose = doc.add_paragraph()
    purpose.alignment = WD_ALIGN_PARAGRAPH.CENTER
    purpose.add_run(
        "This document is the transfer specification for the current Android prototype. "
        "It explains the implemented product, identifies the active and legacy code paths, "
        "records every operational rule and configuration, and appends every text source file."
    )
    doc.add_page_break()

    add_heading(doc, "Document Scope", 1)
    add_para(doc, "The authoritative transfer consists of this Word document and the accompanying source archive. The archive preserves the exact buildable project structure. The source appendix in this document is intended for review and AI ingestion; the archive remains the copy and build source of truth.")
    zip_hash = sha256(ZIP_PATH) if ZIP_PATH.exists() else "Archive not present when document was generated"
    add_table(doc, ["Deliverable", "Purpose", "SHA 256"], [
        (OUTPUT.name, "Detailed specification and complete text source listing", "Calculated after generation"),
        (ZIP_PATH.name, "Complete source package without private signing material or generated builds", zip_hash),
    ], widths=[2.15, 2.65, 2.2])
    add_para(doc, "Excluded secrets and machine specific files. The export deliberately omits the upload keystore, passwords, local.properties, google-services.json, APK files, AAB files, Gradle build folders and local caches. These items either contain private credentials, are specific to one computer, or are generated outputs.", "Excluded secrets and machine specific files.")

    add_heading(doc, "Contents", 1)
    add_numbered(doc, [
        "Current product and source of truth",
        "Runtime architecture and Android integration",
        "Game model and FreeCell rules",
        "Deal modes progression scoring and persistence",
        "Interface settings themes languages tutorial and celebration",
        "Remote Config advertising and analytics",
        "Build release testing and migration requirements",
        "Known gaps and production work",
        "Repository manifest and complete source listing",
    ])

    add_heading(doc, "Current Product and Source of Truth", 1)
    add_para(doc, "The current application is a hybrid Android app. MainActivity creates a native WebView and loads app/src/main/assets/freecell.html into it. The HTML file contains the active board renderer, game rules, input handling, menus, tutorial, animations, sound synthesis, themes, translations, progression and persistence model. Java supplies the Android lifecycle, protected bridge methods, vibration, orientation, external links, Firebase Analytics, Firebase Remote Config and Google Mobile Ads.")
    add_para(doc, "The Java classes under game and ui are an earlier native Canvas prototype. They compile and have unit tests, but MainActivity does not instantiate them. A migration must treat freecell.html as the current gameplay implementation. The native FreeCellGame and FreeCellView classes are useful reference code only unless the receiving platform deliberately chooses to reactivate or port them.")
    add_table(doc, ["Area", "Active implementation", "Status"], [
        ("Android entry point", "MainActivity and FreeCellApplication", "Active"),
        ("Gameplay and interface", "app/src/main/assets/freecell.html", "Active source of truth"),
        ("Ads and Remote Config", "ads package", "Active but requires production IDs and Firebase setup"),
        ("Analytics", "AnalyticsTracker plus JavaScript bridge events", "Active only when Firebase is configured"),
        ("Native game engine", "game package", "Compiled legacy reference"),
        ("Native Canvas renderer", "ui/FreeCellView.java", "Compiled legacy reference"),
        ("Native statistics store", "StatsStore.java", "Compiled but not used by current WebView flow"),
    ], widths=[1.45, 3.65, 1.9])

    add_heading(doc, "Product Feature Inventory", 2)
    add_bullets(doc, [
        "Playable FreeCell board with eight tableau columns, four free cells and four suit foundations.",
        "Tap to move, select then destination, drag and drop, and legal multi card sequence movement.",
        "Animated movement for tableau, cells and foundations with cleanup that prevents ghost trails.",
        "Invalid moves remain in place and use a horizontal shake, optional vibration and a short rejection tone.",
        "Hint, automatic hints, Undo, automatic safe foundation moves, timer, move counter and score.",
        "Random, Winnable, Numbered, Daily Challenge, Restart and developer victory test modes.",
        "First launch loading screen followed by a mandatory tutorial decision before normal gameplay.",
        "Six tutorial steps, rules, local statistics, recent game log and level progression.",
        "Six visual themes with separate card and table selection, three marked free and three marked locked but testable.",
        "English, Portuguese, Spanish, French, Italian and German interface text.",
        "Victory transition, confetti, result panel, reward display and a separate level up celebration.",
        "Firebase Remote Config controlled banner, interstitial and app open advertising lifecycle.",
        "Firebase Analytics allowlist covering onboarding, gameplay, progression, personalization, daily challenges and ads.",
    ])

    add_heading(doc, "Runtime Architecture", 1)
    add_code_block(doc, """FreeCellApplication
  └─ increments process session counter

MainActivity
  ├─ creates WebView and banner container
  ├─ injects saved state and first run tutorial flag
  ├─ exposes AndroidBridge
  ├─ owns Android lifecycle and Back handling
  ├─ starts RemoteAdConfigRepository
  ├─ owns AdsController
  └─ owns AnalyticsTracker

freecell.html
  ├─ active FreeCell state and rules
  ├─ HTML and CSS rendering
  ├─ tap drag animations sound and vibration requests
  ├─ menus tutorial themes localization and progression
  └─ state persistence and analytics calls through AndroidBridge
""", 8.2)

    add_heading(doc, "Android Host", 2)
    add_bullets(doc, [
        "Application ID and namespace are com.nexusplaylabss.freecell.",
        "MainActivity uses a full screen WebView above a native FrameLayout reserved for an anchored adaptive banner.",
        "JavaScript and DOM storage are enabled. Direct file and content access are disabled.",
        "The HTML fragment is loaded through loadDataWithBaseURL using https://freecell.local as its base origin.",
        "A generated prelude injects window.__freecellInitialState and window.__freecellTutorialRequired before the game script starts.",
        "onPause saves state and pauses the WebView. onResume resumes it. onStart and onStop drive app open ad foreground and background logic.",
        "Android 13 and later use OnBackInvokedCallback. Earlier Android versions use onBackPressed. The JavaScript layer first closes tutorials or modals; otherwise Android exits the activity.",
    ])

    add_heading(doc, "Android Bridge Contract", 2)
    add_table(doc, ["Method", "Direction", "Behavior"], [
        ("loadState", "JavaScript to Android", "Returns the persisted JSON state or an empty object"),
        ("saveState", "JavaScript to Android", "Persists JSON up to 500000 characters"),
        ("isFirstRunTutorialRequired", "JavaScript to Android", "Returns true until onboarding is completed or skipped"),
        ("onFirstRunTutorialCompleted", "JavaScript to Android", "Stores completion and unblocks ads"),
        ("onGameStarted", "JavaScript to Android", "Updates games played and evaluates banner and interstitial eligibility"),
        ("trackEvent", "JavaScript to Android", "Forwards allowlisted analytics events and parameters"),
        ("setOrientation", "JavaScript to Android", "Locks or unlocks the requested screen orientation"),
        ("openExternal", "JavaScript to Android", "Opens only the allowlisted privacy URL or support email"),
        ("vibrate", "JavaScript to Android", "Runs a clamped vibration from 1 to 200 milliseconds"),
    ], widths=[1.65, 1.35, 4.0])

    add_heading(doc, "Game Data Model", 1)
    add_table(doc, ["Value", "Representation", "Meaning"], [
        ("Card", "{s, r}", "s is 0 clubs, 1 diamonds, 2 hearts or 3 spades; r is 1 through 13"),
        ("Tableau", "Array of 8 arrays", "Cards in each column from top to exposed bottom"),
        ("Free cells", "Array of 4 nullable cards", "Each cell holds at most one card"),
        ("Foundations", "Array of 4 integers", "Highest completed rank for each suit from 0 through 13"),
        ("Location", "type pile index", "Addresses a tableau card, free cell or foundation"),
        ("History", "Snapshots", "Tableau, cells, foundations and move count before a legal move"),
        ("Initial tableau", "Deep copy", "Original deal used by Restart"),
        ("Current seed", "Unsigned 32 bit number", "Recreates numbered and shuffled deals"),
    ], widths=[1.15, 1.65, 4.2])

    add_heading(doc, "Deck and Deal Rules", 2)
    add_bullets(doc, [
        "The deck contains every combination of four suits and ranks Ace through King, for 52 unique cards.",
        "A shuffled deal uses a deterministic Microsoft style linear congruential generator. Each selected card is removed from the remaining deck, then dealt round robin by index modulo eight.",
        "The resulting column lengths are 7, 7, 7, 7, 6, 6, 6 and 6.",
        "A saved board is accepted only when it has eight tableau columns, four cells, four foundations, valid suit and rank values, no duplicates and exactly 52 represented cards including cards already in foundations.",
    ])

    add_heading(doc, "Legal Move Rules", 2)
    add_numbered(doc, [
        "A foundation cannot be used as a source in the active JavaScript implementation.",
        "A foundation accepts exactly one exposed card whose suit matches the target foundation and whose rank is one above the current foundation rank.",
        "A free cell accepts exactly one exposed card only when that cell is empty.",
        "A tableau destination accepts a card or ordered run when the moving first card is one rank below the destination top card and has the opposite color.",
        "Any card or legal run may move to an empty tableau column.",
        "A tableau run is legal only when every adjacent pair descends by one rank and alternates red and black.",
        "The maximum run length is (empty free cells plus 1) multiplied by 2 to the power of usable empty tableau columns. An empty destination column is excluded from the temporary empty column count.",
        "A move commits one snapshot, removes the source card or run, adds it to the destination and increments moves by one.",
        "Undo restores the previous snapshot but sets the displayed move count to the previous current move count plus one. Undo therefore increases moves and reduces the calculated score rather than reversing the counter.",
        "The player wins when all four foundation values equal 13.",
    ])

    add_heading(doc, "Tap Drag Hint and Automatic Move Logic", 2)
    add_bullets(doc, [
        "With Tap to move enabled, tapping a card tries a legal destination in this order: its foundation, a nonempty tableau column, an empty tableau column, then the first empty free cell when the source is an exposed tableau card.",
        "With Tap to move disabled, the first tap selects a card and the next tap chooses a destination.",
        "Dragging starts after a movement threshold of 7 CSS pixels. The implementation creates temporary visual clones, hides the source cards, animates to the target and removes every clone before committing the move.",
        "A manual board or foundation flight lasts about 480 milliseconds. Automatic foundation flights last about 520 milliseconds and start after a 55 millisecond preparation delay.",
        "The hint system prioritizes free cell to foundation, tableau to foundation, tableau to tableau and finally reports that no obvious move was found.",
        "Automatic hints wait 3.6 seconds of inactivity and reschedule when a modal is open.",
        "Automatic foundation movement waits 120 milliseconds, considers exposed tableau cards and occupied free cells, and moves the first legal card that is an Ace or Two or is no more than two ranks ahead of the lowest foundation.",
        "Reduced motion disables or shortens movement, dealing, tutorial and celebration animations according to the device preference.",
    ])

    add_heading(doc, "Counters and Win Conditions", 2)
    add_table(doc, ["Counter", "Formula or trigger"], [
        ("Moves", "Increments by one for each legal move and by one for every successful Undo"),
        ("Score", "max 0 of 10 multiplied by total foundation cards minus moves"),
        ("Time", "Increments once per second while gameplay is active and the document is visible"),
        ("Win", "All four foundations reach King"),
        ("Reward display", "Fixed visual value of 10; no currency wallet is implemented"),
        ("High score display", "Currently repeats the current game score; no persisted high score comparison"),
    ], widths=[1.65, 5.35])

    add_heading(doc, "Game Modes", 1)
    add_table(doc, ["Mode", "Implemented behavior", "Current limitation"], [
        ("Random Game", "60 percent seed from easy list, 35 percent from medium list and 5 percent time based open seed", "Seed lists are curated inputs but are not solver verified inside the app"),
        ("Winnable Game", "Builds deterministic descending same suit columns after shuffling suit order", "Guaranteed but artificial and much easier than a normal shuffled FreeCell deal"),
        ("Game by Number", "Accepts 0 through 9999999 and runs the deterministic shuffle", "The interface correctly warns that not every numbered game is solvable"),
        ("Daily Challenge", "Uses the local calendar date to derive one deal seed for the current day", "Only today is playable; streak is currently hard coded to 3 and daily completion history is not persisted"),
        ("Restart", "Restores the original tableau with empty cells and foundations", "Counts as a newly started game for ad cadence and analytics"),
        ("Developer Victory", "Creates three complete foundations plus Queen of Spades and a movable King of Spades", "Visible in the current production interface and still awards level progress"),
    ], widths=[1.2, 3.55, 2.25])

    add_heading(doc, "Seed Pools", 2)
    add_para(doc, "The active source contains an intro list plus separate easy and medium lists. The intro list is used by tutorial visuals, not as a guaranteed first five gameplay sequence. The Random Game selector applies its 60 35 5 weighting on every new random or daily seed selection as described in the source.")
    add_code_block(doc, "introDeals = [25904, 5152, 11987, 13464, 9998]\n\neasyDeals = [62090, 1529, 38168, 41426, 41993, 48995, 3250, 48800, 54477, 58573, 61556, 5300, 17871, 59641, 41293, 43306, 53488, 18427, 31316, 52255, 14624, 18150, 49676, 50203, 1081, 15140, 30584, 49487, 50534, 58441, 2514, 12411, 17772, 37509, 1150, 3225, 5213, 7807, 21657, 28856, 5877, 11863, 62487, 7018, 21900, 31185]\n\nmediumDeals = [33624, 19951, 30418, 30849, 45580, 6893, 13532, 18026, 43073, 48689, 52573, 57355, 63675, 2508, 27853, 32016, 38770, 43196, 57435, 14826, 30755, 63249, 892, 4929, 5907, 8961, 14014, 61047, 23328, 40441, 8471, 10772, 40041, 6749, 15196, 27121, 33710, 41747, 47917, 56070, 60306, 5814, 30329, 164, 7058, 49923]\n", 7.5)

    add_heading(doc, "Level Progression", 1)
    add_para(doc, "The progression requirement for level n is n wins. Level 1 requires one win, level 2 requires two wins, level 3 requires three wins and the requirement continues linearly. Every completed deal adds one win. Excess wins carry into the next level through a loop, although normal gameplay awards one at a time.")
    add_table(doc, ["From level", "Wins required", "Cumulative wins to reach next level"], [
        ("1", "1", "1"),
        ("2", "2", "3"),
        ("3", "3", "6"),
        ("4", "4", "10"),
        ("5", "5", "15"),
        ("10", "10", "55"),
    ], widths=[1.6, 1.7, 3.7])
    add_bullets(doc, [
        "Difficulty is selected by game mode and seed pool, not by player level.",
        "The New Game menu displays the current level, current wins, required wins and a progress bar.",
        "A victory shows the earned win and progression. When a level changes, Continue opens a separate ornamental level up panel before returning to New Game.",
        "The current code calls progression for all wins, including Numbered Game and the developer victory shortcut. Excluding developer progress is recommended before production.",
    ])

    add_heading(doc, "Loading Onboarding and Tutorial", 1)
    add_bullets(doc, [
        "The loading screen uses green felt, a wooden border, four animated Ace cards, suit indicators and a progress bar. It contains only the FREECELL title and no studio label.",
        "Normal animation lasts about 1.66 seconds plus a 350 millisecond exit fade. Reduced motion uses a shorter path.",
        "On the first process use for a fresh install, MainActivity marks the tutorial as required. The close button is hidden while the first run tutorial intro is open.",
        "The player may Start or Skip. Either completion path stores first_tutorial_completed and unblocks advertising, then starts a Random Game.",
        "The tutorial remains available from Settings after onboarding and logs a separate settings source.",
    ])
    add_table(doc, ["Step", "Title", "Visual action"], [
        ("1", "Main objective", "Ace of Spades moves to its foundation"),
        ("2", "Collect matching suits", "Two of Spades moves onto the Ace of Spades"),
        ("3", "Move cards", "Seven of Hearts moves onto a prepared black Eight"),
        ("4", "Move a sequence", "Queen of Hearts, Jack of Clubs and Ten of Diamonds move as a run"),
        ("5", "Use free cells", "Queen of Spades moves to an empty free cell"),
        ("6", "Complete the game", "Four Kings move one by one to foundations already showing Queens"),
    ], widths=[0.6, 1.75, 4.65])

    add_heading(doc, "Settings", 1)
    add_table(doc, ["Setting", "Default", "Implemented effect"], [
        ("Tap to move", "On", "Automatically searches legal destinations when a card is tapped"),
        ("Auto move to foundations", "On", "Schedules conservative foundation moves after legal moves"),
        ("Automatic hints", "Off", "Highlights an obvious move after 3.6 seconds"),
        ("Highlight movable cards", "On", "Displays the selected card lift and highlight"),
        ("Sound", "On", "Enables synthesized move, foundation and rejection sounds"),
        ("Vibration", "On", "Vibrates for an invalid move"),
        ("Victory celebration", "On", "Enables card flight transition and confetti"),
        ("Language", "English", "Changes labels immediately and persists selection"),
        ("Time and moves", "On", "Shows or hides time and moves in the HUD"),
        ("Left handed mode", "Off", "Reverses toolbar direction"),
        ("Lock orientation", "Off", "Requests Android orientation lock"),
        ("Free cell position", "Left", "Swaps the free cell and foundation order"),
    ], widths=[2.0, 0.85, 4.15])
    add_para(doc, "Settings also contains Level Progress, Game History, Statistics, Completed Numbered Games, Tutorial, Rules, Privacy Policy, Terms of Service and Contact Us. Quick Play remains only as an unused translation label and is not exposed as a setting.")

    add_heading(doc, "Themes", 1)
    add_table(doc, ["ID", "Display name", "Table color", "Free marker", "Behavior"], [
        ("classic", "Classic", "#0c7c3d", "Yes", "White traditional card face"),
        ("aurora", "Aurora", "#17645e", "Yes", "Soft violet card face"),
        ("sunlight", "Bordeaux", "#651d32", "Yes", "Bordeaux table and warm card face; persisted ID remains sunlight"),
        ("neon", "Neon", "#063b2b", "No", "Dark card face with bright green accent"),
        ("moonstone", "Moonstone", "#254b50", "No", "Cool gray blue card treatment"),
        ("emberwood", "Emberwood", "#4f5631", "No", "Warm tan card treatment"),
    ], widths=[0.85, 1.15, 1.1, 0.85, 3.05])
    add_para(doc, "The three locked themes remain selectable for testing. There is no purchase or entitlement check. Card and table choices are independent even though both use the same six theme IDs.")

    add_heading(doc, "Card Rendering and Motion", 1)
    add_bullets(doc, [
        "Cards are rendered from HTML and CSS. Number cards show rank and suit in the upper left plus a large center suit on exposed cards.",
        "Jack, Queen and King use original inline SVG court illustrations created in the project. Their art stays below the rank and suit corner area.",
        "Covered tableau cards suppress their large center pip or court illustration while keeping the rank and suit visible.",
        "Movement uses temporary cloned cards and a four point curved keyframe path. The source is hidden only while the clone exists, then cleanup removes the clone before the state is committed.",
        "New deal cards enter from below one by one at 34 millisecond intervals.",
        "A rejected move uses a 340 millisecond horizontal shake without a popup movement.",
    ])

    add_heading(doc, "Sound and Haptics", 2)
    add_bullets(doc, [
        "All sound is generated at runtime with the Web Audio API; the project contains no proprietary sound assets.",
        "Standard successful moves use a short 510 hertz tone.",
        "Invalid moves use a short 155 hertz tone.",
        "Card flights add a filtered noise slide.",
        "Foundation placement adds two short tones at 620 and 880 hertz.",
        "Invalid moves request a 35 millisecond Android vibration when vibration is enabled.",
    ])

    add_heading(doc, "Victory Flow", 1)
    add_numbered(doc, [
        "The final legal foundation move completes the four Kings and computes the score.",
        "When celebration is enabled, sixteen animated cards fly through the screen with sparks and a Board Cleared ornament.",
        "The transition lasts approximately 6.35 seconds before the result panel opens.",
        "Confetti falls across the screen for approximately 3.8 seconds.",
        "The result panel displays the victory ribbon, current score, repeated high score value, time, moves, level progress and a fixed reward of 10.",
        "Continue opens the level up celebration when the win crossed a level boundary; otherwise it opens New Game.",
    ])

    add_heading(doc, "Localization", 1)
    add_table(doc, ["Code", "Language", "Coverage"], [
        ("de", "German", "HUD, menus, tutorial, daily challenge and progression"),
        ("en", "English", "HUD, menus, tutorial, daily challenge and progression"),
        ("es", "Spanish", "HUD, menus, tutorial, daily challenge and progression"),
        ("fr", "French", "HUD, menus, tutorial, daily challenge and progression"),
        ("it", "Italian", "HUD, menus, tutorial, daily challenge and progression"),
        ("pt", "Portuguese", "HUD, menus, tutorial, daily challenge and progression"),
    ], widths=[0.75, 1.25, 5.0])
    add_para(doc, "Languages appear alphabetically by their display names in the selector. The WebView translation dictionaries control almost all current interface labels. Native values resources provide only a small fallback set in English and Portuguese and do not drive the active WebView interface.")

    add_heading(doc, "Persistence", 1)
    add_table(doc, ["Store", "Key or file", "Data"], [
        ("SharedPreferences", "freecell_web_state saved_state", "Versioned JSON game state, options, themes, language, level, statistics and recent log"),
        ("SharedPreferences", "freecell_web_state first_tutorial_completed", "First run onboarding completion"),
        ("SharedPreferences", "freecell_ad_state session_count", "Process launch count"),
        ("SharedPreferences", "freecell_ad_state games_played", "Started deal count used for ads"),
        ("SharedPreferences", "freecell_ad_state last_interstitial_game", "Game count at last shown interstitial"),
        ("SharedPreferences", "freecell_ad_state background_at", "Timestamp used for resume app open eligibility"),
    ], widths=[1.35, 2.35, 3.3])
    add_bullets(doc, [
        "The active state schema version is 1.",
        "The bridge rejects state strings larger than 500000 characters.",
        "Runtime undo retains at most 150 snapshots; persistence writes the last 40 and restore accepts the last 40 valid snapshots.",
        "The recent game log retains eight entries.",
        "State saves on each legal move, option or theme change, every 15 seconds, document visibility loss and pagehide.",
        "If the Android bridge is absent, the preview falls back to localStorage and optional OpenAI widget state.",
    ])

    add_heading(doc, "Remote Config", 1)
    add_para(doc, "Firebase Remote Config uses one parameter named ads_config with JSON type. The bundled asset is applied immediately as the safe default. If Firebase is configured, the app activates the value fetched during a previous session, applies it, then fetches in the background for a later launch. The minimum production fetch interval is 12 hours. This prevents advertising cadence from changing in the middle of a game.")
    remote_json = (ROOT / "app/src/main/assets/ad_config_defaults.json").read_text(encoding="utf-8")
    add_code_block(doc, remote_json, 8.0)
    add_table(doc, ["Field", "Meaning", "Accepted range or fallback"], [
        ("enabled", "Master switch for all ads", "Default true"),
        ("interstitials.interval", "Started games between shown interstitials", "1 to 100; default 1"),
        ("interstitials availability session", "Minimum process session", "1 to 10000; default 1"),
        ("interstitials games_played", "Minimum started game count", "0 to 1000000; default 3"),
        ("banners availability session", "Minimum process session", "1 to 10000; default 1"),
        ("banners games_played", "Minimum started game count", "0 to 1000000; default 0"),
        ("app_open cold_start_session", "First cold start session eligible", "1 to 10000; default 1"),
        ("app_open background_min_session", "First resume session eligible", "1 to 10000; default 1"),
        ("app_open splash_timeout", "Cold start load window in seconds", "0 to 30; default 6"),
        ("app_open background_time_to_show_min", "Minimum background duration in minutes", "1 to 1440; default 1"),
        ("ad_unit_ids", "Production banner interstitial and app open unit IDs", "Empty means no release request for that format"),
        ("enable_amazon_sdk", "Amazon demand flag", "Parsed and logged only"),
        ("enable_pubmatic_sdk", "PubMatic demand flag", "Parsed and logged only"),
    ], widths=[2.5, 2.85, 1.65])

    add_heading(doc, "Advertising Behavior", 2)
    add_bullets(doc, [
        "All ads are blocked while first run onboarding is pending.",
        "Debug builds always use Google official test ad units. Release builds use only IDs from ads_config.",
        "Banner uses the current orientation anchored adaptive format and appears in the native container below the WebView.",
        "Interstitial eligibility is checked when a nondeveloper deal starts. A shown interstitial records the game number used as the next interval baseline.",
        "App open ads may show on cold start within the configured splash timeout or after the configured background duration.",
        "Only one full screen ad may be visible at a time. Dismissal or display failure triggers a reload.",
        "The current manifest contains Google's test AdMob App ID. It must be replaced with the production AdMob App ID.",
        "Amazon and PubMatic mediation adapters, vendor identifiers and account setup are not included. Their flags do not activate demand by themselves.",
        "Google UMP consent and regional privacy handling are not implemented and are required before production advertising.",
    ])

    add_heading(doc, "Firebase Setup", 2)
    add_numbered(doc, [
        "Create or select the Firebase project.",
        "Register an Android application using the exact package com.nexusplaylabss.freecell.",
        "Download google-services.json and place it at app/google-services.json.",
        "Create the ads_config parameter as JSON and publish the complete object.",
        "Create production Banner, Interstitial and App Open units in AdMob and place their IDs inside ad_unit_ids.",
        "Replace the test AdMob application ID in app/src/main/res/values/strings.xml.",
        "Implement and test consent before initializing or requesting production ads.",
        "Rebuild and sign a new AAB after adding google-services.json and production identifiers.",
    ])

    add_heading(doc, "Analytics", 1)
    add_para(doc, "AnalyticsTracker operates only when FirebaseApp is configured. It accepts a fixed allowlist of event names and parameter names, converts primitive values into a Firebase Bundle, truncates strings to 100 characters and ignores malformed or unexpected data. The game does not log card identities, layouts, free text or email addresses.")
    analytics_rows = [
        ("app_ready", "Loading flow completes", "first_run, load_ms"),
        ("tutorial_open", "Tutorial intro opens", "source"),
        ("tutorial_start", "Guided steps start", "source"),
        ("tutorial_step", "A step first appears", "source, step, total_steps"),
        ("tutorial_skip", "Tutorial is skipped or closed", "source, step, total_steps"),
        ("tutorial_complete", "Final step completes", "source, step, total_steps"),
        ("game_start", "New or replayed deal starts", "game_mode, level, replay"),
        ("game_win", "Real game reaches four Kings", "game_mode, level, moves, duration_seconds, score"),
        ("game_abandon", "Unfinished deal is replaced", "game_mode, level, moves, duration_seconds"),
        ("hint_used", "Manual hint requested", "game_mode, level, moves"),
        ("undo_used", "Undo succeeds", "game_mode, level, moves"),
        ("menu_open", "Settings Themes or New Game opens", "menu"),
        ("setting_changed", "Option or free cell side changes", "setting plus enabled or value"),
        ("language_changed", "Language changes", "language"),
        ("theme_selected", "Card or table theme changes", "theme, theme_type"),
        ("daily_challenge_open", "Calendar opens", "source"),
        ("daily_challenge_start", "Today deal starts", "level"),
        ("daily_challenge_complete", "Today deal completes", "level, moves, duration_seconds, score"),
        ("level_up", "Progress crosses a level", "previous_level, new_level"),
        ("ad_request", "SDK load begins", "ad_format, placement, session_count, games_played"),
        ("ad_loaded", "SDK returns an ad", "ad_format, placement, session_count, games_played"),
        ("ad_shown", "Impression or fullscreen display", "ad_format, placement, session_count, games_played"),
        ("ad_dismissed", "Fullscreen ad closes", "ad_format, placement, session_count, games_played"),
        ("ad_failed", "Load or presentation fails", "common ad parameters, error_code, error_domain"),
    ]
    add_table(doc, ["Event", "Trigger", "Parameters"], analytics_rows, widths=[1.55, 2.7, 2.75])

    add_heading(doc, "Build and Release Configuration", 1)
    add_table(doc, ["Item", "Current value"], [
        ("Application ID", "com.nexusplaylabss.freecell"),
        ("Version", "0.4.4"),
        ("Version code", "9"),
        ("Minimum Android SDK", "24"),
        ("Target and compile SDK", "36"),
        ("Java", "17"),
        ("Android Gradle Plugin", "9.4.0"),
        ("Gradle wrapper", "9.6.0"),
        ("Firebase BoM", "34.19.0"),
        ("Google Mobile Ads", "25.5.0"),
        ("Release optimization", "R8 minification and resource shrinking enabled"),
    ], widths=[2.35, 4.65])
    add_code_block(doc, """JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew test lintDebug assembleDebug

export UPLOAD_KEYSTORE=/absolute/path/upload-keystore.jks
export UPLOAD_KEYSTORE_PASSWORD=secret
export UPLOAD_KEY_ALIAS=freecell-upload
export UPLOAD_KEY_PASSWORD=secret
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew bundleRelease
""", 8.0)
    add_para(doc, "The private upload keystore is intentionally absent from the source archive. The receiving team must preserve it separately because future updates to the existing Play application must use the registered upload key unless Google approves a reset.")

    add_heading(doc, "Tests", 1)
    add_bullets(doc, [
        "AdConfigTest verifies JSON parsing, the third game interstitial threshold, interval behavior, banner eligibility and one minute background app open eligibility.",
        "FreeCellGameTest verifies a 52 card unique native deal, native state round trip and a suggested move followed by Undo.",
        "The most recent corrected release build completed Gradle test and bundleRelease successfully with package com.nexusplaylabss.freecell.",
        "The active JavaScript engine currently has no automated browser or instrumentation test suite. Gameplay regression testing is therefore still manual.",
    ])

    add_heading(doc, "Known Gaps and Production Work", 1)
    add_table(doc, ["Priority", "Gap", "Required action"], [
        ("Release blocker", "No google-services.json in source", "Register the exact Android package in Firebase and rebuild"),
        ("Release blocker", "Test AdMob App ID", "Replace with production App ID and production unit IDs"),
        ("Release blocker", "No consent flow", "Implement Google UMP and regional privacy behavior"),
        ("High", "Developer victory button visible", "Hide behind a debug flag and prevent progression awards"),
        ("High", "Daily streak fixed at 3", "Persist daily completions and compute streak and monthly state"),
        ("High", "Completed Numbered Games is not a true index", "Persist individual completed deal numbers"),
        ("High", "Random deal solvability not verified", "Use a solver verified catalogue or integrate a solver"),
        ("Medium", "Locked themes remain selectable", "Add entitlement checks or relabel them as previews"),
        ("Medium", "Reward 10 has no wallet", "Connect to a real economy or remove the reward"),
        ("Medium", "High Score repeats current score", "Persist and compare best score by mode"),
        ("Medium", "Terms view is placeholder", "Link the approved production terms document"),
        ("Medium", "Amazon and PubMatic flags are nonfunctional", "Add mediation adapters and vendor setup or remove flags"),
        ("Medium", "Active and legacy engines coexist", "Delete or clearly isolate legacy Java engine after migration choice"),
        ("Low", "Java source folders retain the old directory prefix", "Move files to app/src/main/java/com/nexusplaylabss/freecell without changing package declarations"),
        ("Quality", "No automated WebView gameplay suite", "Add browser level tests and Android instrumentation tests"),
        ("Quality", "Accessibility coverage is partial", "Add keyboard focus review, screen reader semantics and contrast testing"),
    ], widths=[1.1, 2.55, 3.35])

    add_heading(doc, "Migration Instructions for Another AI Platform", 1)
    add_numbered(doc, [
        "Unpack FreeCell-Complete-Source-v0.4.4.zip without flattening directories.",
        "Treat app/src/main/assets/freecell.html as the active UI and game engine. Do not replace it with the unused FreeCellView path unless a native rewrite is intentional.",
        "Preserve applicationId com.nexusplaylabss.freecell if the result will update the existing Play Console application.",
        "Preserve the clean room boundary. Do not import code or proprietary assets from competitor APKs or recordings.",
        "Add environment specific Firebase, AdMob, consent and legal values outside the shared source where appropriate.",
        "Run the JVM tests and a debug build before changing gameplay. Add tests around legalMove, Undo scoring, deal mode selection, persistence restoration and first run tutorial behavior.",
        "Validate every card move type on a physical Android phone, including drag cancellation, multi card capacity, automatic foundation flights, Undo and app background restoration.",
        "Generate a signed AAB only with the separately protected upload key and verify the package and version code before upload.",
    ])

    add_heading(doc, "Repository Manifest", 1)
    text_files = all_source_files()
    manifest_rows = []
    for path in text_files:
        rel = path.relative_to(ROOT)
        text = path.read_text(encoding="utf-8", errors="replace")
        manifest_rows.append((str(rel), str(text.count("\n") + (1 if text else 0)), str(path.stat().st_size), sha256(path)[:16]))
    add_table(doc, ["Text source path", "Lines", "Bytes", "SHA 256 prefix"], manifest_rows, widths=[4.25, 0.55, 0.75, 1.45])

    add_heading(doc, "Binary and Visual Resources", 2)
    binary_rows = []
    for path in binary_resource_files():
        binary_rows.append((str(path.relative_to(ROOT)), str(path.stat().st_size), sha256(path)[:20]))
    add_table(doc, ["Resource path", "Bytes", "SHA 256 prefix"], binary_rows, widths=[4.75, 0.8, 1.45])
    add_para(doc, "Binary files are included intact in the ZIP and are not converted into text in this document. The private JKS signing key and generated application binaries are excluded.")

    doc.add_page_break()
    add_heading(doc, "Complete Text Source Listing", 1)
    add_para(doc, "The following appendix reproduces every text source, build file, resource definition, test, script and repository document included in the transfer archive. File paths and SHA 256 prefixes can be matched against the manifest above. The ZIP is authoritative for exact bytes and binary resources.")
    for index, path in enumerate(text_files, 1):
        rel = path.relative_to(ROOT)
        file_heading = add_heading(doc, f"Source File {index} {rel}", 2)
        file_heading.paragraph_format.page_break_before = True
        metadata = doc.add_paragraph()
        metadata.add_run(f"Bytes {path.stat().st_size}  •  SHA 256 {sha256(path)}").italic = True
        content = path.read_text(encoding="utf-8", errors="replace")
        add_code_block(doc, content, 6.6 if path.name == "freecell.html" else 7.0)

    core = doc.core_properties
    core.title = "FreeCell Complete Technical Handoff"
    core.subject = "Product specification architecture rules configuration and complete source listing"
    core.author = "Nexus Play Labs"
    core.keywords = "FreeCell Android source code architecture Remote Config Firebase AdMob"
    core.comments = "Generated from the repository state for transfer to another development platform."
    doc.save(OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    build_document()
