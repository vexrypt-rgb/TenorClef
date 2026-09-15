package adris.altoclef.tasks.speedrun.testrun2.core;

import adris.altoclef.tasks.speedrun.testrun2.McCompat;

/**
 * One owner of forward/jump for @t2core only.
 * Place/eat/shield stay in SurviveTick + Baritone; this only nudges.
 */
public final class T2Input {

    private static int hold;

    private T2Input() {}

    public static void walkTurn() {
        McCompat.setYaw(McCompat.playerYaw() + 70f);
        McCompat.setMove(true, false);
        hold = 20 * 2;
    }

    public static void swim() {
        McCompat.setMove(true, true);
        hold = 15;
    }

    public static void noJump() {
        try {
            net.minecraft.client.MinecraftClient.getInstance().options.jumpKey.setPressed(false);
        } catch (Throwable ignored) {}
        McCompat.setMove(false, false);
    }

    /** Ctrl+K / @stop. Keys stay down until this runs. */
    public static void releaseAll() {
        hold = 0;
        try {
            var opt = net.minecraft.client.MinecraftClient.getInstance().options;
            opt.jumpKey.setPressed(false);
            opt.useKey.setPressed(false);
            opt.attackKey.setPressed(false);
            opt.sneakKey.setPressed(false);
        } catch (Throwable ignored) {}
        McCompat.setMove(false, false);
        try { adris.altoclef.tasks.speedrun.testrun2.HolePillar.reset(); } catch (Throwable ignored) {}
    }

    public static void tick() {
        if (hold > 0) {
            hold--;
            if (hold == 0) McCompat.setMove(false, false);
        }
    }
}
