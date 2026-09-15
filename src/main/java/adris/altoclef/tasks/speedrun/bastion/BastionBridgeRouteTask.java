package adris.altoclef.tasks.speedrun.bastion;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

public class BastionBridgeRouteTask extends BastionRouteTask {

    public BastionBridgeRouteTask(AltoClef mod, BlockPos origin) {
        super(mod, origin, 32);
    }

    @Override
    public BastionType getType() {
        return BastionType.BRIDGE;
    }

    @Override
    protected Task getRouteTask() {
        double distSq = mod.getPlayer().squaredDistanceTo(
                bastionOrigin.getX() + 0.5, bastionOrigin.getY() + 0.5, bastionOrigin.getZ() + 0.5);
        if (distSq > 45 * 45) {
            Task go = goToTarget(bastionOrigin, "Bridge route – approaching");
            if (go != null) return go;
            abandon("cannot approach bridge origin");
            return null;
        }
        Optional<BlockPos> chest = findNearbyChest(28);
        if (chest.isPresent()) {
            Task go = goToTarget(chest.get(), "Bridge route – chest");
            if (go != null) return go;
        }
        Optional<BlockPos> gold = mod.getBlockScanner().getNearestBlock(Blocks.GOLD_BLOCK, Blocks.GILDED_BLACKSTONE);
        if (gold.isPresent() && gold.get().isWithinDistance(bastionOrigin, 40)) {
            Task go = goToTarget(gold.get(), "Bridge route – gold");
            if (go != null) return go;
        }
        if (StorageHelper.getItemCount(mod, Items.GOLD_INGOT) >= 16) return null;
        if (mod.getBlockScanner().isUnreachable(bastionOrigin)) {
            abandon("bridge origin unreachable");
            return null;
        }
        Task go = goToTarget(bastionOrigin, "Bridge route – searching");
        if (go == null) abandon("no pathable bridge targets");
        return go;
    }
}
