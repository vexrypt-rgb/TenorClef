package adris.altoclef.compose;

import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.Task;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;

/** Small helpers for Composition authors. Each returns a plain {@link Task}. */
public final class Kit {
    private Kit() {}

    /** Runs the tasks one after another; fails as soon as one fails. */
    public static Task seq(String label, Task... tasks) {
        return new SequenceTask(label, Arrays.asList(tasks));
    }

    /** Succeeds after the given wall-clock duration. */
    public static Task waitMs(long ms) {
        return new TimedTask("wait " + ms + "ms", ms, null);
    }

    /** Never finishes on its own (stays RUNNING until stopped). */
    public static Task forever(String label) {
        return new TimedTask(label, -1, null);
    }

    /** Fails (non-recoverable) after one tick. */
    public static Task failWith(FailureReason reason, String message) {
        return new TimedTask("fail " + reason, 0, new adris.altoclef.tasksystem.TaskFailure(reason, message, false));
    }

    /** Travels to a block through the normal movement path. */
    public static Task goTo(int x, int y, int z) {
        return new GetToBlockTask(new BlockPos(x, y, z));
    }

    static List<Task> list(Task... t) {
        return Arrays.asList(t);
    }
}
