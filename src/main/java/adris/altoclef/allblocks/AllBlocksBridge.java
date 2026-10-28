package adris.altoclef.allblocks;

import adris.altoclef.Debug;

import java.lang.reflect.Proxy;

/**
 * Ostinato: registers TenorClef as the runner behind Ostinato's {@code #allblocks}. Reflection, like
 * {@code TunnelProvisionBridge}: a no-op on Ostinato builds without the allblocks API.
 */
public final class AllBlocksBridge {

    private AllBlocksBridge() {}

    public static boolean install() {
        try {
            Class<?> iface = Class.forName("baritone.api.allblocks.IAllBlocksRunner");
            Class<?> registry = Class.forName("baritone.api.allblocks.AllBlocksRunners");
            Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (self, method, args) -> {
                switch (method.getName()) {
                    case "start":
                        return AllBlocks.start((String) args[0]);
                    case "status":
                        return AllBlocks.status();
                    case "stop":
                        return AllBlocks.stop();
                    case "report":
                        return AllBlocks.report((Integer) args[0]);
                    case "reset":
                        return AllBlocks.reset();
                    case "tour":
                        return AllBlocks.tour();
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
            Debug.logInternal("[allblocks] TenorClef registered as Ostinato #allblocks runner");
            return true;
        } catch (ClassNotFoundException noApi) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Debug.logInternal("[allblocks] could not register #allblocks runner: " + e);
            return false;
        }
    }
}
