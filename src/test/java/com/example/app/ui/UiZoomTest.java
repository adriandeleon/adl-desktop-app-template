package com.example.app.ui;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.example.app.config.Settings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WIN-12: the UI zoom's steps, its range, and the stylesheet it scales. */
class UiZoomTest {

    @Test
    void stepsStayInTheRange() {
        assertEquals(110, UiZoom.in(100));
        assertEquals(90, UiZoom.out(100));
        assertEquals(Settings.MAX_UI_ZOOM, UiZoom.in(Settings.MAX_UI_ZOOM));
        assertEquals(Settings.MIN_UI_ZOOM, UiZoom.out(Settings.MIN_UI_ZOOM));
        assertEquals(Settings.MAX_UI_ZOOM, Settings.clampUiZoom(900));
        assertEquals(Settings.MIN_UI_ZOOM, Settings.clampUiZoom(-5));
    }

    @Test
    void everyPixelLengthIsScaledAndNothingElse() {
        String css = ".root { -fx-font-size: 14px; } .a { -fx-padding: 4px 8.5px; -fx-border-width: 1; "
                + "-fx-font-size: 1.2em; -fx-background-color: #ababab; } .b-2px { -fx-min-width: 10px; }";
        String scaled = UiZoom.scale(css, 150);
        assertTrue(scaled.contains("-fx-font-size: 21px;"), scaled);
        assertTrue(scaled.contains("-fx-padding: 6px 12.75px;"), scaled);
        assertTrue(scaled.contains("-fx-border-width: 1;"), "a length with no unit is left alone");
        assertTrue(scaled.contains("1.2em"), "an em follows the root size already");
        assertTrue(scaled.contains("#ababab"));
        assertTrue(scaled.contains(".b-2px"), "a selector is never scaled");
        assertTrue(scaled.contains("-fx-min-width: 15px;"), scaled);
        assertEquals(css, UiZoom.scale(css, Settings.DEFAULT_UI_ZOOM));
    }

    @Test
    void theSheetTravelsAsADataUrl() {
        String url = UiZoom.dataUrl(".a { -fx-font-size: 9px; }");
        assertTrue(url.startsWith("data:text/css;base64,"));
        assertEquals(
                ".a { -fx-font-size: 9px; }",
                new String(Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1)), StandardCharsets.UTF_8));
    }

    @Test
    void anOutOfRangeZoomInTheFileIsClampedWhenRead() {
        Settings s = new Settings();
        s.uiZoom = 1000;
        assertEquals(Settings.MAX_UI_ZOOM, s.clamped().uiZoom);
        s.uiZoom = 0;
        assertEquals(Settings.MIN_UI_ZOOM, s.clamped().uiZoom);
    }
}
