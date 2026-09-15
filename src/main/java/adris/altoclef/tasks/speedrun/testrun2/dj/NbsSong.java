package adris.altoclef.tasks.speedrun.testrun2.dj;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** OpenNBS / classic .nbs note list. */
public final class NbsSong {

    public final String name;
    public final float ticksPerSecond;
    public final List<Note> notes;

    public NbsSong(String name, float ticksPerSecond, List<Note> notes) {
        this.name = name;
        this.ticksPerSecond = ticksPerSecond <= 0 ? 10f : ticksPerSecond;
        this.notes = notes;
    }

    public static final class Note {
        public final int tick;
        public final int instrument;
        public final int key;

        public Note(int tick, int instrument, int key) {
            this.tick = tick;
            this.instrument = instrument;
            this.key = key;
        }
    }

    public static NbsSong load(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file);
             DataInputStream d = new DataInputStream(in)) {
            int first = readUShort(d);
            int version = 0;
            if (first == 0) {
                version = d.readByte() & 0xff;
                d.readByte(); // vanilla instrument count
                if (version >= 3) readUShort(d); // song length
            }
            readUShort(d); // layers
            String name = readNbsString(d);
            readNbsString(d); // author
            readNbsString(d); // orig author
            readNbsString(d); // desc
            int tempo = readUShort(d);
            // skip auto / time sig / minutes spent / left clicks / right / loops...
            d.readByte();
            d.readByte();
            readUShort(d);
            readUInt(d);
            readUInt(d);
            readUInt(d);
            readUInt(d);
            readUInt(d);
            readNbsString(d);
            if (version >= 4) {
                d.readByte();
                d.readByte();
                readUShort(d);
            }
            List<Note> notes = new ArrayList<>();
            int tick = -1;
            while (true) {
                int jump = readUShort(d);
                if (jump == 0) break;
                tick += jump;
                int layer = -1;
                while (true) {
                    int layerJump = readUShort(d);
                    if (layerJump == 0) break;
                    layer += layerJump;
                    int inst = d.readByte() & 0xff;
                    int key = d.readByte() & 0xff;
                    if (version >= 4) {
                        d.readByte(); // velocity
                        d.readByte(); // panning
                        readShort(d); // pitch
                    }
                    notes.add(new Note(tick, inst, key));
                }
            }
            float tps = tempo / 100f;
            if (tps < 0.5f) tps = 10f;
            if (name == null || name.isBlank()) name = file.getFileName().toString();
            return new NbsSong(name, tps, notes);
        }
    }

    /** Short C scale so DJ works with an empty folder. */
    public static NbsSong builtinScale() {
        List<Note> n = new ArrayList<>();
        int[] keys = {33, 35, 37, 38, 40, 42, 44, 45};
        int t = 0;
        for (int k : keys) {
            n.add(new Note(t, 0, k));
            t += 2;
        }
        return new NbsSong("scale", 8f, n);
    }

    private static int readUShort(DataInputStream d) throws Exception {
        int a = d.readUnsignedByte();
        int b = d.readUnsignedByte();
        return a | (b << 8);
    }

    private static int readShort(DataInputStream d) throws Exception {
        int u = readUShort(d);
        return u >= 32768 ? u - 65536 : u;
    }

    private static long readUInt(DataInputStream d) throws Exception {
        long a = d.readUnsignedByte();
        long b = d.readUnsignedByte();
        long c = d.readUnsignedByte();
        long e = d.readUnsignedByte();
        return a | (b << 8) | (c << 16) | (e << 24);
    }

    private static String readNbsString(DataInputStream d) throws Exception {
        int len = (int) readUInt(d);
        if (len <= 0 || len > 1_000_000) return "";
        byte[] buf = new byte[len];
        d.readFully(buf);
        return new String(buf, StandardCharsets.UTF_8);
    }
}
