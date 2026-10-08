# Swarm, fleet and agents

Several mechanisms let more than one bot, or an outside program, drive TenorClef. They are layered:

| Layer | Command | Use it for |
| --- | --- | --- |
| Butler | `@butler` | other *players* asking the bot for something by whisper |
| Fleet | `@fleet` | a trusted list of your own bots; simple "everyone get N of X" |
| **Swarm** | `@swarm` | a leader assigning objectives to workers over Ostinato's swarm link, with verification and failure handling |
| Agent | `@agent`, `@ado` | a local program or LLM driving the bot through files/JSON |
| SIGIL | `@seal` | encrypted text to a circle or a contact |

(`@swarm` is TenorClef's objectives; `#swarm` is Ostinato's link and signed region builds. They share one
link: see the [Ostinato swarm guide](https://github.com/vexrypt-rgb/Ostinato/blob/main/docs/guides/swarm-and-region-builds.md).)

## Butler: remote control by whisper
Players on the whitelist can whisper `@get iron_pickaxe` to the bot. Settings (`ButlerConfig`):
`useButlerWhitelist` (default on), `useButlerBlacklist` (on), `requirePrefixMsg` (on), `sendAuthorizationResponse`.

- `@butler` prints the current configuration; `@butler allow <name>` appends the name to
  `altoclef_butler_whitelist.txt` (reload the world or restart if it does not take effect).
- `@reload_settings` re-reads settings and the butler lists.
- A whisper can **never** run `escape`, `agent`, `ado`, `t2reset`, `reset`, `mapart`, `aa` or `testrun2`, and a
  user sending more than ~5 commands within 1.5 s each is rate limited (`ButlerGuard`).
- With SIGIL options (`sigilRequireSealed`, `sigilReplySealed`, `sigilAutoDecrypt`) sealed requests get
  sealed replies; unsealed requests are answered normally unless you require sealing.

## Fleet: your own bots
`@fleet` (alias `@link`) keeps a list of trusted usernames and sends bot-to-bot whispers (`~ ping`,
`~ pong`, `~ do get <item> <n>`, `~ done <item>`). Only listed players are trusted.

| Command | Effect |
| --- | --- |
| `@fleet` | show members and usage |
| `@fleet add <IGN>` / `remove <IGN>` | edit the list (do this on **both** bots) |
| `@fleet ping` | broadcast a ping |
| `@fleet do <item> [n]` | ask every other member for `n/members` of the item (default 16) |

Fleet replies are not sealed.

## Swarm: leader and workers
The swarm decides *which agent does which objective*, then *checks the result*. TenorClef decides what;
Ostinato does the walking and mining. It is **not a separate network**: objective messages ride Ostinato's
swarm link (sealed, signed, replay-checked, roster-authenticated) as message type `TCS`. Who may talk and who
leads come from Ostinato's roster (`swarm.txt`, `lead=`). Read the
[Ostinato swarm guide](https://github.com/vexrypt-rgb/Ostinato/blob/main/docs/guides/swarm-and-region-builds.md)
first for the one-time setup (`swarmEnabled true`, roster, SIGIL circle).

### Easiest: the Swarm tab
Open the TenorClef menu and pick **Swarm**. The top line tells you what is wrong or what to do next.
- Leader: **Lead the swarm**, then type an item and count and press **Gather it**.
- Helpers: **Join the leader**.
- **Build it** sends a schematic name (a file in `run/schematics`) to Ostinato's `#swarm build`.
- **Stop everything** cancels all jobs and stops the builders. **Leave** says goodbye.
Helpers are listed with ready / busy / dead / offline, and the last jobs show their state and failure reason.

### Setup by command
1. Ostinato: `swarmEnabled true`, a roster group with `lead=`, a SIGIL circle. `#swarm status` should show the link up.
2. Leader: `@swarm lead` (refused unless you are a roster `lead=`).
3. Each helper: `@swarm join [CAN_MINE,CAN_FIGHT,…]`. It joins its roster lead automatically (no leader name); no list = all capabilities.
4. Leader: `@swarm acquire <item> [n]`.

### Commands
| Command | Meaning |
| --- | --- |
| `@swarm lead` | become the coordinator |
| `@swarm join [caps]` | register with your roster lead; replaces any previous worker session |
| `@swarm leave` | cancel current work and say goodbye |
| `@swarm acquire <item> [n]` | submit an objective (priority 5, any capability) |
| `@swarm status` (default) | agents, assignments, event count, failure count |
| `@swarm why [id]` | the ledger's event-by-event story for one assignment |
| `@swarm cancel [id]` | cancel an assignment; with no id, cancel every active one |
| `@swarm local [item] [n]` | one-client loopback: leader and a worker in the same process (testing) |

Developer-only test hooks: `@swarm host` (open the world to LAN on port 25599 in offline mode) and
`@swarm script <file>` (tail a file of `@…`, `#…`, `/…` and `wait N` lines). They exist for live testing.

### How an assignment lives
`UNASSIGNED → ASSIGNED → ACCEPTED → RUNNING → SUCCEEDED / FAILED / CANCELLED`.
The leader scores connected, ready agents deterministically, offers the work, and the worker accepts or
rejects. A worker runs the objective as an `@goal` acquisition and reports progress. **Success is never taken
on trust:** the worker sends its evidence (item count) and the leader verifies it against the objective.

Failures are classified (`AGENT_LOST`, `OFFER_TIMEOUT`, `REJECTED`, `ABANDONED`, `RUN_TIMEOUT`,
`UNVERIFIED_SUCCESS`, `REPORTED`) and retried up to `maxAttempts` (3). Defaults: heartbeat timeout 15 s,
offer timeout 10 s, run timeout 10 min, abandon grace 5 s, retry delay for situational failures 10 s. A worker
that makes no movement or inventory progress for 60 s reports `STALLED:<status>`. A worker that dies respawns
itself, and the leader marks it ready again when its heartbeats resume.

### What has and has not been verified
- Verified live on 1.21.11 over the Ostinato link (leader + one worker): register, assign/accept/progress,
  verified success (`acquire oak_log 1`), stall detection and diagnosis, `why`, `cancel <id>`, leave and
  re-join, worker death and recovery, heartbeat-silence disconnect, non-leader `lead` refusal, and Ostinato
  `#swarm status/ping/build/stop` (order partitioned over four roster members and acknowledged).
- Also verified live: reassignment to a second live worker after the first is killed, bare `@swarm cancel`, real block placement by `#swarm build`, and signed-sender mode (S2S, signets pinned; a verified `acquire`). Signed lines are larger, so the link allows 40 s heartbeat timeout / 30 s offer timeout and helpers heartbeat every 12 s.
- Not verified live: clicking the Swarm tab buttons (the tab itself was seen rendering), objective kinds
  other than ACQUIRE.
- Other Minecraft versions build but were not run in a world, and need an Ostinato build with `registerHandler`;
  older builds report "too old for TenorClef swarms".
- Unit tests: `SwarmCoordinatorTest`, `SwarmRuntimeLinkTest`.

### Wire format
`s1 <op> k=v …` inside one Ostinato message of type `TCS`. Agent→leader: `reg hb accept reject prog result obs bye`.
Leader→agent: `offer cancel`. Heartbeats carry health and position (`alive=0` when dead).

## Agent: files and JSON
`@agent on` starts a loop that writes a snapshot every 2 s. Files are under `<gameDir>/altoclef/agent/`:
`snapshot.json inbox.txt request.json response.json outbox.log`.

| Command | Effect |
| --- | --- |
| `@agent on` / `off` | start the loop / queue a stop |
| `@agent ask <command>` | queue one command line, e.g. `@agent ask xget bread` |
| `@agent json {…}` | dispatch a JSON request immediately |
| `@agent goal <text>` | set a free-text goal for the T2 agent |
| `@agent snap` | print the snapshot and protocol status |

Allowed verbs: `get xget food goto wait say idle stop testrun2 aa t2core equip`. JSON actions:
`get acquire goal status snap cancel` — schema in [AGENT_PROTOCOL.md](../AGENT_PROTOCOL.md).
`@ado <a> [b c d]` queues one action (`get`, `xget`, `food`, `goto`, `stop`, …) without the loop.

## SIGIL: `@seal`
`@seal <circle|player> <message>` encrypts the message and sends it as public chat lines (for a circle) or
whispers (for a contact). Facts to know before relying on it:
- The port is pinned to SIGIL 0.3.0; codebook v1 is unsupported.
- Passphrases and private keys are stored **in plaintext** under `altoclef/sigil/`.
- Sealed requests get sealed replies, or none; fleet replies are not sealed.
- The Java encoder writes integers above 32 bits as raw digits where the Python reference truncates.
