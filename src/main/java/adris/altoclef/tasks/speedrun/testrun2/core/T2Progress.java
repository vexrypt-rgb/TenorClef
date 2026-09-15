package adris.altoclef.tasks.speedrun.testrun2.core;

import adris.altoclef.AltoClef;
import net.minecraft.item.Items;

/** Inventory-only stall clock. Same XZ is not a stall. */
public final class T2Progress {

    private int last;
    private int stale;

    public void reset() {
        last = Integer.MIN_VALUE;
        stale = 0;
    }

    public int staleTicks() {
        return stale;
    }

    public void tick(AltoClef mod) {
        int h = T2Reserve.count(mod, Items.COBBLESTONE)
                + T2Reserve.count(mod, Items.DIRT) * 3
                + T2Reserve.count(mod, Items.WOODEN_PICKAXE) * 11
                + T2Reserve.count(mod, Items.STONE_PICKAXE) * 13
                + T2Reserve.count(mod, Items.IRON_PICKAXE) * 17
                + T2Reserve.count(mod, Items.BREAD) * 19
                + T2Reserve.count(mod, Items.CRAFTING_TABLE) * 23;
        if (h != last) {
            last = h;
            stale = 0;
        } else {
            stale++;
        }
    }
}
