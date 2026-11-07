package com.example.app.packaging;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.app.AppInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the license material every build has to ship (Pauta REPO-04).
 *
 * <p>A dependency is added in {@code pom.xml} and attributed in {@code NOTICE}, with no compiler between
 * them, so this test is the link: every dependency that ships must be named in {@code NOTICE} by its
 * {@code groupId:artifactId}. The pom is read as text: the tests run inside the app's module, which does
 * not read {@code java.xml}.
 */
class NoticeCoverageTest {

    private static final Path REPO = Path.of(System.getProperty("user.dir"));

    /**
     * Every dependency declared in {@code pom.xml} that ships, as {@code groupId:artifactId}: those of the
     * project and of its profiles, in any scope but {@code test} and {@code provided}. A plugin's own
     * {@code <dependencies>} do not ship, so plugin blocks are dropped first.
     */
    static List<String> shippedDependencies(String pom) {
        String text = pom.replaceAll("(?s)<!--.*?-->", "").replaceAll("(?s)<plugin>.*?</plugin>", "");
        List<String> out = new ArrayList<>();
        Matcher dependency =
                Pattern.compile("(?s)<dependency>(.*?)</dependency>").matcher(text);
        while (dependency.find()) {
            String body = dependency.group(1);
            String scope = tag(body, "scope");
            if ("test".equals(scope) || "provided".equals(scope)) {
                continue;
            }
            out.add(tag(body, "groupId") + ":" + tag(body, "artifactId"));
        }
        return out;
    }

    private static String tag(String xml, String name) {
        Matcher m =
                Pattern.compile("<" + name + ">\\s*([^<]*?)\\s*</" + name + ">").matcher(xml);
        return m.find() ? m.group(1) : "";
    }

    @Test
    void everyShippedDependencyIsNamedInNotice() throws IOException {
        String notice = Files.readString(REPO.resolve("NOTICE"));
        List<String> dependencies = shippedDependencies(Files.readString(REPO.resolve("pom.xml")));
        assertTrue(
                dependencies.size() >= 3 && dependencies.contains("org.openjfx:javafx-controls"),
                "the pom scan found suspiciously few dependencies: " + dependencies);
        List<String> missing = new ArrayList<>();
        for (String coordinates : dependencies) {
            if (!notice.contains(coordinates)) {
                missing.add(coordinates);
            }
        }
        assertTrue(
                missing.isEmpty(),
                "these runtime dependencies are declared in pom.xml but NOTICE does not name them by their "
                        + "groupId:artifactId; add an entry (name, coordinates, copyright, license) for each: "
                        + missing);
    }

    @Test
    void theScanSkipsTestProvidedAndPluginDependencies() {
        String pom = """
                <project><dependencies>
                  <dependency><groupId>a</groupId><artifactId>runtime</artifactId></dependency>
                  <!-- <dependency><groupId>a</groupId><artifactId>commented</artifactId></dependency> -->
                  <dependency><groupId>a</groupId><artifactId>tested</artifactId><scope>test</scope></dependency>
                  <dependency><groupId>a</groupId><artifactId>annotations</artifactId><scope>provided</scope></dependency>
                </dependencies><build><plugins><plugin><dependencies>
                  <dependency><groupId>a</groupId><artifactId>tool</artifactId></dependency>
                </dependencies></plugin></plugins></build>
                <profiles><profile><dependencies>
                  <dependency><groupId>b</groupId><artifactId>profiled</artifactId></dependency>
                </dependencies></profile></profiles></project>
                """;
        assertEquals(List.of("a:runtime", "b:profiled"), shippedDependencies(pom));
    }

    @Test
    void theApplicationJarCarriesLicenseAndNoticeFromTheRepositoryRoot() throws Exception {
        // The same resources jlink puts into the installers' runtime image and shade into the fat jar. Read
        // from the app's own classes: a plain resource lookup can answer with a dependency's META-INF/LICENSE.
        assertArrayEquals(
                Files.readAllBytes(REPO.resolve("LICENSE")),
                appFile("META-INF/LICENSE"),
                "the packaged LICENSE is not the repository's");
        assertArrayEquals(
                Files.readAllBytes(REPO.resolve("NOTICE")),
                appFile("META-INF/NOTICE"),
                "the packaged NOTICE is not the repository's");
    }

    @Test
    void everyBundledFontFamilyShipsItsOpenFontLicense() throws IOException {
        // The app's own resource folder, whatever scripts/rename.sh called its package.
        String root = NoticeCoverageTest.class
                .getPackageName()
                .replaceFirst("\\.packaging$", "")
                .replace('.', '/');
        Path fonts = REPO.resolve("src/main/resources/" + root + "/fonts");
        List<String> families = new ArrayList<>();
        try (var dirs = Files.list(fonts)) {
            dirs.filter(Files::isDirectory)
                    .forEach(d -> families.add(d.getFileName().toString()));
        }
        assertFalse(families.isEmpty(), "no font families found under " + fonts);
        String notice = Files.readString(REPO.resolve("NOTICE"));
        assertTrue(notice.contains("OFL.txt"), "NOTICE should say where each family's license file is");
        for (String family : families) {
            String text = new String(resource("/" + root + "/fonts/" + family + "/OFL.txt"), StandardCharsets.UTF_8);
            // OFL-1.1 condition 2: every copy carries the copyright notice and the license.
            assertTrue(
                    text.contains("SIL OPEN FONT LICENSE Version 1.1"),
                    family + "/OFL.txt is not the SIL Open Font License 1.1");
            assertTrue(text.contains("Copyright"), family + "/OFL.txt has no copyright notice");
        }
    }

    /** A file from where the app's own classes are: target/classes in a build, or the app's jar. */
    private static byte[] appFile(String name) throws Exception {
        Path classes = Path.of(AppInfo.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        if (Files.isDirectory(classes)) {
            Path file = classes.resolve(name);
            assertTrue(
                    Files.isRegularFile(file),
                    name + " is not in " + classes + ": it would be missing from every artifact");
            return Files.readAllBytes(file);
        }
        try (FileSystem jar = FileSystems.newFileSystem(classes)) {
            return Files.readAllBytes(jar.getPath(name));
        }
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = NoticeCoverageTest.class.getResourceAsStream(name)) {
            assertNotNull(in, name + " is not on the classpath: it would be missing from every artifact");
            return in.readAllBytes();
        }
    }
}
