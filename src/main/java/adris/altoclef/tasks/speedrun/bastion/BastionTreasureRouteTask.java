package adris.altoclef.tasks.speedrun.bastion;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Treasure bastion route – highest priority for speedruns.
 *
 * Uses goToTarget so unreachable/blacklisted gold/chests/origin are abandoned
 * instead of thrashing Blacklist Try N forever.
 */
public class BastionTreasureRouteTask extends BastionRouteTask {

    public BastionTreasureRouteTask(AltoClef mod, BlockPos origin) {
        super(mod, origin, 48); // Treasure usually yields a lot; higher target is fine
    }

    @Override
    public BastionType getType() {
        return BastionType.TREASURE;
    }

    @Override
    protected Task getRouteTask() {
        double distSq = mod.getPlayer().squaredDistanceTo(
                bastionOrigin.getX() + 0.5,
                bastionOrigin.getY() + 0.5,
                bastionOrigin.getZ() + 0.5
        );
        if (distSq > 40 * 40) {
            Task go = goToTarget(bastionOrigin, "Treasure route – approaching bastion");
            if (go != null) return go;
            abandon("cannot approach bastion origin");
            return null;
        }

        Optional<BlockPos> chest = findNearbyChest(28);
        if (chest.isPresent()) {
            Task go = goToTarget(chest.get(), "Treasure route – going for chest");
            if (go != null) return go;
        }

        Optional<BlockPos> goldBlock = mod.getBlockScanner().getNearestBlock(
                Blocks.GOLD_BLOCK, Blocks.GILDED_BLACKSTONE
        );
        if (goldBlock.isPresent()) {
            BlockPos pos = goldBlock.get();
            if (mod.getPlayer().squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 35 * 35) {
                Task go = goToTarget(pos, "Treasure route – mining gold block / gilded blackstone");
                if (go != null) return go;
            }
        }

        if (StorageHelper.getItemCount(mod, Items.GOLD_INGOT) >= 24) {
            setDebugState("Treasure route – enough gold, finishing early");
            return null;
        }

        // Origin unreachable or already failed out → abandon rather than loop
        if (mod.getBlockScanner().isUnreachable(bastionOrigin)) {
            abandon("bastion origin unreachable and no more loot");
            return null;
        }
        Task go = goToTarget(bastionOrigin, "Treasure route – searching for more loot");
        if (go == null) {
            abandon("no pathable loot targets left");
        }
        return go;
    }
}
