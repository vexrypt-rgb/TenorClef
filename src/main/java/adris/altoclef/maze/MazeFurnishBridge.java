package adris.altoclef.maze;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;

import java.lang.reflect.Proxy;

/**
 * Registers TenorClef as Ostinato's maze furnisher, so {@code #maze.backrooms furnish} runs {@link MazeFurnishTask}.
 * Done by reflection: only the 26.3 Ostinato jar has {@code baritone.api.maze}, and on every other line this is a no-op.
 */
public final class MazeFurnishBridge {

    private MazeFurnishBridge() {}

    public static boolean install() {
        try {
            Class<?> furnisher = Class.forName("baritone.api.maze.IMazeFurnisher");
            Class<?> registry = Class.forName("baritone.api.maze.MazeFurnishers");
            Object proxy = Proxy.newProxyInstance(furnisher.getClassLoader(), new Class<?>[]{furnisher}, (self, method, args) -> {
                switch (method.getName()) {
                    case "furnish":
                        return accept(args[0]);
                    case "busy":
                        return busy();
                    case "cancel":
                        cancel();
                        return null;
                    case "name":
                    case "toString":
                        return "TenorClef";
                    case "hashCode":
                        return System.identityHashCode(self);
                    case "equals":
                        return self == args[0];
                    default:
                        return null;
                }
            });
            registry.getMethod("register", furnisher).invoke(null, proxy);
            Debug.logInternal("[maze] TenorClef registered as Ostinato maze furnisher");
            return true;
        } catch (ClassNotFoundException noMazeApi) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Debug.logInternal("[maze] could not register furnisher: " + e);
            return false;
        }
    }

    // Ostinato run107: #maze.backrooms.connected digs the next segment only once the furnish task has let go.
    private static boolean busy() {
        AltoClef mod = AltoClef.getInstance();
        return mod != null && mod.getUserTaskChain().getCurrentTask() instanceof MazeFurnishTask;
    }

    private static void cancel() {
        AltoClef mod = AltoClef.getInstance();
        if (mod != null && mod.getUserTaskChain().getCurrentTask() instanceof MazeFurnishTask) {
            mod.getUserTaskChain().cancel(mod);
        }
    }

    private static boolean accept(Object job) {
        AltoClef mod = AltoClef.getInstance();
        // Run44: dig verify passed, then this returned false because the client world was null for that
        // tick. The job does not need the world to be queued. A missing player still means we are not in game.
        if (mod == null || mod.getPlayer() == null) {
            Debug.logInternal("[maze] furnish not ready mod=" + (mod != null));
            return false;
        }
        try {
            MazeFurnishPlan plan = MazeFurnishPlan.fromOstinato(job);
            mod.runUserTask(new MazeFurnishTask(plan));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Debug.logInternal("[maze] bad furnish job: " + e);
            return false;
        }
    }
}
