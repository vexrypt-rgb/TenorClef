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
 * {@code @swarm why <id>}, {@code @swarm cancel <id>}. Worker: {@code @swarm join [CAN_MINE,...]},
 * {@code @swarm leave}. Who leads and who may join comes from the Ostinato roster (swarm.txt).
 */
public class SwarmCommand extends Command {
    public SwarmCommand() {
        super("swarm", "Swarm coordination over the Ostinato link: lead | join [caps] | leave | acquire <item> [n] | status | why <id> | cancel <id>",
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
            case "script" -> {
                // Test hook: follow a file and run each line as it appears ("wait N", "#baritone cmd", "@altoclef cmd", "// note") on a timer.
                java.nio.file.Path file = java.nio.file.Path.of(a1);
                Thread t = new Thread(() -> {
                    int done = 0;
                    while (true) {
                    java.util.List<String> lines;
                    try {
                        lines = java.nio.file.Files.exists(file) ? java.nio.file.Files.readAllLines(file) : java.util.List.of();
                    } catch (java.io.IOException e) {
                        lines = java.util.List.of();
                    }
                    if (lines.size() <= done) {
                        try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                        continue;
                    }
                    java.util.List<String> fresh = lines.subList(done, lines.size());
                    done = lines.size();
                    for (String raw : new java.util.ArrayList<>(fresh)) {
                        String line = raw.trim();
                        if (line.isEmpty() || line.startsWith("//")) continue;
                        try {
                            if (line.startsWith("wait ")) {
                                Thread.sleep(Long.parseLong(line.substring(5).trim()) * 1000L);
                            } else {
                                net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                                    try {
                                        adris.altoclef.Debug.logMessage("SCRIPT> " + line);
                                        if (line.startsWith("/")) adris.altoclef.multiversion.entity.PlayerVer.sendChatCommand(mod.getPlayer(), line.substring(1));
                                        else if (line.startsWith("#")) mod.getClientBaritone().getCommandManager().execute(line.substring(1));
                                        else mod.getCommandExecutor().executeWithPrefix(line);
                                    } catch (Throwable e) {
                                        adris.altoclef.Debug.logWarning("SCRIPT failed: " + line + ": " + e);
                                    }
                                });
                            }
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    }
                }, "swarm-script");
                t.setDaemon(true);
                t.start();
            }
            case "host" -> {
                // Test hook: open this singleplayer world to LAN in offline mode so other clients can join.
                adris.altoclef.multiversion.entity.PlayerVer.sendChatCommand(mod.getPlayer(), "publish true survival 25599");
                try {
                    net.minecraft.server.MinecraftServer srv = net.minecraft.client.MinecraftClient.getInstance().getServer();
                    if (srv != null) srv.setOnlineMode(false);
                } catch (Throwable t) {
                    mod.log("Swarm host: could not switch the server to offline mode: " + t);
                }
                mod.log("Swarm: hosted on port 25599");
            }
            case "lead" -> {
                try {
                    SwarmRuntime.lead(mod);
                    mod.log("Swarm: leading. Members of your roster group join with @swarm join.");
                } catch (IllegalStateException e) {
                    mod.log("Swarm: " + e.getMessage());
                }
            }
            case "join" -> {
                try {
                    Set<Capability> caps = a1.isBlank() ? EnumSet.allOf(Capability.class) : Capability.decode(a1);
                    SwarmRuntime.join(mod, caps);
                    mod.log("Swarm: joined " + SwarmRuntime.link().leader() + " with " + caps);
                } catch (IllegalStateException e) {
                    mod.log("Swarm: " + e.getMessage());
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
            case "cancel" -> {
                // No id means "stop whatever is running", so a player never has to look up a task id.
                java.util.List<String> ids = new java.util.ArrayList<>();
                if (leader != null && !a1.isBlank()) ids.add(a1);
                else if (leader != null) for (Assignment t : leader.assignments()) if (!t.state().isTerminal()) ids.add(t.id);
                int n = 0;
                for (String id : ids) if (leader.cancel(id, System.currentTimeMillis())) n++;
                mod.log(n == 0 ? "Swarm: nothing to cancel" : "Swarm: cancelled " + n + (n == 1 ? " job" : " jobs"));
            }
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
