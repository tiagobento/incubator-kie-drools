# CiComputeBuildScopes snapshot scenarios

Each subdirectory is a scenario consumed by
[`CiComputeBuildScopesTest.java`](../CiComputeBuildScopesTest.java). The runner
feeds `changed-files.txt` to `CiComputeBuildScopes` and asserts the produced
`upstream` / `affected` lists match the committed `expected-upstream.txt` /
`expected-affected.txt` files.

## Naming

Scenarios are numbered (`01-…`, `02-…`, …) so new ones append without
renumbering. The name after the number is descriptive but not load-bearing —
if it needs to change, rename the directory without renumbering. Each scenario
has its own `README.md` explaining the case it covers.

## Coverage matrix

| #  | Scenario                         | What it exercises                                                  |
|----|----------------------------------|--------------------------------------------------------------------|
| 01 | `leaf-module-source`             | single source change in a pure leaf (no downstream)                |
| 02 | `hub-module-source`              | single source change in a hub module (large downstream cascade)    |
| 03 | `root-pom`                       | root `pom.xml` change → whole reactor via parent-pom edges         |
| 04 | `two-independent-leaves`         | two unrelated leaves changed (set union, no overlap)               |
| 05 | `upstream-downstream-pair`       | two modules where one depends on the other (downstream subsumed)   |
| 06 | `aggregator-pom`                 | intermediate aggregator `pom.xml` → children via parent-pom edges  |
| 07 | `removed-module`                 | paths under a now-deleted module → walk up to the surviving parent |
| 08 | `combined-mixed-changes`         | mix of source + aggregator-pom + hub + removed paths               |

### Notes on "adds a module" and "removes a module"

* **Adding a module.** In a real PR, the new module's files are on disk once
  CI checks out the branch, so `CiComputeBuildScopes` maps them like any
  other module — there is nothing structurally different to exercise. The
  operation that *defines* the addition from the script's perspective is the
  parent-pom change that registered the new `<module>` entry, and that is
  covered by scenario **06** (`aggregator-pom`): the parent-pom edges
  dumped by the graph extension cascade into every child, new or existing.

* **Removing a module.** The removed module is absent from both the
  filesystem and the reactor graph, so paths under it walk up to the
  surviving parent's `pom.xml` — covered by scenario **07**
  (`removed-module`). This verifies the walk-up logic handles
  non-existent paths without crashing and converges on the nearest still-
  present ancestor module.

## Updating the golden files

Initial generation or any intentional drift:

```bash
mvn -f script/ci/extension/pom.xml install
export GRAPH_EXTENSION_JAR=$HOME/.m2/repository/local/tools/graph-dump-extension/1.0.0/graph-dump-extension-1.0.0.jar
CI_UPDATE_GOLDEN=1 jbang script/ci-tests/CiComputeBuildScopesTest.java
git add script/ci-tests/scenarios/*/expected-*.txt
```

If a CI run fails because the cascade shifted (a module moved, a dep was
added or dropped, a module was added/removed from the reactor), regenerate
and commit the updated goldens in a **separate, dedicated PR** — reviewers
can then judge whether the shift is expected.

## Adding a new scenario

1. `mkdir script/ci-tests/scenarios/NN-short-name/`
2. Write `changed-files.txt` with real repo-relative paths (or non-existent
   paths to simulate deletions).
3. Write a short `README.md` explaining the case and the expected structure.
4. Run with `CI_UPDATE_GOLDEN=1` to generate the `expected-*.txt` files.
5. Commit everything.
