package adris.altoclef.util.slots;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;

import java.util.stream.IntStream;

public class PlayerSlot extends Slot {
    public static final PlayerSlot CRAFT_OUTPUT_SLOT = new PlayerSlot(0);
    // Armor slots are not visible in crafting/furnace (they break), and as such are unsafe to use.
    public static final PlayerSlot ARMOR_HELMET_SLOT = new PlayerSlot(5);
    public static final PlayerSlot ARMOR_CHESTPLATE_SLOT = new PlayerSlot(6);
    public static final PlayerSlot ARMOR_LEGGINGS_SLOT = new PlayerSlot(7);
    public static final PlayerSlot ARMOR_BOOTS_SLOT = new PlayerSlot(8);
    public static final PlayerSlot[] ARMOR_SLOTS = new PlayerSlot[]{
            ARMOR_HELMET_SLOT,
            ARMOR_CHESTPLATE_SLOT,
            ARMOR_LEGGINGS_SLOT,
            ARMOR_BOOTS_SLOT
    };
    public static final PlayerSlot OFFHAND_SLOT = new PlayerSlot(45);

    public static final PlayerSlot[] CRAFT_INPUT_SLOTS = IntStream.range(0, 4).mapToObj(PlayerSlot::getCraftInputSlot).toArray(PlayerSlot[]::new);

    public PlayerSlot(int windowSlot) {
        this(windowSlot, false);
    }

    protected PlayerSlot(int slot, boolean inventory) {
        super(slot, inventory);
    }

    public static PlayerSlot getCraftInputSlot(int x, int y) {
        return getCraftInputSlot(y * 2 + x);
    }

    public static PlayerSlot getCraftInputSlot(int index) {
        return new PlayerSlot(index + 1);
    }

    public static Slot getEquipSlot(EquipmentSlot equipSlot) {
        switch (equipSlot) {
            case MAINHAND:
                assert MinecraftClient.getInstance().player != null;
                return Slot.getFromCurrentScreenInventory(MinecraftClient.getInstance().player.getInventory().selectedSlot);
            case OFFHAND:
                return OFFHAND_SLOT;
            case FEET:
                return ARMOR_BOOTS_SLOT;
            case LEGS:
                return ARMOR_LEGGINGS_SLOT;
            case CHEST:
                return ARMOR_CHESTPLATE_SLOT;
            case HEAD:
                return ARMOR_HELMET_SLOT;
        }
        return null;
    }

    public static Slot getEquipSlot() {
        return getEquipSlot(EquipmentSlot.MAINHAND);
    }

    @Override
    public int inventorySlotToWindowSlot(int inventorySlot) {
        // Ostinato: 2026-10-30. PlayerScreenHandler layout (see the wiki.vg/Inventory link on Slot):
        // window 0 output, 1-4 craft input, 5-8 armour, 9-35 main, 36-44 hotbar, 45 offhand.
        // PlayerInventory layout: 0-8 hotbar, 9-35 main, 36 feet .. 39 head, 40 offhand.
        // Only the hotbar/main cases used to be mapped, so ARMOR_*_SLOT.getInventorySlot() answered 5..8 and
        // OFFHAND_SLOT answered 9 -- both are real inventory indices of *different* stacks. Armour is stored
        // feet-first while the window lists head-first, hence 44 - index.
        if (inventorySlot < 9) {
            return inventorySlot + 36;
        }
        if (inventorySlot >= 36 && inventorySlot <= 39) {
            return 44 - inventorySlot; // 36 feet -> 8, 37 legs -> 7, 38 chest -> 6, 39 head -> 5
        }
        if (inventorySlot == 40) {
            return 45;
        }
        return inventorySlot;
    }

    @Override
    protected int windowSlotToInventorySlot(int windowSlot) {
        if (windowSlot >= 36 && windowSlot <= 44) {
            return windowSlot - 36;
        }
        if (windowSlot >= 5 && windowSlot <= 8) {
            return 44 - windowSlot; // 5 head -> 39, 6 chest -> 38, 7 legs -> 37, 8 feet -> 36
        }
        if (windowSlot == 45) {
            return 40;
        }
        return windowSlot;
    }

    @Override
    protected String getName() {
        return "Player";
    }

}
