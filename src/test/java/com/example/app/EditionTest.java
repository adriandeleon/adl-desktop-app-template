package com.example.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Pauta edition has one source, {@code pauta.version} in {@code pom.xml} (ABOUT-08): the build
 * filters it into {@link AppInfo}, and the README's badge and "Built to" line and {@code AGENTS.md}
 * name the same number, so moving to a new edition changes them together or fails here. The
 * conformance they name, and {@code pauta.json} against the edition's rules, are {@code
 * PautaConformanceTest}'s.
 */
class EditionTest {

    private static String read(String file) throws Exception {
        return Files.readString(Path.of(file), StandardCharsets.UTF_8);
    }

    private static String pomEdition() throws Exception {
        Matcher m =
                Pattern.compile("<pauta\\.version>([^<]+)</pauta\\.version>").matcher(read("pom.xml"));
        assertTrue(m.find(), "pom.xml defines pauta.version");
        return m.group(1).strip();
    }

    @Test
    void appInfoReadsTheEditionFromThePom() throws Exception {
        assertEquals(pomEdition(), AppInfo.PAUTA_EDITION);
    }

    @Test
    void theReadmeAndAgentsNameTheSameEdition() throws Exception {
        String edition = pomEdition();
        String page = "https://pauta-project.dev/" + edition + "/";
        String readme = read("README.md");
        // The badge and the "Built to" line also name the conformance: PautaConformanceTest holds them.
        assertTrue(readme.contains("badge/Pauta-" + edition + "%20"), "README badge");
        assertTrue(readme.contains("edition [" + edition + "](" + page + ")"), "README names " + edition);
        assertTrue(read("AGENTS.md").contains("edition [" + edition + "](" + page + ")"), "AGENTS.md");
        Matcher other = Pattern.compile("Pauta[- ](\\d+\\.\\d+\\.\\d+)").matcher(readme);
        while (other.find()) assertEquals(edition, other.group(1), "README names another edition");
    }
}
