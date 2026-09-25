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
//#endif

/**
 * Headless fresh-world creator for the SIM harness.
 *
 * <p>Replaces the PowerShell SendKeys create-world automation, which can never work on
 * the SIM box: there is no interactive desktop, so {@code SetForegroundWindow} is a
 * no-op and {@code GetForegroundWindow()} returns 0 (see docs/SIM_HARNESS.md, blocker
 * {@code need_ui_focus}).
 *
 * <p>The harness contract is explicit: <b>a new Survival Easy world each cycle —
 * never Hardcore, never load an old save</b>. This mixin honours exactly that:
 * <ul>
 *   <li>{@link GameMode#SURVIVAL}, {@link Difficulty#EASY}, {@code hardcore = false}.</li>
 *   <li>Name is {@code AutoRun_yyyyMMdd_HHmmss}, matching the shell harness so log
 *       parsers keep working.</li>
 *   <li>Always creates; this class contains no load-an-existing-save path at all.</li>
 * </ul>
 *
 * <p><b>Why the tick and not {@code init()}.</b> The create entry point is
 * {@code MinecraftClient.method_29607(...)}, which blocks the calling thread in a
 * render/yield loop until the integrated server is up (it drives
 * {@code LevelLoadingScreen}). Running that from inside {@code TitleScreen.init()} would
 * re-enter screen construction on the render thread mid-{@code init}. Injecting at
 * {@code TitleScreen.tick()} TAIL keeps us on the render thread but at a clean frame
 * boundary — the same place a button click would originate.
 *
 * <p><b>All mutable state lives in {@link AutoWorldState}, not here.</b> A mixin class
 * may not expose non-private static methods — Mixin rejects it with
 * {@code InvalidMixinException: contains non-private static method rearm()V} at apply
 * time, which javac cannot see. The S159 spawn gate calls
 * {@link AutoWorldState#rearm()} from {@code ResetSignal} to get a new seed.
 *
 * <p><b>1.16.1 only.</b> This drives the 1.16.1 SIM harness with 1.16.1-only APIs
 * ({@code RegistryTracker}, {@code method_29607}); other versions compile an empty mixin.
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
    //$$         // Storage not ready yet; retry on a later tick.
    //$$         return;
    //$$     }
    //$$     if (AutoWorldState.delay() < 0) {
    //$$         // Let the title screen settle (resource reload, audio, first frames) before
    //$$         // we block it in the world-creation loop.
    //$$         AutoWorldState.setDelay(20 * 3);
    //$$         Debug.logHarness("AUTOWORLD: fresh-world create armed (firing in 3s)");
    //$$     }
    //$$     if (AutoWorldState.tickDelay() > 0) {
    //$$         return;
    //$$     }
    //$$
    //$$     AutoWorldState.setCreated(true);
    //$$     // Suffix on reroll: two worlds created inside the same second would otherwise
    //$$     // collide on the directory name and the create call would fail.
    //$$     int reroll = AutoWorldState.rerollCount();
    //$$     String name = "AutoRun_" + LocalDateTime.now().format(WORLD_NAME_FORMAT)
    //$$             + (reroll > 0 ? ("_r" + reroll) : "");
    //$$
    //$$     GameRules rules = new GameRules();
    //$$     // Yarn 1.16.1 build.21 mislabels LevelInfo's fields: the 3rd argument is HARDCORE
    //$$     // and the 5th is ALLOW COMMANDS (vanilla CreateWorldScreen passes `hardcore`, then
    //$$     // `cheatsEnabled && !hardcore`). Structures come from GeneratorOptions, not here.
    //$$     // Passing `true` third created every AutoRun world in hardcore (level.dat
    //$$     // hardcore=1): each death turned the bot into a spectator that sank through the
    //$$     // floor — the "respawn free-fall" behind S165/S169/S172.
    //$$     LevelInfo levelInfo = new LevelInfo(
    //$$             name,
    //$$             GameMode.SURVIVAL,
    //$$             false,              // hardcore — MUST stay false
    //$$             Difficulty.EASY,
    //$$             false,              // allowCommands
    //$$             rules,
    //$$             DataPackSettings.SAFE_MODE);
    //$$
    //$$     Debug.logHarness("AUTOWORLD: creating fresh world '" + name
    //$$             + "' mode=survival difficulty=easy hardcore=false");
    //$$
    //$$     try {
    //$$         client.openScreen(null);
    //$$         // RegistryTracker.create() (NOT `new RegistryTracker.Modifiable()`):
    //$$         // the no-arg Modifiable is an EMPTY dimension-type registry, and the server
    //$$         // dies with "Unregistered dimension type" in MinecraftServer.createWorlds.
    //$$         // `create()` is what MoreOptionsDialog's no-arg ctor uses — it registers the
    //$$         // vanilla overworld/nether/end dimension types.
    //$$         // See crash-2026-09-20_14.27.49-server.txt.
    //$$         client.method_29607(
    //$$                 name,
    //$$                 levelInfo,
    //$$                 RegistryTracker.create(),
    //$$                 GeneratorOptions.getDefaultOptions());
    //$$         Debug.logHarness("AUTOWORLD: create call returned — waiting for join");
    //$$     } catch (Throwable t) {
    //$$         Debug.logHarness("AUTOWORLD: create failed: " + t);
    //$$         // Let a later tick try again rather than wedging the client.
    //$$         AutoWorldState.setCreated(false);
    //$$         AutoWorldState.setDelay(20 * 10);
    //$$     }
    //$$ }
    //#endif
}
