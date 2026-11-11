package com.example.app.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.layout.HeaderBar;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import com.example.app.config.Settings;
import com.example.app.config.ThemeChoice;
import org.jspecify.annotations.Nullable;

/**
 * The Caret &amp; Ink pair, Pauta's family palette: {@code editora-light.css} and
 * {@code editora-dark.css}, AtlantaFX themes copied unchanged from Editora. Switchable at run time
 * without a restart; {@link ThemeChoice#SYSTEM} follows the operating system and changes with it. App
 * CSS names colors through the theme's {@code -color-*} roles (as {@code -state-*} tokens), never hex
 * literals, so it follows too.
 */
public final class Themes {

    /** The light control theme, a user-agent stylesheet. */
    static final String LIGHT_THEME = "editora-light.css";

    /** The dark control theme, a user-agent stylesheet. */
    static final String DARK_THEME = "editora-dark.css";

    /** The high-contrast sheet: the dark theme's roles set to black, white and saturated states (ACC-09). */
    static final String HIGH_CONTRAST = "high-contrast.css";

    /** Windows' own high-contrast preference (ACC-09). */
    static final String WINDOWS_HIGH_CONTRAST = "Windows.SPI.HighContrast";

    /** Whether the OS asks for high contrast now: the theme follows it, and goes back when it ends. */
    private static boolean systemHighContrast;

    /** The UI font sheet: Inter on {@code .root}, attached to every window's scene. */
    static final String UI_FONT = "ui-font.css";

    private static final String WINDOW_HOOKED = Themes.class.getName() + ".uiFont";

    /** The sheets the UI zoom scales: the control themes and the app's own (WIN-12). */
    private static final List<String> ZOOMED = List.of(LIGHT_THEME, DARK_THEME, "app.css");

    /** Each scaled sheet, by name and percent, as a data: URL. Built off the FX thread ({@link #prepareZoom}). */
    private static final Map<String, String> SCALED = new ConcurrentHashMap<>();

    private static ThemeChoice choice = ThemeChoice.DEFAULT;
    private static int zoom = Settings.DEFAULT_UI_ZOOM;
    private static boolean listening;
    private static boolean uiFontInstalled;

    private Themes() {}

    /** Applies a theme to the whole app and every open window. Call on the FX thread. */
    public static void apply(ThemeChoice newChoice) {
        choice = newChoice;
        if (!listening) {
            listening = true;
            Platform.getPreferences().colorSchemeProperty().addListener((obs, old, scheme) -> {
                if (choice == ThemeChoice.SYSTEM) apply(ThemeChoice.SYSTEM);
            });
            // ACC-09: Windows' high-contrast mode switches the app to its high-contrast theme, and back.
            systemHighContrast = Boolean.TRUE.equals(Platform.getPreferences().get(WINDOWS_HIGH_CONTRAST));
            Platform.getPreferences().addListener((javafx.collections.MapChangeListener<String, Object>) change -> {
                if (!WINDOWS_HIGH_CONTRAST.equals(change.getKey())) return;
                systemHighContrast = Boolean.TRUE.equals(change.getValueAdded());
                apply(choice);
            });
        }
        boolean dark = isDark();
        boolean high = effective() == ThemeChoice.HIGH_CONTRAST;
        Application.setUserAgentStylesheet(zoomed(dark ? DARK_THEME : LIGHT_THEME, zoom));
        for (Window window : Window.getWindows()) {
            Scene scene = window.getScene();
            if (scene != null) {
                scene.getPreferences().setColorScheme(dark ? ColorScheme.DARK : ColorScheme.LIGHT);
                highContrast(scene, high);
            }
            if (window instanceof Stage stage) titleButtons(stage);
        }
    }

    /** The theme in effect: the high-contrast one while the OS asks for it, else the one chosen. Pure. */
    static ThemeChoice effective(ThemeChoice choice, boolean systemHighContrast) {
        return systemHighContrast ? ThemeChoice.HIGH_CONTRAST : choice;
    }

    static ThemeChoice effective() {
        return effective(choice, systemHighContrast);
    }

    /** Puts the high-contrast sheet on {@code scene}, or takes it off. */
    private static void highContrast(Scene scene, boolean on) {
        String url = sheet(HIGH_CONTRAST);
        if (on && !scene.getStylesheets().contains(url)) scene.getStylesheets().add(url);
        if (!on) scene.getStylesheets().remove(url);
    }

    /**
     * Tells a window that draws its own title bar ({@code StageStyle.EXTENDED}) whether the system's
     * window buttons sit on light or dark: without it they keep the OS's colours and can vanish on a
     * dark title bar.
     */
    public static void titleButtons(Stage stage) {
        if (stage.getStyle() == StageStyle.EXTENDED) {
            HeaderBar.setSystemColorScheme(stage, isDark() ? ColorScheme.DARK : ColorScheme.LIGHT);
        }
    }

    public static ThemeChoice choice() {
        return choice;
    }

    /** Whether the theme in effect is dark. */
    public static boolean isDark() {
        return isDark(effective(), Platform.getPreferences().getColorScheme());
    }

    static boolean isDark(ThemeChoice choice, ColorScheme system) {
        return choice == ThemeChoice.DARK
                || choice == ThemeChoice.HIGH_CONTRAST
                || (choice == ThemeChoice.SYSTEM && system == ColorScheme.DARK);
    }

    /**
     * Styles a new scene: the UI font and the app stylesheet, and the theme's light or dark so the
     * window title bar matches. Every scene and dialog goes through here: a dialog is a scene of its
     * own and inherits neither.
     */
    public static Scene style(Scene scene) {
        // The UI font is attached by installUiFont once the window shows; adding it here too means a
        // scene measured before it is shown is measured in the font it is drawn in.
        addSheet(scene, UI_FONT);
        highContrast(scene, effective() == ThemeChoice.HIGH_CONTRAST);
        String app = zoomed("app.css", zoom);
        if (!scene.getStylesheets().contains(app)) scene.getStylesheets().add(app);
        scene.getPreferences().setColorScheme(isDark() ? ColorScheme.DARK : ColorScheme.LIGHT);
        return scene;
    }

    /**
     * Renders every window in Inter. Dialogs, popups, context menus and tooltips each live in a scene of
     * their own that does not share {@code app.css}, so a listener on the live window list adds
     * {@code ui-font.css} to each window's scene, and to a new scene when a window's scene changes. A
     * scene sheet beats the user-agent theme and is re-applied cleanly when the theme switches. Call
     * once at start-up, on the FX thread; later calls do nothing.
     */
    public static void installUiFont() {
        if (uiFontInstalled) return;
        uiFontInstalled = true;
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window window : change.getAddedSubList()) hookUiFont(window);
            }
        });
        for (Window window : Window.getWindows()) hookUiFont(window);
    }

    /** Attaches the UI font to a window's scene now and whenever its scene changes; once per window. */
    static void hookUiFont(Window window) {
        if (window.getProperties().putIfAbsent(WINDOW_HOOKED, Boolean.TRUE) != null) return;
        addUiFont(window.getScene());
        window.sceneProperty().addListener((obs, old, scene) -> addUiFont(scene));
    }

    private static void addUiFont(@Nullable Scene scene) {
        if (scene == null) return;
        addSheet(scene, UI_FONT);
        highContrast(scene, effective() == ThemeChoice.HIGH_CONTRAST); // popups, menus and tooltips too
    }

    private static void addSheet(Scene scene, String sheet) {
        String url = sheet(sheet);
        if (!scene.getStylesheets().contains(url)) scene.getStylesheets().add(url);
    }

    /** The UI zoom in effect, in percent. */
    public static int zoom() {
        return zoom;
    }

    /**
     * Reads and scales the sheets for {@code percent}: string work over the theme's few thousand lines, so
     * never on the FX thread (PERF-01). {@link #zoom(int)} then only swaps URLs.
     */
    public static void prepareZoom(int percent) {
        for (String name : ZOOMED) zoomed(name, percent);
    }

    /**
     * Applies the UI zoom to the whole app at once (WIN-12): the control theme, every open window's app
     * sheet, and the glyphs and sizes bound to {@link UiZoom#factor}. FX thread; call {@link #prepareZoom}
     * first, off it.
     */
    public static void zoom(int percent) {
        int next = Settings.clampUiZoom(percent);
        if (next == zoom) return;
        String oldApp = zoomed("app.css", zoom);
        zoom = next;
        String newApp = zoomed("app.css", zoom);
        UiZoom.setFactor(zoom);
        Application.setUserAgentStylesheet(zoomed(isDark() ? DARK_THEME : LIGHT_THEME, zoom));
        for (Window window : Window.getWindows()) {
            Scene scene = window.getScene();
            if (scene == null) continue;
            int at = scene.getStylesheets().indexOf(oldApp);
            if (at >= 0) scene.getStylesheets().set(at, newApp);
        }
    }

    /** One of the zoomed sheets at {@code percent}: the bundled file at 100%, a scaled copy otherwise. */
    static String zoomed(String name, int percent) {
        if (percent == Settings.DEFAULT_UI_ZOOM) return sheet(name);
        return SCALED.computeIfAbsent(name + "@" + percent, k -> UiZoom.dataUrl(UiZoom.scale(text(name), percent)));
    }

    /** A bundled stylesheet's text. */
    private static String text(String name) {
        try (InputStream in =
                Objects.requireNonNull(Themes.class.getResourceAsStream("/com/example/app/styles/" + name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The URL of one of the app's stylesheets. */
    static String sheet(String name) {
        return Objects.requireNonNull(Themes.class.getResource("/com/example/app/styles/" + name))
                .toExternalForm();
    }
}
