package com.example.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.app.PautaConformance.Conformance;
import com.example.app.PautaConformance.Entry;
import com.example.app.PautaConformance.Kind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pauta's Conformance page: the helper computes Complete, Core or Partial (PAUTA-02); {@code pauta.json}
 * agrees with the edition's {@code rules.json}, copied to {@code src/test/resources/pauta-rules.json}
 * (PAUTA-06, PAUTA-07, PAUTA-09, PAUTA-10); and the README's badge and "Built to" line name the edition
 * and the conformance the helper returns (PAUTA-15).
 */
class PautaConformanceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Pending exceptions the template records link issues in the template's own repository. They stay
     * valid in an app made from it until that app re-audits and opens its own (TODO.md).
     */
    private static final String TEMPLATE_REPO = "adriandeleon/adl-desktop-app-template";

    private static Entry entry(String rule, String level, Kind kind) {
        String link = kind == Kind.NOT_APPLICABLE ? null : "https://github.com/x/y/issues/1";
        return new Entry(rule, level, "https://pauta-project.dev/4.1.0/x/#" + rule.toLowerCase(), kind, "R.", link);
    }

    // ---- the helper (PAUTA-02) ----

    @Test
    void noExceptionsIsComplete() {
        assertEquals(Conformance.COMPLETE, PautaConformance.of(List.of()));
    }

    @Test
    void aPendingMustIsPartialAndAPendingShouldIsCore() {
        assertEquals(Conformance.PARTIAL, PautaConformance.of(List.of(entry("A-01", "MUST", Kind.PENDING))));
        assertEquals(Conformance.CORE, PautaConformance.of(List.of(entry("A-02", "SHOULD", Kind.PENDING))));
        assertEquals(
                Conformance.PARTIAL,
                PautaConformance.of(
                        List.of(entry("A-02", "SHOULD", Kind.PENDING), entry("A-01", "MUST", Kind.PENDING))));
    }

    @Test
    void deviationsNotApplicableEntriesAndMaysLeaveTheConformanceAlone() {
        List<Entry> none = List.of(
                entry("A-01", "MUST", Kind.NOT_APPLICABLE),
                entry("A-02", "SHOULD", Kind.NOT_APPLICABLE),
                entry("A-03", "SHOULD", Kind.DEVIATION),
                entry("A-04", "MAY", Kind.PENDING));
        assertEquals(Conformance.COMPLETE, PautaConformance.of(none));
        List<Entry> core = new java.util.ArrayList<>(none);
        core.add(entry("A-05", "SHOULD", Kind.PENDING));
        assertEquals(Conformance.CORE, PautaConformance.of(core));
    }

    @Test
    void theNamesAreTheWordsTheReadmeAndTheEnvironmentWrite() {
        assertEquals("Complete", Conformance.COMPLETE.english());
        assertEquals("Core", Conformance.CORE.english());
        assertEquals("partial", Conformance.PARTIAL.key());
        assertEquals("notApplicable", Kind.NOT_APPLICABLE.key());
        assertEquals("not-applicable", Kind.NOT_APPLICABLE.json());
        assertThrows(IllegalArgumentException.class, () -> Kind.parse("waived"));
    }

    @Test
    void pendingFirstThenDeviationsThenNotApplicableMustsBeforeShouldsThenById() {
        List<Entry> sorted = PautaConformance.sorted(List.of(
                entry("Z-01", "MUST", Kind.NOT_APPLICABLE),
                entry("B-02", "SHOULD", Kind.DEVIATION),
                entry("C-03", "SHOULD", Kind.PENDING),
                entry("D-04", "MUST", Kind.PENDING),
                entry("A-05", "MUST", Kind.PENDING),
                entry("A-06", "MAY", Kind.PENDING)));
        assertEquals(
                List.of("A-05", "D-04", "C-03", "A-06", "B-02", "Z-01"),
                sorted.stream().map(Entry::rule).toList());
    }

    @Test
    void countsAndTheEnvironmentLine() {
        List<Entry> list = List.of(
                entry("A-01", "MUST", Kind.PENDING),
                entry("A-02", "SHOULD", Kind.PENDING),
                entry("A-03", "SHOULD", Kind.DEVIATION));
        assertEquals(Map.of(Kind.PENDING, 2, Kind.DEVIATION, 1), PautaConformance.counts(list));
        assertEquals(1, PautaConformance.pending(list, "MUST"));
        assertEquals(1, PautaConformance.pending(list, "SHOULD"));
        assertEquals("Pauta conformance: Partial (3 exceptions)", PautaConformance.environmentLine(list));
        assertEquals(
                "Pauta conformance: Core (1 exception)",
                PautaConformance.environmentLine(List.of(entry("A-02", "SHOULD", Kind.PENDING))));
        assertEquals("Pauta conformance: Complete (no exceptions)", PautaConformance.environmentLine(List.of()));
    }

    @Test
    void issueAndDecisionNumbers() {
        assertEquals("#212", PautaConformance.issueNumber("https://github.com/a/b/issues/212"));
        assertNull(PautaConformance.issueNumber("https://github.com/a/b/blob/master/docs/decisions/0007-x.md"));
        assertEquals(
                "0007", PautaConformance.decisionNumber("https://github.com/a/b/blob/master/docs/decisions/0007-x.md"));
        assertNull(PautaConformance.decisionNumber("https://github.com/a/b/issues/212"));
        assertNull(PautaConformance.decisionNumber("https://github.com/a/b/blob/master/docs/decisions/README.md"));
    }

    @Test
    void parsesEveryField() throws Exception {
        List<Entry> parsed = PautaConformance.parse(JSON.readTree("""
                {"exceptions": [
                  {"rule": "A-01", "level": "MUST", "url": "u", "kind": "pending", "reason": "R.", "link": "l"},
                  {"rule": "A-02", "level": "SHOULD", "url": "v", "kind": "not-applicable", "reason": "S."}
                ]}"""));
        assertEquals(new Entry("A-01", "MUST", "u", Kind.PENDING, "R.", "l"), parsed.get(0));
        assertNull(parsed.get(1).link());
        assertEquals(Kind.NOT_APPLICABLE, parsed.get(1).kind());
        assertTrue(PautaConformance.parse(JSON.readTree("{}")).isEmpty());
    }

    // ---- pauta.json against the edition (PAUTA-09, PAUTA-10) ----

    private static String read(String file) throws Exception {
        return Files.readString(Path.of(file), StandardCharsets.UTF_8);
    }

    private static String pomEdition() throws Exception {
        Matcher m =
                Pattern.compile("<pauta\\.version>([^<]+)</pauta\\.version>").matcher(read("pom.xml"));
        assertTrue(m.find(), "pom.xml defines pauta.version");
        return m.group(1).strip();
    }

    private static JsonNode edition() throws Exception {
        return JSON.readTree(Path.of("src/test/resources/pauta-rules.json").toFile());
    }

    private static JsonNode file() throws Exception {
        return JSON.readTree(
                Path.of("src/main/resources/com/example/app/pauta.json").toFile());
    }

    @Test
    void theRulesCopyIsTheEditionThePomNames() throws Exception {
        assertEquals(pomEdition(), edition().path("edition").asText(), "replace pauta-rules.json with this edition's");
        assertTrue(edition().path("rules").size() > 100);
    }

    @Test
    void theBundledFileIsTheOneTheAppReads() throws Exception {
        assertEquals(PautaConformance.parse(file()), PautaConformance.exceptions());
    }

    @Test
    void everyEntryNamesARuleOfTheEditionWithItsLevelAndAddress() throws Exception {
        Map<String, JsonNode> rules = new HashMap<>();
        for (JsonNode rule : edition().path("rules")) rules.put(rule.path("id").asText(), rule);
        Set<String> seen = new HashSet<>();
        for (JsonNode node : file().path("exceptions")) {
            String id = node.path("rule").asText();
            for (String field : List.of("rule", "level", "url", "kind", "reason")) {
                assertFalse(node.path(field).asText().isBlank(), id + " has no " + field);
            }
            JsonNode rule = rules.get(id);
            assertNotNull(rule, id + " is not a rule of Pauta " + pomEdition());
            assertTrue(seen.add(id), id + " appears twice");
            assertEquals(rule.path("level").asText(), node.path("level").asText(), id + "'s level");
            assertEquals(rule.path("url").asText(), node.path("url").asText(), id + "'s url");
        }
    }

    @Test
    void eachKindHasWhatItNeeds() throws Exception {
        String repos = "(" + Pattern.quote(AppInfo.GITHUB_REPO) + "|" + Pattern.quote(TEMPLATE_REPO) + ")";
        Pattern issue = Pattern.compile("https://github\\.com/" + repos + "/issues/\\d+");
        Pattern decision =
                Pattern.compile("https://github\\.com/" + repos + "/blob/master/(docs/decisions/[^/]+\\.md)");
        for (Entry e : PautaConformance.parse(file())) {
            String link = e.link();
            switch (e.kind()) {
                case NOT_APPLICABLE -> assertNull(link, e.rule() + ": a not-applicable entry has no link");
                case PENDING -> {
                    assertNotNull(link, e.rule() + ": a pending entry links its issue (PAUTA-07)");
                    assertTrue(issue.matcher(link).matches(), e.rule() + ": not an issue in this repository: " + link);
                }
                case DEVIATION -> {
                    assertEquals("SHOULD", e.level(), e.rule() + ": only a SHOULD can be a deviation (PAUTA-06)");
                    assertNotNull(link, e.rule() + ": a deviation links its decision record");
                    Matcher m = decision.matcher(link);
                    assertTrue(m.matches(), e.rule() + ": not a decision record: " + link);
                    Path record = Path.of(m.group(2));
                    assertTrue(Files.exists(record), record + " is missing");
                    assertTrue(read(record.toString()).contains(e.rule()), record + " does not name " + e.rule());
                }
            }
            // PAUTA-08: one plain sentence for the user, without rule text or IDs.
            assertTrue(e.reason().endsWith("."), e.rule() + ": the reason is a sentence");
            assertFalse(e.reason().matches(".*\\b[A-Z]{2,6}-\\d{2,3}\\b.*"), e.rule() + ": the reason names a rule");
        }
    }

    // ---- the README (PAUTA-15) ----

    @Test
    void theReadmeNamesTheEditionAndTheConformanceAndLinksTheDocsPage() throws Exception {
        String edition = pomEdition();
        Conformance conformance = PautaConformance.conformance();
        String readme = read("README.md");
        String docs = AppInfo.WEBSITE + "/docs/pauta/";

        Matcher badge = Pattern.compile(
                        "\\[!\\[Pauta]\\(https://img\\.shields\\.io/badge/Pauta-([0-9.]+)%20%C2%B7%20(\\w+)-\\w+\\)]\\(([^)]+)\\)")
                .matcher(readme);
        assertTrue(badge.find(), "README has the Pauta badge");
        assertEquals(edition, badge.group(1), "the badge's edition");
        assertEquals(conformance.english(), badge.group(2), "the badge's conformance");
        assertEquals(docs, badge.group(3), "the badge links the conformance page");

        String opening = conformance == Conformance.PARTIAL ? "Working toward" : "Built to";
        String other = conformance == Conformance.PARTIAL ? "Built to [Pauta" : "Working toward [Pauta";
        String line = opening + " [Pauta (the adl desktop apps guidelines)](https://pauta-project.dev/), edition ["
                + edition + "](https://pauta-project.dev/" + edition + "/), [Pauta conformance: "
                + conformance.english() + "](" + docs + ").";
        assertTrue(readme.contains(line), "README's line: " + line);
        assertFalse(readme.contains(other), "README still says " + other);
    }
}
