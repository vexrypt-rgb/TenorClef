package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Ostinato: string from cobwebs (broken with a sword, which drops string) whenever a web is known, else spiders.
 * Maze run15 spent 12+ minutes hunting spiders in daylight for string x2 while walking through cobwebs it could
 * not harvest (no sword, Baritone useSwordToMine off).
 */
public class CollectStringTask extends ResourceTask {
    private static final Item[] SWORDS = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
    private final int count;
    private boolean prevUseSword;

    public CollectStringTask(int count) {
        super(new ItemTarget(Items.STRING, count));
        this.count = count;
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {
        mod.getBehaviour().push();
        mod.getBehaviour().addProtectedItems(SWORDS);
        prevUseSword = mod.getClientBaritoneSettings().useSwordToMine.value;
        mod.getClientBaritoneSettings().useSwordToMine.value = true;
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        if (mod.getBlockScanner().anyFound(Blocks.COBWEB)) {
            if (!mod.getItemStorage().hasItem(SWORDS)) {
                setDebugState("Getting a sword for cobwebs");
                Item sword = mod.getItemStorage().hasItem(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.BLACKSTONE) ? Items.STONE_SWORD : Items.WOODEN_SWORD;
                return TaskCatalogue.getItemTask(sword, 1);
            }
            setDebugState("Cutting cobwebs for string");
            return new MineAndCollectTask(new ItemTarget(Items.STRING, count), new Block[]{Blocks.COBWEB}, MiningRequirement.HAND);
        }
        setDebugState("No cobwebs known, hunting spiders");
        return new KillAndLootTask(SpiderEntity.class, new ItemTarget(Items.STRING, count));
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
        mod.getClientBaritoneSettings().useSwordToMine.value = prevUseSword;
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectStringTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collect string (cobweb/spider)";
    }
}
