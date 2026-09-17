package adris.altoclef.tasks.speedrun.testrun2;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Best-effort read of client advancement progress. Yarn names shift;
 * any failure returns false (treat as not done).
 */
public final class AdvancementProbe {

    private AdvancementProbe() {}

    public static boolean done(String id) {
        if (id == null || id.isEmpty()) return false;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return false;
            Object nh = mc.player.networkHandler;
            if (nh == null) return false;
            Object handler = nh.getClass().getMethod("getAdvancementHandler").invoke(nh);
            if (handler == null) return false;
            Identifier ident = parseId(id);
            Object adv = findAdvancement(handler, ident);
            if (adv == null) return false;
            Object progress;
            try {
                progress = handler.getClass()
                        .getMethod("getAdvancementProgress", adv.getClass())
                        .invoke(handler, adv);
            } catch (NoSuchMethodException e) {
                progress = handler.getClass()
                        .getMethod("getAdvancementProgress", Object.class)
                        .invoke(handler, adv);
            }
            if (progress == null) return false;
            Object obtained = progress.getClass().getMethod("isDone").invoke(progress);
            return obtained instanceof Boolean && (Boolean) obtained;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Identifier parseId(String id) {
        String path = id;
        if (id.contains(":")) path = id.substring(id.indexOf(':') + 1);
        return Identifier.of("minecraft", path);
    }

    private static Object findAdvancement(Object handler, Identifier ident) {
        try {
            Object mgr = handler.getClass().getMethod("getManager").invoke(handler);
            if (mgr != null) {
                try {
                    return mgr.getClass().getMethod("get", Identifier.class).invoke(mgr, ident);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) handler.getClass()
                    .getMethod("getAdvancements").invoke(handler);
            if (map != null) {
                for (Object k : map.keySet()) {
                    if (String.valueOf(k).contains(ident.getPath())) return map.get(k);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
