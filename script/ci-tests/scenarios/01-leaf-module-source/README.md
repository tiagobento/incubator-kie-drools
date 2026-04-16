# 01 — Leaf-module source change

**Input:** a single `.java` file in `drools-beliefs`, a pure leaf module
(nothing in the reactor depends on it).

**What this tests:** the minimal cascade case. A leaf change must resolve
to exactly one module in `affected`, with `upstream` containing only the
module's own transitive reactor dependencies. No downstream propagation
should happen.

**Expected structure:**
- `affected` = `{org.drools:drools-beliefs}`
- `upstream` = transitive reactor deps of `drools-beliefs` (≈26 modules)
- `affected ∩ upstream = ∅`

**Why snapshot:** if `drools-beliefs` picks up any reactor-internal
downstream dependents (someone starts depending on it), the `affected` set
grows — the test breaks and forces that coupling to be reviewed.
