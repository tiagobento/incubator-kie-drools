# 10 — Plugin `<dependency>` counts as a reactor dependency

**Input:** any change inside
`drools-test-coverage/test-suite` (`org.drools.testcoverage:drools-test-suite`).

**What this tests:** that a `<dependency>` declared *inside a build
plugin* (`maven-surefire-plugin` → `<dependencies>` → `org.kie:kie-maven-plugin`)
is treated as a real reactor edge by the dep-graph extractor — i.e. it
shows up in `upstream` for the consuming module exactly as a regular
`<dependency>` would.

**Expected structure:**
- `changed`  = `{org.drools.testcoverage:drools-test-suite}`
- `affected` = `drools-test-suite` + its reactor downstreams
- `upstream` MUST contain `org.kie:kie-maven-plugin` (and its transitive
  reactor deps), proving the plugin-level dependency was followed

**Why snapshot:** if the extractor stops walking plugin-level
`<dependencies>`, `kie-maven-plugin` silently drops out of `upstream`
and the wrong subset of the reactor gets rebuilt. The test breaks
loudly so the regression can't slip through. Regenerate with
`CI_UPDATE_GOLDEN=1`.
