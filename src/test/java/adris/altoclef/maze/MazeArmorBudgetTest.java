package adris.altoclef.maze;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the armour allowance that the furnish task spends before its first placement.
 *
 * <p>Ostinato: 2026-10-30. The invariant being protected is the one that was broken in every recorded run: the budget
 * was a per-room field, so each room re-spent the same 3600 ticks on the same piece and gave up again
 * ({@code skip-armor} in 3/3 rooms of run101, 3/3 of run102, 3/6 of run105).
 */
public class MazeArmorBudgetTest {

    @Test
    void oneAllowanceCoversTheWholeSet() {
        MazeArmorBudget budget = new MazeArmorBudget();
        String chestplate = "copper_chestplate";
        for (int i = 0; i < MazeArmorBudget.TICKS_PER_SET; i++) {
            Assertions.assertTrue(budget.spend(chestplate), "tick " + (i + 1) + " is inside the allowance");
        }
        Assertions.assertEquals(MazeArmorBudget.TICKS_PER_SET, budget.spent());
        Assertions.assertTrue(budget.mayAttempt(chestplate), "the last allowed tick does not give the piece up");

        Assertions.assertFalse(budget.spend(chestplate), "the first tick past the allowance gives up");
        Assertions.assertEquals(MazeArmorBudget.TICKS_PER_SET, budget.spent(), "the exhausted tick is not charged");
        Assertions.assertFalse(budget.mayAttempt(chestplate), "a given-up piece is never attempted again");
    }

    @Test
    void aGivenUpPieceStaysGivenUpAcrossRooms() {
        // The same instance is what a second room sees: that is the fix.
        MazeArmorBudget budget = new MazeArmorBudget();
        String chestplate = "copper_chestplate";
        while (budget.spend(chestplate)) {
            // spend the allowance
        }
        Assertions.assertFalse(budget.mayAttempt(chestplate));

        // Room 2 asks again; it must not be handed a fresh allowance.
        Assertions.assertFalse(budget.mayAttempt(chestplate));
        Assertions.assertFalse(budget.spend(chestplate));
        Assertions.assertEquals(MazeArmorBudget.TICKS_PER_SET, budget.spent(), "no ticks are charged to a given-up piece");
    }

    @Test
    void theAllowanceIsNotPerPiece() {
        // Spending on one piece leaves nothing for the next, so armour cannot run 4 x 3 min before the first light.
        MazeArmorBudget budget = new MazeArmorBudget();
        for (int i = 0; i < MazeArmorBudget.TICKS_PER_SET; i++) {
            Assertions.assertTrue(budget.spend("copper_helmet"));
        }
        Assertions.assertFalse(budget.spend("copper_leggings"), "the second piece shares the first piece's allowance");
        Assertions.assertFalse(budget.mayAttempt("copper_leggings"));
    }

    @Test
    void aNullPieceIsNeverAttempted() {
        MazeArmorBudget budget = new MazeArmorBudget();
        Assertions.assertFalse(budget.mayAttempt(null));
        Assertions.assertFalse(budget.spend(null));
        Assertions.assertEquals(0, budget.spent());
        Assertions.assertTrue(budget.gaveUp().isEmpty());
    }
}
