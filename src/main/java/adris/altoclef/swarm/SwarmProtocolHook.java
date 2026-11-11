package adris.altoclef.swarm;

import adris.altoclef.AltoClef;
import adris.altoclef.chains.UserTaskChain;
import adris.altoclef.commandsystem.CommandExecutor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * TenorClef {@code @} commands for the Phase 1 swarm protocol.
 * This class does not import Ostinato protocol types, so it still compiles against a stock Baritone jar.
 * Ostinato installs it as a proxy for ExternalCommandHook when that interface is present.
 * Idle is not treated as verified success. {@link #confirmStopped} only reports that
 * {@link AltoClef#stopTasks()} actually left the user chain stopped, the same stop {@code @stop} uses.
 */
public final class SwarmProtocolHook implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        if ("knows".equals(name)) {
            return Boolean.valueOf(knows(args == null || args.length == 0 ? null : String.valueOf(args[0])));
        }
        if ("start".equals(name)) {
            return Boolean.valueOf(start((UUID) args[0], (String) args[1], args.length > 2 ? (String) args[2] : ""));
        }
        if ("confirmStopped".equals(name)) {
            return Boolean.valueOf(confirmStopped((UUID) args[0]));
        }
        if ("equals".equals(name)) {
            return Boolean.valueOf(proxy == args[0]);
        }
        if ("hashCode".equals(name)) {
            return Integer.valueOf(System.identityHashCode(proxy));
        }
        if ("toString".equals(name)) {
            return "SwarmProtocolHook";
        }
        return null;
    }

    public boolean knows(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        CommandExecutor executor = AltoClef.getCommandExecutor();
        return executor != null && executor.get(name) != null;
    }

    public boolean start(UUID commandId, String name, String args) {
        CommandExecutor executor = AltoClef.getCommandExecutor();
        if (executor == null || executor.get(name) == null) {
            return false;
        }
        String line = args == null || args.trim().isEmpty() ? name : name + " " + args;
        executor.executeWithPrefix(line);
        return true;
    }

    public boolean confirmStopped(UUID commandId) {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null) {
            return false;
        }
        try {
            mod.stopTasks();
        } catch (RuntimeException e) {
            return false;
        }
        UserTaskChain chain = mod.getUserTaskChain();
        if (chain == null) {
            return true;
        }
        return !chain.isActive() || chain.isRunningIdleTask();
    }
}
