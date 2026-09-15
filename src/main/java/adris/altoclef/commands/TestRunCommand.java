package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.movement.TungstenMovement;
import adris.altoclef.tasks.speedrun.SpeedrunBeatMinecraftTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Experimental modern RSG speedrun command ({@code @testrun}).
 * Runs {@link SpeedrunBeatMinecraftTask} alongside {@code @gamer} for testing.
 *
 * Modes (optional first arg, default {@code start}):
 * <ul>
 *   <li>{@code start} - full route from overworld early</li>
 *   <li>{@code nether}/{@code barter}/{@code fortress}/{@code stronghold}/{@code end} - jump-start phase</li>
 *   <li>{@code status} - report live phase/debug if a testrun is active</li>
 *   <li>{@code verbose} - toggle verbose phase logging for subsequent runs</li>
 *   <li>{@code phases}/{@code help} - list modes and phase order</li>
 * </ul>
 * Optional flags (any order after mode): {@code pearls=N} {@code rods=N}
 * {@code skipfood}|{@code food} {@code mover=auto|tungsten|baritone} {@code verbose} {@code nohud}
 */
public class TestRunCommand extends Command {

    /** Session toggle applied when {@code @testrun verbose} is used alone, or as a flag. */
    private static boolean sessionVerbose = false;

    private static final String HELP_TEXT = String.join("\n",
            "@testrun [mode] [flags...]  -- modern RSG via SpeedrunBeatMinecraftTask (not @gamer)",
            "  start       full route from OVERWORLD_EARLY (default)",
            "  nether      NETHER_ENTRY",
            "  barter      BARTER_AND_LOOT (pearls/bastion)",
            "  fortress    FORTRESS (blaze rods)",
            "  stronghold  STRONGHOLD",
            "  end         END_FIGHT",
            "  status      live phase + timers/counts",
            "  verbose     toggle verbose phase logs (session)",
            "  phases|help this text",
            "Flags: pearls=14 rods=7 skipfood|food mover=auto|tungsten|baritone verbose nohud",
            "Also: @status @coords @inventory @stop @pause @unpause"
    );

    public TestRunCommand() {
        super("testrun",
                "Modern RSG speedrun (@testrun [mode] [flags...])",
                new StringArg("mode", "start"),
                new StringArg("f1", "", false),
                new StringArg("f2", "", false),
                new StringArg("f3", "", false),
                new StringArg("f4", "", false),
                new StringArg("f5", "", false),
                new StringArg("f6", "", false));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String mode = parser.get(String.class);
        List<String> flags = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String f = parser.get(String.class);
            if (f != null && !f.isBlank()) flags.add(f.trim());
        }
        if (mode == null || mode.isBlank()) mode = "start";
        mode = mode.trim().toLowerCase(Locale.ROOT);

        // Allow @testrun pearls=14 ... (first token is a flag, imply start)
        if (isFlagToken(mode)) {
            flags.add(0, mode);
            mode = "start";
        }

        switch (mode) {
            case "help", "?", "phases", "phase" -> {
                for (String line : HELP_TEXT.split("\n")) {
                    mod.log(line);
                }
                finish();
            }
            case "status", "info" -> {
                reportStatus(mod);
                finish();
            }
            case "verbose", "v" -> {
                if (flags.isEmpty()) {
                    sessionVerbose = !sessionVerbose;
                    mod.log("@testrun verbose session=" + sessionVerbose);
                    finish();
                } else {
                    // verbose + other flags => start with verbose
                    flags.add("verbose");
                    startFrom(mod, SpeedrunBeatMinecraftTask.Phase.OVERWORLD_EARLY, flags);
                }
            }
            case "start", "run", "full" ->
                    startFrom(mod, SpeedrunBeatMinecraftTask.Phase.OVERWORLD_EARLY, flags);
            case "nether", "nether_entry", "portal" ->
                    startFrom(mod, SpeedrunBeatMinecraftTask.Phase.NETHER_ENTRY, flags);
            case "barter", "loot", "nether_gear", "bastion" ->
                startFrom(mod, SpeedrunBeatMinecraftTask.Phase.BARTER_AND_LOOT, flags);
            case "fortress", "blaze", "rods" ->
                    startFrom(mod, SpeedrunBeatMinecraftTask.Phase.FORTRESS, flags);
            case "stronghold", "eyes", "sh" ->
                    startFrom(mod, SpeedrunBeatMinecraftTask.Phase.STRONGHOLD, flags);
            case "end", "dragon", "end_fight", "fight" ->
                    startFrom(mod, SpeedrunBeatMinecraftTask.Phase.END_FIGHT, flags);
            default -> {
                mod.logWarning("Unknown @testrun mode '" + mode + "'. Use @testrun help");
                finish();
            }
        }
    }

    private static boolean isFlagToken(String t) {
        String s = t.toLowerCase(Locale.ROOT);
        return s.contains("=")
                || s.equals("skipfood") || s.equals("nofood")
                || s.equals("food") || s.equals("collectfood")
                || s.equals("verbose") || s.equals("v")
                || s.equals("nohud") || s.equals("hud");
    }

    private void startFrom(AltoClef mod, SpeedrunBeatMinecraftTask.Phase phase, List<String> flags) {
        SpeedrunBeatMinecraftTask.Config cfg = SpeedrunBeatMinecraftTask.Config.defaults();
        if (sessionVerbose) cfg.verbose = true;
        applyFlags(cfg, flags);
        mod.log("Starting @testrun at " + phase
                + " pearls=" + cfg.pearlTarget
                + " rods=" + cfg.blazeRodTarget
                + " skipFood=" + cfg.skipFood
                + " mover=" + cfg.mover
                + " verbose=" + cfg.verbose
                + " (dim=" + WorldHelper.getCurrentDimension() + ")");
        mod.runUserTask(new SpeedrunBeatMinecraftTask(mod, phase, cfg), this::finish);
    }

    private static void applyFlags(SpeedrunBeatMinecraftTask.Config cfg, List<String> flags) {
        if (flags == null) return;
        for (String raw : flags) {
            if (raw == null || raw.isBlank()) continue;
            String f = raw.trim().toLowerCase(Locale.ROOT);
            if (f.equals("skipfood") || f.equals("nofood")) {
                cfg.skipFood = true;
                continue;
            }
            if (f.equals("food") || f.equals("collectfood")) {
                cfg.skipFood = false;
                continue;
            }
            if (f.equals("verbose") || f.equals("v")) {
                cfg.verbose = true;
                continue;
            }
            if (f.equals("nohud")) {
                cfg.phaseChatHud = false;
                continue;
            }
            if (f.equals("hud")) {
                cfg.phaseChatHud = true;
                continue;
            }
            int eq = f.indexOf('=');
            if (eq <= 0) continue;
            String key = f.substring(0, eq).trim();
            String val = f.substring(eq + 1).trim();
            switch (key) {
                case "pearls", "pearl", "enderpearls" -> cfg.pearlTarget = parsePositive(val, cfg.pearlTarget);
                case "rods", "blaze", "blazerods", "blaze_rods" -> cfg.blazeRodTarget = parsePositive(val, cfg.blazeRodTarget);
                case "mover", "travel", "path" -> cfg.mover = TungstenMovement.parseTravelMover(val);
                case "skipfood", "nofood" -> cfg.skipFood = parseBool(val, true);
                case "food", "collectfood" -> cfg.skipFood = !parseBool(val, true);
                case "verbose" -> cfg.verbose = parseBool(val, true);
                case "hud" -> cfg.phaseChatHud = parseBool(val, true);
                default -> {}
            }
        }
    }

    private static int parsePositive(String val, int fallback) {
        try {
            int n = Integer.parseInt(val);
            return n > 0 ? n : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBool(String val, boolean fallback) {
        if (val == null || val.isBlank()) return fallback;
        return switch (val.toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on", "y" -> true;
            case "0", "false", "no", "off", "n" -> false;
            default -> fallback;
        };
    }

    private void reportStatus(AltoClef mod) {
        SpeedrunBeatMinecraftTask speedrun = findActiveSpeedrun(mod);
        if (speedrun == null) {
            List<Task> tasks = mod.getUserTaskChain().getTasks();
            if (tasks.isEmpty()) {
                mod.log("@testrun status: no user tasks running (start with @testrun)");
            } else {
                mod.log("@testrun status: SpeedrunBeatMinecraftTask not active. CURRENT: " + tasks.get(0));
            }
            mod.log("sessionVerbose=" + sessionVerbose + " | " + TungstenMovement.statusLine());
            return;
        }
        mod.log("@testrun status: phase=" + speedrun.getCurrentPhase()
                + " dim=" + WorldHelper.getCurrentDimension()
                + " | " + speedrun);
        String summary = speedrun.getDebugSummary();
        if (summary != null && !summary.isBlank()) {
            mod.log(summary);
        }
    }

    private static SpeedrunBeatMinecraftTask findActiveSpeedrun(AltoClef mod) {
        Task current = mod.getUserTaskChain().getCurrentTask();
        if (current instanceof SpeedrunBeatMinecraftTask s) {
            return s;
        }
        List<Task> tasks = mod.getUserTaskChain().getTasks();
        for (Task t : tasks) {
            if (t instanceof SpeedrunBeatMinecraftTask s) {
                return s;
            }
        }
        return null;
    }
}
