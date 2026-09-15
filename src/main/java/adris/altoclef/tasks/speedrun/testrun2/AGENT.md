# @agent — API / AI control (v0)

Does not change `@testrun2`, `@aa`, `@get`, `@schem`.

## In game

```
@agent on          # start loop
@agent snap        # print snapshot
@ado xget bread    # queue an action
@ado goto 10 64 -4
@ado stop
@agent off
```

## Files

`<gameDir>/altoclef/agent/`

| File | Direction |
|---|---|
| `snapshot.json` | bot → agent, rewritten ~every 2s |
| `inbox.txt` | agent → bot, one command per line, consumed |
| `outbox.log` | append-only results |

Example snapshot:

```json
{"t":"1:02.0","dim":"OVERWORLD","xyz":[12,64,-8],"hp":18,"hunger":7,"phase":"AGENT","child":"-","inv":{"cobblestone":32}}
```

Example external loop (Python):

```python
from pathlib import Path
p = Path(r"C:\Users\redfa\Documents\MinecraftDev\altoclef\versions\1.16.5\run\altoclef\agent")
snap = (p / "snapshot.json").read_text()
# call your API with snap, get back "xget bread"
(p / "inbox.txt").write_text("xget bread\n")
```

## Allowed verbs

`get` `xget` `food` `goto` `wait` `say` `idle` `stop` `testrun2` `aa` `t2core` `equip`

Anything else is logged `DENY` and ignored. No raw Java, no `#build`.
