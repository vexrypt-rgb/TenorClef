package adris.altoclef.tasks.speedrun.testrun2.schematic;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasks.speedrun.testrun2.T2Brain;
import adris.altoclef.tasks.speedrun.testrun2.T2History;
import adris.altoclef.tasksystem.Task;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.io.File;

/**
 * Gather tools + bulk blocks, then ask Baritone to place a schematic.
 *
 * Softlocks this closes (Meloweh @build notes):
 *   1. Last cobble used to pillar out of the hole it mined for that cobble.
 *   2. Scaffold inside the schematic, then tear it down because the cell is air.
 *   3. Craft GUI left open.
 *   4. Re-collect a full stack after every single placed block.
 *
 * Sticky children. T2Brain overlays jump/water/GUI. Protected reserve of placeables.
 */
public class SchematicBuildTask extends Task {

    private enum Phase { KIT, RESERVE, BUILD, DONE }

    private static final int RESERVE_COBBLE = 32;
    private static final int RESERVE_DIRT = 16;
    private static final int BUILD_COBBLE = 64;
    private static final int HYSTERESIS = 16;

    private final File file;
    private final BlockPos origin;

    private Phase phase = Phase.KIT;
    private Task child;
    private Object builder;
    private boolean buildStarted;
    private int still;
    private int lastHash;
    private int placedHint;
    private boolean done;

    public SchematicBuildTask(File file) {
        this(file, null);
    }

    public SchematicBuildTask(File file, BlockPos origin) {
        this.file = file;
        this.origin = origin;
    }

    @Override
    protected void onStart() {
        T2Brain.reset();
        phase = Phase.KIT;
        child = null;
        builder = null;
        buildStarted = false;
        still = 0;
        done = false;
        AltoClef mod = AltoClef.getInstance();
        try {
            mod.getBehaviour().push();
            mod.getBehaviour().addProtectedItems(
                    Items.COBBLESTONE, Items.DIRT, Items.NETHERRACK,
                    Items.OAK_PLANKS, Items.CRAFTING_TABLE,
                    Items.STONE_PICKAXE, Items.IRON_PICKAXE,
                    Items.STONE_AXE, Items.STONE_SHOVEL);
        } catch (Throwable ignored) {}
        Debug.logMessage("SCHEM load " + file.getName()
                + " from " + file.getAbsolutePath()
                + (origin == null ? " origin=feet" : " origin=" + origin));
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;

        Task fix = T2Brain.help(mod, "SCHEM:" + phase, child);
        if (fix != null) {
            child = fix;
            return child;
        }

        int cobble = count(mod, Items.COBBLESTONE);
        int dirt = count(mod, Items.DIRT);
        int hash = cobble + dirt * 3 + count(mod, Items.STONE_PICKAXE) * 11
                + count(mod, Items.IRON_PICKAXE) * 13;
        if (hash != lastHash) {
            lastHash = hash;
            still = 0;
        } else {
            still++;
        }

        if (phase == Phase.KIT) {
            Task kit = kit(mod);
            if (kit != null) {
                child = kit;
                return child;
            }
            phase = Phase.RESERVE;
            T2History.note("SCHEM kit done");
        }

        if (phase == Phase.RESERVE) {
            // Hysteresis: only gather when below reserve, stop at BUILD_COBBLE.
            if (cobble < RESERVE_COBBLE) {
                child = TaskCatalogue.getItemTask(Items.COBBLESTONE, BUILD_COBBLE);
                return child;
            }
            if (dirt < RESERVE_DIRT) {
                child = TaskCatalogue.getItemTask(Items.DIRT, RESERVE_DIRT);
                return child;
            }
            phase = Phase.BUILD;
            T2History.note("SCHEM reserve cobble=" + cobble + " dirt=" + dirt);
        }

        if (phase == Phase.BUILD) {
            // Do not dump the reserve into a pillar-hole loop.
            if (cobble < HYSTERESIS) {
                T2History.note("SCHEM top-up cobble " + cobble);
                pauseBuilder();
                child = TaskCatalogue.getItemTask(Items.COBBLESTONE, BUILD_COBBLE);
                return child;
            }
            if (!buildStarted) {
                if (!startBuilder(mod)) {
                    Debug.logWarning("SCHEM E200 baritone builder failed to start — file or API");
                    done = true;
                    return null;
                }
                buildStarted = true;
            }
            if (builderFinished()) {
                phase = Phase.DONE;
                done = true;
                Debug.logMessage("SCHEM done " + file.getName());
                return null;
            }
            if (still > 20 * 25) {
                T2History.note("SCHEM E201 builder idle 25s — nudge");
                McCompat.setYaw(McCompat.playerYaw() + 45f);
                McCompat.setMove(true, false);
                still = 0;
                if (cobble < BUILD_COBBLE / 2) {
                    pauseBuilder();
                    buildStarted = false;
                    child = TaskCatalogue.getItemTask(Items.COBBLESTONE, BUILD_COBBLE);
                    return child;
                }
            }
            child = null;
            return null;
        }

        return null;
    }

    private Task kit(AltoClef mod) {
        if (count(mod, Items.STONE_PICKAXE) + count(mod, Items.IRON_PICKAXE)
                + count(mod, Items.DIAMOND_PICKAXE) < 1) {
            if (count(mod, Items.WOODEN_PICKAXE) < 1) {
                return TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
            }
            return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
        }
        if (count(mod, Items.STONE_AXE) + count(mod, Items.IRON_AXE) < 1
                && count(mod, Items.WOODEN_AXE) < 1) {
            return TaskCatalogue.getItemTask(Items.STONE_AXE, 1);
        }
        if (count(mod, Items.CRAFTING_TABLE) < 1) {
            try {
                if (!mod.getBlockScanner().anyFound(net.minecraft.block.Blocks.CRAFTING_TABLE)) {
                    return TaskCatalogue.getItemTask(Items.CRAFTING_TABLE, 1);
                }
            } catch (Throwable ignored) {
                return TaskCatalogue.getItemTask(Items.CRAFTING_TABLE, 1);
            }
        }
        return null;
    }

    private boolean startBuilder(AltoClef mod) {
        try {
            Object bari = mod.getClientBaritone();
            if (bari == null) return false;
            builder = bari.getClass().getMethod("getBuilderProcess").invoke(bari);
            if (builder == null) return false;
            BlockPos feet = origin != null ? origin : mod.getPlayer().getBlockPos();
            Vec3i originVec = new Vec3i(feet.getX(), feet.getY(), feet.getZ());
            boolean ok = false;
            try {
                Object r = builder.getClass()
                        .getMethod("build", String.class, File.class, Vec3i.class)
                        .invoke(builder, file.getName(), file, originVec);
                ok = !(r instanceof Boolean) || (Boolean) r;
            } catch (NoSuchMethodException e) {
                builder.getClass()
                        .getMethod("build", String.class, File.class, BlockPos.class)
                        .invoke(builder, file.getName(), file, feet);
                ok = true;
            }
            Debug.logMessage("SCHEM baritone build " + file.getName() + " @ " + feet + " ok=" + ok);
            return ok;
        } catch (Throwable t) {
            Debug.logWarning("SCHEM E200 " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    private void pauseBuilder() {
        if (builder == null) return;
        try {
            builder.getClass().getMethod("onLostControl").invoke(builder);
        } catch (Throwable ignored) {}
        McCompat.cancelPathing();
        buildStarted = false;
    }

    private boolean builderFinished() {
        if (builder == null) return false;
        try {
            Object active = builder.getClass().getMethod("isActive").invoke(builder);
            if (active instanceof Boolean && !((Boolean) active) && buildStarted && still > 20 * 3) {
                placedHint++;
                return placedHint > 2;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static int count(AltoClef mod, net.minecraft.item.Item item) {
        try {
            return mod.getItemStorage().getItemCount(item);
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    protected void onStop(Task interrupt) {
        pauseBuilder();
        try {
            AltoClef.getInstance().getBehaviour().pop();
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isFinished() {
        return done || phase == Phase.DONE;
    }

    @Override
    protected boolean isEqual(Task other) {
        if (!(other instanceof SchematicBuildTask o)) return false;
        return file.getName().equals(o.file.getName());
    }

    @Override
    protected String toDebugString() {
        return "schem " + file.getName() + " ph=" + phase;
    }
}
