package adris.altoclef.util.helpers;

/**
 * Pure hotbar selection for equip-without-swap.
 * No Minecraft types — callers pass item identity tokens (e.g. translation keys
 * or enum names) for the 9 hotbar slots plus the wanted item.
 * <p>
 * {@link adris.altoclef.control.SlotHandler#forceEquipItem} used to always set
 * {@code selectedSlot = 1} then SWAP the wanted item into slot 1. When a pick
 * already sat in another hotbar slot (common after PlaceBlocks/HolePillar left
 * dirt selected), that briefly (or lastingly) equipped the wrong item → E109b
 * fist-mining with a wood pick in inventory.
 */
public final class HotbarEquipSelector {

    private HotbarEquipSelector() {}

    /**
     * @param hotbarItemIds length 9; null/empty means empty slot
     * @param wantId        identity of the item to equip; null never matches
     * @return hotbar index 0..8 if present, else -1
     */
    public static int findHotbarSlot(String[] hotbarItemIds, String wantId) {
        if (hotbarItemIds == null || wantId == null || wantId.isEmpty()) return -1;
        int n = Math.min(9, hotbarItemIds.length);
        for (int i = 0; i < n; i++) {
            String id = hotbarItemIds[i];
            if (id != null && !id.isEmpty() && wantId.equals(id)) return i;
        }
        return -1;
    }

    /** True when the currently selected hotbar slot already holds {@code wantId}. */
    public static boolean alreadyEquipped(String[] hotbarItemIds, int selectedSlot, String wantId) {
        if (wantId == null || wantId.isEmpty()) return false;
        if (selectedSlot < 0 || selectedSlot > 8) return false;
        if (hotbarItemIds == null || selectedSlot >= hotbarItemIds.length) return false;
        String id = hotbarItemIds[selectedSlot];
        return id != null && wantId.equals(id);
    }
}
