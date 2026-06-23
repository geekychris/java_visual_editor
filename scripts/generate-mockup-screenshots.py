#!/usr/bin/env python3
"""Generate IDE-style mockup screenshots for the user guide.

NOT REAL INTELLIJ CAPTURES — I can't reliably drive a Swing app from a
script. But these are much closer than the labelled placeholders:

  • Form previews are REAL JavaFX renders from the sidecar process
  • IDE chrome (tool windows, toolbar, dialogs) is composed in PIL to look
    like a real IntelliJ Light-theme screenshot

Run:
    scripts/generate-mockup-screenshots.py

Writes 19 PNGs into docs/images/.
"""
from __future__ import annotations
import base64
import io
import json
import os
import socket
import subprocess
import sys
import time
from pathlib import Path
from typing import Optional, Tuple

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
IMG_DIR = ROOT / "docs" / "images"
IMG_DIR.mkdir(parents=True, exist_ok=True)
RENDERER_JAR = ROOT / "preview-renderer" / "build" / "libs" / "preview-renderer.jar"

# ─── IntelliJ-ish colour palette (Light theme) ──────────────────────────────
BG          = (245, 246, 250)
FRAME       = (210, 213, 220)
TOOL_BG     = (243, 243, 243)
TOOL_HDR_BG = (236, 236, 236)
INK         = (45,  55,  72)
INK_DIM     = (105, 115, 130)
ACCENT      = (74,  108, 179)
SUBTLE      = (235, 235, 240)
SELECT      = (210, 230, 255)
HANDLE_FG   = (255, 59,  48)
GUIDE_PINK  = (255, 105, 180)
GUIDE_BLUE  = (0,   122, 255)
TRAFFIC_R   = (255, 95,  86)
TRAFFIC_Y   = (255, 189, 46)
TRAFFIC_G   = (39,  201, 63)

W, H = 1280, 800

# ─── Font loading (system fallback chain) ───────────────────────────────────
def _font(size: int, *, bold=False):
    candidates = []
    if bold:
        candidates += [
            "/System/Library/Fonts/Helvetica.ttc",
            "/System/Library/Fonts/SFNS.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        ]
    candidates += [
        "/System/Library/Fonts/Helvetica.ttc",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for c in candidates:
        if Path(c).exists():
            try:
                return ImageFont.truetype(c, size)
            except Exception:
                continue
    return ImageFont.load_default()


F_HEAD = _font(15, bold=True)
F_BODY = _font(13)
F_MONO = _font(13)
F_CAP  = _font(14, bold=True)
F_SMALL = _font(11)
F_TITLE = _font(12, bold=True)

# ─── Sidecar renderer client ────────────────────────────────────────────────
class Renderer:
    def __init__(self):
        self.proc: Optional[subprocess.Popen] = None
        self.port: Optional[int] = None
        self.sock: Optional[socket.socket] = None

    def start(self):
        if not RENDERER_JAR.exists():
            print(f"==> Building renderer jar")
            subprocess.run(["./gradlew", ":preview-renderer:shadowJar"], cwd=ROOT, check=True)
        print("==> Starting sidecar renderer")
        self.proc = subprocess.Popen(
            ["java", "-jar", str(RENDERER_JAR)],
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True,
        )
        for _ in range(50):
            line = self.proc.stdout.readline()
            if line.startswith("PORT="):
                self.port = int(line.removeprefix("PORT=").strip())
                break
            time.sleep(0.1)
        if not self.port:
            raise RuntimeError("Renderer didn't emit PORT line")
        self.sock = socket.create_connection(("127.0.0.1", self.port), timeout=15)
        print(f"==> Renderer ready on port {self.port}")

    def stop(self):
        try:
            if self.sock:
                self.sock.sendall(b'{"op":"shutdown"}\n')
        except Exception:
            pass
        if self.proc:
            self.proc.terminate()
            try: self.proc.wait(2)
            except Exception: self.proc.kill()

    def render(self, fxml: str, width: int, height: int) -> Image.Image:
        req = json.dumps({"op": "render", "fxml": fxml, "width": width, "height": height})
        self.sock.sendall((req + "\n").encode("utf-8"))
        buf = b""
        while not buf.endswith(b"\n"):
            chunk = self.sock.recv(65536)
            if not chunk:
                raise RuntimeError("Renderer closed")
            buf += chunk
        msg = json.loads(buf.decode("utf-8"))
        if msg.get("op") != "frame":
            raise RuntimeError(f"Renderer error: {msg.get('message')}")
        png = base64.b64decode(msg["pngBase64"])
        return Image.open(io.BytesIO(png)).convert("RGB")


# ─── Sample FXML strings for the form previews ──────────────────────────────
HELLO_FXML = '''<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.CheckBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.AnchorPane?>

<AnchorPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml"
            prefWidth="500" prefHeight="320">
    <children>
        <Label fx:id="title" text="Visual Java demo" layoutX="20" layoutY="20"
               style="-fx-font-size: 18px; -fx-font-weight: bold;"/>
        <Label fx:id="nameLabel" text="Your name:" layoutX="20" layoutY="80"/>
        <TextField fx:id="nameField" layoutX="120" layoutY="76" prefWidth="240"/>
        <CheckBox fx:id="agreeCheck" text="I agree to the terms" layoutX="20" layoutY="130"/>
        <Button fx:id="submitBtn" text="Submit" layoutX="20" layoutY="180" prefWidth="100"/>
        <Button fx:id="cancelBtn" text="Cancel" layoutX="130" layoutY="180" prefWidth="100"/>
        <Label fx:id="status" text="(status messages appear here)" layoutX="20" layoutY="260"
               style="-fx-text-fill: #777;"/>
    </children>
</AnchorPane>
'''

LOGIN_FXML = '''<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.PasswordField?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.AnchorPane?>

<AnchorPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml"
            prefWidth="380" prefHeight="220">
    <children>
        <Label text="Sign In" layoutX="24" layoutY="20" style="-fx-font-size: 20px; -fx-font-weight: bold;"/>
        <Label text="Username" layoutX="24" layoutY="70"/>
        <TextField layoutX="110" layoutY="66" prefWidth="240" promptText="username"/>
        <Label text="Password" layoutX="24" layoutY="106"/>
        <PasswordField layoutX="110" layoutY="102" prefWidth="240" promptText="password"/>
        <Button text="Sign In" layoutX="220" layoutY="160" prefWidth="130"/>
        <Button text="Cancel" layoutX="110" layoutY="160" prefWidth="100"/>
    </children>
</AnchorPane>
'''


# ─── Drawing helpers ────────────────────────────────────────────────────────
def new_canvas() -> Tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    return img, d


def macos_titlebar(d: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int, title: str):
    d.rectangle([x, y, x + w, y + h], fill=(225, 225, 230), outline=FRAME)
    d.ellipse([x + 12, y + 8, x + 24, y + 20], fill=TRAFFIC_R)
    d.ellipse([x + 30, y + 8, x + 42, y + 20], fill=TRAFFIC_Y)
    d.ellipse([x + 48, y + 8, x + 60, y + 20], fill=TRAFFIC_G)
    bbox = d.textbbox((0, 0), title, font=F_TITLE)
    tw = bbox[2] - bbox[0]
    d.text((x + (w - tw) // 2, y + 6), title, font=F_TITLE, fill=INK_DIM)


def panel(d, x, y, w, h, header: Optional[str] = None, *, fill=(255, 255, 255)):
    d.rectangle([x, y, x + w, y + h], fill=fill, outline=FRAME)
    if header:
        d.rectangle([x, y, x + w, y + 24], fill=TOOL_HDR_BG, outline=FRAME)
        d.text((x + 10, y + 5), header, font=F_HEAD, fill=INK)


def lines(d, x, y, items, font=F_BODY, color=INK, line_h=18, indent=0):
    for i, t in enumerate(items):
        d.text((x + indent, y + i * line_h), t, font=font, fill=color)


def button(d, x, y, w, h, text, *, active=False):
    fill = ACCENT if active else (250, 250, 250)
    ink = (255, 255, 255) if active else INK
    d.rectangle([x, y, x + w, y + h], fill=fill, outline=FRAME)
    bbox = d.textbbox((0, 0), text, font=F_BODY)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    d.text((x + (w - tw) // 2, y + (h - th) // 2 - 1), text, font=F_BODY, fill=ink)


def tag(d, x, y, text, fill=(232, 240, 254)):
    bbox = d.textbbox((0, 0), text, font=F_SMALL)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    pad = 6
    d.rectangle([x, y, x + tw + 2 * pad, y + th + 6], fill=fill, outline=FRAME)
    d.text((x + pad, y + 2), text, font=F_SMALL, fill=INK)
    return x + tw + 2 * pad + 4


def save(img: Image.Image, name: str):
    out = IMG_DIR / name
    img.save(out, "PNG", optimize=True)
    print(f"    {name}  ({out.stat().st_size // 1024} KB)")


# ─── Per-screenshot functions ───────────────────────────────────────────────
def shot_overview(r: Renderer):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Hello.fxml — sample-fxml — IntelliJ IDEA")
    # Left: palette
    panel(d, 0, 30, 220, H - 30, "Palette")
    lines(d, 8, 60, [
        "▾ Containers", "  Pane", "  AnchorPane", "  BorderPane", "  HBox", "  VBox",
        "▾ Controls", "  Button", "  Label", "  TextField", "  CheckBox", "  ComboBox", "  Slider",
        "▸ Lists & Tables", "▸ Display",
    ])
    # Form outline at bottom of palette
    panel(d, 0, 420, 220, H - 420, "Form Outline")
    lines(d, 8, 450, [
        "▾ AnchorPane",
        "    Label · title",
        "    Label · nameLabel",
        "    TextField · nameField",
        "    CheckBox · agreeCheck",
        "    Button · submitBtn",
        "    Button · cancelBtn",
        "    Label · status",
    ])
    # Right: properties
    panel(d, W - 250, 30, 250, H - 30, "Properties")
    d.text((W - 240, 60), "Button · submitBtn", font=F_HEAD, fill=INK)
    prop_rows = [
        ("(Name)", "submitBtn"),
        ("text", "Submit"),
        ("layoutX", "20"),
        ("layoutY", "180"),
        ("prefWidth", "100"),
        ("prefHeight", "—"),
        ("style", ""),
        ("visible", "true"),
        ("disable", "false"),
        ("mnemonicParsing", "true"),
    ]
    for i, (k, v) in enumerate(prop_rows):
        y = 90 + i * 22
        d.line([(W - 250, y + 18), (W, y + 18)], fill=SUBTLE)
        d.text((W - 240, y), k, font=F_BODY, fill=INK_DIM)
        d.text((W - 120, y), v, font=F_BODY, fill=INK)
    # Centre toolbar
    panel(d, 220, 30, W - 470, 36, header=None, fill=(248, 248, 250))
    bx = 230
    for label, on in [("Rulers", True), ("Grid", False), ("Snap", False), ("Guides", True)]:
        button(d, bx, 36, 64, 24, label, active=on); bx += 70
    bx += 14
    for label in ["L", "C", "R", "T", "M", "B"]:
        button(d, bx, 36, 28, 24, label); bx += 30
    bx += 14
    for label in ["Run", "Tab Order…", "Menu…", "Wire-Up…", "Bind POJO…", "Wire All"]:
        bbox = d.textbbox((0, 0), label, font=F_BODY)
        bw = bbox[2] - bbox[0] + 18
        button(d, bx, 36, bw, 24, label); bx += bw + 6
    # Centre canvas with real form render
    canvas_x, canvas_y = 220, 70
    canvas_w, canvas_h = W - 470, H - 70
    d.rectangle([canvas_x, canvas_y, canvas_x + canvas_w, canvas_y + canvas_h], fill=(230, 232, 236))
    # Render Hello.fxml to a real PNG
    form_img = r.render(HELLO_FXML, 500, 320)
    fx = canvas_x + (canvas_w - form_img.width) // 2
    fy = canvas_y + 40
    img.paste(form_img, (fx, fy))
    # rulers along top + left of canvas area
    d.rectangle([canvas_x, canvas_y, canvas_x + canvas_w, canvas_y + 20], fill=(240, 240, 244), outline=FRAME)
    for x in range(0, canvas_w, 50):
        d.line([(canvas_x + x, canvas_y + 12), (canvas_x + x, canvas_y + 20)], fill=INK_DIM)
        d.text((canvas_x + x + 2, canvas_y + 2), str(x), font=F_SMALL, fill=INK_DIM)
    d.rectangle([canvas_x, canvas_y, canvas_x + 20, canvas_y + canvas_h], fill=(240, 240, 244), outline=FRAME)
    save(img, "01-overview.png")


def shot_new_form_dialog(r: Renderer):
    img, d = new_canvas()
    # Faded background
    d.rectangle([0, 0, W, H], fill=(225, 228, 234))
    # Dialog window
    dx, dy, dw, dh = 240, 120, 800, 540
    macos_titlebar(d, dx, dy, dw, 30, "New FXML Form")
    d.rectangle([dx, dy + 30, dx + dw, dy + dh], fill=(255, 255, 255), outline=FRAME)
    # Left: template list
    panel(d, dx, dy + 30, 260, dh - 30, "Template")
    templates = [
        ("Blank Form", False),
        ("Login Form", True),
        ("About Box", False),
        ("Splash Screen", False),
        ("Settings Dialog", False),
        ("Wizard (3 steps)", False),
        ("CRUD Form", False),
        ("Form with Menu + Toolbar + Status", False),
    ]
    for i, (name, sel) in enumerate(templates):
        y = dy + 64 + i * 28
        if sel:
            d.rectangle([dx + 2, y - 2, dx + 258, y + 22], fill=SELECT)
        d.text((dx + 14, y + 2), name, font=F_BODY, fill=INK)
    # Right: description + name
    d.text((dx + 280, dy + 64), "Username + Password + Sign In / Cancel.", font=F_BODY, fill=INK)
    d.line([(dx + 280, dy + 100), (dx + dw - 24, dy + 100)], fill=SUBTLE)
    d.text((dx + 280, dy + 116), "Form name (no .fxml needed):", font=F_BODY, fill=INK)
    d.rectangle([dx + 280, dy + 140, dx + 580, dy + 168], fill=(252, 252, 254), outline=FRAME)
    d.text((dx + 290, dy + 146), "LoginForm", font=F_BODY, fill=INK)
    # Buttons
    button(d, dx + dw - 220, dy + dh - 50, 90, 30, "Cancel")
    button(d, dx + dw - 120, dy + dh - 50, 96, 30, "OK", active=True)
    save(img, "02-new-form-dialog.png")


def shot_palette(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Palette tool window")
    # Zoom view: render the palette wide
    panel(d, 240, 60, 800, 700, "Palette")
    cats = [
        ("Containers", ["Pane", "AnchorPane", "BorderPane", "GridPane", "StackPane",
                        "HBox", "VBox", "FlowPane", "TabPane", "ScrollPane",
                        "SplitPane", "TitledPane", "Accordion", "ToolBar"]),
        ("Controls", ["Button", "ToggleButton", "MenuButton", "Label", "Hyperlink",
                      "TextField", "PasswordField", "TextArea", "CheckBox",
                      "RadioButton", "ComboBox", "ChoiceBox", "DatePicker",
                      "ColorPicker", "Spinner", "Slider", "Separator"]),
        ("Lists & Tables", ["ListView", "TableView", "TreeView", "TreeTableView", "MenuBar"]),
        ("Display", ["ProgressBar", "ProgressIndicator", "ImageView"]),
    ]
    y = 100
    for cat, items in cats:
        d.rectangle([248, y, 1032, y + 24], fill=(240, 240, 244))
        d.text((258, y + 4), cat, font=F_HEAD, fill=INK)
        y += 28
        for it in items:
            d.text((278, y), "■  " + it, font=F_BODY, fill=INK)
            y += 18
        y += 8
    save(img, "03-palette.png")


def shot_form_outline(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Form Outline")
    panel(d, 320, 80, 640, 640, "Form Outline")
    tree = [
        ("▾ AnchorPane", 0),
        ("▾ MenuBar · menuBar", 1),
        ("    Menu · fileMenu", 2),
        ("        MenuItem · newMenuItem", 3),
        ("        MenuItem · openMenuItem", 3),
        ("        MenuItem · saveMenuItem", 3),
        ("    Menu · editMenu", 2),
        ("    Menu · helpMenu", 2),
        ("▾ ToolBar · toolBar", 1),
        ("    Button · newBtn", 2),
        ("    Button · saveBtn", 2),
        ("▾ AnchorPane · contentArea", 1),
        ("    Label · title", 2),
        ("    TextField · nameField", 2),
        ("    CheckBox · agreeCheck", 2),
        ("    Button · submitBtn", 2),
        ("    Button · cancelBtn", 2),
        ("▾ HBox · statusBar", 1),
        ("    Label · statusLabel", 2),
    ]
    for i, (txt, depth) in enumerate(tree):
        d.text((340 + depth * 16, 124 + i * 22), txt, font=F_BODY, fill=INK)
    save(img, "04-form-outline.png")


def shot_properties(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Properties")
    panel(d, 320, 80, 640, 640, "Properties")
    d.text((340, 110), "TextField · nameField", font=F_HEAD, fill=INK)
    rows = [
        ("(Name)", "nameField"),
        ("layoutX", "120"),
        ("layoutY", "76"),
        ("prefWidth", "240"),
        ("prefHeight", "—"),
        ("style", ""),
        ("visible", "true"),
        ("disable", "false"),
        ("text", ""),
        ("promptText", "your name"),
        ("editable", "true"),
    ]
    y = 144
    d.line([(340, y - 4), (940, y - 4)], fill=FRAME)
    d.text((340, y - 22), "Property", font=F_CAP, fill=INK)
    d.text((640, y - 22), "Value", font=F_CAP, fill=INK)
    for i, (k, v) in enumerate(rows):
        ry = y + i * 32
        d.line([(340, ry + 22), (940, ry + 22)], fill=SUBTLE)
        d.text((340, ry + 4), k, font=F_BODY, fill=INK_DIM)
        d.rectangle([628, ry, 940, ry + 26], fill=(252, 252, 254), outline=FRAME)
        d.text((638, ry + 5), v, font=F_BODY, fill=INK)
    save(img, "05-properties.png")


def shot_vj_forms(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "VJ Forms")
    panel(d, 320, 80, 640, 640, "Visual Java forms (3)")
    items = [
        ("▾ Hello.fxml", 0),
        ("    ↳ HelloController.java", 1),
        ("▾ LoginForm.fxml", 0),
        ("    ↳ LoginFormController.java", 1),
        ("▾ ContactForm.fxml", 0),
        ("    ↳ ContactFormController.java", 1),
    ]
    for i, (t, depth) in enumerate(items):
        d.text((340 + depth * 20, 124 + i * 28), t, font=F_BODY, fill=INK)
    save(img, "06-vj-forms.png")


def shot_designer_toolbar(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Designer toolbar")
    # Big toolbar zoom
    panel(d, 80, 200, W - 160, 100, header=None, fill=(248, 248, 250))
    bx, by = 100, 230
    d.text((bx, by - 18), "View:", font=F_HEAD, fill=INK)
    for label, on in [("Rulers", True), ("Grid", False), ("Snap to grid", False), ("Smart guides", True)]:
        button(d, bx, by, 100, 32, label, active=on); bx += 110
    bx += 12; d.line([(bx, by), (bx, by + 32)], fill=FRAME); bx += 12
    d.text((bx, by - 18), "Align:", font=F_HEAD, fill=INK)
    for label in ["←", "↔", "→", "↑", "⇕", "↓", "⇋H", "⇋V"]:
        button(d, bx, by, 40, 32, label); bx += 44
    bx += 12; d.line([(bx, by), (bx, by + 32)], fill=FRAME); bx += 12
    for label in ["Run", "Tab Order…", "Menu…", "Wire-Up…", "Bind POJO…", "Wire All"]:
        bbox = d.textbbox((0, 0), label, font=F_BODY)
        bw = bbox[2] - bbox[0] + 20
        button(d, bx, by, bw, 32, label); bx += bw + 8
    d.text((100, 320), "1. View toggles  ·  2. Align cluster  ·  3. Run + dialogs + bulk-wire",
           font=F_BODY, fill=INK_DIM)
    save(img, "07-designer-toolbar.png")


def shot_selection_handles(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Selection")
    # Render real form, then overlay selection chrome on the Submit button
    form = r.render(HELLO_FXML, 500, 320)
    fx = (W - form.width) // 2
    fy = 100
    d.rectangle([0, 60, W, H], fill=(230, 232, 236))
    img.paste(form, (fx, fy))
    # Submit button is at FXML coords ~ (20, 180, 100, 26)
    sx, sy, sw, sh = fx + 20, fy + 180, 100, 26
    d.rectangle([sx - 2, sy - 2, sx + sw + 2, sy + sh + 2], outline=HANDLE_FG, width=2)
    for hx, hy in [(sx, sy), (sx + sw, sy), (sx + sw, sy + sh), (sx, sy + sh)]:
        d.rectangle([hx - 4, hy - 4, hx + 4, hy + 4], fill=(255, 255, 255), outline=HANDLE_FG, width=2)
    d.text((fx + form.width + 20, fy), "Selected: submitBtn", font=F_HEAD, fill=INK)
    d.text((fx + form.width + 20, fy + 24), "Red outline + 4 corner handles", font=F_BODY, fill=INK_DIM)
    save(img, "08-selection-handles.png")


def shot_smart_guides(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Smart guides")
    form = r.render(HELLO_FXML, 500, 320)
    fx = (W - form.width) // 2
    fy = 100
    d.rectangle([0, 60, W, H], fill=(230, 232, 236))
    img.paste(form, (fx, fy))
    # Imagine dragging submitBtn upward; guide line aligns its left edge with the title Label
    gx = fx + 20  # left edge
    for y in range(fy + 20, fy + 320, 8):
        d.line([(gx, y), (gx, y + 4)], fill=GUIDE_PINK, width=1)
    # Ghost outline for the moving widget
    d.rectangle([fx + 20, fy + 150, fx + 120, fy + 176], outline=GUIDE_BLUE, width=2)
    d.text((fx + form.width + 20, fy), "Smart guide aligning to title's left edge", font=F_HEAD, fill=INK)
    save(img, "09-smart-guides.png")


def shot_ruler_guides(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Ruler guides")
    form = r.render(HELLO_FXML, 500, 320)
    fx = (W - form.width) // 2
    fy = 100
    d.rectangle([0, 60, W, H], fill=(230, 232, 236))
    img.paste(form, (fx, fy))
    # Two solid user-placed guides
    d.line([(fx + 120, fy), (fx + 120, fy + 320)], fill=GUIDE_BLUE, width=1)
    d.line([(fx, fy + 80), (fx + 500, fy + 80)], fill=GUIDE_BLUE, width=1)
    d.text((fx + form.width + 20, fy), "Solid blue = user guides", font=F_HEAD, fill=INK)
    d.text((fx + form.width + 20, fy + 24), "Drag from rulers to drop new ones", font=F_BODY, fill=INK_DIM)
    save(img, "10-ruler-guides.png")


def shot_rightclick(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Right-click menu")
    form = r.render(HELLO_FXML, 500, 320)
    fx = (W - form.width) // 2
    fy = 100
    d.rectangle([0, 60, W, H], fill=(230, 232, 236))
    img.paste(form, (fx, fy))
    # Popup menu at submitBtn
    mx, my = fx + 130, fy + 200
    items = [
        "Add onAction   (default)",
        "Add onMouseClicked",
        "Add onMousePressed",
        "Add onMouseReleased",
        "Add onMouseEntered",
        "Add onMouseExited",
        "Add onKeyPressed",
        "Add onKeyReleased",
        "Add onKeyTyped",
        "—",
        "Rename fx:id…",
        "Delete submitBtn",
    ]
    mh = 26 * len(items) + 10
    d.rectangle([mx, my, mx + 280, my + mh], fill=(252, 252, 254), outline=FRAME)
    for i, t in enumerate(items):
        if t == "—":
            d.line([(mx + 8, my + 8 + i * 26 + 12), (mx + 272, my + 8 + i * 26 + 12)], fill=SUBTLE)
        else:
            color = HANDLE_FG if "Delete" in t else INK
            d.text((mx + 16, my + 8 + i * 26), t, font=F_BODY, fill=color)
    save(img, "11-rightclick-events.png")


def shot_generated_handler(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "HelloController.java — generated handler")
    panel(d, 0, 30, W, H - 30, fill=(252, 252, 254))
    # Mock code editor with line numbers
    code = [
        ("12  ", "@FXML"),
        ("13  ", "private void agreeCheckAction(ActionEvent event) {"),
        ("14  ", "    // Sample interactions with agreeCheck — uncomment what you need:"),
        ("15  ", "    //"),
        ("16  ", "    // boolean checked = agreeCheck.isSelected();"),
        ("17  ", "    // if (checked) {"),
        ("18  ", "    //     // agreeCheck was just checked"),
        ("19  ", "    // } else {"),
        ("20  ", "    //     // agreeCheck was just unchecked"),
        ("21  ", "    // }"),
        ("22  ", "    //"),
        ("23  ", "    // // Modify state:"),
        ("24  ", "    // agreeCheck.setSelected(!checked);"),
        ("25  ", "    // agreeCheck.setText(\"New label\");"),
        ("26  ", "    // agreeCheck.setIndeterminate(false);"),
        ("27  ", "    // agreeCheck.setDisable(true);"),
        ("28  ", "}"),
    ]
    GREEN = (0, 128, 0); KEY = (128, 0, 128); STR = (32, 128, 32); ANN = (210, 137, 0)
    for i, (ln, txt) in enumerate(code):
        d.text((20, 80 + i * 22), ln, font=F_MONO, fill=INK_DIM)
        # Simple coloring: lines starting with // are green, @FXML annotation orange
        if txt.lstrip().startswith("//"):
            d.text((90, 80 + i * 22), txt, font=F_MONO, fill=GREEN)
        elif "@FXML" in txt:
            d.text((90, 80 + i * 22), txt, font=F_MONO, fill=ANN)
        else:
            d.text((90, 80 + i * 22), txt, font=F_MONO, fill=INK)
    save(img, "12-generated-handler.png")


def shot_wireup(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Wire-Up Recipe")
    d.rectangle([0, 0, W, H], fill=(225, 228, 234))
    dx, dy, dw, dh = 160, 100, 960, 600
    macos_titlebar(d, dx, dy, dw, 30, "Wire-Up Recipe")
    d.rectangle([dx, dy + 30, dx + dw, dy + dh], fill=(255, 255, 255), outline=FRAME)
    # Left: recipe list
    panel(d, dx, dy + 30, 300, dh - 30, "Recipe")
    recipes = [
        ("Close Window", False), ("Toggle Visibility", False),
        ("CheckBox Enables Target", False), ("Enable When Non-Empty", False),
        ("Bind Slider to Label", False), ("Open File Chooser", False),
        ("Required Fields Validation", True), ("Confirm Before Action", False),
        ("Group RadioButtons", False), ("Tab Change Handler", False),
        ("MessageBox (MsgBox)", False), ("InputBox", False),
        ("Background Task", False), ("Status Bar", False),
        ("Show Modal Dialog", False),
    ]
    for i, (n, sel) in enumerate(recipes):
        y = dy + 60 + i * 26
        if sel:
            d.rectangle([dx + 4, y - 2, dx + 296, y + 22], fill=SELECT)
        d.text((dx + 14, y + 2), n, font=F_BODY, fill=INK)
    # Right: roles
    panel(d, dx + 300, dy + 30, dw - 300, dh - 30, fill=(255, 255, 255))
    d.text((dx + 320, dy + 56), "On button click, check that 1–4 text fields are non-empty.", font=F_BODY, fill=INK)
    d.text((dx + 320, dy + 76), "If any are blank, show a warning Alert and stop. Otherwise call handleSubmit().",
           font=F_BODY, fill=INK_DIM)
    d.line([(dx + 320, dy + 110), (dx + dw - 24, dy + 110)], fill=SUBTLE)
    roles = [("Submit Button:", "submitBtn — Button"), ("Required Field 1:", "nameField — TextField"),
             ("Required Field 2:", "emailField — TextField"), ("Required Field 3:", "(none)"),
             ("Required Field 4:", "(none)")]
    for i, (k, v) in enumerate(roles):
        ry = dy + 130 + i * 60
        d.text((dx + 320, ry), k, font=F_HEAD, fill=INK)
        d.rectangle([dx + 460, ry - 4, dx + 880, ry + 24], fill=(252, 252, 254), outline=FRAME)
        d.text((dx + 470, ry), v, font=F_BODY, fill=INK)
    button(d, dx + dw - 220, dy + dh - 50, 90, 30, "Cancel")
    button(d, dx + dw - 120, dy + dh - 50, 96, 30, "OK", active=True)
    save(img, "13-wireup-dialog.png")


def shot_pojo_binding(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "POJO Binding Wizard")
    d.rectangle([0, 0, W, H], fill=(225, 228, 234))
    dx, dy, dw, dh = 120, 80, 1000, 640
    macos_titlebar(d, dx, dy, dw, 30, "POJO Binding Wizard")
    d.rectangle([dx, dy + 30, dx + dw, dy + dh], fill=(255, 255, 255), outline=FRAME)
    # FQN row
    d.text((dx + 18, dy + 60), "Class FQN:", font=F_BODY, fill=INK)
    d.rectangle([dx + 100, dy + 55, dx + 740, dy + 80], fill=(252, 252, 254), outline=FRAME)
    d.text((dx + 110, dy + 60), "com.example.Contact", font=F_BODY, fill=INK)
    button(d, dx + 760, dy + 53, 90, 30, "Browse…")
    # Table header
    hx, hy = dx + 20, dy + 110
    d.rectangle([hx, hy, hx + dw - 40, hy + 28], fill=(240, 240, 244))
    d.text((hx + 20, hy + 6), "Property", font=F_CAP, fill=INK)
    d.text((hx + 320, hy + 6), "Type", font=F_CAP, fill=INK)
    d.text((hx + 540, hy + 6), "Widget (fx:id)", font=F_CAP, fill=INK)
    rows = [
        ("name", "String", "nameField — TextField"),
        ("age", "int", "ageSpinner — Spinner"),
        ("birthday", "LocalDate", "birthdayDatePicker — DatePicker"),
        ("active", "boolean", "activeCheckBox — CheckBox"),
        ("email", "String", "emailField — TextField"),
        ("notes", "String", "(skip)"),
    ]
    for i, (p, t, w) in enumerate(rows):
        ry = hy + 30 + i * 32
        d.line([(hx, ry + 30), (hx + dw - 40, ry + 30)], fill=SUBTLE)
        d.text((hx + 20, ry + 6), p, font=F_BODY, fill=INK)
        d.text((hx + 320, ry + 6), t, font=F_BODY, fill=INK_DIM)
        d.rectangle([hx + 540, ry, hx + dw - 60, ry + 26], fill=(252, 252, 254), outline=FRAME)
        d.text((hx + 550, ry + 6), w, font=F_BODY, fill=INK)
    d.text((dx + 18, dy + dh - 60), "Contact: 6 bindable properties", font=F_BODY, fill=INK_DIM)
    button(d, dx + dw - 220, dy + dh - 50, 90, 30, "Cancel")
    button(d, dx + dw - 120, dy + dh - 50, 96, 30, "OK", active=True)
    save(img, "14-pojo-binding.png")


def shot_menu_editor(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Menu Editor")
    d.rectangle([0, 0, W, H], fill=(225, 228, 234))
    dx, dy, dw, dh = 200, 100, 880, 600
    macos_titlebar(d, dx, dy, dw, 30, "Menu Editor")
    d.rectangle([dx, dy + 30, dx + dw, dy + dh], fill=(255, 255, 255), outline=FRAME)
    # Left: tree
    panel(d, dx, dy + 30, 360, dh - 80, "Menu structure")
    tree = [
        ("▾ MenuBar", 0, False),
        ("    ▾ File", 1, False),
        ("        New", 2, False),
        ("        Open…", 2, True),
        ("        Save", 2, False),
        ("        Exit", 2, False),
        ("    ▾ Edit", 1, False),
        ("        Undo", 2, False),
        ("        Redo", 2, False),
        ("    ▾ Help", 1, False),
        ("        About…", 2, False),
    ]
    for i, (t, depth, sel) in enumerate(tree):
        y = dy + 64 + i * 24
        if sel:
            d.rectangle([dx + 2, y - 2, dx + 358, y + 22], fill=SELECT)
        d.text((dx + 14 + depth * 14, y + 2), t, font=F_BODY, fill=INK)
    # Tree buttons
    by = dy + dh - 60
    bx = dx + 8
    for label in ["+ Menu", "+ Item", "Remove", "▲", "▼"]:
        button(d, bx, by, 60, 30, label); bx += 64
    # Right: properties
    px = dx + 360
    d.text((px + 16, dy + 60), "Edit Open…", font=F_HEAD, fill=INK)
    fields = [
        ("Text:", "Open…"),
        ("fx:id:", "openMenuItem"),
        ("Accelerator:", "Shortcut+O"),
        ("Handler name:", "onOpen"),
    ]
    for i, (k, v) in enumerate(fields):
        y = dy + 110 + i * 50
        d.text((px + 16, y), k, font=F_BODY, fill=INK)
        d.rectangle([px + 160, y - 4, px + 480, y + 22], fill=(252, 252, 254), outline=FRAME)
        d.text((px + 170, y), v, font=F_BODY, fill=INK)
    d.text((px + 16, dy + 320), "☐ Insert separator before this item", font=F_BODY, fill=INK_DIM)
    d.text((px + 16, dy + 348), "☐ Checkable (CheckMenuItem)", font=F_BODY, fill=INK_DIM)
    button(d, dx + dw - 220, dy + dh - 50, 90, 30, "Cancel")
    button(d, dx + dw - 120, dy + dh - 50, 96, 30, "OK", active=True)
    save(img, "15-menu-editor.png")


def shot_tab_order(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Tab Order")
    d.rectangle([0, 0, W, H], fill=(225, 228, 234))
    dx, dy, dw, dh = 280, 140, 720, 520
    macos_titlebar(d, dx, dy, dw, 30, "Tab Order")
    d.rectangle([dx, dy + 30, dx + dw, dy + dh], fill=(255, 255, 255), outline=FRAME)
    panel(d, dx + 16, dy + 60, dw - 200, dh - 90, "Focus order")
    widgets = [
        "1.  TextField · nameField",
        "2.  PasswordField · passwordField",
        "3.  CheckBox · agreeCheck",
        "4.  Button · loginBtn",
        "5.  Button · cancelBtn",
    ]
    for i, w in enumerate(widgets):
        sel = i == 1
        y = dy + 90 + i * 32
        if sel:
            d.rectangle([dx + 18, y - 2, dx + dw - 200, y + 24], fill=SELECT)
        d.text((dx + 30, y + 2), w, font=F_BODY, fill=INK)
    # Up/Down buttons
    by = dy + 100
    button(d, dx + dw - 170, by, 140, 36, "▲ Move Up")
    button(d, dx + dw - 170, by + 50, 140, 36, "▼ Move Down")
    button(d, dx + dw - 220, dy + dh - 50, 90, 30, "Cancel")
    button(d, dx + dw - 120, dy + dh - 50, 96, 30, "OK", active=True)
    save(img, "16-tab-order.png")


def shot_column_editor(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Columns of tableView")
    d.rectangle([0, 0, W, H], fill=(225, 228, 234))
    dx, dy, dw, dh = 200, 120, 880, 560
    macos_titlebar(d, dx, dy, dw, 30, "Columns of tableView")
    d.rectangle([dx, dy + 30, dx + dw, dy + dh], fill=(255, 255, 255), outline=FRAME)
    d.text((dx + 16, dy + 50), "Each row is one column. The Property column is the bean property name used by PropertyValueFactory.",
           font=F_BODY, fill=INK_DIM)
    # Table header
    hx, hy = dx + 16, dy + 90
    headers = ["Header", "fx:id", "Property", "Pref Width", "Sortable", "Resizable"]
    cw = [180, 140, 160, 80, 80, 80]
    d.rectangle([hx, hy, hx + dw - 160, hy + 28], fill=(240, 240, 244))
    x = hx
    for h, w in zip(headers, cw):
        d.text((x + 8, hy + 6), h, font=F_CAP, fill=INK)
        x += w
    rows = [
        ("Name", "nameCol", "name", "180", "✓", "✓"),
        ("Age", "ageCol", "age", "60", "✓", "✓"),
        ("Birthday", "birthdayCol", "birthday", "120", "✓", "✓"),
        ("Email", "emailCol", "email", "200", "✗", "✓"),
    ]
    for i, row in enumerate(rows):
        ry = hy + 30 + i * 32
        d.line([(hx, ry + 30), (hx + dw - 160, ry + 30)], fill=SUBTLE)
        x = hx
        for v, w in zip(row, cw):
            d.text((x + 8, ry + 6), v, font=F_BODY, fill=INK)
            x += w
    # Action buttons
    by = dy + 90
    button(d, dx + dw - 130, by + 0,   110, 32, "+ Add")
    button(d, dx + dw - 130, by + 40,  110, 32, "Remove")
    button(d, dx + dw - 130, by + 80,  110, 32, "▲ Up")
    button(d, dx + dw - 130, by + 120, 110, 32, "▼ Down")
    button(d, dx + dw - 220, dy + dh - 50, 90, 30, "Cancel")
    button(d, dx + dw - 120, dy + dh - 50, 96, 30, "OK", active=True)
    save(img, "17-column-editor.png")


def shot_running_app(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Visual Java sample — Hello.fxml")
    # Just the form rendered at native size, centred on a desktop background
    d.rectangle([0, 30, W, H], fill=(220, 230, 245))
    form = r.render(LOGIN_FXML, 380, 220)
    # Decorate as a window
    wx, wy = (W - form.width - 80) // 2, 200
    macos_titlebar(d, wx, wy, form.width + 80, 30, "Sign In")
    d.rectangle([wx, wy + 30, wx + form.width + 80, wy + form.height + 60], fill=(255, 255, 255), outline=FRAME)
    img.paste(form, (wx + 40, wy + 50))
    save(img, "18-running-app.png")


def shot_welcome(r):
    img, d = new_canvas()
    macos_titlebar(d, 0, 0, W, 30, "Welcome to IntelliJ IDEA")
    d.rectangle([0, 30, W, H], fill=(255, 255, 255))
    d.text((W // 2 - 130, 90), "Visual Java", font=_font(40, bold=True), fill=INK)
    d.text((W // 2 - 200, 150), "Recent Projects", font=F_HEAD, fill=INK_DIM)
    projects = ["java_forms",  "sample-fxml", "hello-fxml", "ContactApp"]
    for i, p in enumerate(projects):
        py = 200 + i * 80
        d.rectangle([100, py, W - 100, py + 60], fill=(248, 248, 252), outline=FRAME)
        d.text((130, py + 12), p, font=F_HEAD, fill=INK)
        d.text((130, py + 32), f"~/code/{p}", font=F_SMALL, fill=INK_DIM)
    save(img, "00-welcome.png")


def main():
    r = Renderer()
    r.start()
    try:
        print(f"==> Generating mockups to {IMG_DIR}")
        for fn in [
            shot_welcome, shot_overview, shot_new_form_dialog, shot_palette,
            shot_form_outline, shot_properties, shot_vj_forms,
            shot_designer_toolbar, shot_selection_handles, shot_smart_guides,
            shot_ruler_guides, shot_rightclick, shot_generated_handler,
            shot_wireup, shot_pojo_binding, shot_menu_editor, shot_tab_order,
            shot_column_editor, shot_running_app,
        ]:
            try:
                fn(r)
            except Exception as e:
                print(f"  !! {fn.__name__}: {e}")
        print("==> Done.")
    finally:
        r.stop()


if __name__ == "__main__":
    sys.exit(main())
