package adris.altoclef.util;

import adris.altoclef.Debug;

/**
 * Mutable state for {@link AutoWorldCreateMixin}, deliberately held OUTSIDE the mixin.
 *
 * <p><b>Why this class exists rather than {@code @Unique} statics on the mixin.</b> Mixin
 * rejects a mixin class that exposes non-private static methods, at APPLY time — not
 * at compile time:
 *
 * <pre>
 * InvalidMixinException: Mixin altoclef.mixins.json:AutoWorldCreateMixin from mod altoclef
 *   contains non-private static method rearm()V
 *     at MixinApplicatorStandard.checkMethodVisibility(MixinApplicatorStandard.java:781)
 * </pre>
 *
 * <p>{@code @Unique} marks members that must not be transferred to the target class, but
 * it does not exempt a public static method from that visibility check, so javac is happy
 * and the game dies 37 seconds into {@code runClient} with no compile error to point at
 * it (run O). Anything another class has to call therefore cannot live on the mixin.
 *
 * <p><b>And it must not live in the {@code adris.altoclef.mixins} package either.</b>
 * That package is declared as {@code "package": "adris.altoclef.mixins"} in
 * {@code altoclef.mixins.json}, which makes Mixin <i>own</i> it: any class in there that
 * is not itself a registered mixin throws on first reference —
 * {@code IllegalClassLoadError: ... is in a defined mixin package adris.altoclef.mixins.*
 * owned by altoclef.mixins.json and cannot be referenced directly}. Registered accessor
 * interfaces are exempt, which is why the other classes in that package are safe.
 */
public final class AutoWorldState {

    private static boolean created = false;
    private static int delay = -1;
    private static int rerollCount = 0;

    private AutoWorldState() {}

    /** True once a world has been created and we must not create another. */
    public static boolean isCreated() {
        return created;
    }

    public static void setCreated(boolean value) {
        created = value;
    }

    /**
     * Countdown in ticks before the create call fires. Negative means "not armed yet";
     * the mixin initialises it on its first tick so the title screen can settle.
     */
    public static int delay() {
        return delay;
    }

    public static void setDelay(int ticks) {
        delay = ticks;
    }

    /** Decrement the countdown and return the new value. */
    public static int tickDelay() {
        return --delay;
    }

    /**
     * Re-arm creation so the next title screen makes a FRESH world (a new random seed).
     *
     * <p>Used by the S159 spawn gate: when a seed spawns with no village and no ruined
     * portal the bot disconnects and must be able to try another seed inside the same
     * JVM, instead of the harness burning the rest of its 55-minute timeout on a dead
     * client.
     */
    public static void rearm() {
        created = false;
        delay = 20 * 5;
        rerollCount++;
        // S174: remember that the NEXT create is a reroll, so the client-tick driver knows
        // it must poll for the title screen instead of relying on TitleScreen.tick().
        needsRerollDrive = true;
        Debug.logHarness("AUTOWORLD: re-armed for a fresh seed (reroll #" + rerollCount + ")");
    }

    /**
     * S174 — has a rearm happened whose fresh world still has to be created?
     *
     * <p><b>Why this exists.</b> The reroll path was written to depend on
     * {@code TitleScreen.tick()} (via AutoWorldCreateMixin): rearm sets {@code created=false},
     * {@code ResetSignal} calls {@code mc.disconnect()}, the title screen appears, and its
     * tick creates a new world. <b>That never happens in this harness.</b> Run W fired a
     * perfectly correct S165 reset at 23:35:27 — the log says
     * {@code AUTOWORLD: re-armed for a fresh seed (reroll #1)} — and then sat in the old world
     * for 13+ minutes with the periodic {@code World save took 0ms} heartbeat and ZERO
     * title-screen or AUTOWORLD activity. {@code disconnect()} from a live integrated server
     * does not land the headless client on TitleScreen, so the only thing that could drive the
     * next create call was a screen that was never opened.
     *
     * <p>This is the same failure family as S168/S171/S172 — a mechanism whose trigger is
     * unreachable — which is why the reroll LOOKED fine: no run before W had ever needed one,
     * so the dead path cost nothing. The first time it was exercised, it hung the client until
     * the harness timeout. The fix is to drive the reroll from {@code MinecraftClient.tick()},
     * which always runs regardless of screen.
     */
    private static boolean needsRerollDrive = false;

    public static boolean needsRerollDrive() {
        return needsRerollDrive;
    }

    public static void clearRerollDrive() {
        needsRerollDrive = false;
    }

    public static int rerollCount() {
        return rerollCount;
    }
}
