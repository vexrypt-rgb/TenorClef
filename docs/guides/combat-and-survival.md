# Combat and survival

Two layers cooperate. **TenorClef** decides *when* to fight, flee, eat or recover (Mob Defense, the threat
monitor, goals). **Ostinato** performs the fighting and movement itself (its PvP process, the water-bucket
and ladder clutches, pillaring). See the [Ostinato combat guide](https://github.com/vexrypt-rgb/Ostinato/blob/main/docs/guides/combat-and-clutch.md).

## Automatic behaviours (settings, all on by default)
| Setting | Behaviour |
| --- | --- |
| `mobDefense` | Uses a kill aura to push mobs away; runs from creepers about to blow, from wither skeletons and other very dangerous mobs, and from hostiles when health is low |
| `forceFieldStrategy` | How the aura attacks: `FASTEST` (every frame), `DELAY` (when the attack is charged), `SMART` (at most every 0.2 s, default), `OFF` |
| `dodgeProjectiles` | Try to dodge incoming arrows and other projectiles (needs `mobDefense`) |
| `killOrAvoidAnnoyingHostiles` | Kill or run from mobs (skeletons, groups) that stay close too long |
| `autoEat` | Eat when hungry or in danger |
| `autoMLGBucket` | Place a water bucket to cancel fall damage when knocked off course |
| `extinguishSelfWithWater` | Put out fire with water when not fire-immune |
| `avoidDrowning` | Do not sink when Baritone is not moving the bot |
| `autoRespawn`, `autoReconnect` | Respawn on the death screen; reconnect after a disconnect |

When Mob Defense interrupts a running `@goal`/`@get`, the task is suspended and resumed afterwards. Since
v0.24.0 a suspended `@goal` is not cancelled by the interruption.

## Commands
| Command | What it does |
| --- | --- |
| `@hero` | Hunt and kill all hostile mobs around |
| `@pvp` / `@pvp players` | Fight the nearest player |
| `@pvp mobs` | Fight hostile mobs |
| `@pvp bench N` | Singleplayer PvP bench with `N` rounds |
| `@t2fight` | Fight the nearest hostile with whatever tool/weapon is best |
| `@threat` | Print the threat monitor: assessment, `timeToDanger`, suggested recovery, active goal |
| `@idle` | Stand still |
| `@stop` | Cancel everything |

`@pvp` here hands the fight to the Ostinato PvP process: crit chaining, W-taps, hit selection, jump resets
on knockback, strafing, axe against raised shields, shield against bows, a bow at range, golden apples and an
offhand totem when low. Ostinato also has its own `#pvp <name>|players|stats|enemies|clear` for players and `#pve [hostiles|<mob id>|stats|clear]` for mobs (Ostinato 1.19.0+; `#pvp hostiles` now runs `#pve`). `@pvp mobs` uses PvE on Ostinato builds that have it.

## Falls and ladders
`autoMLGBucket` uses a water bucket. Ostinato's `allowLadderClutch` (off by default) can instead place a
ladder or vine on a wall during a long fall, and `pickupLadders` picks it up again. This needs one on the
hotbar; details in the Ostinato guide.

## Death handling
- `autoRespawn` clicks Respawn. `deathCommand` can run a command after death.
- `@back` walks to the death position written to `altoclef/last_death.txt`.
- Known gap: the `/kill @s` path through the death-menu chain was not observed in a live run.

## Honest limits
Combat quality was tuned mostly against one opponent bot and singleplayer benches; do not assume it is
tournament-grade against real players. Creeper handling and some fall scenarios are still listed as open in
the project notes.
