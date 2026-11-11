package adris.altoclef.swarm;

import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.Arg;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/**
 * Writes the installed {@code @} commands as catalog text for Ostinato's Phase 1 registry.
 * This class does not import Ostinato swarm types, so it still compiles against a stock Baritone jar.
 * Every {@code @} command is marked ambiguous: chain idle is not a verified success.
 */
public final class Phase1AtAdapter {

    private Phase1AtAdapter() {}

    public static String export(Collection<Command> commands) {
        if (commands == null || commands.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        Set<Command> seen = Collections.newSetFromMap(new IdentityHashMap<Command, Boolean>());
        for (Command command : commands) {
            if (command == null || !seen.add(command)) {
                continue;
            }
            write(out, command);
        }
        return out.toString();
    }

    private static void write(StringBuilder out, Command command) {
        if (command.getNames() == null || command.getNames().isEmpty()) {
            return;
        }
        String primary = command.getNames().get(0).toLowerCase(Locale.ROOT);
        out.append("---\n");
        out.append("v1\n");
        out.append("name=").append(primary).append('\n');
        for (int i = 1; i < command.getNames().size(); i++) {
            out.append("alias=").append(command.getNames().get(i).toLowerCase(Locale.ROOT)).append('\n');
        }
        out.append("help=").append(oneLine(command.getDescription())).append('\n');
        String usage = primary;
        try {
            usage = command.getHelpRepresentation(command.getNames().get(0)).trim();
        } catch (RuntimeException | adris.altoclef.commandsystem.exception.CommandException ignored) {
            usage = primary;
        }
        out.append("usage=").append(oneLine(usage)).append('\n');
        out.append("perm=").append(permission(primary)).append('\n');
        out.append("targets=bot,group,all\n");
        out.append("caps=").append(caps(primary)).append('\n');
        out.append("lease=false\n");
        out.append("mutate=").append(mutates(primary)).append('\n');
        out.append("ambiguous=true\n");
        out.append("origin=at\n");
        Arg<?>[] args = command.getArgs();
        if (args != null) {
            for (Arg<?> arg : args) {
                if (arg == null || arg.name == null) {
                    continue;
                }
                out.append("arg=").append(arg.name).append(':').append(arg.getTypeName()).append(':');
                out.append(arg.hasDefault ? "optional" : "required");
                if (arg.hasDefault && arg.defaultValue != null) {
                    out.append(':').append(oneLine(String.valueOf(arg.defaultValue)));
                }
                out.append('\n');
            }
        }
    }

    private static String permission(String name) {
        if ("stop".equals(name) || "pause".equals(name) || "disconnect".equals(name)
                || "cancel".equals(name) || "forcecancel".equals(name) || "set".equals(name)) {
            return "owner";
        }
        return "swarm";
    }

    private static String caps(String name) {
        if (is(name, "goto", "come", "follow", "path", "goal", "thisway", "explore", "surface", "axis", "home", "elytra", "portal")) {
            return "navigation";
        }
        if (is(name, "mine", "tunnel", "farm")) {
            return "mining";
        }
        if (is(name, "build", "sel", "litematica", "schematic", "place")) {
            return "building";
        }
        if (is(name, "pvp", "pve", "bastion")) {
            return "combat";
        }
        if (is(name, "pickup", "inventory", "stash", "deposit", "give")) {
            return "inventory";
        }
        if (is(name, "render", "freecam")) {
            return "liveRendering";
        }
        return "";
    }

    private static boolean mutates(String name) {
        String c = caps(name);
        return "mining".equals(c) || "building".equals(c) || "inventory".equals(c) || "place".equals(name);
    }

    private static boolean is(String name, String... options) {
        for (String option : options) {
            if (option.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String oneLine(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
