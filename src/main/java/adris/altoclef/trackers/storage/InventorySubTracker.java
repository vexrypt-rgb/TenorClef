package adris.altoclef.trackers.storage;

import adris.altoclef.multiversion.ScreenVer;
import adris.altoclef.trackers.Tracker;
import adris.altoclef.trackers.TrackerManager;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.CraftingTableSlot;
import adris.altoclef.util.slots.CursorSlot;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
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

    // ------------------------------------------------------------------------------------------------------------
    // Ostinato: unchanged-screen fast path.
    //
    // TrackerManager.tick() marks EVERY tracker dirty on every client tick, so updateState() runs once per tick
    // even when the player is standing still. The old body rebuilt four HashMaps, one ArrayList per distinct item
    // and one Slot wrapper per window slot every single time: for a 46-slot player inventory that is ~46 Slot
    // objects + ~46 hash inserts + ~10 lists per tick, i.e. the largest per-tick allocation left in TenorClef.
    //
    // Two caches remove it without weakening the snapshot:
    //
    //   _slots      the immutable Slot wrappers for the current (handler, screen, slot count) triple. Slot is a
    //               value object built from a window index, so the same window slot yields the same Slot every
    //               tick; only a screen change invalidates the array.
    //   _scratch /  the previous tick's (Item, count) for every window slot. If they all match, the four published
    //   _lastItems  maps still describe exactly what is on screen and are KEPT — nothing is allocated. The check is
    //   _lastCounts exact (Item identity + int count), not a hash, so a hash collision cannot hide a change.
    //
    // A rebuild is forced whenever the handler, the screen or the slot count changed, because those decide the slot
    // classification (crafting output ignored, player-vs-container split), not just the contents.
    private Slot[] _slots;
    private ScreenHandler _slotsHandler;
    private Screen _slotsScreen;
    private int _slotsCount = -1;

    private ItemStack[] _scratch;
    private Item[] _lastItems;
    private int[] _lastCounts;
    private boolean _hasSnapshot;

    public InventorySubTracker(TrackerManager manager) {
        super(manager);
    }

    /**
     * Refresh through the dirty flag rather than by calling {@link #updateState()} behind its back. Nothing calls
     * {@link ItemStorageTracker#updateState()} today (that tracker has no {@code ensureUpdated()} call site), so this
     * is currently unused — but if that path is ever revived it must not scan a screen that is already up to date,
     * and it must not leave this tracker's flag set for the next reader to scan a second time.
     */
    void refresh() {
        ensureUpdated();
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
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        ScreenHandler handler = player != null ? player.currentScreenHandler : null;
        _prevScreenHandler = handler;

        if (handler == null) {
            // Nothing on screen: publish empty maps once, then stay put.
            if (_hasSnapshot || !itemCountsPlayer.isEmpty() || !itemCountsContainer.isEmpty()) {
                itemToSlotPlayer = new HashMap<>();
                itemToSlotContainer = new HashMap<>();
                itemCountsPlayer = new HashMap<>();
                itemCountsContainer = new HashMap<>();
            }
            forgetSnapshot();
            return;
        }

        final int n = handler.slots.size();
        final Screen screen = ScreenVer.current(mc);
        final boolean slotsRebuilt = _slots == null || _slotsHandler != handler
                || _slotsScreen != screen || _slotsCount != n;
        if (slotsRebuilt) {
            Slot[] slots = new Slot[n];
            for (int i = 0; i < n; i++) {
                slots[i] = Slot.getFromCurrentScreen(i);
            }
            _slots = slots;
            _slotsHandler = handler;
            _slotsScreen = screen;
            _slotsCount = n;
        }
        final Slot[] slots = _slots;

        if (_scratch == null || _scratch.length != n) {
            _scratch = new ItemStack[n];
            _lastItems = new Item[n];
            _lastCounts = new int[n];
            _hasSnapshot = false;
        }

        boolean changed = slotsRebuilt || !_hasSnapshot;
        for (int i = 0; i < n; i++) {
            Slot slot = slots[i];
            if (slot == null) {
                // Unreachable: getCurrentType() maps every ContainerType to a Slot. Keep the snapshot aligned anyway.
                _scratch[i] = ItemStack.EMPTY;
                _lastItems[i] = Items.AIR;
                _lastCounts[i] = 0;
                continue;
            }
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            _scratch[i] = stack;
            Item item = stack.getItem();
            int count = stack.getCount();
            if (!changed && (_lastItems[i] != item || _lastCounts[i] != count)) {
                changed = true;
            }
            _lastItems[i] = item;
            _lastCounts[i] = count;
        }
        _hasSnapshot = true;

        if (!changed) {
            // Every window slot holds the same item and the same count as when the published maps were built, and
            // the handler/screen/slot count that decide slot classification are unchanged too. Rebuilding would
            // produce byte-identical maps, so keep them.
            return;
        }

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

        for (int i = 0; i < n; i++) {
            Slot slot = slots[i];
            // Ignore cursor slot, that's handled separately.
            if (slot == null || slot.equals(CursorSlot.SLOT)) {
                continue;
            }
            // Add separately if we're in a container vs player inventory.
            if (!shouldIgnoreSlotForContainer(slot)) {
                registerItem(_scratch[i], slot, slot.isSlotInPlayerInventory(),
                        slotsPlayer, slotsContainer, countsPlayer, countsContainer);
            }
        }

        itemToSlotPlayer = slotsPlayer;
        itemToSlotContainer = slotsContainer;
        itemCountsPlayer = countsPlayer;
        itemCountsContainer = countsContainer;
    }

    /** Drop the caches that describe a particular screen, so the next scan starts from scratch. */
    private void forgetSnapshot() {
        _slots = null;
        _slotsHandler = null;
        _slotsScreen = null;
        _slotsCount = -1;
        _scratch = null;
        _lastItems = null;
        _lastCounts = null;
        _hasSnapshot = false;
    }

    @Override
    protected synchronized void reset() {
        // Swap, don't clear in place — same reasoning as updateState(). synchronized: TrackerManager.tick() resets
        // every tracker from the client thread without a lock, and updateState() now reads the scan caches, so an
        // unsynchronised reset could null them in the middle of a scan on another thread.
        itemToSlotPlayer = new HashMap<>();
        itemToSlotContainer = new HashMap<>();
        itemCountsPlayer = new HashMap<>();
        itemCountsContainer = new HashMap<>();
        forgetSnapshot();
    }

    @Override
    protected boolean isDirty() {
        ScreenHandler handler = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.currentScreenHandler : null;
        return super.isDirty() || handler != _prevScreenHandler;
    }
}
