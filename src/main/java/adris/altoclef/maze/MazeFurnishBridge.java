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

    private static boolean accept(Object job) {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || mod.getPlayer() == null || mod.getWorld() == null) {
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
