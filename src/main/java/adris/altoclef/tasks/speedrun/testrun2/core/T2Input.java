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
        Float turn = safeTurn();
        if (turn == null) {
            // Every heading drops more than 3 blocks or meets lava: standing still is the nudge.
            McCompat.setMove(false, false);
            hold = 0;
            adris.altoclef.tasks.speedrun.testrun2.T2Log.warn("S187", "walk nudge suppressed: no safe heading");
            return;
        }
        McCompat.setYaw(McCompat.playerYaw() + turn);
        McCompat.setMove(true, false);
        hold = 20 * 2;
    }

    /** S187: first ledge/lava-free turn, or null. Falls back to the old +70° if the probe fails. */
    private static Float safeTurn() {
        try {
            var mod = adris.altoclef.AltoClef.getInstance();
            var world = mod.getWorld();
            var player = mod.getPlayer();
            if (world == null || player == null) return 70f;
            SafeHeading.Terrain terrain = (x, y, z) -> {
                net.minecraft.util.math.BlockPos p = new net.minecraft.util.math.BlockPos(x, y, z);
                net.minecraft.block.BlockState s = world.getBlockState(p);
                if (s.getBlock() == net.minecraft.block.Blocks.LAVA) return SafeHeading.Cell.LAVA;
                return s.getCollisionShape(world, p).isEmpty() ? SafeHeading.Cell.OPEN : SafeHeading.Cell.SOLID;
            };
            return SafeHeading.pickTurn(terrain, player.getX(), player.getY(), player.getZ(), McCompat.playerYaw());
        } catch (Throwable t) {
            return 70f;
        }
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
