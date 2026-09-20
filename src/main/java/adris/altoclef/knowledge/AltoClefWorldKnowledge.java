package adris.altoclef.knowledge;

import adris.altoclef.AltoClef;
import adris.altoclef.trackers.BlockScanner;
import adris.altoclef.trackers.CraftingRecipeTracker;
import adris.altoclef.trackers.EntityTracker;
import adris.altoclef.trackers.MiscBlockTracker;
import adris.altoclef.trackers.SimpleChunkTracker;
import adris.altoclef.trackers.storage.ItemStorageTracker;
import net.minecraft.block.Block;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * {@link WorldKnowledge} backed by the live {@link AltoClef} singleton container.
 * Trackers remain owned/constructed by AltoClef; this re-exposes them and wraps a
 * few high-value reads as {@link KnowledgeFact}s with a small in-memory cache.
 */
public final class AltoClefWorldKnowledge implements WorldKnowledge {

    static final String KEY_PLAYER_POS = "player.pos";
    static final String KEY_PLAYER_HEALTH = "player.health";
    static final String KEY_ENTITY_ALIVE_PREFIX = "entity.alive.";
    static final String KEY_NEAREST_ENTITY_PREFIX = "entity.nearest.";
    static final String KEY_BLOCK_PRESENT_PREFIX = "block.present.";
    static final String KEY_LAST_SEEN_BLOCK_PREFIX = "block.lastSeen.";

    private final AltoClef mod;
    private final KnowledgeFactCache factCache;

    public AltoClefWorldKnowledge(AltoClef mod) {
        this(mod, new KnowledgeFactCache());
    }

    /** Package-visible for tests that inject a cache. */
    AltoClefWorldKnowledge(AltoClef mod, KnowledgeFactCache factCache) {
        if (mod == null) {
            throw new IllegalArgumentException("mod");
        }
        if (factCache == null) {
            throw new IllegalArgumentException("factCache");
        }
        this.mod = mod;
        this.factCache = factCache;
    }

    /** Selected-signal fact cache (not a full world DB). */
    public KnowledgeFactCache getFactCache() {
        return factCache;
    }

    @Override
    public boolean inGame() {
        return AltoClef.inGame();
    }

    @Override
    public ClientPlayerEntity getPlayer() {
        return mod.getPlayer();
    }

    @Override
    public ClientWorld getWorld() {
        return mod.getWorld();
    }

    @Override
    public ItemStorageTracker getItemStorage() {
        return mod.getItemStorage();
    }

    @Override
    public EntityTracker getEntityTracker() {
        return mod.getEntityTracker();
    }

    @Override
    public BlockScanner getBlockScanner() {
        return mod.getBlockScanner();
    }

    @Override
    public SimpleChunkTracker getChunkTracker() {
        return mod.getChunkTracker();
    }

    @Override
    public MiscBlockTracker getMiscBlockTracker() {
        return mod.getMiscBlockTracker();
    }

    @Override
    public CraftingRecipeTracker getCraftingRecipeTracker() {
        return mod.getCraftingRecipeTracker();
    }

    @Override
    public long currentTick() {
        ClientWorld world = getWorld();
        if (world != null) {
            return world.getTime();
        }
        return 0L;
    }

    @Override
    public KnowledgeFact<Vec3d> playerPositionFact() {
        long tick = currentTick();
        ClientPlayerEntity player = getPlayer();
        if (player != null) {
            KnowledgeFact<Vec3d> live = KnowledgeFact.of(player.getPos(), tick, 1.0, KnowledgeSource.SENSOR);
            factCache.put(KEY_PLAYER_POS, live);
            return live;
        }
        return memoryOrUnknown(KEY_PLAYER_POS, tick);
    }

    @Override
    public KnowledgeFact<Float> playerHealthFact() {
        long tick = currentTick();
        ClientPlayerEntity player = getPlayer();
        if (player != null) {
            KnowledgeFact<Float> live = KnowledgeFact.of(player.getHealth(), tick, 1.0, KnowledgeSource.SENSOR);
            factCache.put(KEY_PLAYER_HEALTH, live);
            return live;
        }
        return memoryOrUnknown(KEY_PLAYER_HEALTH, tick);
    }

    @Override
    public KnowledgeFact<Boolean> entityAliveFact(Entity entity) {
        long tick = currentTick();
        if (entity == null) {
            return KnowledgeFact.unknown();
        }
        String key = KEY_ENTITY_ALIVE_PREFIX + entity.getId();
        boolean alive = entity.isAlive();
        // Dead is a high-confidence SENSOR observation; alive likewise.
        KnowledgeFact<Boolean> live = KnowledgeFact.of(alive, tick, 1.0, KnowledgeSource.SENSOR);
        factCache.put(key, live);
        return live;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public KnowledgeFact<Entity> nearestEntityFact(Class... entityTypes) {
        long tick = currentTick();
        String key = KEY_NEAREST_ENTITY_PREFIX + typeKey(entityTypes);
        EntityTracker tracker = getEntityTracker();
        if (tracker != null && entityTypes != null && entityTypes.length > 0) {
            Optional<Entity> nearest = tracker.getClosestEntity(entityTypes);
            if (nearest.isPresent() && nearest.get().isAlive()) {
                KnowledgeFact<Entity> live = KnowledgeFact.of(nearest.get(), tick, 1.0, KnowledgeSource.SCANNER);
                factCache.put(key, live);
                return live;
            }
        }
        KnowledgeFact<Entity> mem = memoryOrUnknown(key, tick);
        if (mem.isKnown() && mem.getValue() != null && !mem.getValue().isAlive()) {
            factCache.remove(key);
            return KnowledgeFact.unknown();
        }
        return mem;
    }

    @Override
    public KnowledgeFact<Boolean> blockPresentFact(BlockPos pos, Block... expected) {
        long tick = currentTick();
        if (pos == null) {
            return KnowledgeFact.unknown();
        }
        String key = KEY_BLOCK_PRESENT_PREFIX + pos.toShortString();
        ClientWorld world = getWorld();
        if (world != null) {
            Block at = world.getBlockState(pos).getBlock();
            boolean match = expected == null || expected.length == 0;
            if (!match) {
                for (Block b : expected) {
                    if (b != null && b == at) {
                        match = true;
                        break;
                    }
                }
            } else {
                // No filter: "present" means non-air
                match = !world.getBlockState(pos).isAir();
            }
            KnowledgeFact<Boolean> live = KnowledgeFact.of(match, tick, 1.0, KnowledgeSource.SENSOR);
            factCache.put(key, live);
            return live;
        }
        return memoryOrUnknown(key, tick);
    }

    @Override
    public KnowledgeFact<BlockPos> lastSeenBlockFact(Block... blocks) {
        long tick = currentTick();
        String key = KEY_LAST_SEEN_BLOCK_PREFIX + blockKey(blocks);
        BlockScanner scanner = getBlockScanner();
        if (scanner != null && blocks != null && blocks.length > 0) {
            Optional<BlockPos> nearest = scanner.getNearestBlock(blocks);
            if (nearest.isPresent()) {
                KnowledgeFact<BlockPos> live = KnowledgeFact.of(nearest.get(), tick, 1.0, KnowledgeSource.SCANNER);
                factCache.put(key, live);
                return live;
            }
        }
        return memoryOrUnknown(key, tick);
    }

    private <T> KnowledgeFact<T> memoryOrUnknown(String key, long nowTick) {
        KnowledgeFact<T> cached = factCache.get(key);
        if (!cached.isKnown()) {
            return KnowledgeFact.unknown();
        }
        // Serve as MEMORY with decayed confidence (half-life 100 ticks ≈ 5s).
        return KnowledgeFact.of(
                cached.getValue(),
                cached.getObservedTick(),
                cached.decayedConfidence(nowTick, 100L),
                KnowledgeSource.MEMORY
        );
    }

    @SuppressWarnings("rawtypes")
    private static String typeKey(Class... types) {
        if (types == null || types.length == 0) {
            return "any";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(types[i] != null ? types[i].getName() : "null");
        }
        return sb.toString();
    }

    private static String blockKey(Block... blocks) {
        if (blocks == null || blocks.length == 0) {
            return "any";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.length; i++) {
            if (i > 0) sb.append(',');
            // toString is stable enough for cache keys across versions
            sb.append(blocks[i] != null ? blocks[i].toString() : "null");
        }
        return sb.toString();
    }
}
