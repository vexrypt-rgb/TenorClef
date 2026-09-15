package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.GoToTargetArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.commandsystem.GotoTarget;
import adris.altoclef.movement.TungstenMovement;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.GetToXZTask;
import adris.altoclef.tasks.movement.GetToYTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.util.math.BlockPos;

/**
 * Explicit Tungsten-preferred travel command (@tgoto).
 * Falls back to Baritone with a clear message when Tungsten is not on the classpath.
 * Real ;goto lives in the Tungsten mod itself once vendored.
 */
public class TungstenGotoCommand extends Command {

    public TungstenGotoCommand() {
        super("tgoto", "Travel via Tungsten physics A* when available, else Baritone",
                new GoToTargetArg("[x y z dimension]/[x z dimension]/[y dimension]/[dimension]/[x y z]/[x z]/[y]")
        );
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        GotoTarget target = parser.get(GotoTarget.class);
        if (!TungstenMovement.isAvailable()) {
            TungstenMovement.logMissingBackend(mod);
        }
        mod.runUserTask(movementTask(target), this::finish);
    }

    private static Task movementTask(GotoTarget target) {
        return switch (target.getType()) {
            case XYZ -> TungstenMovement.gotoBlock(new BlockPos(target.getX(), target.getY(), target.getZ()));
            case XZ -> new GetToXZTask(target.getX(), target.getZ(), target.getDimension());
            case Y -> new GetToYTask(target.getY(), target.getDimension());
            case NONE -> GotoCommand.getMovementTaskFor(target);
        };
    }
}
