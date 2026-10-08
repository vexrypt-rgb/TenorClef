package adris.altoclef.butler;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.ChatMessageEvent;
import adris.altoclef.eventbus.events.TaskFinishedEvent;
import adris.altoclef.sigil.SigilService;
import adris.altoclef.ui.MessagePriority;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.message.MessageType;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The butler system lets authorized players send commands to the bot to execute.
 * <p>
 * This effectively makes the bot function as a servant, or butler.
 * <p>
 * Authorization is defined in "altoclef_butler_whitelist.txt" and "altoclef_butler_blacklist.txt"
 * and depends on the "useButlerWhitelist" and "useButlerBlacklist" settings in "altoclef_settings.json"
 */
public class Butler {

    private static final String BUTLER_MESSAGE_START = "` ";

    private final AltoClef mod;

    private final WhisperChecker whisperChecker = new WhisperChecker();

    private final UserAuth userAuth;

    private String currentUser = null;

    // Players whose latest command arrived sealed, and how to seal the answer back to them (SIGIL).
    private final Map<String, SigilService.Sealed> sealedRoutes = new ConcurrentHashMap<>();

    // Utility variables for command logic
    private boolean commandInstantRan = false;
    private boolean commandFinished = false;

    public Butler(AltoClef mod) {
        this.mod = mod;
        userAuth = new UserAuth(mod);

        // Revoke our current user whenever a task finishes.
        EventBus.subscribe(TaskFinishedEvent.class, evt -> {
            if (currentUser != null) {
                currentUser = null;
            }
        });

        // Receive system events
        EventBus.subscribe(ChatMessageEvent.class, evt -> {
            boolean debug = ButlerConfig.getInstance().whisperFormatDebug;
            String message = evt.messageContent();
            String sender = evt.senderName();
            MessageType messageType = evt.messageType();
            String receiver = mod.getPlayer().getName().getString();
            // System messages (advancements, command errors) have no sender on 1.16; answering them loops.
            if (sender != null && !sender.isEmpty() && !Objects.equals(sender, receiver) && shouldAccept(messageType)) {
                String wholeMessage = sender + " " + receiver + " " + message;
                if (debug) {
                    Debug.logMessage("RECEIVED WHISPER: \"" + wholeMessage + "\".");
                }
                this.mod.getButler().receiveMessage(wholeMessage, receiver);
            } else if (sender != null && !Objects.equals(sender, receiver)
                    && ButlerConfig.getInstance().sigilAutoDecrypt && SigilService.looksSealed(message)) {
                autoDecrypt(sender, message);
            }
        });
    }

    private static boolean shouldAccept(MessageType messageType) {
        //#if MC >= 11904
        return messageType.chat().style().isItalic()
                && messageType.chat().style().getColor() != null
                && Objects.equals(messageType.chat().style().getColor().getName(), "gray");
        //#else
        //$$ //it doesnt look like previous versions did any type of checking
        //$$ return true;
        //#endif
    }

    private void receiveMessage(String msg, String receiver) {
        // Format: <USER> whispers to you: <MESSAGE>
        // Format: <USER> whispers: <MESSAGE>
        WhisperChecker.MessageResult result = this.whisperChecker.receiveMessage(mod, receiver, msg);
        if (result != null) {
            this.receiveWhisper(result.from, result.message);
        } else if (ButlerConfig.getInstance().whisperFormatDebug) {
            Debug.logMessage("    Not Parsing: MSG format not found.");
        }
    }

    /** Public chat: show the plaintext locally if the keyring can open it. Never sent anywhere. */
    private void autoDecrypt(String sender, String message) {
        SigilService.get().open(sender, message, sealed -> {
            String who = sealed.from().isEmpty() ? sender : sender + " (" + sealed.from() + ")";
            Debug.logMessage("[SIGIL decrypted " + sealed.mode() + " " + sealed.label() + "] <" + who + "> " + sealed.plaintext());
        }, why -> {
            // Most tokens in chat are for circles we do not have; stay quiet unless debugging.
            if (ButlerConfig.getInstance().whisperFormatDebug) Debug.logInternal("SIGIL auto-decrypt skipped: " + why);
        });
    }

    /** Ostinato swarm traffic: a single S1C/S2C/S2S token. The swarm link consumes these; the butler must not answer them. */
    private static final java.util.regex.Pattern SWARM_TOKEN =
            java.util.regex.Pattern.compile("\\s*S(?:1C|2C|2S)\\.[a-z0-9]{4}\\.[A-Za-z0-9_-]{38,}\\s*");

    private void receiveWhisper(String username, String message) {
        if (SWARM_TOKEN.matcher(message).matches()) {
            return;
        }
        if (!message.startsWith(BUTLER_MESSAGE_START) && SigilService.looksSealed(message)) {
            // Decrypt first, off the game thread; auth and command parsing see only the plaintext.
            SigilService.get().open(username, message, sealed -> {
                sealedRoutes.put(username, sealed);
                handleWhisper(username, sealed.plaintext());
            }, why -> {
                if (ButlerConfig.getInstance().whisperFormatDebug) {
                    Debug.logMessage("    Rejecting: sealed whisper from \"" + username + "\" could not be opened: " + why);
                }
            });
            return;
        }
        if (!message.startsWith(BUTLER_MESSAGE_START) && ButlerConfig.getInstance().sigilRequireSealed) {
            if (ButlerConfig.getInstance().whisperFormatDebug) {
                Debug.logMessage("    Rejecting: \"" + username + "\" sent an unsealed whisper and sigilRequireSealed is on.");
            }
            return;
        }
        sealedRoutes.remove(username);
        handleWhisper(username, message);
    }

    private void handleWhisper(String username, String message) {

        boolean debug = ButlerConfig.getInstance().whisperFormatDebug;
        // Ignore messages from other bots.
        if (message.startsWith(BUTLER_MESSAGE_START)) {
            if (adris.altoclef.tasks.speedrun.testrun2.fleet.FleetProtocol.handle(username, message)) {
                return;
            }
            if (debug) {
                Debug.logMessage("    Rejecting: MSG is detected to be sent from another bot.");
            }
            return;
        }

        if (userAuth.isUserAuthorized(username)) {
            String reject = ButlerGuard.rejectReason(mod, username, message);
            if (reject != null) {
                if (debug) {
                    Debug.logMessage("    Rejecting butler: " + reject);
                }
                sendWhisper(username, "` denied: " + reject, MessagePriority.UNAUTHORIZED);
                return;
            }
            executeWhisper(username, message);
        } else {
            if (debug) {
                Debug.logMessage("    Rejecting: User \"" + username + "\" is not authorized.");
            }
            if (ButlerConfig.getInstance().sendAuthorizationResponse) {
                sendWhisper(username, ButlerConfig.getInstance().failedAuthorizationResposne.replace("{from}", username), MessagePriority.UNAUTHORIZED);
            }
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isUserAuthorized(String username) {
        return userAuth.isUserAuthorized(username);
    }

    public void onLog(String message, MessagePriority priority) {
        if (currentUser != null) {
            sendWhisper(message, priority);
        }
    }

    public void onLogWarning(String message, MessagePriority priority) {
        if (currentUser != null) {
            sendWhisper("[WARNING:] " + message, priority);
        }
    }

    public void tick() {
        // Nothing for now.
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public boolean hasCurrentUser() {
        return currentUser != null;
    }

    private void executeWhisper(String username, String message) {
        String prevUser = currentUser;
        commandInstantRan = true;
        commandFinished = false;
        currentUser = username;
        sendWhisper("Command Executing: " + message, MessagePriority.TIMELY);

        String prefix = mod.getModSettings().getCommandPrefix();
        String body = message.trim();
        if (body.startsWith(prefix)) body = body.substring(prefix.length()).trim();
        else if (body.startsWith("@")) body = body.substring(1).trim();
        AltoClef.getCommandExecutor().execute(prefix + body, () -> {
            // On finish
            sendWhisper("Command Finished: " + message, MessagePriority.TIMELY);
            if (!commandInstantRan) {
                currentUser = null;
            }
            commandFinished = true;
        }, e -> {
            for (String msg : e.getMessage().split("\n")) {
                sendWhisper("TASK FAILED: " + msg, MessagePriority.ASAP);
            }
            e.printStackTrace();
            currentUser = null;
            commandInstantRan = false;
        });
        commandInstantRan = false;
        // Only set the current user if we're still running.
        if (commandFinished) {
            currentUser = prevUser;
        }
    }

    private void sendWhisper(String message, MessagePriority priority) {
        if (currentUser != null) {
            sendWhisper(currentUser, message, priority);
        } else {
            Debug.logWarning("Failed to send butler message as there are no users present: " + message);
        }
    }

    private void sendWhisper(String username, String message, MessagePriority priority) {
        SigilService.Sealed route = sealedRoutes.get(username);
        if (route != null && ButlerConfig.getInstance().sigilReplySealed) {
            // A sealed request gets a sealed answer, or none: never fall back to plaintext.
            SigilService.get().sealReply(route, username, BUTLER_MESSAGE_START + message,
                    line -> mod.getMessageSender().enqueueWhisper(username, line, priority));
            return;
        }
        mod.getMessageSender().enqueueWhisper(username, BUTLER_MESSAGE_START + message, priority);
    }
}
