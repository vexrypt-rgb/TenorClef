package kaptainwutax.tungsten.commandsystem;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import kaptainwutax.tungsten.TungstenMod;
import net.minecraft.server.command.CommandSource;
import net.minecraft.text.LiteralText;

public abstract class Command {

	protected static final Object REGISTRY_ACCESS = null; // 1.16.1: no CommandRegistryAccess
    protected static final int SINGLE_SUCCESS = com.mojang.brigadier.Command.SINGLE_SUCCESS;
    protected final static SimpleCommandExceptionType INCORRECT_USE = new SimpleCommandExceptionType(new LiteralText("Incorrect command use!"));
    
    private final String _name;
    private final String _description;
    protected TungstenMod _mod;
    private Runnable _onFinish = null;

    public Command(String name, String description, TungstenMod mod) {
        _name = name;
        _description = description;
        _mod = mod;
    }

    public String getName() {
        return _name;
    }

    public String getDescription() {
        return _description;
    }

    public void run(TungstenMod mod, String line, Runnable onFinish) throws CommandException {
        _onFinish = onFinish;
        try {
			CommandExecutor.dispatch(line);
		} catch (CommandSyntaxException e) {
			e.printStackTrace();
		}
    }

    protected void finish() {
        if (_onFinish != null)
            _onFinish.run();
    }
    
    public abstract void build(LiteralArgumentBuilder<CommandSource> builder);
    
    protected static <T> RequiredArgumentBuilder<CommandSource, T> argument(final String name, final ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    protected static LiteralArgumentBuilder<CommandSource> literal(final String name) {
        return LiteralArgumentBuilder.literal(name);
    }
    
    public final void registerTo(CommandDispatcher<CommandSource> dispatcher) {
        register(dispatcher, _name);
    }
    
    public void register(CommandDispatcher<CommandSource> dispatcher, String name) {
        LiteralArgumentBuilder<CommandSource> builder = LiteralArgumentBuilder.literal(name);
        build(builder);
        dispatcher.register(builder);
    }
}
