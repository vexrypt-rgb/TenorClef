package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.DoubleArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.util.WarpClock;

//#if MC >= 12003
import net.minecraft.client.MinecraftClient;
//#endif

/**
 * @warp 5  -> world and bot run 5x faster (singleplayer sim only).
 * @warp 1  -> back to normal.
 */
public class WarpCommand extends Command {

    public WarpCommand() throws CommandException {
        super("warp", "Sim time warp: run the singleplayer world and bot N times faster (1 = off, max 20)",
                new DoubleArg("factor", 1.0)
        );
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        WarpClock.set(parser.get(Double.class).floatValue());
        float f = WarpClock.get();
        //#if MC >= 12003
        // Vanilla drives both server and client from ServerTickManager; needs cheats on.
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.networkHandler.sendChatCommand("tick rate " + (20f * f));
        }
        //#endif
        Debug.logMessage("WARP x" + f + (f == 1f ? " (off)"
                : "  (singleplayer only; client caps at 10 ticks/frame, so keep fps >= " + (int) Math.ceil(f * 2) + ")"));
        finish();
    }
}
