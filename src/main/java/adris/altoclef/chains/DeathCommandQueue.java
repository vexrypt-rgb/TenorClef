package adris.altoclef.chains;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Death commands waiting to run, advanced once per client tick by {@link DeathMenuChain}. Pure (no Minecraft types)
 * so the lifecycle is unit-tested.
 *
 * <p>Replaces {@code while (player.isAlive());} on the render thread. That loop only ever exited once the client
 * player read as dead, which is the normal state on the death screen, so commands ran at once. When the player read
 * as alive (a server-side heal or respawn raced the 2 s death-screen timer) it never exited: packets that could
 * change the state are applied by the same thread, so the client froze. Now the commands wait without blocking: they
 * run when the player reads dead (the old exit condition), or once {@link #MAX_HOLD_TICKS} ticks of reading alive
 * have passed (the respawn completed; the old code would have hung here). They run exactly once, a new schedule
 * replaces a pending one, and leaving the world discards them.
 */
public final class DeathCommandQueue {

    public enum PlayerState { NO_PLAYER, DEAD, ALIVE }

    /** Ticks a pending batch may wait while the player reads alive before it runs anyway. */
    public static final int MAX_HOLD_TICKS = 40;

    private List<String> pending;
    private int heldTicks;

    /** Queues the commands of one respawn; replaces any batch that has not run yet. */
    public void schedule(List<String> commands) {
        pending = commands == null || commands.isEmpty() ? null : new ArrayList<>(commands);
        heldTicks = 0;
    }

    /**
     * Advances the queue by one tick. Never waits. Returns what happened: "ran", "ran-after-hold", "held",
     * "discarded" or "idle".
     */
    public String tick(PlayerState state, Consumer<String> runner) {
        if (pending == null) return "idle";
        if (state == PlayerState.NO_PLAYER) {
            pending = null;
            return "discarded";
        }
        if (state == PlayerState.ALIVE && ++heldTicks < MAX_HOLD_TICKS) return "held";
        List<String> batch = pending;
        pending = null; // cleared before running so a command that reschedules cannot run twice
        for (String command : batch) runner.accept(command);
        return state == PlayerState.DEAD ? "ran" : "ran-after-hold";
    }

    public boolean hasPending() {
        return pending != null;
    }
}
