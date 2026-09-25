package adris.altoclef.trackers.storage;

import adris.altoclef.trackers.Tracker;
import adris.altoclef.trackers.TrackerManager;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.CraftingTableSlot;
import adris.altoclef.util.slots.CursorSlot;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Keeps track of the player's inventory items
 */
public class InventorySubTracker extends Tracker {

    // NOT final and NOT cleared-in-place: updateState() builds fresh maps and swaps them
    // in, so a reader never sees a half-built inventory. See updateState() for the crash
    // this fixes. volatile for safe publication to readers that do not take the lock.
    private volatile HashMap<Item, List<Slot>> itemToSlotPlayer = new HashMap<>();
    private volatile HashMap<Item, List<Slot>> itemToSlotContainer = new HashMap<>();
    private volatile HashMap<Item, Integer> itemCountsPlayer = new HashMap<>();
    private volatile HashMap<Item, Integer> itemCountsContainer = new HashMap<>();

    private ScreenHandler _prevScreenHandler;

    public InventorySubTracker(TrackerManager manager) {
        super(manager);
    }

    private static boolean shouldIgnoreSlotForContainer(Slot slot) {
        // IMPORTANT NOTE:!!!!
        // Ignore crafting table output when calculating container slots.
        //
        // Why?
        // Because we don't want the bot to think we "have" an item if it's in our output slot. Otherwise it will
        // softlock because it will assume we're all good (we got the item!) when in reality we need to grab that item.
        //
        // We also don't want our bot to think we "have" an item if it's in our armor/crafting/shield slots. That's annoying to work with.
        if (slot instanceof CraftingTableSlot && slot.equals(CraftingTableSlot.OUTPUT_SLOT))
            return true;
        if (slot instanceof PlayerSlot) {
            // Ignore non-normal inventory slots
            int window = slot.getWindowSlot();
            return window < 9 || window > 44;
        }
        return false;
    }

    public int getItemCount(boolean playerInventory, boolean containerInventory, Item... items) {
        ensureUpdated();
        int result = 0;
        ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot();
        for (Item item : items) {
            if (playerInventory && cursorStack.getItem().equals(item))
                result += cursorStack.getCount();
            if (playerInventory)
                result += itemCountsPlayer.getOrDefault(item, 0);
            if (containerInventory)
                result += itemCountsContainer.getOrDefault(item, 0);
        }
        return result;
    }

    public boolean hasItem(boolean playerInventoryOnly, Item... items) {
        ensureUpdated();
        ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot();
        for (Item item : items) {
            if (cursorStack.getItem().equals(item))
                return true;
            if (itemCountsPlayer.containsKey(item))
                return true;
            if (!playerInventoryOnly && itemCountsContainer.containsKey(item))
                return true;
        }
        return false;
    }

    public List<Slot> getSlotsWithItems(boolean playerInventory, boolean containerInventory, Item... items) {
        ensureUpdated();
        List<Slot> result = new ArrayList<>();
        ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot();
        for (Item item : items) {
            if (playerInventory && cursorStack.getItem().equals(item))
                result.add(CursorSlot.SLOT);
            if (playerInventory)
                result.addAll(itemToSlotPlayer.getOrDefault(item, Collections.emptyList()));
            if (containerInventory)
                result.addAll(itemToSlotContainer.getOrDefault(item, Collections.emptyList()));
        }
        return result;
    }

    public List<ItemStack> getInventoryStacks(boolean includeCursor) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || player.getInventory() == null)
            return Collections.emptyList();
        PlayerInventory inv = player.getInventory();
        // 36 player + 1 offhand + 4 armor
        List<ItemStack> result = new ArrayList<>(41 + (includeCursor ? 1 : 0));
        if (includeCursor) {
            result.add(StorageHelper.getItemStackInCursorSlot());
        }
        result.addAll(inv.main);
        result.addAll(inv.armor);
        result.addAll(inv.offHand);
        return result;
    }

    private List<Slot> getSlotsThatCanFit(HashMap<Item, List<Slot>> list, ItemStack item, boolean acceptPartial) {
        List<Slot> result = new ArrayList<>();
        // First add fillable slots
        for (Slot toCheckStackable : list.getOrDefault(item.getItem(), Collections.emptyList())) {
            // Ignore cursor slot.
            if (Slot.isCursor(toCheckStackable))
                continue;
            ItemStack stackToAddTo = StorageHelper.getItemStackInSlot(toCheckStackable);
            // We must have SOME room left, then we decide whether we care about having ENOUGH
            if (!stackToAddTo.isEmpty() && ItemHelper.canStackTogether(item, stackToAddTo)) {
                int roomLeft = stackToAddTo.getMaxCount() - stackToAddTo.getCount();
                if (acceptPartial || roomLeft > item.getCount()) {
                    result.add(toCheckStackable);
                }
            }
        }
        // Then add air slots that can insert our item
        if (MinecraftClient.getInstance().player != null) {
            ScreenHandler handler = MinecraftClient.getInstance().player.currentScreenHandler;
            for (Slot airSlot : list.getOrDefault(Items.AIR, Collections.emptyList())) {
                // Ignore cursor slot
                if (airSlot.equals(CursorSlot.SLOT))
                    continue;
                int windowCheck = airSlot.getWindowSlot();
                // Special case: Armor/shield, we wish to ignore these if our inventory is not open.
                if (windowCheck < handler.slots.size() && handler.getSlot(windowCheck).canInsert(item)) {
                    result.add(airSlot);
                }
            }
        }
        return result;
    }

    public List<Slot> getSlotsThatCanFit(boolean includePlayer, boolean includeContainer, ItemStack item, boolean acceptPartial) {
        ensureUpdated();
        final List<Slot> result = new ArrayList<>();
        if (includePlayer)
            result.addAll(getSlotsThatCanFit(itemToSlotPlayer, item, acceptPartial));
        if (includeContainer)
            result.addAll(getSlotsThatCanFit(itemToSlotContainer, item, acceptPartial));
        return result;
    }

    public boolean hasEmptySlot(boolean playerInventoryOnly) {
        return hasItem(playerInventoryOnly, Items.AIR);
    }

    /**
     * Fills the four maps passed in. It deliberately touches NO shared state: the caller
     * owns these maps until it publishes them, so two concurrent scans can never end up
     * adding into the same list.
     */
    private static void registerItem(ItemStack stack, Slot slot, boolean isSlotPlayerInventory,
                                     HashMap<Item, List<Slot>> itemToSlotPlayer,
                                     HashMap<Item, List<Slot>> itemToSlotContainer,
                                     HashMap<Item, Integer> itemCountsPlayer,
                                     HashMap<Item, Integer> itemCountsContainer) {
        Item item = stack.getItem();
        int count = stack.getCount();
        if (stack.isEmpty()) {
            // If our cursor slot is empty, IGNORE IT as we don't want to treat it as a valid slot.
            item = Items.AIR;
            count = 0;
        }

        if (isSlotPlayerInventory) {
            itemCountsPlayer.put(item, itemCountsPlayer.getOrDefault(item, 0) + count);
        } else {
            itemCountsContainer.put(item, itemCountsContainer.getOrDefault(item, 0) + count);
        }

        if (slot != null) {
            HashMap<Item, List<Slot>> toAdd = isSlotPlayerInventory ? itemToSlotPlayer : itemToSlotContainer;
            if (!toAdd.containsKey(item))
                toAdd.put(item, new ArrayList<>());
            toAdd.get(item).add(slot);
        }
    }

    @Override
    protected void updateState() {
        _prevScreenHandler = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.currentScreenHandler : null;

        // Build into FRESH maps and publish them by swapping the field references at the
        // very end. The old code cleared the shared maps in place and refilled them, which
        // is a data race on two counts:
        //
        //   1. A concurrent reader could observe a half-built inventory (maps cleared,
        //      only some slots re-added) and decide the bot has no pickaxe.
        //   2. Two concurrent updateState() calls could add into the SAME ArrayList —
        //      thread B's put(item, new ArrayList<>()) replaces the list thread A is
        //      currently filling, and A's next get(item) returns B's. Both then call
        //      add() on it, which corrupts the backing array. That is exactly the crash
        //      that ended run O at 16:33:
        //        ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0
        //          at java.util.ArrayList.add(ArrayList.java:484)
        //      (size advanced to 1 while elementData was still the shared EMPTY array).
        //
        // Swapping removes both: a scan never mutates published state, and readers always
        // see one complete snapshot.
        HashMap<Item, List<Slot>> slotsPlayer = new HashMap<>();
        HashMap<Item, List<Slot>> slotsContainer = new HashMap<>();
        HashMap<Item, Integer> countsPlayer = new HashMap<>();
        HashMap<Item, Integer> countsContainer = new HashMap<>();

        ScreenHandler handler = null;
        if (MinecraftClient.getInstance().player != null) {
            handler = MinecraftClient.getInstance().player.currentScreenHandler;
        }
        if (handler != null) {
            for (Slot slot : Slot.getCurrentScreenSlots()) {
                // Ignore cursor slot, that's handled separately.
                if (slot.equals(CursorSlot.SLOT))
                    continue;
                ItemStack stack = StorageHelper.getItemStackInSlot(slot);
                // Add separately if we're in a container vs player inventory.
                if (!shouldIgnoreSlotForContainer(slot)) {
                    registerItem(stack, slot, slot.isSlotInPlayerInventory(),
                            slotsPlayer, slotsContainer, countsPlayer, countsContainer);
                }
            }
        }

        itemToSlotPlayer = slotsPlayer;
        itemToSlotContainer = slotsContainer;
        itemCountsPlayer = countsPlayer;
        itemCountsContainer = countsContainer;
    }

    @Override
    protected void reset() {
        // Swap, don't clear in place — same reasoning as updateState().
        itemToSlotPlayer = new HashMap<>();
        itemToSlotContainer = new HashMap<>();
        itemCountsPlayer = new HashMap<>();
        itemCountsContainer = new HashMap<>();
    }

    @Override
    protected boolean isDirty() {
        ScreenHandler handler = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.currentScreenHandler : null;
        return super.isDirty() || handler != _prevScreenHandler;
    }
}
