#!/usr/bin/env python3
"""Generate placeholder PNGs for every screenshot referenced by USER_GUIDE.md.

These are real images (not broken-image icons), so the user guide renders
properly even before real captures exist. `scripts/capture-screenshots.sh`
overwrites them with real screenshots as the user steps through.
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
IMG_DIR = ROOT / "docs" / "images"
IMG_DIR.mkdir(parents=True, exist_ok=True)

# Filename → caption (matches scripts/capture-screenshots.sh order)
SHOTS = [
    ("00-welcome.png",            "IntelliJ Welcome / Recent Projects"),
    ("01-overview.png",           "Designer view — Palette · Canvas · Properties"),
    ("02-new-form-dialog.png",    "File → New → FXML Form — template picker"),
    ("03-palette.png",            "Palette tool window with categorised widgets"),
    ("04-form-outline.png",       "Form Outline tree (bottom of Palette)"),
    ("05-properties.png",         "Properties tool window with editable rows"),
    ("06-vj-forms.png",           "VJ Forms — every .fxml + its controller"),
    ("07-designer-toolbar.png",   "Designer toolbar zoom"),
    ("08-selection-handles.png",  "Selected widget — red box + corner handles"),
    ("09-smart-guides.png",       "Mid-drag — pink smart guide aligning edges"),
    ("10-ruler-guides.png",       "Solid blue user-placed ruler guide"),
    ("11-rightclick-events.png",  "Right-click popup — events list + Rename + Delete"),
    ("12-generated-handler.png",  "Generated handler with commented cheat-sheet"),
    ("13-wireup-dialog.png",      "Wire-Up Recipe dialog — recipes + roles"),
    ("14-pojo-binding.png",       "POJO Binding Wizard — property → widget mapping"),
    ("15-menu-editor.png",        "Menu Editor — MenuBar / Menu / MenuItem tree"),
    ("16-tab-order.png",          "Tab Order — focusable widgets with ▲▼"),
    ("17-column-editor.png",      "TableView Column Editor — column rows"),
    ("18-running-app.png",        "Running JavaFX app from the toolbar Run"),
]

# Match the user guide aspect ratio of a typical screenshot.
W, H = 1280, 800
BG = (245, 247, 250)
FRAME = (210, 215, 222)
ACCENT = (74, 108, 179)  # the IDE-blue we use in mermaid diagrams
INK = (45, 55, 72)
SUBTLE = (140, 150, 165)

def font(size: int):
    candidates = [
        "/System/Library/Fonts/Helvetica.ttc",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for c in candidates:
        p = Path(c)
        if p.exists():
            try:
                return ImageFont.truetype(str(p), size)
            except Exception:
                continue
    return ImageFont.load_default()

f_filename = font(28)
f_caption = font(40)
f_note = font(20)
f_meta = font(18)


def make(file_name: str, caption: str):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    # Outer card frame
    pad = 60
    d.rectangle([pad, pad, W - pad, H - pad], outline=FRAME, width=2, fill=(255, 255, 255))

    # Accent bar at the top
    d.rectangle([pad, pad, W - pad, pad + 8], fill=ACCENT)

    # Filename, top-left
    d.text((pad + 24, pad + 28), file_name, font=f_filename, fill=INK)

    # Caption, centred vertically
    bbox = d.textbbox((0, 0), caption, font=f_caption)
    cw, ch = bbox[2] - bbox[0], bbox[3] - bbox[1]
    d.text(((W - cw) / 2, (H - ch) / 2 - 40), caption, font=f_caption, fill=INK)

    # Subtle marker
    d.text(((W - cw) / 2, (H - ch) / 2 + 30), "placeholder", font=f_meta, fill=SUBTLE)

    # Instruction footer
    foot = "Run scripts/capture-screenshots.sh to replace with a real capture"
    bbox = d.textbbox((0, 0), foot, font=f_note)
    fw = bbox[2] - bbox[0]
    d.text(((W - fw) / 2, H - pad - 36), foot, font=f_note, fill=SUBTLE)

    out = IMG_DIR / file_name
    img.save(out, "PNG", optimize=True)
    return out


def main():
    print(f"==> Writing {len(SHOTS)} placeholders to {IMG_DIR}")
    for name, caption in SHOTS:
        path = make(name, caption)
        print(f"    {path.name}  ({path.stat().st_size // 1024} KB)")
    print("==> Done. Markdown viewers will now show these placeholders.")
    print("==> Run scripts/capture-screenshots.sh when you want real captures.")


if __name__ == "__main__":
    sys.exit(main())
