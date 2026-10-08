package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.swarm.Assignment;
import adris.altoclef.swarm.Capability;
import adris.altoclef.swarm.Objective;
import adris.altoclef.swarm.SwarmCoordinator;
import adris.altoclef.swarm.SwarmLedger;
import adris.altoclef.swarm.SwarmRuntime;

import java.util.EnumSet;
import java.util.Set;

/**
 * Swarm control. Leader: {@code @swarm lead}, {@code @swarm acquire <item> [n]}, {@code @swarm status},
 * {@code @swarm why <id>}, {@code @swarm cancel <id>}. Worker: {@code @swarm join <leader> [CAN_MINE,...]},
 * {@code @swarm leave}. Both sides must list each other with {@code @fleet}.
 */
public class SwarmCommand extends Command {
    public SwarmCommand() {
        super("swarm", "Swarm coordination: lead | join <leader> [caps] | leave | acquire <item> [n] | status | why <id> | cancel <id>",
                new StringArg("action", "status"),
                new StringArg("arg1", ""),
                new StringArg("arg2", ""));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String action = parser.get(String.class).toLowerCase();
        String a1 = parser.get(String.class);
        String a2 = parser.get(String.class);
        SwarmCoordinator leader = SwarmRuntime.leader();
        switch (action) {
            case "lead" -> {
                SwarmRuntime.lead(mod);
                mod.log("Swarm: leading. Workers: @swarm join " + mod.getPlayer().getName().getString());
            }
            case "join" -> {
                if (a1.isBlank()) {
                    mod.log("Usage: @swarm join <leader> [CAN_MINE,CAN_FIGHT,...]");
                } else {
                    Set<Capability> caps = a2.isBlank() ? EnumSet.allOf(Capability.class) : Capability.decode(a2);
                    SwarmRuntime.join(mod, a1, caps);
                    mod.log("Swarm: joined " + a1 + " with " + caps);
                }
            }
            case "local" -> {
                // Live-test aid: -Dtenorclef.swarm.tp="x y z" moves the player to open ground first.
                String tp = System.getProperty("tenorclef.swarm.tp");
                if (tp != null) adris.altoclef.multiversion.entity.PlayerVer.sendChatCommand(mod.getPlayer(), "tp @s " + tp);
                SwarmCoordinator l = SwarmRuntime.local(mod);
                int n = a2.isBlank() ? 1 : Integer.parseInt(a2.trim());
                Assignment a = l.submit(l.create(Objective.acquire(a1.isBlank() ? "oak_log" : a1, n), 5,
                        EnumSet.noneOf(Capability.class)), System.currentTimeMillis());
                mod.log("Swarm local: " + a);
            }
            case "dlead" -> {
                // Two-client live test, leader side: open the world to LAN on a fixed port, trust the worker, lead, submit.
                String me = mod.getPlayer().getName().getString();
                adris.altoclef.tasks.speedrun.testrun2.fleet.Fleet.add(me);
                adris.altoclef.tasks.speedrun.testrun2.fleet.Fleet.add(a1);
                adris.altoclef.multiversion.entity.PlayerVer.sendChatCommand(mod.getPlayer(), "publish true survival 25599");
                // Test accounts have no Mojang session: the integrated server must not demand authentication.
                try {
                    net.minecraft.server.MinecraftServer srv = net.minecraft.client.MinecraftClient.getInstance().getServer();
                    if (srv != null) srv.setOnlineMode(false);
                } catch (Throwable t) {
                    mod.log("Swarm duo: could not switch the server to offline mode: " + t);
                }
                SwarmCoordinator l = SwarmRuntime.lead(mod);
                int n = a2.isBlank() ? 1 : Integer.parseInt(a2.trim());
                mod.log("Swarm duo leader " + me + ": " + l.submit(l.create(Objective.acquire("oak_log", n), 5,
                        EnumSet.noneOf(Capability.class)), System.currentTimeMillis()));
            }
            case "djoin" -> {
                String me = mod.getPlayer().getName().getString();
                adris.altoclef.tasks.speedrun.testrun2.fleet.Fleet.add(me);
                adris.altoclef.tasks.speedrun.testrun2.fleet.Fleet.add(a1);
                String tp = System.getProperty("tenorclef.swarm.tp");
                if (tp != null) adris.altoclef.multiversion.entity.PlayerVer.sendChatCommand(mod.getPlayer(), "tp @s " + tp);
                SwarmRuntime.join(mod, a1, EnumSet.allOf(Capability.class));
                mod.log("Swarm duo worker " + me + " -> leader " + a1);
            }
            case "leave" -> {
                SwarmRuntime.leave();
                mod.log("Swarm: left.");
            }
            case "acquire" -> {
                if (leader == null) {
                    mod.log("Swarm: not leading. @swarm lead first.");
                } else if (a1.isBlank()) {
                    mod.log("Usage: @swarm acquire <item> [count]");
                } else {
                    int n = 1;
                    try {
                        if (!a2.isBlank()) n = Integer.parseInt(a2.trim());
                    } catch (NumberFormatException ignored) {
                        // keep 1
                    }
                    Assignment a = leader.submit(leader.create(Objective.acquire(a1, n), 5, EnumSet.noneOf(Capability.class)),
                            System.currentTimeMillis());
                    mod.log("Swarm: " + a);
                }
            }
            case "cancel" -> mod.log(leader != null && leader.cancel(a1, System.currentTimeMillis()) ? "Swarm: cancelled " + a1 : "Swarm: nothing to cancel");
            case "why" -> {
                if (leader == null) mod.log("Swarm: not leading.");
                else for (String line : leader.ledger().why(a1).split("\n")) if (!line.isBlank()) mod.log(line);
            }
            default -> {
                if (leader == null) mod.log("Swarm: not leading" + (SwarmRuntime.worker() != null
                        ? "; worker, holding " + SwarmRuntime.worker().currentAssignment() : "."));
                else {
                    for (String line : leader.status()) mod.log(line);
                    mod.log("events: " + leader.ledger().all().size() + ", failures: " + leader.ledger().count(SwarmLedger.Type.TASK_FAILED));
                }
            }
        }
        finish();
    }
}
