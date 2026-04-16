///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Snapshot tests for {@code script/ci/CiComputeBuildScopes.java}.
 *
 * For each scenario directory under {@code script/ci-tests/scenarios/}:
 *   changed-files.txt      — input, one real repo-relative file path per line
 *   expected-upstream.txt  — golden list of groupId:artifactId, sorted
 *   expected-affected.txt  — golden list of groupId:artifactId, sorted
 *   expected-changed.txt   — golden list of groupId:artifactId, sorted (directly changed)
 *
 * The test runs CiComputeBuildScopes with the scenario's changed-files.txt
 * and diffs the produced upstream/affected/changed lists against the committed
 * goldens. Any divergence — a listed file moved, a module renamed/added/
 * removed, or a reactor dependency edge changed — breaks the test. The
 * fix is to regenerate the goldens in a follow-up PR.
 *
 * Env:
 *   GRAPH_EXTENSION_JAR   required — path to graph-dump-extension jar
 *   CI_UPDATE_GOLDEN=1    optional — rewrite the golden files instead of asserting
 *
 * Run:
 *   jbang script/ci-tests/CiComputeBuildScopesTest.java
 */
public class CiComputeBuildScopesTest {

    static final Path REPO_ROOT = Paths.get("").toAbsolutePath();
    static final Path SCENARIOS_DIR = REPO_ROOT.resolve("script/ci-tests/scenarios");
    static final Path SCRIPT = REPO_ROOT.resolve("script/ci/CiComputeBuildScopes.java");

    public static void main(String[] args) throws Exception {
        boolean updateGolden = "1".equals(System.getenv("CI_UPDATE_GOLDEN"));

        if (!Files.isRegularFile(SCRIPT)) {
            System.err.println("script not found: " + SCRIPT);
            System.exit(2);
        }
        if (!Files.isDirectory(SCENARIOS_DIR)) {
            System.err.println("scenarios dir not found: " + SCENARIOS_DIR);
            System.exit(2);
        }
        if (System.getenv("GRAPH_EXTENSION_JAR") == null) {
            System.err.println("GRAPH_EXTENSION_JAR env var not set — build the extension first:");
            System.err.println("  mvn -f script/ci/extension/pom.xml install");
            System.err.println("  export GRAPH_EXTENSION_JAR=$HOME/.m2/repository/local/tools/graph-dump-extension/1.0.0/graph-dump-extension-1.0.0.jar");
            System.exit(2);
        }

        List<Path> scenarios;
        try (Stream<Path> s = Files.list(SCENARIOS_DIR)) {
            scenarios = s.filter(Files::isDirectory).sorted().toList();
        }
        if (scenarios.isEmpty()) {
            System.err.println("no scenarios under " + SCENARIOS_DIR);
            System.exit(2);
        }

        int failed = 0;
        for (Path scenario : scenarios) {
            if (!runScenario(scenario, updateGolden)) failed++;
        }

        System.err.println();
        if (failed > 0) {
            System.err.println(failed + " of " + scenarios.size() + " scenario(s) failed.");
            System.err.println();
            System.err.println("If the divergence is expected (modules moved/added/removed, deps changed),");
            System.err.println("regenerate the golden files and commit them in a follow-up PR:");
            System.err.println("  CI_UPDATE_GOLDEN=1 jbang script/ci-tests/CiComputeBuildScopesTest.java");
            System.exit(1);
        }
        System.err.println("All " + scenarios.size() + " scenario(s) passed.");
    }

    private static boolean runScenario(Path scenario, boolean updateGolden) throws Exception {
        String name = scenario.getFileName().toString();
        Path changedFiles = scenario.resolve("changed-files.txt");
        Path expectedUpstream = scenario.resolve("expected-upstream.txt");
        Path expectedAffected = scenario.resolve("expected-affected.txt");
        Path expectedChanged  = scenario.resolve("expected-changed.txt");

        if (!Files.isRegularFile(changedFiles)) {
            System.err.println("[" + name + "] SKIP: missing changed-files.txt");
            return true;
        }

        System.err.println("[" + name + "] running…");
        Path tmp = Files.createTempDirectory("cbs-test-" + name + "-");
        Path actualUpstream = tmp.resolve("upstream.txt");
        Path actualAffected = tmp.resolve("affected.txt");
        Path actualChanged  = tmp.resolve("changed.txt");

        int rc = runScript(changedFiles, actualUpstream, actualAffected, actualChanged);
        if (rc != 0) {
            System.err.println("[" + name + "] FAIL: CiComputeBuildScopes exited with " + rc);
            return false;
        }

        if (updateGolden) {
            Files.copy(actualUpstream, expectedUpstream, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(actualAffected, expectedAffected, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(actualChanged,  expectedChanged,  StandardCopyOption.REPLACE_EXISTING);
            System.err.println("[" + name + "] UPDATED goldens ("
                    + Files.readAllLines(expectedUpstream).size() + " upstream, "
                    + Files.readAllLines(expectedAffected).size() + " affected, "
                    + Files.readAllLines(expectedChanged).size()  + " changed)");
            return true;
        }

        boolean upstreamOk = diffLines(name, "upstream", expectedUpstream, actualUpstream);
        boolean affectedOk = diffLines(name, "affected", expectedAffected, actualAffected);
        boolean changedOk  = diffLines(name, "changed",  expectedChanged,  actualChanged);
        if (upstreamOk && affectedOk && changedOk) {
            System.err.println("[" + name + "] OK");
            return true;
        }
        return false;
    }

    private static int runScript(Path input, Path upstreamOut, Path affectedOut, Path changedOut)
            throws IOException, InterruptedException {
        List<String> cmd = List.of(
                "jbang", SCRIPT.toString(),
                input.toAbsolutePath().toString(),
                upstreamOut.toAbsolutePath().toString(),
                affectedOut.toAbsolutePath().toString(),
                changedOut.toAbsolutePath().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(REPO_ROOT.toFile())
                .redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) System.err.println("    " + line);
        }
        return p.waitFor();
    }

    private static boolean diffLines(String name, String label, Path expected, Path actual) throws IOException {
        if (!Files.isRegularFile(expected)) {
            System.err.println("[" + name + "] FAIL: " + label + " golden file missing: " + expected);
            System.err.println("    (run with CI_UPDATE_GOLDEN=1 to generate it)");
            return false;
        }
        List<String> exp = Files.readAllLines(expected);
        List<String> act = Files.readAllLines(actual);
        if (exp.equals(act)) return true;

        System.err.println("[" + name + "] FAIL: " + label + " list differs from golden");
        Set<String> expSet = new LinkedHashSet<>(exp);
        Set<String> actSet = new LinkedHashSet<>(act);
        TreeSet<String> missing = new TreeSet<>(expSet);
        missing.removeAll(actSet);
        TreeSet<String> extra = new TreeSet<>(actSet);
        extra.removeAll(expSet);
        if (!missing.isEmpty()) {
            System.err.println("    - in golden but NOT produced (" + missing.size() + "):");
            missing.forEach(s -> System.err.println("      - " + s));
        }
        if (!extra.isEmpty()) {
            System.err.println("    + produced but NOT in golden (" + extra.size() + "):");
            extra.forEach(s -> System.err.println("      + " + s));
        }
        return false;
    }
}
