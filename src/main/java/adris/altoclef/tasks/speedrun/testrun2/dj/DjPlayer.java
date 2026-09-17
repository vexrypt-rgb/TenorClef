package adris.altoclef.tasks.speedrun.testrun2.dj;

import adris.altoclef.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Client-side note-block player (you hear it; no world piano). */
public final class DjPlayer {

    private static NbsSong song;
    private static int index;
    private static double songTick;
    private static boolean playing;
    private static String title = "-";

    private DjPlayer() {}

    public static boolean playing() {
        return playing;
    }

    public static String title() {
        return title;
    }

    public static void stop() {
        playing = false;
        song = null;
        index = 0;
        songTick = 0;
        title = "-";
    }

    public static void play(NbsSong s) {
        song = s;
        index = 0;
        songTick = 0;
        playing = s != null && !s.notes.isEmpty();
        title = s == null ? "-" : s.name;
        Debug.logMessage("DJ play " + title + " notes=" + (s == null ? 0 : s.notes.size())
                + " tps=" + (s == null ? 0 : s.ticksPerSecond));
    }

    public static void playFile(Path file) {
        try {
            play(NbsSong.load(file));
        } catch (Throwable t) {
            Debug.logWarning("DJ nbs " + t.getClass().getSimpleName() + " " + t.getMessage());
            play(NbsSong.builtinScale());
        }
    }

    public static void playFirstIn(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    List<Path> nbs = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".nbs"))
                            .toList();
                    if (!nbs.isEmpty()) {
                        playFile(nbs.get(0));
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {}
        play(NbsSong.builtinScale());
    }

    public static void tick() {
        if (!playing || song == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        songTick += song.ticksPerSecond / 20.0;
        while (index < song.notes.size() && song.notes.get(index).tick <= songTick + 0.001) {
            hit(mc, song.notes.get(index));
            index++;
        }
        if (index >= song.notes.size()) {
            Debug.logMessage("DJ finished " + title);
            stop();
        }
    }

    private static void hit(MinecraftClient mc, NbsSong.Note n) {
        try {
            SoundEvent ev = instrument(n.instrument);
            int nb = n.key - 33;
            if (nb < 0) nb = 0;
            if (nb > 24) nb = 24;
            float pitch = (float) Math.pow(2.0, (nb - 12) / 12.0);
            BlockPos pos = mc.player.getBlockPos();
            try {
                mc.getSoundManager().play(PositionedSoundInstance.master(ev, pitch));
            } catch (Throwable t) {
                mc.world.playSound(mc.player, pos, ev, net.minecraft.sound.SoundCategory.RECORDS, 3f, pitch);
            }
        } catch (Throwable ignored) {}
    }

    private static SoundEvent instrument(int id) {
        Object ref = switch (id) {
            case 1 -> SoundEvents.BLOCK_NOTE_BLOCK_BASS;
            case 2 -> SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM;
            case 3 -> SoundEvents.BLOCK_NOTE_BLOCK_SNARE;
            case 4 -> SoundEvents.BLOCK_NOTE_BLOCK_HAT;
            case 5 -> SoundEvents.BLOCK_NOTE_BLOCK_GUITAR;
            case 6 -> SoundEvents.BLOCK_NOTE_BLOCK_FLUTE;
            case 7 -> SoundEvents.BLOCK_NOTE_BLOCK_BELL;
            case 8 -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME;
            case 9 -> SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE;
            case 10 -> SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
            case 11 -> SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL;
            case 12 -> SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO;
            case 13 -> SoundEvents.BLOCK_NOTE_BLOCK_BIT;
            case 14 -> SoundEvents.BLOCK_NOTE_BLOCK_BANJO;
            case 15 -> SoundEvents.BLOCK_NOTE_BLOCK_PLING;
            default -> SoundEvents.BLOCK_NOTE_BLOCK_HARP;
        };
        if (ref instanceof SoundEvent se) return se;
        try {
            Object v = ref.getClass().getMethod("value").invoke(ref);
            if (v instanceof SoundEvent se) return se;
        } catch (Throwable ignored) {}
        try {
            Object v = ref.getClass().getMethod("comp_349").invoke(ref);
            if (v instanceof SoundEvent se) return se;
        } catch (Throwable ignored) {}
        return SoundEvent.of(net.minecraft.util.Identifier.of("minecraft", "block.note_block.harp"));
    }
}

