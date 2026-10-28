package adris.altoclef.allblocks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ostinato: #allblocks. What a run has done in one world, saved as JSON after every change so a crash or relaunch
 * continues from the next missing item. {@code <game dir>/altoclef/allblocks/<world>.json}.
 */
public final class AllBlocksProgress {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Failure {
        public int attempts;
        public String last;
        /** True once the item failed every attempt; reported as stuck until a reset or "start retry". */
        public boolean stuck;
    }

    public int version = 1;
    public String world;
    /** The origin block (feet position at the first start). Null until the first start. */
    public int[] origin;
    /** Chest index each candidate item is stored in, assigned once in plan order. */
    public Map<String, Integer> chestOf = new LinkedHashMap<>();
    /** Items deposited and seen in their chest. */
    public Map<String, Integer> stored = new LinkedHashMap<>();
    /** Grid cell each block was placed in. */
    public Map<String, Integer> placed = new LinkedHashMap<>();
    /** Blocks the grid does not take, with the reason. */
    public Map<String, String> placeSkipped = new LinkedHashMap<>();
    /** Grid cells that cannot be used (water in the cell, ...). */
    public Map<Integer, String> badCells = new LinkedHashMap<>();
    public Map<String, Failure> failures = new LinkedHashMap<>();
    /** Chest indexes whose label sign is written. */
    public Map<Integer, Boolean> labeled = new LinkedHashMap<>();
    public int nextCell;
    public long startedAt;
    public long gatherTicks;

    public static Path file(Path gameDir, String world) {
        return gameDir.resolve("altoclef").resolve("allblocks").resolve(world.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }

    public static AllBlocksProgress load(Path file, String world) {
        try {
            if (Files.exists(file)) {
                AllBlocksProgress p = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), AllBlocksProgress.class);
                if (p != null) {
                    p.fillNulls();
                    return p;
                }
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("[allblocks] could not read " + file + ": " + e);
        }
        AllBlocksProgress p = new AllBlocksProgress();
        p.world = world;
        p.startedAt = System.currentTimeMillis();
        return p;
    }

    private void fillNulls() {
        if (chestOf == null) chestOf = new LinkedHashMap<>();
        if (stored == null) stored = new LinkedHashMap<>();
        if (placed == null) placed = new LinkedHashMap<>();
        if (placeSkipped == null) placeSkipped = new LinkedHashMap<>();
        if (badCells == null) badCells = new LinkedHashMap<>();
        if (failures == null) failures = new LinkedHashMap<>();
        if (labeled == null) labeled = new LinkedHashMap<>();
    }

    /** Writes to a temp file and moves it over, so a crash mid-write keeps the previous save. */
    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            System.out.println("[allblocks] could not save " + file + ": " + e);
        }
    }

    public Failure failure(String id) {
        return failures.computeIfAbsent(id, k -> new Failure());
    }
}
