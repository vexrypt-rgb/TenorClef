package adris.altoclef.benchmark;

//#if MC <= 11601
//$$ import baritone.api.BaritoneAPI;
//$$ import baritone.api.IBaritone;
//$$ import baritone.api.pathing.calc.IPath;
//$$ import baritone.api.pathing.goals.Goal;
//$$ import baritone.api.pathing.goals.GoalBlock;
//$$ import baritone.api.pathing.goals.GoalXZ;
//$$ import baritone.api.utils.PathCalculationResult;
//$$ import baritone.api.utils.SettingsUtil;
//$$ import baritone.pathing.calc.AStarPathFinder;
//$$ import baritone.pathing.movement.CalculationContext;
//$$ import baritone.utils.pathing.Favoring;
//$$ import net.minecraft.client.MinecraftClient;
//$$ import net.minecraft.server.network.ServerPlayerEntity;
//$$ import net.minecraft.util.math.BlockPos;
//$$ import net.minecraft.world.Heightmap;
//$$
//$$ import java.io.PrintWriter;
//$$ import java.nio.file.Files;
//$$ import java.nio.file.Path;
//$$ import java.nio.file.Paths;
//$$ import java.time.LocalDateTime;
//$$ import java.time.format.DateTimeFormatter;
//$$ import java.util.ArrayList;
//$$ import java.util.Arrays;
//$$ import java.util.List;
//$$ import java.util.Locale;
//$$ import adris.altoclef.Debug;
//$$ import adris.altoclef.movement.TungstenMovement;
//$$
//$$ /**
//$$  * Headless-friendly pathfinding benchmark (1.16.1 SIM target).
//$$  *
//$$  * <p><b>search</b>: runs Baritone's A* alone (no movement) from the bot's position to a fixed
//$$  * ring of goals, several reps each, and records nodes / ms / path cost. Optional sweep
//$$  * {@code key=v1,v2,...} re-runs the whole ring once per Baritone setting value, so a
//$$  * settings change can be compared inside one launch.
//$$  *
//$$  * <p><b>travel</b>: end-to-end with a mover (baritone | tungsten). Teleports back to the
//$$  * start before each trial and records game ticks to arrive and final distance.
//$$  *
//$$  * <p>Writes {@code pathbench/pathbench_<mode>_<time>.csv} and logs a {@code PATHBENCH}
//$$  * summary. {@code -Dtenorclef.pathbench.exit=true} stops the client afterwards (headless loop).
//$$  */
//$$ public final class PathBench {
//$$
//$$     private static volatile Thread running;
//$$     private static final int[][] DIRS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
//$$     private static final int[] DISTS = {32, 96};
//$$
//$$     private PathBench() {}
//$$
//$$     public static boolean isRunning() {
//$$         return running != null && running.isAlive();
//$$     }
//$$
//$$     public static String start(String mode, String opt, int reps) {
//$$         if (isRunning()) return "PATHBENCH already running";
//$$         MinecraftClient mc = MinecraftClient.getInstance();
//$$         if (mc.player == null || mc.world == null) return "PATHBENCH needs a loaded world";
//$$         BlockPos origin = mc.player.getBlockPos();
//$$         Thread t = new Thread(() -> {
//$$             try {
//$$                 if (mode.equalsIgnoreCase("wreck")) wreck(mc, origin, Math.max(1, reps));
//$$                 else if (mode.equalsIgnoreCase("swim")) swim(mc, origin, Math.max(1, reps));
//$$                 else if (mode.equalsIgnoreCase("travel")) for (String m : (opt == null ? "-" : opt).split("[;+]")) travel(mc, origin, m, Math.max(1, reps));
//$$                 else for (String sweep : (opt == null ? "-" : opt).split("[;+]")) search(mc, origin, sweep, Math.max(1, reps));
//$$             } catch (Throwable e) {
//$$                 Debug.logHarness("PATHBENCH failed: " + e);
//$$                 e.printStackTrace();
//$$             } finally {
//$$                 running = null;
//$$                 if (Boolean.getBoolean("tenorclef.pathbench.exit")) {
//$$                     Debug.logHarness("PATHBENCH exit requested");
//$$                     mc.execute(mc::scheduleStop);
//$$                 }
//$$             }
//$$         }, "PathBench");
//$$         t.setDaemon(true);
//$$         running = t;
//$$         t.start();
//$$         return "PATHBENCH " + mode + " started at " + origin.toShortString();
//$$     }
//$$
//$$     private static List<BlockPos> ring(MinecraftClient mc, BlockPos origin) {
//$$         List<BlockPos> out = new ArrayList<>();
//$$         for (int d : DISTS) {
//$$             for (int[] dir : DIRS) {
//$$                 double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
//$$                 int x = origin.getX() + (int) Math.round(dir[0] * d / len);
//$$                 int z = origin.getZ() + (int) Math.round(dir[1] * d / len);
//$$                 // Client worlds only receive MOTION_BLOCKING/WORLD_SURFACE heightmaps; NO_LEAVES reads 0.
//$$                 final int fx = x, fz = z;
//$$                 int y;
//$$                 try { y = mc.submit(() -> surfaceY(mc, fx, fz)).get(); } catch (Exception e) { y = surfaceY(mc, fx, fz); }
//$$                 out.add(new BlockPos(x, y, z));
//$$             }
//$$         }
//$$         return out;
//$$     }
//$$
//$$     private static int surfaceY(MinecraftClient mc, int x, int z) {
//$$         int y = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
//$$         if (y > 0) return y;
//$$         BlockPos.Mutable p = new BlockPos.Mutable(x, 255, z);
//$$         while (p.getY() > 0 && mc.world.getBlockState(p).getCollisionShape(mc.world, p).isEmpty()) p.move(0, -1, 0);
//$$         return p.getY() + 1;
//$$     }
//$$
//$$     // ---- search ------------------------------------------------------------------------
//$$
//$$     private static void search(MinecraftClient mc, BlockPos origin, String opt, int reps) throws Exception {
//$$         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
//$$         List<BlockPos> goals = ring(mc, origin);
//$$         String key = null;
//$$         List<String> values = new ArrayList<>();
//$$         values.add(null);
//$$         String original = null;
//$$         if (opt != null && opt.contains("=")) {
//$$             key = opt.substring(0, opt.indexOf('='));
//$$             values = Arrays.asList(opt.substring(opt.indexOf('=') + 1).split(","));
//$$             baritone.api.Settings.Setting<?> s = BaritoneAPI.getSettings().byLowerName.get(key.toLowerCase(Locale.ROOT));
//$$             if (s == null) throw new IllegalArgumentException("unknown baritone setting " + key);
//$$             original = SettingsUtil.settingValueToString(s);
//$$         }
//$$         long timeout = Long.getLong("tenorclef.pathbench.timeoutMs", 4000L);
//$$         PrintWriter csv = open("search");
//$$         csv.println("setting,value,goal,dx,dz,dist,rep,result,ms,nodes,pathLen,costTicks");
//$$         try {
//$$             for (String v : values) {
//$$                 if (key != null) SettingsUtil.parseAndApply(BaritoneAPI.getSettings(), key.toLowerCase(Locale.ROOT), v);
//$$                 long sumMs = 0, sumNodes = 0; double sumCost = 0; int ok = 0, n = 0;
//$$                 for (int gi = 0; gi < goals.size(); gi++) {
//$$                     BlockPos g = goals.get(gi);
//$$                     Goal goal = new GoalXZ(g.getX(), g.getZ());
//$$                     for (int r = 0; r < reps; r++) {
//$$                         CalculationContext ctx = mc.submit(() -> new CalculationContext(baritone, true)).get(); // BlockStateInterface must be built on the client thread
//$$                         AStarPathFinder pf = new AStarPathFinder(origin.getX(), origin.getY(), origin.getZ(), goal, new Favoring(null, ctx), ctx);
//$$                         long t0 = System.nanoTime();
//$$                         PathCalculationResult res = pf.calculate(timeout, timeout);
//$$                         long ms = (System.nanoTime() - t0) / 1_000_000L;
//$$                         IPath p = res.getPath().orElse(null);
//$$                         boolean reached = p != null && goal.isInGoal(p.getDest());
//$$                         double cost = 0;
//$$                         if (p != null) for (baritone.api.pathing.movement.IMovement m : p.movements()) cost += m.getCost();
//$$                         int nodes = p == null ? 0 : p.getNumNodesConsidered();
//$$                         csv.printf(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%d,%s,%d,%d,%d,%.1f%n",
//$$                                 key == null ? "" : key, v == null ? "" : v, gi, g.getX() - origin.getX(), g.getZ() - origin.getZ(),
//$$                                 (int) Math.round(Math.sqrt(g.getSquaredDistance(origin))), r,
//$$                                 reached ? "GOAL" : (p != null ? "PARTIAL" : res.getType().name()), ms, nodes, p == null ? 0 : p.length(), cost);
//$$                         n++; sumMs += ms; sumNodes += nodes;
//$$                         if (reached) { ok++; sumCost += cost; }
//$$                     }
//$$                 }
//$$                 Debug.logHarness(String.format(Locale.ROOT,
//$$                         "PATHBENCH SUMMARY mode=search %s goalRate=%d/%d avgMs=%.1f avgNodes=%.0f avgGoalCost=%.1f",
//$$                         key == null ? "baseline" : key + "=" + v, ok, n, sumMs / (double) n, sumNodes / (double) n, ok == 0 ? 0 : sumCost / ok));
//$$             }
//$$         } finally {
//$$             if (key != null) SettingsUtil.parseAndApply(BaritoneAPI.getSettings(), key.toLowerCase(Locale.ROOT), original);
//$$             csv.close();
//$$         }
//$$     }
//$$
//$$     // ---- travel ------------------------------------------------------------------------
//$$
//$$     /** Tungsten movement along a Baritone block route: Baritone picks the blocks, Tungsten the sprint/jump inputs. */
//$$     private static boolean startGuided(MinecraftClient mc, IBaritone b, BlockPos g) {
//$$         try {
//$$             BlockPos from = mc.submit(() -> mc.player.getBlockPos()).get();
//$$             CalculationContext ctx = mc.submit(() -> new CalculationContext(b, true)).get();
//$$             AStarPathFinder pf = new AStarPathFinder(from.getX(), from.getY(), from.getZ(), new GoalBlock(g), new Favoring(null, ctx), ctx);
//$$             long ms = Long.getLong("tenorclef.pathbench.guideMs", 1500L);
//$$             IPath p = pf.calculate(ms, ms * 2).getPath().orElse(null);
//$$             if (p == null || p.positions().size() < 2) return TungstenMovement.requestPathTo(g);
//$$             // Keep every Nth block plus every height change, so Tungsten is free to cut corners on flat runs.
//$$             int stride = Integer.getInteger("tenorclef.pathbench.guideStride", 3);
//$$             List<? extends baritone.api.utils.BetterBlockPos> pos = p.positions();
//$$             java.util.List<BlockPos> way = new java.util.ArrayList<>();
//$$             for (int i = 0; i < pos.size(); i++) {
//$$                 boolean yChange = i > 0 && pos.get(i).getY() != pos.get(i - 1).getY()
//$$                         || i + 1 < pos.size() && pos.get(i).getY() != pos.get(i + 1).getY();
//$$                 if (i == 0 || i == pos.size() - 1 || i % stride == 0 || yChange) way.add(new BlockPos(pos.get(i).getX(), pos.get(i).getY(), pos.get(i).getZ()));
//$$             }
//$$             BlockPos end = way.get(way.size() - 1);
//$$             return TungstenMovement.requestPathVia(end, way);
//$$         } catch (Exception e) {
//$$             Debug.logHarness("PATHBENCH guided route failed: " + e);
//$$             return TungstenMovement.requestPathTo(g);
//$$         }
//$$     }
//$$
//$$     private static void travel(MinecraftClient mc, BlockPos origin, String opt, int reps) throws Exception {
//$$         String mover = opt == null || opt.equals("-") ? "baritone" : opt.toLowerCase(Locale.ROOT);
//$$         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
//$$         List<BlockPos> goals = ring(mc, origin);
//$$         long limitTicks = Long.getLong("tenorclef.pathbench.travelTicks", 20L * 90);
//$$         // End a trial early once it stops getting closer; 0 disables.
//$$         long stallTicks = Long.getLong("tenorclef.pathbench.stallTicks", 400L);
//$$         long idleTicks = Long.getLong("tenorclef.pathbench.idleTicks", 80L);
//$$         // Optional goal subset, e.g. -Dtenorclef.pathbench.goals=8,9,10
//$$         String goalSel = System.getProperty("tenorclef.pathbench.goals", "").trim();
//$$         java.util.Set<Integer> only = new java.util.HashSet<>();
//$$         if (!goalSel.isEmpty()) for (String x : goalSel.split(",")) only.add(Integer.parseInt(x.trim()));
//$$         PrintWriter csv = open("travel_" + mover);
//$$         csv.println("mover,goal,dx,dz,dist,rep,result,ticks,endDist,firstMoveTicks");
//$$         int ok = 0, n = 0, moved = 0; long sumTicks = 0, sumFirst = 0; double sumEnd = 0;
//$$         try {
//$$             for (int gi = 0; gi < goals.size(); gi++) {
//$$                 if (!only.isEmpty() && !only.contains(gi)) continue;
//$$                 BlockPos g = goals.get(gi);
//$$                 for (int r = 0; r < reps; r++) {
//$$                     teleport(mc, origin);
//$$                     long t0 = worldTime(mc);
//$$                     boolean started = mover.equals("tungsten") ? TungstenMovement.requestPathTo(g) : mover.equals("guided") ? startGuided(mc, baritone, g) : startBaritone(mc, baritone, g);
//$$                     long firstMove = -1;
//$$                     double startD = dist(mc, g);
//$$                     String result = started ? "TIMEOUT" : "NOSTART";
//$$                     double bestD = startD; long bestAt = 0; long lastReq = 0; double lastD = startD; long lastMoveAt = 0;
//$$                     while (started) {
//$$                         Thread.sleep(25);
//$$                         long el = worldTime(mc) - t0;
//$$                         double d = dist(mc, g);
//$$                         if (firstMove < 0 && Math.abs(d - startD) > 0.5) firstMove = el;
//$$                         if (d < 2.0) { result = "GOAL"; break; }
//$$                         if (d < bestD - 1.0) { bestD = d; bestAt = el; }
//$$                         if (stallTicks > 0 && el - bestAt > stallTicks) { result = "STALLED"; break; }
//$$                         if (Math.abs(d - lastD) > 0.3) { lastD = d; lastMoveAt = el; }
//$$                         if (!mover.equals("baritone") && idleTicks > 0 && firstMove >= 0 && el - lastMoveAt > idleTicks && el - lastReq > idleTicks && el < limitTicks) {
//$$                             TungstenMovement.cancel(); Thread.sleep(100);
//$$                             if (mover.equals("guided")) startGuided(mc, baritone, g); else TungstenMovement.requestPathTo(g);
//$$                             lastReq = el; lastMoveAt = el; continue;
//$$                         }
//$$                         boolean active = !mover.equals("baritone") ? TungstenMovement.isPathing() : baritone.getCustomGoalProcess().isActive();
//$$                         if (!active && el - lastReq > 40) {
//$$                             if (mover.equals("tungsten") && el < limitTicks) { TungstenMovement.requestPathTo(g); lastReq = el; continue; }
//$$                             if (mover.equals("guided") && el < limitTicks) { startGuided(mc, baritone, g); lastReq = el; continue; }
//$$                             result = "STOPPED"; break;
//$$                         }
//$$                         if (el > limitTicks) break;
//$$                     }
//$$                     if (!mover.equals("baritone")) TungstenMovement.cancel();
//$$                     else mc.execute(() -> baritone.getPathingBehavior().cancelEverything());
//$$                     long ticks = worldTime(mc) - t0;
//$$                     double end = dist(mc, g);
//$$                     csv.printf(Locale.ROOT, "%s,%d,%d,%d,%d,%d,%s,%d,%.2f,%d%n", mover, gi, g.getX() - origin.getX(), g.getZ() - origin.getZ(),
//$$                             (int) Math.round(Math.sqrt(g.getSquaredDistance(origin))), r, result, ticks, end, firstMove);
//$$                     csv.flush();
//$$                     n++;
//$$                     if (result.equals("GOAL")) { ok++; sumTicks += ticks; }
//$$                     if (firstMove >= 0) { moved++; sumFirst += firstMove; }
//$$                     sumEnd += end;
//$$                     Thread.sleep(500);
//$$                 }
//$$             }
//$$         } finally {
//$$             csv.close();
//$$         }
//$$         Debug.logHarness(String.format(Locale.ROOT, "PATHBENCH SUMMARY mode=travel mover=%s goalRate=%d/%d avgGoalTicks=%.0f avgFirstMoveTicks=%.1f avgEndDist=%.1f",
//$$                 mover, ok, n, ok == 0 ? 0 : sumTicks / (double) ok, moved == 0 ? -1 : sumFirst / (double) moved, n == 0 ? 0 : sumEnd / n));
//$$     }
//$$
//$$     // ---- swim --------------------------------------------------------------------------
//$$
//$$     /** Glass tank of water high above origin; Baritone must reach 3D goals inside it (floor, mid-depth, surface). */
//$$     private static void swim(MinecraftClient mc, BlockPos origin, int reps) throws Exception {
//$$         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
//$$         BaritoneAPI.getSettings().chatDebug.value = true;
//$$         int R = 12, H = 12, by = 200, ox = origin.getX(), oz = origin.getZ();
//$$         java.util.concurrent.CompletableFuture<Void> built = new java.util.concurrent.CompletableFuture<>();
//$$         mc.getServer().execute(() -> {
//$$             net.minecraft.server.world.ServerWorld w = mc.getServer().getOverworld();
//$$             for (int x = -R - 1; x <= R + 1; x++) for (int z = -R - 1; z <= R + 1; z++) for (int y = by - 1; y < by + H; y++) {
//$$                 boolean wall = Math.abs(x) > R || Math.abs(z) > R || y < by;
//$$                 w.setBlockState(new BlockPos(ox + x, y, oz + z), wall ? net.minecraft.block.Blocks.GLASS.getDefaultState() : net.minecraft.block.Blocks.WATER.getDefaultState(), 2);
//$$             }
//$$             built.complete(null);
//$$         });
//$$         built.get();
//$$         Thread.sleep(3000);
//$$         BlockPos start = new BlockPos(ox, by + H - 2, oz);
//$$         int[][] offs = {{10, 0, 10}, {-10, 0, 10}, {-10, 0, -10}, {10, 0, -10}, {10, 5, 0}, {0, 5, -10}, {-10, 9, 0}, {0, 2, 10}};
//$$         long limitTicks = Long.getLong("tenorclef.pathbench.travelTicks", 20L * 90);
//$$         PrintWriter csv = open("swim_baritone");
//$$         csv.println("mover,goal,dx,dy,dz,dist,rep,result,ticks,endDist,firstMoveTicks");
//$$         int ok = 0, n = 0;
//$$         try {
//$$             for (int gi = 0; gi < offs.length; gi++) {
//$$                 BlockPos g = new BlockPos(ox + offs[gi][0], by + offs[gi][1], oz + offs[gi][2]);
//$$                 for (int r = 0; r < reps; r++) {
//$$                     teleport(mc, start);
//$$                     long t0 = worldTime(mc);
//$$                     startBaritone(mc, baritone, g);
//$$                     double startD = dist3(mc, g), bestD = startD; long bestAt = 0, firstMove = -1;
//$$                     String result = "TIMEOUT";
//$$                     while (true) {
//$$                         Thread.sleep(25);
//$$                         long el = worldTime(mc) - t0;
//$$                         double d = dist3(mc, g);
//$$                         if (firstMove < 0 && Math.abs(d - startD) > 0.5) firstMove = el;
//$$                         if (d < 1.5) { result = "GOAL"; break; }
//$$                         if (mc.player.isDead()) { result = "DIED"; break; }
//$$                         if (el % 20 == 0) { Object cur = baritone.getPathingBehavior().getCurrent(); Debug.logHarness(String.format(Locale.ROOT, "SWIM t=%d pos=%.1f,%.1f,%.1f d=%.1f seg=%s", el, mc.player.getX(), mc.player.getY(), mc.player.getZ(), d, cur == null ? "none" : ((baritone.api.pathing.path.IPathExecutor) cur).getPath().movements().get(Math.min(((baritone.api.pathing.path.IPathExecutor) cur).getPosition(), ((baritone.api.pathing.path.IPathExecutor) cur).getPath().movements().size() - 1)).getClass().getSimpleName())); }
//$$                         if (d < bestD - 1.0) { bestD = d; bestAt = el; }
//$$                         if (el - bestAt > 400) { result = "STALLED"; break; }
//$$                         if (el > 40 && !baritone.getCustomGoalProcess().isActive()) { result = "STOPPED"; break; }
//$$                         if (el > limitTicks) break;
//$$                     }
//$$                     mc.execute(() -> baritone.getPathingBehavior().cancelEverything());
//$$                     long ticks = worldTime(mc) - t0;
//$$                     csv.printf(Locale.ROOT, "baritone,%d,%d,%d,%d,%d,%d,%s,%d,%.2f,%d%n", gi, offs[gi][0], offs[gi][1] - (H - 2), offs[gi][2],
//$$                             (int) Math.round(Math.sqrt(g.getSquaredDistance(start))), r, result, ticks, dist3(mc, g), firstMove);
//$$                     csv.flush();
//$$                     n++;
//$$                     if (result.equals("GOAL")) ok++;
//$$                     Thread.sleep(500);
//$$                 }
//$$             }
//$$         } finally {
//$$             csv.close();
//$$         }
//$$         Debug.logHarness(String.format(Locale.ROOT, "PATHBENCH SUMMARY mode=swim goalRate=%d/%d", ok, n));
//$$     }
//$$
//$$     private static double dist3(MinecraftClient mc, BlockPos g) {
//$$         if (mc.player == null) return 1e9;
//$$         double dx = mc.player.getX() - (g.getX() + 0.5), dy = mc.player.getY() - g.getY(), dz = mc.player.getZ() - (g.getZ() + 0.5);
//$$         return Math.sqrt(dx * dx + dy * dy + dz * dz);
//$$     }
//$$
//$$     // ---- wreck -------------------------------------------------------------------------
//$$
//$$     /** Real shipwrecks from the seed: start 20 blocks off at the water surface, Baritone swims to a chest and opens it. */
//$$     private static void wreck(MinecraftClient mc, BlockPos origin, int count) throws Exception {
//$$         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
//$$         BaritoneAPI.getSettings().chatDebug.value = true;
//$$         long limitTicks = Long.getLong("tenorclef.pathbench.travelTicks", 20L * 120);
//$$         int[][] dirs = {{0, 0}, {800, 0}, {-800, 0}, {0, 800}, {0, -800}, {800, 800}, {-800, -800}, {800, -800}};
//$$         PrintWriter csv = open("wreck_baritone");
//$$         csv.println("wreck,x,y,z,startDist,result,ticks,endDist,opened,items");
//$$         int ok = 0, n = 0;
//$$         try {
//$$             for (int wi = 0; wi < Math.min(count, dirs.length); wi++) {
//$$                 BlockPos from = origin.add(dirs[wi][0], 0, dirs[wi][1]);
//$$                 BlockPos[] found = mc.getServer().submit(() -> {
//$$                     net.minecraft.server.world.ServerWorld w = mc.getServer().getOverworld();
//$$                     BlockPos wp = w.locateStructure(net.minecraft.world.gen.feature.StructureFeature.SHIPWRECK, from, 50, false);
//$$                     if (wp == null) return null;
//$$                     BlockPos best = null;
//$$                     for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++)
//$$                         for (net.minecraft.block.entity.BlockEntity be : w.getChunk((wp.getX() >> 4) + cx, (wp.getZ() >> 4) + cz).getBlockEntities().values())
//$$                             if (be instanceof net.minecraft.block.entity.ChestBlockEntity && (best == null || be.getPos().getSquaredDistance(wp) < best.getSquaredDistance(wp))) best = be.getPos();
//$$                     if (best == null) return null;
//$$                     int sx = best.getX() + 20, sz = best.getZ();
//$$                     w.getChunk(sx >> 4, sz >> 4);
//$$                     int sy = w.getTopY(Heightmap.Type.MOTION_BLOCKING, sx, sz);
//$$                     return new BlockPos[]{best, new BlockPos(sx, sy, sz)};
//$$                 }).get();
//$$                 if (found == null) { Debug.logHarness("PATHBENCH wreck " + wi + ": no chest found near " + from.toShortString()); continue; }
//$$                 BlockPos chest = found[0], start = found[1];
//$$                 Debug.logHarness("PATHBENCH wreck " + wi + " chest=" + chest.toShortString() + " start=" + start.toShortString());
//$$                 teleport(mc, start);
//$$                 // Far teleports: the client player is frozen until its chunks arrive.
//$$                 for (int i = 0; i < 200; i++) {
//$$                     boolean all = mc.submit(() -> { for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) if (!mc.world.getChunkManager().isChunkLoaded((start.getX() >> 4) + cx, (start.getZ() >> 4) + cz)) return false; return true; }).get();
//$$                     if (all) break;
//$$                     Thread.sleep(100);
//$$                 }
//$$                 Thread.sleep(2000);
//$$                 teleport(mc, start);
//$$                 long t0 = worldTime(mc);
//$$                 mc.execute(() -> baritone.getCustomGoalProcess().setGoalAndPath(new baritone.api.pathing.goals.GoalGetToBlock(chest)));
//$$                 double startD = eyeDist(mc, chest), bestD = startD; long bestAt = 0;
//$$                 String result = "TIMEOUT";
//$$                 while (true) {
//$$                     Thread.sleep(25);
//$$                     long el = worldTime(mc) - t0;
//$$                     double d = eyeDist(mc, chest);
//$$                     if (mc.player == null || mc.player.isDead()) { result = "DIED"; break; }
//$$                     if (el % 20 == 0) Debug.logHarness(String.format(Locale.ROOT, "WRECK t=%d pos=%.1f,%.1f,%.1f d=%.1f air=%d", el, mc.player.getX(), mc.player.getY(), mc.player.getZ(), d, mc.player.getAir()));
//$$                     if (d < bestD - 1.0) { bestD = d; bestAt = el; }
//$$                     if (!baritone.getCustomGoalProcess().isActive() && el > 40) { result = d < 4.5 ? "GOAL" : "STOPPED"; break; }
//$$                     if (el - bestAt > 600) { result = "STALLED"; break; }
//$$                     if (el > limitTicks) break;
//$$                 }
//$$                 mc.execute(() -> baritone.getPathingBehavior().cancelEverything());
//$$                 long ticks = worldTime(mc) - t0;
//$$                 double end = eyeDist(mc, chest);
//$$                 boolean opened = false; int items = -1;
//$$                 if (result.equals("GOAL") || end < 4.5) {
//$$                     mc.execute(() -> mc.interactionManager.interactBlock(mc.player, mc.world, net.minecraft.util.Hand.MAIN_HAND,
//$$                             new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(chest), net.minecraft.util.math.Direction.UP, chest, false)));
//$$                     Thread.sleep(1500);
//$$                     opened = mc.player.currentScreenHandler != mc.player.playerScreenHandler;
//$$                     if (opened) {
//$$                         items = 0;
//$$                         for (int i = 0; i < 27; i++) if (!mc.player.currentScreenHandler.getSlot(i).getStack().isEmpty()) items++;
//$$                         mc.execute(() -> mc.player.closeHandledScreen());
//$$                     }
//$$                 }
//$$                 if (mc.player != null && mc.player.isDead()) mc.execute(() -> mc.player.requestRespawn());
//$$                 csv.printf(Locale.ROOT, "%d,%d,%d,%d,%.1f,%s,%d,%.2f,%s,%d%n", wi, chest.getX(), chest.getY(), chest.getZ(), startD, result, ticks, end, opened, items);
//$$                 csv.flush();
//$$                 n++;
//$$                 if (opened) ok++;
//$$                 Thread.sleep(1000);
//$$             }
//$$         } finally {
//$$             csv.close();
//$$         }
//$$         Debug.logHarness(String.format(Locale.ROOT, "PATHBENCH SUMMARY mode=wreck opened=%d/%d", ok, n));
//$$     }
//$$
//$$     private static double eyeDist(MinecraftClient mc, BlockPos b) {
//$$         if (mc.player == null) return 1e9;
//$$         double dx = mc.player.getX() - (b.getX() + 0.5), dy = mc.player.getEyeY() - (b.getY() + 0.5), dz = mc.player.getZ() - (b.getZ() + 0.5);
//$$         return Math.sqrt(dx * dx + dy * dy + dz * dz);
//$$     }
//$$
//$$     private static boolean startBaritone(MinecraftClient mc, IBaritone b, BlockPos g) {
//$$         mc.execute(() -> b.getCustomGoalProcess().setGoalAndPath(new GoalBlock(g)));
//$$         return true;
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
//$$                 sp.setAir(sp.getMaxAir());
//$$                 sp.setHealth(sp.getMaxHealth());
//$$                 sp.networkHandler.requestTeleport(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, sp.yaw, sp.pitch);
//$$             }
//$$         });
//$$         // Poll for arrival instead of a fixed sleep: under warp a fixed wait is many game ticks of sinking.
//$$         for (int i = 0; i < 60; i++) {
//$$             Thread.sleep(25);
//$$             if (mc.player != null && mc.player.squaredDistanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 1.0) break;
//$$         }
//$$         Thread.sleep(50);
//$$     }
//$$
//$$     private static long worldTime(MinecraftClient mc) {
//$$         return mc.world == null ? 0 : mc.world.getTime();
//$$     }
//$$
//$$     private static double dist(MinecraftClient mc, BlockPos g) {
//$$         if (mc.player == null) return 1e9;
//$$         double dx = mc.player.getX() - (g.getX() + 0.5), dz = mc.player.getZ() - (g.getZ() + 0.5);
//$$         return Math.sqrt(dx * dx + dz * dz);
//$$     }
//$$
//$$     private static PrintWriter open(String tag) throws java.io.IOException {
//$$         Path dir = Paths.get("pathbench");
//$$         Files.createDirectories(dir);
//$$         Path f = dir.resolve("pathbench_" + tag + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
//$$         Debug.logHarness("PATHBENCH writing " + f.toAbsolutePath());
//$$         return new PrintWriter(Files.newBufferedWriter(f));
//$$     }
//$$ }
//#else
/** PathBench drives the 1.16.1 SIM target only (Baritone internals differ elsewhere). */
public final class PathBench {
    private PathBench() {}
    public static boolean isRunning() { return false; }
    public static String start(String mode, String opt, int reps) { return "PATHBENCH is 1.16.1-only"; }
}
//#endif
