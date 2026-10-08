package adris.altoclef.butler;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.util.time.TimerGame;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WhisperChecker {

    private static final TimerGame _repeatTimer = new TimerGame(0.1);

    private static String _lastMessage = null;

    // this didn't work correctly, so I rewrote it without fancy regex stuff -miran
    public static MessageResult tryParse(String ourUsername, String whisperFormat, String message) {
        // Walk the format token by token: placeholders capture, literal tokens ("whispers:") must match exactly.
        String[] formatTokens = whisperFormat.trim().split(" +");
        ArrayList<String> messageParts = new ArrayList<>(Arrays.asList(message.split(" ")));
        MessageResult result = new MessageResult();
        for (int i = 0; i < formatTokens.length; i++) {
            String token = formatTokens[i];
            if (messageParts.isEmpty()) return null;

            if (token.equals("{from}")) {
                result.from = messageParts.remove(0);
            } else if (token.equals("{to}")) {
                String toUser = messageParts.remove(0);
                if (!toUser.equals(ourUsername)) {
                    Debug.logInternal("Rejected message since it is sent to " + toUser + " and not " + ourUsername);
                    return null;
                }
            } else if (token.equals("{message}")) {
                // Leave one message part for every format token that follows.
                int end = messageParts.size() - (formatTokens.length - i - 1);
                if (end < 1) return null;
                result.message = String.join(" ", messageParts.subList(0, end));
                messageParts.subList(0, end).clear();
            } else if (token.startsWith("{") && token.endsWith("}")) {
                throw new IllegalArgumentException("Unknown part: " + token);
            } else if (!messageParts.remove(0).equals(token)) {
                return null;
            }
        }
        return messageParts.isEmpty() ? result : null;
    }

    public MessageResult receiveMessage(AltoClef mod, String ourUsername, String msg) {
        String foundMiddlePart = "";
        int index = -1;

        boolean duplicate = (msg.equals(_lastMessage));
        if (duplicate && !_repeatTimer.elapsed()) {
            _repeatTimer.reset();
            // It's probably an actual duplicate. IDK why we get those but yeah.
            return null;
        }

        _lastMessage = msg;

        for (String format : ButlerConfig.getInstance().whisperFormats) {
            MessageResult check = tryParse(ourUsername, format, msg);
            if (check != null) {
                String user = check.from;
                String message = check.message;
                if (user == null || message == null) break;
                return check;
            }
        }

        return null;
    }

    public static class MessageResult {
        public String from;
        public String message;

        @Override
        public String toString() {
            return "MessageResult{" +
                    "from='" + from + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }
    }
}
