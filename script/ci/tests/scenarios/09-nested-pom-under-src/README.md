# 09 — Nested `pom.xml` under `src/`

**Input:** a file inside
`kie-maven-plugin/src/it/kie-maven-plugin-test-kjar-3/` — a Maven
invoker fixture that is itself a Maven project (has its own `pom.xml`)
but is **not** part of the reactor.

**What this tests:** the walk-up-to-nearest-pom logic must skip any
`pom.xml` that lives under a `src/` folder (invoker fixtures under
`src/it/`, test projects under `src/test/resources/`, etc.). Those poms
exist on disk but aren't registered in the reactor, so the nearest
*reactor* pom is the enclosing module — `kie-maven-plugin` in this
case.

**Expected structure:**
- `changed` = `org.kie:kie-maven-plugin` (the enclosing reactor module,
  **not** the nested fixture pom)
- `affected` / `upstream` follow from `kie-maven-plugin`'s position in
  the reactor graph

**Why snapshot:** without the `src/` skip, the walk-up stops at the
fixture pom and `CiComputeBuildScopes` emits a
`warn: no maven project at …` plus an empty scope — silently causing
the build step to do nothing on PRs that only touch invoker fixtures.
