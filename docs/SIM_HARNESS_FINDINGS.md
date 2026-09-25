# SIM / overnight harness — findings

**Date:** 2026-09-20 ~05:00 PT  
**Target machine:** `b7b8e2e7-d629-4a3e-9809-04f69b7bdd10`  
**Repo (Windows):** `C:\Users\redfa\Documents\MinecraftDev\altoclef`  
**Also searched:** Ostinato, Ostinato-1.16.1, `*sim*` trees (box mirrors + prior Windows-agent transcripts)

> **Executor note:** This pass ran with **box-bound Shell** (no `machineId` on Shell/Read). Live Windows probes (java/Minecraft process, `versions\1.16.1\run\crash-reports`, on-disk settings) were **not** run here. Parent should CopyFromBox this file to  
> `C:\Users\redfa\Documents\MinecraftDev\altoclef\docs\SIM_HARNESS_FINDINGS.md`  
> and confirm live state with a `machine: { sameMachine: {} }` executor.

---

## Executive answer

| Question | Answer |
|----------|--------|
| True headless / GameTest / dedicated-server **sim** for `@testrun2`? | **No.** |
| Overnight **live-client** automation harness? | **In progress.** Spec: `sim-harness/docs/SIM_HARNESS.md`. Script: `sim-harness/scripts/overnight-testrun2.ps1`. Prior art: `C:\Users\redfa\agent-tools\altoclef-auto\` (1.21-era create-world / launch / kill / overnight-loop). |
| Overnight without a human? | **Only via live Fabric `runClient` + UI focus** (create-world Tab/Enter or coords, then chat `@testrun2`). Not headless MC. |
| Verdict | **Finish/harden the live-client harness** — do not build a GameTest sim. Offline `BenchmarkHarness` is mock-only and does not drive `@testrun2`. |

---

## What was searched

### Named docs

| Path | Role |
|------|------|
| `OVERNIGHT_NOTES.md` (repo root mirrors) | No compile while `@testrun` live; create-world = Survival+Easy, never Hardcore; screenshot-read UI labels |
| `docs/BENCHMARKS.md` | Phase 10 **offline** Scenario / BenchmarkHarness — **not** live RSG |
| `docs/ROADMAP.md` / `ARCHITECTURE.md` | Out of scope: full in-game RSG harness |
| `docs/TECH_DEBT.md` | “Tests” ≈ in-game commands (`TestCommand`, `TestRunCommand`, `Testrun2Command`, …) |
| `docs/AGENT_PROTOCOL.md` | File inbox / `@agent` JSON — useful **after** join if agent loop running |
| `sim-harness/docs/SIM_HARNESS.md` | **Canonical overnight live-client spec** (2026-09-20) |
| Ostinato SETUP/USAGE | No AltoClef overnight / GameTest harness |

No standalone `SIM.md` / `TESTING.md` / `HARNESS.md` / `OVERNIGHT.md` beyond the above.

### Scripts that launch MC / create worlds / send chat

| Location | Notes |
|----------|--------|
| **`sim-harness/scripts/overnight-testrun2.ps1`** | Unattended 1.16.1 draft: patch settings → `:1.16.1:runClient` → keyboard create-world → chat `@testrun2` → stall/crash monitor → `logs/overnight-status.json` |
| **`C:\Users\redfa\agent-tools\altoclef-auto\`** (Windows, from transcripts) | `launch-mc.ps1`, `kill-mc.ps1`, `create-world-and-start.ps1`, `overnight-loop.ps1`, `replay-create-world.ps1`, teach/replay CSV+JSON, `status.txt` |
| Repo `APPLY-*-ON-WINDOWS.ps1` (box) | Patch helpers only |

### Teach / click recordings (create-world)

- `click-teach.csv` → `click-recipe.json` (absolute px: singleplayer, create_new, difficulty×3, create)
- Multi-strategy create: **% client coords**, **Tab/Enter/Space**, alternate %, More World Options
- Proven-ish create-new coords after maximize: **~`0.66, 0.84`**; also left-bottom **`~0.25–0.35 x, ~0.85–0.90 y`**
- Box screenshots of the flow: `a19*.png`, `e2-create.png`, `t4-easy.png`, `t5-creating.png`, `ui-now-hardcore-check.png`
- Hard rules: never match Brave/YouTube “Minecraft*” titles; never Hardcore; verify Easy; prefer **Tab+Enter** over blind center click (center = Play Selected)

### `idleCommand` / `@testrun2` notes

| Mechanism | Unattended? | Guidance |
|-----------|-------------|----------|
| **Chat SendKeys** (`t` → `@testrun2` → Enter) | Yes if MC focused | **Primary** |
| **`Settings.idleCommand`** | Auto when idle | **Keep `""`** for this harness |
| **Agent inbox** `<runDir>/altoclef/agent/inbox.txt` | Only if `@agent` loop already running | Fallback |
| In-process MessageSender | N/A from PowerShell | No external API |

`Testrun2Command` → `@testrun2` → modern RSG task. Log cues per SIM_HARNESS: `TESRUN2 start`, `User Task Set`, `T2 [NOW]`, `ph=BOOTSTRAP`.

### Ostinato / `*sim*` folders

- Ostinato = Baritone fork. Tungsten “sim” = physics ticks, not an AltoClef test harness.
- Project harness folder: **`/workspace/sim-harness`** (docs + overnight script).

---

## Engineering state (box mirrors: TenorClef-main / e94-bootstrap-gate)

### `PlaceBlockTask` cobble fallback — **OK (no BlockOptionalMeta)**

- `getMaterialTask` uses `ItemTarget` for DIRT + **COBBLESTONE** (+ netherrack / cobbled deepslate).
- Throwaway miss: `return Blocks.COBBLESTONE.getDefaultState();`
- No `BlockOptionalMeta` under `tasks/construction/` in these mirrors.

*Windows checkout not re-read this pass.*

### `Settings.speedrunMoverPreference` — **default `"baritone"`**

Overnight script also forces baritone in run-dir settings and clears `idleCommand`.

### Minecraft running? / latest crashes?

**Unknown** (no Windows Shell this executor). Check on machine:

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
  Where-Object { $_.CommandLine -match '1\.16\.1|altoclef|fabric' }
Get-ChildItem "C:\Users\redfa\Documents\MinecraftDev\altoclef\versions\1.16.1\run\crash-reports" |
  Sort-Object LastWriteTime -Descending | Select-Object -First 5
```

### How create-world was automated before

**Tab/Enter preferred; coords as fallback:**

1. Keyboard: Tab focus + Enter; cycle difficulty toward Easy; Survival; never Hardcore.
2. Coords/teach: ~`(0.66, 0.84)` Create New World; teach CSV → recipe JSON.
3. Verify: new save name, join log lines, screenshot Easy/Survival, optional `level.dat`.

---

## Exists vs build

```
EXISTS
├── Offline BenchmarkHarness (mock) — unit tests only
├── In-game @testrun2 + agent inbox file protocol
├── Windows agent-tools\altoclef-auto\* (1.21 create-world / overnight-loop)
├── sim-harness/docs/SIM_HARNESS.md — overnight live-client SPEC
└── sim-harness/scripts/overnight-testrun2.ps1 — 1.16.1 draft runner

MISSING
├── Headless / dedicated-server / GameTest RSG sim
└── Fully proven unattended 1.16.1 create-world (UI focus still fragile)
```

### Finish the “sim” (= finish live harness)

1. Copy `sim-harness/` onto the Windows repo if not already there.
2. JDK + Ostinato jar for 1.16.1; `speedrunMoverPreference=baritone`; `idleCommand=""`.
3. Dry-run create-world with screenshots; retune Tab counts/coords for **1.16.1** UI.
4. Run `overnight-testrun2.ps1`; poll `logs/overnight-status.json` + `latest.log`.
5. Do **not** invest in GameTest/headless for `@testrun2`.

---

## Parent one-liner

**No headless sim** — overnight path is **live `runClient` automation**. Spec+draft script under box `sim-harness/`; prior UI automation under Windows `agent-tools\altoclef-auto`. Cobble/mover defaults look correct in source mirrors; **MC/crashes not verified here**. CopyFromBox → `altoclef\docs\SIM_HARNESS_FINDINGS.md`, then sameMachine executor to deploy/harden the script.
