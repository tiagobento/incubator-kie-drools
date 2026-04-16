# CI scripts

Used by [.github/workflows/ci.yaml](../../.github/workflows/ci.yaml).

- [CiComputeBuildScopes.java](CiComputeBuildScopes.java) — changed files → `changed` / `affected` / `upstream` module lists.
- [CiSummary.java](CiSummary.java) — Surefire XML → `$GITHUB_STEP_SUMMARY`.
- [extension/](extension/) — Maven extension that dumps the reactor graph. Required by `CiComputeBuildScopes`.
- Tests: [../ci-tests/CiComputeBuildScopesTest.java](../ci-tests/CiComputeBuildScopesTest.java).

## Local run

`CiComputeBuildScopes` builds the extension on first run (and rebuilds it whenever
the pom or sources are newer than the jar). Set `GRAPH_EXTENSION_JAR` to override.

```bash
export MAVEN_DEPENDENCY_GRAPH_FILE=/tmp/graph.tsv

git diff --name-only $BASE_REF > /tmp/changed_files.txt
jbang script/ci/CiComputeBuildScopes.java /tmp/changed_files.txt /tmp/upstream.txt /tmp/affected.txt /tmp/changed.txt

UPSTREAM=$(paste -sd, /tmp/upstream.txt); AFFECTED=$(paste -sd, /tmp/affected.txt)
[ -n "$UPSTREAM" ] && mvn -B -fae -DskipTests -pl "$UPSTREAM" install
[ -n "$AFFECTED" ] && mvn -B -fae -pl "$AFFECTED" install

MAVEN_PL_CHANGED=$(paste -sd, /tmp/changed.txt) MAVEN_PL_AFFECTED="$AFFECTED" MAVEN_PL_UPSTREAM="$UPSTREAM" \
  jbang script/ci/CiSummary.java
```

`upstream` and `affected` are disjoint — do not pass `-am`/`-amd`.
