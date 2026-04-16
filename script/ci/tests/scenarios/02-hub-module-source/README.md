# 02 — Hub-module source change

**Input:** a single `.java` file in `drools-core`, a central hub on which
most of the reactor transitively depends.

**What this tests:** the large-cascade case. A hub-module change must pull
its entire transitive downstream into `affected`, while its own intra-
reactor dependencies end up in `upstream`. The two sets are large but
still disjoint.

**Expected structure:**
- `affected` includes `org.drools:drools-core` plus ~135 downstream modules
- `upstream` includes `drools-core`'s ~12 intra-reactor dependencies
- `affected ∩ upstream = ∅`

**Why snapshot:** `drools-core`'s position in the graph is load-bearing
for build performance. If a module silently starts or stops depending on
`drools-core` (directly or transitively), the cascade shifts. The test
break draws a reviewer's attention to that.
