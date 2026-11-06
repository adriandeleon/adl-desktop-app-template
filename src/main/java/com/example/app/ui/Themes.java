package com.example.app.ui;

import java.util.Objects;

import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.HeaderBar;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.example.app.config.ThemeChoice;

/**
 * AtlantaFX light and dark, switchable at run time without a restart. {@link ThemeChoice#SYSTEM}
 * follows the operating system and changes with it. App CSS uses AtlantaFX's {@code -color-*} tokens,
 * never hex literals, so it follows too.
 */
public final class Themes {

    private static ThemeChoice choice = ThemeChoice.SYSTEM;
    private static boolean listening;

    private Themes() {}

    /** Applies a theme to the whole app and every open window. Call on the FX thread. */
    public static void apply(ThemeChoice newChoice) {
        choice = newChoice;
        if (!listening) {
            listening = true;
            Platform.getPreferences().colorSchemeProperty().addListener((obs, old, scheme) -> {
                if (choice == ThemeChoice.SYSTEM) apply(ThemeChoice.SYSTEM);
            });
        }
        boolean dark = isDark();
        Application.setUserAgentStylesheet(
                dark ? new PrimerDark().getUserAgentStylesheet() : new PrimerLight().getUserAgentStylesheet());
        for (Window window : Window.getWindows()) {
            Scene scene = window.getScene();
            if (scene != null) scene.getPreferences().setColorScheme(dark ? ColorScheme.DARK : ColorScheme.LIGHT);
            if (window instanceof Stage stage) titleButtons(stage);
        }
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
        return isDark(choice, Platform.getPreferences().getColorScheme());
    }

    static boolean isDark(ThemeChoice choice, ColorScheme system) {
        return choice == ThemeChoice.DARK || (choice == ThemeChoice.SYSTEM && system == ColorScheme.DARK);
    }

    /**
     * Styles a new scene: the UI font and the app stylesheet, and the theme's light or dark so the
     * window title bar matches. Every scene and dialog goes through here: a dialog is a scene of its
     * own and inherits neither.
     */
    public static Scene style(Scene scene) {
        for (String sheet : new String[] {"ui-font.css", "app.css"}) {
            String url = Objects.requireNonNull(Themes.class.getResource("/com/example/app/styles/" + sheet))
                    .toExternalForm();
            if (!scene.getStylesheets().contains(url)) scene.getStylesheets().add(url);
        }
        scene.getPreferences().setColorScheme(isDark() ? ColorScheme.DARK : ColorScheme.LIGHT);
        return scene;
    }
}
