///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//COMPILE_OPTIONS -encoding UTF-8

import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Parses Maven Surefire / Failsafe XML reports and writes a GitHub Actions
 * job summary to $GITHUB_STEP_SUMMARY.
 *
 * Environment variables:
 *   CI_REPO_ROOT                Path to the repository root  (default: current directory)
 *   GITHUB_STEP_SUMMARY         Path to the GitHub step summary file (set automatically by GitHub Actions)
 *   MAVEN_PL_UPSTREAM           Comma-separated upstream groupId:artifactId list (from CiComputeBuildScopes)
 *   MAVEN_PL_AFFECTED           Comma-separated affected groupId:artifactId list (changed + transitive downstream)
 *   MAVEN_PL_CHANGED            Comma-separated directly-changed groupId:artifactId list (subset of affected)
 *   MAVEN_DEPENDENCY_GRAPH_FILE TSV dump produced by the graph-dump Maven extension via CiComputeBuildScopes.
 *                               Lines: `P<TAB>ga<TAB>abs-basedir` (one per reactor project)
 *                                      `D<TAB>dependent-ga<TAB>dependency-ga` (one per direct edge)
 */
class CiSummary {

    record Suite(String module, int tests, int failures, int errors, int skipped, List<Fail> fails) {}
    record Fail(String classname, String test, String type, String message) {}

    /** Reactor GA → relative module path. Populated from the graph file. */
    static final Map<String, String> gaToPath = new LinkedHashMap<>();
    /** All reactor module paths, in reactor-iteration order. */
    static final Set<String> allPaths = new LinkedHashSet<>();
    /** module path → paths of its direct upstream (dependency) modules. */
    static final Map<String, Set<String>> upstreamsOf = new LinkedHashMap<>();
    /** Paths of reactor modules that are BOMs (imported via <dependencyManagement><scope>import</scope>). */
    static final Set<String> bomPaths = new LinkedHashSet<>();

    /** Max nodes in the Mermaid graph; beyond this, show collapsible lists instead. */
    static final int MERMAID_NODE_LIMIT = 1000;

    /** When false, the Mermaid graph is wrapped in a collapsed <details> block. */
    static boolean mermaidExpanded = false;
    static String matrixOs   = "";
    static String matrixJava = "";

    public static void main(String[] args) throws Exception {
        var root        = Path.of(env("CI_REPO_ROOT", ".")).toAbsolutePath().normalize();
        var summaryPath = env("GITHUB_STEP_SUMMARY", null);
        var upstreamGa  = env("MAVEN_PL_UPSTREAM",   "");
        var affectedGa  = env("MAVEN_PL_AFFECTED",   "");
        var changedGa   = env("MAVEN_PL_CHANGED",    "");
        var graphPath   = env("MAVEN_DEPENDENCY_GRAPH_FILE", null);
        mermaidExpanded = "true".equalsIgnoreCase(env("MERMAID_EXPANDED", "false"));
        matrixOs        = env("MATRIX_OS",   "");
        matrixJava      = env("MATRIX_JAVA", "");

        // Reuse the reactor graph already computed by CiComputeBuildScopes
        // rather than re-scanning POMs here.
        if (graphPath != null) {
            try {
                loadGraphFile(Path.of(graphPath), root);
            } catch (Exception e) {
                log("Warning: failed to load graph file %s: %s — build-scope section unavailable",
                    graphPath, e.getMessage());
            }
        }

        var upstream = resolveGas(upstreamGa);
        var affected = resolveGas(affectedGa);
        var changed  = resolveGas(changedGa);

        // ── 1. Collect all surefire / failsafe XML reports ────────────────────
        var xmlFiles = new ArrayList<Path>();
        try (var walk = Files.walk(root)) {
            walk.filter(p -> {
                var name = p.getFileName().toString();
                var dir  = p.getParent() == null ? "" : p.getParent().getFileName().toString();
                return name.endsWith(".xml")
                    && (name.startsWith("TEST-") || name.startsWith("IT-"))
                    && (dir.equals("surefire-reports") || dir.equals("failsafe-reports"));
            }).forEach(xmlFiles::add);
        }

        // ── 2. Parse ──────────────────────────────────────────────────────────
        var suites = new ArrayList<Suite>();
        for (var f : xmlFiles) {
            var s = parseSuite(root, f);
            if (s != null) suites.add(s);
        }

        // ── 3. Aggregate ──────────────────────────────────────────────────────
        int total   = suites.stream().mapToInt(Suite::tests).sum();
        int failed  = suites.stream().mapToInt(Suite::failures).sum();
        int errored = suites.stream().mapToInt(Suite::errors).sum();
        int skipped = suites.stream().mapToInt(Suite::skipped).sum();
        int passed  = total - failed - errored - skipped;

        // ── 4. Build Markdown ─────────────────────────────────────────────────
        var md = new StringBuilder();

        if (!affected.isEmpty()) {
            appendScopeSection(md, upstream, affected, changed);
        }

        md.append("## Test Results\n\n");

        if (total == 0) {
            md.append("> ℹ️ No test reports found.\n");
        } else {
            boolean ok = failed + errored == 0;
            md.append(ok ? "> 🎉 **All tests passed.**\n\n" : "> ⚠️ **Some tests failed.**\n\n");

            md.append("| ✅ Passed | ❌ Failed | 💥 Errors | ⏭️ Skipped | TOTAL |\n");
            md.append("|---:|---:|---:|---:|---:|\n");
            md.append("| **").append(passed ).append("** ")
              .append("| **").append(failed ).append("** ")
              .append("| **").append(errored).append("** ")
              .append("| **").append(skipped).append("** ")
              .append("| **").append(total  ).append("** |\n");

            // Group failing suites by module
            var byModule = suites.stream()
                .filter(s -> !s.fails().isEmpty())
                .collect(Collectors.groupingBy(Suite::module, TreeMap::new, Collectors.toList()));

            if (!byModule.isEmpty()) {
                md.append("\n### Failed Tests\n\n");
                for (var entry : byModule.entrySet()) {
                    var module   = entry.getKey().isEmpty() ? "(root)" : entry.getKey();
                    var allFails = entry.getValue().stream().flatMap(s -> s.fails().stream()).toList();
                    int n        = allFails.size();

                    md.append("<details>\n<summary><b>")
                      .append(module).append("</b> — ")
                      .append(n).append(n == 1 ? " failure" : " failures")
                      .append("</summary>\n\n");

                    for (var f : allFails) {
                        md.append("**`").append(f.classname()).append("#").append(f.test()).append("`**");
                        if (!f.type().isBlank()) md.append(" — `").append(f.type()).append("`");
                        md.append("\n");
                        var msg = f.message().trim();
                        if (!msg.isEmpty()) {
                            if (msg.length() > 300) msg = msg.substring(0, 300) + "…";
                            // Flatten newlines so it renders as a blockquote
                            md.append("> ").append(msg.replace("\n", " ")).append("\n");
                        }
                        md.append("\n");
                    }
                    md.append("</details>\n\n");
                }
            }
        }

        appendReproduceSection(md, upstreamGa, affectedGa);

        // ── 5. Write ──────────────────────────────────────────────────────────
        var summary = md.toString();
        if (summaryPath != null) {
            Files.writeString(Path.of(summaryPath), summary,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            System.out.print(summary);
        }

        log("Summarized %d tests across %d suite(s): %d passed, %d failed, %d errors, %d skipped",
            total, suites.size(), passed, failed, errored, skipped);
    }

    // ── Build scope section ───────────────────────────────────────────────────

    static void appendScopeSection(StringBuilder md,
                                    Set<String> upstream, Set<String> affected,
                                    Set<String> changed) {
        var all = new LinkedHashSet<String>();
        all.addAll(upstream);
        all.addAll(affected);

        var skipped = new TreeSet<String>();
        for (var path : allPaths) {
            if (!upstream.contains(path) && !affected.contains(path)) skipped.add(path);
        }

        md.append("## Build Scope\n\n");
        if (!changed.isEmpty()) {
            md.append("**").append(changed.size()).append(" changed** (");
            appendBomBreakdown(md, changed);
            md.append("directly modified)");
            md.append(" · ");
        }
        md.append("**").append(affected.size()).append(" affected** (");
        appendBomBreakdown(md, affected);
        md.append("changed + downstream, built + tested)");
        if (!upstream.isEmpty()) {
            md.append(" · **").append(upstream.size()).append(" upstream** (");
            appendBomBreakdown(md, upstream);
            md.append("built, tests skipped)");
        }
        if (!skipped.isEmpty()) {
            md.append(" · **").append(skipped.size()).append(" skipped** (");
            appendBomBreakdown(md, skipped);
            md.append("not built)");
        }
        md.append("\n\n");

        if (all.size() <= MERMAID_NODE_LIMIT) {
            appendMermaidGraph(md, upstream, affected, changed, all, skipped);
        } else {
            appendModuleLists(md, upstream, affected, changed, skipped);
        }
    }

    /**
     * Writes a "{X} modules + {N} BOMs; " prefix when {@code paths} contains any BOMs.
     * BOMs are not built in the compile/test sense — they're just pom-only version manifests —
     * so keeping the count distinct makes CI logs easier to reconcile.
     */
    static void appendBomBreakdown(StringBuilder md, Set<String> paths) {
        long bomCount = paths.stream().filter(bomPaths::contains).count();
        if (bomCount == 0) return;
        long modCount = paths.size() - bomCount;
        md.append(modCount).append(modCount == 1 ? " module + " : " modules + ");
        md.append(bomCount).append(bomCount == 1 ? " BOM; " : " BOMs; ");
    }

    /** Emits a Mermaid node declaration; BOMs use subroutine shape, modules use category-specific shapes. */
    static void appendMermaidNode(StringBuilder md, String path, String moduleShapeOpen, String moduleShapeClose) {
        var id    = mermaidId(path);
        var label = shortName(path);
        md.append("    ").append(id);
        if (bomPaths.contains(path)) {
            md.append("[[\"").append(label).append("\"]]");
        } else {
            md.append(moduleShapeOpen).append("\"").append(label).append("\"").append(moduleShapeClose);
        }
        md.append("\n");
    }

    static void appendMermaidGraph(StringBuilder md,
                                    Set<String> upstream, Set<String> affected,
                                    Set<String> changed,
                                    Set<String> all, Set<String> skipped) {
        // Build adjacency restricted to `all`. Direction is upstream → downstream
        // to match the Mermaid arrow we emit ("upstream --> dependent").
        Map<String, Set<String>> adj = new LinkedHashMap<>();
        for (var p : all) adj.put(p, new LinkedHashSet<>());
        for (var p : all) {
            for (var up : upstreamsOf.getOrDefault(p, Set.of())) {
                if (!up.equals(p) && all.contains(up)) {
                    adj.get(up).add(p);
                }
            }
        }

        // Transitive reduction: drop edges implied by a longer path. Without this
        // the reactor graph is dominated by parent-pom / BOM edges that bury the
        // actual structure under a mesh of redundant arrows.
        var reduced = transitiveReduction(all, adj);

        if (!mermaidExpanded) {
            md.append("<details>\n<summary>Dependency graph</summary>\n\n");
        }
        md.append("```mermaid\n");
        md.append("flowchart TD\n");

        // BOMs: subroutine shape [[label]] regardless of category — distinctive from the
        // three module shapes below so the graph is readable even in monochrome.
        // Upstream modules: stadium  ([label])
        for (var path : upstream) {
            appendMermaidNode(md, path, "([", "])");
        }
        // Changed modules: hexagon  {{label}}
        for (var path : changed) {
            appendMermaidNode(md, path, "{{", "}}");
        }
        // Affected-but-not-changed modules: rectangle  [label]
        for (var path : affected) {
            if (changed.contains(path)) continue;
            appendMermaidNode(md, path, "[", "]");
        }

        for (var u : all) {
            for (var v : reduced.getOrDefault(u, Set.of())) {
                md.append("    ").append(mermaidId(u)).append(" --> ").append(mermaidId(v)).append("\n");
            }
        }

        md.append("    classDef upstream fill:#4a90d9,color:#fff,stroke:#2c6fad\n");
        md.append("    classDef affected fill:#f5a623,color:#fff,stroke:#c0820a\n");
        md.append("    classDef changed  fill:#d0021b,color:#fff,stroke:#8a0112,stroke-width:2px\n");
        md.append("    classDef bom      fill:#9b59b6,color:#fff,stroke:#6c3483\n");

        // BOM nodes get their own class; exclude them from the per-category classes so
        // the purple fill wins regardless of where the BOM falls in changed/upstream/affected.
        var upstreamIds = upstream.stream()
            .filter(p -> !bomPaths.contains(p))
            .map(CiSummary::mermaidId).collect(Collectors.joining(","));
        var affectedOnlyIds = affected.stream()
            .filter(p -> !changed.contains(p))
            .filter(p -> !bomPaths.contains(p))
            .map(CiSummary::mermaidId).collect(Collectors.joining(","));
        var changedIds = changed.stream()
            .filter(p -> !bomPaths.contains(p))
            .map(CiSummary::mermaidId).collect(Collectors.joining(","));
        var bomIds = all.stream()
            .filter(bomPaths::contains)
            .map(CiSummary::mermaidId).collect(Collectors.joining(","));
        if (!upstreamIds.isEmpty())     md.append("    class ").append(upstreamIds).append(" upstream\n");
        if (!affectedOnlyIds.isEmpty()) md.append("    class ").append(affectedOnlyIds).append(" affected\n");
        if (!changedIds.isEmpty())      md.append("    class ").append(changedIds).append(" changed\n");
        if (!bomIds.isEmpty())          md.append("    class ").append(bomIds).append(" bom\n");

        md.append("```\n");
        if (!mermaidExpanded) {
            md.append("\n</details>\n");
        }
        md.append("\n");

        appendCollapsibleList(md, "Changed modules",  changed);
        appendCollapsibleList(md, "Upstream modules", upstream);
        appendCollapsibleList(md, "Affected modules", affected);
        appendCollapsibleList(md, "Skipped modules",  skipped);
    }

    /**
     * Returns a reduced adjacency where every edge (u, v) is kept only if no
     * alternative path u → w → … → v exists — i.e., the transitive reduction
     * of the DAG. Safe to call on non-DAGs but will also remove edges that sit
     * on a cycle; the reactor graph is always a DAG, so that's a non-issue.
     */
    static Map<String, Set<String>> transitiveReduction(Set<String> nodes,
                                                         Map<String, Set<String>> adj) {
        // 1. Transitive closure via fixpoint iteration. n ≤ MERMAID_NODE_LIMIT,
        //    so the quadratic behaviour is fine here.
        Map<String, Set<String>> reach = new HashMap<>();
        for (var n : nodes) reach.put(n, new HashSet<>());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var u : nodes) {
                var r = reach.get(u);
                for (var w : adj.getOrDefault(u, Set.of())) {
                    if (r.add(w)) changed = true;
                    for (var x : reach.getOrDefault(w, Set.of())) {
                        if (r.add(x)) changed = true;
                    }
                }
            }
        }
        // 2. Keep u→v only when no *other* neighbour w of u already reaches v.
        Map<String, Set<String>> reduced = new LinkedHashMap<>();
        for (var u : nodes) {
            var neighbours = adj.getOrDefault(u, Set.of());
            var keep = new LinkedHashSet<String>();
            for (var v : neighbours) {
                boolean redundant = false;
                for (var w : neighbours) {
                    if (w.equals(v)) continue;
                    if (reach.getOrDefault(w, Set.of()).contains(v)) {
                        redundant = true;
                        break;
                    }
                }
                if (!redundant) keep.add(v);
            }
            reduced.put(u, keep);
        }
        return reduced;
    }

    static void appendReproduceSection(StringBuilder md, String upstreamGa, String affectedGa) {
        md.append("## Reproduce This Build Locally\n\n");

        if (!matrixOs.isBlank() || !matrixJava.isBlank()) {
            md.append("> Built on **").append(matrixOs.isBlank() ? "unknown OS" : matrixOs)
              .append("** with **Java ").append(matrixJava.isBlank() ? "?" : matrixJava).append("**\n\n");
        }

        boolean isPr = !affectedGa.isBlank();
        if (isPr) {
            if (!upstreamGa.isBlank()) {
                md.append("**Step 1 — Build upstream dependencies (tests skipped):**\n\n");
                md.append("```bash\n");
                md.append("mvn -T 1C --batch-mode --no-transfer-progress -fae")
                  .append(" -DskipTests -Denforcer.skip=true -Dcheckstyle.skip=true -Dformatter.skip=true")
                  .append(" -Dsurefire.redirectTestOutputToFile=true")
                  .append(" -pl \"").append(upstreamGa).append("\"")
                  .append(" install\n");
                md.append("```\n\n");

                md.append("**Step 2 — Build changed and affected modules (with tests):**\n\n");
            } else {
                md.append("**Build changed and affected modules (with tests):**\n\n");
            }
            md.append("```bash\n");
            md.append("mvn --batch-mode --no-transfer-progress -fae")
              .append(" -Dsurefire.redirectTestOutputToFile=true")
              .append(" -pl \"").append(affectedGa).append("\"")
              .append(" install\n");
            md.append("```\n\n");
        } else {
            md.append("**Full build:**\n\n");
            md.append("```bash\n");
            md.append("mvn --batch-mode --no-transfer-progress -fae -Dsurefire.redirectTestOutputToFile=true install\n");
            md.append("```\n\n");
        }
    }

    static void appendModuleLists(StringBuilder md,
                                   Set<String> upstream, Set<String> affected,
                                   Set<String> changed, Set<String> skipped) {
        appendCollapsibleList(md, "Changed modules",  changed);
        appendCollapsibleList(md, "Upstream modules", upstream);
        appendCollapsibleList(md, "Affected modules", affected);
        appendCollapsibleList(md, "Skipped modules",  skipped);
    }

    static void appendCollapsibleList(StringBuilder md, String title, Collection<String> items) {
        if (items.isEmpty()) return;
        md.append("<details>\n<summary>").append(title).append(" (")
          .append(items.size()).append(")</summary>\n\n");
        items.forEach(m -> {
            md.append("- `").append(m.isEmpty() ? "." : m).append("`");
            if (bomPaths.contains(m)) md.append(" — _BOM_");
            md.append("\n");
        });
        md.append("\n</details>\n\n");
    }

    // ── Graph file loader ─────────────────────────────────────────────────────

    /**
     * Parses the TSV dump produced by the graph-dump Maven extension and
     * populates {@link #gaToPath}, {@link #allPaths} and {@link #upstreamsOf}.
     */
    static void loadGraphFile(Path file, Path root) throws IOException {
        var pendingEdges = new ArrayList<String[]>();  // {dependent-ga, dependency-ga}
        var pendingBoms  = new ArrayList<String>();    // BOM GAs
        for (var line : Files.readAllLines(file)) {
            var parts = line.split("\t", -1);
            if (parts.length < 2) continue;
            switch (parts[0]) {
                case "P" -> {
                    if (parts.length < 3) break;
                    var ga  = parts[1];
                    var abs = Path.of(parts[2]).toAbsolutePath().normalize();
                    var rel = root.relativize(abs).toString().replace('\\', '/');
                    gaToPath.put(ga, rel);
                    allPaths.add(rel);
                    upstreamsOf.computeIfAbsent(rel, k -> new LinkedHashSet<>());
                }
                case "D" -> {
                    if (parts.length < 3) break;
                    pendingEdges.add(new String[]{parts[1], parts[2]});
                }
                case "B" -> pendingBoms.add(parts[1]);
                default  -> { /* ignore unknown record types */ }
            }
        }
        // Resolve edges after all P lines have been seen, so forward references work.
        for (var e : pendingEdges) {
            var dependent  = gaToPath.get(e[0]);
            var dependency = gaToPath.get(e[1]);
            if (dependent != null && dependency != null && !dependent.equals(dependency)) {
                upstreamsOf.computeIfAbsent(dependent, k -> new LinkedHashSet<>()).add(dependency);
            }
        }
        for (var bomGa : pendingBoms) {
            var path = gaToPath.get(bomGa);
            if (path != null) bomPaths.add(path);
        }
    }

    // ── Surefire XML parsing ──────────────────────────────────────────────────

    static Suite parseSuite(Path root, Path xml) {
        try {
            var ts = parseXml(xml).getDocumentElement();

            int tests    = intAttr(ts, "tests");
            int failures = intAttr(ts, "failures");
            int errors   = intAttr(ts, "errors");
            int skipped  = intAttr(ts, "skipped");

            // Derive module: root/<module>/target/[surefire|failsafe]-reports/TEST-X.xml
            var rel = root.relativize(xml).toString().replace('\\', '/');
            var sep = rel.indexOf("/target/");
            var module = sep < 0 ? "" : rel.substring(0, sep);

            var fails = new ArrayList<Fail>();
            var tcs   = ts.getElementsByTagName("testcase");
            for (int i = 0; i < tcs.getLength(); i++) {
                var tc  = (Element) tcs.item(i);
                var cls = tc.getAttribute("classname");
                var tst = tc.getAttribute("name");
                for (var tag : List.of("failure", "error")) {
                    var nl = tc.getElementsByTagName(tag);
                    if (nl.getLength() > 0) {
                        var el = (Element) nl.item(0);
                        fails.add(new Fail(cls, tst, el.getAttribute("type"), el.getAttribute("message")));
                    }
                }
            }

            return new Suite(module, tests, failures, errors, skipped, fails);
        } catch (Exception e) {
            System.err.printf("[CI] Skipping %s: %s%n", xml, e.getMessage());
            return null;
        }
    }

    // ── XML helpers ───────────────────────────────────────────────────────────

    static Document parseXml(Path file) throws Exception {
        var fac = DocumentBuilderFactory.newInstance();
        fac.setNamespaceAware(false);
        // Prevent XXE and avoid slow network lookups for DTDs
        fac.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        fac.setFeature("http://xml.org/sax/features/external-general-entities", false);
        fac.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return fac.newDocumentBuilder().parse(file.toFile());
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /**
     * Parses comma-separated {@code groupId:artifactId} tokens and maps each to
     * its module path via {@link #gaToPath}. Unknown GAs are logged and dropped.
     * Requires {@link #loadGraphFile} to have been called first.
     */
    static Set<String> resolveGas(String gas) {
        if (gas == null || gas.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(gas.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .map(ga -> {
                var path = gaToPath.get(ga);
                if (path == null) log("Warning: no reactor module for %s", ga);
                return path;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Converts a module path to a safe Mermaid node ID. */
    static String mermaidId(String path) {
        return "N_" + (path.isEmpty() ? "root" : path.replaceAll("[^a-zA-Z0-9]", "_"));
    }

    /** Returns the last path segment (directory name), which is typically the artifactId. */
    static String shortName(String path) {
        if (path.isEmpty()) return "root";
        var idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    static int intAttr(Element el, String name) {
        try { return Integer.parseInt(el.getAttribute(name)); } catch (Exception e) { return 0; }
    }

    static String env(String name, String def) {
        var v = System.getenv(name);
        return v != null && !v.isBlank() ? v : def;
    }

    static void log(String fmt, Object... args) {
        System.out.printf("[CI] " + fmt + "%n", args);
    }
}
