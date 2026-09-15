package adris.altoclef.tasks.speedrun.testrun2.mapart;

import net.minecraft.item.Item;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MapArtGrid {

    public final int w, h;
    public final String[][] id;
    public final Map<String, Integer> counts = new LinkedHashMap<>();

    public MapArtGrid(int w, int h, String[][] id) {
        this.w = w;
        this.h = h;
        this.id = id;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                counts.merge(id[y][x], 1, Integer::sum);
            }
        }
    }

    public static MapArtGrid load(Path dir) {
        try {
            Path art = dir.resolve("art.txt");
            if (!Files.exists(art)) return null;
            var lines = Files.readAllLines(art, StandardCharsets.UTF_8);
            if (lines.isEmpty()) return null;
            String[] hw = lines.get(0).trim().split("\\s+");
            int w = Integer.parseInt(hw[0]);
            int h = Integer.parseInt(hw[1]);
            String[][] id = new String[h][w];
            for (int y = 0; y < h; y++) {
                String[] cells = lines.get(y + 1).trim().split("\\s+");
                for (int x = 0; x < w; x++) {
                    id[y][x] = x < cells.length ? cells[x] : "stone";
                }
            }
            return new MapArtGrid(w, h, id);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Item itemOf(String id) {
        for (MapPalette.Swatch s : MapPalette.ALL) {
            if (s.id.equals(id)) return s.item;
        }
        return null;
    }
}
