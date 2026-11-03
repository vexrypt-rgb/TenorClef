package adris.altoclef.mixins;

import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
//#if MC <= 11601
//$$ import adris.altoclef.Debug;
//$$ import adris.altoclef.Settings;
//$$ import adris.altoclef.util.AutoWorldState;
//$$ import net.minecraft.client.MinecraftClient;
//$$ import net.minecraft.resource.DataPackSettings;
//$$ import net.minecraft.util.registry.RegistryTracker;
//$$ import net.minecraft.world.Difficulty;
//$$ import net.minecraft.world.GameMode;
//$$ import net.minecraft.world.GameRules;
//$$ import net.minecraft.world.gen.GeneratorOptions;
//$$ import net.minecraft.world.level.LevelInfo;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ import java.time.LocalDateTime;
//$$ import java.time.format.DateTimeFormatter;
//#elseif MC >= 260000
//$$ import adris.altoclef.Debug;
//$$ import adris.altoclef.Settings;
//$$ import adris.altoclef.util.AutoWorldState;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.world.Difficulty;
//$$ import net.minecraft.world.level.GameType;
//$$ import net.minecraft.world.level.LevelSettings;
//$$ import net.minecraft.world.level.WorldDataConfiguration;
//$$ import net.minecraft.world.level.levelgen.WorldOptions;
//$$ import net.minecraft.world.level.levelgen.presets.WorldPresets;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ import java.time.LocalDateTime;
//$$ import java.time.format.DateTimeFormatter;
//$$ import java.util.OptionalLong;
//#endif

/**
 * Headless fresh-world creator for the SIM / speedrun harness.
 *
 * <p>1.16.1 uses {@code MinecraftClient.method_29607}; 26.3+ uses
 * {@code Minecraft.createWorldOpenFlows().createFreshLevel(...)}.
 * Other versions compile an empty mixin.
 */
@Mixin(TitleScreen.class)
public class AutoWorldCreateMixin {
    //#if MC <= 11601
    //$$
    //$$ private static final DateTimeFormatter WORLD_NAME_FORMAT =
    //$$         DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    //$$
    //$$ @Inject(at = @At("TAIL"), method = "tick()V")
    //$$ private void altoAutoCreateWorld(CallbackInfo info) {
    //$$     if (AutoWorldState.isCreated() || !Settings.isAutoLoadWorldEnabled()) {
    //$$         return;
    //$$     }
    //$$     MinecraftClient client = MinecraftClient.getInstance();
    //$$     if (client == null || client.getLevelStorage() == null) {
    //$$         return;
    //$$     }
    //$$     if (AutoWorldState.delay() < 0) {
    //$$         AutoWorldState.setDelay(20 * 3);
    //$$         Debug.logHarness("AUTOWORLD: fresh-world create armed (firing in 3s)");
    //$$     }
    //$$     if (AutoWorldState.tickDelay() > 0) {
    //$$         return;
    //$$     }
    //$$
    //$$     AutoWorldState.setCreated(true);
    //$$     int reroll = AutoWorldState.rerollCount();
    //$$     String name = "AutoRun_" + LocalDateTime.now().format(WORLD_NAME_FORMAT)
    //$$             + (reroll > 0 ? ("_r" + reroll) : "");
    //$$
    //$$     GameRules rules = new GameRules();
    //$$     LevelInfo levelInfo = new LevelInfo(
    //$$             name,
    //$$             GameMode.SURVIVAL,
    //$$             false,
    //$$             Difficulty.EASY,
    //$$             false,
    //$$             rules,
    //$$             DataPackSettings.SAFE_MODE);
    //$$
    //$$     Debug.logHarness("AUTOWORLD: creating fresh world '" + name
    //$$             + "' mode=survival difficulty=easy hardcore=false");
    //$$
    //$$     try {
    //$$         client.openScreen(null);
    //$$         client.method_29607(
    //$$                 name,
    //$$                 levelInfo,
    //$$                 RegistryTracker.create(),
    //$$                 seededOptions());
    //$$         Debug.logHarness("AUTOWORLD: create call returned — waiting for join");
    //$$     } catch (Throwable t) {
    //$$         Debug.logHarness("AUTOWORLD: create failed: " + t);
    //$$         AutoWorldState.setCreated(false);
    //$$         AutoWorldState.setDelay(20 * 10);
    //$$     }
    //$$ }
    //$$
    //$$ private static GeneratorOptions seededOptions() {
    //$$     GeneratorOptions def = GeneratorOptions.getDefaultOptions();
    //$$     String seed = System.getProperty("tenorclef.seed");
    //$$     if (seed == null || seed.isEmpty()) return def;
    //$$     long v;
    //$$     try { v = Long.parseLong(seed.trim()); } catch (NumberFormatException e) { v = seed.trim().hashCode(); }
    //$$     Debug.logHarness("AUTOWORLD: fixed seed " + v);
    //$$     return def.withHardcore(false, java.util.OptionalLong.of(v));
    //$$ }
    //#elseif MC >= 260000
    //$$
    //$$ private static final DateTimeFormatter WORLD_NAME_FORMAT =
    //$$         DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    //$$
    //$$ @Inject(at = @At("TAIL"), method = "tick()V")
    //$$ private void altoAutoCreateWorld(CallbackInfo info) {
    //$$     if (AutoWorldState.isCreated() || !Settings.isAutoLoadWorldEnabled()) {
    //$$         return;
    //$$     }
    //$$     Minecraft client = Minecraft.getInstance();
    //$$     if (client == null || client.getLevelSource() == null) {
    //$$         return;
    //$$     }
    //$$     if (AutoWorldState.delay() < 0) {
    //$$         AutoWorldState.setDelay(20 * 3);
    //$$         Debug.logHarness("AUTOWORLD: fresh-world create armed (firing in 3s) [26.3]");
    //$$     }
    //$$     if (AutoWorldState.tickDelay() > 0) {
    //$$         return;
    //$$     }
    //$$
    //$$     AutoWorldState.setCreated(true);
    //$$     int reroll = AutoWorldState.rerollCount();
    //$$     String name = "AutoRun_" + LocalDateTime.now().format(WORLD_NAME_FORMAT)
    //$$             + (reroll > 0 ? ("_r" + reroll) : "");
    //$$
    //$$     LevelSettings.DifficultySettings difficulty =
    //$$             new LevelSettings.DifficultySettings(Difficulty.EASY, false, false);
    //$$     LevelSettings levelSettings = new LevelSettings(
    //$$             name,
    //$$             GameType.SURVIVAL,
    //$$             difficulty,
    //$$             false,
    //$$             WorldDataConfiguration.DEFAULT);
    //$$
    //$$     WorldOptions options = seededOptions26();
    //$$
    //$$     Debug.logHarness("AUTOWORLD: creating fresh world '" + name
    //$$             + "' mode=survival difficulty=easy hardcore=false via createFreshLevel");
    //$$
    //$$     try {
    //$$         client.setScreen(null);
    //$$         client.createWorldOpenFlows().createFreshLevel(
    //$$                 name,
    //$$                 levelSettings,
    //$$                 options,
    //$$                 WorldPresets::createNormalWorldDimensions,
    //$$                 null);
    //$$         Debug.logHarness("AUTOWORLD: createFreshLevel returned — waiting for join");
    //$$     } catch (Throwable t) {
    //$$         Debug.logHarness("AUTOWORLD: createFreshLevel failed: " + t);
    //$$         AutoWorldState.setCreated(false);
    //$$         AutoWorldState.setDelay(20 * 10);
    //$$     }
    //$$ }
    //$$
    //$$ private static WorldOptions seededOptions26() {
    //$$     WorldOptions def = WorldOptions.defaultWithRandomSeed();
    //$$     String seed = System.getProperty("tenorclef.seed");
    //$$     if (seed == null || seed.isEmpty()) return def;
    //$$     long v;
    //$$     try { v = Long.parseLong(seed.trim()); } catch (NumberFormatException e) { v = seed.trim().hashCode(); }
    //$$     Debug.logHarness("AUTOWORLD: fixed seed " + v);
    //$$     return def.withSeed(OptionalLong.of(v));
    //$$ }
    //#endif
}
