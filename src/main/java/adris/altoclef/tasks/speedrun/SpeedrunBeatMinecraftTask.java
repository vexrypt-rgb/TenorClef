package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.movement.TungstenMovement;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.speedrun.bastion.*;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

import java.util.EnumMap;
import java.util.Optional;

/**
 * Experimental high-level speedrun task for modern 1.16 Any% RSG style routes.
 *
 * Phase order (target < 10 minutes):
 *
 * 1. OVERWORLD_EARLY   - Wood, tools, buried treasure / surface iron, portal materials
 * 2. NETHER_ENTRY      - Enter Nether as fast as possible
 * 3. BARTER_AND_LOOT   - Piglin bartering (pearls priority) + bastion if useful
 * 4. FORTRESS          - Blaze rods if still needed
 * 5. STRONGHOLD        - Locate + enter stronghold, fill portal
 * 6. END_FIGHT         - Bed / one-cycle dragon kill + exit
 */
public class SpeedrunBeatMinecraftTask extends Task {

    public enum Phase {
        OVERWORLD_EARLY,
        NETHER_ENTRY,
        BARTER_AND_LOOT,
        FORTRESS,
        STRONGHOLD,
        END_FIGHT,
        DONE
    }

    /** Tunables for @testrun (defaults match prior hardcodes). */
    public static class Config {
        public int pearlTarget = 14;
        public int blazeRodTarget = 7;
        /** When true, EarlyOverworld skips CollectFood (default false — food on unless skipfood). */
        public boolean skipFood = false;
        public TungstenMovement.TravelMover mover = TungstenMovement.TravelMover.AUTO;
        public boolean verbose = false;
        /** One-liner chat HUD on phase change. */
        public boolean phaseChatHud = true;

        public static Config defaults() {
            Config c = new Config();
            try {
                AltoClef inst = AltoClef.getInstance();
                if (inst != null && inst.getModSettings() != null) {
                    var s = inst.getModSettings();
                    c.pearlTarget = s.getSpeedrunPearlTarget();
                    c.blazeRodTarget = s.getSpeedrunBlazeRodTarget();
                    c.skipFood = s.getSpeedrunSkipFood();
                    c.mover = TungstenMovement.parseTravelMover(s.getSpeedrunMoverPreference());
                    c.verbose = s.getSpeedrunVerbose();
                    c.phaseChatHud = s.getSpeedrunPhaseChatHud();
                }
            } catch (Throwable ignored) {}
            return c;
        }
    }

    private final AltoClef mod;
    private final Phase startPhase;
    private final Config config;
    private Phase currentPhase = Phase.OVERWORLD_EARLY;
    private Phase loggedPhase = null;

    private Task overworldEarlyTask = null;
    private Task netherEntryTask = null;
    private Task barterTask = null;
    private Task emergencyFoodTask = null;
    private long barterPhaseEnterMs = 0L;
    private int barterGoldAtEnter = 0;
    private long lastBarterStatusLogMs = 0L;
    private boolean bastionAbandonedThisPhase = false;
    private static final long GOLD_HOPELESS_MS = 90_000L;
    private Task fortressTask = null;
    private Task strongholdTask = null;
    private Task endFightTask = null;
    /** -1 = none; 0-15 = divine fossil origin X in nether chunk 0,0 */
    private int divineFossilX = -1;
    private boolean divineScanAttempted = false;

    // Per-phase timing (wall clock)
    private long runStartMs = 0L;
    private long phaseEnterMs = 0L;
    private Phase timingPhase = null;
    private final EnumMap<Phase, Long> phaseMs = new EnumMap<>(Phase.class);
    private long lastVerboseLogMs = 0L;
    private String lastVerboseKey = "";
    private TungstenMovement.TravelMover previousMover = null;

    public SpeedrunBeatMinecraftTask(AltoClef mod) {
        this(mod, Phase.OVERWORLD_EARLY, null);
    }

    /** Start at a specific phase (for @testrun nether/barter/...). Default path unchanged. */
    public SpeedrunBeatMinecraftTask(AltoClef mod, Phase startPhase) {
        this(mod, startPhase, null);
    }

    public SpeedrunBeatMinecraftTask(AltoClef mod, Phase startPhase, Config config) {
        this.mod = mod;
        this.startPhase = startPhase != null ? startPhase : Phase.OVERWORLD_EARLY;
        this.config = config != null ? config : Config.defaults();
    }

    public Phase getCurrentPhase() {
        return currentPhase;
    }

    public Config getConfig() {
        return config;
    }

    /** Compact inventory/phase summary for @testrun status. */
    public String getDebugSummary() {
        int pearls = mod.getItemStorage().getItemCount(Items.ENDER_PEARL);
        int rods = mod.getItemStorage().getItemCount(Items.BLAZE_ROD);
        int powder = mod.getItemStorage().getItemCount(Items.BLAZE_POWDER);
        int eyes = mod.getItemStorage().getItemCount(Items.ENDER_EYE);
        int beds = mod.getItemStorage().getItemCount(adris.altoclef.util.helpers.ItemHelper.BED);
        int gold = mod.getItemStorage().getItemCount(Items.GOLD_INGOT);
        return "testrun counts: pearls=" + pearls + "/" + config.pearlTarget
                + " rods=" + rods + "/" + config.blazeRodTarget
                + " powder=" + powder
                + " eyes=" + eyes
                + " beds=" + beds
                + " gold=" + gold
                + " divineX=" + divineFossilX
                + " startPhase=" + startPhase
                + " skipFood=" + config.skipFood
                + " mover=" + config.mover
                + " verbose=" + config.verbose
                + " | " + formatTimersLine();
    }

    private String formatTimersLine() {
        long now = System.currentTimeMillis();
        long total = runStartMs > 0 ? (now - runStartMs) : 0;
        StringBuilder sb = new StringBuilder("timers:");
        for (Phase p : Phase.values()) {
            if (p == Phase.DONE) continue;
            long ms = phaseMs.getOrDefault(p, 0L);
            if (timingPhase == p && phaseEnterMs > 0) {
                ms += (now - phaseEnterMs);
            }
            if (ms <= 0 && p != currentPhase) continue;
            sb.append(' ').append(shortPhase(p)).append('=').append(ms / 1000).append('s');
        }
        sb.append(" total=").append(formatTotal(total));
        return sb.toString();
    }

    private static String shortPhase(Phase p) {
        return switch (p) {
            case OVERWORLD_EARLY -> "OW";
            case NETHER_ENTRY -> "NE";
            case BARTER_AND_LOOT -> "BR";
            case FORTRESS -> "FT";
            case STRONGHOLD -> "SH";
            case END_FIGHT -> "END";
            case DONE -> "DONE";
        };
    }

    private static String formatTotal(long ms) {
        long sec = Math.max(0, ms / 1000);
        if (sec < 60) return sec + "s";
        return (sec / 60) + "m" + (sec % 60) + "s";
    }

    private void enterSpeedrunPhase(Phase next, String label) {
        long now = System.currentTimeMillis();
        if (runStartMs <= 0) {
            runStartMs = now;
            phaseEnterMs = now;
            timingPhase = next;
        } else if (timingPhase != null && timingPhase != next) {
            long spent = Math.max(0, now - phaseEnterMs);
            phaseMs.merge(timingPhase, spent, Long::sum);
            long totalMs = now - runStartMs;
            if (config.phaseChatHud) {
                Debug.logMessage("Speedrun PHASE=" + next
                        + " (+" + (spent / 1000) + "s, total " + formatTotal(totalMs) + ")");
            }
            phaseEnterMs = now;
            timingPhase = next;
        } else if (timingPhase == null) {
            phaseEnterMs = now;
            timingPhase = next;
        }

        if (loggedPhase != next) {
            loggedPhase = next;
            if (config.verbose) {
                verboseLog("phase", "Speedrun: " + label);
            } else if (!config.phaseChatHud) {
                Debug.logMessage("Speedrun: " + label);
            }
        }
        currentPhase = next;
        setDebugState(label);
    }

    private void verboseLog(String key, String message) {
        if (!config.verbose) return;
        long now = System.currentTimeMillis();
        if (key.equals(lastVerboseKey) && (now - lastVerboseLogMs) < 2000L) {
            return;
        }
        lastVerboseKey = key;
        lastVerboseLogMs = now;
        Debug.logMessage(message);
    }

    private void clearPortalEntryTasks() {
        if (overworldEarlyTask != null) {
            overworldEarlyTask.stop();
            overworldEarlyTask = null;
        }
        if (netherEntryTask != null) {
            netherEntryTask.stop();
            netherEntryTask = null;
        }
    }

    @Override
    protected void onStart() {
        currentPhase = startPhase;
        loggedPhase = null;
        divineFossilX = -1;
        divineScanAttempted = false;
        overworldEarlyTask = null;
        netherEntryTask = null;
        barterTask = null;
        fortressTask = null;
        strongholdTask = null;
        endFightTask = null;
        phaseMs.clear();
        runStartMs = System.currentTimeMillis();
        phaseEnterMs = runStartMs;
        timingPhase = startPhase;
        lastVerboseLogMs = 0L;
        lastVerboseKey = "";
        previousMover = TungstenMovement.getTravelMover();
        TungstenMovement.setTravelMover(config.mover);
        setDebugState("Starting modern speedrun route (@testrun) at " + startPhase
                + " pearls=" + config.pearlTarget + " rods=" + config.blazeRodTarget
                + " skipFood=" + config.skipFood + " mover=" + config.mover);
        if (config.phaseChatHud) {
            Debug.logMessage("Speedrun PHASE=" + startPhase + " (+0s, total 0s)");
        }
    }

    @Override
    protected Task onTick() {
        if (WorldHelper.getCurrentDimension() == Dimension.NETHER
                && (currentPhase == Phase.OVERWORLD_EARLY || currentPhase == Phase.NETHER_ENTRY)) {
            clearPortalEntryTasks();
            enterSpeedrunPhase(Phase.BARTER_AND_LOOT, "NETHER_ENTRY complete -> NETHER_GEAR (BARTER_AND_LOOT)");
        }

        Task foodInterrupt = maybeEmergencyFood();
        if (foodInterrupt != null) {
            return foodInterrupt;
        }

        switch (currentPhase) {
            case OVERWORLD_EARLY:
                return handleOverworldEarly();
            case NETHER_ENTRY:
                return handleNetherEntry();
            case BARTER_AND_LOOT:
                return handleBarterAndLoot();
            case FORTRESS:
                return handleFortress();
            case STRONGHOLD:
                return handleStronghold();
            case END_FIGHT:
                return handleEndFight();
            case DONE:
            default:
                setDebugState("Speedrun complete!");
                return null;
        }
    }

    private Task handleOverworldEarly() {
        setDebugState("Phase 1: Overworld early (modern route)");
        verboseLog("ow", "Speedrun subgoal: OVERWORLD_EARLY");

        if (WorldHelper.getCurrentDimension() == Dimension.NETHER) {
            clearPortalEntryTasks();
            enterSpeedrunPhase(Phase.BARTER_AND_LOOT, "NETHER_ENTRY complete -> NETHER_GEAR (BARTER_AND_LOOT)");
            return null;
        }

        if (overworldEarlyTask == null) {
            overworldEarlyTask = new EarlyOverworldSpeedrunTask(mod, config.skipFood);
        }

        if (overworldEarlyTask.isFinished()) {
            clearPortalEntryTasks();
            enterSpeedrunPhase(Phase.BARTER_AND_LOOT, "NETHER_ENTRY complete -> NETHER_GEAR (BARTER_AND_LOOT)");
            return null;
        }
        if (overworldEarlyTask.thisOrChildSatisfies(t -> t instanceof GetToBlockTask gtb && gtb.isStaleFinished())) {
            Debug.logMessage("Speedrun: stale GetToBlock under OVERWORLD_EARLY - forcing NETHER_GEAR if in nether, else re-entry");
            if (WorldHelper.getCurrentDimension() == Dimension.NETHER) {
                clearPortalEntryTasks();
                enterSpeedrunPhase(Phase.BARTER_AND_LOOT, "NETHER_ENTRY complete -> NETHER_GEAR (BARTER_AND_LOOT)");
                return null;
            }
            clearPortalEntryTasks();
            enterSpeedrunPhase(Phase.NETHER_ENTRY, "NETHER_ENTRY");
            return handleNetherEntry();
        }

        return overworldEarlyTask;
    }

    private Task handleNetherEntry() {
        setDebugState("Phase 2: NETHER_ENTRY");
        verboseLog("ne", "Speedrun subgoal: NETHER_ENTRY");

        if (WorldHelper.getCurrentDimension() == Dimension.NETHER) {
            clearPortalEntryTasks();
            enterSpeedrunPhase(Phase.BARTER_AND_LOOT, "NETHER_ENTRY complete -> NETHER_GEAR (BARTER_AND_LOOT)");
            return null;
        }

        if (netherEntryTask != null && netherEntryTask.isFinished()) {
            clearPortalEntryTasks();
            enterSpeedrunPhase(Phase.BARTER_AND_LOOT, "NETHER_ENTRY complete -> NETHER_GEAR (BARTER_AND_LOOT)");
            return null;
        }
        if (netherEntryTask != null
                && netherEntryTask.thisOrChildSatisfies(t -> t instanceof GetToBlockTask gtb && gtb.isStaleFinished())) {
            Debug.logMessage("Speedrun: clearing stale GetToBlock under NETHER_ENTRY");
            clearPortalEntryTasks();
        }

        if (netherEntryTask == null || netherEntryTask.stopped()) {
            if (loggedPhase != Phase.NETHER_ENTRY) {
                enterSpeedrunPhase(Phase.NETHER_ENTRY, "NETHER_ENTRY");
            }
            netherEntryTask = new DefaultGoToDimensionTask(Dimension.NETHER);
        }
        return netherEntryTask;
    }

    private Task handleBarterAndLoot() {
        if (loggedPhase != Phase.BARTER_AND_LOOT) {
            enterSpeedrunPhase(Phase.BARTER_AND_LOOT, "NETHER_GEAR (BARTER_AND_LOOT)");
            barterPhaseEnterMs = System.currentTimeMillis();
            barterGoldAtEnter = mod.getItemStorage().getItemCount(Items.GOLD_INGOT);
            bastionAbandonedThisPhase = false;
        }
        setDebugState("Phase 3: NETHER_GEAR - Bastion route (if any) + Piglin bartering");
        verboseLog("br", "Speedrun subgoal: BARTER_AND_LOOT pearls>=" + config.pearlTarget);
        logBarterStatusIfDue();

        if (!divineScanAttempted && WorldHelper.getCurrentDimension() == Dimension.NETHER) {
            try {
                boolean near00 = Math.hypot(mod.getPlayer().getX(), mod.getPlayer().getZ()) < 128;
                boolean chunkLoaded = mod.getWorld() != null && mod.getWorld().isChunkLoaded(0, 0);
                if (near00 || chunkLoaded) {
                    divineScanAttempted = true;
                    var det = adris.altoclef.tasks.speedrun.stronghold.DivineFossilDetector.detect(mod);
                    if (det.isPresent()) {
                        divineFossilX = det.get().fossilX;
                        setDebugState("Divine fossil detected X=" + divineFossilX);
                    }
                }
            } catch (Exception ignored) {}
        }

        if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
            enterSpeedrunPhase(Phase.NETHER_ENTRY, "NETHER_ENTRY");
            return handleNetherEntry();
        }

        final int pearlTarget = Math.max(1, config.pearlTarget);

        // Stale GetToBlock under NETHER_GEAR — clear/replace subtask
        if (barterTask != null
                && barterTask.thisOrChildSatisfies(task -> task instanceof GetToBlockTask gtb && gtb.isStaleFinished())) {
            Debug.logMessage("Speedrun: stale GetToBlock under NETHER_GEAR - clearing barter/loot subtask");
            try { barterTask.stop(); } catch (Throwable ignored) {}
            barterTask = null;
            bastionAbandonedThisPhase = true;
        }

        long now = System.currentTimeMillis();
        int goldNow = mod.getItemStorage().getItemCount(Items.GOLD_INGOT);
        int pearls = mod.getItemStorage().getItemCount(Items.ENDER_PEARL);
        if (barterPhaseEnterMs > 0 && (now - barterPhaseEnterMs) > GOLD_HOPELESS_MS
                && goldNow <= barterGoldAtEnter && pearls < pearlTarget) {
            if (goldNow < 8 && !bastionAbandonedThisPhase) {
                Debug.logMessage("Speedrun: gold hopeless for 90s+ under NETHER_GEAR (gold=" + goldNow
                        + ") - abandoning bastion loot, trying piglin barter / fortress pivot");
                bastionAbandonedThisPhase = true;
                if (barterTask instanceof BastionRouteTask) {
                    try { barterTask.stop(); } catch (Throwable ignored) {}
                    barterTask = null;
                }
            }
            if ((now - barterPhaseEnterMs) > GOLD_HOPELESS_MS * 2 && goldNow < 4 && pearls == 0) {
                Debug.logMessage("Speedrun: NETHER_GEAR hopeless - pivoting to FORTRESS");
                if (barterTask != null) {
                    try { barterTask.stop(); } catch (Throwable ignored) {}
                    barterTask = null;
                }
                enterSpeedrunPhase(Phase.FORTRESS, "FORTRESS (pivot from hopeless barter)");
                return null;
            }
        }

        if (!bastionAbandonedThisPhase && (barterTask == null || !(barterTask instanceof BastionRouteTask))) {
            Optional<BastionDetector.DetectedBastion> bastion =
                    BastionDetector.detect(mod, 90);

            if (bastion.isPresent()) {
                BastionDetector.DetectedBastion b = bastion.get();
                setDebugState("Detected " + b.type + " bastion - running route");
                verboseLog("bastion", "Speedrun: bastion " + b.type);

                switch (b.type) {
                    case TREASURE -> barterTask = new BastionTreasureRouteTask(mod, b.origin);
                    case HOUSING  -> barterTask = new BastionHousingRouteTask(mod, b.origin);
                    case BRIDGE   -> barterTask = new BastionBridgeRouteTask(mod, b.origin);
                    case STABLES  -> barterTask = new BastionStablesRouteTask(mod, b.origin);
                    default       -> barterTask = null;
                }

                if (barterTask != null) {
                    return barterTask;
                }
            }
        }

        if (barterTask instanceof BastionRouteTask && barterTask.isFinished()) {
            if (barterTask instanceof BastionRouteTask br && br.isAbandoned()) {
                bastionAbandonedThisPhase = true;
                Debug.logMessage("Speedrun: bastion route abandoned - switching to piglin barter");
            }
            barterTask = null;
        }

        if (barterTask == null) {
            barterTask = new PiglinBarterSpeedrunTask(mod, pearlTarget);
            Debug.logMessage("Speedrun: NETHER_GEAR mode=BARTER pearls=" + pearls + "/" + pearlTarget
                    + " gold=" + goldNow);
        }

        if (barterTask.isFinished()) {
            setDebugState("Bartering complete - moving to fortress / stronghold");
            enterSpeedrunPhase(Phase.FORTRESS, "FORTRESS");
            barterTask = null;
            return null;
        }

        return barterTask;
    }


    private void logBarterStatusIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastBarterStatusLogMs < 8000L) return;
        lastBarterStatusLogMs = now;
        int pearls = mod.getItemStorage().getItemCount(Items.ENDER_PEARL);
        int gold = mod.getItemStorage().getItemCount(Items.GOLD_INGOT);
        int nuggets = mod.getItemStorage().getItemCount(Items.GOLD_NUGGET);
        int helmet = mod.getItemStorage().getItemCount(Items.GOLDEN_HELMET);
        String mode = "SEARCH";
        if (barterTask instanceof BastionRouteTask) mode = "LOOT";
        else if (barterTask instanceof PiglinBarterSpeedrunTask) mode = "BARTER";
        if (bastionAbandonedThisPhase && !(barterTask instanceof BastionRouteTask)) mode = "BARTER";
        Debug.logMessage("Speedrun NETHER_GEAR mode=" + mode
                + " pearls=" + pearls + "/" + config.pearlTarget
                + " gold=" + gold + " nuggets=" + nuggets
                + " gHelmet=" + helmet
                + " foodScore=" + StorageHelper.calculateInventoryFoodScore(mod)
                + (mod.getPlayer() != null ? (" hunger=" + mod.getPlayer().getHungerManager().getFoodLevel()) : ""));
    }

    /**
     * Emergency food interrupt: short CollectFood only when critically hungry and empty.
     * Does NOT re-enable heavy CollectFood every run.
     */
    private Task maybeEmergencyFood() {
        PlayerEntity player = mod.getPlayer();
        if (player == null) return null;
        int hunger = player.getHungerManager().getFoodLevel();
        float sat = player.getHungerManager().getSaturationLevel();
        boolean critical = hunger <= 6 || (sat <= 0.05f && hunger <= 10);
        if (!critical) {
            emergencyFoodTask = null;
            return null;
        }

        int foodScore = StorageHelper.calculateInventoryFoodScore(mod);
        if (foodScore > 0) {
            if (!mod.getFoodChain().isTryingToEat()) {
                Debug.logMessage("Speedrun: emergency food (hunger=" + hunger + ") haveFood=" + foodScore);
            }
            emergencyFoodTask = null;
            return null; // FoodChain eats asynchronously
        }

        if (emergencyFoodTask == null || emergencyFoodTask.isFinished() || emergencyFoodTask.stopped()) {
            Debug.logMessage("Speedrun: emergency food (hunger=" + hunger + ") collecting short @food");
            emergencyFoodTask = new CollectFoodTask(10);
        }
        setDebugState("Emergency food (hunger=" + hunger + ")");
        return emergencyFoodTask;
    }

    private Task handleFortress() {
        setDebugState("Phase 4: Fortress (blaze rods)");
        verboseLog("ft", "Speedrun subgoal: FORTRESS rods>=" + config.blazeRodTarget);

        if (fortressTask == null) {
            fortressTask = new FortressSpeedrunTask(mod, Math.max(1, config.blazeRodTarget));
        }

        if (fortressTask.isFinished()) {
            setDebugState("Fortress complete - moving to stronghold");
            enterSpeedrunPhase(Phase.STRONGHOLD, "STRONGHOLD");
            fortressTask = null;
            return null;
        }

        return fortressTask;
    }

    private Task handleStronghold() {
        setDebugState("Phase 5: Stronghold + portal");
        verboseLog("sh", "Speedrun subgoal: STRONGHOLD");

        if (WorldHelper.getCurrentDimension() == Dimension.END) {
            enterSpeedrunPhase(Phase.END_FIGHT, "END_FIGHT");
            strongholdTask = null;
            return null;
        }

        if (strongholdTask == null) {
            strongholdTask = new StrongholdSpeedrunTask(mod, divineFossilX);
        }

        if (strongholdTask.isFinished()) {
            setDebugState("Stronghold complete - End fight");
            enterSpeedrunPhase(Phase.END_FIGHT, "END_FIGHT");
            strongholdTask = null;
            return null;
        }

        return strongholdTask;
    }

    private Task handleEndFight() {
        setDebugState("Phase 6: End fight (bed / one-cycle)");
        verboseLog("end", "Speedrun subgoal: END_FIGHT");

        if (endFightTask == null) {
            endFightTask = new KillEnderDragonWithBedsTask();
        }

        if (mod.getEntityTracker().entityFound(net.minecraft.entity.boss.dragon.EnderDragonEntity.class) == false
                && WorldHelper.getCurrentDimension() == Dimension.END) {
            enterSpeedrunPhase(Phase.DONE, "DONE");
            return null;
        }

        return endFightTask;
    }

    @Override
    protected void onStop(Task interruptTask) {
        clearPortalEntryTasks();
        long now = System.currentTimeMillis();
        if (timingPhase != null && phaseEnterMs > 0) {
            phaseMs.merge(timingPhase, Math.max(0, now - phaseEnterMs), Long::sum);
        }
        if (previousMover != null) {
            TungstenMovement.setTravelMover(previousMover);
            previousMover = null;
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SpeedrunBeatMinecraftTask;
    }

    @Override
    protected String toDebugString() {
        return "SpeedrunBeatMinecraftTask [" + currentPhase + "]";
    }

    @Override
    public boolean isFinished() {
        return currentPhase == Phase.DONE;
    }
}
