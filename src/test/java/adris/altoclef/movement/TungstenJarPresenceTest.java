package adris.altoclef.movement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Offline evidence that a 1.16.1 Tungsten jar staged for Gradle contains the
 * classes TungstenBridge reflects on. Run via main (no JUnit required):
 *   javac ... TungstenJarPresenceTest.java && java ... TungstenJarPresenceTest
 */
public class TungstenJarPresenceTest {

    private static final String[] REQUIRED = {
            "kaptainwutax/tungsten/TungstenMod.class",
            "kaptainwutax/tungsten/TungstenModDataContainer.class",
            "kaptainwutax/tungsten/path/PathFinder.class",
            "kaptainwutax/tungsten/path/PathExecutor.class",
            "kaptainwutax/tungsten/task/FollowEntityTask.class"
    };

    public static void main(String[] args) throws Exception {
        List<Path> jars = find1161Jars();
        if (jars.isEmpty()) {
            throw new AssertionError("No tungsten*1.16.1*.jar under libs/ or vendor/tungsten-1.16.1/build/libs");
        }
        Path jar = jars.get(0);
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String req : REQUIRED) {
                ZipEntry e = zip.getEntry(req);
                if (e == null) {
                    throw new AssertionError(jar.getFileName() + " missing " + req);
                }
                System.out.println("OK " + req);
            }
        }
        System.out.println("PASS " + jar);
    }

    private static List<Path> find1161Jars() throws IOException {
        List<Path> out = new ArrayList<>();
        Path cur = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            collect(cur.resolve("libs"), out);
            collect(cur.resolve("vendor/tungsten-1.16.1/build/libs"), out);
            cur = cur.getParent();
        }
        return out;
    }

    private static void collect(Path dir, List<Path> out) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("tungsten") && n.endsWith(".jar")
                        && (n.contains("1.16.1") || n.contains("1161"))
                        && !n.contains("sources") && !n.contains("dev");
            }).forEach(out::add);
        }
    }
}
