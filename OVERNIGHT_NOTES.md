# Overnight notes

## Pending apply (next kill -> compile)
- **Combat sword preference** (2026-09-06 ~11:40 PT): equip best sword (wood->stone->iron+) when fighting; do not treat axe as combat weapon.
  - `AbstractKillEntityTask`: `bestWeapon`/`equipWeapon` swords-only; log once `combat: equipping sword`.
  - `KillAura`: delegates to shared sword equip.
  - `EarlyOverworldSpeedrunTask`: soft-try craft sword for combat even if axe satisfies `hasWeapon` route gate; stone sword preferred once stone available; axe/fist only after soft-timeout give-up.
  - `MobDefenseChain`: axe may still count in fight/flee damage gate; equip path prefers sword.
- **Do not compile while @testrun client is LIVE** - apply on next kill->compile cycle.

## Create-world workers
- Never Hardcore.
- Always Survival + Easy.
- Screenshot-read labels (verify difficulty/mode from UI text in screenshots).
