package adris.altoclef.commandsystem;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.commandsystem.exception.RuntimeCommandException;

import java.util.Collection;
import java.util.HashMap;
import java.util.function.Consumer;

public class CommandExecutor {

    private final HashMap<String, Command> commandSheet = new HashMap<>();
    private final AltoClef mod;

    public CommandExecutor(AltoClef mod) {
        this.mod = mod;
    }

    public void registerNewCommand(Command... commands) {
        for (Command command : commands) {
            for (String name : command.getNames()) {
                if (commandSheet.containsKey(name)) {
                    Debug.logInternal("Command with name " + name + " already exists! Can't register that name twice.");
                    continue;
                }
                
                commandSheet.put(name, command);
            }
        }
    }

    public String getCommandPrefix() {
        return mod.getModSettings().getCommandPrefix();
    }

    public boolean isClientCommand(String line) {
        return line.startsWith(getCommandPrefix());
    }

    // This is how we "nest" command finishes so we can complete them in order.
    private void executeRecursive(Command[] commands, String[] parts, int index, Runnable onFinish, Consumer<CommandException> getException) {
        if (index >= commands.length) {
            onFinish.run();
            return;
        }
        Command command = commands[index];
        String part = parts[index];
        if (command == null) {
            getException.accept(new RuntimeCommandException("Invalid command:" + part));
            executeRecursive(commands, parts, index + 1, onFinish, getException);
            return;
        }
        // Ostinato: local @ chat admits through the unified registry when that class is installed.
        // The registry does not read permission for local chat. A missing class keeps the old path.
        // command.run throws a checked CommandException, so the runnable catches it here. An outer
        // catch cannot see that exception (Java 25 rejects a catch the try body cannot throw).
        String stripped = part.strip();
        Runnable next = () -> executeRecursive(commands, parts, index + 1, onFinish, getException);
        Runnable body = () -> {
            try {
                command.run(mod, stripped, next);
            } catch (CommandException ae) {
                report(command, part, ae, getException);
            }
        };
        if (!dispatchLocalAt(stripped, body)) {
            body.run();
        }
    }

    // Ostinato: same usage line the old catch printed.
    private static void report(Command command, String part, CommandException error, Consumer<CommandException> getException) {
        try {
            getException.accept(new RuntimeCommandException(error.getMessage() + "\nUsage: " + command.getHelpRepresentation(new StringReader(part).nextOrEmpty()), error));
        } catch (RuntimeCommandException e) {
            throw new IllegalStateException("Should not happen!");
        }
    }

    /** True when the unified registry ran {@code body}. False means this class should run it. */
    private static boolean dispatchLocalAt(String line, Runnable body) {
        try {
            Class<?> type = Class.forName("baritone.swarm.center.UnifiedChat");
            Object ran = type.getMethod("localAt", String.class, Runnable.class).invoke(null, line, body);
            return Boolean.TRUE.equals(ran);
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (java.lang.reflect.InvocationTargetException invoked) {
            Throwable cause = invoked.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void execute(String line, Runnable onFinish, Consumer<CommandException> getException) {
        if (!isClientCommand(line)) return;
        line = line.substring(getCommandPrefix().length());
        // Run commands separated by ;
        String[] parts = line.split(";");
        Command[] commands = new Command[parts.length];
        try {
            for (int i = 0; i < parts.length; ++i) {
                String part = parts[i].strip();
                if (part.startsWith(getCommandPrefix())) {
                    part = part.substring(getCommandPrefix().length());
                }

                commands[i] = getCommand(part);
            }
        } catch (CommandException e) {
            getException.accept(e);
        }
        executeRecursive(commands, parts, 0, onFinish, getException);
    }

    public void execute(String line, Consumer<CommandException> getException) {
        execute(line, () -> {
        }, getException);
    }

    public void execute(String line) {
        execute(line, ex -> Debug.logWarning(ex.getMessage()));
    }

    public void executeWithPrefix(String line) {
        if (!line.startsWith(getCommandPrefix())) {
            line = getCommandPrefix() + line;
        }
        execute(line);
    }

    private Command getCommand(String line) throws RuntimeCommandException {
        line = line.trim();
        if (line.length() != 0) {
            String command = line;
            int firstSpace = line.indexOf(' ');
            if (firstSpace != -1) {
                command = line.substring(0, firstSpace);
            }

            if (!commandSheet.containsKey(command)) {
                throw new RuntimeCommandException("Command " + command + " does not exist.");
            }

            return commandSheet.get(command);
        }
        return null;

    }

    public Collection<Command> allCommands() {
        return commandSheet.values();
    }

    public Collection<String> allCommandNames() {
        return commandSheet.keySet();
    }

    public Command get(String name) {
        return (commandSheet.getOrDefault(name, null));
    }
}
