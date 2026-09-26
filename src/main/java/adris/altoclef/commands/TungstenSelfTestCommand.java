package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.IntArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.movement.TungstenGotoTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Random;

/**
 * @ttest [legs]: live self-test of Tungsten travel. Walks N legs to random surface
 * points ~24 blocks away and prints one TTEST line per leg (ok / fallback / timeout),
 * so the harness can measure Tungsten on a random seed without knowing coordinates.
 */
public class TungstenSelfTestCommand extends Command {

    public TungstenSelfTestCommand() {
        super("ttest", "Tungsten travel self-test on random surface legs", new IntArg("legs", 8));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        int legs = parser.get(Integer.class);
        mod.runUserTask(new SelfTestTask(legs), this::finish);
    }

    private static void report(String s) {
        System.out.println("TENORCLEF: TTEST " + s);
    }

    private static final class SelfTestTask extends Task {
        private final int legs;
        private final Random rng = new Random();
        private int leg;
        private int ok, fallback, timeout;
        private TungstenGotoTask current;
        private long legStart;
        private long warmupUntil;
        private BlockPos from;

        SelfTestTask(int legs) {
            this.legs = legs;
        }

        @Override
        protected void onStart() {
            leg = 0;
            current = null;
            warmupUntil = System.currentTimeMillis() + 5000;
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (mod.getPlayer() == null || System.currentTimeMillis() < warmupUntil) return null;
            if (current != null) {
                long el = System.currentTimeMillis() - legStart;
                if (current.isFinished() || el > 120_000) {
                    String res = !current.isFinished() ? "timeout" : current.usedFallback() ? "fallback" : "ok";
                    if (res.equals("ok")) ok++; else if (res.equals("fallback")) fallback++; else timeout++;
                    report("leg=" + leg + " result=" + res + " ms=" + el + " from=" + from.toShortString()
                            + " to=" + mod.getPlayer().getBlockPos().toShortString());
                    current = null;
                } else {
                    return current;
                }
            }
            if (leg >= legs) return null;
            leg++;
            from = mod.getPlayer().getBlockPos();
            double a = rng.nextDouble() * Math.PI * 2;
            BlockPos xz = from.add((int) (Math.cos(a) * 24), 0, (int) (Math.sin(a) * 24));
            BlockPos target = mod.getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, xz);
            report("leg=" + leg + " start from=" + from.toShortString() + " target=" + target.toShortString());
            current = new TungstenGotoTask(target);
            legStart = System.currentTimeMillis();
            return current;
        }

        @Override
        protected void onStop(Task interruptTask) {
            report("done ok=" + ok + " fallback=" + fallback + " timeout=" + timeout + " of " + leg);
        }

        @Override
        public boolean isFinished() {
            return leg >= legs && current == null && System.currentTimeMillis() >= warmupUntil;
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof SelfTestTask;
        }

        @Override
        protected String toDebugString() {
            return "TungstenSelfTest " + leg + "/" + legs;
        }
    }
}
