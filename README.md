# Visual Java

[![Build plugin](https://github.com/geekychris/java_visual_editor/actions/workflows/build.yml/badge.svg)](https://github.com/geekychris/java_visual_editor/actions/workflows/build.yml)

A VB6-style visual UI designer for JavaFX, delivered as an IntelliJ plugin.

You design a form by dragging widgets onto a canvas, edit their properties in a
sidebar, double-click a button to write its event handler, and run a real
JavaFX app on F5. The designer renders the actual scene graph — what you see
is what the running app shows.

> **Status:** active development. Things mostly work but edges are rough — see
> [Known limitations](docs/USER_GUIDE.md#known-limitations).

---

## In one minute

| | |
|---|---|
| **47 widgets** in a categorised palette | Containers, Controls, Lists & Tables, Display (incl. Charts, WebView, HTMLEditor, MediaView) |
| Drag-and-drop | onto the form or nested into a container — TabPane/SplitPane/Accordion/ScrollPane/BorderPane all accept drops with the right slot semantics |
| Click to select | + Cmd-click multi-select, Alt-click → parent, Arrow-key nudge |
| Properties inspector | live edits round-trip to FXML and back; CSS classes as chips, ImageView image with a file picker |
| Form Outline tree | pick anything by name (toggle wrapper-node visibility) |
| Alignment helpers | rulers, grid, snap-to-grid, smart guides (incl. multi-drag), Photoshop-style ruler-drag guides (persisted to a `.design.json` sidecar), focus-order overlay |
| Align toolbar | Left/Center/Right/Top/Middle/Bottom + Distribute H/V |
| Double-click events | jumps to a generated handler pre-seeded with a usable cheat-sheet |
| **21 wire-up recipes** | Close Window, Toggle Visibility, Required-Fields Validation, Confirm Before Action, MessageBox, InputBox, Background Task, Timer, … |
| **Component Help** | per-widget summary, FXML snippet, controller-code snippet, Oracle Javadoc links — hover the palette or open the tool window |
| **Tool windows** | Palette · Form Outline · VJ Forms · Properties · Resources · i18n editor · A11y inspector · Component Help |
| **Visual editors** | Menu Editor (round-trips), Tab Order (drag-drop + cross-parent reparenting), TableView Column Editor, POJO Binding |
| **Packaging** | `jpackage` wizard produces `.app` / `.dmg` / `.exe` / `.msi` / `.deb` / `.rpm` |
| **Web export (v2 preview)** | FXML → Thymeleaf HTML + Spring controller stub |
| **Custom controls** | register your own classes into the palette per-project |
| Sidecar JavaFX renderer | the designer canvas IS the running app, rendered out-of-process; populates TableView with mock rows + applies project CSS live |

---

## Install

### From a GitHub Release

Every git tag matching `v*` builds the plugin via the
[Build plugin](.github/workflows/build.yml) workflow and publishes a release
with the `.zip` attached. In IntelliJ:

1. Download the `visual-java-*.zip` from
   [Releases](https://github.com/geekychris/java_visual_editor/releases).
2. Settings → Plugins → ⚙ → **Install Plugin from Disk…** → pick the zip.
3. Disable the bundled "JavaFX" plugin (Settings → Plugins → search JavaFX → uncheck) — its Scene Builder hooks lock the EDT during FXML undo.
4. Restart IntelliJ.

### From source

```bash
scripts/install.sh
```

Builds the plugin .zip and prints the install steps for your real IntelliJ.
The script also tells you to disable the bundled "JavaFX" plugin.

If you'd rather just play in a sandbox without touching your real IDE:

```bash
scripts/dev.sh
```

Launches a separate sandbox IntelliJ Community 2025.1 instance with the
plugin preloaded. Closing the sandbox window doesn't affect your real IDE.

---

## Quick start

1. **New form:** right-click any directory in the Project tree → **New → FXML
   Form** → name it → opens in the Designer tab.
2. **Drag widgets** from the Palette (left tool window) onto the canvas. Drop
   a Button on top of an HBox and the Button nests inside.
3. **Edit properties** in the Properties tool window (right). Type `300` into
   `prefWidth`, press Tab → designer re-renders within ~250ms.
4. **Wire an event:** double-click any widget. A handler method is generated
   with a commented cheat-sheet you can uncomment and fill in.
5. **Wire a pattern:** click **Wire-Up…** in the designer toolbar → pick a
   recipe (Close Window, Toggle Visibility, Required-Fields Validation, etc.)
   → assign components → OK. The controller gets the binding statements and
   FXML wiring in one undo step.
6. **Run** `./gradlew run` in the form's project (or use the IntelliJ run
   config if `Launcher.main` is set up — see
   [USER_GUIDE → Running](docs/USER_GUIDE.md#running-the-app)).

---

## Repo layout

```
/                          repo root
├── plugin/                IntelliJ plugin (Kotlin)
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/visualjava/...
├── preview-renderer/      Sidecar JavaFX renderer (Java)
│   ├── build.gradle.kts
│   └── src/main/java/com/visualjava/preview/PreviewRenderer.java
├── sample-fxml/           Demo app for testing the plugin
│   └── src/main/java/com/example/{HelloApp,Launcher}.java
├── scripts/
│   ├── build.sh           Build the installable .zip
│   ├── install.sh         Build + print install steps
│   └── dev.sh             Kill + relaunch sandbox IDE
├── docs/
│   ├── USER_GUIDE.md      Full user guide
│   └── DESIGN.md          Architecture and design notes
└── README.md              You are here
```

---

## Docs

- **[User guide](docs/USER_GUIDE.md)** — installation, every feature, recipes,
  known issues.
- **[Design](docs/DESIGN.md)** — architecture (with mermaid), the sidecar
  renderer protocol, PSI model, extension points, how to add a recipe or
  widget.
- **[Releasing](docs/RELEASING.md)** — how to cut a release, what the CI
  workflow does, version-bump dance, recovery when a Release ends up
  without its zip attached.

The user guide ships with **labelled placeholder screenshots** so it renders
correctly out of the box. Replace them with real captures:

```bash
scripts/capture-screenshots.sh    # macOS — walks you through 18 captures
```

Or re-generate the placeholders if you ever delete them:

```bash
scripts/generate-placeholders.py
```
