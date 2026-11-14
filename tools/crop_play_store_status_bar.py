from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "play-store-screenshots-final"
OUTPUT = ROOT / "play-store-screenshots-no-statusbar"
TOP_CROP = 80

OUTPUT.mkdir(parents=True, exist_ok=True)

for source_path in sorted(SOURCE.glob("FreeCell-*.png")):
    with Image.open(source_path) as source:
        image = source.convert("RGB")
        if image.size != (1080, 1920):
            raise ValueError(f"Unexpected dimensions for {source_path.name}: {image.size}")
        cropped = image.crop((0, TOP_CROP, 1080, 1920))
        output_name = source_path.stem + "-No-Status-Bar.png"
        cropped.save(OUTPUT / output_name, "PNG", optimize=True)

