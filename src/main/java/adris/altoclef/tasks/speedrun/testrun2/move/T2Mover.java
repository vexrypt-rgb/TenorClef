package adris.altoclef.tasks.speedrun.testrun2.move;

import net.minecraft.util.math.BlockPos;

/**
 * Thin movement API so AltoClef tasks do not talk to Baritone directly.
 * v1: walk, swim-out, cancel, busy. No farm/explore/elytra.
 */
public interface T2Mover {

    Result walkTo(BlockPos pos);

    Result swimOut();

    Result mineToward(BlockPos pos);

    void cancel();

    boolean busy();

    enum Status { OK, BUSY, FAIL, UNSUPPORTED }

    final class Result {
        public final Status status;
        public final String reason;

        public Result(Status status, String reason) {
            this.status = status;
            this.reason = reason == null ? "" : reason;
        }

        public static Result ok() {
            return new Result(Status.OK, "");
        }

        public static Result busy(String why) {
            return new Result(Status.BUSY, why);
        }

        public static Result fail(String why) {
            return new Result(Status.FAIL, why);
        }
    }
}
