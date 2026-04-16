# 05 — Upstream/downstream pair changed simultaneously

**Input:** one source file in `drools-core` and one in `drools-kiesession`.
`drools-kiesession` depends directly on `drools-core`.

**What this tests:** when the two changed modules already share an
upstream/downstream relationship, the downstream module (`drools-
kiesession`) is already inside `drools-core`'s cascade. The final
`affected` set must be the same as scenario **02** (hub-module change),
not a "bigger" set — deduplication must hold.

Conversely, `drools-kiesession` must **not** appear in `upstream` even
though it is a dependency of several other affected modules, because
`affected` and `upstream` are disjoint by construction.

**Expected structure:**
- `affected` equals scenario 02's `affected` (or a superset by exactly
  `{org.drools:drools-kiesession}` if `kiesession` sits outside `core`'s
  downstream — but it does not, so the sets should match byte-for-byte)
- `upstream` equals scenario 02's `upstream` minus `drools-kiesession` (if
  `kiesession` was present there), and is unchanged otherwise
- `affected ∩ upstream = ∅`

**Why snapshot:** catches regressions where the traversal forgets to
deduplicate or accidentally leaks a just-affected module into `upstream`.
