# 1.16.1 sim (live client)

The only faithful sim is the real Fabric **1.16.1** client (`:1.16.1:runClient`).
Offline `benchmark` mock scenarios do **not** emulate 1.16.1 and must not be treated as the sim.

## Loop

`scripts\sim-loop.ps1` keeps one 1.16.1 client alive:
- new Survival **Easy** world each cycle (never Hardcore; never load an old save)
- `@testrun2` via chat after join (`idleCommand` empty)
- soft-reset to title between worlds when possible (no Gradle relaunch)

```powershell
cd C:\Users\redfa\Documents\MinecraftDev\altoclef
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
powershell -ExecutionPolicy Bypass -File .\scripts\sim-loop.ps1
```

Status: `logs\overnight-status.json`  
Summary: `logs\MORNING_SUMMARY.md`
