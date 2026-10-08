package adris.altoclef.butler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WhisperCheckerTest {
    private static final String[] FORMATS = new ButlerConfig().whisperFormats;

    private static WhisperChecker.MessageResult first(String me, String line) {
        for (String f : FORMATS) {
            WhisperChecker.MessageResult r = WhisperChecker.tryParse(me, f, line);
            if (r != null) return r;
        }
        return null;
    }

    @Test
    void whispersFormatSkipsLiteral() {
        WhisperChecker.MessageResult r = first("Bot", "Alice whispers: @help me");
        assertEquals("Alice", r.from);
        assertEquals("@help me", r.message);
    }

    @Test
    void whispersToYouFormat() {
        WhisperChecker.MessageResult r = first("Bot", "Alice whispers to you: @get iron");
        assertEquals("Alice", r.from);
        assertEquals("@get iron", r.message);
    }

    @Test
    void realEventShapeReachesThirdFormat() {
        WhisperChecker.MessageResult r = first("Bot", "Alice Bot @help");
        assertEquals("Alice", r.from);
        assertEquals("@help", r.message);
    }

    @Test
    void wrongRecipientRejected() {
        assertNull(first("Bot", "Alice Someone @help"));
    }

    @Test
    void literalMismatchRejected() {
        assertNull(WhisperChecker.tryParse("Bot", "{from} whispers: {message}", "Alice shouts: hi"));
    }
}
