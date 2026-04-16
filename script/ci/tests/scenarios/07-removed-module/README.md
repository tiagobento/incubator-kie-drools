# 07 — Simulated module removal

**Input:** `drools-drl/pom.xml` plus two paths inside a *non-existent*
`drools-drl/drools-drl-removed-fixture/` directory. The fixture name is
deliberately one that does not (and must not) exist in the repo.

**What this tests:** the walk-up logic in `CiComputeBuildScopes`.
When a PR deletes a module, its directory and `pom.xml` are absent from
the HEAD filesystem and from the reactor graph. `CiComputeBuildScopes`
must walk up from each deleted path to the nearest *surviving* ancestor
`pom.xml` (here: `drools-drl/`) and map the deletion to that surviving
aggregator, rather than crashing or defaulting to the repo root.

The aggregator's parent-pom edges then cascade the change into every
surviving child, which is the correct "we removed a module from this
subtree" behavior.

**Expected structure:**
- `affected` equals scenario **06**'s `affected` (same aggregator,
  same cascade) — the deleted fixture path contributes nothing extra
  because it maps to the same `drools-drl` module
- `upstream` equals scenario 06's `upstream`
- `affected ∩ upstream = ∅`

**Why snapshot:** if the walk-up logic breaks (for example, stops early
and maps deleted paths to the repo root), `affected` would balloon to
the entire reactor. The test would then fail, drawing attention to the
regression.

> **Note:** do not create a real `drools-drl/drools-drl-removed-fixture/`
> directory in the repo — that would defeat the point of the scenario
> (the path must not resolve to an actual module).
