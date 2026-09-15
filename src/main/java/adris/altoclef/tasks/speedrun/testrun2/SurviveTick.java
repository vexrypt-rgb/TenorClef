package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Eat only. Never hold useKey while a shield is in the offhand — that is
 * "stuck blocking" and kills sprint.
 */
public final class SurviveTick {

    private static final Item[] FOODS = {
            Items.GOLDEN_CARROT, Items.COOKED_PORKCHOP, Items.COOKED_BEEF, Items.COOKED_MUTTON,
            Items.COOKED_CHICKEN, Items.COOKED_SALMON, Items.COOKED_COD, Items.BREAD,
            Items.BAKED_POTATO, Items.APPLE, Items.CARROT, Items.COOKED_RABBIT,
            Items.PORKCHOP, Items.BEEF, Items.CHICKEN, Items.MUTTON, Items.COOKIE
    };

    private static int eatTicks;
    private static boolean holdingUse;

    private SurviveTick() {}

    public static void tick(AltoClef mod) {
        PlayerEntity me = mod.getPlayer();
        if (me == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;

        int hunger = 20;
        try {
            hunger = me.getHungerManager().getFoodLevel();
        } catch (Throwable ignored) {}

        boolean shieldOff = false;
        try {
            shieldOff = me.getOffHandStack().getItem() == Items.SHIELD;
        } catch (Throwable ignored) {}

        // Shield blocks use-key eat. Only refuse when we are not starving.
        if (hunger > 14 || (shieldOff && hunger > 6)) {
            releaseUse(mc);
            eatTicks = 0;
            return;
        }

        Item food = null;
        for (Item i : FOODS) {
            if (mod.getItemStorage().hasItem(i)) {
                food = i;
                break;
            }
        }
        if (food == null) {
            releaseUse(mc);
            return;
        }
        try {
            mod.getSlotHandler().getClass().getMethod("forceEquipItem", Item.class)
                    .invoke(mod.getSlotHandler(), food);
        } catch (Throwable ignored) {}
        eatTicks++;
        if (eatTicks <= 30) {
            mc.options.useKey.setPressed(true);
            holdingUse = true;
        } else {
            releaseUse(mc);
            eatTicks = 0;
        }
    }

    private static void releaseUse(MinecraftClient mc) {
        if (!holdingUse) return;
        try {
            mc.options.useKey.setPressed(false);
        } catch (Throwable ignored) {}
        holdingUse = false;
    }
}
