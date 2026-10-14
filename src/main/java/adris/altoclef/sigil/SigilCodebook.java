package adris.altoclef.sigil;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SIGIL codebook v2 (port of codebook.py at sigil 0.3.0). Plaintext is packed before AES-GCM and
 * the token carries a public ".z." flag. The lexicon is frozen at 4096 entries: never reorder it.
 * <p>
 * Codebook v1 (0xC1, 2048 entries, sigil 0.1/0.2) is not supported; such payloads are rejected.
 */
public final class SigilCodebook {

    public static final int MAGIC2 = 0xC2;
    public static final int MAGIC1 = 0xC1;
    public static final int WORD_COUNT = 4096;
    private static final String PUNCT = " .,:\n-/?!'\"();+";
    private static final String LEXICON_RESOURCE = "/assets/altoclef/sigil/lexicon_v2.txt";

    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9']*");
    private static final Pattern INT = Pattern.compile("-?[0-9]+");

    private static volatile Lexicon lexicon;

    private SigilCodebook() {}

    private static final class Lexicon {
        final String[] words;
        final Map<String, Integer> index = new HashMap<>();
        final List<String> phrases = new ArrayList<>();
        final List<Integer> phraseIndex = new ArrayList<>();

        Lexicon(String[] words) {
            this.words = words;
            // Later duplicates win, same as the dict comprehension in codebook.py.
            for (int i = 0; i < words.length; i++) index.put(words[i], i);
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < words.length; i++) if (words[i].indexOf(' ') >= 0) order.add(i);
            order.sort((a, b) -> Integer.compare(words[b].length(), words[a].length()));
            for (int i : order) {
                phrases.add(words[i]);
                phraseIndex.add(i);
            }
        }
    }

    private static Lexicon lexicon() {
        Lexicon l = lexicon;
        if (l != null) return l;
        synchronized (SigilCodebook.class) {
            if (lexicon == null) lexicon = new Lexicon(loadWords());
            return lexicon;
        }
    }

    private static String[] loadWords() {
        List<String> out = new ArrayList<>();
        try (InputStream in = SigilCodebook.class.getResourceAsStream(LEXICON_RESOURCE)) {
            if (in == null) throw new IllegalStateException("missing resource " + LEXICON_RESOURCE);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) out.add(line);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (out.size() != WORD_COUNT) {
            throw new IllegalStateException("lexicon_v2.txt has " + out.size() + " lines, need " + WORD_COUNT);
        }
        return out.toArray(new String[0]);
    }

    /** The frozen lexicon, for fingerprint words and dice passphrases. */
    public static List<String> words() {
        return List.of(lexicon().words);
    }

    // ------------------------------------------------------------------------------------------
    // Bit I/O
    // ------------------------------------------------------------------------------------------

    private static final class BitsOut {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        long acc;
        int n;

        void write(long value, int width) {
            value &= (1L << width) - 1;
            acc = (acc << width) | value;
            n += width;
            while (n >= 8) {
                n -= 8;
                buf.write((int) ((acc >> n) & 0xFF));
                acc &= (1L << n) - 1;
            }
        }

        byte[] finish() {
            if (n > 0) {
                buf.write((int) ((acc << (8 - n)) & 0xFF));
                acc = 0;
                n = 0;
            }
            return buf.toByteArray();
        }
    }

    private static final class BitsIn {
        final byte[] data;
        int i;
        long acc;
        int n;

        BitsIn(byte[] data, int start) {
            this.data = data;
            this.i = start;
        }

        int read(int width) {
            while (n < width) {
                if (i >= data.length) throw new IllegalArgumentException("truncated bitstream");
                acc = (acc << 8) | (data[i++] & 0xFF);
                n += 8;
            }
            n -= width;
            int v = (int) ((acc >> n) & ((1L << width) - 1));
            acc &= (1L << n) - 1;
            return v;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Compress
    // ------------------------------------------------------------------------------------------

    private static String asciiLower(String s) {
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) if (c[i] >= 'A' && c[i] <= 'Z') c[i] += 32;
        return new String(c);
    }

    public static byte[] compress(String text) {
        Lexicon lx = lexicon();
        BitsOut out = new BitsOut();
        String lower = asciiLower(text);
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\n') {
                emitPunct(out, '\n');
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            int matched = -1;
            int end = -1;
            for (int p = 0; p < lx.phrases.size(); p++) {
                String phrase = lx.phrases.get(p);
                if (!lower.startsWith(phrase, i)) continue;
                int e = i + phrase.length();
                if (e < n && Character.isLetterOrDigit(text.charAt(e))) continue;
                matched = lx.phraseIndex.get(p);
                end = e;
                break;
            }
            if (matched >= 0) {
                emitWord(out, matched);
                i = end;
                continue;
            }
            Matcher m = WORD.matcher(text);
            m.region(i, n);
            if (m.lookingAt()) {
                String raw = m.group();
                Integer idx = lx.index.get(asciiLower(raw));
                if (idx != null) {
                    emitWord(out, idx);
                } else {
                    emitRaw(out, raw);
                }
                i += raw.length();
                continue;
            }
            m = INT.matcher(text);
            m.region(i, n);
            if (m.lookingAt()) {
                String digits = m.group();
                emitInt(out, digits);
                i += digits.length();
                continue;
            }
            if (PUNCT.indexOf(c) >= 0) {
                emitPunct(out, c);
                i++;
                continue;
            }
            int cp = text.codePointAt(i);
            int len = Character.charCount(cp);
            emitRaw(out, text.substring(i, i + len));
            i += len;
        }
        out.write(0b111, 3);
        byte[] body = out.finish();
        byte[] res = new byte[body.length + 1];
        res[0] = (byte) MAGIC2;
        System.arraycopy(body, 0, res, 1, body.length);
        return res;
    }

    private static void emitWord(BitsOut out, int index) {
        if (index < 64) {
            out.write(0b000, 3);
            out.write(index, 6);
        } else if (index < 320) {
            out.write(0b001, 3);
            out.write(index - 64, 8);
        } else {
            out.write(0b010, 3);
            out.write(index, 12);
        }
    }

    private static void emitInt(BitsOut out, String digits) {
        BigInteger v = new BigInteger(digits);
        if (v.signum() >= 0 && v.compareTo(BigInteger.valueOf(63)) <= 0) {
            out.write(0b011, 3);
            out.write(v.longValue(), 6);
            return;
        }
        BigInteger zz = v.shiftLeft(1).xor(v.shiftRight(63));
        int bits = Math.max(2, zz.bitLength());
        if (bits > 32) {
            // The Python encoder silently truncates here. Send long numbers verbatim instead.
            emitRaw(out, digits);
            return;
        }
        out.write(0b100, 3);
        out.write(bits - 1, 5);
        out.write(zz.longValue(), bits);
    }

    private static void emitPunct(BitsOut out, char ch) {
        out.write(0b101, 3);
        out.write(PUNCT.indexOf(ch), 4);
    }

    private static void emitRaw(BitsOut out, String s) {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        for (int k = 0; k < raw.length; k += 16) {
            int len = Math.min(16, raw.length - k);
            out.write(0b110, 3);
            out.write(len - 1, 4);
            for (int j = 0; j < len; j++) out.write(raw[k + j] & 0xFF, 8);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Expand
    // ------------------------------------------------------------------------------------------

    public static String expand(byte[] data) {
        if (data.length == 0 || (data[0] & 0xFF) != MAGIC2) throw new IllegalArgumentException("not codebook v2");
        String[] words = lexicon().words;
        BitsIn bits = new BitsIn(data, 1);
        List<String> parts = new ArrayList<>();
        boolean needSpace = false;
        while (true) {
            int tag = bits.read(3);
            String token = null;
            switch (tag) {
                case 0b000 -> token = words[bits.read(6)];
                case 0b001 -> token = words[64 + bits.read(8)];
                case 0b010 -> token = words[bits.read(12)];
                case 0b011 -> token = String.valueOf(bits.read(6));
                case 0b100 -> {
                    int width = bits.read(5) + 1;
                    long zz = ((long) bits.read(width)) & 0xFFFFFFFFL;
                    token = String.valueOf((zz >>> 1) ^ -(zz & 1));
                }
                case 0b101 -> {
                    char ch = PUNCT.charAt(bits.read(4));
                    parts.add(String.valueOf(ch));
                    needSpace = ".,:;?!".indexOf(ch) >= 0;
                }
                case 0b110 -> {
                    int len = bits.read(4) + 1;
                    byte[] raw = new byte[len];
                    for (int k = 0; k < len; k++) raw[k] = (byte) bits.read(8);
                    String s = new String(raw, StandardCharsets.UTF_8);
                    if (!s.isEmpty() && Character.isLetterOrDigit(s.codePointAt(0))) {
                        token = s;
                    } else {
                        parts.add(s);
                        needSpace = false;
                    }
                }
                default -> {
                    return String.join("", parts);
                }
            }
            if (token != null) {
                if (needSpace && !parts.isEmpty()) {
                    String last = parts.get(parts.size() - 1);
                    if (!(last.endsWith(" ") || last.endsWith("\n"))) parts.add(" ");
                }
                parts.add(token);
                needSpace = true;
            }
        }
    }

    /** Returns the packed payload when it is smaller than the UTF-8 bytes, else null. */
    public static byte[] maybeCompress(String text) {
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        byte[] packed = compress(text);
        return packed.length < raw.length ? packed : null;
    }

    public static String maybeExpand(byte[] payload) {
        if (payload.length > 0 && (payload[0] & 0xFF) == MAGIC2) return expand(payload);
        if (payload.length > 0 && (payload[0] & 0xFF) == MAGIC1) {
            throw new IllegalArgumentException("codebook v1 payloads (sigil 0.1/0.2) are not supported");
        }
        return new String(payload, StandardCharsets.UTF_8);
    }
}
