package adris.altoclef.benchmark;

//#if MC <= 11601
//$$ import adris.altoclef.AltoClef;
//$$ import adris.altoclef.Debug;
//$$ import adris.altoclef.TaskCatalogue;
//$$ import adris.altoclef.eventbus.EventBus;
//$$ import adris.altoclef.eventbus.Subscription;
//$$ import adris.altoclef.eventbus.events.BlockPlaceEvent;
//$$ import adris.altoclef.fugue.FugueCoordinator;
//$$ import adris.altoclef.fugue.FugueTask;
//$$ import adris.altoclef.tasksystem.Task;
//$$ import adris.altoclef.tasksystem.TaskFailure;
//$$ import adris.altoclef.tasksystem.TaskResult;
//$$ import adris.altoclef.util.WaterEscapeTrace;
//$$ import baritone.api.BaritoneAPI;
//$$ import net.minecraft.block.Blocks;
//$$ import net.minecraft.client.MinecraftClient;
//$$ import net.minecraft.item.Item;
//$$ import net.minecraft.item.ItemStack;
//$$ import net.minecraft.item.Items;
//$$ import net.minecraft.server.network.ServerPlayerEntity;
//$$ import net.minecraft.server.world.ServerWorld;
//$$ import net.minecraft.util.math.BlockPos;
//$$ import net.minecraft.util.math.Box;
//$$ import net.minecraft.world.Heightmap;
//$$
//$$ import java.io.PrintWriter;
//$$ import java.nio.file.Files;
//$$ import java.nio.file.Path;
//$$ import java.nio.file.Paths;
//$$ import java.time.LocalDateTime;
//$$ import java.time.format.DateTimeFormatter;
//$$ import java.util.ArrayList;
//$$ import java.util.LinkedHashMap;
//$$ import java.util.List;
//$$ import java.util.Locale;
//$$ import java.util.Map;
//$$ import java.util.concurrent.atomic.AtomicInteger;
//$$
//$$ /**
//$$  * Live A/B harness for Fugue v1 (1.16.1 SIM target). One arm per client launch, so both arms see identical,
//$$  * untouched terrain: with {@code -Dtenorclef.seed} pinned, trial N of either arm runs at the same spot (spots are
//$$  * picked by a deterministic rule from the world spawn, 512 blocks apart, so no trial reuses another's area).
//$$  *
//$$  * <p>{@code @fuguebench <stock|fugue> [empty:3,partial:1,full:1,steal:1,cancel:1]}. Every trial: Peaceful, morning,
//$$  * fed and healed, inventory set by the scenario, dropped items cleared, block scanner reset. The objective is an
//$$  * iron pickaxe; stock = {@code TaskCatalogue.getItemTask("iron_pickaxe", 1)} (what {@code @get} runs), fugue =
//$$  * {@link FugueTask#live}. Success = task result SUCCESS <b>and</b> the harness independently sees an iron pickaxe in
//$$  * the inventory. Every trial is logged (FUGUEBENCH TRIAL ...) and written to {@code fuguebench/*.csv}; none are
//$$  * dropped. {@code -Dtenorclef.fuguebench.exit=true} closes the client afterwards.
//$$  */
//$$ public final class FugueBench {
//$$
//$$     private static volatile Thread running;
//$$     private static final String[] LEFTOVER = {"log", "planks", "stick", "cobblestone", "coal", "raw_iron", "iron_ingot",
//$$             "crafting_table", "furnace", "wooden_pickaxe", "stone_pickaxe", "iron_pickaxe"};
//$$     private static final int SPACING = 512;
//$$
//$$     private FugueBench() {}
//$$
//$$     public static boolean isRunning() {
//$$         return running != null && running.isAlive();
//$$     }
//$$
//$$     public static String start(String arm, String plan) {
//$$         if (isRunning()) return "FUGUEBENCH already running";
//$$         MinecraftClient mc = MinecraftClient.getInstance();
//$$         if (mc.player == null || mc.world == null || mc.getServer() == null) return "FUGUEBENCH needs a loaded singleplayer world";
//$$         String a = arm == null ? "" : arm.toLowerCase(Locale.ROOT);
//$$         if (!a.equals("stock") && !a.equals("fugue") && !a.equals("fuguer")) return "FUGUEBENCH arm must be stock, fugue or fuguer";
//$$         String p = plan == null || plan.equals("-") ? "empty:3,partial:1,full:1,steal:1,cancel:1" : plan;
//$$         Thread t = new Thread(() -> {
//$$             try {
//$$                 run(mc, a, p);
//$$             } catch (Throwable e) {
//$$                 Debug.logHarness("FUGUEBENCH failed: " + e);
//$$                 e.printStackTrace();
//$$             } finally {
//$$                 running = null;
//$$                 if (Boolean.getBoolean("tenorclef.fuguebench.exit")) {
//$$                     Debug.logHarness("FUGUEBENCH exit requested");
//$$                     mc.execute(mc::scheduleStop);
//$$                 }
//$$             }
//$$         }, "FugueBench");
//$$         t.setDaemon(true);
//$$         running = t;
//$$         t.start();
//$$         return "FUGUEBENCH " + a + " " + p + " started";
//$$     }
//$$
//$$     private static final class Snap {
//$$         boolean finished, stopped, dead;
//$$         TaskResult result;
//$$         TaskFailure failure;
//$$         double x, y, z;
//$$         int cobble, pick;
//$$     }
//$$
//$$     private static void run(MinecraftClient mc, String arm, String plan) throws Exception {
//$$         List<String> trials = new ArrayList<>();
//$$         for (String part : plan.split(",")) {
//$$             String[] kv = part.trim().split(":");
//$$             int reps = kv.length > 1 ? Integer.parseInt(kv[1].trim()) : 1;
//$$             for (int r = 0; r < reps; r++) trials.add(kv[0].trim().toLowerCase(Locale.ROOT));
//$$         }
//$$         long limitTicks = Long.getLong("tenorclef.fuguebench.limitTicks", 20L * 60 * 7);
//$$         long cancelAt = Long.getLong("tenorclef.fuguebench.cancelTicks", 20L * 40);
//$$         mc.getServer().submit(() -> mc.getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true)).get();
//$$         BlockPos spawn = mc.getServer().submit(() -> mc.getServer().getOverworld().getSpawnPos()).get();
//$$         // v1.1: -Dtenorclef.fuguebench.spots=1,3 runs trial i at spot candidate spots[i % n] instead of the next fresh one
//$$         // (one launch = one fresh world, so each listed spot is untouched when its trial starts).
//$$         String spotList = System.getProperty("tenorclef.fuguebench.spots", "").trim();
//$$         List<Integer> fixedSpots = new ArrayList<>();
//$$         if (!spotList.isEmpty()) for (String c : spotList.split(",")) fixedSpots.add(Integer.parseInt(c.trim()));
//$$         Debug.logHarness("FUGUEBENCH arm=" + arm + " trials=" + trials + " spawn=" + spawn.toShortString() + " limitTicks=" + limitTicks
//$$                 + " seed=" + System.getProperty("tenorclef.seed") + " spots=" + (fixedSpots.isEmpty() ? "sequential" : fixedSpots));
//$$
//$$         AtomicInteger tables = new AtomicInteger(), furnaces = new AtomicInteger();
//$$         boolean[] counting = {false};
//$$         Subscription<BlockPlaceEvent> sub = EventBus.subscribe(BlockPlaceEvent.class, evt -> {
//$$             if (!counting[0] || evt.blockState == null) return;
//$$             if (evt.blockState.getBlock() == Blocks.CRAFTING_TABLE) tables.incrementAndGet();
//$$             else if (evt.blockState.getBlock() == Blocks.FURNACE) furnaces.incrementAndGet();
//$$         });
//$$
//$$         PrintWriter csv = open(arm);
//$$         StringBuilder head = new StringBuilder("arm,scenario,idx,spotX,spotY,spotZ,result,verified,success,ticks,wallMs,dist,tablesPlaced,furnacesPlaced,deaths,stolenAt");
//$$         for (String k : LEFTOVER) head.append(",left_").append(k);
//$$         head.append(",fugueStarts,fugueFailedAttempts,fugueReopens,fugueReplans,planUsTotal,planUsMax,failure,fugueSeq,spotCandidate,fugueStalls,fugueRecoveries,fugueResumed,fugueIneffective,probeUsTotal,probeUsMax,probeTicks,exclusions");
//$$         csv.println(head);
//$$         Map<String, List<long[]>> perScenario = new LinkedHashMap<>();
//$$         int candidate = 0;
//$$         try {
//$$             for (int idx = 0; idx < trials.size(); idx++) {
//$$                 String scen = trials.get(idx);
//$$                 BlockPos spot = null;
//$$                 int cand;
//$$                 if (fixedSpots.isEmpty()) {
//$$                     while (spot == null && candidate < 200) spot = landSpot(mc, spawn, ++candidate);
//$$                     cand = candidate;
//$$                 } else {
//$$                     cand = fixedSpots.get(idx % fixedSpots.size());
//$$                     spot = landSpot(mc, spawn, cand);
//$$                 }
//$$                 if (spot == null) throw new IllegalStateException("no land spot");
//$$                 // Water-escape study: "pond" starts the trial standing in the 1-deep pond next to candidate 1 where
//$$                 // every f11 timeout happened (benchmark setup only; -Dtenorclef.fuguebench.pond=x,y,z overrides).
//$$                 if (scen.equals("pond")) spot = pondSpot();
//$$
//$$                 setup(mc, scen, spot);
//$$                 teleport(mc, spot);
//$$                 if (scen.equals("pond")) logPond(mc, idx, spot);
//$$                 Thread.sleep(2500); // let surrounding chunks arrive before the bot scans
//$$                 mc.execute(() -> AltoClef.getInstance().getBlockScanner().reset());
//$$                 Thread.sleep(400);
//$$                 mc.execute(() -> AltoClef.getInstance().getBlockScanner().reset());
//$$                 Thread.sleep(400);
//$$                 tables.set(0);
//$$                 furnaces.set(0);
//$$
//$$                 Task task = mc.submit(() -> arm.startsWith("fugue") ? (Task) FugueTask.live("iron_pickaxe", 1, arm.equals("fuguer"))
//$$                         : TaskCatalogue.getItemTask("iron_pickaxe", 1)).get();
//$$                 long t0 = worldTime(mc), wall0 = System.currentTimeMillis();
//$$                 WaterEscapeTrace.summaryAndReset();
//$$                 counting[0] = true;
//$$                 mc.execute(() -> AltoClef.getInstance().runUserTask(task));
//$$                 String harness = null;
//$$                 double dist = 0;
//$$                 Snap prev = snap(mc, task), s = prev;
//$$                 int deaths = 0;
//$$                 long stolenAt = -1;
//$$                 boolean cancelled = false;
//$$                 long nextBeat = 400;
//$$                 Thread.sleep(250);
//$$                 while (true) {
//$$                     Thread.sleep(50);
//$$                     long el = worldTime(mc) - t0;
//$$                     s = snap(mc, task);
//$$                     double dx = s.x - prev.x, dy = s.y - prev.y, dz = s.z - prev.z;
//$$                     double step = Math.sqrt(dx * dx + dy * dy + dz * dz);
//$$                     if (step < 8) dist += step;
//$$                     if (s.dead && !prev.dead) deaths++;
//$$                     prev = s;
//$$                     if (s.finished || s.stopped) break;
//$$                     if (s.dead) { harness = "DIED"; break; }
//$$                     if (scen.equals("steal") && stolenAt < 0 && s.cobble >= 8) {
//$$                         stolenAt = el;
//$$                         steal(mc);
//$$                         Debug.logHarness("FUGUEBENCH STEAL idx=" + idx + " t=" + el + " removed cobblestone=" + s.cobble);
//$$                     }
//$$                     if (scen.equals("cancel") && !cancelled && el >= cancelAt) {
//$$                         cancelled = true;
//$$                         mc.execute(() -> AltoClef.getInstance().stopTasks());
//$$                     }
//$$                     if (el > limitTicks) { harness = "TIMEOUT"; break; }
//$$                     if (el >= nextBeat) { nextBeat = el + 400; heartbeat(mc, task, idx, el, s); }
//$$                 }
//$$                 long ticks = worldTime(mc) - t0, wall = System.currentTimeMillis() - wall0;
//$$                 counting[0] = false;
//$$                 if (!(s.finished || s.stopped)) {
//$$                     mc.execute(() -> AltoClef.getInstance().stopTasks());
//$$                     Thread.sleep(500);
//$$                     s = snap(mc, task);
//$$                 }
//$$                 Map<String, Integer> left = leftovers(mc);
//$$                 boolean verified = left.getOrDefault("iron_pickaxe", 0) >= 1;
//$$                 String result = harness != null ? harness : String.valueOf(s.result);
//$$                 boolean success = scen.equals("cancel")
//$$                         ? s.result == TaskResult.CANCELLED && !verified
//$$                         : harness == null && s.result == TaskResult.SUCCESS && verified;
//$$
//$$                 String fm = "";
//$$                 int fStarts = -1, fFailed = -1, fReopens = -1, fReplans = -1;
//$$                 long pTot = -1, pMax = -1;
//$$                 String seq = "";
//$$                 int fStalls = -1, fRec = -1, fRes = -1, fIneff = -1;
//$$                 long prTot = -1, prMax = -1, prN = -1;
//$$                 String excl = "";
//$$                 if (task instanceof FugueTask ft && ft.getCoordinator() != null) {
//$$                     FugueCoordinator c = ft.getCoordinator();
//$$                     fStarts = c.getNodeStarts(); fFailed = c.getFailedAttempts(); fReopens = c.getReopens(); fReplans = c.getReplans();
//$$                     fStalls = c.getStalls(); fRec = c.getRecoveries(); fRes = c.getRecoveriesResumed(); fIneff = c.getRecoveriesIneffective();
//$$                     prTot = ft.getProbeNanosTotal() / 1000; prMax = ft.getProbeNanosMax() / 1000; prN = ft.getProbeCount();
//$$                     excl = String.join(" ", c.getExclusions());
//$$                     pTot = c.getPlanNanosTotal() / 1000; pMax = c.getPlanNanosMax() / 1000;
//$$                     seq = String.join(">", c.getSequence());
//$$                     fm = " fugue[" + c.metrics() + " interrupts=" + ft.getInterrupts() + "]";
//$$                 }
//$$                 String failure = s.failure == null ? "" : s.failure.toString().replace(',', ';');
//$$                 StringBuilder row = new StringBuilder();
//$$                 row.append(String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%s,%s,%s,%d,%d,%.1f,%d,%d,%d,%d",
//$$                         arm, scen, idx, spot.getX(), spot.getY(), spot.getZ(), result, verified, success, ticks, wall, dist,
//$$                         tables.get(), furnaces.get(), deaths, stolenAt));
//$$                 StringBuilder leftStr = new StringBuilder();
//$$                 for (String k : LEFTOVER) {
//$$                     row.append(',').append(left.getOrDefault(k, 0));
//$$                     if (left.getOrDefault(k, 0) > 0) leftStr.append(k).append('=').append(left.get(k)).append(' ');
//$$                 }
//$$                 row.append(String.format(Locale.ROOT, ",%d,%d,%d,%d,%d,%d,%s,%s,%d,%d,%d,%d,%d", fStarts, fFailed, fReopens, fReplans, pTot, pMax, failure, seq,
//$$                         cand, fStalls, fRec, fRes, fIneff));
//$$                 row.append(String.format(Locale.ROOT, ",%d,%d,%d,%s", prTot, prMax, prN, excl.replace(',', ':')));
//$$                 csv.println(row);
//$$                 csv.flush();
//$$                 Debug.logHarness(String.format(Locale.ROOT,
//$$                         "FUGUEBENCH TRIAL arm=%s scen=%s idx=%d cand=" + cand + " spot=%s result=%s verified=%s success=%s ticks=%d wallMs=%d dist=%.0f tables=%d furnaces=%d deaths=%d stolenAt=%d failure=%s left=[%s]%s",
//$$                         arm, scen, idx, spot.toShortString(), result, verified, success, ticks, wall, dist, tables.get(), furnaces.get(),
//$$                         deaths, stolenAt, failure.isEmpty() ? "-" : failure, leftStr.toString().trim(), fm));
//$$                 Debug.logHarness("FUGUEBENCH WATER arm=" + arm + " scen=" + scen + " idx=" + idx + " " + WaterEscapeTrace.summaryAndReset());
//$$                 perScenario.computeIfAbsent(scen, k -> new ArrayList<>()).add(new long[]{success ? 1 : 0, ticks, (long) dist});
//$$                 Thread.sleep(500);
//$$             }
//$$         } finally {
//$$             counting[0] = false;
//$$             EventBus.unsubscribe(sub);
//$$             csv.close();
//$$         }
//$$         for (Map.Entry<String, List<long[]>> e : perScenario.entrySet()) {
//$$             List<long[]> rows = e.getValue();
//$$             long ok = rows.stream().filter(r -> r[0] == 1).count();
//$$             long[] ticks = rows.stream().mapToLong(r -> r[1]).sorted().toArray();
//$$             double meanDist = rows.stream().mapToLong(r -> r[2]).average().orElse(0);
//$$             Debug.logHarness(String.format(Locale.ROOT, "FUGUEBENCH SUMMARY arm=%s scen=%s n=%d success=%d medianTicks=%d meanDist=%.0f",
//$$                     arm, e.getKey(), rows.size(), ok, ticks.length == 0 ? 0 : ticks[ticks.length / 2], meanDist));
//$$         }
//$$         Debug.logHarness("FUGUEBENCH done arm=" + arm);
//$$     }
//$$
//$$     /** Candidate N on a line east of spawn; a few north/south offsets if it lands in water. Deterministic per seed. */
//$$     private static BlockPos landSpot(MinecraftClient mc, BlockPos spawn, int n) throws Exception {
//$$         int[] offsets = {0, 64, -64, 128, -128};
//$$         for (int off : offsets) {
//$$             int x = spawn.getX() + SPACING * n, z = spawn.getZ() + off;
//$$             int y = loadSurface(mc, x, z);
//$$             if (y <= 1) continue;
//$$             final int fy = y;
//$$             boolean dry = mc.submit(() -> mc.world.getBlockState(new BlockPos(x, fy - 1, z)).getFluidState().isEmpty()
//$$                     && mc.world.getBlockState(new BlockPos(x, fy, z)).getFluidState().isEmpty()).get();
//$$             if (dry) return new BlockPos(x, y, z);
//$$             Debug.logHarness("FUGUEBENCH spot " + x + "," + z + " is water, trying next");
//$$         }
//$$         return null;
//$$     }
//$$
//$$     private static int loadSurface(MinecraftClient mc, int x, int z) throws Exception {
//$$         for (int i = 0; i < 40; i++) {
//$$             // Re-pin at y=200 every poll: a slow chunk load once let the bot free-fall to the surface and die,
//$$             // and DeathMenuChain's `while (player.isAlive());` spin then froze the client.
//$$             teleport(mc, new BlockPos(x, 200, z));
//$$             Thread.sleep(250);
//$$             boolean loaded = mc.submit(() -> mc.world != null && mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)).get();
//$$             if (!loaded) continue;
//$$             int y = mc.submit(() -> surfaceY(mc, x, z)).get();
//$$             if (y > 1) return y;
//$$         }
//$$         return -1;
//$$     }
//$$
//$$     private static int surfaceY(MinecraftClient mc, int x, int z) {
//$$         int y = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
//$$         if (y <= 0) return -1;
//$$         BlockPos.Mutable q = new BlockPos.Mutable(x, y - 1, z);
//$$         while (q.getY() > 0 && (mc.world.getBlockState(q).getBlock() instanceof net.minecraft.block.LeavesBlock
//$$                 || mc.world.getBlockState(q).getCollisionShape(mc.world, q).isEmpty() && mc.world.getBlockState(q).getFluidState().isEmpty())) q.move(0, -1, 0);
//$$         return q.getY() + 1;
//$$     }
//$$
//$$     private static void setup(MinecraftClient mc, String scen, BlockPos spot) throws Exception {
//$$         java.util.UUID id = mc.player.getUuid();
//$$         mc.getServer().submit(() -> {
//$$             ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayer(id);
//$$             ServerWorld w = mc.getServer().getOverworld();
//$$             w.setTimeOfDay(1000);
//$$             w.getEntities(net.minecraft.entity.ItemEntity.class, new Box(spot).expand(128), e -> true).forEach(net.minecraft.entity.Entity::remove);
//$$             if (sp == null) return;
//$$             sp.inventory.clear();
//$$             switch (scen) {
//$$                 case "partial":
//$$                     sp.inventory.insertStack(new ItemStack(Items.CRAFTING_TABLE, 1));
//$$                     sp.inventory.insertStack(new ItemStack(Items.STONE_PICKAXE, 1));
//$$                     sp.inventory.insertStack(new ItemStack(Items.STICK, 2));
//$$                     break;
//$$                 case "full":
//$$                     sp.inventory.insertStack(new ItemStack(Items.IRON_INGOT, 3));
//$$                     sp.inventory.insertStack(new ItemStack(Items.STICK, 2));
//$$                     sp.inventory.insertStack(new ItemStack(Items.CRAFTING_TABLE, 1));
//$$                     break;
//$$                 case "pond":
//$$                     // what every f11 pond timeout still held when it stalled: next step is cobblestone
//$$                     sp.inventory.insertStack(new ItemStack(Items.WOODEN_PICKAXE, 1));
//$$                     sp.inventory.insertStack(new ItemStack(Items.CRAFTING_TABLE, 1));
//$$                     sp.inventory.insertStack(new ItemStack(Items.STICK, 6));
//$$                     break;
//$$                 default:
//$$                     break; // empty, steal, cancel: start with nothing
//$$             }
//$$         }).get();
//$$     }
//$$
//$$     private static BlockPos pondSpot() {
//$$         String[] c = System.getProperty("tenorclef.fuguebench.pond", "579,62,-51").split(",");
//$$         return new BlockPos(Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), Integer.parseInt(c[2].trim()));
//$$     }
//$$
//$$     private static void logPond(MinecraftClient mc, int idx, BlockPos spot) throws Exception {
//$$         String s = mc.submit(() -> {
//$$             boolean feet = !mc.world.getBlockState(spot).getFluidState().isEmpty();
//$$             boolean head = !mc.world.getBlockState(spot.up()).getFluidState().isEmpty();
//$$             boolean floor = mc.world.getBlockState(spot.down()).getMaterial().isSolid();
//$$             return "feetWater=" + feet + " headWater=" + head + " solidFloor=" + floor + " player=" + mc.player.getBlockPos().toShortString()
//$$                     + " touchingWater=" + mc.player.isTouchingWater();
//$$         }).get();
//$$         Debug.logHarness("FUGUEBENCH POND idx=" + idx + " spot=" + spot.toShortString() + " " + s);
//$$     }
//$$
//$$     private static void steal(MinecraftClient mc) throws Exception {
//$$         java.util.UUID id = mc.player.getUuid();
//$$         mc.getServer().submit(() -> {
//$$             ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayer(id);
//$$             if (sp == null) return;
//$$             for (int i = 0; i < sp.inventory.size(); i++) {
//$$                 if (sp.inventory.getStack(i).getItem() == Items.COBBLESTONE) sp.inventory.setStack(i, ItemStack.EMPTY);
//$$             }
//$$         }).get();
//$$     }
//$$
//$$     /**
//$$      * Every 20 s: position and the running task path, so stalls can be diagnosed in either arm. v1.1: the deepest
//$$      * five tasks in full (v1 cut the path at depth 12 and 60 chars, which hid the stock cobblestone stall's state).
//$$      */
//$$     private static void heartbeat(MinecraftClient mc, Task task, int idx, long el, Snap s) throws Exception {
//$$         String path = mc.submit(() -> {
//$$             List<String> all = new ArrayList<>();
//$$             for (Task t = task; t != null && all.size() < 64; t = t.getSub()) {
//$$                 String n = t.toString();
//$$                 all.add(n.length() > 140 ? n.substring(0, 140) : n);
//$$             }
//$$             int from = Math.max(1, all.size() - 5);
//$$             StringBuilder b = new StringBuilder("depth=" + all.size() + " ");
//$$             if (!all.isEmpty()) b.append(all.get(0).length() > 50 ? all.get(0).substring(0, 50) : all.get(0));
//$$             if (from > 1) b.append(" > ...");
//$$             for (int i = from; i < all.size(); i++) b.append(" > ").append(all.get(i));
//$$             return b.toString();
//$$         }).get();
//$$         Debug.logHarness(String.format("FUGUEBENCH BEAT idx=%d t=%d pos=%.0f,%.0f,%.0f cobble=%d path=%s", idx, el, s.x, s.y, s.z, s.cobble, path));
//$$     }
//$$
//$$     private static Snap snap(MinecraftClient mc, Task task) throws Exception {
//$$         return mc.submit(() -> {
//$$             Snap s = new Snap();
//$$             s.finished = task.isFinished();
//$$             s.stopped = task.stopped();
//$$             s.result = task.getLastResult();
//$$             s.failure = task.getLastFailure();
//$$             s.dead = mc.player == null || mc.player.isDead();
//$$             if (mc.player != null) {
//$$                 s.x = mc.player.getX(); s.y = mc.player.getY(); s.z = mc.player.getZ();
//$$                 s.cobble = count(mc, Items.COBBLESTONE);
//$$                 s.pick = count(mc, Items.IRON_PICKAXE);
//$$             }
//$$             return s;
//$$         }).get();
//$$     }
//$$
//$$     private static int count(MinecraftClient mc, Item item) {
//$$         int n = 0;
//$$         for (int i = 0; i < mc.player.inventory.size(); i++) {
//$$             ItemStack st = mc.player.inventory.getStack(i);
//$$             if (st.getItem() == item) n += st.getCount();
//$$         }
//$$         return n;
//$$     }
//$$
//$$     private static Map<String, Integer> leftovers(MinecraftClient mc) throws Exception {
//$$         return mc.submit(() -> {
//$$             Map<String, Integer> m = new LinkedHashMap<>();
//$$             if (mc.player == null) return m;
//$$             for (String k : LEFTOVER) {
//$$                 Item[] matches = TaskCatalogue.getItemMatches(k);
//$$                 int n = 0;
//$$                 if (matches != null) for (Item it : matches) n += count(mc, it);
//$$                 m.put(k, n);
//$$             }
//$$             return m;
//$$         }).get();
//$$     }
//$$
//$$     private static void teleport(MinecraftClient mc, BlockPos p) throws InterruptedException {
//$$         if (mc.getServer() == null || mc.player == null) return;
//$$         if (mc.player.isDead()) { mc.execute(() -> mc.player.requestRespawn()); Thread.sleep(2000); }
//$$         java.util.UUID id = mc.player.getUuid();
//$$         mc.getServer().execute(() -> {
//$$             ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayer(id);
//$$             if (sp != null) {
//$$                 sp.setVelocity(0, 0, 0);
//$$                 sp.fallDistance = 0;
//$$                 sp.getHungerManager().setFoodLevel(20);
//$$                 sp.getHungerManager().setSaturationLevelClient(5.0f);
//$$                 sp.setAir(sp.getMaxAir());
//$$                 sp.setHealth(sp.getMaxHealth());
//$$                 sp.networkHandler.requestTeleport(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, sp.yaw, sp.pitch);
//$$             }
//$$         });
//$$         for (int i = 0; i < 60; i++) {
//$$             Thread.sleep(25);
//$$             if (mc.player != null && mc.player.squaredDistanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 1.0) break;
//$$         }
//$$         mc.execute(() -> BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything());
//$$         Thread.sleep(50);
//$$     }
//$$
//$$     private static long worldTime(MinecraftClient mc) {
//$$         return mc.world == null ? 0 : mc.world.getTime();
//$$     }
//$$
//$$     private static PrintWriter open(String arm) throws java.io.IOException {
//$$         Path dir = Paths.get("fuguebench");
//$$         Files.createDirectories(dir);
//$$         Path f = dir.resolve("fuguebench_" + arm + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
//$$         Debug.logHarness("FUGUEBENCH writing " + f.toAbsolutePath());
//$$         return new PrintWriter(Files.newBufferedWriter(f));
//$$     }
//$$ }
//#else
/** FugueBench drives the 1.16.1 SIM target only (it uses the integrated server's 1.16.1 internals). */
public final class FugueBench {
    private FugueBench() {}
    public static boolean isRunning() { return false; }
    public static String start(String arm, String plan) { return "FUGUEBENCH is 1.16.1-only"; }
}
//#endif
