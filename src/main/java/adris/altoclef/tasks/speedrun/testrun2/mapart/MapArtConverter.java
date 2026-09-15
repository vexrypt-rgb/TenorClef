package adris.altoclef.tasks.speedrun.testrun2.mapart;

import adris.altoclef.Debug;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** PNG/JPG → 128×128 (or tiled) block grid + material counts. */
public final class MapArtConverter {

    public static final int MAP = 128;

    private MapArtConverter() {}

    public static Path convert(Path image) {
        return convert(image, MAP, MAP);
    }

    public static Path convert(Path image, int w, int h) {
        MapArtFiles.ensure();
        if (image == null || !Files.exists(image)) {
            Debug.logWarning("MAPART no image");
            return null;
        }
        BufferedImage src;
        try {
            src = ImageIO.read(image.toFile());
        } catch (Throwable t) {
            Debug.logWarning("MAPART read fail " + t.getMessage());
            return null;
        }
        if (src == null) {
            Debug.logWarning("MAPART ImageIO returned null");
            return null;
        }
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        String[][] grid = new String[h][w];
        int[][] r = new int[h][w];
        int[][] gc = new int[h][w];
        int[][] b = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = scaled.getRGB(x, y);
                r[y][x] = (rgb >> 16) & 255;
                gc[y][x] = (rgb >> 8) & 255;
                b[y][x] = rgb & 255;
            }
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rr = clamp(r[y][x]);
                int gg = clamp(gc[y][x]);
                int bb = clamp(b[y][x]);
                MapPalette.Swatch sw = MapPalette.nearest(rr, gg, bb);
                grid[y][x] = sw.id;
                counts.merge(sw.id, 1, Integer::sum);
                int er = rr - sw.r;
                int eg = gg - sw.g;
                int eb = bb - sw.b;
                diffuse(r, gc, b, x + 1, y, er, eg, eb, 7.0 / 16);
                diffuse(r, gc, b, x - 1, y + 1, er, eg, eb, 3.0 / 16);
                diffuse(r, gc, b, x, y + 1, er, eg, eb, 5.0 / 16);
                diffuse(r, gc, b, x + 1, y + 1, er, eg, eb, 1.0 / 16);
            }
        }

        String stem = image.getFileName().toString().replaceAll("\\.[^.]+$", "");
        stem = stem.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path dir = MapArtFiles.out().resolve(stem);
        try {
            Files.createDirectories(dir);
            StringBuilder art = new StringBuilder();
            art.append(w).append(' ').append(h).append('\n');
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (x > 0) art.append(' ');
                    art.append(grid[y][x]);
                }
                art.append('\n');
            }
            Files.writeString(dir.resolve("art.txt"), art.toString(), StandardCharsets.UTF_8);
            StringBuilder mat = new StringBuilder();
            int total = 0;
            for (var e : counts.entrySet()) {
                mat.append(e.getKey()).append(' ').append(e.getValue()).append('\n');
                total += e.getValue();
            }
            mat.append("# total ").append(total).append(" stacks~").append((total + 63) / 64).append('\n');
            Files.writeString(dir.resolve("materials.txt"), mat.toString(), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("source.txt"), image.toAbsolutePath() + "\n", StandardCharsets.UTF_8);
            Debug.logMessage("MAPART wrote " + dir.toAbsolutePath() + " " + w + "x" + h
                    + " kinds=" + counts.size() + " blocks=" + total);
            return dir;
        } catch (Throwable t) {
            Debug.logWarning("MAPART write " + t.getMessage());
            return null;
        }
    }

    public static Path convertInboxAll() {
        MapArtFiles.ensure();
        Path last = null;
        try (var stream = Files.list(MapArtFiles.inbox())) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp"))) {
                    continue;
                }
                Path out = convert(p);
                if (out != null) last = out;
            }
        } catch (Throwable t) {
            Debug.logWarning("MAPART inbox " + t.getMessage());
        }
        return last;
    }

    private static void diffuse(int[][] r, int[][] g, int[][] b, int x, int y,
                                int er, int eg, int eb, double w) {
        if (y < 0 || y >= r.length || x < 0 || x >= r[0].length) return;
        r[y][x] += (int) Math.round(er * w);
        g[y][x] += (int) Math.round(eg * w);
        b[y][x] += (int) Math.round(eb * w);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
