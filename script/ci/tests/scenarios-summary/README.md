# CiSummary snapshot scenarios

Each subdirectory is a self-contained fixture for `CiSummaryTest`.

Layout:

```
<scenario>/
  env.properties        KEY=VALUE lines, passed as env vars to CiSummary.
                        Common keys: MAVEN_PL_UPSTREAM, MAVEN_PL_AFFECTED,
                        MAVEN_PL_CHANGED, MERMAID_EXPANDED, MATRIX_OS, MATRIX_JAVA.
  graph.tsv             (optional) Dep-graph TSV. The token {ROOT} is replaced with
                        the absolute path to this scenario's root/ before being passed
                        to CiSummary via DEP_GRAPH_EXTRACTOR__OUTPUT_FILE.
  root/                 Acts as CI_REPO_ROOT. Place surefire/failsafe XML reports
                        under <module>/target/{surefire,failsafe}-reports/.
  expected-summary.md   Golden stdout of CiSummary (with [CI] log lines stripped).
```

Regenerate goldens after an intentional output change:

```bash
CI_UPDATE_GOLDEN=1 jbang script/ci/tests/CiSummaryTest.java
```
