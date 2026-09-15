package adris.altoclef.tasks.speedrun.bastion;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

public class BastionHousingRouteTask extends BastionRouteTask {

    public BastionHousingRouteTask(AltoClef mod, BlockPos origin) {
        super(mod, origin, 36);
    }

    @Override
    public BastionType getType() {
        return BastionType.HOUSING;
    }

    @Override
    protected Task getRouteTask() {
        double distSq = mod.getPlayer().squaredDistanceTo(
                bastionOrigin.getX() + 0.5,
                bastionOrigin.getY() + 0.5,
                bastionOrigin.getZ() + 0.5
        );

        if (distSq > 45 * 45) {
            Task go = goToTarget(bastionOrigin, "Housing route – approaching bastion");
            if (go != null) return go;
            abandon("cannot approach housing origin");
            return null;
        }

        Optional<BlockPos> chest = findNearbyChest(32);
        if (chest.isPresent()) {
            Task go = goToTarget(chest.get(), "Housing route – looting housing chest");
            if (go != null) return go;
        }

        Optional<BlockPos> gold = mod.getBlockScanner().getNearestBlock(
                Blocks.GOLD_BLOCK, Blocks.GILDED_BLACKSTONE
        );
        if (gold.isPresent() && gold.get().isWithinDistance(bastionOrigin, 40)) {
            Task go = goToTarget(gold.get(), "Housing route – gold block / gilded blackstone");
            if (go != null) return go;
        }

        if (StorageHelper.getItemCount(mod, Items.GOLD_INGOT) >= 20) {
            setDebugState("Housing route – enough gold, finishing");
            return null;
        }

        if (mod.getBlockScanner().isUnreachable(bastionOrigin)) {
            abandon("housing origin unreachable");
            return null;
        }
        Task go = goToTarget(bastionOrigin, "Housing route – searching units");
        if (go == null) abandon("no pathable housing targets");
        return go;
    }
}
