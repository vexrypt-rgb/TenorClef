# Swarm, fleet and agents

Several mechanisms let more than one bot, or an outside program, drive TenorClef. They are layered:

| Layer | Command | Use it for |
| --- | --- | --- |
| Butler | `@butler` | other *players* asking the bot for something by whisper |
| Fleet | `@fleet` | a trusted list of your own bots; simple "everyone get N of X" |
| **Swarm** | `@swarm` | a leader assigning objectives to workers, with verification and failure handling |
| Agent | `@agent`, `@ado` | a local program or LLM driving the bot through files/JSON |
| SIGIL | `@seal` | encrypted text to a circle or a contact |

(Ostinato has its own, separate `#swarm` for signed region builds; see the
[Ostinato swarm guide](https://github.com/vexrypt-rgb/Ostinato/blob/main/docs/guides/swarm-and-region-builds.md).
Do not confuse `@swarm` — TenorClef — with `#swarm`.)

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

## Swarm: leader and workers (new in v0.24.0)
The swarm decides *which agent does which objective*, then *checks the result*. TenorClef decides what;
Ostinato does the walking and mining.

### Setup
1. On every bot: `@fleet add <leader>` and `@fleet add <worker>`; the bots must be able to whisper each other.
2. Leader: `@swarm lead`.
3. Each worker: `@swarm join <leaderName> [CAN_MINE,CAN_FIGHT,…]` (no capability list = all capabilities).
4. Leader: `@swarm acquire <item> [n]`.

### Commands
| Command | Meaning |
| --- | --- |
| `@swarm lead` | become the coordinator |
| `@swarm join <leader> [caps]` | register as a worker; replaces any previous worker session |
| `@swarm leave` | cancel current work and say goodbye |
| `@swarm acquire <item> [n]` | submit an objective (priority 5, any capability) |
| `@swarm status` (default) | agents, assignments, event count, failure count |
| `@swarm why <id>` | the ledger's event-by-event story for one assignment |
| `@swarm cancel <id>` | cancel an assignment |
| `@swarm local [item] [n]` | one-client loopback: leader and a worker in the same process (testing) |
| `@swarm dlead <worker> [n]`, `@swarm djoin <leader>` | two-client test helpers (open LAN on port 25599, offline mode) |

### How an assignment lives
`UNASSIGNED → ASSIGNED → ACCEPTED → RUNNING → SUCCEEDED / FAILED / CANCELLED`.
The leader scores connected, ready agents deterministically, offers the work, and the worker accepts or
rejects. A worker runs the objective as an `@goal` acquisition and reports progress. **Success is never taken
on trust:** the worker sends its evidence (item count) and the leader verifies it against the objective.

Failures are classified (`AGENT_LOST`, `OFFER_TIMEOUT`, `REJECTED`, `ABANDONED`, `RUN_TIMEOUT`,
`UNVERIFIED_SUCCESS`, `REPORTED`) and retried up to `maxAttempts` (3). Defaults: heartbeat timeout 15 s,
offer timeout 10 s, run timeout 10 min, abandon grace 5 s, retry delay for situational failures 10 s. A worker
that makes no movement or inventory progress for 60 s reports `STALLED:<status>`.

### What has and has not been verified
- Verified live: single client (`@swarm local`) and two clients over whispers, ending
  `TASK_SUCCEEDED … verified have=3`; loss of a worker detected after about 15 s, with the assignment requeued.
- Not verified live: reassignment to a *second live* worker (needs three clients), a worker re-registering
  after restart, objective kinds other than ACQUIRE, beliefs feeding scheduling, automatic reservations.
- Played live on 1.21.11 only; other versions build but were not run in a world.
- Unit tests: `SwarmCoordinatorTest` (16 tests).

### Wire format
`s1 <op> k=v …` carried inside fleet whispers. Agent→leader: `reg hb accept reject prog result obs bye`.
Leader→agent: `offer cancel`.

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
