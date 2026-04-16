# 04 — Two independent leaf modules changed

**Input:** one source file in `drools-beliefs` and one in `drools-fastutil`.
Both are leaves; neither depends on the other.

**What this tests:** the multi-module case with **no overlap** between the
two cascades. `affected` must be the set-union of each module's individual
cascade (here: just the two modules themselves, since both are leaves).
`upstream` must be the union of each module's transitive reactor deps,
with nothing added or double-counted.

**Expected structure:**
- `affected` = `{org.drools:drools-beliefs, org.drools:drools-fastutil}`
- `upstream` = union of both modules' transitive reactor deps
- `affected ∩ upstream = ∅`

**Why snapshot:** guards the union-logic against regressions that would
either miss one changed module or double-count its upstream.
