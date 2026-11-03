package adris.altoclef.util;

import adris.altoclef.Debug;
import adris.altoclef.Settings;
import adris.altoclef.multiversion.ScreenVer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;

/**
 * Shared AutoWorld entry points used by TitleScreen and ClientTick mixins.
 * 26.3 create lives here so a stuck AccessibilityOnboardingScreen can't starve TitleScreen.tick.
 */
public final class AutoWorldBootstrap {
    private static boolean proved = false;
    private static int screenProbe = 0;

    private AutoWorldBootstrap() {}

    /** Called every client tick. Safe no-op when autoload is off. */
    public static void onClientTick() {
        if (!Settings.isAutoLoadWorldEnabled()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        //#if MC >= 260000
        //$$ dismissOnboardingIfNeeded(client);
        //#endif
        if (!proved) {
            proved = true;
            Object scr = ScreenVer.current(client);
            Debug.logHarness("AUTOWORLD: client-tick driver active screen="
                    + (scr == null ? "null" : scr.getClass().getSimpleName())
                    + " created=" + AutoWorldState.isCreated());
        }
        if (AutoWorldState.isCreated()) {
            return;
        }
        Object scr = ScreenVer.current(client);
        if (!(scr instanceof TitleScreen)) {
            if (++screenProbe % 200 == 1) {
                Debug.logHarness("AUTOWORLD: waiting for TitleScreen (have "
                        + (scr == null ? "null" : scr.getClass().getSimpleName()) + ")");
            }
            return;
        }
        tryCreateFreshWorld(client);
    }

    //#if MC >= 260000
    //$$ private static void dismissOnboardingIfNeeded(net.minecraft.client.Minecraft client) {
    //$$     Object scr = ScreenVer.current(client);
    //$$     if (scr instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboard) {
    //$$         Debug.logHarness("AUTOWORLD: dismissing AccessibilityOnboardingScreen");
    //$$         try {
    //$$             onboard.onClose();
    //$$         } catch (Throwable t) {
    //$$             try {
    //$$                 client.options.onboardingAccessibilityFinished();
    //$$                 client.setScreenAndShow(new net.minecraft.client.gui.screens.TitleScreen());
    //$$             } catch (Throwable t2) {
    //$$                 Debug.logHarness("AUTOWORLD: onboarding dismiss failed: " + t2);
    //$$             }
    //$$         }
    //$$     }
    //$$ }
    //$$
    //$$ public static void tryCreateFreshWorld(net.minecraft.client.Minecraft client) {
    //$$     if (AutoWorldState.isCreated()) return;
    //$$     if (client.getLevelSource() == null) return;
    //$$     if (AutoWorldState.delay() < 0) {
    //$$         AutoWorldState.setDelay(20 * 3);
    //$$         Debug.logHarness("AUTOWORLD: fresh-world create armed (firing in 3s) [26.3 client-tick]");
    //$$         return;
    //$$     }
    //$$     if (AutoWorldState.tickDelay() > 0) return;
    //$$
    //$$     AutoWorldState.setCreated(true);
    //$$     int reroll = AutoWorldState.rerollCount();
    //$$     String name = "AutoRun_" + java.time.LocalDateTime.now()
    //$$             .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    //$$             + (reroll > 0 ? ("_r" + reroll) : "");
    //$$
    //$$     net.minecraft.world.level.LevelSettings.DifficultySettings difficulty =
    //$$             new net.minecraft.world.level.LevelSettings.DifficultySettings(
    //$$                     net.minecraft.world.Difficulty.EASY, false, false);
    //$$     net.minecraft.world.level.LevelSettings levelSettings =
    //$$             new net.minecraft.world.level.LevelSettings(
    //$$                     name,
    //$$                     net.minecraft.world.level.GameType.SURVIVAL,
    //$$                     difficulty,
    //$$                     false,
    //$$                     net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
    //$$
    //$$     net.minecraft.world.level.levelgen.WorldOptions options =
    //$$             net.minecraft.world.level.levelgen.WorldOptions.defaultWithRandomSeed();
    //$$     String seed = System.getProperty("tenorclef.seed");
    //$$     if (seed != null && !seed.isEmpty()) {
    //$$         long v;
    //$$         try { v = Long.parseLong(seed.trim()); }
    //$$         catch (NumberFormatException e) { v = seed.trim().hashCode(); }
    //$$         options = options.withSeed(java.util.OptionalLong.of(v));
    //$$         Debug.logHarness("AUTOWORLD: fixed seed " + v);
    //$$     }
    //$$
    //$$     Debug.logHarness("AUTOWORLD: creating fresh world '" + name
    //$$             + "' mode=survival difficulty=easy hardcore=false via createFreshLevel");
    //$$     try {
    //$$         client.setScreenAndShow(null);
    //$$         client.createWorldOpenFlows().createFreshLevel(
    //$$                 name,
    //$$                 levelSettings,
    //$$                 options,
    //$$                 net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions,
    //$$                 null);
    //$$         Debug.logHarness("AUTOWORLD: createFreshLevel returned - waiting for join");
    //$$     } catch (Throwable t) {
    //$$         Debug.logHarness("AUTOWORLD: createFreshLevel failed: " + t);
    //$$         AutoWorldState.setCreated(false);
    //$$         AutoWorldState.setDelay(20 * 10);
    //$$     }
    //$$ }
    //#else
    public static void tryCreateFreshWorld(MinecraftClient client) {
        // TitleScreen mixin owns create on 1.16.1; client-tick only probes here.
        if (AutoWorldState.delay() < 0) {
            AutoWorldState.setDelay(20 * 3);
            Debug.logHarness("AUTOWORLD: TitleScreen seen; create left to AutoWorldCreateMixin");
        }
    }
    //#endif
}
