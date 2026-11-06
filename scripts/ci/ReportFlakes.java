import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Lists the tests that failed and then passed when Surefire ran them again.
 *
 * <p>CI reruns a failing test ({@code -Dsurefire.rerunFailingTestsCount=2}), so a test that fails now
 * and then no longer turns the build red. That must not hide it: a flaky test is a race in the test or
 * in the app. This reads the Surefire XML reports and prints each test that needed a rerun as Markdown
 * for the job summary, and as a warning annotation on stderr.
 *
 * <pre>
 *   java scripts/ci/ReportFlakes.java target/surefire-reports &gt;&gt; "$GITHUB_STEP_SUMMARY"
 * </pre>
 *
 * Always exits 0 (2 on bad usage): the tests passed in the end; the report is what matters.
 */
public final class ReportFlakes {

    record Flake(String test, int attempts, String message) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: java scripts/ci/ReportFlakes.java <surefire-reports-dir>");
            System.exit(2);
        }
        List<Path> reports = reports(Path.of(args[0]));
        if (reports.isEmpty()) {
            return; // the build stopped before any test ran: nothing to say
        }
        var flakes = new ArrayList<Flake>();
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        for (Path report : reports) {
            NodeList cases = factory.newDocumentBuilder().parse(report.toFile()).getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                var testCase = (Element) cases.item(i);
                var attempts = new ArrayList<Element>();
                for (var node = testCase.getFirstChild(); node != null; node = node.getNextSibling()) {
                    if (node instanceof Element child
                            && (child.getTagName().equals("flakyFailure") || child.getTagName().equals("flakyError"))) {
                        attempts.add(child);
                    }
                }
                if (!attempts.isEmpty()) {
                    String message = attempts.getFirst().getAttribute("message");
                    if (message.isBlank()) {
                        message = attempts.getFirst().getTextContent();
                    }
                    flakes.add(new Flake(testCase.getAttribute("classname") + "." + testCase.getAttribute("name"),
                            attempts.size(), firstLine(message)));
                }
            }
        }
        flakes.sort(Comparator.comparing(Flake::test));

        System.out.println("### Flaky tests");
        if (flakes.isEmpty()) {
            System.out.println("None: no test needed a rerun.");
            return;
        }
        System.out.println(flakes.size() + " test(s) failed and then passed on a rerun. Each one is a race worth fixing.");
        System.out.println();
        System.out.println("| Test | Failed attempts | First failure |");
        System.out.println("|---|---|---|");
        for (Flake flake : flakes) {
            System.out.println("| `" + flake.test() + "` | " + flake.attempts() + " | "
                    + truncate(flake.message().replace('|', '/')) + " |");
            System.err.println("::warning title=Flaky test::" + flake.test() + " passed only on a rerun: "
                    + truncate(flake.message()));
        }
    }

    private static List<Path> reports(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("TEST-") && name.endsWith(".xml");
                    })
                    .sorted()
                    .toList();
        }
    }

    private static String firstLine(String text) {
        String stripped = text == null ? "" : text.strip();
        return stripped.isEmpty() ? "" : stripped.lines().findFirst().orElse("").strip();
    }

    private static String truncate(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200);
    }
}
