package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.multiversion.ScreenVer;
import adris.altoclef.multiversion.entity.EntityVer;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractFurnaceScreen;
import net.minecraft.client.gui.screen.ingame.BrewingStandScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * The few game calls the testrun2 code needs on every supported version.
 *
 * Everything here is compiled against the game. Looking Minecraft members up by name at run time only works
 * in the dev environment: a release jar is remapped, the names are gone and every such lookup fails quietly.
 */
public final class McCompat {

    private McCompat() {}

    /** 16 for 1.16.x, 17 for 1.17.x, … 21 for 1.21.x, 26 for 26.x. */
    public static int gameMinor() {
        //#if MC >= 260000
        //$$ return 26;
        //#elseif MC >= 12100
        return 21;
        //#elseif MC >= 12000
        //$$ return 20;
        //#elseif MC >= 11900
        //$$ return 19;
        //#elseif MC >= 11800
        //$$ return 18;
        //#elseif MC >= 11700
        //$$ return 17;
        //#else
        //$$ return 16;
        //#endif
    }

    /** The item with this id ("mace", "MACE"), or null when this version has none. */
    public static Item item(String name) {
        Identifier id = id(name);
        return id != null && Registries.ITEM.containsId(id) ? Registries.ITEM.get(id) : null;
    }

    /** The block with this id, or null when this version has none. */
    public static Block block(String name) {
        Identifier id = id(name);
        return id != null && Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id) : null;
    }

    private static Identifier id(String name) {
        if (name == null) return null;
        try {
            return Identifier.of(name.toLowerCase(Locale.ROOT));
        } catch (RuntimeException notAnId) {
            return null;
        }
    }

    public static boolean gone(Entity e) {
        return e == null || !e.isAlive() || EntityVer.isGone(e);
    }

    private static ClientPlayerEntity player() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null ? null : mc.player;
    }

    public static float playerYaw() {
        ClientPlayerEntity p = player();
        return p == null ? 0f : p.getYaw();
    }

    public static void setYaw(float yaw) {
        ClientPlayerEntity p = player();
        if (p != null) p.setYaw(yaw);
    }

    /** Presses or releases the forward and jump keys. They stay as set until the next call. */
    public static void setMove(boolean forward, boolean jump) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        if (mc.player != null) mc.player.setSprinting(forward);
        mc.options.forwardKey.setPressed(forward);
        mc.options.jumpKey.setPressed(jump);
    }

    public static void cancelPathing() {
        adris.altoclef.AltoClef mod = adris.altoclef.AltoClef.getInstance();
        if (mod == null || mod.getClientBaritone() == null) return;
        mod.getClientBaritone().getPathingBehavior().cancelEverything();
        mod.getClientBaritone().getInputOverrideHandler().clearAllKeys();
    }

    public static Screen currentScreen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null ? null : ScreenVer.current(mc);
    }

    /** A furnace or brewing stand is open: the bot legitimately stands still while these work. */
    public static boolean slowScreenOpen() {
        Screen screen = currentScreen();
        return screen instanceof AbstractFurnaceScreen || screen instanceof BrewingStandScreen;
    }

    /** A container screen is open: inventory, crafting table, chest, furnace and the like. */
    public static boolean containerScreenOpen() {
        return currentScreen() instanceof HandledScreen;
    }

    public static void closeScreen() {
        ClientPlayerEntity p = player();
        if (p != null) p.closeHandledScreen();
    }

    /** Registry path of the biome at {@code pos} ("beach", "snowy_tundra"), or "?" when it has none. */
    public static String biomePath(World world, BlockPos pos) {
        if (world == null || pos == null) return "?";
        //#if MC >= 11802
        return world.getBiome(pos).getKey().map(k -> k.getValue().getPath()).orElse("?");
        //#elseif MC >= 11605
        //$$ Identifier id = world.getRegistryManager().get(net.minecraft.util.registry.Registry.BIOME_KEY).getId(world.getBiome(pos));
        //$$ return id == null ? "?" : id.getPath();
        //#else
        //$$ Identifier id = net.minecraft.util.registry.Registry.BIOME.getId(world.getBiome(pos));
        //$$ return id == null ? "?" : id.getPath();
        //#endif
    }

    /**
     * S195: true while Baritone is placing a block — the current path step is a pillar, or it
     * is holding right-click. Callers that force-equip a pickaxe must leave the hotbar alone
     * then: Baritone selects the block one tick and places it after a sneak tick, so swapping
     * the pick back in every tick means the block never lands (live run fix6: IRON @236,52,15
     * jumping with an empty hand for 2+ minutes while holding 27 cobblestone).
     */
    public static boolean baritonePlacing(adris.altoclef.AltoClef mod) {
        try {
            var bari = mod.getClientBaritone();
            if (bari.getInputOverrideHandler().isInputForcedDown(
                    baritone.api.utils.input.Input.CLICK_RIGHT)) {
                return true;
            }
            var ex = bari.getPathingBehavior().getCurrent();
            if (ex == null) return false;
            var moves = ex.getPath().movements();
            int i = ex.getPosition();
            return i >= 0 && i < moves.size()
                    && moves.get(i).getClass().getSimpleName().contains("Pillar");
        } catch (Throwable t) {
            return false;
        }
    }
}
