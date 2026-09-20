package adris.altoclef.util.helpers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class ShoreStandSelectorTest {

    @Test
    void picksNearestSolidShore() {
        // liquid at 0,64,0; solid shores at +X and -X; player near +X
        Set<String> solid = Set.of("1,63,0", "-1,63,0");
        Set<String> air = Set.of("1,64,0", "1,65,0", "-1,64,0", "-1,65,0", "0,64,1", "0,65,1", "0,64,-1", "0,65,-1");
        Optional<int[]> stand = ShoreStandSelector.select(
                0, 64, 0,
                2, 64, 0,
                s -> solid.contains(key(s[0], s[1] - 1, s[2])),
                s -> air.contains(key(s[0], s[1], s[2])),
                s -> air.contains(key(s[0], s[1] + 1, s[2])),
                s -> s[0] == 0 && s[1] == 64 && s[2] == 0
        );
        Assertions.assertTrue(stand.isPresent());
        Assertions.assertArrayEquals(new int[]{1, 64, 0}, stand.get());
    }

    @Test
    void skipsLiquidColumnAndNonSolid() {
        // only -Z has solid under + air; +X is liquid; +Z no solid
        Set<String> solid = new HashSet<>(Set.of("0,63,-1"));
        Set<String> air = Set.of("0,64,-1", "0,65,-1", "1,64,0", "1,65,0");
        Optional<int[]> stand = ShoreStandSelector.select(
                0, 64, 0,
                0, 64, 0,
                s -> solid.contains(key(s[0], s[1] - 1, s[2])),
                s -> air.contains(key(s[0], s[1], s[2])),
                s -> air.contains(key(s[0], s[1] + 1, s[2])),
                s -> s[0] == 1 && s[1] == 64 && s[2] == 0 // treat +X as liquid column
        );
        Assertions.assertTrue(stand.isPresent());
        Assertions.assertArrayEquals(new int[]{0, 64, -1}, stand.get());
    }

    @Test
    void emptyWhenNoShore() {
        Optional<int[]> stand = ShoreStandSelector.select(
                0, 64, 0,
                0, 64, 0,
                s -> false,
                s -> true,
                s -> true,
                s -> false
        );
        Assertions.assertTrue(stand.isEmpty());
    }

    @Test
    void escapeAndScoopPredicates() {
        Assertions.assertTrue(ShoreStandSelector.shouldEscapeBeforeInteract(true, false));
        Assertions.assertFalse(ShoreStandSelector.shouldEscapeBeforeInteract(true, true));
        Assertions.assertFalse(ShoreStandSelector.shouldEscapeBeforeInteract(false, false));
        Assertions.assertTrue(ShoreStandSelector.canScoopFromFooting(false, false));
        Assertions.assertTrue(ShoreStandSelector.canScoopFromFooting(true, true));
        Assertions.assertFalse(ShoreStandSelector.canScoopFromFooting(true, false));
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
