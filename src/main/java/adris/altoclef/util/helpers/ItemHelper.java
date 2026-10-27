package adris.altoclef.util.helpers;

import adris.altoclef.multiversion.CItems;
import adris.altoclef.multiversion.CBlocks;

import adris.altoclef.AltoClef;
import adris.altoclef.multiversion.BlockTagVer;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.multiversion.versionedfields.Blocks;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.util.WoodType;
import net.minecraft.block.Block;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;

import java.util.*;

/**
 * Helper functions and definitions for useful groupings of items
 */
public class ItemHelper {
    public static final Item[] SAPLINGS = new Item[]{Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING,
            Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING, Items.MANGROVE_PROPAGULE,
            Items.CHERRY_SAPLING};
    public static final Block[] SAPLING_SOURCES = new Block[]{Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES,
            Blocks.BIRCH_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
            Blocks.MANGROVE_PROPAGULE, Blocks.CHERRY_LEAVES};
    public static final Item[] HOSTILE_MOB_DROPS = new Item[]{Items.BLAZE_ROD, Items.FEATHER, Items.CHICKEN,
            Items.COOKED_CHICKEN, Items.ROTTEN_FLESH, Items.ZOMBIE_HEAD, Items.GUNPOWDER, Items.CREEPER_HEAD,
            Items.TOTEM_OF_UNDYING, Items.EMERALD, Items.PORKCHOP, Items.COOKED_PORKCHOP, Items.LEATHER,
            Items.MAGMA_CREAM, Items.PHANTOM_MEMBRANE, Items.ARROW, Items.SADDLE, Items.SHULKER_SHELL, Items.BONE,
            Items.SKELETON_SKULL, Items.SLIME_BALL, Items.STRING, Items.SPIDER_EYE, Items.SCULK_CATALYST,
            Items.GLASS_BOTTLE, Items.GLOWSTONE_DUST, Items.REDSTONE, Items.STICK, Items.SUGAR, Items.POTION,
            Items.NETHER_STAR, Items.COAL, Items.WITHER_SKELETON_SKULL, Items.GHAST_TEAR, Items.IRON_INGOT,
            Items.CARROT, Items.POTATO, Items.BAKED_POTATO, Items.COPPER_INGOT};
    public static final Item[] DIRTS = new Item[]{Items.DIRT, Items.DIRT_PATH, Items.COARSE_DIRT, Items.ROOTED_DIRT};
    public static final Item[] PLANKS = new Item[]{Items.ACACIA_PLANKS, Items.BIRCH_PLANKS, Items.CRIMSON_PLANKS,
            Items.DARK_OAK_PLANKS, Items.OAK_PLANKS, Items.JUNGLE_PLANKS, Items.SPRUCE_PLANKS, Items.WARPED_PLANKS,
            Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.BAMBOO_PLANKS};
    public static final Item[] LEAVES = new Item[]{Items.ACACIA_LEAVES, Items.BIRCH_LEAVES, Items.DARK_OAK_LEAVES,
            Items.OAK_LEAVES, Items.JUNGLE_LEAVES, Items.SPRUCE_LEAVES, Items.MANGROVE_LEAVES, Items.CHERRY_LEAVES};
    public static final Item[] WOOD = new Item[]{Items.ACACIA_WOOD, Items.BIRCH_WOOD, Items.CRIMSON_HYPHAE,
            Items.DARK_OAK_WOOD, Items.OAK_WOOD, Items.JUNGLE_WOOD, Items.SPRUCE_WOOD, Items.WARPED_HYPHAE,
            Items.MANGROVE_WOOD};
    public static final Item[] WOOD_BUTTON = new Item[]{Items.ACACIA_BUTTON, Items.BIRCH_BUTTON, Items.CRIMSON_BUTTON,
            Items.DARK_OAK_BUTTON, Items.OAK_BUTTON, Items.JUNGLE_BUTTON, Items.SPRUCE_BUTTON, Items.WARPED_BUTTON,
            Items.MANGROVE_BUTTON, Items.BAMBOO_BUTTON, Items.CHERRY_BUTTON};
    public static final Item[] WOOD_SIGN = new Item[]{Items.ACACIA_SIGN, Items.BIRCH_SIGN, Items.CRIMSON_SIGN,
            Items.DARK_OAK_SIGN, Items.OAK_SIGN, Items.JUNGLE_SIGN, Items.SPRUCE_SIGN, Items.WARPED_SIGN,
            Items.MANGROVE_SIGN, Items.BAMBOO_SIGN, Items.CHERRY_SIGN};
    public static final Item[] WOOD_HANGING_SIGN = new Item[]{Items.ACACIA_HANGING_SIGN, Items.BIRCH_HANGING_SIGN,
            Items.CRIMSON_HANGING_SIGN, Items.DARK_OAK_HANGING_SIGN, Items.OAK_HANGING_SIGN, Items.JUNGLE_HANGING_SIGN,
            Items.SPRUCE_HANGING_SIGN, Items.WARPED_HANGING_SIGN, Items.MANGROVE_HANGING_SIGN, Items.BAMBOO_HANGING_SIGN,
            Items.CHERRY_HANGING_SIGN};
    public static final Item[] WOOD_PRESSURE_PLATE = new Item[]{Items.ACACIA_PRESSURE_PLATE, Items.BIRCH_PRESSURE_PLATE,
            Items.CRIMSON_PRESSURE_PLATE, Items.DARK_OAK_PRESSURE_PLATE, Items.OAK_PRESSURE_PLATE,
            Items.JUNGLE_PRESSURE_PLATE, Items.SPRUCE_PRESSURE_PLATE, Items.WARPED_PRESSURE_PLATE,
            Items.MANGROVE_PRESSURE_PLATE, Items.BAMBOO_PRESSURE_PLATE, Items.CHERRY_PRESSURE_PLATE};
    public static final Item[] WOOD_FENCE = new Item[]{Items.ACACIA_FENCE, Items.BIRCH_FENCE, Items.DARK_OAK_FENCE,
            Items.OAK_FENCE, Items.JUNGLE_FENCE, Items.SPRUCE_FENCE, Items.CRIMSON_FENCE, Items.WARPED_FENCE,
            Items.MANGROVE_FENCE, Items.BAMBOO_FENCE, Items.CHERRY_FENCE};
    public static final Item[] WOOD_FENCE_GATE = new Item[]{Items.ACACIA_FENCE_GATE, Items.BIRCH_FENCE_GATE,
            Items.DARK_OAK_FENCE_GATE, Items.OAK_FENCE_GATE, Items.JUNGLE_FENCE_GATE, Items.SPRUCE_FENCE_GATE,
            Items.CRIMSON_FENCE_GATE, Items.WARPED_FENCE_GATE, Items.MANGROVE_FENCE_GATE, Items.BAMBOO_FENCE_GATE,
            Items.CHERRY_FENCE_GATE};
    public static final Item[] WOOD_BOAT = new Item[]{Items.ACACIA_BOAT, Items.BIRCH_BOAT, Items.DARK_OAK_BOAT,
            Items.OAK_BOAT, Items.JUNGLE_BOAT, Items.SPRUCE_BOAT, Items.MANGROVE_BOAT, Items.CHERRY_BOAT};
    public static final Item[] WOOD_DOOR = new Item[]{Items.ACACIA_DOOR, Items.BIRCH_DOOR, Items.CRIMSON_DOOR,
            Items.DARK_OAK_DOOR, Items.OAK_DOOR, Items.JUNGLE_DOOR, Items.SPRUCE_DOOR, Items.WARPED_DOOR,
            Items.MANGROVE_DOOR, Items.BAMBOO_DOOR, Items.CHERRY_DOOR};
    public static final Item[] WOOD_SLAB = new Item[]{Items.ACACIA_SLAB, Items.BIRCH_SLAB, Items.CRIMSON_SLAB,
            Items.DARK_OAK_SLAB, Items.OAK_SLAB, Items.JUNGLE_SLAB, Items.SPRUCE_SLAB, Items.WARPED_SLAB,
            Items.MANGROVE_SLAB, Items.BAMBOO_SLAB, Items.CHERRY_SLAB};
    public static final Item[] WOOD_STAIRS = new Item[]{Items.ACACIA_STAIRS, Items.BIRCH_STAIRS, Items.CRIMSON_STAIRS,
            Items.DARK_OAK_STAIRS, Items.OAK_STAIRS, Items.JUNGLE_STAIRS, Items.SPRUCE_STAIRS, Items.WARPED_STAIRS,
            Items.MANGROVE_STAIRS, Items.BAMBOO_STAIRS, Items.CHERRY_STAIRS};
    public static final Item[] WOOD_TRAPDOOR = new Item[]{Items.ACACIA_TRAPDOOR, Items.BIRCH_TRAPDOOR,
            Items.CRIMSON_TRAPDOOR, Items.DARK_OAK_TRAPDOOR, Items.OAK_TRAPDOOR, Items.JUNGLE_TRAPDOOR,
            Items.SPRUCE_TRAPDOOR, Items.WARPED_TRAPDOOR, Items.MANGROVE_TRAPDOOR, Items.BAMBOO_TRAPDOOR,
            Items.CHERRY_TRAPDOOR};
    public static final Item[] LOG = new Item[]{Items.ACACIA_LOG, Items.BIRCH_LOG, Items.DARK_OAK_LOG, Items.OAK_LOG, Items.JUNGLE_LOG, Items.SPRUCE_LOG,
            Items.ACACIA_WOOD, Items.BIRCH_WOOD, Items.DARK_OAK_WOOD, Items.OAK_WOOD, Items.JUNGLE_WOOD, Items.SPRUCE_WOOD,
            Items.STRIPPED_ACACIA_LOG, Items.STRIPPED_BIRCH_LOG, Items.STRIPPED_DARK_OAK_LOG, Items.STRIPPED_OAK_LOG, Items.STRIPPED_JUNGLE_LOG, Items.STRIPPED_SPRUCE_LOG,
            Items.STRIPPED_ACACIA_WOOD, Items.STRIPPED_BIRCH_WOOD, Items.STRIPPED_DARK_OAK_WOOD, Items.STRIPPED_OAK_WOOD, Items.STRIPPED_JUNGLE_WOOD, Items.STRIPPED_SPRUCE_WOOD,
            Items.CRIMSON_STEM, Items.WARPED_STEM, Items.CRIMSON_HYPHAE, Items.WARPED_HYPHAE,
            Items.STRIPPED_CRIMSON_STEM, Items.STRIPPED_WARPED_STEM, Items.STRIPPED_CRIMSON_HYPHAE,
            Items.STRIPPED_WARPED_HYPHAE, Items.MANGROVE_LOG, Items.MANGROVE_WOOD, Items.STRIPPED_MANGROVE_LOG,
            Items.STRIPPED_MANGROVE_WOOD, Items.CHERRY_LOG, Items.CHERRY_WOOD, Items.STRIPPED_CHERRY_LOG,
            Items.STRIPPED_CHERRY_WOOD};
    public static final Item[] STRIPPED_LOGS = new Item[]{Items.STRIPPED_ACACIA_LOG, Items.STRIPPED_BIRCH_LOG,
            Items.STRIPPED_DARK_OAK_LOG, Items.STRIPPED_OAK_LOG, Items.STRIPPED_JUNGLE_LOG, Items.STRIPPED_SPRUCE_LOG,
            Items.STRIPPED_CRIMSON_STEM, Items.STRIPPED_WARPED_STEM, Items.STRIPPED_MANGROVE_LOG,
            Items.STRIPPED_CHERRY_LOG};
    public static final Item[] STRIPPABLE_LOGS = new Item[]{Items.ACACIA_LOG, Items.BIRCH_LOG, Items.DARK_OAK_LOG,
            Items.OAK_LOG, Items.JUNGLE_LOG, Items.SPRUCE_LOG, Items.CRIMSON_STEM, Items.WARPED_STEM, Items.MANGROVE_LOG,
            Items.CHERRY_LOG};
    public static final Item[] DYE = new Item[]{CItems.WHITE_DYE, CItems.BLACK_DYE, CItems.BLUE_DYE, CItems.BROWN_DYE, CItems.CYAN_DYE, CItems.GRAY_DYE, CItems.GREEN_DYE, CItems.LIGHT_BLUE_DYE, CItems.LIGHT_GRAY_DYE, CItems.LIME_DYE, CItems.MAGENTA_DYE, CItems.ORANGE_DYE, CItems.PINK_DYE, CItems.PURPLE_DYE, CItems.RED_DYE, CItems.YELLOW_DYE};
    public static final Item[] WOOL = new Item[]{CItems.WHITE_WOOL, CItems.BLACK_WOOL, CItems.BLUE_WOOL, CItems.BROWN_WOOL, CItems.CYAN_WOOL, CItems.GRAY_WOOL, CItems.GREEN_WOOL, CItems.LIGHT_BLUE_WOOL, CItems.LIGHT_GRAY_WOOL, CItems.LIME_WOOL, CItems.MAGENTA_WOOL, CItems.ORANGE_WOOL, CItems.PINK_WOOL, CItems.PURPLE_WOOL, CItems.RED_WOOL, CItems.YELLOW_WOOL};
    public static final Item[] BED = new Item[]{CItems.WHITE_BED, CItems.BLACK_BED, CItems.BLUE_BED, CItems.BROWN_BED, CItems.CYAN_BED, CItems.GRAY_BED, CItems.GREEN_BED, CItems.LIGHT_BLUE_BED, CItems.LIGHT_GRAY_BED, CItems.LIME_BED, CItems.MAGENTA_BED, CItems.ORANGE_BED, CItems.PINK_BED, CItems.PURPLE_BED, CItems.RED_BED, CItems.YELLOW_BED};
    public static final Item[] CARPET = new Item[]{CItems.WHITE_CARPET, CItems.BLACK_CARPET, CItems.BLUE_CARPET, CItems.BROWN_CARPET, CItems.CYAN_CARPET, CItems.GRAY_CARPET, CItems.GREEN_CARPET, CItems.LIGHT_BLUE_CARPET, CItems.LIGHT_GRAY_CARPET, CItems.LIME_CARPET, CItems.MAGENTA_CARPET, CItems.ORANGE_CARPET, CItems.PINK_CARPET, CItems.PURPLE_CARPET, CItems.RED_CARPET, CItems.YELLOW_CARPET};
    public static final Item[] SHULKER_BOXES = new Item[]{CItems.WHITE_SHULKER_BOX, CItems.BLACK_SHULKER_BOX, CItems.BLUE_SHULKER_BOX, CItems.BROWN_SHULKER_BOX, CItems.CYAN_SHULKER_BOX, CItems.GRAY_SHULKER_BOX, CItems.GREEN_SHULKER_BOX, CItems.LIGHT_BLUE_SHULKER_BOX, CItems.LIGHT_GRAY_SHULKER_BOX, CItems.LIME_SHULKER_BOX, CItems.MAGENTA_SHULKER_BOX, CItems.ORANGE_SHULKER_BOX, CItems.PINK_SHULKER_BOX, CItems.PURPLE_SHULKER_BOX, CItems.RED_SHULKER_BOX, CItems.YELLOW_SHULKER_BOX};
    public static final Item[] FLOWER = new Item[]{Items.ALLIUM, Items.AZURE_BLUET, Items.BLUE_ORCHID, Items.CORNFLOWER, Items.DANDELION, Items.LILAC, Items.LILY_OF_THE_VALLEY, Items.ORANGE_TULIP, Items.OXEYE_DAISY, Items.PINK_TULIP, Items.POPPY, Items.PEONY, Items.RED_TULIP, Items.ROSE_BUSH, Items.SUNFLOWER, Items.WHITE_TULIP};
    public static final Item[] LEATHER_ARMORS = new Item[]{Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_HELMET, Items.LEATHER_BOOTS};
    public static final Item[] GOLDEN_ARMORS = new Item[]{Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_HELMET, Items.GOLDEN_BOOTS};
    public static final Item[] IRON_ARMORS = new Item[]{Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_HELMET, Items.IRON_BOOTS};
    public static final Item[] DIAMOND_ARMORS = new Item[]{Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_HELMET, Items.DIAMOND_BOOTS};
    public static final Item[] NETHERITE_ARMORS = new Item[]{Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_HELMET, Items.NETHERITE_BOOTS};
    public static final Item[] WOODEN_TOOLS = new Item[]{Items.WOODEN_PICKAXE, Items.WOODEN_SHOVEL, Items.WOODEN_SWORD, Items.WOODEN_AXE, Items.WOODEN_HOE};
    public static final Item[] STONE_TOOLS = new Item[]{Items.STONE_PICKAXE, Items.STONE_SHOVEL, Items.STONE_SWORD, Items.STONE_AXE, Items.STONE_HOE};
    public static final Item[] IRON_TOOLS = new Item[]{Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_HOE};
    public static final Item[] GOLDEN_TOOLS = new Item[]{Items.GOLDEN_PICKAXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_SWORD, Items.GOLDEN_AXE, Items.GOLDEN_HOE};
    public static final Item[] DIAMOND_TOOLS = new Item[]{Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_HOE};
    public static final Item[] NETHERITE_TOOLS = new Item[]{Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_HOE};
    public static final Block[] WOOD_SIGNS_ALL = new Block[]{Blocks.ACACIA_SIGN, Blocks.BIRCH_SIGN, Blocks.DARK_OAK_SIGN,
            Blocks.OAK_SIGN, Blocks.JUNGLE_SIGN, Blocks.SPRUCE_SIGN, Blocks.ACACIA_WALL_SIGN, Blocks.BIRCH_WALL_SIGN,
            Blocks.DARK_OAK_WALL_SIGN, Blocks.OAK_WALL_SIGN, Blocks.JUNGLE_WALL_SIGN, Blocks.SPRUCE_WALL_SIGN,
            Blocks.MANGROVE_SIGN, Blocks.MANGROVE_WALL_SIGN, Blocks.BAMBOO_SIGN, Blocks.BAMBOO_WALL_SIGN,
            Blocks.CHERRY_SIGN, Blocks.CHERRY_WALL_SIGN};

    private static final Map<Item, Item> logToPlanks = new HashMap<>() {
        {
            put(Items.CHERRY_LOG, Items.CHERRY_PLANKS);
            //#if MC >= 12104
            //$$ put(Items.PALE_OAK_LOG, Items.PALE_OAK_PLANKS);
            //#endif
            put(Items.CHERRY_WOOD, Items.CHERRY_PLANKS);
            //#if MC >= 12104
            //$$ put(Items.PALE_OAK_WOOD, Items.PALE_OAK_PLANKS);
            //#endif
            put(Items.STRIPPED_CHERRY_LOG, Items.CHERRY_PLANKS);
            //#if MC >= 12104
            //$$ put(Items.STRIPPED_PALE_OAK_LOG, Items.PALE_OAK_PLANKS);
            //#endif
            put(Items.STRIPPED_CHERRY_WOOD, Items.CHERRY_PLANKS);
            //#if MC >= 12104
            //$$ put(Items.STRIPPED_PALE_OAK_WOOD, Items.PALE_OAK_PLANKS);
            //#endif
            put(Items.MANGROVE_LOG, Items.MANGROVE_PLANKS);
            put(Items.MANGROVE_WOOD, Items.MANGROVE_PLANKS);
            put(Items.STRIPPED_MANGROVE_LOG, Items.MANGROVE_PLANKS);
            put(Items.STRIPPED_MANGROVE_WOOD, Items.MANGROVE_PLANKS);
            put(Items.ACACIA_LOG, Items.ACACIA_PLANKS);
            put(Items.BIRCH_LOG, Items.BIRCH_PLANKS);
            put(Items.CRIMSON_STEM, Items.CRIMSON_PLANKS);
            put(Items.DARK_OAK_LOG, Items.DARK_OAK_PLANKS);
            put(Items.OAK_LOG, Items.OAK_PLANKS);
            put(Items.JUNGLE_LOG, Items.JUNGLE_PLANKS);
            put(Items.SPRUCE_LOG, Items.SPRUCE_PLANKS);
            put(Items.WARPED_STEM, Items.WARPED_PLANKS);
            put(Items.STRIPPED_ACACIA_LOG, Items.ACACIA_PLANKS);
            put(Items.STRIPPED_BIRCH_LOG, Items.BIRCH_PLANKS);
            put(Items.STRIPPED_CRIMSON_STEM, Items.CRIMSON_PLANKS);
            put(Items.STRIPPED_DARK_OAK_LOG, Items.DARK_OAK_PLANKS);
            put(Items.STRIPPED_OAK_LOG, Items.OAK_PLANKS);
            put(Items.STRIPPED_JUNGLE_LOG, Items.JUNGLE_PLANKS);
            put(Items.STRIPPED_SPRUCE_LOG, Items.SPRUCE_PLANKS);
            put(Items.STRIPPED_WARPED_STEM, Items.WARPED_PLANKS);
            put(Items.ACACIA_WOOD, Items.ACACIA_PLANKS);
            put(Items.BIRCH_WOOD, Items.BIRCH_PLANKS);
            put(Items.CRIMSON_HYPHAE, Items.CRIMSON_PLANKS);
            put(Items.DARK_OAK_WOOD, Items.DARK_OAK_PLANKS);
            put(Items.OAK_WOOD, Items.OAK_PLANKS);
            put(Items.JUNGLE_WOOD, Items.JUNGLE_PLANKS);
            put(Items.SPRUCE_WOOD, Items.SPRUCE_PLANKS);
            put(Items.WARPED_HYPHAE, Items.WARPED_PLANKS);
            put(Items.STRIPPED_ACACIA_WOOD, Items.ACACIA_PLANKS);
            put(Items.STRIPPED_BIRCH_WOOD, Items.BIRCH_PLANKS);
            put(Items.STRIPPED_CRIMSON_HYPHAE, Items.CRIMSON_PLANKS);
            put(Items.STRIPPED_DARK_OAK_WOOD, Items.DARK_OAK_PLANKS);
            put(Items.STRIPPED_OAK_WOOD, Items.OAK_PLANKS);
            put(Items.STRIPPED_JUNGLE_WOOD, Items.JUNGLE_PLANKS);
            put(Items.STRIPPED_SPRUCE_WOOD, Items.SPRUCE_PLANKS);
            put(Items.STRIPPED_WARPED_HYPHAE, Items.WARPED_PLANKS);
        }
    };
    private static final Map<Item, Item> planksToLogs = new HashMap<>() {
        {
            put(Items.CHERRY_PLANKS, Items.CHERRY_LOG);
            //#if MC >= 12104
            //$$ put(Items.PALE_OAK_PLANKS, Items.PALE_OAK_LOG);
            //#endif
            put(Items.MANGROVE_PLANKS, Items.MANGROVE_LOG);
            put(Items.ACACIA_PLANKS, Items.ACACIA_LOG);
            put(Items.BIRCH_PLANKS, Items.BIRCH_LOG);
            put(Items.CRIMSON_PLANKS, Items.CRIMSON_STEM);
            put(Items.DARK_OAK_PLANKS, Items.DARK_OAK_LOG);
            put(Items.OAK_PLANKS, Items.OAK_LOG);
            put(Items.JUNGLE_PLANKS, Items.JUNGLE_LOG);
            put(Items.SPRUCE_PLANKS, Items.SPRUCE_LOG);
            put(Items.WARPED_PLANKS, Items.WARPED_STEM);
        }
    };
    private static final Map<Item, Item> strippedToLogs = new HashMap<>() {
        {
            put(Items.STRIPPED_CHERRY_LOG, Items.CHERRY_LOG);
            //#if MC >= 12104
            //$$ put(Items.STRIPPED_PALE_OAK_LOG, Items.PALE_OAK_LOG);
            //#endif
            put(Items.STRIPPED_MANGROVE_LOG, Items.MANGROVE_LOG);
            put(Items.STRIPPED_ACACIA_LOG, Items.ACACIA_LOG);
            put(Items.STRIPPED_BIRCH_LOG, Items.BIRCH_LOG);
            put(Items.STRIPPED_CRIMSON_STEM, Items.CRIMSON_STEM);
            put(Items.STRIPPED_DARK_OAK_LOG, Items.DARK_OAK_LOG);
            put(Items.STRIPPED_OAK_LOG, Items.OAK_LOG);
            put(Items.STRIPPED_JUNGLE_LOG, Items.JUNGLE_LOG);
            put(Items.STRIPPED_SPRUCE_LOG, Items.SPRUCE_LOG);
            put(Items.STRIPPED_WARPED_STEM, Items.WARPED_STEM);
        }
    };
    // This is kinda jank ngl
    private static final Map<MapColor, ColorfulItems> colorMap = new HashMap<MapColor, ColorfulItems>() {
        {
            p(DyeColor.RED, "red", CItems.RED_DYE, CItems.RED_WOOL, CItems.RED_BED, CItems.RED_CARPET, CItems.RED_STAINED_GLASS, CItems.RED_STAINED_GLASS_PANE, CItems.RED_TERRACOTTA, CItems.RED_GLAZED_TERRACOTTA, CItems.RED_CONCRETE, CItems.RED_CONCRETE_POWDER, CItems.RED_BANNER, CItems.RED_SHULKER_BOX, CBlocks.RED_WALL_BANNER);
            p(DyeColor.WHITE, "white", CItems.WHITE_DYE, CItems.WHITE_WOOL, CItems.WHITE_BED, CItems.WHITE_CARPET, CItems.WHITE_STAINED_GLASS, CItems.WHITE_STAINED_GLASS_PANE, CItems.WHITE_TERRACOTTA, CItems.WHITE_GLAZED_TERRACOTTA, CItems.WHITE_CONCRETE, CItems.WHITE_CONCRETE_POWDER, CItems.WHITE_BANNER, CItems.WHITE_SHULKER_BOX, CBlocks.WHITE_WALL_BANNER);
            p(DyeColor.BLACK, "black", CItems.BLACK_DYE, CItems.BLACK_WOOL, CItems.BLACK_BED, CItems.BLACK_CARPET, CItems.BLACK_STAINED_GLASS, CItems.BLACK_STAINED_GLASS_PANE, CItems.BLACK_TERRACOTTA, CItems.BLACK_GLAZED_TERRACOTTA, CItems.BLACK_CONCRETE, CItems.BLACK_CONCRETE_POWDER, CItems.BLACK_BANNER, CItems.BLACK_SHULKER_BOX, CBlocks.BLACK_WALL_BANNER);
            p(DyeColor.BLUE, "blue", CItems.BLUE_DYE, CItems.BLUE_WOOL, CItems.BLUE_BED, CItems.BLUE_CARPET, CItems.BLUE_STAINED_GLASS, CItems.BLUE_STAINED_GLASS_PANE, CItems.BLUE_TERRACOTTA, CItems.BLUE_GLAZED_TERRACOTTA, CItems.BLUE_CONCRETE, CItems.BLUE_CONCRETE_POWDER, CItems.BLUE_BANNER, CItems.BLUE_SHULKER_BOX, CBlocks.BLUE_WALL_BANNER);
            p(DyeColor.BROWN, "brown", CItems.BROWN_DYE, CItems.BROWN_WOOL, CItems.BROWN_BED, CItems.BROWN_CARPET, CItems.BROWN_STAINED_GLASS, CItems.BROWN_STAINED_GLASS_PANE, CItems.BROWN_TERRACOTTA, CItems.BROWN_GLAZED_TERRACOTTA, CItems.BROWN_CONCRETE, CItems.BROWN_CONCRETE_POWDER, CItems.BROWN_BANNER, CItems.BROWN_SHULKER_BOX, CBlocks.BROWN_WALL_BANNER);
            p(DyeColor.CYAN, "cyan", CItems.CYAN_DYE, CItems.CYAN_WOOL, CItems.CYAN_BED, CItems.CYAN_CARPET, CItems.CYAN_STAINED_GLASS, CItems.CYAN_STAINED_GLASS_PANE, CItems.CYAN_TERRACOTTA, CItems.CYAN_GLAZED_TERRACOTTA, CItems.CYAN_CONCRETE, CItems.CYAN_CONCRETE_POWDER, CItems.CYAN_BANNER, CItems.CYAN_SHULKER_BOX, CBlocks.CYAN_WALL_BANNER);
            p(DyeColor.GRAY, "gray", CItems.GRAY_DYE, CItems.GRAY_WOOL, CItems.GRAY_BED, CItems.GRAY_CARPET, CItems.GRAY_STAINED_GLASS, CItems.GRAY_STAINED_GLASS_PANE, CItems.GRAY_TERRACOTTA, CItems.GRAY_GLAZED_TERRACOTTA, CItems.GRAY_CONCRETE, CItems.GRAY_CONCRETE_POWDER, CItems.GRAY_BANNER, CItems.GRAY_SHULKER_BOX, CBlocks.GRAY_WALL_BANNER);
            p(DyeColor.GREEN, "green", CItems.GREEN_DYE, CItems.GREEN_WOOL, CItems.GREEN_BED, CItems.GREEN_CARPET, CItems.GREEN_STAINED_GLASS, CItems.GREEN_STAINED_GLASS_PANE, CItems.GREEN_TERRACOTTA, CItems.GREEN_GLAZED_TERRACOTTA, CItems.GREEN_CONCRETE, CItems.GREEN_CONCRETE_POWDER, CItems.GREEN_BANNER, CItems.GREEN_SHULKER_BOX, CBlocks.GREEN_WALL_BANNER);
            p(DyeColor.LIGHT_BLUE, "light_blue", CItems.LIGHT_BLUE_DYE, CItems.LIGHT_BLUE_WOOL, CItems.LIGHT_BLUE_BED, CItems.LIGHT_BLUE_CARPET, CItems.LIGHT_BLUE_STAINED_GLASS, CItems.LIGHT_BLUE_STAINED_GLASS_PANE, CItems.LIGHT_BLUE_TERRACOTTA, CItems.LIGHT_BLUE_GLAZED_TERRACOTTA, CItems.LIGHT_BLUE_CONCRETE, CItems.LIGHT_BLUE_CONCRETE_POWDER, CItems.LIGHT_BLUE_BANNER, CItems.LIGHT_BLUE_SHULKER_BOX, CBlocks.LIGHT_BLUE_WALL_BANNER);
            p(DyeColor.LIGHT_GRAY, "light_gray", CItems.LIGHT_GRAY_DYE, CItems.LIGHT_GRAY_WOOL, CItems.LIGHT_GRAY_BED, CItems.LIGHT_GRAY_CARPET, CItems.LIGHT_GRAY_STAINED_GLASS, CItems.LIGHT_GRAY_STAINED_GLASS_PANE, CItems.LIGHT_GRAY_TERRACOTTA, CItems.LIGHT_GRAY_GLAZED_TERRACOTTA, CItems.LIGHT_GRAY_CONCRETE, CItems.LIGHT_GRAY_CONCRETE_POWDER, CItems.LIGHT_GRAY_BANNER, CItems.LIGHT_GRAY_SHULKER_BOX, CBlocks.LIGHT_GRAY_WALL_BANNER);
            p(DyeColor.LIME, "lime", CItems.LIME_DYE, CItems.LIME_WOOL, CItems.LIME_BED, CItems.LIME_CARPET, CItems.LIME_STAINED_GLASS, CItems.LIME_STAINED_GLASS_PANE, CItems.LIME_TERRACOTTA, CItems.LIME_GLAZED_TERRACOTTA, CItems.LIME_CONCRETE, CItems.LIME_CONCRETE_POWDER, CItems.LIME_BANNER, CItems.LIME_SHULKER_BOX, CBlocks.LIME_WALL_BANNER);
            p(DyeColor.MAGENTA, "magenta", CItems.MAGENTA_DYE, CItems.MAGENTA_WOOL, CItems.MAGENTA_BED, CItems.MAGENTA_CARPET, CItems.MAGENTA_STAINED_GLASS, CItems.MAGENTA_STAINED_GLASS_PANE, CItems.MAGENTA_TERRACOTTA, CItems.MAGENTA_GLAZED_TERRACOTTA, CItems.MAGENTA_CONCRETE, CItems.MAGENTA_CONCRETE_POWDER, CItems.MAGENTA_BANNER, CItems.MAGENTA_SHULKER_BOX, CBlocks.MAGENTA_WALL_BANNER);
            p(DyeColor.ORANGE, "orange", CItems.ORANGE_DYE, CItems.ORANGE_WOOL, CItems.ORANGE_BED, CItems.ORANGE_CARPET, CItems.ORANGE_STAINED_GLASS, CItems.ORANGE_STAINED_GLASS_PANE, CItems.ORANGE_TERRACOTTA, CItems.ORANGE_GLAZED_TERRACOTTA, CItems.ORANGE_CONCRETE, CItems.ORANGE_CONCRETE_POWDER, CItems.ORANGE_BANNER, CItems.ORANGE_SHULKER_BOX, CBlocks.ORANGE_WALL_BANNER);
            p(DyeColor.PINK, "pink", CItems.PINK_DYE, CItems.PINK_WOOL, CItems.PINK_BED, CItems.PINK_CARPET, CItems.PINK_STAINED_GLASS, CItems.PINK_STAINED_GLASS_PANE, CItems.PINK_TERRACOTTA, CItems.PINK_GLAZED_TERRACOTTA, CItems.PINK_CONCRETE, CItems.PINK_CONCRETE_POWDER, CItems.PINK_BANNER, CItems.PINK_SHULKER_BOX, CBlocks.PINK_WALL_BANNER);
            p(DyeColor.PURPLE, "purple", CItems.PURPLE_DYE, CItems.PURPLE_WOOL, CItems.PURPLE_BED, CItems.PURPLE_CARPET, CItems.PURPLE_STAINED_GLASS, CItems.PURPLE_STAINED_GLASS_PANE, CItems.PURPLE_TERRACOTTA, CItems.PURPLE_GLAZED_TERRACOTTA, CItems.PURPLE_CONCRETE, CItems.PURPLE_CONCRETE_POWDER, CItems.PURPLE_BANNER, CItems.PURPLE_SHULKER_BOX, CBlocks.PURPLE_WALL_BANNER);
            p(DyeColor.RED, "red", CItems.RED_DYE, CItems.RED_WOOL, CItems.RED_BED, CItems.RED_CARPET, CItems.RED_STAINED_GLASS, CItems.RED_STAINED_GLASS_PANE, CItems.RED_TERRACOTTA, CItems.RED_GLAZED_TERRACOTTA, CItems.RED_CONCRETE, CItems.RED_CONCRETE_POWDER, CItems.RED_BANNER, CItems.RED_SHULKER_BOX, CBlocks.RED_WALL_BANNER);
            p(DyeColor.YELLOW, "yellow", CItems.YELLOW_DYE, CItems.YELLOW_WOOL, CItems.YELLOW_BED, CItems.YELLOW_CARPET, CItems.YELLOW_STAINED_GLASS, CItems.YELLOW_STAINED_GLASS_PANE, CItems.YELLOW_TERRACOTTA, CItems.YELLOW_GLAZED_TERRACOTTA, CItems.YELLOW_CONCRETE, CItems.YELLOW_CONCRETE_POWDER, CItems.YELLOW_BANNER, CItems.YELLOW_SHULKER_BOX, CBlocks.YELLOW_WALL_BANNER);
        }

        void p(DyeColor color, String colorName, Item dye, Item wool, Item bed, Item carpet, Item stainedGlass, Item stainedGlassPane, Item terracotta, Item glazedTerracotta, Item concrete, Item concretePowder, Item banner, Item shulker, Block wallBanner) {
            put(color.getMapColor(), new ColorfulItems(color, colorName, dye, wool, bed, carpet, stainedGlass, stainedGlassPane, terracotta, glazedTerracotta, concrete, concretePowder, banner, shulker, wallBanner));
        }
    };
    private static final Map<WoodType, WoodItems> woodMap = new HashMap<WoodType, WoodItems>() {
        {
            p(WoodType.CHERRY, "cherry", Items.CHERRY_PLANKS, Items.CHERRY_LOG, Items.STRIPPED_CHERRY_LOG, Items.STRIPPED_CHERRY_WOOD, Items.CHERRY_WOOD, Items.CHERRY_SIGN, Items.CHERRY_HANGING_SIGN, Items.CHERRY_DOOR, Items.CHERRY_BUTTON, Items.CHERRY_STAIRS, Items.CHERRY_SLAB, Items.CHERRY_FENCE, Items.CHERRY_FENCE_GATE, Items.CHERRY_BOAT, Items.CHERRY_SAPLING, Items.CHERRY_LEAVES, Items.CHERRY_PRESSURE_PLATE, Items.CHERRY_TRAPDOOR);
            //#if MC >= 12104
            //$$ p(WoodType.PALE_OAK, "pale_oak", Items.PALE_OAK_PLANKS, Items.PALE_OAK_LOG, Items.STRIPPED_PALE_OAK_LOG, Items.STRIPPED_PALE_OAK_WOOD, Items.PALE_OAK_WOOD, Items.PALE_OAK_SIGN, Items.PALE_OAK_HANGING_SIGN, Items.PALE_OAK_DOOR, Items.PALE_OAK_BUTTON, Items.PALE_OAK_STAIRS, Items.PALE_OAK_SLAB, Items.PALE_OAK_FENCE, Items.PALE_OAK_FENCE_GATE, Items.PALE_OAK_BOAT, Items.PALE_OAK_SAPLING, Items.PALE_OAK_LEAVES, Items.PALE_OAK_PRESSURE_PLATE, Items.PALE_OAK_TRAPDOOR);
            //#endif
            p(WoodType.BAMBOO, "bamboo", null, null, Items.STRIPPED_BAMBOO_BLOCK, null, null, Items.BAMBOO_SIGN, Items.BAMBOO_HANGING_SIGN, Items.BAMBOO_DOOR, Items.BAMBOO_BUTTON, Items.BAMBOO_STAIRS, Items.BAMBOO_SLAB, Items.BAMBOO_FENCE, Items.BAMBOO_FENCE_GATE, Items.BAMBOO_RAFT, Items.BAMBOO, null, Items.BAMBOO_PRESSURE_PLATE, Items.BAMBOO_TRAPDOOR);
            p(WoodType.MANGROVE, "mangrove", Items.MANGROVE_PLANKS, Items.MANGROVE_LOG, Items.STRIPPED_MANGROVE_LOG, Items.STRIPPED_MANGROVE_WOOD, Items.MANGROVE_WOOD, Items.MANGROVE_SIGN, Items.MANGROVE_HANGING_SIGN, Items.MANGROVE_DOOR, Items.MANGROVE_BUTTON, Items.MANGROVE_STAIRS, Items.MANGROVE_SLAB, Items.MANGROVE_FENCE, Items.MANGROVE_FENCE_GATE, Items.MANGROVE_BOAT, Items.MANGROVE_PROPAGULE, Items.MANGROVE_LEAVES, Items.MANGROVE_PRESSURE_PLATE, Items.MANGROVE_TRAPDOOR);
            p(WoodType.ACACIA, "acacia", Items.ACACIA_PLANKS, Items.ACACIA_LOG, Items.STRIPPED_ACACIA_LOG, Items.STRIPPED_ACACIA_WOOD, Items.ACACIA_WOOD, Items.ACACIA_SIGN, Items.ACACIA_HANGING_SIGN, Items.ACACIA_DOOR, Items.ACACIA_BUTTON, Items.ACACIA_STAIRS, Items.ACACIA_SLAB, Items.ACACIA_FENCE, Items.ACACIA_FENCE_GATE, Items.ACACIA_BOAT, Items.ACACIA_SAPLING, Items.ACACIA_LEAVES, Items.ACACIA_PRESSURE_PLATE, Items.ACACIA_TRAPDOOR);
            p(WoodType.BIRCH, "birch", Items.BIRCH_PLANKS, Items.BIRCH_LOG, Items.STRIPPED_BIRCH_LOG, Items.STRIPPED_BIRCH_WOOD, Items.BIRCH_WOOD, Items.BIRCH_SIGN, Items.BIRCH_HANGING_SIGN, Items.BIRCH_DOOR, Items.BIRCH_BUTTON, Items.BIRCH_STAIRS, Items.BIRCH_SLAB, Items.BIRCH_FENCE, Items.BIRCH_FENCE_GATE, Items.BIRCH_BOAT, Items.BIRCH_SAPLING, Items.BIRCH_LEAVES, Items.BIRCH_PRESSURE_PLATE, Items.BIRCH_TRAPDOOR);
            p(WoodType.CRIMSON, "crimson", Items.CRIMSON_PLANKS, Items.CRIMSON_STEM, Items.STRIPPED_CRIMSON_STEM, Items.STRIPPED_CRIMSON_HYPHAE, Items.CRIMSON_HYPHAE, Items.CRIMSON_SIGN, Items.CRIMSON_HANGING_SIGN, Items.CRIMSON_DOOR, Items.CRIMSON_BUTTON, Items.CRIMSON_STAIRS, Items.CRIMSON_SLAB, Items.CRIMSON_FENCE, Items.CRIMSON_FENCE_GATE, null, Items.CRIMSON_FUNGUS, null, Items.CRIMSON_PRESSURE_PLATE, Items.CRIMSON_TRAPDOOR);
            p(WoodType.DARK_OAK, "dark_oak", Items.DARK_OAK_PLANKS, Items.DARK_OAK_LOG, Items.STRIPPED_DARK_OAK_LOG, Items.STRIPPED_DARK_OAK_WOOD, Items.DARK_OAK_WOOD, Items.DARK_OAK_SIGN, Items.DARK_OAK_HANGING_SIGN, Items.DARK_OAK_DOOR, Items.DARK_OAK_BUTTON, Items.DARK_OAK_STAIRS, Items.DARK_OAK_SLAB, Items.DARK_OAK_FENCE, Items.DARK_OAK_FENCE_GATE, Items.DARK_OAK_BOAT, Items.DARK_OAK_SAPLING, Items.DARK_OAK_LEAVES, Items.DARK_OAK_PRESSURE_PLATE, Items.DARK_OAK_TRAPDOOR);
            p(WoodType.OAK, "oak", Items.OAK_PLANKS, Items.OAK_LOG, Items.STRIPPED_OAK_LOG, Items.STRIPPED_OAK_WOOD, Items.OAK_WOOD, Items.OAK_SIGN, Items.OAK_HANGING_SIGN, Items.OAK_DOOR, Items.OAK_BUTTON, Items.OAK_STAIRS, Items.OAK_SLAB, Items.OAK_FENCE, Items.OAK_FENCE_GATE, Items.OAK_BOAT, Items.OAK_SAPLING, Items.OAK_LEAVES, Items.OAK_PRESSURE_PLATE, Items.OAK_TRAPDOOR);
            p(WoodType.JUNGLE, "jungle", Items.JUNGLE_PLANKS, Items.JUNGLE_LOG, Items.STRIPPED_JUNGLE_LOG, Items.STRIPPED_JUNGLE_WOOD, Items.JUNGLE_WOOD, Items.JUNGLE_SIGN, Items.JUNGLE_HANGING_SIGN, Items.JUNGLE_DOOR, Items.JUNGLE_BUTTON, Items.JUNGLE_STAIRS, Items.JUNGLE_SLAB, Items.JUNGLE_FENCE, Items.JUNGLE_FENCE_GATE, Items.JUNGLE_BOAT, Items.JUNGLE_SAPLING, Items.JUNGLE_LEAVES, Items.JUNGLE_PRESSURE_PLATE, Items.JUNGLE_TRAPDOOR);
            p(WoodType.SPRUCE, "spruce", Items.SPRUCE_PLANKS, Items.SPRUCE_LOG, Items.STRIPPED_SPRUCE_LOG, Items.STRIPPED_SPRUCE_WOOD, Items.SPRUCE_WOOD, Items.SPRUCE_SIGN, Items.SPRUCE_HANGING_SIGN, Items.SPRUCE_DOOR, Items.SPRUCE_BUTTON, Items.SPRUCE_STAIRS, Items.SPRUCE_SLAB, Items.SPRUCE_FENCE, Items.SPRUCE_FENCE_GATE, Items.SPRUCE_BOAT, Items.SPRUCE_SAPLING, Items.SPRUCE_LEAVES, Items.SPRUCE_PRESSURE_PLATE, Items.SPRUCE_TRAPDOOR);
            p(WoodType.WARPED, "warped", Items.WARPED_PLANKS, Items.WARPED_STEM, Items.STRIPPED_WARPED_STEM, Items.STRIPPED_WARPED_HYPHAE, Items.WARPED_HYPHAE, Items.WARPED_SIGN, Items.WARPED_HANGING_SIGN, Items.WARPED_DOOR, Items.WARPED_BUTTON, Items.WARPED_STAIRS, Items.WARPED_SLAB, Items.WARPED_FENCE, Items.WARPED_FENCE_GATE, null, Items.WARPED_FUNGUS, null, Items.WARPED_PRESSURE_PLATE, Items.WARPED_TRAPDOOR);
        }

        void p(WoodType type, String prefix, Item planks, Item log, Item strippedLog, Item strippedWood, Item wood, Item sign, Item hangingSign, Item door, Item button, Item stairs, Item slab, Item fence, Item fenceGate, Item boat, Item sapling, Item leaves, Item pressurePlate, Item trapdoor) {
            put(type, new WoodItems(prefix, planks, log, strippedLog, strippedWood, wood, sign, hangingSign, door, button, stairs, slab, fence, fenceGate, boat, sapling, leaves, pressurePlate, trapdoor));
        }
    };
    public static final HashMap<Item, Item> cookableFoodMap = new HashMap<>() {
        {
            put(Items.PORKCHOP, Items.COOKED_PORKCHOP);
            put(Items.BEEF, Items.COOKED_BEEF);
            put(Items.CHICKEN, Items.COOKED_CHICKEN); // chicken is best meat, fight me
            put(Items.MUTTON, Items.COOKED_MUTTON);
            put(Items.RABBIT, Items.COOKED_RABBIT);
            put(Items.SALMON, Items.COOKED_SALMON);
            put(Items.COD, Items.COOKED_COD);
            put(Items.POTATO, Items.BAKED_POTATO);
        }
    };
    public static final Item[] RAW_FOODS = cookableFoodMap.keySet().toArray(Item[]::new);
    public static final Item[] COOKED_FOODS = cookableFoodMap.values().toArray(Item[]::new);
    private static Map<Item, Integer> fuelTimeMap = null;

    public static String stripItemName(Item item) {
        String[] possibilities = new String[]{"item.minecraft.", "block.minecraft."};
        for (String possible : possibilities) {
            if (item.getTranslationKey().startsWith(possible)) {
                return item.getTranslationKey().substring(possible.length());
            }
        }
        return item.getTranslationKey();
    }

    public static Item[] blocksToItems(Block[] blocks) {
        Item[] result = new Item[blocks.length];
        for (int i = 0; i < blocks.length; ++i) {
            result[i] = blocks[i].asItem();
        }
        return result;
    }

    /* Logs:
        ACACIA
        BIRCH
        CHERRY
        CRIMSON
        DARK_OAK
        OAK
        JUNGLE
        SPRUCE
        WARPED
     */

    /* Colors:
        WHITE
        BLACK
        BLUE
        BROWN
        CYAN
        GRAY
        GREEN
        LIGHT_BLUE
        LIGHT_GRAY
        LIME
        MAGENTA
        ORANGE
        PINK
        PURPLE
        RED
        YELLOW
     */

    public static Block[] itemsToBlocks(Item[] items) {
        ArrayList<Block> result = new ArrayList<>();
        for (Item item : items) {
            if (item instanceof BlockItem) {
                Block b = Block.getBlockFromItem(item);
                if (b != null && b != Blocks.AIR) {
                    result.add(b);
                }
            }
        }
        return result.toArray(Block[]::new);
    }

    public static Item logToPlanks(Item logItem) {
        return logToPlanks.getOrDefault(logItem, null);
    }

    public static Item planksToLog(Item plankItem) {
        return planksToLogs.getOrDefault(plankItem, null);
    }

    public static Item strippedToLogs(Item logItem) {
        return strippedToLogs.getOrDefault(logItem, null);
    }

    public static ColorfulItems getColorfulItems(MapColor color) {
        return colorMap.get(color);
    }

    public static ColorfulItems getColorfulItems(DyeColor color) {
        return getColorfulItems(color.getMapColor());
    }

    public static Collection<ColorfulItems> getColorfulItems() {
        return colorMap.values();
    }

    public static WoodItems getWoodItems(WoodType type) {
        return woodMap.get(type);
    }

    public static Collection<WoodItems> getWoodItems() {
        return woodMap.values();
    }

    public static Optional<Item> getCookedFood(Item rawFood) {
        return Optional.ofNullable(cookableFoodMap.getOrDefault(rawFood, null));
    }

    public static String trimItemName(String name) {
        if (name.startsWith("block.minecraft.")) {
            name = name.substring("block.minecraft.".length());
        } else if (name.startsWith("item.minecraft.")) {
            name = name.substring("item.minecraft.".length());
        }
        return name;
    }

    public static boolean areShearsEffective(Block b) {
        return
                //b.getRegistryEntry().streamTags().anyMatch(t -> t ==
                // BlockTags.LEAVES); should also work... but is slower
                b instanceof LeavesBlock
                        || b == Blocks.COBWEB
                        || b == Blocks.SHORT_GRASS
                        || b == Blocks.TALL_GRASS
                        || b == Blocks.LILY_PAD
                        || b == Blocks.FERN
                        || b == Blocks.DEAD_BUSH
                        || b == Blocks.VINE
                        || b == Blocks.TRIPWIRE
                        || BlockTagVer.isWool(b)
                        || b == Blocks.NETHER_SPROUTS;
    }

    private static boolean isStackProtected(AltoClef mod, ItemStack stack) {
        if (stack.hasEnchantments() && mod.getModSettings().getDontThrowAwayEnchantedItems())
            return true;
        if (ItemVer.hasCustomName(stack) && mod.getModSettings().getDontThrowAwayCustomNameItems())
            return true;
        return mod.getBehaviour().isProtected(stack.getItem()) || mod.getModSettings().isImportant(stack.getItem());
    }

    public static boolean canThrowAwayStack(AltoClef mod, ItemStack stack) {
        // Can't throw away empty stacks!
        if (stack.isEmpty())
            return false;
        if (isStackProtected(mod, stack))
            return false;
        return mod.getModSettings().isThrowaway(stack.getItem()) || mod.getModSettings().shouldThrowawayUnusedItems();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean canStackTogether(ItemStack from, ItemStack to) {
        if (to.isEmpty() && from.getCount() <= from.getMaxCount())
            return true;
        return to.getItem().equals(from.getItem()) && (from.getCount() + to.getCount() < to.getMaxCount());
    }

    private static Map<Item, Integer> getFuelTimeMap() {
        if (fuelTimeMap == null) {
            //#if MC >= 260000
            //$$ fuelTimeMap = new java.util.HashMap<>();
            //$$ java.util.Map<net.minecraft.world.item.Item, Integer> fixedFuel = new java.util.HashMap<>();
            //$$ fixedFuel.put(net.minecraft.world.item.Items.LAVA_BUCKET, 20000);
            //$$ fixedFuel.put(net.minecraft.world.item.Items.COAL_BLOCK, 16000);
            //$$ fixedFuel.put(net.minecraft.world.item.Items.DRIED_KELP_BLOCK, 4001);
            //$$ fixedFuel.put(net.minecraft.world.item.Items.BLAZE_ROD, 2400);
            //$$ fixedFuel.put(net.minecraft.world.item.Items.COAL, 1600);
            //$$ fixedFuel.put(net.minecraft.world.item.Items.CHARCOAL, 1600);
            //$$ fixedFuel.put(net.minecraft.world.item.Items.STICK, 100);
            //$$ fixedFuel.put(net.minecraft.world.item.Items.BOWL, 100);
            //$$ fixedFuel.put(net.minecraft.world.item.Items.BAMBOO, 50);
            //$$ for (net.minecraft.world.item.Item it : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            //$$     net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(it);
            //$$     if (fixedFuel.containsKey(it)) fuelTimeMap.put(it, fixedFuel.get(it));
            //$$     else if (st.is(net.minecraft.tags.ItemTags.LOGS_THAT_BURN) || st.is(net.minecraft.tags.ItemTags.PLANKS)) fuelTimeMap.put(it, 300);
            //$$     else if (st.is(net.minecraft.tags.ItemTags.WOODEN_SLABS)) fuelTimeMap.put(it, 150);
            //$$ }
            //#elseif MC >= 12102
            //$$ fuelTimeMap = new java.util.HashMap<>();
            //$$ net.minecraft.item.FuelRegistry fuels = net.minecraft.client.MinecraftClient.getInstance().world != null ? net.minecraft.client.MinecraftClient.getInstance().world.getFuelRegistry() : null;
            //$$ if (fuels != null) for (Item it : fuels.getFuelItems()) fuelTimeMap.put(it, fuels.getFuelTicks(new net.minecraft.item.ItemStack(it)));
            //#else
            fuelTimeMap = AbstractFurnaceBlockEntity.createFuelTimeMap();
            //#endif
        }
        return fuelTimeMap;
    }

    public static double getFuelAmount(Item... items) {
        double total = 0;
        for (Item item : items) {
            if (getFuelTimeMap().containsKey(item)) {
                int timeTicks = getFuelTimeMap().get(item);
                // 300 ticks of wood -> 1.5 operations
                // 200 ticks -> 1 operation
                total += (double) timeTicks / 200.0;
            }
        }
        return total;
    }

    public static double getFuelAmount(ItemStack stack) {
        return getFuelAmount(stack.getItem()) * stack.getCount();
    }

    public static boolean isFuel(Item item) {
        return getFuelTimeMap().containsKey(item);
    }

    public boolean isRawFood(Item item) {
        return cookableFoodMap.containsKey(item);
    }

    /** Replaces instanceof ToolItem (deleted in 1.21.11). */
    public static boolean isTool(net.minecraft.item.Item item) {
        //#if MC < 12102
        return item instanceof net.minecraft.item.ToolItem;
        //#else
        //$$ return item != null && item.getComponents().contains(net.minecraft.component.DataComponentTypes.TOOL);
        //#endif
    }

    /** Attack damage this item adds; works without SwordItem/MiningToolItem on 1.21.11. */
    public static float meleeDamageOf(net.minecraft.item.Item item) {
        //#if MC >= 12102
        //$$ return attributeSum(item, "attack_damage");
        //#else
        if (item instanceof net.minecraft.item.SwordItem sword) {
            return sword.getMaterial().getAttackDamage();
        }
        if (item instanceof net.minecraft.item.MiningToolItem tool) {
            return tool.getMaterial().getAttackDamage();
        }
        return 0;
        //#endif
    }

    public static float meleeDps(net.minecraft.item.Item item) {
        //#if MC >= 12102
        //$$ float damage = 1.0f + attributeSum(item, "attack_damage");
        //$$ float speed = 4.0f + attributeSum(item, "attack_speed");
        //$$ if (speed <= 0) return 0;
        //$$ return damage * speed;
        //#else
        float dmg = meleeDamageOf(item);
        if (dmg <= 0) return 0;
        // Prefer swords slightly via higher effective rate assumption when class exists.
        if (item instanceof net.minecraft.item.SwordItem) {
            return (1.0f + dmg) * 1.6f;
        }
        return (1.0f + dmg) * 1.0f;
        //#endif
    }

    public static net.minecraft.entity.EquipmentSlot getArmorSlot(net.minecraft.item.Item item) {
        if (item == null) {
            return null;
        }
        //#if MC >= 12102
        //$$ var equippable = item.getComponents().get(net.minecraft.component.DataComponentTypes.EQUIPPABLE);
        //$$ return equippable == null ? null : equippable.slot();
        //#else
        return item instanceof net.minecraft.item.ArmorItem armor ? armor.getSlotType() : null;
        //#endif
    }

    //#if MC >= 12005
    //#if MC >= 260000
    //$$ public static net.minecraft.tags.TagKey<net.minecraft.world.item.Item> toolFamily(net.minecraft.world.item.Item item) {
    //#else
    public static net.minecraft.registry.tag.TagKey<net.minecraft.item.Item> toolFamily(net.minecraft.item.Item item) {
    //#endif
        if (item == null) return null;
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(item);
        if (stackIn(stack, net.minecraft.registry.tag.ItemTags.PICKAXES)) return net.minecraft.registry.tag.ItemTags.PICKAXES;
        if (stackIn(stack, net.minecraft.registry.tag.ItemTags.AXES)) return net.minecraft.registry.tag.ItemTags.AXES;
        if (stackIn(stack, net.minecraft.registry.tag.ItemTags.SHOVELS)) return net.minecraft.registry.tag.ItemTags.SHOVELS;
        if (stackIn(stack, net.minecraft.registry.tag.ItemTags.HOES)) return net.minecraft.registry.tag.ItemTags.HOES;
        if (stackIn(stack, net.minecraft.registry.tag.ItemTags.SWORDS)) return net.minecraft.registry.tag.ItemTags.SWORDS;
        return null;
    }

    //#if MC >= 260000
    //$$ public static boolean stackIn(net.minecraft.world.item.ItemStack stack, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
    //$$     return stack.is(tag);
    //$$ }
    //#else
    public static boolean stackIn(net.minecraft.item.ItemStack stack, net.minecraft.registry.tag.TagKey<net.minecraft.item.Item> tag) {
        return stack.isIn(tag);
    }
    //#endif
    //#else
    //$$ public static Object toolFamily(net.minecraft.item.Item item) { return null; }
    //#endif

    public static double toolQuality(net.minecraft.item.Item item) {
        //#if MC >= 12102
        //$$ net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(item);
        //$$ if (stackIn(stack, net.minecraft.registry.tag.ItemTags.SWORDS)) {
        //$$     return meleeDps(item);
        //$$ }
        //$$ net.minecraft.block.BlockState reference;
        //$$ if (stackIn(stack, net.minecraft.registry.tag.ItemTags.PICKAXES)) {
        //$$     reference = net.minecraft.block.Blocks.STONE.getDefaultState();
        //$$ } else if (stackIn(stack, net.minecraft.registry.tag.ItemTags.AXES)) {
        //$$     reference = net.minecraft.block.Blocks.OAK_LOG.getDefaultState();
        //$$ } else if (stackIn(stack, net.minecraft.registry.tag.ItemTags.SHOVELS)) {
        //$$     reference = net.minecraft.block.Blocks.DIRT.getDefaultState();
        //$$ } else {
        //$$     reference = net.minecraft.block.Blocks.STONE.getDefaultState();
        //$$ }
        //$$ return stack.getMiningSpeedMultiplier(reference);
        //#else
        if (item instanceof net.minecraft.item.ToolItem tool) {
            return adris.altoclef.multiversion.ToolMaterialVer.getMiningLevel(tool);
        }
        return 0;
        //#endif
    }

    //#if MC >= 12102
    //$$ private static float attributeSum(net.minecraft.item.Item item, String path) {
    //$$     if (item == null) return 0;
    //$$     try {
    //$$         ItemStack stack = new ItemStack(item);
    //$$         var comp = stack.get(net.minecraft.component.DataComponentTypes.ATTRIBUTE_MODIFIERS);
    //$$         if (comp == null) return 0;
    //$$         float sum = 0;
    //$$         for (var entry : comp.modifiers()) {
    //$$             String id = entry.attribute().getKey().map(k -> k.getValue().getPath()).orElse("");
    //$$             if (path.equals(id)) sum += (float) entry.modifier().value();
    //$$         }
    //$$         return sum;
    //$$     } catch (Throwable t) {
    //$$         return 0;
    //$$     }
    //$$ }
    //#endif
    public static class ColorfulItems {
        public DyeColor color;
        public String colorName;
        public Item dye;
        public Item wool;
        public Item bed;
        public Item carpet;
        public Item stainedGlass;
        public Item stainedGlassPane;
        public Item terracotta;
        public Item glazedTerracotta;
        public Item concrete;
        public Item concretePowder;
        public Item banner;
        public Item shulker;
        public Block wallBanner;

        public ColorfulItems(DyeColor color, String colorName, Item dye, Item wool, Item bed, Item carpet, Item stainedGlass, Item stainedGlassPane, Item terracotta, Item glazedTerracotta, Item concrete, Item concretePowder, Item banner, Item shulker, Block wallBanner) {
            this.color = color;
            this.colorName = colorName;
            this.dye = dye;
            this.wool = wool;
            this.bed = bed;
            this.carpet = carpet;
            this.stainedGlass = stainedGlass;
            this.stainedGlassPane = stainedGlassPane;
            this.terracotta = terracotta;
            this.glazedTerracotta = glazedTerracotta;
            this.concrete = concrete;
            this.concretePowder = concretePowder;
            this.banner = banner;
            this.shulker = shulker;
            this.wallBanner = wallBanner;
        }
    }

    public static class WoodItems {
        public String prefix;
        public Item planks;
        public Item log;
        public Item strippedLog;
        public Item strippedWood;
        public Item wood;
        public Item sign;
        public Item hangingSign;
        public Item door;
        public Item button;
        public Item stairs;
        public Item slab;
        public Item fence;
        public Item fenceGate;
        public Item boat;
        public Item sapling;
        public Item leaves;
        public Item pressurePlate;
        public Item trapdoor;

        public WoodItems(String prefix, Item planks, Item log, Item strippedLog, Item strippedWood, Item wood, Item sign, Item hangingSign, Item door, Item button, Item stairs, Item slab, Item fence, Item fenceGate, Item boat, Item sapling, Item leaves, Item pressurePlate, Item trapdoor) {
            this.prefix = prefix;
            this.planks = planks;
            this.log = log;
            this.strippedLog = strippedLog;
            this.strippedWood = strippedWood;
            this.wood = wood;
            this.sign = sign;
            this.hangingSign = hangingSign;
            this.door = door;
            this.button = button;
            this.stairs = stairs;
            this.slab = slab;
            this.fence = fence;
            this.fenceGate = fenceGate;
            this.boat = boat;
            this.sapling = sapling;
            this.leaves = leaves;
            this.pressurePlate = pressurePlate;
            this.trapdoor = trapdoor;
        }

        public boolean isNetherWood() {
            return planks == Items.CRIMSON_PLANKS || planks == Items.WARPED_PLANKS;
        }
    }

}
