# Settings

TenorClef settings live in `altoclef/altoclef_settings.json` under the game directory (created on first
launch). Edit the file, then run `@reload_settings` in game. The authoritative list, with documentation per
field, is `src/main/java/adris/altoclef/Settings.java`. Movement settings (`movementBackend`,
`kinematicTravel`, `allowLadderClutch`, …) belong to Ostinato and are changed with `#set` there; see the
[Ostinato settings guide](https://github.com/vexrypt-rgb/Ostinato/blob/main/docs/guides/settings.md).

## Interface and logging
| Setting | Default | Meaning |
| --- | --- | --- |
| `commandPrefix` | `@` | chat command prefix |
| `chatLogPrefix` | `[Alto Clef] ` | prefix on the bot's chat lines |
| `logLevel` | `NORMAL` | verbosity |
| `showTaskChains`, `showTimer`, `showDebugTickMs` | on, on, off | on-screen debug |
| `hideAllWarningLogs` | off | hide warnings |

## Survival (see [Combat and survival](combat-and-survival.md))
`mobDefense`, `forceFieldStrategy` (`FASTEST`/`DELAY`/`SMART`/`OFF`), `dodgeProjectiles`,
`killOrAvoidAnnoyingHostiles`, `avoidDrowning`, `extinguishSelfWithWater`, `autoEat`, `autoMLGBucket`,
`autoRespawn`, `autoReconnect`, `autoCloseScreenWhenLookingOrMining`.

## Resources and inventory
| Setting | Default | Meaning |
| --- | --- | --- |
| `resourceMineRange` | 100 | how far to look for blocks to mine |
| `resourceChestLocateRange` | 500 | how far to look for chests |
| `resourcePickupDropRange` | -1 | how far to chase dropped items (-1 = automatic) |
| `entityReachRange` | 4 | reach used when attacking |
| `containerItemMoveDelay` | 0.2 | seconds between container item moves |
| `useCraftingBookToCraft` | true | recipe-book crafting (disabled on 1.21.4, whose servers do not sync recipes) |
| `collectPickaxeFirst` | true | get a pickaxe before other work |
| `replantCrops` | true | replant when harvesting food |
| `minimumFoodAllowed`, `foodUnitsToCollect` | 0, 0 | when and how much to forage |
| `avoidSearchingDungeonChests`, `avoidOceanBlocks` | true, false | avoid risky areas |
| `throwawayItems`, `importantItems`, `supportedFuels` | lists | what may be dropped / must be kept / burns |
| `throwAwayUnusedItems`, `reservedBuildingBlockCount` | true, 64 | inventory clean-up |
| `dontThrowAwayCustomNameItems`, `dontThrowAwayEnchantedItems` | true | keep named / enchanted items |
| `limitFuelsToSupportedFuels` | true | only burn `supportedFuels` |
| `areasToProtect` | none | regions to leave alone |
| `netherFastTravelWalkingRange` | 600 | distance above which Nether travel is preferred |

## Automation
| Setting | Meaning |
| --- | --- |
| `autoRunCommand` | command run once after the world loads (how all tests here start) |
| `autoLoadWorld`, `autoLoadWorldName` | load a singleplayer world at start |
| `idleCommand` | command to run when the bot has nothing to do |
| `deathCommand` | command to run after a death |

## Speedrun
`speedrunPearlTarget` (14), `speedrunBlazeRodTarget` (7), `speedrunSkipFood` (false),
`speedrunMoverPreference` (`ostinato`), `speedrunVerbose` (false), `speedrunPhaseChatHud` (true). These are
the defaults for `@testrun` flags.

## Butler and SIGIL (`ButlerConfig`)
`useButlerWhitelist`, `useButlerBlacklist`, `requirePrefixMsg`, `sendAuthorizationResponse`,
`whisperFormatDebug`, `sigilRequireSealed`, `sigilReplySealed`, `sigilAutoDecrypt`. See
[Swarm, fleet and agents](swarm-and-fleet.md).

## JVM flags used for testing
| Flag | Effect |
| --- | --- |
| `-Dtenorclef.swarm.log=true` | log swarm ledger events on the leader |
| `-Dtenorclef.swarm.tp="x y z"` | teleport on `@swarm local`/`djoin` (use open ground) |
| `-Dostinato.simbench=…` | Ostinato simulation benchmark (see Ostinato guides) |
