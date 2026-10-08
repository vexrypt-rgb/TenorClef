# Small-objective runs (1.16.1)

Fresh world each run (seed 12345, empty inventory, `movementbackend baritone`, autorun via
`altoclef_settings.json`). Evidence column says what was actually observed in `latest.log`.

| Date | Command | Result | Time | Evidence |
|---|---|---|---|---|
| 2026-09-28 | `get cobblestone 3` | finished | 30.8 s | vanilla advancement *Stone Age* (cobblestone obtained); count of 3 not independently checked |
| 2026-09-28 | `get cobblestone 3` (after Butler fix) | finished | 63.9 s | *Stone Age*; 0 whisper-loop lines (was 37) |
| 2026-09-28 | `get iron_pickaxe` | finished | 115.2 s | *Stone Age* → *Getting an Upgrade* → *Acquire Hardware* → *Isn't It Iron Pick* |
| 2026-09-28 | `get water_bucket` | finished | 122.3 s | *Acquire Hardware* (iron for the bucket); bucket fill is **task-reported only** (no vanilla advancement exists for it) |
| 2026-09-28 | `get iron_pickaxe` (rep 2) | finished | 110 s | *Stone Age* → *Getting an Upgrade* → *Acquire Hardware* → *Isn't It Iron Pick* |
| 2026-09-28 | `get iron_pickaxe` (rep 3) | finished | 111 s | same four advancements |
| 2026-09-28 | `get water_bucket` (rep 2) | finished | 441 s | *Acquire Hardware*; ~5 min stuck on an unreachable spruce log drop (see below) |
| 2026-09-28 | `get water_bucket` (rep 3) | finished | 135 s | *Acquire Hardware* |
| 2026-09-28 | `get water_bucket` (rep 4) | finished | 112 s | *Acquire Hardware* |
| 2026-09-28 | `get water_bucket` (rep 5) | finished | 142 s | *Acquire Hardware*; unreachable drop given up after 24 s, as designed |
| 2026-09-28 | `get water_bucket` (rep 6) | finished | 116 s | *Acquire Hardware* |
| 2026-09-28 | `get cobblestone 3` (rep 3) | finished | 35 s | *Stone Age* |

| 2026-09-28 | `goto 87 -5 -115` (below bedrock, unreachable) | aborted, as intended | 68 s | ALTERNATE_PATH 1/3 → RETRY 2/3 → RETRY 3/3 → "Progress retries exhausted, aborting goal"; after the verified fix: `outcome=FAILED reason=TIMEOUT verified=false` |

Recovery exercised once (the unreachable goto). Before the fix its TRAVEL line mislabelled the abort ARRIVED.
Repeats: iron_pickaxe 3/3 (110–115 s), water_bucket 6/6 (112–441 s), cobblestone 3/3 (31–64 s). Water bucket fill
is still task-reported only. Timings are not a benchmark.

**Sporadic slow pickup (1 of 6 water_bucket runs).** From 22:18:58 to 22:24:00 the bot kept trying to pick up an
unreachable spruce log drop before `PickupDroppedItemTask` gave up on it. In a normal run the same fallback fires
24 s after the task starts. Hypothesis, not confirmed: `PickupDroppedItemTask.onTick` resets its progress checker
whenever Baritone reports it is pathing, so a path that never reaches the drop can postpone the fallback
indefinitely. The same reset-while-pathing pattern appears in 12 task files, so it has not been changed without
a reproduction.
