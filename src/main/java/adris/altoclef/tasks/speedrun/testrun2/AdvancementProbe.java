package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientAdvancementManager;

//#if MC >= 12002
import net.minecraft.advancement.AdvancementEntry;
//#else
//$$ import net.minecraft.advancement.Advancement;
//#endif

import java.util.Map;

/**
 * Reads the client's advancement progress (synced by the server on join and on every change).
 *
 * The old version reflected on getAdvancementProgress / getAdvancements, which exist in no
 * Minecraft version, so done() was always false: @aa never skipped owned advancements and never
 * noticed a goal completing, so every goal ran to its timeout.
 */
public final class AdvancementProbe {

    private AdvancementProbe() {}

    public static boolean done(String id) {
        AdvancementProgress p = progress(id);
        return p != null && p.isDone();
    }

    /** True once the server has sent the advancement list (false right after join). */
    public static boolean ready() {
        Map<Object, AdvancementProgress> m = progresses();
        return m != null && !m.isEmpty();
    }

    /** Owned / known counts over the given ids, for status lines. */
    public static int countDone(Iterable<String> ids) {
        int n = 0;
        for (String id : ids) if (done(id)) n++;
        return n;
    }

    public static AdvancementProgress progress(String id) {
        if (id == null || id.isEmpty()) return null;
        String want = id.contains(":") ? id : "minecraft:" + id;
        Map<Object, AdvancementProgress> m = progresses();
        if (m == null) return null;
        for (Map.Entry<Object, AdvancementProgress> e : m.entrySet()) {
            if (want.equals(idOf(e.getKey()))) return e.getValue();
        }
        return null;
    }

    private static Map<Object, AdvancementProgress> progresses() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.player.networkHandler == null) return null;
            ClientAdvancementManager h = mc.player.networkHandler.getAdvancementHandler();
            if (h == null) return null;
            return ((ClientAdvancementManagerAccessor) h).altoClefGetProgresses();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String idOf(Object key) {
        //#if MC >= 12002
        return key instanceof AdvancementEntry e ? e.id().toString() : null;
        //#else
        //$$ return key instanceof Advancement a ? a.getId().toString() : null;
        //#endif
    }
}
