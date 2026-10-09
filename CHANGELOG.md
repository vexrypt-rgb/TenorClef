# TenorClef 0.25.1

Pairs with Ostinato v1.18.0. Fixes the 1.16.1 and 26.3 jars of 0.25.0, which did not start. 1.21.4 and 1.21.11 are unchanged.

- **26.3**: two mixins no longer match 26.3's classes. The camera-angle hook is now optional (it was a no-op), and the chunk-load hook uses the 26.3 `ClientboundLevelChunkPacketData` signature, so chunk events work again.
- **1.16.1**: the mixin config no longer claims Java 21 (downgraded builds declare Java 8/17), and the bundled downgrader API is updated to 1.2.2 so `System.getProperty` and other downgraded calls resolve.
- Every release jar now joins a world and runs 45 s without a crash (smoke-tested with the released Ostinato jars; 1.16.1 on Java 8, the others on their own Java). Swarm features were not re-run in this smoke test.

# TenorClef 0.25.0

Pairs with Ostinato v1.18.0. The swarm needs this Ostinato: it uses the new `registerHandler` hook.

- **The swarm now rides Ostinato's swarm link.** `@swarm` uses the same sealed, signed chat link and roster (`swarm.txt`, `lead=`) as `#swarm`, so there is one swarm, not two. `dlead`/`djoin` and the separate whisper transport are gone.
- **Swarm tab in the TenorClef menu**: lead, join or leave the swarm, hand out gather and build jobs, and stop everything, with a plain-language status of every helper and job.
- `@swarm cancel` with no id cancels every active job. `@swarm lead` is refused on a member that is not the roster leader.
- Dead helpers respawn on their own (the autorun respawn counter bug is fixed) and report back to the leader.
- Heartbeats are slower (12 s) and link timeouts wider (40 s / 30 s), so signed (S2S) mode stays inside the link's rate limit.
- The Butler ignores swarm link traffic instead of answering "not authorized".
- Verified live with three clients: assignment, verified completion, stall diagnosis, reassignment to a second helper after the first died, cancel, `#swarm build` placing blocks, and signed S2S mode. Clicking the Swarm tab buttons has not been tried by hand.
- Guides rewritten: `docs/guides/swarm-and-fleet.md`.

# TenorClef 0.24.0

Pairs with Ostinato v1.1.4.

- Swarm system: `@swarm lead|join|acquire|status|why|cancel`. TenorClef decides what, Ostinato decides how. Assignments carry a lifecycle, agents a registry and capability scoring; every outcome is verified against inventory evidence, and `@swarm why <id>` explains it from the event ledger. Lost agents' work is requeued. Verified live with one and two clients.
- `@goal`/swarm tasks survive Mob Defense interruptions (the goal used to be cancelled), and a retried goal no longer reuses the dead task of its previous run.
- Showcase course offset works on 1.16.1.

# TenorClef 0.23.3

Pairs with Ostinato v1.0.8.

- Elytra mace modes now work: the elytra is equipped from the hotbar by inventory swap, takeoff uses a wind-charge boost when there are no rockets, gliding starts with a real jump press, and the chestplate is swapped back after landing.
- All combat actions go through real mouse/keyboard inputs (from v1.0.7).

# TenorClef 0.23.2

Pairs with Ostinato v1.0.7.

- Target velocity is derived from per-tick position deltas (remote players report zero), so shield/deflect counters against mace dives now fire.
- Pearl strike throws with lead on the target; the wind-charge pop of the pearl is kept.
- No jump-crits while the target is diving.

# TenorClef 0.23.1

Every build runs on [Ostinato](https://github.com/vexrypt-rgb/Ostinato) (the Baritone fork); 1.21.11 and 26.3 pair with Ostinato v1.0.5.

- PvP: raises the shield between its own swings, and follows an axe shield-breach with a fast sword hit.
- PvP: the crossbow now loads properly and aims over arrow drop instead of firing into the ground; the bow aims the same way.
- Fight recorder: counts landed hits from the hurt flash when a server hides player health, and no longer logs a death or a vanished target as a win.

# TenorClef 0.23.0

Every build runs on [Ostinato](https://github.com/vexrypt-rgb/Ostinato) v1.0.3.

- Ostinato PvP (`#pvp`): sword, axe, shield, bow, crossbow, cobweb, potion, crystal, anchor and mace styles,
  multi-opponent retargeting and automatic fight recording.
- Freecam enemy list: middle-click a player in freecam to mark an enemy.
- 1.21.4 build updated to the new Ostinato jar; 1.21.11 and 26.3 builds pair with Ostinato v1.0.4.

# TenorClef 0.22.2

Every build runs on [Ostinato](https://github.com/vexrypt-rgb/Ostinato) (the Baritone fork), not upstream Baritone.
Fixes on top of 0.22.0. (0.22.1 was tagged with incomplete notes; use 0.22.2.)

## Downloads

| Minecraft | TenorClef jar | Ostinato |
|---|---|---|
| 1.21.4 | `tenorclef-mc1.21.4-v0.22.2.jar` | Ostinato v1.0.2 `ostinato-mc1.21.4-*.jar` |
| 1.16.1 | `tenorclef-mc1.16.1-v0.22.2.jar` | Ostinato v1.0.2 `ostinato-mc1.16.1-*.jar` |
| 1.21.11 (experimental) | `tenorclef-mc1.21.11-v0.22.2.jar` | Ostinato v1.0.2 `ostinato-mc1.21.11-*.jar` |
| 26.3 (experimental) | `tenorclef-mc26.3-v0.22.2.jar` | Ostinato v1.0.2 `ostinato-mc26.3-*.jar` (Java 25) |

Install Fabric Loader, Fabric API, the TenorClef jar and the matching Ostinato jar. Do not add a second Baritone jar.

## Fixes

- Dive: the bot sinks under a roof edge instead of jumping into it, and no longer freezes against it.
- Swimming out onto a shallow shore ledge climbs it instead of sinking.
- Chat logs a "swim stall" line when a swim movement is stuck, to make reports easier to diagnose.

# TenorClef 0.22.0

This release rolls up everything since 0.20.0 (the 0.21.0 tag was never published).

## Highlights

- Freecam: `#freecam` with a GUI toggle, a ghost of the bot showing its current task, and an opacity slider. Includes fixes for respawn and a ghost mixin crash.
- Showcase command and tab: parkour, swim, dive, boat, kinematic and physics demos, with a sealed pool corridor so the bot has to swim.
- Movement fixes from the bundled Ostinato jars: swim entry off shallow ledges, waterfall swimming, boat placement, robust jump templates, scissor underflow.
- Starts on quick-play worlds; 1.21.4 GUI and dropdown fixes.

## Experimental builds

- 1.21.11 and 26.3 compile and package from the same source, but have not been played in-game. Treat them as previews.
