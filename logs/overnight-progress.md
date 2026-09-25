# Overnight progress

## 2026-09-20 05:07:49 PT
- Status: harness was **idle** (no overnight-status.json; last latest.log at 04:55 PT showed User task FINISHED in IRON ~5m58s on New World (18)/Player434, then client stopped).
- Action: relaunching `scripts\overnight-testrun2.ps1 -ForceKillStale` (mover=baritone via Ensure-BaritoneSettings). Cleared stuck e94 `:1.16.1:compileJava` that was wedged on `:1.21:preprocessCode`.
- Goal: unattended @testrun2 overnight; watch for crash / illegal PORTAL with pick=0.

## 2026-09-20 05:10:04 PT
- Harness **live**: `overnight-testrun2.ps1 -ForceKillStale` (wrapper PID 19444, gradle PID 5152).
- Status: `waiting_join`; settings idleCommand empty + speedrunMoverPreference=baritone.
- Killed stale java PIDs 10012/11984 (stuck e94 compile).
- Fixed script launch: UTF-8 BOM rewrite of overnight-testrun2.ps1 (PS 5.1 parse flake on prior encoding).
- Watching join / create-world / @testrun2; next ticks stay quiet unless crash or PORTAL+pick=0.

## 2026-09-20 05:10:54 PT
- Recovered `need_ui_focus`: focused MC window and sent create-world keys for `AutoRun_20260920_051050` (Survival/Easy intended). Harness still in join wait.

