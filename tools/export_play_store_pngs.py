from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "play-store-screenshots-preview"
OUTPUT = ROOT / "play-store-screenshots-final"
FILES = [
    ("01-classic.png", "FreeCell-01-Classic.png"),
    ("02-aurora.png", "FreeCell-02-Aurora.png"),
    ("03-sunlight.png", "FreeCell-03-Sunlight.png"),
    ("04-neon.png", "FreeCell-04-Neon.png"),
    ("05-moonstone.png", "FreeCell-05-Moonstone.png"),
    ("06-emberwood.png", "FreeCell-06-Emberwood.png"),
    ("07-classic-aurora-cards.png", "FreeCell-07-Classic-Aurora-Cards.png"),
]

OUTPUT.mkdir(parents=True, exist_ok=True)

for source_name, output_name in FILES:
    with Image.open(SOURCE / source_name) as source:
        image = source.convert("RGB")
        if image.size != (1080, 1920):
            raise ValueError(f"Unexpected dimensions for {source_name}: {image.size}")
        image.save(OUTPUT / output_name, "PNG", optimize=True)

