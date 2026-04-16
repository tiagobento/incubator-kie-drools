# 08 — Combined mixed changes

**Input:** a realistic multi-module PR combining several of the earlier
scenarios' building blocks in one changed-files list:
- an aggregator pom (`drools-drl/pom.xml`)
- a non-existent path under a removed sibling (simulated deletion)
- a leaf-module source (`drools-beliefs`)
- a hub-module source (`drools-core`)
- a downstream of that hub (`drools-kiesession`)

**What this tests:** end-to-end behavior when several of the interesting
cases interact in a single run:
- union across unrelated cascades (drools-drl subtree, drools-beliefs,
  drools-core) must be correct
- downstream subsumption still holds (`drools-kiesession` inside
  `drools-core`'s cascade)
- walk-up from deleted paths still resolves to the surviving aggregator
- `affected` and `upstream` remain disjoint under the full combination

**Expected structure:**
- `affected` = union of scenarios 06, 01, 02, 05 (minus duplicates)
- `upstream` = union of each above scenario's `upstream`, minus anything
  in the combined `affected`
- `affected ∩ upstream = ∅`

**Why snapshot:** catches interaction bugs that only surface when
multiple kinds of changes land together — exactly the shape of real PRs.
