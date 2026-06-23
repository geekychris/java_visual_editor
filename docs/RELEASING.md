# Releasing Visual Java

How the plugin gets built and published. The [Build plugin
workflow](../.github/workflows/build.yml) does the heavy lifting; this doc
explains the trigger model, the version-bump dance, and the recovery path
when an asset doesn't end up where you wanted it.

- [What the workflow does](#what-the-workflow-does)
- [Cutting a release](#cutting-a-release)
  - [Path A — tag from the command line (recommended)](#path-a--tag-from-the-command-line-recommended)
  - [Path B — create a Release via the GitHub UI](#path-b--create-a-release-via-the-github-ui)
- [Version numbering](#version-numbering)
- [What users see](#what-users-see)
- [Recovery: attach a zip to a Release that's missing it](#recovery-attach-a-zip-to-a-release-thats-missing-it)
- [Local installation (skip the release process entirely)](#local-installation-skip-the-release-process-entirely)
- [Common gotchas](#common-gotchas)

---

## What the workflow does

`.github/workflows/build.yml` runs on three triggers:

| Trigger | When | What it does |
|---|---|---|
| `push` to `main` or any tag | Every commit / tag push | Build `:plugin:buildPlugin`, upload the zip as a 30-day Actions artifact. On tags: also attach the zip to a Release. |
| `pull_request` to `main` | Each push to a PR branch | Build + upload artifact (read-only Gradle cache). |
| `release` (`published` / `created`) | Creating a Release via the GitHub UI | Build, attach zip to that Release. |
| `workflow_dispatch` | Manual run via the Actions UI | Build + upload artifact. |

Concurrency is grouped by ref with `cancel-in-progress: true` — stacking
pushes to the same branch only keeps the most recent build alive.

Asset path: `plugin/build/distributions/plugin-<version>.zip` (the
`<version>` is whatever `pluginVersion` is in `plugin/gradle.properties` at
build time).

---

## Cutting a release

### Path A — tag from the command line (recommended)

```bash
# 1. Bump the version in plugin/gradle.properties (see "Version numbering" below).
$EDITOR plugin/gradle.properties
git add plugin/gradle.properties
git commit -m "release v0.2.0"

# 2. Tag and push.
git tag v0.2.0
git push origin main
git push origin v0.2.0   # or: git push --tags
```

The push of the tag fires the workflow. About 4–6 minutes later:

- A Release named `v0.2.0` exists at
  `https://github.com/geekychris/java_visual_editor/releases/tag/v0.2.0`.
- `plugin-0.2.0.zip` is attached.
- Release notes are auto-generated from commits since the previous tag
  (`generate_release_notes: true` on `softprops/action-gh-release@v2`).

Tag names are unconstrained — `v0.2.0`, `release`, `nightly-2026-06-23`
all work. Stick to semver-style `v*` tags for the auto-generated notes to
read naturally.

### Path B — create a Release via the GitHub UI

If you'd rather use the GitHub UI:

1. Bump `plugin/gradle.properties` and commit/push (so the workflow builds
   the right version).
2. Web UI → **Releases → Draft a new release**.
3. Pick or create a tag (e.g. `v0.2.0`).
4. Click **Publish release**.

This fires the `release` trigger on the workflow, which builds and attaches
the zip to your new Release.

> **Why the dance:** creating a Release through the UI does **not** fire
> the `push` event, only the `release` event. The workflow subscribes to
> both, so either path works — but a Release without a corresponding tag
> push won't have ever built the zip until the `release` trigger runs.

---

## Version numbering

The plugin's version is set in **two places** that must agree:

- `plugin/gradle.properties` — `pluginVersion=0.2.0` (drives the zip
  filename and the manifest the IntelliJ plugin marketplace reads).
- The git tag — convention is `v<version>`, so `pluginVersion=0.2.0` ↔
  tag `v0.2.0`.

The tag also controls release notes auto-generation. `v*` tags compare
nicely; arbitrary names work but the auto-notes can look weird.

We don't enforce the match in CI today (a follow-up). For now, check before
tagging:

```bash
grep pluginVersion plugin/gradle.properties
git tag --list | tail -5
```

`SNAPSHOT` suffixes (`0.2.0-SNAPSHOT`) are fine for development builds — the
zip is uploaded as a 30-day Actions artifact on every push and can be
downloaded from the workflow run page if you need a one-off.

---

## What users see

Once a Release is published with the zip attached, users install it via:

1. Download `plugin-<version>.zip` from the
   [Releases page](https://github.com/geekychris/java_visual_editor/releases).
2. IntelliJ → **Settings → Plugins → ⚙ → Install Plugin from Disk…** → pick
   the zip.
3. Restart IntelliJ when prompted.
4. **Disable the bundled JavaFX plugin** (Settings → Plugins → search
   "JavaFX" → uncheck) — Scene Builder hooks freeze the EDT on FXML undo.
   See [USER_GUIDE.md → Installation](USER_GUIDE.md#installation).

---

## Recovery: attach a zip to a Release that's missing it

This happens when a Release was created from the UI before the workflow's
`release` trigger existed, or when CI failed to publish for some reason.

Build locally and attach via `gh`:

```bash
./gradlew :plugin:buildPlugin
gh release upload <tag> plugin/build/distributions/plugin-*.zip \
    --repo geekychris/java_visual_editor
```

To overwrite an existing asset (e.g. a stale upload), add `--clobber`:

```bash
gh release upload <tag> plugin/build/distributions/plugin-*.zip \
    --repo geekychris/java_visual_editor --clobber
```

Verify:

```bash
gh release view <tag> --repo geekychris/java_visual_editor
```

The `asset:` line in the output should list the zip.

You can also re-trigger the workflow from the Actions UI: pick the **Build
plugin** workflow → **Run workflow** → pick a branch/tag → it'll build and
(if it's a tag) attach the zip.

---

## Local installation (skip the release process entirely)

For day-to-day plugin development you don't need a Release at all:

```bash
scripts/dev.sh           # launches a sandbox IntelliJ with the plugin preloaded
```

Or to install into your real IntelliJ from a local build:

```bash
scripts/install.sh       # builds the zip + prints the install steps
```

See [README.md → Install](../README.md#install) and [USER_GUIDE.md →
Installation](USER_GUIDE.md#installation).

---

## Common gotchas

- **Tag pushed, workflow ran, no zip on the Release.** Almost always a
  `v*`-only filter on the publish step (we fixed this in 1ac6383). Inspect
  the latest workflow run's `Attach plugin zip to GitHub Release` step — if
  it says *"This step was skipped due to a conditional"*, your tag didn't
  match. The current workflow accepts any tag.
- **Release created via UI, no zip.** UI-created Releases fire the
  `release` event, not `push`. The workflow now subscribes to both. If
  you're on an old fork without that fix, use the
  [recovery path](#recovery-attach-a-zip-to-a-release-thats-missing-it).
- **Zip filename includes `-SNAPSHOT`.** Bump `pluginVersion` in
  `plugin/gradle.properties` to strip it.
- **Multi-MB Actions cache hits the GitHub quota.** PR builds use
  `cache-read-only: true` (no writes), so only main-branch builds populate
  the cache. If you ever need to clear it, Settings → Actions → Caches.
- **Workflow can't write to Releases.** Check the job has
  `permissions: contents: write` (already set in build.yml). If a fork's
  default-token permissions are stricter, the maintainer needs to flip
  Settings → Actions → "Workflow permissions" to read-write.
