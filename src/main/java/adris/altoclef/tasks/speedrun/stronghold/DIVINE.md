# Divine fossil detection

## Files
- `DivineTables.java` – safe/highroll OW coords for fossil X 0–15
- `DivineFossilDetector.java` – scan Nether chunk (0,0) for bone blocks + origin X heuristic
- `DivineTravelTask.java` – scan → pick best of 3 → path to nether portal spot
- `NinjabrainCalculator` – optional `setDivineFossilX` sector prior

## Usage in speedrun
1. Early Nether (near 0,0): run `DivineTravelTask` or `DivineFossilDetector.detect(mod)`
2. If present, store `fossilX` and/or path toward divine nether coords before exiting
3. When locating stronghold: `calc.setDivineFossilX(fossilX)` then eye throws

## Coordinate space
Table values are **Nether XZ** (same as Ninjabrain Bot safe/highroll strings), not Overworld.

## Origin heuristic
Lowest-Y then min-X then min-Z bone block in chunk 0,0. Replace with exact 14-type patterns later for higher accuracy.
