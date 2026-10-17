# 1.16.5 compile — fork files testrun2 cannot overwrite

Apply these in `src/main/java` (same files preprocess copies to versions/1.16.5).

## 1. `.../tasks/manhunt/FightPlayerTask.java`
Replace every `target.isRemoved()` with `target.removed`

## 2. `.../tasks/speedrun/EarlyOverworldSpeedrunTask.java`
- Delete `Blocks.DEEPSLATE_IRON_ORE` (no deepslate in 1.16). Use only `Blocks.IRON_ORE`.
- Add import: `import net.minecraft.util.math.Direction;`

## 3. `.../tasks/speedrun/FillEndPortalFrameTask.java`
Change
`MinecraftClient.getInstance().world`
to
`net.minecraft.client.MinecraftClient.getInstance().world`

## 4. `.../tasks/speedrun/stronghold/NinjabrainLocateTask.java`
Same as #3.

Then:

```
.\gradlew.bat :1.16.5:runClient
```

If more errors appear, paste the compileJava block only (not the 40-line deprecation wall).
