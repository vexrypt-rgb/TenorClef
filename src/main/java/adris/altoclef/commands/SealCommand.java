package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.sigil.Sigil;
import adris.altoclef.sigil.SigilService;
import adris.altoclef.ui.MessagePriority;

/**
 * {@code @seal <circle|player> <message>}: encrypt with SIGIL and send. A circle gets public chat lines,
 * a contact gets whispers sealed to their signet. Keys come from the SIGIL tab / altoclef/sigil/.
 */
public class SealCommand extends Command {

    private String message = "";

    public SealCommand() {
        super("seal", "Send a SIGIL-encrypted message to a circle (public chat) or a contact (whisper): @seal <circle|player> <message>",
                new StringArg("target"));
    }

    @Override
    public void run(AltoClef mod, String line, Runnable onFinish) throws CommandException {
        // The message is everything after the target, spaces kept.
        String[] p = line.trim().split("\s+", 3);
        message = p.length > 2 ? p[2] : "";
        super.run(mod, p[0] + (p.length > 1 ? " " + p[1] : ""), onFinish);
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String target = parser.get(String.class);
        String text = message;
        if (text.isBlank()) {
            Debug.logWarning("Usage: @seal <circle|player> <message>");
            finish();
            return;
        }
        boolean toCircle = SigilService.get().keyring().circle(target) != null;
        int maxLine = toCircle ? Sigil.MAX_LINE : SigilService.WHISPER_LINE;
        SigilService.get().seal(target, text, maxLine, lines -> {
            for (String l : lines) {
                if (toCircle) mod.getMessageSender().enqueueChat(l, MessagePriority.TIMELY);
                else mod.getMessageSender().enqueueWhisper(target, l, MessagePriority.TIMELY);
            }
            Debug.logMessage("SIGIL sealed " + lines.size() + " line(s) to " + target);
            finish();
        }, why -> {
            Debug.logWarning("SIGIL: " + why);
            finish();
        });
    }
}
