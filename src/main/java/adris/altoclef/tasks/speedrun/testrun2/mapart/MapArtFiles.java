package adris.altoclef.tasks.speedrun.testrun2.mapart;

import adris.altoclef.tasks.speedrun.testrun2.util.GameFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class MapArtFiles {

    private MapArtFiles() {}

    public static Path root() {
        return GameFiles.dir().resolve("mapart");
    }

    public static Path inbox() {
        return root().resolve("inbox");
    }

    public static Path out() {
        return root().resolve("out");
    }

    public static void ensure() {
        try {
            Files.createDirectories(inbox());
            Files.createDirectories(out());
        } catch (Throwable ignored) {}
    }

    public static Path copyInbox(Path src) {
        ensure();
        try {
            String name = src.getFileName().toString();
            Path dest = inbox().resolve(name);
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            return dest;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Path latestOut() {
        ensure();
        try {
            Path last = null;
            long best = -1;
            try (var stream = Files.list(out())) {
                for (Path p : (Iterable<Path>) stream::iterator) {
                    if (!Files.isDirectory(p)) continue;
                    long t = Files.getLastModifiedTime(p).toMillis();
                    if (t > best) {
                        best = t;
                        last = p;
                    }
                }
            }
            return last;
        } catch (Throwable t) {
            return null;
        }
    }
}
