package adris.altoclef.maze;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Ostinato: 2026-11-02. The survival palette Claude's backrooms themes have to stay inside. */
public class MazePaletteTest {

    @Test
    void quartzOnlyBecomesCraftableWhite() {
        Assertions.assertEquals(MazePalette.WHITE_TILES, MazePalette.survivalIds(List.of("smooth_quartz", "quartz_block")));
    }

    @Test
    void whiteConcreteAndPrismarineAreReplaced() {
        Assertions.assertEquals(
                List.of("birch_planks", "polished_diorite", "diorite", "calcite"),
                MazePalette.survivalIds(List.of("white_concrete", "birch_planks", "prismarine_bricks", "sea_lantern")));
    }

    @Test
    void calciteFollowsCraftableDiorite() {
        Assertions.assertEquals(
                List.of("polished_diorite", "calcite"),
                MazePalette.survivalIds(List.of("calcite", "polished_diorite")));
        Assertions.assertEquals(List.of("calcite"), MazePalette.survivalIds(List.of("calcite")));
    }

    @Test
    void anOrdinaryPaletteIsLeftAlone() {
        List<String> office = List.of("yellow_terracotta", "birch_planks", "sandstone");
        Assertions.assertEquals(office, MazePalette.survivalIds(office));
    }

    @Test
    void featuresGatherTheItemThatPlacesThem() {
        Assertions.assertEquals("water_bucket", MazePalette.gatherId("water"));
        Assertions.assertEquals("wheat_seeds", MazePalette.gatherId("wheat"));
        Assertions.assertEquals("dirt", MazePalette.gatherId("farmland"));
        Assertions.assertEquals("stripped_oak_log", MazePalette.gatherId("stripped_oak_log"));
        Assertions.assertEquals("iron_bars", MazePalette.gatherId("iron_bars"));
        Assertions.assertEquals(1, MazePalette.gatherCount("water", 12));
        Assertions.assertEquals(4, MazePalette.gatherCount("wheat", 4));
        Assertions.assertTrue(MazePalette.feature("sugar_cane"));
        Assertions.assertFalse(MazePalette.feature("barrel"));
        Assertions.assertTrue(MazePalette.rejects("smooth_quartz"));
        Assertions.assertFalse(MazePalette.rejects("polished_diorite"));
    }

    @Test
    void supportedListContainsThePaletteTheHandoffNames() {
        for (String id : List.of(
                "polished_diorite", "diorite", "calcite", "water", "iron_bars", "chain", "copper_block",
                "stripped_oak_log", "barrel", "sugar_cane", "wheat", "farmland", "torch")) {
            Assertions.assertTrue(MazePalette.SUPPORTED.contains(id), id);
        }
        for (String banned : List.of("quartz_block", "smooth_quartz", "white_concrete", "prismarine", "sea_lantern")) {
            Assertions.assertFalse(MazePalette.SUPPORTED.contains(banned), banned);
        }
    }
}
