package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.butler.ButlerConfig;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** {@code @butler} status / {@code @butler allow Steve} */
public class ButlerCommand extends Command {

    public ButlerCommand() {
        super(List.of("butler", "tenorbutler"), "TenorClef butler whitelist helper",
                new StringArg("args", "status"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String raw = "status";
        try {
            String a = parser.get(String.class);
            if (a != null && !a.isBlank()) raw = a;
        } catch (Throwable ignored) {}
        String[] p = raw.trim().split("\\s+", 2);
        String mode = p[0].toLowerCase();
        ButlerConfig cfg = ButlerConfig.getInstance();
        switch (mode) {
            case "allow", "add", "wl" -> {
                if (p.length < 2) {
                    Debug.logWarning("BUTLER @butler allow <name>");
                    finish();
                    return;
                }
                String name = p[1].trim();
                try {
                    Path file = Path.of("altoclef_butler_whitelist.txt");
                    if (mod.getModSettings() != null) {
                        // run dir relative
                    }
                    Files.writeString(file, name + "\n", StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    Debug.logMessage("BUTLER added " + name + " to " + file.toAbsolutePath()
                            + " (reload world / restart if it does not take effect)");
                } catch (Throwable t) {
                    Debug.logWarning("BUTLER write " + t.getMessage());
                }
            }
            default -> {
                Debug.logMessage("BUTLER whitelist=" + cfg.useButlerWhitelist
                        + " blacklist=" + cfg.useButlerBlacklist
                        + " prefixRequired=" + cfg.requirePrefixMsg);
                Debug.logMessage("BUTLER whisper @get food  |  denied: escape agent aa testrun2 mapart");
                Debug.logMessage("BUTLER add names to altoclef_butler_whitelist.txt or @butler allow <name>");
            }
        }
        finish();
    }
}
