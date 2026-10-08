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
//$$                 else if (mode.equalsIgnoreCase("column")) column(mc, origin, Math.max(1, reps));
//$$                 else if (mode.equalsIgnoreCase("flow")) flow(mc, origin, Math.max(1, reps));
//$$                 else if (mode.equalsIgnoreCase("boat")) boat(mc, origin, Math.max(1, reps));
//$$                 else if (mode.equalsIgnoreCase("cliff")) cliff(mc, origin, Math.max(1, reps));
//$$                 else if (mode.equalsIgnoreCase("portal")) portal(mc, origin, Math.max(1, reps));
//$$                 else if (mode.equalsIgnoreCase("fall")) fall(mc, origin, Math.max(1, reps));
//$$                 else if (mode.equalsIgnoreCase("swim")) swim(mc, origin, Math.max(1, reps));
//$$                 else if (mode.equalsIgnoreCase("elytra")) elytra(mc, origin, opt, Math.max(1, reps));
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
//$$         if (y > 0) {
//$$             // MOTION_BLOCKING counts leaves; drop through canopies so goals sit on the ground.
//$$             BlockPos.Mutable q = new BlockPos.Mutable(x, y - 1, z);
//$$             while (q.getY() > 0 && (mc.world.getBlockState(q).getBlock() instanceof net.minecraft.block.LeavesBlock
//$$                     || mc.world.getBlockState(q).getCollisionShape(mc.world, q).isEmpty())) q.move(0, -1, 0);
//$$             return q.getY() + 1;
//$$         }
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
//$$         // Spawn drifts a few blocks between runs, which moved every goal. Pin the ring centre (x,z) so goal N is the
//$$         // same block each run; "spawn" restores the old behaviour. Default is the first leaf-free baseline's origin.
//$$         String pin = System.getProperty("tenorclef.pathbench.origin", "65,-110").trim();
//$$         if (!pin.equalsIgnoreCase("spawn")) {
//$$             String[] xz = pin.split(",");
//$$             int px = Integer.parseInt(xz[0].trim()), pz = Integer.parseInt(xz[1].trim());
//$$             teleport(mc, new BlockPos(px, 200, pz)); // load the column before reading its surface
//$$             Thread.sleep(1000);
//$$             int py;
//$$             try { py = mc.submit(() -> surfaceY(mc, px, pz)).get(); } catch (Exception e) { py = surfaceY(mc, px, pz); }
//$$             origin = new BlockPos(px, py, pz);
//$$             teleport(mc, origin);
//$$             Debug.logHarness("PATHBENCH travel origin pinned at " + origin.toShortString());
//$$         }
//$$         List<BlockPos> goals = ring(mc, origin);
//$$         // Mob kills were the only travel misses once goals were fixed; they measure luck, not pathing.
//$$         // Run peaceful by default (-Dtenorclef.pathbench.peaceful=false keeps mobs) and restore afterwards.
//$$         net.minecraft.world.Difficulty prevDifficulty = mc.getServer().getSaveProperties().getDifficulty();
//$$         boolean peaceful = Boolean.parseBoolean(System.getProperty("tenorclef.pathbench.peaceful", "true"));
//$$         if (peaceful) mc.getServer().submit(() -> mc.getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true)).get();
//$$         Debug.logHarness("PATHBENCH travel difficulty=" + (peaceful ? "peaceful" : prevDifficulty.getName()));
//$$         long limitTicks = Long.getLong("tenorclef.pathbench.travelTicks", 20L * 90);
//$$         // End a trial early once it stops getting closer; 0 disables.
//$$         long stallTicks = Long.getLong("tenorclef.pathbench.stallTicks", 400L);
//$$         long idleTicks = Long.getLong("tenorclef.pathbench.idleTicks", 80L);
//$$         // Optional goal subset, e.g. -Dtenorclef.pathbench.goals=8,9,10
//$$         String goalSel = System.getProperty("tenorclef.pathbench.goals", "").trim();
//$$         java.util.Set<Integer> only = new java.util.HashSet<>();
//$$         if (!goalSel.isEmpty()) for (String x : goalSel.split(",")) only.add(Integer.parseInt(x.trim()));
//$$         // "ostinato": Baritone's custom goal with movementBackend=tungsten (Ostinato's own Tungsten bridge).
//$$         // "kinematic": Baritone's path driven by Ostinato's physics look-ahead controller.
//$$         boolean viaCustom = mover.equals("baritone") || mover.equals("ostinato") || mover.equals("kinematic") || mover.equals("physics");
//$$         BaritoneAPI.getSettings().movementBackend.value = mover.equals("ostinato") ? "tungsten" : "baritone";
//$$         BaritoneAPI.getSettings().kinematicTravel.value = mover.equals("kinematic");
//$$         BaritoneAPI.getSettings().physicsTravel.value = mover.equals("physics");
//$$         // surface movement faults (e.g. M01 kinematic hand-back); the default sink discards them
//$$         java.util.function.BiConsumer<String, String> prevFault = BaritoneAPI.getSettings().movementFault.value;
//$$         BaritoneAPI.getSettings().movementFault.value = (code, evidence) -> Debug.logHarness("PATHBENCH FAULT " + code + " " + evidence);
//$$         PrintWriter csv = open("travel_" + mover);
//$$         csv.println("mover,goal,dx,dz,dist,rep,result,ticks,endDist,firstMoveTicks");
//$$         int ok = 0, n = 0, moved = 0, missDied = 0; long sumTicks = 0, sumFirst = 0; double sumEnd = 0;
//$$         try {
//$$             for (int gi = 0; gi < goals.size(); gi++) {
//$$                 if (!only.isEmpty() && !only.contains(gi)) continue;
//$$                 BlockPos g = goals.get(gi);
//$$                 for (int r = 0; r < reps; r++) {
//$$                     teleport(mc, origin);
//$$                     long t0 = worldTime(mc);
//$$                     if (!peaceful && mc.world != null && mc.player != null) { // world state that can differ between trials when mobs are on
//$$                         int hostiles = mc.world.getEntities(net.minecraft.entity.mob.HostileEntity.class, mc.player.getBoundingBox().expand(32), e -> true).size();
//$$                         Debug.logHarness(String.format(Locale.ROOT, "PATHBENCH TRIALSTATE goal=%d rep=%d timeOfDay=%d hostiles32=%d", gi, r, mc.world.getTimeOfDay() % 24000, hostiles));
//$$                     }
//$$                     boolean started = mover.equals("tungsten") ? TungstenMovement.requestPathTo(g) : mover.equals("guided") ? startGuided(mc, baritone, g) : startBaritone(mc, baritone, g);
//$$                     long firstMove = -1;
//$$                     long kin0 = kinTicks(), kinAtBest = kin0; // attributes a stall to the kinematic controller or to Baritone
//$$                     double startD = dist(mc, g);
//$$                     String result = started ? "TIMEOUT" : "NOSTART";
//$$                     double bestD = startD; long bestAt = 0; long lastReq = 0; double lastD = startD; long lastMoveAt = 0;
//$$                     // -Dtenorclef.pathbench.trace=x,z logs every poll while the player is within 3 blocks of (x, z)
//$$                     String tr = System.getProperty("tenorclef.pathbench.trace");
//$$                     double[] trace = tr == null ? null : java.util.Arrays.stream(tr.split(",")).mapToDouble(Double::parseDouble).toArray();
//$$                     // -Dtenorclef.pathbench.route=N logs a breadcrumb every N ticks so routes can be compared across runs
//$$                     long routeEvery = Long.getLong("tenorclef.pathbench.route", 0L), lastRoute = -1;
//$$                     int deaths = 0; boolean wasDead = false; // mob kills are bench noise, not a mover fault; count them so misses can be attributed
//$$                     while (started) {
//$$                         Thread.sleep(25);
//$$                         long el = worldTime(mc) - t0;
//$$                         boolean dead = mc.player == null || mc.player.isDead();
//$$                         if (dead && !wasDead) deaths++;
//$$                         wasDead = dead;
//$$                         double d = dist(mc, g);
//$$                         if (firstMove < 0 && Math.abs(d - startD) > 0.5) firstMove = el;
//$$                         if (d < 2.0) { result = "GOAL"; break; }
//$$                         if (trace != null && mc.player != null && Math.abs(mc.player.getX() - trace[0]) < 3 && Math.abs(mc.player.getZ() - trace[1]) < 3) {
//$$                             net.minecraft.util.math.Vec3d v = mc.player.getVelocity();
//$$                             Debug.logHarness(String.format(Locale.ROOT, "PATHBENCH TRACE t=%d pos=%.2f,%.2f,%.2f vel=%.2f,%.2f,%.2f ground=%s hcoll=%s yaw=%.0f %s",
//$$                                     el, mc.player.getX(), mc.player.getY(), mc.player.getZ(), v.x, v.y, v.z, mc.player.isOnGround(), mc.player.horizontalCollision, mc.player.yaw, execState(baritone)));
//$$                         }
//$$                         if (routeEvery > 0 && mc.player != null && el / routeEvery != lastRoute) {
//$$                             lastRoute = el / routeEvery;
//$$                             Debug.logHarness(String.format(Locale.ROOT, "PATHBENCH ROUTE t=%d age=%d food=%d sprint=%s pos=%.1f,%.1f,%.1f d=%.1f %s", el, mc.player.age, mc.player.getHungerManager().getFoodLevel(), mc.player.isSprinting(), mc.player.getX(), mc.player.getY(), mc.player.getZ(), d, execState(baritone)));
//$$                         }
//$$                         if (d < bestD - 1.0) { bestD = d; bestAt = el; kinAtBest = kinTicks(); }
//$$                         if (stallTicks > 0 && el - bestAt > stallTicks) { result = "STALLED"; break; }
//$$                         if (Math.abs(d - lastD) > 0.3) { lastD = d; lastMoveAt = el; }
//$$                         if (!viaCustom && idleTicks > 0 && firstMove >= 0 && el - lastMoveAt > idleTicks && el - lastReq > idleTicks && el < limitTicks) {
//$$                             TungstenMovement.cancel(); Thread.sleep(100);
//$$                             if (mover.equals("guided")) startGuided(mc, baritone, g); else TungstenMovement.requestPathTo(g);
//$$                             lastReq = el; lastMoveAt = el; continue;
//$$                         }
//$$                         boolean active = !viaCustom ? TungstenMovement.isPathing() : baritone.getCustomGoalProcess().isActive();
//$$                         if (!active && el - lastReq > 40) {
//$$                             if (mover.equals("tungsten") && el < limitTicks) { TungstenMovement.requestPathTo(g); lastReq = el; continue; }
//$$                             if (mover.equals("guided") && el < limitTicks) { startGuided(mc, baritone, g); lastReq = el; continue; }
//$$                             result = "STOPPED"; break;
//$$                         }
//$$                         if (el > limitTicks) break;
//$$                     }
//$$                     // -Dtenorclef.pathbench.tail=N keeps the path running N ticks after the trial ends and logs each poll (for faults after the goal)
//$$                     long tail = Long.getLong("tenorclef.pathbench.tail", 0L), tailEnd = worldTime(mc) + tail;
//$$                     while (tail > 0 && worldTime(mc) < tailEnd && mc.player != null) {
//$$                         Thread.sleep(25);
//$$                         net.minecraft.util.math.Vec3d v = mc.player.getVelocity();
//$$                         Debug.logHarness(String.format(Locale.ROOT, "PATHBENCH TAIL t=%d pos=%.2f,%.2f,%.2f vel=%.2f,%.2f,%.2f ground=%s hcoll=%s %s",
//$$                                 worldTime(mc) - t0, mc.player.getX(), mc.player.getY(), mc.player.getZ(), v.x, v.y, v.z, mc.player.isOnGround(), mc.player.horizontalCollision, execState(baritone)));
//$$                     }
//$$                     boolean activeAtEnd =!viaCustom ? TungstenMovement.isPathing() : baritone.getCustomGoalProcess().isActive();
//$$                     long kinNow = kinTicks();
//$$                     boolean pathAtEnd = viaCustom && baritone.getPathingBehavior().hasPath();
//$$                     boolean calcAtEnd = viaCustom && baritone.getPathingBehavior().getInProgress().isPresent();
//$$                     if (!viaCustom) TungstenMovement.cancel();
//$$                     else mc.execute(() -> baritone.getPathingBehavior().cancelEverything());
//$$                     long ticks = worldTime(mc) - t0;
//$$                     double end = dist(mc, g);
//$$                     csv.printf(Locale.ROOT, "%s,%d,%d,%d,%d,%d,%s,%d,%.2f,%d%n", mover, gi, g.getX() - origin.getX(), g.getZ() - origin.getZ(),
//$$                             (int) Math.round(Math.sqrt(g.getSquaredDistance(origin))), r, result, ticks, end, firstMove);
//$$                     csv.flush();
//$$                     // Per-trial diagnostics so a stall can be located after the fact (CSV lacks position/progress history).
//$$                     BlockPos endPos = mc.player == null ? BlockPos.ORIGIN : mc.player.getBlockPos();
//$$                     Debug.logHarness(String.format(Locale.ROOT, "PATHBENCH TRIAL mover=%s goal=%d rep=%d result=%s ticks=%d target=%s end=%s endDist=%.1f bestDist=%.1f bestAt=%d lastMoveAt=%d firstMove=%d activeAtEnd=%s pathAtEnd=%s calcAtEnd=%s kinDriven=%d kinSinceBest=%d deaths=%d",
//$$                             mover, gi, r, result, ticks, g.toShortString() + "(" + (mc.world == null ? "?" : mc.world.getBlockState(g.down()).getBlock().getTranslationKey()) + ")", endPos.toShortString(), end, bestD, bestAt, lastMoveAt, firstMove, activeAtEnd, pathAtEnd, calcAtEnd, kinNow - kin0, kinNow - kinAtBest, deaths));
//$$                     n++;
//$$                     if (result.equals("GOAL")) { ok++; sumTicks += ticks; } else if (deaths > 0) missDied++;
//$$                     if (firstMove >= 0) { moved++; sumFirst += firstMove; }
//$$                     sumEnd += end;
//$$                     Thread.sleep(500);
//$$                 }
//$$             }
//$$         } finally {
//$$             BaritoneAPI.getSettings().movementBackend.value = "baritone";
//$$             BaritoneAPI.getSettings().kinematicTravel.value = false;
//$$             BaritoneAPI.getSettings().physicsTravel.value = false;
//$$             BaritoneAPI.getSettings().movementFault.value = prevFault;
//$$             if (peaceful) mc.getServer().submit(() -> mc.getServer().setDifficulty(prevDifficulty, true));
//$$             csv.close();
//$$         }
//$$         Debug.logHarness(String.format(Locale.ROOT, "PATHBENCH SUMMARY mode=travel mover=%s goalRate=%d/%d avgGoalTicks=%.0f avgFirstMoveTicks=%.1f avgEndDist=%.1f missesAfterDeath=%d",
//$$                 mover, ok, n, ok == 0 ? 0 : sumTicks / (double) ok, moved == 0 ? -1 : sumFirst / (double) moved, n == 0 ? 0 : sumEnd / n, missDied));
//$$     }
//$$
//$$     // ---- flow --------------------------------------------------------------------------
//$$
//$$     /**
//$$      * Flowing water: (a) a 1x1 waterfall shaft 16 high fed by one source at the top, goal on the ledge
//$$      * above; (b) a sloped stream running down a staircase, goal at its source end; (c) the same staircase dry.
//$$      */
//$$     private static void flow(MinecraftClient mc, BlockPos origin, int reps) throws Exception {
//$$         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
//$$         BaritoneAPI.getSettings().chatDebug.value = true;
//$$         int by = 120, ox = origin.getX(), oz = origin.getZ(), H = 16;
//$$         java.util.concurrent.CompletableFuture<Void> built = new java.util.concurrent.CompletableFuture<>();
//$$         mc.getServer().execute(() -> {
//$$             net.minecraft.server.world.ServerWorld w = mc.getServer().getOverworld();
//$$             net.minecraft.block.BlockState G = net.minecraft.block.Blocks.STONE.getDefaultState(), A = net.minecraft.block.Blocks.AIR.getDefaultState();
//$$             // (a) shaft at x in [-3,3]: stone block with a 1x1 hole, source on top, a 3-wide ledge to step onto
//$$             for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = by - 1; y <= by + H + 3; y++)
//$$                 w.setBlockState(new BlockPos(ox + x, y, oz + z), (y < by + H && y >= by && (x != 0 || z != 0)) || y == by - 1 || (y == by + H - 1 && z != 0) ? G : A, 2);
//$$             for (int y = by; y < by + H; y++) w.setBlockState(new BlockPos(ox, y, oz), A, 2);
//$$             // start area: floor pool 3x3 around the shaft foot, open sideways at z=-1..1, x=-3..-1
//$$             for (int x = -3; x <= -1; x++) for (int y = by; y <= by + 1; y++) w.setBlockState(new BlockPos(ox + x, y, oz), A, 2);
//$$             w.setBlockState(new BlockPos(ox, by + H, oz + 1), G, 2);
//$$             w.setBlockState(new BlockPos(ox, by + H - 1, oz + 1), G, 2);
//$$             w.setBlockState(new BlockPos(ox, by + H, oz), net.minecraft.block.Blocks.WATER.getDefaultState(), 3);
//$$             // (b) staircase stream at z+20: 12 steps down along +x, a source at the top step
//$$             for (int x = -1; x <= 13; x++) for (int z = 18; z <= 22; z++) for (int y = by - 1; y <= by + 16; y++) {
//$$                 int top = by + 12 - Math.max(0, Math.min(12, x));
//$$                 boolean wall = z == 18 || z == 22 || x == -1 || x == 13 || y < top;
//$$                 w.setBlockState(new BlockPos(ox + x, y, oz + z), wall && y <= by + 14 ? G : A, 2);
//$$             }
//$$             for (int z = 19; z <= 21; z++) w.setBlockState(new BlockPos(ox, by + 12, oz + z), net.minecraft.block.Blocks.WATER.getDefaultState(), 3);
//$$             // (c) the same staircase dry at z+30: the walking baseline for (b)
//$$             for (int x = -1; x <= 13; x++) for (int z = 28; z <= 32; z++) for (int y = by - 1; y <= by + 16; y++) {
//$$                 int top = by + 12 - Math.max(0, Math.min(12, x));
//$$                 boolean wall = z == 28 || z == 32 || x == -1 || x == 13 || y < top;
//$$                 w.setBlockState(new BlockPos(ox + x, y, oz + z), wall && y <= by + 14 ? G : A, 2);
//$$             }
//$$             built.complete(null);
//$$         });
//$$         built.get();
//$$         Thread.sleep(8000); // let the water spread
//$$         BlockPos[][] cases = {
//$$                 {new BlockPos(ox - 2, by, oz), new BlockPos(ox, by + H, oz - 1)},
//$$                 {new BlockPos(ox + 12, by + 1, oz + 20), new BlockPos(ox, by + 12, oz + 20)},
//$$                 {new BlockPos(ox + 12, by + 1, oz + 30), new BlockPos(ox, by + 12, oz + 30)},
//$$         };
//$$         PrintWriter csv = open("flow_baritone");
//$$         csv.println("case,rep,result,ticks,endDist");
//$$         int ok = 0, n = 0;
//$$         try {
//$$             for (int ci = 0; ci < cases.length; ci++) for (int r = 0; r < reps; r++) {
//$$                 BlockPos g = cases[ci][1];
//$$                 teleport(mc, cases[ci][0]);
//$$                 long t0 = worldTime(mc), last = -1;
//$$                 startBaritone(mc, baritone, g);
//$$                 String result = "TIMEOUT";
//$$                 while (true) {
//$$                     Thread.sleep(25);
//$$                     long el = worldTime(mc) - t0;
//$$                     if (el == last) continue;
//$$                     last = el;
//$$                     if (dist3(mc, g) < 1.5) { result = "GOAL"; break; }
//$$                     if (mc.player.isDead()) { result = "DIED"; break; }
//$$                     if (el % 40 == 0) Debug.logHarness(String.format(Locale.ROOT, "FLOW c=%d t=%d p=%.1f,%.1f,%.1f d=%.1f m=%s", ci, el, mc.player.getX() - ox, mc.player.getY() - by, mc.player.getZ() - oz, dist3(mc, g), flowMove()));
//$$                     if (el > 40 && !baritone.getCustomGoalProcess().isActive()) { result = "STOPPED"; break; }
//$$                     if (el > 20 * 60) break;
//$$                 }
//$$                 mc.execute(() -> baritone.getPathingBehavior().cancelEverything());
//$$                 csv.printf(Locale.ROOT, "%d,%d,%s,%d,%.1f%n", ci, r, result, worldTime(mc) - t0, dist3(mc, g));
//$$                 csv.flush();
//$$                 n++;
//$$                 if (result.equals("GOAL")) ok++;
//$$                 Thread.sleep(500);
//$$             }
//$$         } finally {
//$$             csv.close();
//$$         }
//$$         Debug.logHarness(String.format(Locale.ROOT, "PATHBENCH SUMMARY mode=flow goalRate=%d/%d", ok, n));
//$$     }
//$$
//$$     // ---- column ------------------------------------------------------------------------
//$$
//$$     /**
//$$      * Roofed water tunnel (no surface to breathe at) 120 long: a magma column at +40 and a soul-sand
//$$      * column at +80 are the only air. Goal at the far end; without using the columns the bot drowns.
//$$      */
//$$     private static void column(MinecraftClient mc, BlockPos origin, int reps) throws Exception {
//$$         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
//$$         BaritoneAPI.getSettings().chatDebug.value = true;
//$$         // doorCourse: no columns, one oak door carried; the only air is a door pocket (Ostinato allowDoorAirPockets).
//$$         boolean doorCourse = Boolean.getBoolean("tenorclef.pathbench.doorCourse");
//$$         int L = 120, by = 100, ox = origin.getX(), oz = origin.getZ();
//$$         java.util.concurrent.CompletableFuture<Void> built = new java.util.concurrent.CompletableFuture<>();
//$$         mc.getServer().execute(() -> {
//$$             net.minecraft.server.world.ServerWorld w = mc.getServer().getOverworld();
//$$             for (int x = -2; x <= L + 2; x++) for (int z = -2; z <= 2; z++) for (int y = by - 1; y <= by + 4; y++) {
//$$                 boolean wall = x < 0 || x > L || Math.abs(z) > 1 || y < by || y > by + 3;
//$$                 w.setBlockState(new BlockPos(ox + x, y, oz + z), wall ? net.minecraft.block.Blocks.GLASS.getDefaultState() : net.minecraft.block.Blocks.WATER.getDefaultState(), 2);
//$$             }
//$$             if (!doorCourse) {
//$$                 w.setBlockState(new BlockPos(ox + 40, by - 1, oz), net.minecraft.block.Blocks.MAGMA_BLOCK.getDefaultState(), 3);
//$$                 w.setBlockState(new BlockPos(ox + 80, by - 1, oz), net.minecraft.block.Blocks.SOUL_SAND.getDefaultState(), 3);
//$$             }
//$$             built.complete(null);
//$$         });
//$$         built.get();
//$$         Thread.sleep(3000);
//$$         BlockPos start = new BlockPos(ox + 1, by + 1, oz), g = new BlockPos(ox + L - 1, by, oz), mag = new BlockPos(ox + 40, by - 1, oz);
//$$         Debug.logHarness("PATHBENCH column cells magma=" + mc.world.getBlockState(mag.up(2)).getBlock() + " soul=" + mc.world.getBlockState(mag.add(40, 2, 0)).getBlock());
//$$         PrintWriter csv = open(doorCourse ? "door_baritone" : "column_baritone");
//$$         csv.println("rep,result,ticks,minAir,minHealth,onMagma,onMagmaUnsneaked,colTicks,doorTicks,doorsLeft,doorBlocksLeft");
//$$         int ok = 0, n = 0;
//$$         try {
//$$             for (int r = 0; r < reps; r++) {
//$$                 teleport(mc, start);
//$$                 mc.execute(() -> { mc.player.setAir(120); mc.player.setHealth(20); });
//$$                 if (doorCourse) {
//$$                     java.util.concurrent.CompletableFuture<Void> inv = new java.util.concurrent.CompletableFuture<>();
//$$                     mc.getServer().execute(() -> {
//$$                         net.minecraft.server.world.ServerWorld w = mc.getServer().getOverworld();
//$$                         for (int x = 0; x <= L; x++) for (int z = -1; z <= 1; z++) for (int y = by; y <= by + 3; y++) {
//$$                             BlockPos q = new BlockPos(ox + x, y, oz + z);
//$$                             if (!w.getBlockState(q).isOf(net.minecraft.block.Blocks.WATER)) w.setBlockState(q, net.minecraft.block.Blocks.WATER.getDefaultState(), 2);
//$$                         }
//$$                         w.getEntities(net.minecraft.entity.ItemEntity.class, new net.minecraft.util.math.Box(ox - 2, by - 2, oz - 3, ox + L + 2, by + 5, oz + 3), e -> true).forEach(net.minecraft.entity.Entity::remove);
//$$                         net.minecraft.server.network.ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayerList().get(0);
//$$                         sp.inventory.clear();
//$$                         sp.inventory.insertStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.OAK_DOOR, 1));
//$$                         inv.complete(null);
//$$                     });
//$$                     inv.get();
//$$                 }
//$$                 Thread.sleep(200);
//$$                 long t0 = worldTime(mc), last = -1;
//$$                 startBaritone(mc, baritone, g);
//$$                 int minAir = 300, onMagma = 0, bad = 0, col = 0, doorT = 0; float minHp = 20;
//$$                 String result = "TIMEOUT";
//$$                 while (true) {
//$$                     Thread.sleep(25);
//$$                     long el = worldTime(mc) - t0;
//$$                     if (el == last) continue;
//$$                     last = el;
//$$                     minAir = Math.min(minAir, mc.player.getAir());
//$$                     minHp = Math.min(minHp, mc.player.getHealth());
//$$                     if (mc.world.getBlockState(mc.player.getBlockPos().down()).getBlock() == net.minecraft.block.Blocks.MAGMA_BLOCK && mc.player.isOnGround()) { onMagma++; if (!mc.player.isSneaking()) bad++; }
//$$                     if (mc.world.getBlockState(new BlockPos(mc.player.getCameraPosVec(1))).getBlock() == net.minecraft.block.Blocks.BUBBLE_COLUMN) col++;
//$$                     if (mc.world.getBlockState(new BlockPos(mc.player.getCameraPosVec(1))).getBlock() instanceof net.minecraft.block.DoorBlock) doorT++;
//$$                     if (dist3(mc, g) < 1.5) { result = "GOAL"; break; }
//$$                     if (mc.player.isDead()) { result = "DIED"; break; }
//$$                     if (el % 40 == 0) Debug.logHarness(String.format(Locale.ROOT, "COLUMN t=%d x=%.1f y=%.1f air=%d hp=%.0f", el, mc.player.getX() - ox, mc.player.getY(), mc.player.getAir(), mc.player.getHealth()));
//$$                     if (el > 20 * 120) break;
//$$                 }
//$$                 mc.execute(() -> baritone.getPathingBehavior().cancelEverything());
//$$                 int doorsLeft = mc.player.inventory.count(net.minecraft.item.Items.OAK_DOOR), doorBlocks = 0;
//$$                 for (int x = 0; x <= L; x++) for (int z = -1; z <= 1; z++) for (int y = by; y <= by + 3; y++)
//$$                     if (mc.world.getBlockState(new BlockPos(ox + x, y, oz + z)).getBlock() instanceof net.minecraft.block.DoorBlock) doorBlocks++;
//$$                 csv.printf(Locale.ROOT, "%d,%s,%d,%d,%.0f,%d,%d,%d,%d,%d,%d%n", r, result, worldTime(mc) - t0, minAir, minHp, onMagma, bad, col, doorT, doorsLeft, doorBlocks);
//$$                 csv.flush();
//$$                 n++;
//$$                 if (result.equals("GOAL")) ok++;
//$$                 if (mc.player.isDead()) { mc.execute(() -> mc.player.requestRespawn()); Thread.sleep(2000); }
//$$                 Thread.sleep(500);
//$$             }
//$$         } finally {
//$$             csv.close();
//$$         }
//$$         Debug.logHarness(String.format(Locale.ROOT, "PATHBENCH SUMMARY mode=column goalRate=%d/%d", ok, n));
//$$     }
//$$
//$$    // ---- portal ------------------------------------------------------------------------
//$$
//$$    /**
//$$     * Fall clutch in isolation: drop from H blocks above a stone floor holding one water bucket
//$$     * (in the main inventory, like a real run) and no user task. SAFE when the player lands alive.
//$$     */
//$$    private static void fall(MinecraftClient mc, BlockPos origin, int reps) throws Exception {
//$$        int ox = origin.getX() + 40, oz = origin.getZ(), by = 100;
//$$        PrintWriter csv = open("fall");
//$$        csv.println("height,rep,result,ticks,hpLost");
//$$        int ok = 0, n = 0;
//$$        try {
//$$            for (int H : new int[]{12, 25, 45}) {
//$$                for (int r = 0; r < reps; r++) {
//$$                    java.util.concurrent.CompletableFuture<Void> built = new java.util.concurrent.CompletableFuture<>();
//$$                    mc.getServer().execute(() -> {
//$$                        net.minecraft.server.world.ServerWorld w = mc.getServer().getOverworld();
//$$                        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) for (int y = by - 2; y <= by + 60; y++) {
//$$                            net.minecraft.block.BlockState st = y < by ? net.minecraft.block.Blocks.STONE.getDefaultState() : net.minecraft.block.Blocks.AIR.getDefaultState();
//$$                            w.setBlockState(new BlockPos(ox + x, y, oz + z), st, 2);
//$$                        }
//$$                        ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayerList().get(0);
//$$                        sp.inventory.clear();
//$$                        sp.inventory.setStack(20, new net.minecraft.item.ItemStack(net.minecraft.item.Items.WATER_BUCKET));
//$$                        sp.setHealth(sp.getMaxHealth());
//$$                        sp.getHungerManager().setFoodLevel(20);
//$$                        built.complete(null);
//$$                    });
//$$                    built.get();
//$$                    // Chains only tick while a user task runs (TaskRunner.active); real play always has one.
//$$                    mc.execute(() -> adris.altoclef.AltoClef.getInstance().runUserTask(new adris.altoclef.tasks.movement.IdleTask()));
//$$                    Thread.sleep(1500);
//$$                    teleport(mc, new BlockPos(ox, by + H, oz));
//$$                    long t0 = worldTime(mc);
//$$                    String result = "TIMEOUT";
//$$                    while (true) {
//$$                        Thread.sleep(25);
//$$                        long el = worldTime(mc) - t0;
//$$                        if (mc.player.isDead() || mc.player.getHealth() <= 0) { result = "DIED"; break; }
//$$                        if (el > 10 && mc.player.isOnGround() || el > 10 && mc.player.isTouchingWater()) { Thread.sleep(500); result = mc.player.isDead() ? "DIED" : "SAFE"; break; }
//$$                        if (el > 20 * 15) break;
//$$                    }
//$$                    float lost = 20 - mc.player.getHealth();
//$$                    long ticks = worldTime(mc) - t0;
//$$                    csv.printf(Locale.ROOT, "%d,%d,%s,%d,%.1f%n", H, r, result, ticks, lost);
//$$                    csv.flush();
//$$                    Debug.logHarness(String.format(Locale.ROOT, "FALL H=%d rep=%d result=%s ticks=%d hpLost=%.1f", H, r, result, ticks, lost));
//$$                    n++;
//$$                    if (result.equals("SAFE")) ok++;
//$$                    if (result.equals("DIED")) { mc.execute(() -> mc.player.requestRespawn()); Thread.sleep(3000); }
//$$                    Thread.sleep(1000);
//$$                }
//$$            }
//$$        } finally {
//$$            csv.close();
//$$        }
//$$        Debug.logHarness("PATHBENCH SUMMARY mode=fall safe=" + ok + "/" + n);
//$$    }
//$$
//$$    /**
//$$     * Nether-portal bucket build in isolation: a flat stone pad at y=120 with a 5x5 lava pool and a
//$$     * 3x3 water pool, rebuilt each trial. Kit: iron pickaxe, 2 buckets, flint and steel, 32 cobblestone. Runs
//$$     * ConstructNetherPortalBucketTask; GOAL when a nether portal block appears on the pad.
//$$     */
//$$    private static void portal(MinecraftClient mc, BlockPos origin, int reps) throws Exception {
//$$        int ox = origin.getX(), oz = origin.getZ(), y0 = 120; // sky pad: no natural water/lava/caves under it
//$$        PrintWriter csv = open("portal");
//$$        csv.println("rep,result,ticks,lastState,lastLineState");
//$$        int ok = 0, n = 0; long sumTicks = 0;
//$$        try {
//$$            for (int r = 0; r < reps; r++) {
//$$                java.util.concurrent.CompletableFuture<Void> built = new java.util.concurrent.CompletableFuture<>();
//$$                final int r0 = r;
//$$                mc.getServer().execute(() -> {
//$$                    net.minecraft.server.world.ServerWorld w = mc.getServer().getOverworld();
//$$                    for (int x = -16; x <= 26; x++) for (int z = -14; z <= 14; z++) {
//$$                        for (int y = y0 - 3; y <= y0 + 10; y++) {
//$$                            net.minecraft.block.BlockState st = y < y0 ? net.minecraft.block.Blocks.STONE.getDefaultState() : net.minecraft.block.Blocks.AIR.getDefaultState();
//$$                            if (y == y0 - 1 && x >= 8 && x <= 12 && Math.abs(z) <= 2) st = net.minecraft.block.Blocks.LAVA.getDefaultState();
//$$                            if (y == y0 - 1 && x >= -9 && x <= -7 && Math.abs(z) <= 1) st = net.minecraft.block.Blocks.WATER.getDefaultState();
//$$                            // Bedrock rim: an open sky-pad edge is a 50-block drop real terrain never has (trace rep0 died walking off it).
//$$                            if ((x == -16 || x == 26 || Math.abs(z) == 14) && y >= y0 && y <= y0 + 3) st = net.minecraft.block.Blocks.BEDROCK.getDefaultState();
//$$                            w.setBlockState(new BlockPos(ox + x, y, oz + z), st, 2);
//$$                        }
//$$                    }
//$$                    // Seed 12345 has natural lava below the pad (y=52, y=71) that the task preferred over the pad pool.
//$$                    // Replace all lava within 64 blocks below the pad with stone so the pad pool is the only lake.
//$$                    if (r0 == 0) for (int x = -64; x <= 64; x++) for (int z = -64; z <= 64; z++) for (int y = 1; y < y0 - 3; y++) {
//$$                        BlockPos lp = new BlockPos(ox + x, y, oz + z);
//$$                        if (w.getBlockState(lp).getBlock() == net.minecraft.block.Blocks.LAVA) w.setBlockState(lp, net.minecraft.block.Blocks.STONE.getDefaultState(), 2);
//$$                    }
//$$                    w.getEntities(net.minecraft.entity.ItemEntity.class, new net.minecraft.util.math.Box(ox - 64, 0, oz - 64, ox + 64, y0 + 11, oz + 64), e -> true).forEach(net.minecraft.entity.Entity::remove);
//$$                    ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayerList().get(0);
//$$                    sp.inventory.clear();
//$$                    sp.inventory.insertStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_PICKAXE));
//$$                    sp.inventory.insertStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.BUCKET, 2));
//$$                    sp.inventory.insertStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.FLINT_AND_STEEL));
//$$                    sp.inventory.insertStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.COBBLESTONE, 32)); // cast-frame throwaways: PORTAL phase arrives with cobble
//$$                    built.complete(null);
//$$                });
//$$                built.get();
//$$                teleport(mc, new BlockPos(ox, y0, oz));
//$$                // Fresh scanner per rep: its blacklist and cached positions otherwise carry over from the previous rep.
//$$                mc.execute(() -> adris.altoclef.AltoClef.getInstance().getBlockScanner().reset());
//$$                // Wait until the scanner knows the pad lava; 1.5s was not enough and the task picked a natural lake below.
//$$                final BlockPos padLava = new BlockPos(ox + 10, y0 - 1, oz);
//$$                boolean seen = false;
//$$                for (int i = 0; i < 200 && !seen; i++) {
//$$                    Thread.sleep(100);
//$$                    seen = adris.altoclef.AltoClef.getInstance().getBlockScanner().getKnownLocations(net.minecraft.block.Blocks.LAVA).stream().anyMatch(p -> p.isWithinDistance(padLava, 3));
//$$                }
//$$                Debug.logHarness("PORTAL rep=" + r + " origin=" + ox + "," + y0 + "," + oz + " pool=x" + (ox + 8) + ".." + (ox + 12) + ",y" + (y0 - 1) + ",z" + (oz - 2) + ".." + (oz + 2) + " scannerSawPadLava=" + seen);
//$$                adris.altoclef.tasks.construction.compound.ConstructNetherPortalBucketTask task = new adris.altoclef.tasks.construction.compound.ConstructNetherPortalBucketTask();
//$$                mc.execute(() -> adris.altoclef.AltoClef.getInstance().runUserTask(task));
//$$                long t0 = worldTime(mc), last = -1;
//$$                String result = "TIMEOUT", state = "", lastLogged = "", lastChain = "", lastHazard = "";
//$$                int lavaBuckets = 0; long traceUntil = -1;
//$$                while (true) {
//$$                    Thread.sleep(25);
//$$                    long el = worldTime(mc) - t0;
//$$                    if (el == last) continue;
//$$                    last = el;
//$$                    String ds = task.getDebugState();
//$$                    if (ds != null) state = ds;
//$$                    if (!state.equals(lastLogged)) { Debug.logHarness("PORTAL t=" + el + " state=" + state); lastLogged = state; }
//$$                    StringBuilder chain = new StringBuilder();
//$$                    for (adris.altoclef.tasksystem.Task c = task.getSub(); c != null && chain.length() < 400; c = c.getSub()) chain.append(c.getClass().getSimpleName()).append('>');
//$$                    if (!chain.toString().equals(lastChain)) { Debug.logHarness("PORTAL t=" + el + " chain=" + chain); lastChain = chain.toString(); }
//$$                    // Per-tick trace from each lava pickup until lava entry (or 60 ticks): which movement puts us in the pool?
//$$                    int lb = mc.player.inventory.count(net.minecraft.item.Items.LAVA_BUCKET);
//$$                    if (lb > lavaBuckets) traceUntil = el + 60;
//$$                    lavaBuckets = lb;
//$$                    if (el <= traceUntil) {
//$$                        String mv = "none";
//$$                        try {
//$$                            baritone.api.pathing.path.IPathExecutor ex = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getCurrent();
//$$                            if (ex != null && ex.getPosition() < ex.getPath().movements().size()) {
//$$                                baritone.api.pathing.movement.IMovement m = ex.getPath().movements().get(ex.getPosition());
//$$                                mv = m.getClass().getSimpleName() + " " + m.getSrc().toShortString() + "->" + m.getDest().toShortString();
//$$                            }
//$$                        } catch (Throwable ignored) { mv = "err"; }
//$$                        net.minecraft.util.math.Vec3d v = mc.player.getVelocity();
//$$                        Debug.logHarness(String.format(Locale.ROOT, "TICK t=%d xyz=%.2f,%.2f,%.2f vel=%.2f,%.2f,%.2f ground=%b fwd=%b sneak=%b lava=%b mv=%s chain=%s", el, mc.player.getX(), mc.player.getY(), mc.player.getZ(), v.x, v.y, v.z, mc.player.isOnGround(), mc.options.keyForward.isPressed(), mc.options.keySneak.isPressed(), mc.player.isInLava(), mv, chain));
//$$                        if (mc.player.isInLava()) traceUntil = -1;
//$$                    }
//$$                    if (mc.player.isOnFire() || mc.player.isInLava() || mc.player.getHealth() < 20) {
//$$                        String hz = String.format(Locale.ROOT, "hp=%.0f fire=%b lava=%b pos=%s", mc.player.getHealth(), mc.player.isOnFire(), mc.player.isInLava(), mc.player.getBlockPos().toShortString());
//$$                        if (!hz.equals(lastHazard)) { Debug.logHarness("PORTAL t=" + el + " " + hz + " chain=" + chain); lastHazard = hz; }
//$$                    }
//$$                    if (el % 200 == 0) {
//$$                        int lavaLeft = 0, obsInPool = 0;
//$$                        for (int x = 8; x <= 12; x++) for (int z = -2; z <= 2; z++) {
//$$                            net.minecraft.block.BlockState bs = mc.world.getBlockState(new BlockPos(ox + x, y0 - 1, oz + z));
//$$                            if (bs.getFluidState().isStill() && bs.getFluidState().getFluid() == net.minecraft.fluid.Fluids.LAVA) lavaLeft++;
//$$                            if (bs.getBlock() == net.minecraft.block.Blocks.OBSIDIAN || bs.getBlock() == net.minecraft.block.Blocks.COBBLESTONE) obsInPool++;
//$$                        }
//$$                        Debug.logHarness(String.format(Locale.ROOT, "PORTAL t=%d pos=%s state=%s poolLava=%d poolSolid=%d", el, mc.player.getBlockPos().toShortString(), state, lavaLeft, obsInPool));
//$$                    }
//$$                    if (portalOnPad(mc, ox, y0, oz)) { result = "GOAL"; break; }
//$$                    if (mc.player.isDead()) { result = "DIED"; break; }
//$$                    if (el > 20 * 300) break;
//$$                }
//$$                long ticks = worldTime(mc) - t0;
//$$                mc.execute(() -> adris.altoclef.AltoClef.getInstance().stopTasks());
//$$                csv.printf(Locale.ROOT, "%d,%s,%d,\"%s\",\"%s\"%n", r, result, ticks, state.replace('"', '\''), lastLogged.replace('"', '\''));
//$$                csv.flush();
//$$                Debug.logHarness(String.format(Locale.ROOT, "PORTAL rep=%d result=%s ticks=%d state=%s", r, result, ticks, state));
//$$                n++;
//$$                if (result.equals("GOAL")) { ok++; sumTicks += ticks; }
//$$                if (mc.player.isDead()) { mc.execute(() -> mc.player.requestRespawn()); Thread.sleep(2000); }
//$$                Thread.sleep(1000);
//$$            }
//$$        } finally {
//$$            csv.close();
//$$        }
//$$        Debug.logHarness(String.format(Locale.ROOT, "PATHBENCH SUMMARY mode=portal goalRate=%d/%d avgGoalTicks=%.0f", ok, n, ok == 0 ? 0.0 : (double) sumTicks / ok));
//$$    }
//$$
//$$    private static boolean portalOnPad(MinecraftClient mc, int ox, int y0, int oz) {
//$$        for (int x = -16; x <= 26; x++) for (int z = -14; z <= 14; z++) for (int y = y0 - 3; y <= y0 + 10; y++)
//$$            if (mc.world.getBlockState(new BlockPos(ox + x, y, oz + z)).getBlock() == net.minecraft.block.Blocks.NETHER_PORTAL) return true;
//$$        return false;
//$$    }
//$$
//$$     // ---- swim --------------------------------------------------------------------------
//$$
//$$     /** Glass tank of water high above origin; Baritone must reach 3D goals inside it (floor, mid-depth, surface). */
//$$     /** 90-block lake between two shores; runs with a boat in the hotbar and without (swim baseline). */
//$$     private static void boat(MinecraftClient mc, BlockPos origin, int reps) throws Exception {
//$$         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
//$$         BaritoneAPI.getSettings().chatDebug.value = true;
//$$         int L = 90, W = 10, by = 200, ox = origin.getX(), oz = origin.getZ();
//$$         java.util.concurrent.CompletableFuture<Void> built = new java.util.concurrent.CompletableFuture<>();
//$$         mc.getServer().execute(() -> {
//$$             net.minecraft.server.world.ServerWorld w = mc.getServer().getOverworld();
//$$             for (int x = -11; x <= L + 11; x++) for (int z = -W - 1; z <= W + 1; z++) for (int y = by - 1; y < by + 6; y++) {
//$$                 boolean wall = x < -10 || x > L + 10 || Math.abs(z) > W || y < by;
//$$                 boolean land = x < 0 || x > L;
//$$                 net.minecraft.block.BlockState st = wall ? net.minecraft.block.Blocks.GLASS.getDefaultState()
//$$                         : y < by + 3 ? (land ? net.minecraft.block.Blocks.STONE.getDefaultState() : net.minecraft.block.Blocks.WATER.getDefaultState())
//$$                         : net.minecraft.block.Blocks.AIR.getDefaultState();
//$$                 if (wall && y >= by + 3) st = net.minecraft.block.Blocks.AIR.getDefaultState();
//$$                 w.setBlockState(new BlockPos(ox + x, y, oz + z), st, 2);
//$$             }
//$$             built.complete(null);
//$$         });
//$$         built.get();
//$$         Thread.sleep(3000);
//$$         BlockPos start = new BlockPos(ox - 5, by + 3, oz);
//$$         BlockPos g = new BlockPos(ox + L + 5, by + 3, oz);
//$$         long limitTicks = Long.getLong("tenorclef.pathbench.travelTicks", 20L * 120);
//$$         PrintWriter csv = open("boat_baritone");
//$$         csv.println("mover,variant,rep,result,ticks,endDist");
//$$         try {
//$$             for (String variant : new String[]{"boat", "swim"}) {
//$$                 for (int r = 0; r < reps; r++) {
//$$                     mc.execute(() -> { for (net.minecraft.entity.Entity e : mc.world.getEntities()) if (e instanceof net.minecraft.entity.vehicle.BoatEntity || e instanceof net.minecraft.entity.ItemEntity) mc.getServer().execute(() -> { net.minecraft.entity.Entity se = mc.getServer().getOverworld().getEntity(e.getUuid()); if (se != null) se.remove(); }); });
//$$                     mc.getServer().execute(() -> {
//$$                         net.minecraft.server.network.ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayerList().get(0);
//$$                         sp.stopRiding(); for (int i = 0; i < sp.inventory.size(); i++) if (sp.inventory.getStack(i).getItem() == net.minecraft.item.Items.OAK_BOAT) sp.inventory.setStack(i, net.minecraft.item.ItemStack.EMPTY);
//$$                         if (variant.equals("boat")) sp.inventory.setStack(20, new net.minecraft.item.ItemStack(net.minecraft.item.Items.OAK_BOAT)); // main inventory: Baritone borrows a hotbar slot
//$$                     });
//$$                     Thread.sleep(500);
//$$                     teleport(mc, start);
//$$                     long t0 = worldTime(mc);
//$$                     startBaritone(mc, baritone, g);
//$$                     double bestD = dist3(mc, g); long bestAt = 0;
//$$                     String result = "TIMEOUT";
//$$                     while (true) {
//$$                         Thread.sleep(25);
//$$                         long el = worldTime(mc) - t0;
//$$                         double d = dist3(mc, g);
//$$                         if (d < 1.5) { result = "GOAL"; break; }
//$$                         if (el % 40 == 0) Debug.logHarness(String.format(Locale.ROOT, "BOAT t=%d pos=%.1f,%.1f,%.1f d=%.1f riding=%s", el, mc.player.getX(), mc.player.getY(), mc.player.getZ(), d, mc.player.hasVehicle()));
//$$                         if (d < bestD - 1.0) { bestD = d; bestAt = el; }
//$$                         if (el - bestAt > 600) { result = "STALLED"; break; }
//$$                         if (el > limitTicks) break;
//$$                     }
//$$                     mc.execute(() -> baritone.getPathingBehavior().cancelEverything());
//$$                     csv.printf(Locale.ROOT, "baritone,%s,%d,%s,%d,%.2f%n", variant, r, result, worldTime(mc) - t0, dist3(mc, g));
//$$                     csv.flush();
//$$                     Thread.sleep(1000);
//$$                 }
//$$             }
//$$         } finally {
//$$             csv.close();
//$$         }
//$$         Debug.logHarness("PATHBENCH SUMMARY mode=boat");
//$$     }
//$$
//$$     /** Sheer cliffs of several heights: ride a boat off the edge vs no boat (Baritone digs down). hp = health lost. */
//$$     private static void cliff(MinecraftClient mc, BlockPos origin, int reps) throws Exception {
//$$         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
//$$         BaritoneAPI.getSettings().chatDebug.value = true;
//$$         int by = 150, ox = origin.getX(), oz = origin.getZ();
//$$         long limitTicks = Long.getLong("tenorclef.pathbench.travelTicks", 20L * 90);
//$$         PrintWriter csv = open("cliff_baritone");
//$$         csv.println("mover,variant,height,rep,result,ticks,hp");
//$$         try {
//$$             for (int H : new int[]{8, 16, 32, 64}) {
//$$                 java.util.concurrent.CompletableFuture<Void> built = new java.util.concurrent.CompletableFuture<>();
//$$                 mc.getServer().execute(() -> {
//$$                     net.minecraft.server.world.ServerWorld w = mc.getServer().getOverworld();
//$$                     for (int x = -9; x <= 16; x++) for (int z = -4; z <= 4; z++) for (int y = by - 1; y < by + 70; y++) {
//$$                         boolean floor = y == by - 1, plateau = x <= 0 && y < by + H;
//$$                         boolean wall = (Math.abs(z) == 4 || x == -9 || x == 16) && y < by + (x <= 0 ? H : 0) + 3;
//$$                         net.minecraft.block.BlockState st = floor || plateau ? net.minecraft.block.Blocks.STONE.getDefaultState()
//$$                                 : wall ? net.minecraft.block.Blocks.GLASS.getDefaultState() : net.minecraft.block.Blocks.AIR.getDefaultState();
//$$                         w.setBlockState(new BlockPos(ox + x, y, oz + z), st, 2);
//$$                     }
//$$                     built.complete(null);
//$$                 });
//$$                 built.get();
//$$                 Thread.sleep(2000);
//$$                 BlockPos start = new BlockPos(ox - 4, by + H, oz);
//$$                 BlockPos g = new BlockPos(ox + 10, by, oz);
//$$                 for (String variant : new String[]{"boat", "none"}) {
//$$                     for (int r = 0; r < reps; r++) {
//$$                         mc.execute(() -> { for (net.minecraft.entity.Entity e : mc.world.getEntities()) if (e instanceof net.minecraft.entity.vehicle.BoatEntity || e instanceof net.minecraft.entity.ItemEntity) mc.getServer().execute(() -> { net.minecraft.entity.Entity se = mc.getServer().getOverworld().getEntity(e.getUuid()); if (se != null) se.remove(); }); });
//$$                         mc.getServer().execute(() -> {
//$$                             net.minecraft.server.network.ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayerList().get(0);
//$$                             sp.stopRiding(); sp.setHealth(sp.getMaxHealth()); sp.fallDistance = 0;
//$$                             for (int i = 0; i < sp.inventory.size(); i++) if (sp.inventory.getStack(i).getItem() == net.minecraft.item.Items.OAK_BOAT) sp.inventory.setStack(i, net.minecraft.item.ItemStack.EMPTY);
//$$                             if (variant.equals("boat")) sp.inventory.setStack(20, new net.minecraft.item.ItemStack(net.minecraft.item.Items.OAK_BOAT)); // main inventory: Baritone borrows a hotbar slot
//$$                         });
//$$                         Thread.sleep(500);
//$$                         teleport(mc, start);
//$$                         Thread.sleep(500);
//$$                         float hp0 = mc.player.getHealth();
//$$                         long t0 = worldTime(mc);
//$$                         startBaritone(mc, baritone, g);
//$$                         String result = "TIMEOUT";
//$$                         while (true) {
//$$                             Thread.sleep(25);
//$$                             long el = worldTime(mc) - t0;
//$$                             if (mc.player.isDead() || mc.player.getHealth() <= 0) { result = "DIED"; break; }
//$$                             if (dist3(mc, g) < 1.5) { result = "GOAL"; break; }
//$$                             if (el % 40 == 0) Debug.logHarness(String.format(Locale.ROOT, "CLIFF H=%d t=%d pos=%.1f,%.1f,%.1f riding=%s hp=%.1f", H, el, mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.hasVehicle(), mc.player.getHealth()));
//$$                             if (el > limitTicks) break;
//$$                         }
//$$                         mc.execute(() -> baritone.getPathingBehavior().cancelEverything());
//$$                         csv.printf(Locale.ROOT, "baritone,%s,%d,%d,%s,%d,%.1f%n", variant, H, r, result, worldTime(mc) - t0, hp0 - mc.player.getHealth());
//$$                         csv.flush();
//$$                         if (result.equals("DIED")) { mc.execute(() -> mc.player.requestRespawn()); Thread.sleep(3000); }
//$$                         Thread.sleep(1000);
//$$                     }
//$$                 }
//$$             }
//$$         } finally {
//$$             csv.close();
//$$         }
//$$         Debug.logHarness("PATHBENCH SUMMARY mode=cliff");
//$$     }
//$$
//$$     /**
//$$      * Elytra: from the air at height with an elytra, Baritone's elytra process flies to far goals.
//$$      * opt "glide" gives no rockets and starts higher with nearer goals (pure glide descent).
//$$      */
//$$     private static void elytra(MinecraftClient mc, BlockPos origin, String opt, int reps) throws Exception {
//$$         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
//$$         if ("osc".equalsIgnoreCase(opt)) { elytraOsc(mc, origin); return; }
//$$         boolean glide = "glide".equalsIgnoreCase(opt);
//$$         int ox = origin.getX(), oz = origin.getZ(), sy = glide ? 250 : 200;
//$$         int[][] offs = glide ? new int[][]{{300, 0}, {0, -250}, {-200, 200}} : new int[][]{{1000, 0}, {0, -1000}, {-700, 700}, {1500, 800}};
//$$         long limitTicks = Long.getLong("tenorclef.pathbench.travelTicks", 20L * 180);
//$$         PrintWriter csv = open(glide ? "elytra_glide" : "elytra");
//$$         csv.println("goal,dx,dz,dist,rep,result,ticks,endDist,rockets,hp,minHp");
//$$         int ok = 0, n = 0;
//$$         try {
//$$             for (int gi = 0; gi < offs.length; gi++) {
//$$                 int gx = ox + offs[gi][0], gz = oz + offs[gi][1];
//$$                 for (int r = 0; r < reps; r++) {
//$$                     mc.submit(() -> baritone.getPathingBehavior().cancelEverything()).get();
//$$                     java.util.UUID id = mc.player.getUuid();
//$$                     mc.getServer().submit(() -> {
//$$                         ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayer(id);
//$$                         if (sp == null) return;
//$$                         sp.inventory.clear();
//$$                         sp.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new net.minecraft.item.ItemStack(net.minecraft.item.Items.ELYTRA));
//$$                         if (!glide) sp.inventory.setStack(0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.FIREWORK_ROCKET, 64));
//$$                         sp.getHungerManager().setFoodLevel(20);
//$$                         sp.setHealth(sp.getMaxHealth());
//$$                         sp.fallDistance = 0;
//$$                         sp.setVelocity(0, 0, 0);
//$$                     }).get();
//$$                     teleport(mc, new BlockPos(ox, sy, oz));
//$$                     Thread.sleep(300);
//$$                     teleport(mc, new BlockPos(ox, sy, oz));
//$$                     int rockets0 = mc.player.inventory.count(net.minecraft.item.Items.FIREWORK_ROCKET);
//$$                     long t0 = worldTime(mc);
//$$                     mc.submit(() -> baritone.getElytraProcess().pathTo(new GoalXZ(gx, gz))).get();
//$$                     String result = "TIMEOUT";
//$$                     float minHp = mc.player.getHealth();
//$$                     long lastLog = -1;
//$$                     while (true) {
//$$                         Thread.sleep(25);
//$$                         long el = worldTime(mc) - t0;
//$$                         if (mc.player == null || mc.player.isDead()) { result = "DIED"; break; }
//$$                         minHp = Math.min(minHp, mc.player.getHealth());
//$$                         double hd = Math.hypot(mc.player.getX() - gx - 0.5, mc.player.getZ() - gz - 0.5);
//$$                         if (el / 10 != lastLog) {
//$$                             lastLog = el / 10;
//$$                             Debug.logHarness(String.format(Locale.ROOT, "ELYTRA t=%d pos=%.0f,%.0f,%.0f d=%.0f fly=%s hp=%.1f vy=%.2f act=%s ctl=%s", el, mc.player.getX(), mc.player.getY(), mc.player.getZ(), hd, mc.player.isFallFlying(), mc.player.getHealth(), mc.player.getVelocity().y, baritone.getElytraProcess().isActive(), baritone.getPathingControlManager().mostRecentInControl().map(pr -> pr.displayName()).orElse("-")));
//$$                         }
//$$                         if (!baritone.getElytraProcess().isActive() && el > 40) { result = hd < 48 ? "LANDED" : "STOPPED"; break; }
//$$                         if (el > limitTicks) break;
//$$                     }
//$$                     mc.submit(() -> baritone.getPathingBehavior().cancelEverything()).get();
//$$                     long ticks = worldTime(mc) - t0;
//$$                     boolean alive = mc.player != null && !mc.player.isDead();
//$$                     int used = alive ? rockets0 - mc.player.inventory.count(net.minecraft.item.Items.FIREWORK_ROCKET) : 0;
//$$                     double endD = alive ? Math.hypot(mc.player.getX() - gx - 0.5, mc.player.getZ() - gz - 0.5) : -1;
//$$                     csv.printf(Locale.ROOT, "%d,%d,%d,%d,%d,%s,%d,%.1f,%d,%.1f,%.1f%n", gi, offs[gi][0], offs[gi][1],
//$$                             (int) Math.hypot(offs[gi][0], offs[gi][1]), r, result, ticks, endD, used, alive ? mc.player.getHealth() : 0f, minHp);
//$$                     csv.flush();
//$$                     n++;
//$$                     if (result.equals("LANDED")) ok++;
//$$                     if (result.equals("DIED")) { mc.execute(() -> mc.player.requestRespawn()); Thread.sleep(3000); }
//$$                 }
//$$             }
//$$         } finally {
//$$             csv.close();
//$$         }
//$$         Debug.logHarness(String.format(Locale.ROOT, "PATHBENCH SUMMARY mode=elytra%s landRate=%d/%d", glide ? "_glide" : "", ok, n));
//$$     }
//$$
//$$     /**
//$$      * Pitch oscillation, no Baritone, no rockets: deployed at y=250 flying +x, dive at +D until
//$$      * vy < -0.5 after a dive of 15 blocks, climb at -C until vy <= 0. Logs every apex (cycle top);
//$$      * a technique that sustains flight would show apex heights that don't fall.
//$$      */
//$$     private static void elytraOsc(MinecraftClient mc, BlockPos origin) throws Exception {
//$$         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
//$$         int[][] variants = {{40, 40}, {10, 20}, {30, 50}};
//$$         PrintWriter csv = open("elytra_osc");
//$$         csv.println("dive,climb,apex,ticks,x,y,speed");
//$$         try {
//$$             for (int[] v : variants) {
//$$                 mc.submit(() -> baritone.getPathingBehavior().cancelEverything()).get();
//$$                 if (mc.player.isDead()) { mc.execute(() -> mc.player.requestRespawn()); Thread.sleep(3000); }
//$$                 java.util.UUID id = mc.player.getUuid();
//$$                 mc.getServer().submit(() -> {
//$$                     ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayer(id);
//$$                     if (sp == null) return;
//$$                     sp.inventory.clear();
//$$                     sp.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new net.minecraft.item.ItemStack(net.minecraft.item.Items.ELYTRA));
//$$                     sp.setHealth(sp.getMaxHealth());
//$$                     sp.fallDistance = 0;
//$$                 }).get();
//$$                 teleport(mc, new BlockPos(origin.getX(), 250, origin.getZ()));
//$$                 Thread.sleep(300);
//$$                 teleport(mc, new BlockPos(origin.getX(), 250, origin.getZ()));
//$$                 mc.getServer().submit(() -> {
//$$                     ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayer(id);
//$$                     if (sp != null) sp.startFallFlying();
//$$                 }).get();
//$$                 long t0 = worldTime(mc), last = -1;
//$$                 boolean diving = true;
//$$                 double diveTop = 250;
//$$                 int apex = 0;
//$$                 while (worldTime(mc) - t0 < 20L * 90 && mc.player != null && !mc.player.isDead() && mc.player.getY() > 100) {
//$$                     long t = worldTime(mc);
//$$                     if (t == last) { Thread.sleep(5); continue; }
//$$                     last = t;
//$$                     double vy = mc.player.getVelocity().y, y = mc.player.getY();
//$$                     if (diving && y < diveTop - 15 && vy < -0.5) diving = false;
//$$                     else if (!diving && vy <= 0 && t - t0 > 5) {
//$$                         diving = true;
//$$                         diveTop = y;
//$$                         double sp = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
//$$                         csv.printf(Locale.ROOT, "%d,%d,%d,%d,%.1f,%.2f,%.3f%n", v[0], -v[1], apex++, t - t0, mc.player.getX() - origin.getX(), y, sp);
//$$                     }
//$$                     float pitch = diving ? v[0] : -v[1];
//$$                     mc.submit(() -> { mc.player.yaw = -90; mc.player.pitch = pitch; }).get();
//$$                 }
//$$                 Debug.logHarness(String.format(Locale.ROOT, "ELYTRAOSC %d/-%d end t=%d x=%.0f y=%.1f fly=%s", v[0], v[1], worldTime(mc) - t0, mc.player.getX() - origin.getX(), mc.player.getY(), mc.player.isFallFlying()));
//$$                 csv.flush();
//$$             }
//$$         } finally {
//$$             csv.close();
//$$         }
//$$         Debug.logHarness("PATHBENCH SUMMARY mode=elytra_osc");
//$$     }
//$$
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
//$$                     // every rep starts on the same breath (full by default), otherwise results depend on the previous rep
//$$                     int air0 = Integer.getInteger("tenorclef.pathbench.swimAir", 300);
//$$                     mc.getServer().execute(() -> mc.getServer().getPlayerManager().getPlayerList().forEach(p -> p.setAir(Math.min(air0, p.getMaxAir()))));
//$$                     Thread.sleep(200);
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
//$$         long ptm = Long.getLong("tenorclef.wrecktimeout", 0);
//$$         if (ptm > 0) { BaritoneAPI.getSettings().primaryTimeoutMS.value = ptm; BaritoneAPI.getSettings().failureTimeoutMS.value = ptm * 4; }
//$$         int[][] dirs = {{0, 0}, {800, 0}, {-800, 0}, {0, 800}, {0, -800}, {800, 800}, {-800, -800}, {800, -800}};
//$$         PrintWriter csv = open("wreck_baritone");
//$$         csv.println("wreck,x,y,z,startDist,result,ticks,endDist,opened,items");
//$$         // Pathing test, not combat: drowned in the wrecks would otherwise decide the result.
//$$         mc.getServer().submit(() -> mc.getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true)).get();
//$$         if (!"false".equals(System.getProperty("tenorclef.wreckgear"))) {
//$$             // Diver's kit: diamond tools + Aqua Affinity, so hull planks can be mined underwater.
//$$             mc.getServer().submit(() -> {
//$$                 net.minecraft.server.command.ServerCommandSource src = mc.getServer().getCommandSource();
//$$                 for (String cmd : new String[]{
//$$                         "give @a diamond_pickaxe{Enchantments:[{id:efficiency,lvl:5}]}",
//$$                         "give @a diamond_axe{Enchantments:[{id:efficiency,lvl:5}]}",
//$$                         "give @a diamond_shovel{Enchantments:[{id:efficiency,lvl:5}]}",
//$$                         "replaceitem entity @a armor.head diamond_helmet{Enchantments:[{id:aqua_affinity,lvl:1}]}"})
//$$                     mc.getServer().getCommandManager().execute(src, cmd);
//$$             }).get();
//$$         }
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
//$$                 if (Boolean.getBoolean("tenorclef.wreckmap")) {
//$$                     net.minecraft.server.world.ServerWorld sw = mc.getServer().getOverworld();
//$$                     for (int y = chest.getY() + 3; y >= chest.getY() - 1; y--) {
//$$                         StringBuilder sb = new StringBuilder("MAP y=" + y + " ");
//$$                         for (int z = -4; z <= 4; z++) {
//$$                             for (int x = -4; x <= 4; x++) {
//$$                                 BlockPos q = chest.add(x, y - chest.getY(), z);
//$$                                 net.minecraft.block.BlockState bs = sw.getBlockState(q);
//$$                                 boolean wat = bs.getFluidState().isIn(net.minecraft.tag.FluidTags.WATER);
//$$                                 char ch = q.equals(chest) ? 'C' : bs.isAir() ? '.' : bs.getBlock() == net.minecraft.block.Blocks.WATER ? '~'
//$$                                         : bs.getCollisionShape(sw, q).isEmpty() ? (wat ? 'k' : ',') : wat ? 'w'
//$$                                         : bs.getBlock() instanceof net.minecraft.block.FallingBlock ? 's' : bs.getMaterial() == net.minecraft.block.Material.WOOD ? 'o' : '#';
//$$                                 sb.append(ch);
//$$                             }
//$$                             sb.append('|');
//$$                         }
//$$                         Debug.logHarness(sb.toString());
//$$                     }
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
//$$                     if (el % 20 == 0) Debug.logHarness(String.format(Locale.ROOT, "WRECK t=%d pos=%.1f,%.1f,%.1f d=%.1f air=%d mv=%s", el, mc.player.getX(), mc.player.getY(), mc.player.getZ(), d, mc.player.getAir(), curMove()));
//$$                     if (d < bestD - 1.0) { bestD = d; bestAt = el; }
//$$                     if (!baritone.getCustomGoalProcess().isActive() && el > 40) { result = d < 4.5 ? "GOAL" : "STOPPED"; break; }
//$$                     if (d < 4.5 && el % 5 == 0 && mc.submit(() -> seesBlock(mc, chest)).get()) { mc.execute(() -> baritone.getPathingBehavior().cancelEverything()); result = "GOAL"; break; }
//$$                     if (el - bestAt > 1200) { result = "STALLED"; break; }
//$$                     if (el > limitTicks) break;
//$$                 }
//$$                 mc.execute(() -> baritone.getPathingBehavior().cancelEverything());
//$$                 long ticks = worldTime(mc) - t0;
//$$                 double end = eyeDist(mc, chest);
//$$                 boolean opened = false; int items = -1;
//$$                 if (result.equals("GOAL") || end < 4.5) {
//$$                     clearAbove(mc, chest);
//$$                     // Swim descent holds sneak; sneak + use with a tool in hand doesn't open containers.
//$$                     mc.execute(() -> { baritone.getInputOverrideHandler().clearAllKeys(); mc.options.keySneak.setPressed(false); });
//$$                     for (int i = 0; i < 20 && mc.player.isSneaking(); i++) Thread.sleep(50);
//$$                     Object ar = mc.submit(() -> mc.interactionManager.interactBlock(mc.player, mc.world, net.minecraft.util.Hand.MAIN_HAND,
//$$                             new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(chest), net.minecraft.util.math.Direction.UP, chest, false))).get();
//$$                     Debug.logHarness("PATHBENCH open " + ar + " above=" + mc.world.getBlockState(chest.up()) + " at=" + mc.world.getBlockState(chest).getBlock() + " sneak=" + mc.player.isSneaking());
//$$                     for (int i = 0; i < 30 && !opened; i++) { Thread.sleep(100); opened = mc.player.currentScreenHandler != mc.player.playerScreenHandler; }
//$$                     Debug.logHarness("PATHBENCH screen " + mc.currentScreen + " opened=" + opened);
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
//$$     /** A chest with a solid block on top won't open: mine that block out first (best hotbar tool). */
//$$     private static void clearAbove(MinecraftClient mc, BlockPos chest) throws Exception {
//$$         BlockPos up = chest.up();
//$$         for (int t = 0; t < 400; t++) {
//$$             boolean done = mc.submit(() -> {
//$$                 net.minecraft.block.BlockState bs = mc.world.getBlockState(up);
//$$                 if (bs.getCollisionShape(mc.world, up).isEmpty()) return true;
//$$                 int best = mc.player.inventory.selectedSlot; float bestSp = 0;
//$$                 for (int i = 0; i < 9; i++) { float sp = mc.player.inventory.getStack(i).getMiningSpeedMultiplier(bs); if (sp > bestSp) { bestSp = sp; best = i; } }
//$$                 mc.player.inventory.selectedSlot = best;
//$$                 net.minecraft.util.math.Vec3d eye = mc.player.getCameraPosVec(1f);
//$$                 double dx = up.getX() + 0.5 - eye.x, dy = up.getY() + 0.5 - eye.y, dz = up.getZ() + 0.5 - eye.z;
//$$                 mc.player.yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
//$$                 mc.player.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
//$$                 mc.interactionManager.updateBlockBreakingProgress(up, net.minecraft.util.math.Direction.DOWN);
//$$                 mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
//$$                 return false;
//$$             }).get();
//$$             if (done) break;
//$$             Thread.sleep(10);
//$$         }
//$$     }
//$$
//$$     private static boolean seesBlock(MinecraftClient mc, BlockPos b) {
//$$         net.minecraft.util.math.Vec3d eye = mc.player.getCameraPosVec(1f);
//$$         net.minecraft.util.hit.BlockHitResult r = mc.world.rayTrace(new net.minecraft.world.RayTraceContext(eye, new net.minecraft.util.math.Vec3d(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5),
//$$                 net.minecraft.world.RayTraceContext.ShapeType.OUTLINE, net.minecraft.world.RayTraceContext.FluidHandling.NONE, mc.player));
//$$         return r != null && r.getBlockPos().equals(b);
//$$     }
//$$
//$$     private static String flowMove() {
//$$         baritone.api.pathing.path.IPathExecutor ex = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getCurrent();
//$$         if (ex == null) return "none";
//$$         int i = Math.min(ex.getPosition(), ex.getPath().movements().size() - 1);
//$$         baritone.api.pathing.movement.IMovement m = ex.getPath().movements().get(i);
//$$         return m.getClass().getSimpleName().replace("Movement", "") + m.getSrc().getX() + "," + m.getSrc().getY() + "->" + m.getDest().getX() + "," + m.getDest().getY();
//$$     }
//$$
//$$     private static String curMove() {
//$$         try {
//$$             baritone.api.pathing.path.IPathExecutor ex = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getCurrent();
//$$             if (ex == null) return "none";
//$$             baritone.api.pathing.movement.IMovement m = ex.getPath().movements().get(ex.getPosition());
//$$             return m.getClass().getSimpleName().replace("Movement", "") + m.getSrc().toShortString() + ">" + m.getDest().toShortString() + "[" + net.minecraft.client.MinecraftClient.getInstance().world.getBlockState(m.getDest()).toString().replace("Block{minecraft:", "").replace("}", "") + "]";
//$$         } catch (Exception e) { return "?"; }
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
//$$                 sp.getHungerManager().setFoodLevel(20); // every trial starts fed: with mobs on, hunger drained to 0 by goal 15 and disabled sprint
//$$                 sp.getHungerManager().setSaturationLevelClient(5.0f);
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
//$$         // Each run starts fresh: no process (low-air surfacing) carried over from the last one.
//$$         for (int i = 0; i < 40 && mc.player != null && mc.player.getAir() < mc.player.getMaxAir(); i++) Thread.sleep(25);
//$$         mc.execute(() -> BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything());
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
//$$     /** Ticks Ostinato's kinematic controller has driven the player; -1 if the class is absent. Read reflectively: it is not in the API jar. */
//$$     private static String execState(IBaritone baritone) {
//$$         Object ex = baritone.getPathingBehavior().getCurrent();
//$$         if (ex == null) return "noexec";
//$$         try {
//$$             Object drv = ex.getClass().getMethod("getLastDriver").invoke(ex);
//$$             java.lang.reflect.Field f = ex.getClass().getDeclaredField("ticksOnCurrent"); f.setAccessible(true);
//$$             baritone.api.pathing.path.IPathExecutor pe = (baritone.api.pathing.path.IPathExecutor) ex; // read once; getCurrent() can change between calls
//$$             int pos = pe.getPosition();
//$$             java.util.List<? extends baritone.api.pathing.movement.IMovement> mvs = pe.getPath().movements();
//$$             if (mvs.isEmpty()) return "drv=" + drv + " pos=" + pos + " toc=" + f.getInt(ex) + " mv=none";
//$$             Object mv = mvs.get(Math.min(pos, mvs.size() - 1));
//$$             return "drv=" + drv + " pos=" + pos + " toc=" + f.getInt(ex) + " mv=" + mv.getClass().getSimpleName() + " " + ((baritone.api.pathing.movement.IMovement) mv).getSrc() + "->" + ((baritone.api.pathing.movement.IMovement) mv).getDest();
//$$         } catch (ReflectiveOperationException | RuntimeException e) {
//$$             return "exec?" + e;
//$$         }
//$$     }
//$$
//$$     private static long kinTicks() {
//$$         try {
//$$             return Class.forName("baritone.pathing.kinematic.KinematicController").getField("drivenTicks").getLong(null);
//$$         } catch (ReflectiveOperationException e) {
//$$             return -1;
//$$         }
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
