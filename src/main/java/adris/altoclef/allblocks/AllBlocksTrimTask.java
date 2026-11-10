package adris.altoclef.allblocks;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.container.AbstractDoToStorageContainerTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.storage.ContainerCache;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ostinato: #allblocks. Opens a storage chest and takes every copy past the first back into the inventory, so the
 * chest holds exactly one of each item. A stack is picked up and one item right-clicked back into its slot; a second
 * slot of the same item (tools do not stack) is taken whole.
 */
final class AllBlocksTrimTask extends AbstractDoToStorageContainerTask {

    private final BlockPos pos;
    private Slot picked;
    private boolean leaveOne;
    private boolean done;

    AllBlocksTrimTask(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    protected Optional<BlockPos> getContainerTarget() {
        return Optional.of(pos);
    }

    @Override
    protected Task onContainerOpenSubtask(AltoClef mod, ContainerCache cache) {
        if (!mod.getSlotHandler().canDoSlotAction()) return null;
        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
        if (!cursor.isEmpty()) {
            if (leaveOne && picked != null && StorageHelper.getItemStackInSlot(picked).isEmpty()) {
                setDebugState("Putting one back");
                mod.getSlotHandler().clickSlot(picked, 1, SlotActionType.PICKUP);
                leaveOne = false;
                return null;
            }
            Optional<Slot> to = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false);
            if (to.isPresent()) {
                setDebugState("Taking the extra " + cursor.getItem() + " out");
                mod.getSlotHandler().clickSlot(to.get(), 0, SlotActionType.PICKUP);
            } else if (picked != null) {
                Debug.logMessage("ALLBLOCKS trim: inventory full, leaving the rest in chest at " + pos.toShortString());
                mod.getSlotHandler().clickSlot(picked, 0, SlotActionType.PICKUP);
                done = true;
            }
            return null;
        }
        for (Map.Entry<Item, Integer> e : cache.getItemCounts().entrySet()) {
            if (e.getValue() <= 1) continue;
            List<Slot> slots = mod.getItemStorage().getSlotsWithItemContainer(e.getKey());
            if (slots.isEmpty()) continue;
            Slot pick = null;
            for (Slot s : slots) {
                if (StorageHelper.getItemStackInSlot(s).getCount() > 1) {
                    pick = s;
                    break;
                }
            }
            leaveOne = pick != null;
            if (pick == null) {
                if (slots.size() < 2) continue;
                pick = slots.get(1);
            }
            Debug.logMessage("ALLBLOCKS trim: chest at " + pos.toShortString() + " holds " + e.getValue() + " " + e.getKey()
                    + "; keeping one");
            picked = pick;
            mod.getSlotHandler().clickSlot(pick, 0, SlotActionType.PICKUP);
            return null;
        }
        done = true;
        return null;
    }

    @Override
    public boolean isFinished() {
        return done && StorageHelper.getItemStackInCursorSlot().isEmpty();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof AllBlocksTrimTask t && t.pos.equals(pos);
    }

    @Override
    protected String toDebugString() {
        return "Trimming chest " + pos.toShortString() + " to one of each";
    }
}
