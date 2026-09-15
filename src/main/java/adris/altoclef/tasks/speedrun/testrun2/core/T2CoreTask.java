package adris.altoclef.tasks.speedrun.testrun2.core;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.speedrun.testrun2.T2Brain;
import adris.altoclef.tasks.speedrun.testrun2.T2History;
import adris.altoclef.tasksystem.Task;
import net.minecraft.item.Items;

/**
 * Isolated runner for the core-logic experiment.
 * Modes: demo (default), tools, food, cobble.
 */
public class T2CoreTask extends Task {

    public enum Mode { DEMO, TOOLS, FOOD, COBBLE }

    private enum Phase { FOOD, PICK, TABLE, FILL, HOLD, DONE }

    private final Mode mode;
    private final T2Sticky sticky = new T2Sticky();
    private final T2Progress progress = new T2Progress();
    private Phase phase;
    private int holdTicks;
    private int pulse;
    private int yPeak;
    private boolean done;

    public T2CoreTask() {
        this(Mode.DEMO);
    }

    public T2CoreTask(Mode mode) {
        this.mode = mode == null ? Mode.DEMO : mode;
    }

    @Override
    protected void onStart() {
        T2Brain.reset();
        sticky.clear();
        progress.reset();
        phase = (mode == Mode.COBBLE) ? Phase.PICK : Phase.FOOD;
        if (mode == Mode.TOOLS) phase = Phase.PICK;
        holdTicks = 0;
        pulse = 0;
        yPeak = 0;
        done = false;
        T2Reserve.install(AltoClef.getInstance());
        Debug.logMessage("T2CORE start mode=" + mode + " sticky+reserve+progress+input. Isolated.");
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;
        if (adris.altoclef.tasks.speedrun.testrun2.util.QueueWatch.blocked()) return null;

        progress.tick(mod);
        T2Input.tick();
        pulse++;
        if (pulse % (20 * 10) == 0) {
            Debug.logMessage("T2CORE hb ph=" + phase
                    + " cobble=" + T2Reserve.count(mod, Items.COBBLESTONE)
                    + " pick=" + picks(mod)
                    + " stale=" + (progress.staleTicks() / 20) + "s"
                    + " child=" + name(sticky.peek()));
        }

        int y = mod.getPlayer().getBlockY();
        if (y > yPeak) yPeak = y;
        if (phase == Phase.FILL && yPeak - y >= 8) {
            T2History.note("T2CORE C210 hole drop " + (yPeak - y) + " — walk, do not pillar reserve");
            T2Input.walkTurn();
            yPeak = y;
        }

        Task live = sticky.peek();
        Task fix = T2Brain.help(mod, "CORE:" + phase, live);
        if (fix != null) {
            return sticky.keep("brain:" + fix.getClass().getSimpleName(), fix);
        }

        if (phase == Phase.FOOD) {
            int hun = hunger(mod);
            if (hun <= 8 && food(mod) < 1) {
                Task bread = TaskCatalogue.getItemTask(Items.BREAD, 4);
                if (bread != null) return sticky.keep("bread", bread);
            }
            if (mode == Mode.FOOD && (hun > 8 || food(mod) >= 1)) {
                return finishNow("food ok hun=" + hun);
            }
            phase = Phase.PICK;
            T2History.note("T2CORE food ok hun=" + hun);
        }

        if (phase == Phase.PICK) {
            if (picks(mod) < 1) {
                if (T2Reserve.count(mod, Items.WOODEN_PICKAXE) < 1) {
                    return sticky.keep("wood-pick", TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1));
                }
                return sticky.keep("stone-pick", TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1));
            }
            if (mode == Mode.TOOLS) {
                return finishNow("tools ok");
            }
            phase = Phase.TABLE;
            T2History.note("T2CORE pick ready");
        }

        if (phase == Phase.TABLE) {
            boolean table = T2Reserve.count(mod, Items.CRAFTING_TABLE) >= 1;
            try {
                table = table || mod.getBlockScanner().anyFound(net.minecraft.block.Blocks.CRAFTING_TABLE);
            } catch (Throwable ignored) {}
            if (!table) {
                return sticky.keep("table", TaskCatalogue.getItemTask(Items.CRAFTING_TABLE, 1));
            }
            phase = Phase.FILL;
        }

        if (phase == Phase.FILL) {
            if (T2Reserve.belowFloor(mod, Items.COBBLESTONE, T2Reserve.COBBLE_MIN)) {
                if (progress.staleTicks() > 20 * 45) {
                    T2History.note("T2CORE C211 cobble stale 45s — wander");
                    progress.reset();
                    T2Input.walkTurn();
                    return sticky.keep("wander-cobble", new TimeoutWanderTask());
                }
                return sticky.keep("cobble",
                        TaskCatalogue.getItemTask(Items.COBBLESTONE, T2Reserve.COBBLE_FILL));
            }
            phase = Phase.HOLD;
            sticky.clear();
            T2History.note("T2CORE reserve ok cobble=" + T2Reserve.count(mod, Items.COBBLESTONE));
            Debug.logMessage("T2CORE HOLD reserve cobble=" + T2Reserve.count(mod, Items.COBBLESTONE)
                    + " floor=" + T2Reserve.COBBLE_MIN + ". @stop to quit.");
        }

        if (phase == Phase.HOLD) {
            holdTicks++;
            if (T2Reserve.belowFloor(mod, Items.COBBLESTONE, T2Reserve.COBBLE_MIN)) {
                phase = Phase.FILL;
                T2History.note("T2CORE under floor — fill");
                return null;
            }
            if (holdTicks > 20 * 45) {
                return finishNow("hold 45s cobble=" + T2Reserve.count(mod, Items.COBBLESTONE));
            }
            return null;
        }

        return null;
    }

    private Task finishNow(String why) {
        phase = Phase.DONE;
        done = true;
        Debug.logMessage("T2CORE done (" + why + ")");
        return null;
    }

    private static int picks(AltoClef mod) {
        return T2Reserve.count(mod, Items.WOODEN_PICKAXE)
                + T2Reserve.count(mod, Items.GOLDEN_PICKAXE)
                + T2Reserve.count(mod, Items.STONE_PICKAXE)
                + T2Reserve.count(mod, Items.IRON_PICKAXE)
                + T2Reserve.count(mod, Items.DIAMOND_PICKAXE);
    }

    private static int food(AltoClef mod) {
        return T2Reserve.count(mod, Items.BREAD) + T2Reserve.count(mod, Items.APPLE)
                + T2Reserve.count(mod, Items.COOKED_BEEF) + T2Reserve.count(mod, Items.COOKED_PORKCHOP);
    }

    private static int hunger(AltoClef mod) {
        try {
            return mod.getPlayer().getHungerManager().getFoodLevel();
        } catch (Throwable t) {
            return 20;
        }
    }

    private static String name(Task t) {
        return t == null ? "-" : t.getClass().getSimpleName();
    }

    @Override
    protected void onStop(Task interrupt) {
        T2Reserve.uninstall(AltoClef.getInstance());
        sticky.clear();
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof T2CoreTask o && o.mode == mode;
    }

    @Override
    protected String toDebugString() {
        return "t2core " + mode + " ph=" + phase;
    }
}
