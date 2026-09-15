package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.AltoClef;
import net.minecraft.item.Items;

public final class EyeGate {

    public static final int MIN_EYES = 12;

    private EyeGate() {}

    public static boolean ready(AltoClef mod) {
        try {
            return mod.getItemStorage().getItemCount(Items.ENDER_EYE) >= MIN_EYES;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int count(AltoClef mod) {
        try {
            return mod.getItemStorage().getItemCount(Items.ENDER_EYE);
        } catch (Throwable t) {
            return 0;
        }
    }
}
