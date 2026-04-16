///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class CiComputeBuildScopes {

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: jbang CiComputeBuildScopes.java <file-list> <upstream-out> <affected-out> <changed-out>");
            System.err.println();
            System.err.println("env:");
            System.err.println("  GRAPH_EXTENSION_JAR  path to graph-dump-extension jar (required)");
            System.err.println("  MVN                  mvn binary (default: mvn)");
            System.exit(2);
        }

        Path fileList = Paths.get(args[0]);
        Path upstreamOut = Paths.get(args[1]);
        Path affectedOut = Paths.get(args[2]);
        Path changedOut = Paths.get(args[3]);

        Path cwd = Paths.get("").toAbsolutePath();
        if (!Files.isRegularFile(cwd.resolve("pom.xml"))) {
            System.err.println("no pom.xml in " + cwd);
            System.exit(2);
        }

        String extJar = System.getenv("GRAPH_EXTENSION_JAR");
        if (extJar == null || extJar.isBlank()) {
            System.err.println("GRAPH_EXTENSION_JAR env var not set");
            System.exit(2);
        }
        Path extJarPath = Paths.get(extJar).toAbsolutePath();
        if (!Files.isRegularFile(extJarPath)) {
            System.err.println("extension jar not found: " + extJarPath);
            System.exit(2);
        }

        // 1. map changed files -> nearest pom.xml directory (walk up recursively)
        List<Path> changedFiles = Files.readAllLines(fileList).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Paths::get)
                .map(p -> p.isAbsolute() ? p : cwd.resolve(p))
                .toList();

        Set<Path> changedModuleDirs = new HashSet<>();
        for (Path f : changedFiles) {
            Path dir = Files.isDirectory(f) ? f : f.getParent();
            while (dir != null && dir.startsWith(cwd)) {
                if (Files.isRegularFile(dir.resolve("pom.xml"))) {
                    changedModuleDirs.add(dir.toAbsolutePath().normalize());
                    break;
                }
                dir = dir.getParent();
            }
        }

        // 2. run mvn validate with extension, dump graph to file.
        // Persist the graph to MAVEN_DEPENDENCY_GRAPH_FILE when set so downstream
        // tools (CiSummary) can reuse it without re-invoking Maven.
        String graphFileEnv = System.getenv("MAVEN_DEPENDENCY_GRAPH_FILE");
        Path graphFile = (graphFileEnv != null && !graphFileEnv.isBlank())
                ? Paths.get(graphFileEnv).toAbsolutePath()
                : Files.createTempFile("graph-", ".tsv");
        int rc = runMavenWithExtension(cwd, extJarPath, graphFile);
        // extension aborts with non-zero; verify file was written
        if (!Files.isRegularFile(graphFile) || Files.size(graphFile) == 0) {
            System.err.println("graph dump failed (mvn rc=" + rc + ")");
            System.exit(1);
        }

        // 3. parse graph
        Map<String, Path> gaToDir = new HashMap<>();
        Map<String, Set<String>> upstreamOf = new HashMap<>();   // ga -> direct upstreams
        Map<String, Set<String>> downstreamOf = new HashMap<>(); // ga -> direct downstreams

        try (BufferedReader r = Files.newBufferedReader(graphFile)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.split("\t", -1);
                if (parts.length < 3) continue;
                switch (parts[0]) {
                    case "P" -> {
                        gaToDir.put(parts[1], Paths.get(parts[2]).toAbsolutePath().normalize());
                        upstreamOf.computeIfAbsent(parts[1], k -> new HashSet<>());
                        downstreamOf.computeIfAbsent(parts[1], k -> new HashSet<>());
                    }
                    case "D" -> {
                        upstreamOf.computeIfAbsent(parts[1], k -> new HashSet<>()).add(parts[2]);
                        downstreamOf.computeIfAbsent(parts[2], k -> new HashSet<>()).add(parts[1]);
                    }
                }
            }
        }

        // 4. resolve changed dirs -> GA
        Map<Path, String> dirToGa = gaToDir.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        Set<String> changed = new HashSet<>();
        for (Path d : changedModuleDirs) {
            String ga = dirToGa.get(d);
            if (ga == null) {
                System.err.println("warn: no maven project at " + d);
                continue;
            }
            changed.add(ga);
        }

        // 5. affected = changed + transitive downstream
        Set<String> affected = traverse(changed, downstreamOf);

        // 6. upstream = transitive upstream of affected, minus affected
        Set<String> upstreamAll = traverse(affected, upstreamOf);
        upstreamAll.removeAll(affected);

        writeLines(upstreamOut, upstreamAll);
        writeLines(affectedOut, affected);
        writeLines(changedOut, changed);

        int total = gaToDir.size();
        int ignored = total - affected.size() - upstreamAll.size();
        System.out.println("total=" + total
                + " changed=" + changed.size()
                + " affected=" + affected.size()
                + " upstream=" + upstreamAll.size()
                + " ignored=" + ignored);
    }

    private static Set<String> traverse(Set<String> seeds, Map<String, Set<String>> edges) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>(seeds);
        while (!stack.isEmpty()) {
            String cur = stack.pop();
            if (visited.add(cur)) {
                Set<String> next = edges.get(cur);
                if (next != null) stack.addAll(next);
            }
        }
        return visited;
    }

    private static void writeLines(Path out, Collection<String> lines) throws IOException {
        List<String> sorted = new ArrayList<>(lines);
        Collections.sort(sorted);
        Files.write(out, sorted);
    }

    private static int runMavenWithExtension(Path cwd, Path extJar, Path graphOut) throws IOException, InterruptedException {
        // Windows ships `mvn.cmd`, not `mvn.exe` — ProcessBuilder doesn't go through
        // cmd.exe, so the bare name "mvn" fails to resolve.
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String mvn = Optional.ofNullable(System.getenv("MVN"))
                .orElse(windows ? "mvn.cmd" : "mvn");
        List<String> cmd = List.of(
                mvn,
                "-q",
                "-Dgraphdump.out=" + graphOut.toAbsolutePath(),
                "-Dgraphdump.abort=true",
                "-Dorg.slf4j.simpleLogger.defaultLogLevel=error",
                "validate"
        );

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(cwd.toFile())
                .redirectErrorStream(true);
        // register extension via env — MAVEN_OPTS doesn't work for this; use classpath
        pb.environment().put("MAVEN_OPTS",
                Optional.ofNullable(System.getenv("MAVEN_OPTS")).orElse(""));
        // the actual extension registration: -Dmaven.ext.class.path=...
        List<String> fullCmd = new ArrayList<>();
        fullCmd.add(cmd.get(0));
        fullCmd.add("-Dmaven.ext.class.path=" + extJar.toAbsolutePath());
        fullCmd.addAll(cmd.subList(1, cmd.size()));
        pb.command(fullCmd);

        System.err.println("running: " + String.join(" ", fullCmd));
        Process p = pb.start();
        // drain output to stderr so user sees progress
        try (BufferedReader r = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.err.println("  " + line);
            }
        }
        return p.waitFor();
    }
}