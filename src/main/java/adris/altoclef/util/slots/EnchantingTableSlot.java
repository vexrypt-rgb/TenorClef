package adris.altoclef.util.slots;

/** Enchanting table window: 0 item, 1 lapis, then main inventory 2..28 and hotbar 29..37. */
public class EnchantingTableSlot extends Slot {
    public static final EnchantingTableSlot INPUT_SLOT_ITEM = new EnchantingTableSlot(0);
    public static final EnchantingTableSlot INPUT_SLOT_LAPIS = new EnchantingTableSlot(1);

    public EnchantingTableSlot(int slot) {
        this(slot, false);
    }

    EnchantingTableSlot(int slot, boolean inventory) {
        super(slot, inventory);
    }

    @Override
    public int inventorySlotToWindowSlot(int inventorySlot) {
        if (inventorySlot < 9) {
            return inventorySlot + 29;
        }
        return inventorySlot - 7;
    }

    @Override
    protected int windowSlotToInventorySlot(int windowSlot) {
        if (windowSlot >= 29) {
            return windowSlot - 29;
        }
        return windowSlot + 7;
    }

    @Override
    protected String getName() {
        return "Enchanting Table";
    }
}
