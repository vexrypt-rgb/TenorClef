# Agent JSON protocol (Phase 9)

Structured request/response over the existing `@agent` / file channel.
**Does not** replace chat commands (`@agent ask …`, `@get`, `@goal`, …).

## Hypothesis

JSON lines (or a `request.json` drop) on the existing agent file/command channel
are enough. `AgentRequestHandler` routes into `GoalManager` / `TaskCatalogue`
without new networking.

## Schema

### Request

```json
{
  "id": "string",
  "action": "get|acquire|goal|status|snap|cancel",
  "parameters": { "key": "value" }
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `id` | recommended | Echoed on the response for correlation |
| `action` | **yes** | See supported actions |
| `parameters` | no | String / number / boolean values (flattened to strings) |

Top-level convenience keys `item`, `resource`, `name`, `count`, `mode` are
copied into `parameters` if present.

### Response

```json
{
  "id": "string",
  "status": "accepted|running|success|failure|blocked|cancelled",
  "result": { "key": "value" },
  "error": "optional message"
}
```

| Status | Meaning |
|--------|---------|
| `accepted` | Request taken; work started (e.g. GoalManager running) |
| `running` | In progress (status query while busy) |
| `success` | Completed or idle success (e.g. status when not busy) |
| `failure` | Rejected / failed (bad action, missing item, catalogue miss) |
| `blocked` | Cannot run now (e.g. not in game) |
| `cancelled` | Cancel applied |

## Supported actions

| Action | Parameters | Behavior |
|--------|------------|----------|
| `get` / `acquire` | `item` (or `resource`/`name`), `count` (default 1) | Start Phase 7 `AcquireItemGoal` via `GoalManager` + `PlanRunnerTask` |
| `goal` | same | Same path as acquire (explicit goal verb) |
| `status` / `snap` / `snapshot` | — | Structured snapshot from `WorldKnowledge` + active goal/task/threat (partial OK) |
| `cancel` | — | Cancel active `GoalManager` (if any) + `cancelUserTask()` when safe |

Unsupported actions → `failure` with an error listing supported names.

## Transport

No new sockets. Prefer one of:

1. **Chat:** `@agent json {"id":"1","action":"status"}`
2. **Ask with JSON:** `@agent ask {"id":"1","action":"get","parameters":{"item":"cobblestone","count":64}}`
3. **Inbox line:** append a JSON object line to `<gameDir>/altoclef/agent/inbox.txt` while `@agent on`
4. **File drop:** write `<gameDir>/altoclef/agent/request.json` (cleared after take)

Responses are written to `response.json` and appended to `outbox.log`.

Legacy whitelist verbs (`get`, `xget`, `goto`, …) in `inbox.txt` still go through
`AgentActions` unchanged.

## Types (package `adris.altoclef.agent`)

| Type | Role |
|------|------|
| `AgentRequest` / `AgentResponse` / `AgentStatus` | Schema |
| `AgentJson` | Hand-rolled parse/serialize (offline-testable) |
| `AgentRuntime` | Side-effect boundary (fakes in tests) |
| `AgentRequestHandler` | Dispatch |
| `AgentProtocol` | Parse → handle → serialize facade |
| `AltoClefAgentRuntime` | Live GoalManager / WorldKnowledge / cancel |

## Examples

```text
@agent json {"id":"1","action":"get","parameters":{"item":"cobblestone","count":64}}
@agent json {"id":"2","action":"status"}
@agent json {"id":"3","action":"cancel"}
```

Example response:

```json
{"id":"1","status":"accepted","result":{"item":"cobblestone","count":"64","goalId":"acquire:cobblestone","goalStatus":"RUNNING","mode":"goal","planSteps":"1","firstStep":"collect 64 cobblestone","note":"started"}}
```

## Out of scope

- Full LLM tool-calling loop
- Telemetry / benchmarks (Phase 10)
- Breaking `@agent` / `@goal` / `@get`
