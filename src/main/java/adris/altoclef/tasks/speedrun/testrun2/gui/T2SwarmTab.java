package adris.altoclef.tasks.speedrun.testrun2.gui;

import adris.altoclef.swarm.AgentInfo;
import adris.altoclef.swarm.Assignment;
import adris.altoclef.swarm.SwarmCoordinator;
import adris.altoclef.swarm.SwarmRuntime;
import baritone.api.BaritoneAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * Swarm tab of {@link T2MenuScreen}: the same actions as {@code @swarm}, in plain words.
 * The tab only reads {@link SwarmRuntime} state and runs ordinary commands, so it cannot get out of step with them.
 */
final class T2SwarmTab {
    private T2SwarmTab() {}

    static final int ID = 8;

    private static String item = "oak_log";
    private static String count = "1";
    private static String schematic = "";
    private static volatile String status = "Pick a role, then give the swarm a job.";

    static String status() { return status; }
    static String[] fieldLabels() { return new String[]{"item", "count", "schematic"}; }

    /** One plain-language line per fact; the first line says what to do next. */
    static List<String> lines() {
        List<String> out = new ArrayList<>();
        String problem = SwarmRuntime.problem();
        SwarmCoordinator lead = SwarmRuntime.leader();
        if (problem != null) {
            out.add("Not ready: " + problem);
            return out;
        }
        String self = SwarmRuntime.link().self();
        String boss = SwarmRuntime.link().leader();
        out.add("You are " + self + ". Your group leader is " + (boss == null ? "nobody" : boss.equals(self) ? "you" : boss) + ".");
        if (lead != null) {
            List<AgentInfo> agents = lead.agents();
            out.add(agents.isEmpty() ? "Leading. No helpers have joined yet: ask them to press 'Join the leader'."
                    : "Leading " + agents.size() + (agents.size() == 1 ? " helper:" : " helpers:"));
            for (AgentInfo a : agents) {
                String job = a.assignmentId() == null ? "" : "  doing " + a.assignmentId();
                out.add("  " + a.id + "  " + friendly(a) + job);
            }
            List<Assignment> jobs = lead.assignments();
            int from = Math.max(0, jobs.size() - 4);
            for (int i = from; i < jobs.size(); i++) {
                Assignment j = jobs.get(i);
                String line = "Job " + j.id + ": " + j.objective.describe() + " - " + j.state().name().toLowerCase();
                if (j.agentId() != null) line += " (" + j.agentId() + ")";
                if (j.failure() != null) line += " - " + j.failure();
                out.add(line);
            }
        } else if (SwarmRuntime.worker() != null) {
            String cur = SwarmRuntime.worker().currentAssignment();
            out.add("Helping " + boss + ". " + (cur == null ? "Waiting for a job." : "Working on " + cur + "."));
        } else if (SwarmRuntime.link().leads()) {
            out.add("You can lead. Press 'Lead the swarm' to start taking helpers.");
        } else {
            out.add("Press 'Join the leader' to help " + (boss == null ? "your leader" : boss) + ".");
        }
        return out;
    }

    private static String friendly(AgentInfo a) {
        return switch (a.lifecycle()) {
            case READY -> "ready";
            case BUSY -> "busy";
            case DEAD -> "dead, waiting to respawn";
            case DISCONNECTED -> "offline";
            default -> "connecting";
        };
    }

    static void layout(T2MenuScreen s) {
        int fy = s.footerT - 62;
        int fx = s.contentX + 62;
        int fw = s.contentW - 62;
        s.swItem = T2MenuActions.textField(s, fx, fy, fw / 2 - 4, 14, item);
        s.swCount = T2MenuActions.textField(s, fx + fw / 2 + 36, fy, fw / 2 - 36, 14, count);
        s.swSchem = T2MenuActions.textField(s, fx, fy + 16, fw, 14, schematic);

        int bw = (s.contentW - 8) / 3;
        String[][] rows = {
                {"Lead the swarm", "SWM:lead", "Join the leader", "SWM:join", "Leave", "SWM:leave"},
                {"Gather it", "SWM:acquire", "Build it", "SWM:build", "Stop everything", "SWM:stop"},
        };
        int y = fy + 34;
        for (String[] r : rows) {
            for (int c = 0; c * 2 + 1 < r.length; c++) {
                T2MenuActions.button(s, s.contentX + c * (bw + 4), y, bw, 18, r[c * 2], r[c * 2 + 1]);
            }
            y += 20;
        }
        T2MenuActions.button(s, s.px1 - 108, s.footerT + 2, 96, 18, "close", null);
    }

    private static void capture(T2MenuScreen s) {
        item = T2MenuActions.fieldText(s.swItem).trim();
        count = T2MenuActions.fieldText(s.swCount).trim();
        schematic = T2MenuActions.fieldText(s.swSchem).trim();
    }

    private static void say(String m) { status = m; }

    static void run(T2MenuScreen s, String act) {
        capture(s);
        switch (act) {
            case "lead" -> { T2MenuActions.exec("swarm lead"); say("Asked to lead. Helpers can join now."); }
            case "join" -> { T2MenuActions.exec("swarm join"); say("Asked to join your leader."); }
            case "leave" -> { T2MenuActions.exec("swarm leave"); say("Left the swarm."); }
            case "stop" -> {
                T2MenuActions.exec("swarm cancel");
                try {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("swarm stop");
                } catch (Throwable ignored) {
                    // Ostinato link not running: nothing to stop on that side
                }
                say("Told every helper to stop.");
            }
            case "acquire" -> {
                if (item.isEmpty()) { say("Type an item name first, like oak_log."); break; }
                int n = 1;
                try { n = Math.max(1, Integer.parseInt(count)); } catch (NumberFormatException e) { say("Count must be a number."); break; }
                if (SwarmRuntime.leader() == null) { say("Only the leader can hand out jobs: press 'Lead the swarm' first."); break; }
                T2MenuActions.exec("swarm acquire " + item + " " + n);
                say("Job sent: gather " + n + " " + item + ".");
            }
            case "build" -> {
                if (schematic.isEmpty()) { say("Type a schematic name (a file in run/schematics)."); break; }
                try {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("swarm build " + schematic);
                    say("Build order sent for " + schematic + ". Watch Ostinato's chat for the split.");
                } catch (Throwable t) {
                    say("Could not start the build: " + t.getMessage());
                }
            }
            default -> { }
        }
        T2MenuActions.rebuild(s);
    }
}
