package com.example.app.ui;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;

import com.example.app.config.Settings;

/**
 * The UI zoom (WIN-12): one setting, {@link Settings#uiZoom}, scales the whole chrome. The stylesheets are
 * written at 100% in pixels; at any other zoom {@link Themes} gives every window a copy with each pixel size
 * multiplied ({@link #scale}), the root font size among them, so every font follows one root size and the
 * menus' 14 and 11 px follow too (MENU-16). The few sizes the code fixes (a glyph's box, a dialog's width)
 * are bound to {@link #factor} instead of written as plain pixels.
 */
public final class UiZoom {

    /** One step of Zoom In or Zoom Out, in percent. */
    public static final int STEP = 10;

    private static final Pattern PX = Pattern.compile("(?<![\\w.-])(-?\\d*\\.?\\d+)px\\b");

    private static final ReadOnlyDoubleWrapper FACTOR = new ReadOnlyDoubleWrapper(1);

    private UiZoom() {}

    /** The zoom as a factor, 1 at 100%. FX thread. */
    public static ReadOnlyDoubleProperty factor() {
        return FACTOR.getReadOnlyProperty();
    }

    static void setFactor(int percent) {
        FACTOR.set(Settings.clampUiZoom(percent) / 100.0);
    }

    /** One step larger, up to the largest. */
    public static int in(int percent) {
        return Settings.clampUiZoom(percent + STEP);
    }

    /** One step smaller, down to the smallest. */
    public static int out(int percent) {
        return Settings.clampUiZoom(percent - STEP);
    }

    /** {@code px} at the current zoom, following it. */
    static DoubleBinding times(double px) {
        return FACTOR.multiply(px);
    }

    /** Binds {@code property} to {@code px} at the current zoom: a fixed size of the code, zoomed. */
    static void bind(DoubleProperty property, double px) {
        property.bind(times(px));
    }

    /**
     * A stylesheet at {@code percent}: every pixel length multiplied, rounded to a hundredth. An em or a
     * percentage is relative already and is left alone. Pure.
     */
    static String scale(String css, int percent) {
        if (percent == Settings.DEFAULT_UI_ZOOM) return css;
        double factor = percent / 100.0;
        Matcher m = PX.matcher(css);
        StringBuilder out = new StringBuilder(css.length() + 256);
        while (m.find()) {
            double scaled = Math.round(Double.parseDouble(m.group(1)) * factor * 100) / 100.0;
            String number = scaled == Math.rint(scaled)
                    ? Long.toString((long) scaled)
                    : String.format(Locale.ROOT, "%s", scaled);
            m.appendReplacement(out, Matcher.quoteReplacement(number + "px"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** A stylesheet as a {@code data:} URL, which a scene and the user-agent sheet both take. */
    static String dataUrl(String css) {
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }
}
