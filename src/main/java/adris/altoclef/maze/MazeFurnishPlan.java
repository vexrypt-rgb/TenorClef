package adris.altoclef.maze;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * TenorClef's copy of an Ostinato {@code baritone.api.maze.MazeFurnishJob}, with no Ostinato or Minecraft types, so it
 * builds on every version line (only the 26.3 Ostinato jar has the maze API) and can be unit tested.
 */
public final class MazeFurnishPlan {

    public enum Kind { LIGHT, CONTAINER, FILL, SEAL }

    public record Item(String id, int count) {}

    public record Step(Kind kind, int x, int y, int z, List<String> blockIds, List<Item> contents) {
        public Step {
            blockIds = List.copyOf(blockIds);
            contents = List.copyOf(contents);
        }
    }

    public final String summary;
    public final boolean survival;
    public final List<Step> steps;
    /** Ostinato: run100. The room's carved cells as x,y,z triples (empty from an older Ostinato). */
    public final int[] airCells;

    public MazeFurnishPlan(String summary, boolean survival, List<Step> steps) {
        this(summary, survival, steps, new int[0]);
    }

    public MazeFurnishPlan(String summary, boolean survival, List<Step> steps, int[] airCells) {
        this.summary = summary;
        this.survival = survival;
        this.steps = List.copyOf(steps);
        this.airCells = airCells.clone();
    }

    /**
     * What to gather: the first obtainable choice of every placed block, plus every fill item that can be obtained.
     * Loot TenorClef cannot get (a music disc, a sherd) is dropped here and skipped when filling, so a chest still gets
     * the rest of its roll.
     */
    public Map<String, Integer> materials(Predicate<String> obtainable) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Step step : steps) {
            if (step.kind() == Kind.FILL) {
                for (Item item : step.contents()) {
                    if (obtainable.test(item.id())) {
                        out.merge(item.id(), item.count(), Integer::sum);
                    }
                }
            } else {
                String pick = pick(step.blockIds(), obtainable);
                if (pick != null) {
                    out.merge(pick, 1, Integer::sum);
                }
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Ostinato: lights and the container, then the fill, then seals. Ceiling mining drops cobble before
     * the barrel is filled, and the shaft is still open so the bot can reach it. Seals close the room last.
     */
    public List<Step> placeOrder() {
        List<Step> place = new ArrayList<>();
        List<Step> fills = new ArrayList<>();
        List<Step> seals = new ArrayList<>();
        for (Step step : steps) {
            switch (step.kind()) {
                case FILL -> fills.add(step);
                case SEAL -> seals.add(step);
                default -> place.add(step);
            }
        }
        place.addAll(fills);
        place.addAll(seals);
        return place;
    }

    /**
     * Ostinato: run51. A deep-theme room shifted into stone (floor y=73) still listed polished_deepslate.
     * That block only generates below y=8, so the gather walked to bedrock and the 90s abort closed the client.
     * Above that band, every deepslate id becomes cobblestone, which the dig already drops from the shell.
     */
    public static MazeFurnishPlan stoneIfAboveDeepslate(MazeFurnishPlan plan) {
        int cladY = Integer.MAX_VALUE;
        boolean deep = false;
        for (Step s : plan.steps) {
            if (s.kind() != Kind.LIGHT && s.kind() != Kind.SEAL) continue;
            // Only the first id. A later cobbled_deepslate fallback on a yellow-office seal is not a deepslate room.
            if (!s.blockIds().isEmpty() && s.blockIds().get(0).contains("deepslate")) deep = true;
            if (s.kind() == Kind.LIGHT) cladY = Math.min(cladY, s.y());
        }
        if (!deep || cladY <= 8) return plan;
        List<Step> steps = new ArrayList<>();
        for (Step s : plan.steps) {
            List<String> ids = new ArrayList<>();
            for (String id : s.blockIds()) {
                String use = id.contains("deepslate") ? "cobblestone" : id;
                if (!ids.contains(use)) ids.add(use);
            }
            steps.add(new Step(s.kind(), s.x(), s.y(), s.z(), ids, s.contents()));
        }
        System.out.println("MAZETEST FURNISH stone-layer y=" + cladY + " deepslate -> cobblestone");
        return new MazeFurnishPlan(plan.summary, plan.survival, steps, plan.airCells);
    }

    /** First id that passes, or null. */
    public static String pick(List<String> ids, Predicate<String> ok) {
        for (String id : ids) {
            if (ok.test(id)) {
                return id;
            }
        }
        return null;
    }

    /**
     * Reads an Ostinato {@code MazeFurnishJob} by reflection (its record accessors and public fields). Ostinato's API is
     * not remapped, so the names hold in a release jar.
     */
    public static MazeFurnishPlan fromOstinato(Object job) throws ReflectiveOperationException {
        Class<?> c = job.getClass();
        boolean survival = c.getField("survival").getBoolean(job);
        String summary = String.valueOf(c.getMethod("summary").invoke(job));
        List<Step> steps = new ArrayList<>();
        for (Object s : (List<?>) c.getField("steps").get(job)) {
            Class<?> sc = s.getClass();
            Kind kind = Kind.valueOf(((Enum<?>) call(sc, s, "kind")).name());
            List<String> ids = new ArrayList<>();
            for (Object id : (List<?>) call(sc, s, "blockIds")) {
                ids.add(String.valueOf(id));
            }
            List<Item> contents = new ArrayList<>();
            for (Object it : (List<?>) call(sc, s, "contents")) {
                Class<?> ic = it.getClass();
                contents.add(new Item(String.valueOf(call(ic, it, "itemId")), (Integer) call(ic, it, "count")));
            }
            steps.add(new Step(kind, (Integer) call(sc, s, "x"), (Integer) call(sc, s, "y"), (Integer) call(sc, s, "z"), ids, contents));
        }
        int[] air = new int[0];
        try { air = (int[]) c.getField("airCells").get(job); } catch (NoSuchFieldException ignored) {} // Ostinato: run100
        return new MazeFurnishPlan(summary, survival, steps, air);
    }

    private static Object call(Class<?> c, Object o, String name) throws ReflectiveOperationException {
        Method m = c.getMethod(name);
        m.setAccessible(true);
        return m.invoke(o);
    }
}
