package adris.altoclef.swarm;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Catalog text only. No Minecraft client and no Ostinato swarm types. */
class Phase1AtAdapterTest {

    @Test
    void exportsGotoAsAmbiguousSwarmNavigation() throws Exception {
        Command gotoCommand = new Command(List.of("goto"), "Go to a place", new StringArg("target")) {
            @Override
            protected void call(AltoClef mod, ArgParser parser) throws CommandException {
            }
        };
        String text = Phase1AtAdapter.export(List.of(gotoCommand));
        assertTrue(text.contains("---\n"));
        assertTrue(text.contains("v1\n"));
        assertTrue(text.contains("name=goto\n"));
        assertTrue(text.contains("help=Go to a place\n"));
        assertTrue(text.contains("usage=" + gotoCommand.getHelpRepresentation("goto").trim() + "\n"));
        assertTrue(text.contains("perm=swarm\n"));
        assertTrue(text.contains("caps=navigation\n"));
        assertTrue(text.contains("lease=false\n"));
        assertTrue(text.contains("mutate=false\n"));
        assertTrue(text.contains("ambiguous=true\n"));
        assertTrue(text.contains("origin=at\n"));
        assertTrue(text.contains("arg=target:String:required\n"));
        assertFalse(text.contains("ambiguous=false"));
    }

    @Test
    void exportsStopAsOwner() {
        Command stop = new Command(List.of("stop"), "Stop", new StringArg("id", "")) {
            @Override
            protected void call(AltoClef mod, ArgParser parser) {
            }
        };
        String text = Phase1AtAdapter.export(List.of(stop));
        assertTrue(text.contains("name=stop\n"));
        assertTrue(text.contains("perm=owner\n"));
        assertTrue(text.contains("ambiguous=true\n"));
        assertTrue(text.contains("arg=id:String:optional"));
    }
}
