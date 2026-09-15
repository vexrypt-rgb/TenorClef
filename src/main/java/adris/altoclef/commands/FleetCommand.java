package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.fleet.Fleet;
import adris.altoclef.tasks.speedrun.testrun2.fleet.FleetProtocol;

import java.util.List;

/**
 * {@code @fleet add Steve}
 * {@code @fleet ping}
 * {@code @fleet do oak_log 32}  — ask members to get that item
 */
public class FleetCommand extends Command {

    public FleetCommand() {
        super(List.of("fleet", "link"), "Link TenorClef butlers",
                new StringArg("args", "list"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String raw = "list";
        try {
            String a = parser.get(String.class);
            if (a != null && !a.isBlank()) raw = a;
        } catch (Throwable ignored) {}
        String[] p = raw.trim().split("\\s+");
        String mode = p[0].toLowerCase();
        switch (mode) {
            case "add" -> {
                if (p.length < 2) {
                    Debug.logWarning("FLEET @fleet add <IGN>");
                } else {
                    Fleet.add(p[1]);
                    Debug.logMessage("FLEET + " + p[1] + " now " + Fleet.members());
                }
            }
            case "remove", "rm" -> {
                if (p.length >= 2) {
                    Fleet.remove(p[1]);
                    Debug.logMessage("FLEET - " + p[1]);
                }
            }
            case "ping" -> {
                FleetProtocol.broadcast(mod, "~ ping");
                Debug.logMessage("FLEET ping " + Fleet.members());
            }
            case "do", "get", "share" -> {
                if (p.length < 2) {
                    Debug.logWarning("FLEET @fleet do <item> [count]");
                    finish();
                    return;
                }
                String item = p[1];
                int n = p.length >= 3 ? parse(p[2]) : 16;
                List<String> mates = Fleet.members();
                if (mates.isEmpty()) {
                    Debug.logWarning("FLEET empty — @fleet add <IGN> on both bots");
                    finish();
                    return;
                }
                int share = Math.max(1, n / Math.max(1, mates.size()));
                String me = "";
                try { me = mod.getPlayer().getName().getString(); } catch (Throwable ignored) {}
                int sent = 0;
                for (String name : mates) {
                    if (name.equalsIgnoreCase(me)) continue;
                    FleetProtocol.whisper(mod, name, "~ do get " + item + " " + share);
                    sent++;
                }
                Debug.logMessage("FLEET asked " + sent + " bots for " + item + " x" + share + " each");
            }
            default -> Debug.logMessage("FLEET " + Fleet.members()
                    + "  @fleet add <IGN> | ping | do <item> [count]");
        }
        finish();
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return 16;
        }
    }
}
