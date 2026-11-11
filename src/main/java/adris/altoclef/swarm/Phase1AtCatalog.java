package adris.altoclef.swarm;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.CommandExecutor;

/**
 * Installed {@code @} catalog as text. Safe to call when the mod is not in a world: it returns null.
 */
public final class Phase1AtCatalog {

    private Phase1AtCatalog() {}

    public static String exportInstalled() {
        try {
            if (AltoClef.getInstance() == null) {
                return null;
            }
            CommandExecutor executor = AltoClef.getCommandExecutor();
            if (executor == null) {
                return null;
            }
            return Phase1AtAdapter.export(executor.allCommands());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
