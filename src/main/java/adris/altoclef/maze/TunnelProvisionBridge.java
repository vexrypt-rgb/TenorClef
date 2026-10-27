package adris.altoclef.maze;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;

import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Ostinato: registers TenorClef as the survival kit for Ostinato's {@code #maze.tunnels} (tools, armor, food, torches,
 * bed spawn near the dig face). Reflection, like {@link MazeFurnishBridge}: a no-op on lines without the maze API.
 */
public final class TunnelProvisionBridge {

    private TunnelProvisionBridge() {}

    public static boolean install() {
        try {
            Class<?> iface = Class.forName("baritone.api.maze.ITunnelProvisioner");
            Class<?> registry = Class.forName("baritone.api.maze.TunnelProvisioners");
            Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (self, method, args) -> {
                switch (method.getName()) {
                    case "provision":
                        return accept(args[0]);
                    case "busy": {
                        AltoClef mod = AltoClef.getInstance();
                        return mod != null && mod.getUserTaskChain().isActive();
                    }
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
            registry.getMethod("register", iface).invoke(null, proxy);
            Debug.logInternal("[maze] TenorClef registered as Ostinato tunnel provisioner");
            return true;
        } catch (ClassNotFoundException noApi) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Debug.logInternal("[maze] could not register tunnel provisioner: " + e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean accept(Object needs) {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || mod.getPlayer() == null || !(needs instanceof Map)) return false;
        mod.runUserTask(new TunnelProvisionTask((Map<String, Integer>) needs));
        return true;
    }
}
