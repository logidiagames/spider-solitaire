from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "play-store-screenshots-preview"
OUTPUT = SOURCE / "preview-contact-sheet.jpg"
ITEMS = [
    ("01-classic.png", "Classic"),
    ("02-aurora.png", "Aurora"),
    ("03-sunlight.png", "Sunlight"),
    ("04-neon.png", "Neon"),
    ("05-moonstone.png", "Moonstone"),
    ("06-emberwood.png", "Emberwood"),
    ("07-classic-aurora-cards.png", "Classic + Aurora cards"),
]

columns = 3
thumb_width = 300
thumb_height = 533
label_height = 42
gap = 18
margin = 24
rows = (len(ITEMS) + columns - 1) // columns
canvas_width = margin * 2 + columns * thumb_width + (columns - 1) * gap
canvas_height = margin * 2 + rows * (thumb_height + label_height) + (rows - 1) * gap

canvas = Image.new("RGB", (canvas_width, canvas_height), "#101916")
draw = ImageDraw.Draw(canvas)
font = ImageFont.load_default(size=22)

for index, (filename, label) in enumerate(ITEMS):
    row, column = divmod(index, columns)
    x = margin + column * (thumb_width + gap)
    y = margin + row * (thumb_height + label_height + gap)
    with Image.open(SOURCE / filename) as image:
        image = image.convert("RGB")
        image.thumbnail((thumb_width, thumb_height), Image.Resampling.LANCZOS)
        canvas.paste(image, (x, y))
    draw.text((x, y + thumb_height + 9), label, fill="#ffffff", font=font)

canvas.save(OUTPUT, "JPEG", quality=92, optimize=True)

