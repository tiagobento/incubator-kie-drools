# 03 — Root `pom.xml` change

**Input:** the repository-root `pom.xml`.

**What this tests:** root-pom changes must cascade to the entire reactor
via the parent-pom edges emitted by the dep-graph-extractor. Every
reactor module declares the root (directly or transitively) as its
parent, so modifying the root rebuilds everything.

**Expected structure:**
- `affected` = every reactor module (≈255)
- `upstream` is empty (the root has no intra-reactor dependencies)
- `affected ∩ upstream = ∅`

**Why snapshot:** the count is a coarse integrity check for parent-pom
edge emission. If the dep-graph-extractor ever stops emitting parent edges, or a
module's parent is moved out of the reactor, `affected` shrinks below
the reactor size and the test breaks.
