package adris.altoclef.util.slots;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Locks the PlayerScreenHandler window-slot to PlayerInventory-index mapping.
 * <p>
 * Regression: armour and offhand were unmapped, so {@code ARMOR_HELMET_SLOT.getInventorySlot()} answered 5 (hotbar
 * slot 5) and {@code OFFHAND_SLOT.getInventorySlot()} answered 9 (main inventory slot 9). Every reader that treats
 * "inventory slot &lt; 9" as "in the hotbar" or calls {@code getInventory().getStack(index)} then looked at the wrong
 * stack. Pure arithmetic — no Minecraft client needed.
 */
public class PlayerSlotMappingTest {

    private static final int WINDOW_OUTPUT = 0;
    private static final int WINDOW_CRAFT_INPUT_FIRST = 1;
    private static final int WINDOW_CRAFT_INPUT_LAST = 4;
    private static final int WINDOW_ARMOR_FIRST = 5;   // head
    private static final int WINDOW_ARMOR_LAST = 8;    // feet
    private static final int WINDOW_MAIN_FIRST = 9;
    private static final int WINDOW_MAIN_LAST = 35;
    private static final int WINDOW_HOTBAR_FIRST = 36;
    private static final int WINDOW_HOTBAR_LAST = 44;
    private static final int WINDOW_OFFHAND = 45;

    private static final int INV_HOTBAR_LAST = 8;
    private static final int INV_MAIN_FIRST = 9;
    private static final int INV_MAIN_LAST = 35;
    private static final int INV_ARMOR_FIRST = 36;     // feet
    private static final int INV_ARMOR_LAST = 39;      // head
    private static final int INV_OFFHAND = 40;

    @Test
    void offhandMapsToInventoryForty() {
        Assertions.assertEquals(WINDOW_OFFHAND, PlayerSlot.OFFHAND_SLOT.getWindowSlot());
        Assertions.assertEquals(INV_OFFHAND, PlayerSlot.OFFHAND_SLOT.getInventorySlot());
    }

    @Test
    void armorIsHeadFirstInWindowAndFeetFirstInInventory() {
        Assertions.assertEquals(5, PlayerSlot.ARMOR_HELMET_SLOT.getWindowSlot());
        Assertions.assertEquals(6, PlayerSlot.ARMOR_CHESTPLATE_SLOT.getWindowSlot());
        Assertions.assertEquals(7, PlayerSlot.ARMOR_LEGGINGS_SLOT.getWindowSlot());
        Assertions.assertEquals(8, PlayerSlot.ARMOR_BOOTS_SLOT.getWindowSlot());

        Assertions.assertEquals(39, PlayerSlot.ARMOR_HELMET_SLOT.getInventorySlot());
        Assertions.assertEquals(38, PlayerSlot.ARMOR_CHESTPLATE_SLOT.getInventorySlot());
        Assertions.assertEquals(37, PlayerSlot.ARMOR_LEGGINGS_SLOT.getInventorySlot());
        Assertions.assertEquals(36, PlayerSlot.ARMOR_BOOTS_SLOT.getInventorySlot());
    }

    /** The actual regression: armour and offhand must never look like hotbar slots. */
    @Test
    void armorAndOffhandAreNotHotbarSlots() {
        for (PlayerSlot slot : PlayerSlot.ARMOR_SLOTS) {
            int inv = slot.getInventorySlot();
            Assertions.assertTrue(inv > INV_HOTBAR_LAST, "armour reported as hotbar slot: " + slot);
            Assertions.assertTrue(inv >= INV_ARMOR_FIRST && inv <= INV_ARMOR_LAST, "armour outside 36..39: " + slot);
        }
        Assertions.assertTrue(PlayerSlot.OFFHAND_SLOT.getInventorySlot() > INV_HOTBAR_LAST);
    }

    /** Every real window slot must survive window -> inventory -> window unchanged. */
    @Test
    void windowToInventoryRoundTrips() {
        for (int window = WINDOW_ARMOR_FIRST; window <= WINDOW_OFFHAND; window++) {
            PlayerSlot fromWindow = new PlayerSlot(window);
            int inv = fromWindow.getInventorySlot();
            PlayerSlot back = new PlayerSlot(inv, true);
            Assertions.assertEquals(window, back.getWindowSlot(), "window " + window + " -> inv " + inv + " -> window");
            Assertions.assertEquals(fromWindow, back, "equals() must agree for window " + window);
        }
    }

    /** Every real inventory index must survive inventory -> window -> inventory unchanged. */
    @Test
    void inventoryToWindowRoundTrips() {
        for (int inv = 0; inv <= INV_OFFHAND; inv++) {
            PlayerSlot fromInventory = new PlayerSlot(inv, true);
            int window = fromInventory.getWindowSlot();
            Assertions.assertEquals(inv, new PlayerSlot(window).getInventorySlot(),
                    "inv " + inv + " -> window " + window + " -> inv");
        }
    }

    @Test
    void hotbarAndMainAreUnchanged() {
        for (int inv = 0; inv <= INV_HOTBAR_LAST; inv++) {
            Assertions.assertEquals(inv + 36, new PlayerSlot(inv, true).getWindowSlot());
        }
        for (int inv = INV_MAIN_FIRST; inv <= INV_MAIN_LAST; inv++) {
            Assertions.assertEquals(inv, new PlayerSlot(inv, true).getWindowSlot());
        }
        for (int window = WINDOW_HOTBAR_FIRST; window <= WINDOW_HOTBAR_LAST; window++) {
            Assertions.assertEquals(window - 36, new PlayerSlot(window).getInventorySlot());
        }
    }

    /** Craft slots are window-only; they must not be confused with hotbar indices 0-4. */
    @Test
    void craftSlotsKeepTheirOwnIndices() {
        Assertions.assertEquals(WINDOW_OUTPUT, PlayerSlot.CRAFT_OUTPUT_SLOT.getWindowSlot());
        Assertions.assertEquals(WINDOW_CRAFT_INPUT_FIRST, PlayerSlot.CRAFT_INPUT_SLOTS[0].getWindowSlot());
        Assertions.assertEquals(WINDOW_CRAFT_INPUT_LAST, PlayerSlot.CRAFT_INPUT_SLOTS[3].getWindowSlot());
    }
}
