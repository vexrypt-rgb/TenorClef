package adris.altoclef.maze;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * TenorClef: how long the furnish task may spend building the optional survival kit's armour, and which piece it has
 * already given up on.
 *
 * <p>No Minecraft or Ostinato types, so it is unit tested directly (see {@code MazeArmorBudgetTest}).
 *
 * <p>Ostinato: 2026-10-30. The allowance is one budget for the WHOLE set, not one per piece. A per-piece allowance
 * would let armour run for 4 x 3 min before the first light is placed, and the first light is what the harness verify
 * counts. What the code used to do was neither: it kept a single counter but recorded the give-up in a per-room field,
 * so every room re-spent the same 3600 ticks on the same piece.
 *
 * <p>Measured on run105 (a room that passed): kit up to armour 1430 ticks, armour 3600, first gather 3130, first light
 * at t=7900, light=133/133 at t≈12900. So the armour phase was 28% of the room's furnish time and returned one helmet.
 * {@code skip-armor} fired in 3/3 rooms of run101, 3/3 of run102 and 3/6 of run105 — never once completing the set.
 *
 * <p>That is not a location problem: a copper piece is 7-8 ingots, so mining plus smelting does not fit in 3 minutes
 * wherever it is tried. Once a piece has exhausted the allowance it is recorded here and never attempted again in this
 * client session, which is what stops room 2..N from repeating the loss. Because {@code nextKitPiece()} always returns
 * the lowest missing piece, recording the failed piece also ends the phase — but a piece that is later obtained by
 * other means still lets the next one be tried.
 */
final class MazeArmorBudget {

    /** One 3-minute allowance for the whole armour set. */
    static final int TICKS_PER_SET = 3600;

    private final Set<String> gaveUp = new HashSet<>();
    private int spent;

    /** Ticks spent on armour so far this session. */
    int spent() {
        return spent;
    }

    /** The pieces this budget has given up on, in no particular order. */
    Set<String> gaveUp() {
        return Collections.unmodifiableSet(gaveUp);
    }

    /** Whether {@code pieceId} is still worth working on: false once it has exhausted the allowance. */
    boolean mayAttempt(String pieceId) {
        return pieceId != null && !gaveUp.contains(pieceId);
    }

    /**
     * Spends one tick of the allowance on {@code pieceId}.
     *
     * @return false when the allowance is exhausted, in which case {@code pieceId} is recorded as given up and
     *         {@link #mayAttempt} answers false for it from then on.
     */
    boolean spend(String pieceId) {
        if (!mayAttempt(pieceId)) return false;
        if (spent >= TICKS_PER_SET) {
            gaveUp.add(pieceId);
            return false;
        }
        spent++;
        return true;
    }
}
