package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.schematic.SchematicBuildTask;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * {@code @schem list} or {@code @schem house.schem}
 *
 * Files live in {@code <gameDir>/schematics/} (Baritone's folder).
 */
public class BuildSchematicCommand extends Command {

    public BuildSchematicCommand() {
        super("schem", "Gather tools/blocks then Baritone-build a schematic",
                new StringArg("file", "list"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String name = "list";
        try {
            name = parser.get(String.class);
        } catch (Throwable ignored) {}
        if (name == null || name.isBlank()) name = "list";

        File dir = schematicsDir();
        if (!dir.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            Debug.logMessage("SCHEM created " + dir.getAbsolutePath());
        }

        if (name.equalsIgnoreCase("list") || name.equals("?")) {
            File[] files = dir.listFiles((d, n) -> {
                String l = n.toLowerCase(Locale.ROOT);
                return l.endsWith(".schem") || l.endsWith(".schematic")
                        || l.endsWith(".litematic") || l.endsWith(".nbt");
            });
            if (files == null || files.length == 0) {
                Debug.logMessage("SCHEM empty. Put a .schem / .schematic in " + dir.getAbsolutePath());
            } else {
                Debug.logMessage("SCHEM " + files.length + " file(s) in " + dir.getAbsolutePath());
                Arrays.sort(files);
                for (File f : files) {
                    Debug.logMessage("  " + f.getName() + "  " + (f.length() / 1024) + "k");
                }
            }
            finish();
            return;
        }

        File file = resolve(dir, name);
        if (file == null || !file.isFile()) {
            Debug.logWarning("SCHEM not found: " + name + " in " + dir.getAbsolutePath());
            finish();
            return;
        }

        BlockPos origin = null;
        try {
            origin = mod.getPlayer().getBlockPos();
        } catch (Throwable ignored) {}

        Debug.logMessage("SCHEM start " + file.getName());
        mod.runUserTask(new SchematicBuildTask(file, origin), this::finish);
    }

    private static File schematicsDir() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.runDirectory != null) {
                return new File(mc.runDirectory, "schematics");
            }
        } catch (Throwable ignored) {}
        return new File("schematics");
    }

    private static File resolve(File dir, String name) {
        File direct = new File(dir, name);
        if (direct.isFile()) return direct;
        String[] extra = { "", ".schem", ".schematic", ".litematic", ".nbt" };
        for (String e : extra) {
            File f = new File(dir, name + e);
            if (f.isFile()) return f;
        }
        File[] all = dir.listFiles();
        if (all == null) return null;
        for (File f : all) {
            if (f.getName().equalsIgnoreCase(name)
                    || f.getName().toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT))) {
                return f;
            }
        }
        return null;
    }
}
