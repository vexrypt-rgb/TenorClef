package adris.altoclef.sigil;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SIGIL S1 wire format and crypto (port of sigil.py at sigil 0.3.0, commit 697247b).
 * Pure JCA: AES-256-GCM, PBKDF2-HMAC-SHA256 (210k), ECDH P-256, HKDF-SHA256. No Minecraft types,
 * so it is unit-testable and safe to call from any thread. PBKDF2 takes about 0.3s: call
 * {@link #circleKey} off the tick thread; results are cached per (name, passphrase).
 */
public final class Sigil {

    public static final String VERSION = "S1";
    public static final String PROTOCOL = "SIGIL.v1";
    public static final int PBKDF2_ITERS = 210_000;
    public static final int NONCE_LEN = 12;
    public static final int TAG_LEN = 16;
    public static final int P256_COMPRESSED_LEN = 33;
    public static final int MAX_LINE = 256;

    private static final SecureRandom RNG = new SecureRandom();
    private static final ConcurrentHashMap<String, byte[]> KEY_CACHE = new ConcurrentHashMap<>();
    private static final BigInteger P = new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16);
    private static final BigInteger B = new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16);

    private Sigil() {}

    // ------------------------------------------------------------------------------------------
    // Records
    // ------------------------------------------------------------------------------------------

    public record Circle(String name, String slug, String passphrase) {
        public String fingerprint() {
            return shortId(circleKey(name, passphrase));
        }
    }

    /** A local identity. {@code skPem} is a PKCS#8 PEM, the same as the Python keyring. */
    public record Signet(String name, String shortId, byte[] pk, String skPem) {}

    public record Contact(String alias, String nameHint, String shortId, byte[] pk) {}

    /** One decrypted SIGIL line. {@code label} is the circle name or the signet name it was sealed for. */
    public record Opened(String mode, String label, String from, String fromShort, int part, int total, boolean compact, String plaintext) {}

    // ------------------------------------------------------------------------------------------
    // Encoding helpers
    // ------------------------------------------------------------------------------------------

    public static String b64e(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static byte[] b64d(String text) {
        return Base64.getUrlDecoder().decode(text);
    }

    public static String shortId(byte[] data) {
        try {
            String s = b64e(MessageDigest.getInstance("SHA-256").digest(data));
            return s.substring(0, 4).toLowerCase(Locale.ROOT);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String slugify(String name) {
        return slugify(name, 4);
    }

    public static String slugify(String name, int n) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) if (Character.isLetterOrDigit(c)) sb.append(c);
        String cleaned = sb.length() == 0 ? "circ" : sb.toString();
        StringBuilder padded = new StringBuilder(cleaned);
        for (int i = 0; i < n; i++) padded.append('x');
        return padded.substring(0, n);
    }

    // ------------------------------------------------------------------------------------------
    // Keys
    // ------------------------------------------------------------------------------------------

    public static byte[] circleKey(String name, String passphrase) {
        return KEY_CACHE.computeIfAbsent(name + "\u0000" + passphrase, k -> deriveCircleKey(name, passphrase)).clone();
    }

    static byte[] deriveCircleKey(String name, String passphrase) {
        try {
            byte[] salt = Arrays.copyOf(MessageDigest.getInstance("SHA-256")
                    .digest((PROTOCOL + ".circle.salt." + name).getBytes(StandardCharsets.UTF_8)), 16);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return f.generateSecret(new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERS, 256)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** True once the PBKDF2 result for this circle is cached, so callers can tell when open() will be instant. */
    public static boolean keyCached(Circle c) {
        return KEY_CACHE.containsKey(c.name() + "\u0000" + c.passphrase());
    }

    public static Circle newCircle(String name, String passphrase) {
        return new Circle(name, slugify(name), passphrase);
    }

    public static Signet newSignet(String name) {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"), RNG);
            KeyPair kp = g.generateKeyPair();
            byte[] pk = compress((ECPublicKey) kp.getPublic());
            return new Signet(name, shortId(pk), pk, toPem(kp.getPrivate().getEncoded()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Contact newContact(String alias, String nameHint, byte[] pk) {
        byte[] compressed = compressPoint(pk);
        return new Contact(alias, nameHint == null || nameHint.isEmpty() ? alias : nameHint, shortId(compressed), compressed);
    }

    private static ECParameterSpec curve() throws GeneralSecurityException {
        AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
        ap.init(new ECGenParameterSpec("secp256r1"));
        return ap.getParameterSpec(ECParameterSpec.class);
    }

    private static byte[] fixed32(BigInteger v) {
        byte[] raw = v.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }

    static byte[] compress(ECPublicKey pk) {
        ECPoint w = pk.getW();
        byte[] out = new byte[P256_COMPRESSED_LEN];
        out[0] = (byte) (w.getAffineY().testBit(0) ? 3 : 2);
        System.arraycopy(fixed32(w.getAffineX()), 0, out, 1, 32);
        return out;
    }

    /** Accepts a 33-byte compressed or 65-byte uncompressed point; returns the compressed form. */
    static byte[] compressPoint(byte[] raw) {
        return compress(publicKey(raw));
    }

    static ECPublicKey publicKey(byte[] raw) {
        try {
            BigInteger x;
            BigInteger y;
            if (raw.length == 33 && (raw[0] == 2 || raw[0] == 3)) {
                x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 33));
                BigInteger rhs = x.pow(3).subtract(x.multiply(BigInteger.valueOf(3))).add(B).mod(P);
                y = rhs.modPow(P.add(BigInteger.ONE).shiftRight(2), P);
                if (!y.multiply(y).mod(P).equals(rhs)) throw new IllegalArgumentException("point is not on the curve");
                if (y.testBit(0) != (raw[0] == 3)) y = P.subtract(y);
            } else if (raw.length == 65 && raw[0] == 4) {
                x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 33));
                y = new BigInteger(1, Arrays.copyOfRange(raw, 33, 65));
                BigInteger rhs = x.pow(3).subtract(x.multiply(BigInteger.valueOf(3))).add(B).mod(P);
                if (!y.multiply(y).mod(P).equals(rhs)) throw new IllegalArgumentException("point is not on the curve");
            } else {
                throw new IllegalArgumentException("public key has the wrong length");
            }
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), curve()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static ECPrivateKey privateKey(String pem) {
        try {
            String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static String toPem(byte[] der) {
        String b = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN PRIVATE KEY-----\n" + b + "\n-----END PRIVATE KEY-----\n";
    }

    private static byte[] ecdhKey(PrivateKey sk, PublicKey pk, byte[] info) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(sk);
            ka.doPhase(pk, true);
            byte[] shared = ka.generateSecret();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
            byte[] prk = mac.doFinal(shared);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(info);
            mac.update((byte) 1);
            return mac.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] cat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------------------------------
    // AES-GCM
    // ------------------------------------------------------------------------------------------

    private static byte[] gcm(boolean encrypt, byte[] key, byte[] nonce, byte[] aad, byte[] data) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        c.updateAAD(aad);
        return c.doFinal(data);
    }

    // ------------------------------------------------------------------------------------------
    // Seal
    // ------------------------------------------------------------------------------------------

    private static int codePoints(String s) {
        return s.codePointCount(0, s.length());
    }

    private static List<String> chunkPlain(String text, int maxPayloadChars) {
        byte[] raw = utf8(text);
        if (b64e(new byte[NONCE_LEN + raw.length + TAG_LEN]).length() <= maxPayloadChars) return List.of(text);
        int byteBudget = Math.max(24, maxPayloadChars * 3 / 4 - NONCE_LEN - TAG_LEN - 8);
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int curBytes = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String piece = new String(Character.toChars(cp));
            int pb = utf8(piece).length;
            if (curBytes > 0 && curBytes + pb > byteBudget) {
                parts.add(cur.toString());
                cur.setLength(0);
                curBytes = 0;
            }
            cur.append(piece);
            curBytes += pb;
            i += Character.charCount(cp);
        }
        if (cur.length() > 0) parts.add(cur.toString());
        return parts.isEmpty() ? List.of("") : parts;
    }

    private static int packedLen(String s) {
        byte[] packed = SigilCodebook.maybeCompress(s);
        return packed != null ? packed.length : utf8(s).length;
    }

    private static List<String> chunkCompact(String text, int maxPayloadBytes) {
        int budget = Math.max(8, maxPayloadBytes);
        List<String> parts = new ArrayList<>();
        String rest = text;
        while (!rest.isEmpty()) {
            int lo = 1;
            int hi = rest.length();
            int best = 1;
            while (lo <= hi) {
                int mid = (lo + hi) / 2;
                if (packedLen(rest.substring(0, mid)) <= budget) {
                    best = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            if (best < rest.length()) {
                int snapped = rest.substring(0, best).lastIndexOf(' ');
                if (snapped >= Math.max(8, best / 4)) best = snapped + 1;
            }
            if (best < rest.length() && Character.isLowSurrogate(rest.charAt(best))) best--;
            if (best < 1) best = Math.min(rest.length(), 2);
            parts.add(rest.substring(0, best));
            rest = rest.substring(best);
        }
        return parts.isEmpty() ? List.of("") : parts;
    }

    /** Payload bytes plus whether the codebook was used. */
    private record Body(byte[] bytes, boolean z) {}

    private static Body body(String text, boolean compact) {
        if (compact) {
            byte[] packed = SigilCodebook.maybeCompress(text);
            if (packed != null) return new Body(packed, true);
        }
        return new Body(utf8(text), false);
    }

    private static String frag(int i, int total) {
        return total == 1 ? "" : i + "/" + total + ".";
    }

    public static List<String> sealCircle(Circle circle, String plaintext, int maxLine, boolean compact) {
        byte[] key = circleKey(circle.name(), circle.passphrase());
        int header = 12 + circle.slug().length() + (compact ? 2 : 0);
        int room = Math.max(32, maxLine - header);
        int rawBudget = Math.max(24, room * 3 / 4 - NONCE_LEN - TAG_LEN);
        List<String> parts = compact ? chunkCompact(plaintext, rawBudget) : chunkPlain(plaintext, room);
        List<String> lines = new ArrayList<>();
        int total = parts.size();
        try {
            for (int i = 1; i <= total; i++) {
                Body b = body(parts.get(i - 1), compact);
                byte[] nonce = new byte[NONCE_LEN];
                RNG.nextBytes(nonce);
                String aad = PROTOCOL + ".C." + circle.name() + (b.z() ? ".z" : "") + "." + i + "/" + total;
                byte[] ct = gcm(true, key, nonce, utf8(aad), b.bytes());
                lines.add(VERSION + "C." + circle.slug() + (b.z() ? ".z" : "") + "." + frag(i, total) + b64e(cat(nonce, ct)));
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        return lines;
    }

    public static List<String> sealToSignet(Signet local, Contact to, String plaintext, boolean ephemeral, int maxLine, boolean compact) {
        PublicKey theirPk = publicKey(to.pk());
        String theirShort = to.shortId();
        int budget = Math.max(32, maxLine - 16 - theirShort.length() - (compact ? 2 : 0));
        if (ephemeral) budget -= 50;
        int rawBudget = Math.max(24, budget * 3 / 4 - NONCE_LEN - TAG_LEN);
        List<String> parts = compact ? chunkCompact(plaintext, rawBudget) : chunkPlain(plaintext, budget);
        List<String> lines = new ArrayList<>();
        int total = parts.size();
        try {
            for (int i = 1; i <= total; i++) {
                Body b = body(parts.get(i - 1), compact);
                String z = b.z() ? ".z" : "";
                byte[] nonce = new byte[NONCE_LEN];
                RNG.nextBytes(nonce);
                String prefix;
                String blob;
                if (ephemeral) {
                    KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
                    g.initialize(new ECGenParameterSpec("secp256r1"), RNG);
                    KeyPair eph = g.generateKeyPair();
                    byte[] ephPk = compress((ECPublicKey) eph.getPublic());
                    byte[] info = utf8(PROTOCOL + ".E." + theirShort + z + "." + i + "/" + total);
                    byte[] key = ecdhKey(eph.getPrivate(), theirPk, info);
                    blob = b64e(cat(ephPk, nonce, gcm(true, key, nonce, info, b.bytes())));
                    prefix = VERSION + "E." + theirShort + z;
                } else {
                    ECPrivateKey mySk = privateKey(local.skPem());
                    byte[] info = utf8(PROTOCOL + ".K." + local.shortId() + "." + theirShort + z + "." + i + "/" + total);
                    byte[] key = ecdhKey(mySk, theirPk, cat(info, local.pk(), to.pk()));
                    blob = b64e(cat(nonce, gcm(true, key, nonce, info, b.bytes())));
                    prefix = VERSION + "K." + theirShort + "." + local.shortId() + z;
                }
                lines.add(prefix + "." + frag(i, total) + blob);
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        return lines;
    }

    // ------------------------------------------------------------------------------------------
    // Open
    // ------------------------------------------------------------------------------------------

    /** The first whitespace/comma separated word that looks like an S1C/S1K/S1E token, or null. */
    public static String findToken(String text) {
        for (String w : text.replace(',', ' ').trim().split("\\s+")) {
            if (w.startsWith("S1C.") || w.startsWith("S1K.") || w.startsWith("S1E.")) return w;
        }
        return null;
    }

    /** Keyring lookups needed to open a line. */
    public interface Keys {
        List<Circle> circles();

        List<Signet> signets();

        Contact contactByShort(String shortId);
    }

    /**
     * Opens one SIGIL line against the keys. Throws {@link IllegalArgumentException} with a short reason
     * when the line is not SIGIL, not for us, or does not authenticate.
     */
    public static Opened open(String line, Keys keys) {
        String raw = line.trim();
        String found = findToken(raw);
        if (found != null) raw = found;
        String[] parts = raw.split("\\.");
        if (parts.length < 3 || !parts[0].startsWith("S1")) throw new IllegalArgumentException("not a SIGIL S1 message");
        String kind = parts[0].substring(2);
        String blob = parts[parts.length - 1];
        List<String> mid = new ArrayList<>(Arrays.asList(parts).subList(1, parts.length - 1));
        int fi = 1;
        int fn = 1;
        if (!mid.isEmpty() && mid.get(mid.size() - 1).matches("\\d+/\\d+")) {
            String[] f = mid.remove(mid.size() - 1).split("/");
            fi = Integer.parseInt(f[0]);
            fn = Integer.parseInt(f[1]);
            if (fi < 1 || fi > fn || fn > 99) throw new IllegalArgumentException("bad fragment index");
        }
        boolean compact = false;
        if (!mid.isEmpty() && mid.get(mid.size() - 1).equals("z")) {
            compact = true;
            mid.remove(mid.size() - 1);
        }
        try {
            switch (kind) {
                case "C":
                    return openCircle(keys, mid, blob, fi, fn, compact);
                case "K":
                    return openSignet(keys, mid, blob, fi, fn, compact);
                case "E":
                    return openEphemeral(keys, mid, blob, fi, fn, compact);
                default:
                    throw new IllegalArgumentException("unknown SIGIL kind " + kind);
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("did not authenticate (wrong key or altered token)");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed token: " + e.getMessage());
        }
    }

    private static Opened openCircle(Keys keys, List<String> mid, String blob, int fi, int fn, boolean compact) {
        if (mid.isEmpty()) throw new IllegalArgumentException("circle message missing slug");
        String slug = mid.get(0);
        byte[] data = b64d(blob);
        if (data.length < NONCE_LEN + TAG_LEN) throw new IllegalArgumentException("token is truncated");
        byte[] nonce = Arrays.copyOf(data, NONCE_LEN);
        byte[] ct = Arrays.copyOfRange(data, NONCE_LEN, data.length);
        boolean any = false;
        for (Circle c : keys.circles()) {
            if (!c.slug().equals(slug) && !c.name().equalsIgnoreCase(slug)) continue;
            any = true;
            byte[] key = circleKey(c.name(), c.passphrase());
            for (boolean z : compact ? new boolean[]{true, false} : new boolean[]{false}) {
                String aad = PROTOCOL + ".C." + c.name() + (z ? ".z" : "") + "." + fi + "/" + fn;
                try {
                    byte[] pt = gcm(false, key, nonce, utf8(aad), ct);
                    String text = z ? SigilCodebook.maybeExpand(pt) : new String(pt, StandardCharsets.UTF_8);
                    return new Opened("circle", c.name(), "", "", fi, fn, z, text);
                } catch (GeneralSecurityException ignored) {
                    // wrong circle sharing the slug, try the next
                }
            }
        }
        throw new IllegalArgumentException(any
                ? "could not open circle '" + slug + "' (wrong passphrase or altered token)"
                : "no circle with slug '" + slug + "' on this keyring");
    }

    private static Signet signetFor(Keys keys, String toShort) {
        for (Signet s : keys.signets()) if (s.shortId().equals(toShort)) return s;
        return null;
    }

    private static Opened openSignet(Keys keys, List<String> mid, String blob, int fi, int fn, boolean compact) throws GeneralSecurityException {
        if (mid.size() < 2) throw new IllegalArgumentException("directed message missing short-ids");
        String toShort = mid.get(0);
        String fromShort = mid.get(1);
        Signet local = signetFor(keys, toShort);
        if (local == null) throw new IllegalArgumentException("directed at signet " + toShort + ", which is not on this keyring");
        Contact contact = keys.contactByShort(fromShort);
        if (contact == null) throw new IllegalArgumentException("sender " + fromShort + " is not in your contacts; import their signet first");
        String z = compact ? ".z" : "";
        byte[] info = utf8(PROTOCOL + ".K." + fromShort + "." + toShort + z + "." + fi + "/" + fn);
        byte[] key = ecdhKey(privateKey(local.skPem()), publicKey(contact.pk()), cat(info, contact.pk(), local.pk()));
        byte[] data = b64d(blob);
        if (data.length < NONCE_LEN + TAG_LEN) throw new IllegalArgumentException("token is truncated");
        byte[] pt = gcm(false, key, Arrays.copyOf(data, NONCE_LEN), info, Arrays.copyOfRange(data, NONCE_LEN, data.length));
        String text = compact ? SigilCodebook.maybeExpand(pt) : new String(pt, StandardCharsets.UTF_8);
        return new Opened("signet", local.name(), contact.nameHint(), contact.shortId(), fi, fn, compact, text);
    }

    private static Opened openEphemeral(Keys keys, List<String> mid, String blob, int fi, int fn, boolean compact) throws GeneralSecurityException {
        if (mid.isEmpty()) throw new IllegalArgumentException("ephemeral message missing recipient short-id");
        String toShort = mid.get(0);
        Signet local = signetFor(keys, toShort);
        if (local == null) throw new IllegalArgumentException("ephemeral packet is for " + toShort + ", not on this keyring");
        byte[] data = b64d(blob);
        if (data.length < P256_COMPRESSED_LEN + NONCE_LEN + TAG_LEN) throw new IllegalArgumentException("ephemeral packet is truncated");
        byte[] ephRaw = Arrays.copyOf(data, P256_COMPRESSED_LEN);
        byte[] nonce = Arrays.copyOfRange(data, P256_COMPRESSED_LEN, P256_COMPRESSED_LEN + NONCE_LEN);
        byte[] ct = Arrays.copyOfRange(data, P256_COMPRESSED_LEN + NONCE_LEN, data.length);
        String z = compact ? ".z" : "";
        byte[] info = utf8(PROTOCOL + ".E." + toShort + z + "." + fi + "/" + fn);
        byte[] key = ecdhKey(privateKey(local.skPem()), publicKey(ephRaw), info);
        byte[] pt = gcm(false, key, nonce, info, ct);
        String text = compact ? SigilCodebook.maybeExpand(pt) : new String(pt, StandardCharsets.UTF_8);
        return new Opened("ephemeral", local.name(), "", "", fi, fn, compact, text);
    }

    /** Stitches opened fragments (already in order) the way sigil.py does. */
    public static String joinParts(List<Opened> ordered) {
        StringBuilder joined = new StringBuilder();
        for (Opened o : ordered) {
            String p = o.plaintext();
            if (joined.length() > 0 && !p.isEmpty() && o.compact()) {
                char a = joined.charAt(joined.length() - 1);
                char b = p.charAt(0);
                if ((Character.isLetterOrDigit(a) && Character.isLetterOrDigit(b)) || (".,:;?!".indexOf(a) >= 0 && Character.isLetterOrDigit(b))) {
                    joined.append(' ');
                }
            }
            joined.append(p);
        }
        return joined.toString();
    }

    // ------------------------------------------------------------------------------------------
    // Announcements (public lines that introduce a circle or a signet)
    // ------------------------------------------------------------------------------------------

    public static String announceCircle(Circle c) {
        return "S1+CIRCLE." + c.slug() + "." + c.name().replace('.', '_') + ".fp" + c.fingerprint();
    }

    public static String announceSignet(Signet s) {
        return "S1+PK." + s.name().replace('.', '_') + "." + s.shortId() + "." + b64e(s.pk());
    }

    /** Parses an S1+PK announcement into a contact, or returns null if the text has none. */
    public static Contact parseSignetAnnouncement(String text) {
        for (String w : text.replace(',', ' ').trim().split("\\s+")) {
            if (!w.startsWith("S1+PK.")) continue;
            String[] p = w.split("\\.");
            if (p.length < 4) continue;
            try {
                Contact c = newContact(p[1], p[1], b64d(p[3]));
                if (c.shortId().equals(p[2])) return c;
            } catch (RuntimeException ignored) {
                // fall through to the next word
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------
    // Spoken fingerprint and dice passphrases (public lexicon words)
    // ------------------------------------------------------------------------------------------

    public static String speakFingerprint(String fp) {
        List<String> pool = new ArrayList<>();
        for (String w : SigilCodebook.words()) {
            if (w.chars().allMatch(Character::isLetter) && w.length() >= 3 && w.length() <= 10 && !w.startsWith("pad")) pool.add(w);
            if (pool.size() >= 256) break;
        }
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(utf8(PROTOCOL + ".speak." + fp));
            return pool.get(d[0] & 0xFF) + " " + pool.get(d[1] & 0xFF);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String dicePhrase(int n) {
        List<String> pool = new ArrayList<>();
        for (String w : SigilCodebook.words()) {
            if (w.chars().allMatch(Character::isLetter) && w.length() >= 4 && w.length() <= 10 && !w.startsWith("pad")) pool.add(w);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(' ');
            sb.append(pool.get(RNG.nextInt(pool.size())));
        }
        return sb.toString();
    }
}
