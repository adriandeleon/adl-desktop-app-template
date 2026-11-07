package com.example.app.packaging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards build rules that live in {@code pom.xml}, the packaging scripts and the contributor docs, where
 * nothing compiles them.
 *
 * <ul>
 *   <li>{@code LICENSE} and {@code NOTICE} reach every artifact: the jar (and so the fat jar and the
 *       runtime image), the app image the installers wrap, and the Linux packages' license metadata
 *       (Pauta REPO-04).
 *   <li>The JaCoCo floors: the whole-build limits Pauta PLAT-12 names, and the per-package floors that
 *       {@code docs/testing.md} quotes.
 * </ul>
 *
 * The pom is read as text: the tests run inside the app's module, which does not read {@code java.xml}.
 */
class BuildHygieneTest {

    private static final Path REPO = Path.of(System.getProperty("user.dir"));
    private static String pom = "";

    @BeforeAll
    static void readPom() throws IOException {
        pom = Files.readString(REPO.resolve("pom.xml")).replaceAll("(?s)<!--.*?-->", "");
    }

    private static List<String> all(String xml, String tag) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("(?s)<" + tag + ">(.*?)</" + tag + ">").matcher(xml);
        while (m.find()) {
            out.add(m.group(1).trim());
        }
        return out;
    }

    private static String first(String xml, String tag) {
        List<String> found = all(xml, tag);
        return found.isEmpty() ? "" : found.getFirst();
    }

    /** The {@code <execution>} with this id, or a failure naming it. */
    private static String execution(String id) {
        for (String execution : all(pom, "execution")) {
            if (id.equals(first(execution, "id"))) {
                return execution;
            }
        }
        throw new AssertionError("pom.xml has no <execution> with id " + id);
    }

    // ---- LICENSE and NOTICE -----------------------------------------------------------------------------

    @Test
    void licenseAndNoticeArePackagedIntoTheJarFromTheRepositoryRoot() {
        boolean found = false;
        for (String resource : all(first(pom, "resources"), "resource")) {
            if ("${project.basedir}".equals(first(resource, "directory"))) {
                assertEquals("META-INF", first(resource, "targetPath"));
                assertEquals(List.of("LICENSE", "NOTICE"), all(first(resource, "includes"), "include"));
                assertEquals("false", first(resource, "filtering"), "LICENSE and NOTICE ship byte for byte");
                found = true;
            }
        }
        assertTrue(found, "pom.xml no longer packages LICENSE and NOTICE into the jar's META-INF/");
    }

    @Test
    void theAppImageTheInstallersWrapIsHandedLicenseAndNotice() throws IOException {
        String addNotices = execution("add-notices");
        assertTrue(addNotices.contains("scripts/add-notices.sh"), "add-notices no longer runs add-notices.sh");
        List<String> arguments = all(addNotices, "argument");
        assertTrue(
                arguments.contains("${project.basedir}/LICENSE") && arguments.contains("${project.basedir}/NOTICE"),
                "the dist profile no longer copies LICENSE and NOTICE into the app image: " + arguments);
        String installers = Files.readString(REPO.resolve(Path.of("scripts", "build-installers.sh")));
        assertTrue(
                installers.contains("license=\"$root/LICENSE\"") && installers.contains("--license-file \"$license\""),
                "build-installers.sh no longer hands the Linux packages the LICENSE");
    }

    // ---- coverage floors --------------------------------------------------------------------------------

    @Test
    void theWholeBuildKeepsTheCoverageFloorsPautaRequires() {
        String check = execution("jacoco-check");
        assertEquals("verify", first(check, "phase"), "jacoco-check must run at mvn verify");
        String bundle = null;
        for (String rule : all(check, "rule")) {
            if ("BUNDLE".equals(first(rule, "element"))) {
                bundle = rule;
            }
        }
        assertTrue(bundle != null, "jacoco-check lost its BUNDLE rule");
        Map<String, Double> minimum = new LinkedHashMap<>();
        for (String limit : all(bundle, "limit")) {
            minimum.put(first(limit, "counter"), Double.parseDouble(first(limit, "minimum")));
        }
        assertTrue(minimum.getOrDefault("LINE", 0.0) >= 0.90, "the build's line floor is below 0.90: " + minimum);
        assertTrue(minimum.getOrDefault("METHOD", 0.0) >= 0.90, "the method floor is below 0.90: " + minimum);
        assertTrue(minimum.getOrDefault("BRANCH", 0.0) >= 0.79, "the branch floor is below 0.79: " + minimum);
    }

    /** {@code package -> minimum line coverage}, straight from the jacoco-check PACKAGE rules. */
    private static Map<String, String> packageFloors() {
        Map<String, String> floors = new LinkedHashMap<>();
        for (String rule : all(execution("jacoco-check"), "rule")) {
            if (!"PACKAGE".equals(first(rule, "element"))) {
                continue;
            }
            String minimum = "";
            for (String limit : all(rule, "limit")) {
                if ("LINE".equals(first(limit, "counter"))) {
                    minimum = first(limit, "minimum");
                }
            }
            for (String include : all(first(rule, "includes"), "include")) {
                floors.put(include, minimum);
            }
        }
        return floors;
    }

    @Test
    void theCoverageFloorsQuotedInTheTestingGuideAreTheOnesTheBuildEnforces() throws IOException {
        Map<String, String> floors = packageFloors();
        assertTrue(floors.size() >= 5, "expected the per-package JaCoCo rules, found " + floors);
        String guide = Files.readString(REPO.resolve(Path.of("docs", "testing.md")));
        String section = guide.substring(guide.indexOf("## Coverage"));
        Pattern number = Pattern.compile("\\d\\.\\d\\d");
        List<String> wrong = new ArrayList<>();
        floors.forEach((name, minimum) -> {
            int at = section.indexOf("`" + name + "`");
            Matcher m = number.matcher(at < 0 ? "" : section.substring(at));
            if (!m.find() || !m.group().equals(minimum)) {
                wrong.add(name + " (pom.xml enforces " + minimum + ")");
            }
        });
        assertTrue(
                wrong.isEmpty(),
                "docs/testing.md > Coverage quotes a floor that pom.xml does not enforce, or omits one: " + wrong);
    }
}
