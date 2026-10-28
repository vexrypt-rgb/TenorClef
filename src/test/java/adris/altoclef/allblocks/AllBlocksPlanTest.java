package adris.altoclef.allblocks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class AllBlocksPlanTest {

    @Test
    void creativeAndUnobtainableItemsAreLeftOutWithAReason() {
        Assertions.assertNotNull(AllBlocksPlan.exclusion("zombie_spawn_egg"));
        Assertions.assertNotNull(AllBlocksPlan.exclusion("command_block"));
        Assertions.assertNotNull(AllBlocksPlan.exclusion("bedrock"));
        Assertions.assertNotNull(AllBlocksPlan.exclusion("infested_stone"));
        Assertions.assertNull(AllBlocksPlan.exclusion("dragon_egg"));
        Assertions.assertNull(AllBlocksPlan.exclusion("elytra"));
        Assertions.assertNull(AllBlocksPlan.exclusion("oak_planks"));
    }

    @Test
    void generatedCatalogueLinesAreWellFormed() throws Exception {
        Set<String> ordered = new HashSet<>();
        int craft = 0, gaps = 0;
        try (InputStream in = AllBlocksPlanTest.class.getResourceAsStream("/allblocks/catalogue-26.3.txt")) {
            Assertions.assertNotNull(in, "allblocks/catalogue-26.3.txt is on the classpath");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String l; (l = r.readLine()) != null; ) {
                if (l.startsWith("#") || l.isBlank()) continue;
                String[] p = l.split(" ");
                switch (p[0]) {
                    case "order" -> ordered.add(p[1]);
                    case "craft" -> {
                        int grid = Integer.parseInt(p[3]);
                        Assertions.assertEquals(4 + grid * grid, p.length, l);
                        Assertions.assertTrue(ordered.contains(p[1]), "craft output is a registry item: " + l);
                        craft++;
                    }
                    case "smelt" -> Assertions.assertEquals(3, p.length, l);
                    case "shear" -> Assertions.assertEquals(3, p.length, l);
                    case "mine" -> {
                        Assertions.assertEquals(5, p.length, l);
                        Assertions.assertTrue(Set.of("HAND", "WOOD", "STONE", "IRON", "DIAMOND").contains(p[3]), l);
                        Assertions.assertTrue(Set.of("OVERWORLD", "NETHER", "END").contains(p[4]), l);
                    }
                    case "kill" -> {
                        Assertions.assertEquals(4, p.length, l);
                        Assertions.assertTrue(Set.of("OVERWORLD", "NETHER", "END").contains(p[3]), l);
                    }
                    case "gap" -> {
                        Assertions.assertTrue(p.length >= 4, l);
                        Assertions.assertTrue(ordered.contains(p[1]), "gap item is a registry item: " + l);
                        gaps++;
                    }
                    default -> Assertions.fail("unknown line: " + l);
                }
            }
        }
        Assertions.assertTrue(ordered.size() > 1500, "every 26.3 item has an order line");
        Assertions.assertTrue(craft > 1000);
        Assertions.assertEquals(ordered.size(), gaps, "every item has a gap line (its root cause when unsupported)");
    }
}
