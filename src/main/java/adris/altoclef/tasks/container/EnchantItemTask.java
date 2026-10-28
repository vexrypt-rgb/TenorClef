package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.slot.MoveItemToSlotFromInventoryTask;
import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.EnchantingTableSlot;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Arrays;

/**
 * Enchants {@code target} items at an enchanting table (a book becomes an enchanted book). Gets the item, lapis and a
 * table if missing, then clicks the best offer the current level and lapis pay for, at least {@code minLevel}. Short on
 * levels it mines lapis ore for the XP and keeps the lapis. Bookshelves are not built here.
 */
public class EnchantItemTask extends Task {

    private static final int LAPIS = 3;

    private final ItemTarget target;
    private final int minLevel;
    private final Task table = new EnchantInTableTask();
    private int needLevel; // cheapest offer seen at the table that meets minLevel, 0 = unknown
    private boolean unenchantable;

    public EnchantItemTask(ItemTarget target, int minLevel) {
        this.target = target;
        this.minLevel = Math.max(1, minLevel);
    }

    private boolean book() {
        return Arrays.asList(target.getMatches()).contains(Items.BOOK);
    }

    private static boolean tableOpen(AltoClef mod) {
        return mod.getPlayer().currentScreenHandler instanceof EnchantmentScreenHandler;
    }

    private int enchanted(AltoClef mod) {
        if (book()) return mod.getItemStorage().getItemCount(Items.ENCHANTED_BOOK);
        int n = 0;
        for (ItemStack s : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
            if (!s.isEmpty() && target.matches(s.getItem()) && s.hasEnchantments()) n += s.getCount();
        }
        return n;
    }

    private int plain(AltoClef mod) {
        int n = 0;
        for (ItemStack s : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
            if (!s.isEmpty() && target.matches(s.getItem()) && !s.hasEnchantments()) n += s.getCount();
        }
        if (tableOpen(mod)) {
            ItemStack in = StorageHelper.getItemStackInSlot(EnchantingTableSlot.INPUT_SLOT_ITEM);
            if (!in.isEmpty() && target.matches(in.getItem()) && !in.hasEnchantments()) n += in.getCount();
        }
        return n;
    }

    private int lapis(AltoClef mod) {
        int n = mod.getItemStorage().getItemCount(Items.LAPIS_LAZULI);
        if (tableOpen(mod)) n += StorageHelper.getItemStackInSlot(EnchantingTableSlot.INPUT_SLOT_LAPIS).getCount();
        return n;
    }

    private void giveUp(String why) {
        Debug.logHarness("ENCHANT " + target + " gave up: " + why);
        unenchantable = true;
        fail(FailureReason.PRECONDITION_FAILED, why, false);
    }

    @Override
    protected void onStart() {
        needLevel = 0;
        unenchantable = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        int have = enchanted(mod), missing = target.getTargetCount() - have;
        if (plain(mod) < missing) {
            setDebugState("Getting " + target);
            return TaskCatalogue.getItemTask(new ItemTarget(target, missing));
        }
        if (lapis(mod) < 1) {
            setDebugState("Getting lapis");
            return TaskCatalogue.getItemTask("lapis_lazuli", LAPIS);
        }
        int level = mod.getPlayer().experienceLevel, want = Math.max(minLevel, needLevel);
        if (level < want) {
            // Lapis ore drops 2-5 XP a block and the lapis feeds later enchants.
            setDebugState("Mining lapis for XP: level " + level + "/" + want);
            return TaskCatalogue.getItemTask("lapis_lazuli", lapis(mod) + 6);
        }
        setDebugState("Enchanting");
        return table;
    }

    @Override
    public boolean isFinished() {
        AltoClef mod = AltoClef.getInstance();
        return unenchantable || mod.getPlayer() != null && enchanted(mod) >= target.getTargetCount();
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EnchantItemTask t && t.target.equals(target) && t.minLevel == minLevel;
    }

    @Override
    protected String toDebugString() {
        return "Enchanting " + target + " (level " + minLevel + "+)";
    }

    private class EnchantInTableTask extends DoStuffInContainerTask {

        private final TimerGame moveTimer = new TimerGame(0);
        private final TimerGame offerTimer = new TimerGame(3);
        private boolean waiting;

        EnchantInTableTask() {
            super(Blocks.ENCHANTING_TABLE, new ItemTarget("enchanting_table"));
        }

        @Override
        protected boolean isSubTaskEqual(DoStuffInContainerTask other) {
            return other instanceof EnchantInTableTask;
        }

        @Override
        protected boolean isContainerOpen(AltoClef mod) {
            return tableOpen(mod);
        }

        @Override
        protected Task containerSubTask(AltoClef mod) {
            moveTimer.setInterval(mod.getModSettings().getContainerItemMoveDelay());
            if (!moveTimer.elapsed()) return null;
            moveTimer.reset();

            EnchantmentScreenHandler h = (EnchantmentScreenHandler) mod.getPlayer().currentScreenHandler;
            ItemStack in = StorageHelper.getItemStackInSlot(EnchantingTableSlot.INPUT_SLOT_ITEM);
            boolean done = !in.isEmpty() && (in.hasEnchantments() || in.getItem() == Items.ENCHANTED_BOOK);
            if (!in.isEmpty() && (done || !target.matches(in.getItem()))) {
                setDebugState(done ? "Taking enchanted item" : "Clearing table");
                mod.getSlotHandler().clickSlot(EnchantingTableSlot.INPUT_SLOT_ITEM, 0, SlotActionType.QUICK_MOVE);
                waiting = false;
                return null;
            }
            ItemStack lap = StorageHelper.getItemStackInSlot(EnchantingTableSlot.INPUT_SLOT_LAPIS);
            int spare = mod.getItemStorage().getItemCount(Items.LAPIS_LAZULI);
            if (lap.getCount() < LAPIS && spare > 0) {
                setDebugState("Adding lapis");
                int to = lap.getCount() + Math.min(LAPIS - lap.getCount(), spare);
                return new MoveItemToSlotFromInventoryTask(new ItemTarget(Items.LAPIS_LAZULI, to), EnchantingTableSlot.INPUT_SLOT_LAPIS);
            }
            if (in.isEmpty()) {
                setDebugState("Placing " + target);
                waiting = false;
                return new MoveItemToSlotFromInventoryTask(new ItemTarget(target, 1), EnchantingTableSlot.INPUT_SLOT_ITEM);
            }
            if (!waiting) {
                waiting = true;
                offerTimer.reset();
            }
            // Vanilla: offer i needs cost levels, at least i + 1 levels and i + 1 lapis.
            int level = mod.getPlayer().experienceLevel, lapis = h.getLapisCount();
            int best = -1, want = Integer.MAX_VALUE;
            boolean any = false;
            for (int i = 0; i < 3; i++) {
                int cost = h.enchantmentPower[i];
                if (cost <= 0) continue;
                any = true;
                if (cost < minLevel) continue;
                int needs = Math.max(cost, i + 1);
                if (lapis >= i + 1) want = Math.min(want, needs);
                if (needs <= level && lapis >= i + 1) best = i;
            }
            if (!any) {
                if (offerTimer.elapsed()) {
                    giveUp(in.getItem() + " has no offers");
                }
                setDebugState("Waiting for offers");
                return null;
            }
            if (best < 0) {
                if (want == Integer.MAX_VALUE) {
                    // No offer reaches minLevel with the bookshelves around this table.
                    giveUp("no offer reaches level " + minLevel + " offers=" + Arrays.toString(h.enchantmentPower));
                    return null;
                }
                needLevel = want;
                Debug.logHarness("ENCHANT needs level " + want + " (have " + level + ") offers=" + Arrays.toString(h.enchantmentPower));
                return null;
            }
            Debug.logHarness("ENCHANT " + in.getItem() + " offer " + best + " cost " + h.enchantmentPower[best]
                    + " level " + level + " lapis " + lapis);
            mod.getController().clickButton(h.syncId, best);
            needLevel = 0;
            return null;
        }

        @Override
        protected double getCostToMakeNew(AltoClef mod) {
            // Diamonds, obsidian and a book: far worse than walking to a known table.
            return 1500;
        }
    }
}
