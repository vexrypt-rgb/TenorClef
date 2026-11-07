package adris.altoclef.control;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.CursorSlot;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
//#if MC < 260000
import net.minecraft.item.*;
//#else
//$$ import net.minecraft.world.item.BucketItem;
//$$ import net.minecraft.world.item.EmptyMapItem;
//$$ import net.minecraft.world.item.EnderEyeItem;
//$$ import net.minecraft.world.item.FireworkRocketItem;
//$$ import net.minecraft.world.item.FishingRodItem;
//$$ import net.minecraft.world.item.Item;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.Items;
//$$ import net.minecraft.world.item.FoodOnAStickItem;
//$$ import net.minecraft.world.item.PotionItem;
//$$ import net.minecraft.world.item.SpawnEggItem;
//#endif
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;


public class SlotHandler {

    private final AltoClef mod;

    private final TimerGame slotActionTimer = new TimerGame(0);
    private boolean overrideTimerOnce = false;

    public SlotHandler(AltoClef mod) {
        this.mod = mod;
    }

    private void forceAllowNextSlotAction() {
        overrideTimerOnce = true;
    }

    public boolean canDoSlotAction() {
        if (overrideTimerOnce) {
            overrideTimerOnce = false;
            return true;
        }
        slotActionTimer.setInterval(mod.getModSettings().getContainerItemMoveDelay());
        return slotActionTimer.elapsed();
    }

    public void registerSlotAction() {
        mod.getItemStorage().registerSlotAction();
        slotActionTimer.reset();
    }


    public void clickSlot(Slot slot, int mouseButton, SlotActionType type) {
        if (!canDoSlotAction()) return;

        if (slot.getWindowSlot() == -1) {
            clickSlot(PlayerSlot.UNDEFINED, 0, SlotActionType.PICKUP);
            return;
        }
        // NOT THE CASE! We may have something in the cursor slot to place.
        //if (getItemStackInSlot(slot).isEmpty()) return getItemStackInSlot(slot);

        clickWindowSlot(slot.getWindowSlot(), mouseButton, type);
    }

    private void clickSlotForce(Slot slot, int mouseButton, SlotActionType type) {
        forceAllowNextSlotAction();
        clickSlot(slot, mouseButton, type);
    }

    private void clickWindowSlot(int windowSlot, int mouseButton, SlotActionType type) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        registerSlotAction();
        int syncId = player.currentScreenHandler.syncId;
        // Ostinato: run102 wood audit. Log every stack this bot throws away and who threw it.
        ItemStack thrown = type == SlotActionType.THROW
                ? (windowSlot >= 0 && windowSlot < player.currentScreenHandler.slots.size() ? player.currentScreenHandler.getSlot(windowSlot).getStack() : ItemStack.EMPTY)
                : windowSlot == -999 && type == SlotActionType.PICKUP ? StorageHelper.getItemStackInCursorSlot() : ItemStack.EMPTY;
        if (!thrown.isEmpty()) {
            StringBuilder by = new StringBuilder();
            for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                String c = e.getClassName();
                if (!c.startsWith("adris.") || c.endsWith("SlotHandler")) continue;
                by.append(c.substring(c.lastIndexOf('.') + 1)).append(':').append(e.getLineNumber()).append(' ');
                if (by.length() > 120) break;
            }
            System.out.println("MAZETEST throw " + net.minecraft.registry.Registries.ITEM.getId(thrown.getItem()).getPath() + " x" + (type == SlotActionType.THROW && mouseButton == 0 ? 1 : thrown.getCount()) + " by " + by);
        }

        try {
            mod.getController().clickSlot(syncId, windowSlot, mouseButton, type, player);
        } catch (Exception e) {
            Debug.logWarning("Slot Click Error (ignored)");
            e.printStackTrace();
        }
    }

    public void forceEquipItemToOffhand(Item toEquip) {
        if (StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT).getItem() == toEquip) {
            return;
        }
        List<Slot> currentItemSlot = mod.getItemStorage().getSlotsWithItemPlayerInventory(false,
                toEquip);
        for (Slot CurrentItemSlot : currentItemSlot) {
            if (!Slot.isCursor(CurrentItemSlot)) {
                mod.getSlotHandler().clickSlot(CurrentItemSlot, 0, SlotActionType.PICKUP);
            } else {
                mod.getSlotHandler().clickSlot(PlayerSlot.OFFHAND_SLOT, 0, SlotActionType.PICKUP);
            }
        }
    }

    public boolean forceEquipItem(Item toEquip) {

        // Already equipped
        if (StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot()).getItem() == toEquip) return true;

        ClientPlayerEntity player = mod.getPlayer();
        if (player == null) return false;

        // Fast path: item already in hotbar — just select that slot.
        // Old code always set selectedSlot=1 then SWAP'd into slot 1. After
        // PlaceBlocks/HolePillar left dirt selected, that equipped dirt/fist
        // while a wood pick sat in another hotbar slot (E109b fist-mine).
        String wantId = itemId(toEquip);
        String[] hotbarIds = new String[9];
        for (int i = 0; i < 9; i++) {
            ItemStack st = player.getInventory().getStack(i);
            hotbarIds[i] = st.isEmpty() ? null : itemId(st.getItem());
        }
        int hot = adris.altoclef.util.helpers.HotbarEquipSelector.findHotbarSlot(hotbarIds, wantId);
        if (hot >= 0) {
            player.getInventory().selectedSlot = hot;
            return true;
        }

        // Not in hotbar — move into hotbar slot 1 and select it.
        // First + last hotbar slots are often occupied by baritone defaults.
        player.getInventory().selectedSlot = 1;

        // If our item is in our cursor, simply move it to the hotbar.
        boolean inCursor = StorageHelper.getItemStackInSlot(CursorSlot.SLOT).getItem() == toEquip;

        List<Slot> itemSlots = mod.getItemStorage().getSlotsWithItemScreen(toEquip);
        if (!itemSlots.isEmpty()) {
            for (Slot ItemSlots : itemSlots) {
                int hotbar = 1;
                clickSlotForce(Objects.requireNonNull(ItemSlots), inCursor ? 0 : hotbar, inCursor ? SlotActionType.PICKUP : SlotActionType.SWAP);
            }
            return true;
        }
        return false;
    }

    private static String itemId(Item item) {
        if (item == null) return null;
        try {
            return item.getTranslationKey();
        } catch (Throwable t) {
            return item.toString();
        }
    }

    public boolean forceDeequipHitTool() {
        return forceDeequip(stack -> ItemHelper.isTool(stack.getItem()));
    }

    public void forceDeequipRightClickableItem() {
        forceDeequip(stack -> {
                    Item item = stack.getItem();
                    return item instanceof BucketItem // water,lava,milk,fishes
                            || item instanceof EnderEyeItem
                            || item == Items.BOW
                            || item == Items.CROSSBOW
                            || item == Items.FLINT_AND_STEEL || item == Items.FIRE_CHARGE
                            || item == Items.ENDER_PEARL
                            || item instanceof FireworkRocketItem
                            || item instanceof SpawnEggItem
                            || item == Items.END_CRYSTAL
                            || item == Items.EXPERIENCE_BOTTLE
                            || item instanceof PotionItem // also includes splash/lingering
                            || item == Items.TRIDENT
                            || item == Items.WRITABLE_BOOK
                            || item == Items.WRITTEN_BOOK
                            || item instanceof FishingRodItem
                            || item instanceof OnAStickItem
                            || item == Items.COMPASS
                            || item instanceof EmptyMapItem
                            //#if MC >= 12102
                            //$$ || adris.altoclef.util.helpers.ItemHelper.getArmorSlot(item) != null
                            //#else
                            || item instanceof Equipment
                            //#endif
                            || item == Items.LEAD
                            || item == Items.SHIELD;
                }
        );
    }

    /**
     * Tries to de-equip any item that we don't want equipped.
     *
     * @param isBad: Whether an item is bad/shouldn't be equipped
     * @return Whether we successfully de-equipped, or if we didn't have the item equipped at all.
     */
    public boolean forceDeequip(Predicate<ItemStack> isBad) {
        ItemStack equip = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot());
        ItemStack cursor = StorageHelper.getItemStackInSlot(CursorSlot.SLOT);
        if (isBad.test(cursor)) {
            // Throw away cursor slot OR move
            Optional<Slot> fittableSlots = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(equip, false);
            if (fittableSlots.isEmpty()) {
                // Try to swap items with the first non-bad slot.
                for (Slot slot : Slot.getCurrentScreenSlots()) {
                    if (!isBad.test(StorageHelper.getItemStackInSlot(slot))) {
                        clickSlotForce(slot, 0, SlotActionType.PICKUP);
                        return false;
                    }
                }
                if (ItemHelper.canThrowAwayStack(mod, cursor)) {
                    clickSlotForce(PlayerSlot.UNDEFINED, 0, SlotActionType.PICKUP);
                    return true;
                }
                // Can't throw :(
                return false;
            } else {
                // Put in the empty/available slot.
                clickSlotForce(fittableSlots.get(), 0, SlotActionType.PICKUP);
                return true;
            }
        } else if (isBad.test(equip)) {
            // Pick up the item
            clickSlotForce(PlayerSlot.getEquipSlot(), 0, SlotActionType.PICKUP);
            return false;
        } else if (equip.isEmpty() && !cursor.isEmpty()) {
            // cursor is good and equip is empty, so finish filling it in.
            clickSlotForce(PlayerSlot.getEquipSlot(), 0, SlotActionType.PICKUP);
            return true;
        }
        // We're already de-equipped
        return true;
    }

    public void forceEquipSlot(Slot slot) {
        Slot target = PlayerSlot.getEquipSlot();
        clickSlotForce(slot, target.getInventorySlot(), SlotActionType.SWAP);
    }

    public boolean forceEquipItem(Item[] matches, boolean unInterruptable) {
        return forceEquipItem(new ItemTarget(matches, 1), unInterruptable);
    }

    public boolean forceEquipItem(ItemTarget toEquip, boolean unInterruptable) {
        if (toEquip == null) return false;

        //If the bot try to eat
        if (mod.getFoodChain().needsToEat() && !unInterruptable) { //unless we really need to force equip the item
            return false; //don't equip the item for now
        }

        Slot target = PlayerSlot.getEquipSlot();
        // Already equipped
        if (toEquip.matches(StorageHelper.getItemStackInSlot(target).getItem())) return true;

        for (Item item : toEquip.getMatches()) {
            if (mod.getItemStorage().hasItem(item)) {
                if (forceEquipItem(item)) return true;
            }
        }
        return false;
    }

    // By default, don't force equip if the bot is eating.
    public boolean forceEquipItem(Item... toEquip) {
        return forceEquipItem(toEquip, false);
    }

    public void refreshInventory() {
        if (MinecraftClient.getInstance().player == null)
            return;
        for (int i = 0; i < MinecraftClient.getInstance().player.getInventory().main.size(); ++i) {
            Slot slot = Slot.getFromCurrentScreenInventory(i);
            clickSlotForce(slot, 0, SlotActionType.PICKUP);
            clickSlotForce(slot, 0, SlotActionType.PICKUP);
        }
    }
}
