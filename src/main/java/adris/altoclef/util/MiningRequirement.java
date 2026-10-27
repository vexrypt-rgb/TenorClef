package adris.altoclef.util;

import adris.altoclef.Debug;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public enum MiningRequirement implements Comparable<MiningRequirement> {
    HAND(Items.AIR), WOOD(Items.WOODEN_PICKAXE), STONE(Items.STONE_PICKAXE), IRON(Items.IRON_PICKAXE), DIAMOND(Items.DIAMOND_PICKAXE);

    private final Item _minPickaxe;

    MiningRequirement(Item minPickaxe) {
        _minPickaxe = minPickaxe;
    }

    // FIXME this doesnt work for cobwebs because they are broken with shears...
    public static MiningRequirement getMinimumRequirementForBlock(Block block) {
        if (block.getDefaultState().isToolRequired()) {
            for (MiningRequirement req : MiningRequirement.values()) {
                if (req == MiningRequirement.HAND) continue;
                Item pick = req.getMinimumPickaxe();
                if (pick.getDefaultStack().isSuitableFor(block.getDefaultState())) {
                    return req;
                }
            }
            // Ostinato: run53. Snow, snow_block and powder snow need a SHOVEL (not a pick) for drops, so every path-cost
            // query over snow logged this warning and returned DIAMOND (worn iron pick force-saved, snow treated as
            // unminable). A block a vanilla player breaks with a non-pick tool is hand-breakable for pathing; ToolSet
            // still picks the shovel/axe/shears when one is held.
            for (Item other : new Item[]{Items.WOODEN_SHOVEL, Items.WOODEN_AXE, Items.WOODEN_HOE, Items.SHEARS}) {
                if (other.getDefaultStack().isSuitableFor(block.getDefaultState())) return MiningRequirement.HAND;
            }
            Debug.logWarning("Failed to find ANY effective tool against: " + block + ". I assume netherite is not required anywhere, so something else probably went wrong.");
            return MiningRequirement.DIAMOND;
        }
        return MiningRequirement.HAND;
    }

    public Item getMinimumPickaxe() {
        return _minPickaxe;
    }

}
