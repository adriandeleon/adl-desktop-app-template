# Gotchas

Each of these cost one of the family's apps real time.

- **`./mvnw clean -Pdist package`, always with `clean`.** An incremental compile can leave synthetic
  enum-switch classes out of `target/classes`; jlink then ships an app whose keyboard fails on the
  first keypress.
- **Run Spotless on JDK 25.** Palantir Java Format reaches into javac internals and breaks on newer
  JDKs. CI's Format job pins JDK 25.
- **`.mvn/jvm.config` is load-bearing.** Error Prone (and so NullAway) needs those `--add-exports`.
- **`java.awt.headless=true` is the first statement of `main`.** On macOS an initialised AWT
  contends with JavaFX for the AppKit run loop and hangs.
- **No `javafx.stage.Popup` for anything that takes keys.** On Windows a Popup does not reliably take
  OS keyboard focus and the whole app can stop receiving keystrokes. Use `OverlayHost`.
- **Fonts before stylesheets.** CSS that names a font not yet registered silently falls back to the
  system face; `Startup` loads fonts first.
- **Every dialog is a new scene.** Without `Dialogs.styled` it has no app CSS. Inter reaches every
  window, popups and tooltips included, through the window-list listener (`Themes.installUiFont`).
- **Unix-domain socket paths are short** (about 104 bytes on macOS). A deep config folder gets its
  socket in the temp folder (`SingleInstance.socketPath`).
- **A `.app` launched from Finder has a stripped `PATH`.** Anything that runs an external tool (git
  for Settings Sync) must not assume a terminal's `PATH`.
- **jpackage refuses a version starting with 0 on macOS.** The pom gives jpackage `1.x` there and
  `scripts/fix-mac-bundle-version.sh` writes the real version back.
- **No `CustomMenuItem` under the macOS screen menu bar.** AppKit cannot draw a JavaFX node, and one
  such item makes JavaFX quietly refuse the screen bar for the whole menu bar. `AppMenus` uses plain
  items there, with the chord in the text.
- **A stage's style is fixed before it first shows.** The title-bar setting therefore applies on the
  next start, and an unsupported `EXTENDED` style silently becomes `DECORATED` (a header bar drawn under
  a native title bar), which is why `MainWindow.usesHeaderBar` asks the platform first.
- **AOT training needs a window on screen.** A headless training run archives almost nothing and still
  looks successful; `aot-train.sh` uses `xvfb-run` on a Linux machine without a display.
- **A test must never reach the real `gh`.** The developer's GitHub CLI is signed in, and "Create on
  GitHub" would make a real repository: the suite sets `exampleapp.gh` to a command that does not exist.
- **A key value with `{0}` is a MessageFormat pattern**: a single apostrophe in it swallows text.
- **`settings.json` is written off the FX thread.** `ConfigStore.update` returns before the file is on
  disk: call `store.flush()` before reading the file or opening a second store on the same folder.
- **Filters and searches wait for typing to pause** (`Debounce`). The headless tests set
  `Debounce.immediate`, so typing then reading needs no wait; a test of the pause turns it off.
- **A result from a background thread checks its generation inside the `runLater`.** A sync or update
  answer that arrives after `stop()` or `cancel()` must change nothing.
