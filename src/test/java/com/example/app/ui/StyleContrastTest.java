package com.example.app.ui;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.scene.paint.Color;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrast and color-token rules for the stylesheets (pure: reads the CSS text and does the WCAG
 * arithmetic; {@code Color.web} needs no toolkit). These pin what a code diff does not show: readable
 * text set in the ~3:1 "subtle" role, white ink on a light emphasis fill, a severity painted with a
 * role or a hex instead of its state token, a focus ring that vanishes on one surface.
 */
class StyleContrastTest {

    private static final String STYLES = "/com/example/app/styles/";
    private static final List<String> FLAGSHIP = List.of("editora-light", "editora-dark");
    private static final List<String> ROLES = List.of("accent", "success", "warning", "danger");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{3,8}\\b");
    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");

    /** A stylesheet's text, comments removed. */
    private static String read(String name) throws Exception {
        try (InputStream in = Objects.requireNonNull(StyleContrastTest.class.getResourceAsStream(STYLES + name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?s)/\\*.*?\\*/", "");
        }
    }

    /** A rule: its selector list and its declarations. */
    private record Rule(String selectors, String body) {
        boolean has(String selector) {
            for (String s : selectors.split(",")) {
                if (s.strip().replaceAll("\\s+", " ").equals(selector)) return true;
            }
            return false;
        }
    }

    private static List<Rule> rules(String css) {
        List<Rule> rules = new ArrayList<>();
        Matcher m = RULE.matcher(css);
        while (m.find()) rules.add(new Rule(m.group(1).strip(), m.group(2)));
        return rules;
    }

    /** Declarations of every rule whose selector list contains {@code selector} exactly. */
    private static String body(String css, String selector) {
        List<String> bodies =
                rules(css).stream().filter(r -> r.has(selector)).map(Rule::body).toList();
        assertFalse(bodies.isEmpty(), "no rule for " + selector);
        return String.join(";", bodies);
    }

    /** The {@code -color-*} tokens of a control theme's {@code .root} palette. */
    private static Map<String, String> palette(String theme) throws Exception {
        String css = read(theme + ".css");
        int start = css.indexOf(".root {");
        String block = css.substring(start, css.indexOf("\n}", start));
        Map<String, String> tokens = new HashMap<>();
        Matcher m = Pattern.compile("(-color-[a-z0-9-]+)\\s*:\\s*([^;]+);").matcher(block);
        while (m.find()) tokens.put(m.group(1), m.group(2).strip());
        return tokens;
    }

    /** A token's color, aliases resolved. */
    private static Color color(Map<String, String> palette, String token) {
        String value = Objects.requireNonNull(palette.get(token), token);
        while (palette.containsKey(value)) value = palette.get(value);
        return Color.web(value);
    }

    private static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    @Test
    void contrastIsTheWcagRatio() {
        assertEquals(21.0, contrast(Color.BLACK, Color.WHITE), 1e-9);
        assertEquals(1.0, contrast(Color.web("#777777"), Color.web("#777777")), 1e-9);
    }

    /** ICON-03, ICON-09: readable text is muted or stronger, and muted is 4.5:1 on every ground. */
    @Test
    void textThatMustBeReadUsesTheMutedRoleNotSubtle() throws Exception {
        String app = read("app.css");
        for (String selector : List.of(
                ".muted",
                ".hint",
                ".status-bar .status-item",
                ".palette-description",
                ".palette-hint",
                ".key-chip",
                ".message-log-list .time",
                ".settings-card-heading",
                ".setting-desc",
                ".note",
                ".about-version",
                ".about-tagline",
                ".about-env",
                ".header-title",
                ".menu-item-chord")) {
            assertFalse(body(app, selector).contains("-color-fg-subtle"), selector + " is set in the ~3:1 subtle role");
        }
        for (String theme : FLAGSHIP) {
            Map<String, String> p = palette(theme);
            for (String ground : List.of("-color-bg-default", "-color-bg-subtle", "-color-bg-overlay")) {
                double ratio = contrast(color(p, "-color-fg-muted"), color(p, ground));
                assertTrue(ratio >= 4.5, theme + ": muted on " + ground + " is " + ratio);
            }
        }
    }

    /** ICON-10: each role's tinted text on its own subtle fill and on both grounds. */
    @Test
    void tintedTextReachesAaOnItsOwnFillAndOnBothGrounds() throws Exception {
        for (String theme : FLAGSHIP) {
            Map<String, String> p = palette(theme);
            for (String role : ROLES) {
                Color fg = color(p, "-color-" + role + "-fg");
                for (String ground : List.of("-color-" + role + "-subtle", "-color-bg-subtle", "-color-bg-default")) {
                    double ratio = contrast(fg, color(p, ground));
                    assertTrue(ratio >= 4.5, theme + ": " + role + "-fg on " + ground + " is " + ratio);
                }
            }
        }
    }

    /** ICON-11: ink on an emphasis fill is the theme's on-emphasis color, and the rules use it. */
    @Test
    void inkOnAnEmphasisFillIsTheThemesOnEmphasisColor() throws Exception {
        for (String theme : FLAGSHIP) {
            Map<String, String> p = palette(theme);
            for (String role : ROLES) {
                double ratio = contrast(color(p, "-color-fg-emphasis"), color(p, "-color-" + role + "-emphasis"));
                assertTrue(ratio >= 4.5, theme + ": fg-emphasis on " + role + "-emphasis is " + ratio);
            }
        }
        String app = read("app.css");
        assertFalse(app.contains("white"), "white is a hex in disguise: use -color-fg-emphasis");
        List<String> filled = new ArrayList<>();
        for (Rule rule : rules(app)) {
            Matcher fill = Pattern.compile("-fx-background-color:\\s*([^;]+);").matcher(rule.body());
            if (fill.find() && fill.group(1).contains("emphasis")) {
                filled.add(rule.selectors());
                assertTrue(
                        rule.body().contains("-fx-text-fill: -color-fg-emphasis"),
                        rule.selectors() + " paints on an emphasis fill without -color-fg-emphasis");
            }
        }
        assertTrue(filled.contains(".status-bar .unread-errors"), "the unread badge is an emphasis fill");
        assertTrue(filled.contains(".setting-switch:selected"), "an on switch is an emphasis fill");
    }

    /** ICON-12, KBD-16: one ring color on .root, drawn by the :focus-visible layer that ends app.css. */
    @Test
    void theFocusRingIsOneLayerAtTheEndAndThreeToOneOnEverySurface() throws Exception {
        String app = read("app.css");
        assertTrue(body(app, ".root").contains("-focus-ring: -color-accent-emphasis"));
        List<Rule> rules = rules(app);
        int firstRing = -1;
        for (int i = 0; i < rules.size(); i++) {
            boolean ring = rules.get(i).selectors().contains(":focus-visible");
            if (ring && firstRing < 0) firstRing = i;
            if (firstRing >= 0) assertTrue(ring, rules.get(i).selectors() + " follows the focus layer");
        }
        assertTrue(firstRing > 0, "no :focus-visible layer");
        for (Rule ring : rules.subList(firstRing, rules.size())) {
            if (ring.body().contains("-fx-border-color")) {
                assertTrue(ring.body().contains("-fx-border-color: -focus-ring"), ring.selectors());
            }
        }
        for (String theme : FLAGSHIP) {
            Map<String, String> p = palette(theme);
            for (String surface :
                    List.of("-color-bg-default", "-color-bg-subtle", "-color-bg-inset", "-color-bg-overlay")) {
                double ratio = contrast(color(p, "-color-accent-emphasis"), color(p, surface));
                assertTrue(ratio >= 3.0, theme + ": ring on " + surface + " is " + ratio);
            }
        }
    }

    /** ICON-07: the state tokens are named once, in the .root block at the top, each chained to a role. */
    @Test
    void theStateTokensAreDeclaredOnceAtTheTopAndChainToARole() throws Exception {
        String app = read("app.css");
        List<Rule> rules = rules(app);
        Rule tokens = rules.get(0);
        assertEquals(".root", tokens.selectors(), "the token block is the first rule");
        Matcher declared = Pattern.compile("(-state-[a-z-]+)\\s*:\\s*([^;]+);").matcher(tokens.body());
        List<String> names = new ArrayList<>();
        while (declared.find()) {
            names.add(declared.group(1));
            assertTrue(
                    declared.group(2).strip().matches("-color-[a-z0-9-]+|ladder\\(.*"),
                    declared.group(1) + " chains to " + declared.group(2));
        }
        assertTrue(
                names.containsAll(List.of("-state-red", "-state-amber", "-state-green", "-state-accent")),
                names::toString);
        for (Rule rule : rules.subList(1, rules.size())) {
            assertFalse(
                    Pattern.compile("-state-[a-z-]+\\s*:").matcher(rule.body()).find(),
                    rule.selectors() + " declares a state token outside the .root block");
        }
    }

    /** ICON-08: severity colors are state tokens, never a role or a hex; no hex outside the token block. */
    @Test
    void severityColorsComeFromTheStateTokens() throws Exception {
        String app = read("app.css");
        Map<String, String> expected = new HashMap<>();
        expected.put(".status-bar .echo.error", "-state-red");
        expected.put(".status-bar .echo.warning", "-state-amber");
        expected.put(".status-bar .unread-errors", "-state-red-emphasis");
        expected.put(".status-bar .update-notice", "-state-accent");
        expected.put(".message-log-list .error", "-state-red");
        expected.put(".message-log-list .warning", "-state-amber");
        expected.put(".pill.green", "-state-green");
        expected.put(".pill.amber", "-state-amber");
        expected.put(".pill.red", "-state-red");
        expected.put(".about-update-available", "-state-accent");
        expected.put(".about-update-current", "-state-green");
        expected.put(".about-update-failed", "-state-red");
        expected.put(".text-field.invalid", "-state-red-emphasis");
        expected.put(".pauta-conformance.complete", "-state-green");
        expected.put(".pauta-conformance.core", "-state-accent");
        expected.put(".pauta-conformance.partial", "-state-amber");
        expected.put(".pauta-rule", "-state-accent");
        expected.forEach((selector, token) -> {
            String body = body(app, selector);
            assertTrue(body.contains(token + ";"), selector + " should use " + token + ": " + body);
        });
        List<Rule> rules = rules(app);
        for (Rule rule : rules.subList(1, rules.size())) {
            assertFalse(HEX.matcher(rule.body()).find(), rule.selectors() + " has a fixed color: " + rule.body());
            assertFalse(
                    Pattern.compile("-color-(danger|warning|success)-")
                            .matcher(rule.body())
                            .find(),
                    rule.selectors() + " paints a state with a role, not its -state-* token: " + rule.body());
        }
    }

    /** The sync pill reads on its tint over the Settings card (4.5:1) in both flagship themes. */
    @Test
    void theStatusPillsReadOnTheirTintOverTheCard() throws Exception {
        String app = read("app.css");
        for (String role : List.of("success", "warning", "danger")) {
            for (String theme : FLAGSHIP) {
                Map<String, String> p = palette(theme);
                Color raw = color(p, "-color-" + role + "-subtle");
                Color tint = color(p, "-color-bg-subtle")
                        .interpolate(Color.color(raw.getRed(), raw.getGreen(), raw.getBlue()), raw.getOpacity());
                double ratio = contrast(color(p, "-color-" + role + "-fg"), tint);
                assertTrue(ratio >= 4.5, theme + ": " + role + " pill is " + ratio);
            }
        }
        assertTrue(body(app, ".root").contains("-state-green-bg: -color-success-subtle"));
        assertTrue(body(app, ".pill.green").contains("-state-green-bg"));
    }

    /** ICON-01: the UI font comes from the scene sheet, never from a .root rule in app.css. */
    @Test
    void theUiFontIsTheSceneSheetsAlone() throws Exception {
        for (Rule rule : rules(read("app.css"))) {
            if (rule.has(".root")) assertFalse(rule.body().contains("-fx-font-family"), "app.css sets the UI font");
        }
        assertTrue(body(read(Themes.UI_FONT), ".root").contains("-fx-font-family: \"" + Fonts.UI + "\""));
    }

    /** ICON-04: a control theme is a .root block of tokens, then rules that reference only those tokens. */
    @Test
    void theControlThemesFollowTheTokenStructure() throws Exception {
        for (String file : List.of(Themes.LIGHT_THEME, Themes.DARK_THEME)) {
            String css = read(file);
            assertTrue(css.strip().startsWith(".root {"), file + ": the first rule is .root");
            String after = css.substring(css.indexOf("\n}", css.indexOf(".root {")));
            assertFalse(HEX.matcher(after).find(), file + " has a hex after its .root block");
        }
    }
}
