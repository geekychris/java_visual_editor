# docs/images

Screenshots referenced by [USER_GUIDE.md](../USER_GUIDE.md). All are PNGs.

By default this directory holds **labelled placeholders** so the user guide
renders correctly even before real captures exist. Each placeholder image
shows its filename + a short caption + an instruction to run the capture
script.

## Replace placeholders with real screenshots

```bash
scripts/capture-screenshots.sh
```

Walks you through 18 captures step-by-step via macOS `screencapture`,
overwriting one placeholder per step.

## Re-generate the placeholders

If you ever delete the image files or want to reset to placeholders:

```bash
scripts/generate-placeholders.py
```

Writes 18 PNGs (1280×800, ~22KB each) using Python + PIL.

## Expected contents

| File | What it shows |
|---|---|
| `00-welcome.png` | IntelliJ Welcome / Recent Projects view — set context |
| `01-overview.png` | Designer with Palette (left), canvas (centre), Properties (right) — headline shot |
| `02-new-form-dialog.png` | File → New → FXML Form dialog, template list selected |
| `03-palette.png` | Palette tool window with categories (Containers / Controls / Lists & Tables / Display) expanded |
| `04-form-outline.png` | Bottom panel of Palette: Form Outline tree |
| `05-properties.png` | Properties tool window with several editable rows |
| `06-vj-forms.png` | "VJ Forms" project explorer with forms + controllers |
| `07-designer-toolbar.png` | Toolbar zoom: View toggles + Align cluster + Run + Tab Order + Menu + Wire-Up + Bind POJO + Wire All |
| `08-selection-handles.png` | A selected widget showing the red box + 4 corner handles |
| `09-smart-guides.png` | Mid-drag: pink dashed smart guide snapping aligned to another widget's edge |
| `10-ruler-guides.png` | Solid blue user-placed ruler guide visible on canvas |
| `11-rightclick-events.png` | Right-click popup on a widget: Add on… events, Rename fx:id, Delete |
| `12-generated-handler.png` | Generated method in controller showing the commented cheat-sheet body |
| `13-wireup-dialog.png` | Wire-Up Recipe dialog: recipes list + role assignments |
| `14-pojo-binding.png` | POJO Binding Wizard dialog with class FQN + property→widget rows |
| `15-menu-editor.png` | Menu Editor: tree of MenuBar → Menu → MenuItem + property fields |
| `16-tab-order.png` | Tab Order dialog: numbered list of focusable widgets + ▲▼ buttons |
| `17-column-editor.png` | TableView Column Editor: rows for each column + Add/Remove/▲/▼ |
| `18-running-app.png` | Final test: the actual JavaFX window running from the form |

## If you don't have macOS

`screencapture` is macOS-only. On Linux:

- GNOME: `gnome-screenshot -w` (window) or use GIMP's "Create from screen"
- KDE: `spectacle -a` (active window)

On Windows: Snipping Tool's "Window mode" or PowerShell + `Add-Type` (search
for a PowerShell screenshot one-liner).

Save each capture as `docs/images/<filename>.png` matching the table above.

## Re-capturing one image

Just run `screencapture -W docs/images/04-form-outline.png` (replacing the
filename) and click the window you want.
