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
//$$                 if (mode.equalsIgnoreCase("travel")) for (String m : (opt == null ? "-" : opt).split("[;+]")) travel(mc, origin, m, Math.max(1, reps));
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
//$$                     double bestD = startD; long bestAt = 0; long lastReq = 0;
//$$                     while (started) {
//$$                         Thread.sleep(25);
//$$                         long el = worldTime(mc) - t0;
//$$                         double d = dist(mc, g);
//$$                         if (firstMove < 0 && Math.abs(d - startD) > 0.5) firstMove = el;
//$$                         if (d < 2.0) { result = "GOAL"; break; }
//$$                         if (d < bestD - 1.0) { bestD = d; bestAt = el; }
//$$                         if (stallTicks > 0 && el - bestAt > stallTicks) { result = "STALLED"; break; }
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
//$$     private static boolean startBaritone(MinecraftClient mc, IBaritone b, BlockPos g) {
//$$         mc.execute(() -> b.getCustomGoalProcess().setGoalAndPath(new GoalBlock(g)));
//$$         return true;
//$$     }
//$$
//$$     private static void teleport(MinecraftClient mc, BlockPos p) throws InterruptedException {
//$$         if (mc.getServer() == null || mc.player == null) return;
//$$         java.util.UUID id = mc.player.getUuid();
//$$         mc.getServer().execute(() -> {
//$$             ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayer(id);
//$$             if (sp != null) {
//$$                 sp.setVelocity(0, 0, 0);
//$$                 sp.fallDistance = 0;
//$$                 sp.networkHandler.requestTeleport(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, sp.yaw, sp.pitch);
//$$             }
//$$         });
//$$         Thread.sleep(1500);
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
