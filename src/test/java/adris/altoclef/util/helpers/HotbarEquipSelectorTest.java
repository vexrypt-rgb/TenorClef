package adris.altoclef.util.helpers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HotbarEquipSelectorTest {

    @Test
    void findsPickInNonDefaultHotbarSlot() {
        String[] hb = new String[9];
        hb[0] = "dirt";
        hb[3] = "wooden_pickaxe";
        assertEquals(3, HotbarEquipSelector.findHotbarSlot(hb, "wooden_pickaxe"));
        assertFalse(HotbarEquipSelector.alreadyEquipped(hb, 0, "wooden_pickaxe"));
        assertTrue(HotbarEquipSelector.alreadyEquipped(hb, 3, "wooden_pickaxe"));
    }

    @Test
    void missingWantedReturnsMinusOne() {
        String[] hb = new String[]{"dirt", "cobble", null, null, null, null, null, null, null};
        assertEquals(-1, HotbarEquipSelector.findHotbarSlot(hb, "wooden_pickaxe"));
    }

    @Test
    void nullSafe() {
        assertEquals(-1, HotbarEquipSelector.findHotbarSlot(null, "x"));
        assertEquals(-1, HotbarEquipSelector.findHotbarSlot(new String[9], null));
        assertFalse(HotbarEquipSelector.alreadyEquipped(null, 0, "x"));
    }
}
