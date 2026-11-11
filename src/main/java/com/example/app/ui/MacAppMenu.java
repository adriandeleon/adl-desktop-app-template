package com.example.app.ui;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;

import com.example.app.Os;
import com.sun.glass.ui.Application;
import com.sun.glass.ui.Menu;
import com.sun.glass.ui.MenuItem;
import org.jspecify.annotations.Nullable;

/**
 * About and Settings in macOS's application menu, the one named after the app, where every Mac app keeps
 * them (ABOUT-01, WIN-15). JavaFX builds that menu itself (Hide, Hide Others, Show All, Quit) and offers no
 * way to add to it: a {@code MenuBar} on the system menu bar only gets the menus after it. Glass's
 * {@code MacApplication.getAppleMenu} hands out the menu, and Glass's own menu items go into it. After
 * Mirada's {@code os/MacAppMenu}.
 *
 * <p>{@code MacApplication} is in {@code com.sun.glass.ui.mac}, which exists only in macOS's JavaFX, so it is
 * reached by reflection, and only where that package is exported to the app: the macOS launchers pass
 * {@code --add-exports javafx.graphics/com.sun.glass.ui.mac=com.example.app} (the pom's
 * {@code glass.mac.option}). Without it the menu stays JavaFX's, and both commands are still in the Help
 * and File menus and the palette.
 *
 * <p>The Settings item carries no key equivalent of its own: its title shows the chord the keymap binds to
 * {@code app.settings}, as the system menu bar's rows do (MENU-02), and {@link #refresh} writes it again
 * after a keymap change.
 */
final class MacAppMenu {

    private static final Logger LOG = Logger.getLogger(MacAppMenu.class.getName());

    private MacAppMenu() {}

    /** The Settings item, once installed, and the title it shows: rewritten after a keymap change. */
    private static @Nullable Object settingsItem;

    private static @Nullable Supplier<String> settingsTitle;

    /**
     * Puts About and Settings at the top of the application menu, on the FX thread; each runs its command on
     * the FX thread. Returns what it did, or why it did nothing.
     */
    static String install(String aboutTitle, Runnable about, Supplier<String> settingsTitle, Runnable settings) {
        if (!Os.isMac()) return "none (not macOS)";
        Module ours = MacAppMenu.class.getModule();
        Module javafx = Platform.class.getModule();
        if (!javafx.isExported("com.sun.glass.ui", ours) || !javafx.isExported("com.sun.glass.ui.mac", ours)) {
            LOG.warning("No application menu items: com.sun.glass.ui.mac is not exported to the app");
            return "none (com.sun.glass.ui.mac not exported)";
        }
        MacAppMenu.settingsTitle = settingsTitle;
        return Glass.install(aboutTitle, about, settingsTitle.get(), settings);
    }

    /** Writes the Settings item's title again: its chord follows the keymap (WIN-15). */
    static void refresh() {
        Object item = settingsItem;
        Supplier<String> title = settingsTitle;
        if (item != null && title != null) Glass.retitle(item, title.get());
    }

    /** Everything that names a Glass class, apart, so that loading {@link MacAppMenu} loads none. */
    static final class Glass {

        private Glass() {}

        static String install(String aboutTitle, Runnable about, String settingsTitle, Runnable settings) {
            Application glass = Application.GetApplication();
            if (glass == null) return "none (no glass application)";
            Object found;
            try {
                found = glass.getClass().getMethod("getAppleMenu").invoke(glass);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.log(Level.WARNING, "No application menu items", e);
                return "none (" + e + ")";
            }
            if (!(found instanceof Menu menu)) return "none (no application menu)";
            menu.insert(glass.createMenuItem(aboutTitle, callback(about)), 0);
            menu.insert(MenuItem.Separator, 1);
            MenuItem settingsItem = glass.createMenuItem(settingsTitle, callback(settings)); // no key equivalent
            menu.insert(settingsItem, 2);
            menu.insert(MenuItem.Separator, 3);
            MacAppMenu.settingsItem = settingsItem;
            return "about, settings";
        }

        static void retitle(Object item, String title) {
            if (item instanceof MenuItem menuItem) menuItem.setTitle(title);
        }

        private static MenuItem.Callback callback(Runnable action) {
            return new MenuItem.Callback() {
                @Override
                public void action() {
                    action.run();
                }

                @Override
                public void validate() {}
            };
        }
    }
}
