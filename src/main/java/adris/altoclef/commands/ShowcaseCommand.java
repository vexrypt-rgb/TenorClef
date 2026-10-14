package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.BadCommandSyntaxException;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.multiversion.entity.PlayerVer;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds a small course east of the player (needs cheats: uses /fill) and paths across it with the
 * Ostinato movement feature being shown off.
 */
public class ShowcaseCommand extends Command {

    public ShowcaseCommand() throws CommandException {
        super("show", "Showcase course: everything | parkour | swim | dive | boat | ladder | pillar | bridge | tunnel | kinematic | physics | melee | ranged | horde | off",
                new StringArg("demo"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String demo = parser.get(String.class).toLowerCase();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            finish();
            return;
        }
        if (demo.equals("off")) {
            generation.incrementAndGet();
            release(mod);
            mod.log("Showcase stopped (kinematic/physics travel off).");
            finish();
            return;
        }
        if (demo.equals("all") || demo.equals("everything")) {
            startEverything(mod, mc);
            finish();
            return;
        }
        List<String> cmds = new ArrayList<>();
        BlockPos here = mc.player.getBlockPos();
        BlockPos goal = build(mod, demo, cmds, here, Math.min(here.getY() + AIR_HEIGHT, 250));
        for (String c : cmds) PlayerVer.sendChatCommand(mc.player, c);
        mod.log("Showcase '" + demo + "': course built, heading for the emerald pad.");
        final BlockPos g = goal;
        // Give the server a moment to apply the /fill commands before planning.
        Thread t = new Thread(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            mc.execute(() -> begin(mod, demo, g));
            traceWhileRunning(mod, mc);
        }, "showcase-start");
        t.setDaemon(true);
        t.start();
        finish();
    }

    /** Demos that make up "Everything", in order. physics is skipped on 1.21.11, which has no such setting. */
    private static final String[] EVERYTHING = {
            "parkour", "swim", "dive", "boat", "ladder", "pillar", "bridge", "tunnel", "kinematic",
            "melee", "ranged", "horde",
            //#if MC < 12111
            "physics",
            //#endif
    };
    /** Combat demos: a sealed arena full of mobs, finished when none are left rather than when a pad is reached. */
    private static boolean isCombat(String demo) {
        return demo.equals("melee") || demo.equals("ranged") || demo.equals("horde");
    }

    /** Hostile mobs still alive near the player (the arena is sealed, so this is the arena's population). */
    private static int hostilesLeft(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return 0;
        int n = 0;
        for (net.minecraft.entity.Entity e : mc.world.getEntities()) {
            if (e instanceof net.minecraft.entity.mob.HostileEntity && e.isAlive() && Math.abs(e.getY() - mc.player.getY()) < 10
                    && Math.hypot(e.getX() - mc.player.getX(), e.getZ() - mc.player.getZ()) < 30) n++;
        }
        return n;
    }

    /** On the goal pad: close horizontally, at the goal's height (not under it, having fallen off), and standing. */
    private static boolean arrivedAt(MinecraftClient mc, BlockPos g) {
        var p = mc.player;
        return p != null && Math.hypot(p.getX() - (g.getX() + 0.5), p.getZ() - (g.getZ() + 0.5)) < 2.5
                && p.getY() >= g.getY() - 0.2 && p.getY() < g.getY() + 1.5 && p.isOnGround();
    }

    /** Starts the demo's behaviour: a path to the goal pad, or the PvP process for combat demos. */
    private static void begin(AltoClef mod, String demo, BlockPos g) {
        if (isCombat(demo)) mod.getClientBaritone().getCommandManager().execute("pve hostiles");
        else mod.getClientBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(g.getX(), g.getY(), g.getZ()));
    }

    /** How far above the player's feet the courses float. */
    private static final int AIR_HEIGHT = 40;
    /** Gap between one course's pad and the next course's first platform in "everything". */
    private static final int COURSE_GAP = 12;
    private static final AtomicInteger generation = new AtomicInteger();

    /**
     * Runs every demo back to back. Each course is built where the previous one ended (the player stands on its
     * emerald pad), so they chain eastward. "show off" aborts the sequence.
     */
    private void startEverything(AltoClef mod, MinecraftClient mc) {
        final int gen = generation.incrementAndGet();
        // All courses share one height, fixed from where the player starts, and run on east of each other.
        final int airY = Math.min(mc.player.getBlockPos().getY() + AIR_HEIGHT, 250);
        mod.log("Showcase 'everything': " + EVERYTHING.length + " demos back to back, floating at y=" + airY
                + ". 'show off' aborts.");
        Thread t = new Thread(() -> {
            try {
                for (int i = 0; i < EVERYTHING.length; i++) {
                    if (generation.get() != gen) return;
                    String demo = EVERYTHING[i];
                    final int idx = i;
                    List<String> cmds = new ArrayList<>();
                    BlockPos[] goal = new BlockPos[1];
                    onGame(mc, () -> {
                        if (mc.player == null) return;
                        try {
                            BlockPos at = mc.player.getBlockPos();
                            goal[0] = build(mod, demo, cmds,
                                    idx == 0 ? at : at.add(COURSE_GAP, 0, 0), airY);
                        } catch (CommandException e) {
                            mod.log("Showcase everything: " + e.getMessage());
                            return;
                        }
                        for (String c : cmds) PlayerVer.sendChatCommand(mc.player, c);
                        mod.log("Showcase everything (" + (idx + 1) + "/" + EVERYTHING.length + "): " + demo);
                    });
                    if (goal[0] == null) return;
                    Thread.sleep(1500);
                    BlockPos g = goal[0];
                    onGame(mc, () -> begin(mod, demo, g));
                    // Wait for arrival; give up on this demo if it idles for 15 s or takes too long.
                    long start = System.currentTimeMillis(), idleSince = -1;
                    long limit = demo.equals("boat") ? 240_000 : isCombat(demo) ? 150_000 : 120_000;
                    final boolean combat = isCombat(demo);
                    while (generation.get() == gen && System.currentTimeMillis() - start < limit) {
                        Thread.sleep(500);
                        boolean[] st = new boolean[2]; // arrived, pathing
                        onGame(mc, () -> {
                            if (mc.player == null) return;
                            st[0] = combat ? hostilesLeft(mc) == 0 : arrivedAt(mc, g);
                            st[1] = combat || mod.getClientBaritone().getPathingBehavior().isPathing();
                        });
                        if (st[0]) break;
                        long now = System.currentTimeMillis();
                        if (st[1]) idleSince = -1;
                        else if (idleSince < 0) idleSince = now;
                        else if (now - idleSince > 15_000 && now - start > 5_000) break;
                    }
                    onGame(mc, () -> {
                        if (mc.player == null) return;
                        adris.altoclef.Debug.logHarness("SHOWRESULT " + demo + " arrived=" + arrivedAt(mc, g)
                                + " pos=" + mc.player.getBlockPos().toShortString() + " goal=" + g.toShortString()
                                + " hostiles=" + hostilesLeft(mc) + " hp=" + mc.player.getHealth()
                                + " elapsedMs=" + (System.currentTimeMillis() - start));
                        release(mod);
                    });
                    Thread.sleep(2000); // let the viewer see the finish
                }
                if (generation.get() == gen) mod.log("Showcase 'everything' complete.");
            } catch (InterruptedException ignored) {
            }
        }, "showcase-everything");
        t.setDaemon(true);
        t.start();
    }

    /** Stops pathing and drops every key the movement controllers forced (a forced W otherwise outlives the path). */
    private static void release(AltoClef mod) {
        mod.getClientBaritone().getPathingBehavior().cancelEverything();
        mod.getClientBaritone().getInputOverrideHandler().clearAllKeys();
    }

    /** Runs on the game thread and waits for it, so the worker can read and write game state safely. */
    private static void onGame(MinecraftClient mc, Runnable r) throws InterruptedException {
        CompletableFuture<Void> f = new CompletableFuture<>();
        mc.execute(() -> {
            try { r.run(); } finally { f.complete(null); }
        });
        try { f.get(10, TimeUnit.SECONDS); } catch (ExecutionException | TimeoutException ignored) { }
    }

    /** Resets the travel settings and returns the list of /fill commands for the course plus its goal. */
    private static BlockPos build(AltoClef mod, String demo, List<String> cmds, BlockPos p, int airY) throws CommandException {
        Settings s = mod.getClientBaritoneSettings();
        // Every course floats at airY with void all round, so the only way to the goal is the course itself.
        int x = p.getX(), y = airY, z = p.getZ();
        BlockPos goal;
        int startX = x; // where the player is put down
        s.kinematicTravel.value = false;
        //#if MC >= 12111
        s.slowKinematic.value = false;
        //#endif
        //#if MC < 12111
        s.physicsTravel.value = false;
        //#endif
        wipe(cmds, x, y, z);
        cmds.add("clear @s"); // each demo gets only its own items (leftover blocks let the bot bridge round a course)
        switch (demo) {
            case "parkour" -> {
                // Gaps of 4, 3 and 2 blocks, the last onto a 1-wide pillar, over a pit.
                clear(cmds, x, y, z, 30, 3);
                // run-up behind the start: a 4-gap sprint jump needs solid ground to accelerate on
                cmds.add(fill(x - 10, y - 1, z - 1, x + 2, y - 1, z + 1, "gold_block"));
                startX = x - 9; // start at the far end so the bot reaches full sprint before the first gap
                cmds.add(fill(x + 7, y - 1, z - 1, x + 9, y - 1, z + 1, "gold_block"));   // after 4-gap
                cmds.add(fill(x + 13, y - 1, z - 1, x + 15, y - 1, z + 1, "gold_block")); // after 3-gap
                cmds.add(fill(x + 18, y - 1, z, x + 18, y - 1, z, "gold_block"));         // 2-gap, narrow
                cmds.add(fill(x + 21, y - 1, z - 1, x + 26, y - 1, z + 1, "emerald_block"));
                s.allowParkour.value = true;
                goal = new BlockPos(x + 25, y, z);
            }
            case "swim", "dive" -> {
                // Glass-walled pool; "dive" roofs the middle so the route goes under it via an air pocket.
                boolean roof = demo.equals("dive");
                // The pool spans the whole corridor and tall glass walls seal the sides, so there is no way round.
                clear(cmds, x, y, z, 32, 4);
                // Bedrock can't be broken and the barrier on top is too tall to pillar past cheaply.
                cmds.add(fill(x - 2, y - 8, z - 5, x + 32, y + 5, z - 5, "bedrock"));
                cmds.add(fill(x - 2, y - 8, z + 5, x + 32, y + 5, z + 5, "bedrock"));
                cmds.add(fill(x - 2, y - 8, z - 5, x - 2, y + 5, z + 5, "bedrock"));
                cmds.add(fill(x - 2, y + 6, z - 5, x + 32, y + 14, z - 5, "barrier"));
                cmds.add(fill(x - 2, y + 6, z + 5, x + 32, y + 14, z + 5, "barrier"));
                cmds.add(fill(x - 2, y + 6, z - 5, x - 2, y + 14, z + 5, "barrier"));
                cmds.add(fill(x - 1, y - 1, z - 4, x + 2, y - 1, z + 4, "gold_block"));
                cmds.add(fill(x + 3, y - 7, z - 4, x + 27, y - 7, z + 4, "glass"));
                cmds.add(fill(x + 3, y - 6, z - 4, x + 27, y - 1, z + 4, "water"));
                cmds.add(fill(x + 28, y - 7, z - 4, x + 28, y - 1, z + 4, "glass"));
                if (roof) {
                    cmds.add(fill(x + 8, y - 1, z - 4, x + 22, y + 5, z + 4, "bedrock"));
                    cmds.add(fill(x + 15, y - 1, z - 1, x + 15, y - 1, z + 1, "air")); // air pocket
                }
                cmds.add(fill(x + 29, y - 1, z - 4, x + 31, y - 1, z + 4, "emerald_block"));
                s.swimInWater.value = true;
                goal = new BlockPos(x + 30, y, z);
            }
            case "boat" -> {
                // Long canal; the BoatProcess places the boat, sails and picks it back up.
                // Glass banks instead of a stone rim, so nobody can walk along the side of the water.
                clear(cmds, x, y, z, 100, 4);
                cmds.add(fill(x - 1, y - 1, z - 4, x + 1, y - 1, z + 4, "gold_block"));
                cmds.add(fill(x + 2, y - 3, z - 5, x + 93, y - 3, z + 5, "stone"));
                cmds.add(fill(x + 2, y - 2, z - 5, x + 93, y + 1, z - 5, "glass"));
                cmds.add(fill(x + 2, y - 2, z + 5, x + 93, y + 1, z + 5, "glass"));
                cmds.add(fill(x + 2, y - 2, z - 4, x + 93, y - 1, z + 4, "water"));
                cmds.add(fill(x + 94, y - 1, z - 4, x + 98, y - 1, z + 4, "emerald_block"));
                cmds.add("give @s oak_boat");
                s.allowBoats.value = true;
                goal = new BlockPos(x + 97, y, z);
            }
            case "ladder" -> {
                // A 21-high bedrock tower with a ladder up its west face; the goal pad is on top.
                clear(cmds, x, y, z, 8, 3);
                cmds.add(fill(x - 1, y - 1, z - 1, x + 2, y - 1, z + 1, "gold_block"));
                cmds.add(fill(x + 3, y - 1, z, x + 3, y + 19, z, "bedrock"));
                cmds.add(fill(x + 2, y, z, x + 2, y + 19, z, "ladder[facing=west]"));
                cmds.add(fill(x + 3, y + 19, z - 1, x + 6, y + 19, z + 1, "emerald_block"));
                goal = new BlockPos(x + 5, y + 20, z);
            }
            case "pillar" -> {
                // Nothing to climb: the bot has to build itself a tower to the pad 18 blocks up.
                clear(cmds, x, y, z, 8, 3);
                cmds.add(fill(x - 1, y - 1, z - 1, x + 2, y - 1, z + 1, "gold_block"));
                cmds.add(fill(x + 4, y + 17, z - 1, x + 6, y + 17, z + 1, "emerald_block"));
                cmds.add("give @s cobblestone 64");
                goal = new BlockPos(x + 5, y + 18, z);
            }
            case "bridge" -> {
                // A 22-block void gap: the bot has to extend a bridge across it.
                clear(cmds, x, y, z, 32, 3);
                cmds.add(fill(x - 1, y - 1, z - 1, x + 2, y - 1, z + 1, "gold_block"));
                cmds.add(fill(x + 25, y - 1, z - 1, x + 28, y - 1, z + 1, "emerald_block"));
                // one wall right beside the route: the clutch places each block against it
                cmds.add(fill(x + 3, y - 1, z - 1, x + 24, y + 20, z - 1, "bedrock"));
                cmds.add("give @s cobblestone 64");
                //#if MC >= 12111
                s.slowKinematic.value = true; // the wall-clutch driver: sprint-jumps off, laying blocks against the rails
                //#endif
                goal = new BlockPos(x + 27, y, z);
            }
            case "tunnel" -> {
                // A bedrock-shelled block of stone: the only way through is to mine straight along it.
                clear(cmds, x, y, z, 26, 5);
                cmds.add(fill(x - 1, y - 1, z - 1, x + 2, y - 1, z + 1, "gold_block"));
                cmds.add(fill(x + 3, y - 3, z - 5, x + 22, y + 5, z + 5, "bedrock"));
                cmds.add(fill(x + 4, y - 2, z - 4, x + 21, y + 4, z + 4, "stone"));
                cmds.add(fill(x + 3, y, z, x + 3, y + 1, z, "air"));
                cmds.add(fill(x + 22, y, z, x + 22, y + 1, z, "air"));
                cmds.add(fill(x + 23, y - 1, z - 1, x + 26, y - 1, z + 1, "emerald_block"));
                cmds.add("give @s iron_pickaxe");
                goal = new BlockPos(x + 25, y, z);
            }
            case "kinematic", "physics" -> {
                // Runway with a slalom of pillars: the look-ahead controllers carve smooth lines through it.
                clear(cmds, x, y, z, 64, 4);
                cmds.add(fill(x - 1, y - 1, z - 5, x + 63, y - 1, z + 5, "smooth_stone"));
                for (int i = 0; i < 6; i++) {
                    int px = x + 8 + i * 9, pz = z + (i % 2 == 0 ? -1 : 1) * 2;
                    cmds.add(fill(px, y, pz - 2, px, y + 2, pz + 2, "red_concrete"));
                }
                cmds.add(fill(x + 60, y - 1, z - 1, x + 63, y - 1, z + 1, "emerald_block"));
                if (demo.equals("kinematic")) s.kinematicTravel.value = true;
                //#if MC >= 12111
                //$$ else { /* Ostinato's 1.21.11 build has no physicsTravel setting */ }
                //#else
                else s.physicsTravel.value = true;
                //#endif
                goal = new BlockPos(x + 62, y, z);
            }
            case "melee", "ranged", "horde" -> {
                // Sealed bedrock arena (roofed, so sunlight can't burn the mobs); the PvP process hunts them all down.
                clear(cmds, x, y, z, 26, 8);
                cmds.add(fill(x - 1, y - 1, z - 9, x + 25, y + 7, z + 9, "bedrock"));
                cmds.add(fill(x, y - 1, z - 8, x + 24, y - 1, z + 8, "stone"));
                cmds.add(fill(x, y, z - 8, x + 24, y + 6, z + 8, "air"));
                cmds.add("difficulty normal");
                cmds.add("kill @e[type=zombie]");
                cmds.add("kill @e[type=skeleton]");
                cmds.add("kill @e[type=creeper]");
                cmds.add("clear @s");
                cmds.add("effect clear @s");
                startX = x + 2;
                boolean ranged = demo.equals("ranged");
                if (ranged) {
                    cmds.add("give @s bow");
                    cmds.add("give @s arrow 64");
                    cmds.add("give @s diamond_sword");
                } else {
                    cmds.add("give @s diamond_sword");
                    cmds.add("give @s diamond_axe");
                    cmds.add("give @s bow");
                    cmds.add("give @s arrow 32");
                }
                cmds.add("give @s golden_apple 8");
                cmds.add("give @s cooked_beef 32");
                cmds.add("item replace entity @s weapon.offhand with shield");
                cmds.add("item replace entity @s armor.head with iron_helmet");
                cmds.add("item replace entity @s armor.chest with iron_chestplate");
                cmds.add("item replace entity @s armor.legs with iron_leggings");
                cmds.add("item replace entity @s armor.feet with iron_boots");
                cmds.add("effect give @s regeneration 3600 1 true");
                String mob = ranged ? "skeleton" : "zombie";
                int count = demo.equals("horde") ? 8 : ranged ? 3 : 4;
                for (int i = 0; i < count; i++) {
                    cmds.add("summon " + mob + " " + (x + 14 + (i % 4) * 3) + " " + y + " " + (z - 6 + (i * 5) % 13));
                }
                if (demo.equals("horde")) {
                    for (int i = 0; i < 2; i++) cmds.add("summon skeleton " + (x + 22) + " " + y + " " + (z - 3 + i * 6));
                }
                goal = new BlockPos(x + 12, y, z);
            }
            default -> throw new BadCommandSyntaxException("Unknown demo '" + demo + "'. Try everything, parkour, swim, dive, boat, ladder, pillar, bridge, tunnel, kinematic, physics, melee, ranged, horde, off.");
        }
        // A fall off the course must not end the showcase, and the player starts on the course's first platform.
        if (!isCombat(demo)) cmds.add("effect give @s resistance 3600 4 true");
        cmds.add("effect give @s night_vision 3600 0 true");
        cmds.add("tp @s " + (startX + 0.5) + " " + y + " " + (z + 0.5) + " -90 0");
        return goal;
    }

    private static void traceWhileRunning(AltoClef mod, MinecraftClient mc) {
        {
            // -Dtenorclef.show.trace=true: log the player's water/pose state twice a second while the demo runs.
            for (int i = 0; Boolean.getBoolean("tenorclef.show.trace") && i < 80; i++) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                mc.execute(() -> {
                    var pl = mc.player;
                    if (pl == null) return;
                    String mv = "-";
                    var ex = mod.getClientBaritone().getPathingBehavior().getCurrent();
                    if (ex != null && ex.getPosition() < ex.getPath().movements().size()) {
                        var m = ex.getPath().movements().get(ex.getPosition());
                        mv = m.getClass().getSimpleName() + " " + m.getSrc().toShortString() + "->" + m.getDest().toShortString();
                    }
                    System.out.println("SHOWTRACE mv=" + mv);
                    System.out.printf("SHOWTRACE x=%.2f y=%.2f water=%s under=%s swim=%s sprint=%s sneak=%s pose=%s pitch=%.0f pathing=%s%n",
                            pl.getX(), pl.getY(), pl.isTouchingWater(), pl.isSubmergedInWater(), pl.isSwimming(), pl.isSprinting(),
                            pl.isSneaking(), pl.getPose(), pl.getPitch(), mod.getClientBaritone().getPathingBehavior().isPathing());
                });
            }
        }
    }

    /**
     * Turns everything a course could occupy (the longest is the 100-block boat canal, the tallest the ladder
     * tower) to air, so blocks left by earlier runs, or placed by the bot, never get in the way. A /fill is
     * capped at 32768 blocks, so it goes in slabs along x.
     */
    private static void wipe(List<String> cmds, int x, int y, int z) {
        for (int x0 = x - 12; x0 <= x + 112; x0 += 32) {
            cmds.add(fill(x0, y - 10, z - 7, x0 + 31, y + 26, z + 7, "air"));
        }
    }

    private static void clear(List<String> cmds, int x, int y, int z, int len, int half) {
        cmds.add(fill(x - 1, y, z - half - 1, x + len, y + 6, z + half + 1, "air"));
    }

    private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block;
    }
}
