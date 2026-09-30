package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.control.KillAura;
import adris.altoclef.multiversion.versionedfields.Entities;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.tasks.construction.ProjectileProtectionWallTask;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.CustomBaritoneGoalTask;
import adris.altoclef.tasks.movement.DodgeProjectilesTask;
import adris.altoclef.tasks.movement.RunAwayFromCreepersTask;
import adris.altoclef.tasks.movement.RunAwayFromHostilesTask;
import adris.altoclef.tasks.speedrun.DragonBreathTracker;
import adris.altoclef.tasks.speedrun.testrun2.T2Log;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.baritone.CachedProjectile;
import adris.altoclef.util.helpers.*;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import baritone.Baritone;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.effect.StatusEffects;
//#if MC < 260000
import net.minecraft.entity.mob.*;
//#else
//$$ import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
//$$ import net.minecraft.world.entity.monster.Blaze;
//$$ import net.minecraft.world.entity.monster.spider.CaveSpider;
//$$ import net.minecraft.world.entity.monster.Creeper;
//$$ import net.minecraft.world.entity.monster.zombie.Drowned;
//$$ import net.minecraft.world.entity.monster.Enderman;
//$$ import net.minecraft.world.entity.monster.Ghast;
//$$ import net.minecraft.world.entity.monster.hoglin.Hoglin;
//$$ import net.minecraft.world.entity.monster.Monster;
//$$ import net.minecraft.world.entity.monster.cubemob.MagmaCube;
//$$ import net.minecraft.world.entity.Mob;
//$$ import net.minecraft.world.entity.monster.piglin.Piglin;
//$$ import net.minecraft.world.entity.monster.illager.Pillager;
//$$ import net.minecraft.world.entity.monster.Silverfish;
//$$ import net.minecraft.world.entity.monster.skeleton.Skeleton;
//$$ import net.minecraft.world.entity.monster.cubemob.Slime;
//$$ import net.minecraft.world.entity.monster.spider.Spider;
//$$ import net.minecraft.world.entity.monster.skeleton.Stray;
//$$ import net.minecraft.world.entity.monster.illager.Vindicator;
//$$ import net.minecraft.world.entity.monster.Witch;
//$$ import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
//$$ import net.minecraft.world.entity.monster.Zoglin;
//$$ import net.minecraft.world.entity.monster.zombie.Zombie;
//#endif
import net.minecraft.entity.player.PlayerEntity;
//#if MC < 260000
import net.minecraft.entity.projectile.*;
//#else
//$$ import net.minecraft.world.entity.projectile.arrow.Arrow;
//$$ import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
//$$ import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
//$$ import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
//$$ import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
//#endif
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;


import java.util.*;
import java.util.function.Predicate;


// TODO: Optimise shielding against spiders and skeletons

public class MobDefenseChain extends SingleTaskChain {
    private long perchSinceMs;
    private static final double DANGER_KEEP_DISTANCE = 30;
    private static final double CREEPER_KEEP_DISTANCE = 10;
    private static final double ARROW_KEEP_DISTANCE_HORIZONTAL = 2;
    private static final double ARROW_KEEP_DISTANCE_VERTICAL = 10;
    private static final double SAFE_KEEP_DISTANCE = 8;
    private static final List<Class<? extends Entity>> ignoredMobs = List.of(Entities.WARDEN, WitherEntity.class, EndermanEntity.class, BlazeEntity.class,
            WitherSkeletonEntity.class, HoglinEntity.class, ZoglinEntity.class, Entities.PIGLIN_BRUTE, VindicatorEntity.class, MagmaCubeEntity.class);

    private static boolean shielding = false;
    private final DragonBreathTracker dragonBreathTracker = new DragonBreathTracker();
    private final KillAura killAura = new KillAura();
    private Entity targetEntity;
    private boolean doingFunkyStuff = false;
    private boolean wasPuttingOutFire = false;
    private CustomBaritoneGoalTask runAwayTask;
    private long fleeHoldUntilMs;
    private long lastHitMs;
    private String why = "?";
    private Task pillarTask;
    private int pillarY;

    /** S281: s280t re-created the flee task every tick near a hoglin; each restart re-planned from scratch,
     *  the bot stood at one block for 10s and finally fled into lava. Keep the running flee task instead. */
    private CustomBaritoneGoalTask keepRunAway() {
        // S296: s294t's 70-priority flees (danger/eating/hoglin) had no hold and dropped to idle a tick
        // later (70->0 x many, skeleton death at hp 6.8). Every flee holds 5s.
        fleeHoldUntilMs = Math.max(fleeHoldUntilMs, System.currentTimeMillis() + 5000);
        if (runAwayTask instanceof RunAwayFromHostilesTask && !runAwayTask.isFinished()) return runAwayTask;
        return new RunAwayFromHostilesTask(DANGER_KEEP_DISTANCE, true);
    }
    private long fleeStreakStartMs;
    private long fleeStreakLastMs;
    /** S334: true once a 70-flee has run 30s without a 2s break; logs why once. Resets after 10s off. */
    private boolean fleeStreakTooLong(String why) {
        long now = System.currentTimeMillis();
        if (now - fleeStreakLastMs > 2000) fleeStreakStartMs = now;
        if (now - fleeStreakStartMs > 30_000) {
            if (now - fleeStreakLastMs < 2000 && fleeStreakStartMs > 0 && now - fleeStreakStartMs < 30_100)
                adris.altoclef.tasks.speedrun.testrun2.T2History.note("S334 flee streak 30s capped why=" + why);
            if (now - fleeStreakStartMs > 40_000) fleeStreakStartMs = now; // 10s off, then may flee again
            fleeStreakLastMs = now;
            return now - fleeStreakStartMs > 30_000 || now - fleeStreakStartMs < 0;
        }
        fleeStreakLastMs = now;
        return false;
    }
    private float prevHealth = 20;
    private boolean needsChangeOnAttack = false;
    private Entity lockedOnEntity = null;

    private float cachedLastPriority;

    /**
     * S155 — "annoying hostile" engagements return priority 65 (attack) or 80 (flee), both of
     * which outrank UserTaskChain (50). That means the speedrun task is suspended outright, not
     * merely interrupted: its tick never runs, so no telemetry is emitted either.
     *
     * Because {@code KillEntitiesTask} was handed a bare entity class it hunted every entity of
     * that type anywhere in the tracker, so a single unreachable zombie held priority forever.
     * These constants put a wall-clock budget and a distance bound on that behaviour.
     */
    private static final long MAX_ENGAGE_MS = 20_000L;
    private static final long DISENGAGE_COOLDOWN_MS = 45_000L;
    private static final double ENGAGE_CHASE_RANGE = 24.0;
    private long engageStartMs = 0;
    private long disengageUntilMs = 0;

    public MobDefenseChain(TaskRunner runner) {
        super(runner);
    }

    public static double getCreeperSafety(Vec3d pos, CreeperEntity creeper) {
        double distance = creeper.squaredDistanceTo(pos);
        float fuse = creeper.getClientFuseTime(1);

        // Not fusing.
        if (fuse <= 0.001f) return distance;
        return distance * 0.2; // less is WORSE
    }

    private static void startShielding(AltoClef mod) {
        shielding = true;
        mod.getClientBaritone().getPathingBehavior().requestPause();
        mod.getExtraBaritoneSettings().setInteractionPaused(true);
        if (!mod.getPlayer().isBlocking()) {
            ItemStack handItem = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot());
            if (ItemVer.isFood(handItem)) {
                List<ItemStack> spaceSlots = mod.getItemStorage().getItemStacksPlayerInventory(false);
                for (ItemStack spaceSlot : spaceSlots) {
                    if (spaceSlot.isEmpty()) {
                        mod.getSlotHandler().clickSlot(PlayerSlot.getEquipSlot(), 0, SlotActionType.QUICK_MOVE);
                        return;
                    }
                }
                Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
                garbage.ifPresent(slot -> mod.getSlotHandler().forceEquipItem(StorageHelper.getItemStackInSlot(slot).getItem()));
            }
        }
        mod.getInputControls().hold(Input.SNEAK);
        mod.getInputControls().hold(Input.CLICK_RIGHT);
    }

    private static int getDangerousnessScore(List<LivingEntity> toDealWithList) {
        int numberOfProblematicEntities = toDealWithList.size();
        for (LivingEntity toDealWith : toDealWithList) {
            if (toDealWith instanceof EndermanEntity || toDealWith instanceof SlimeEntity || toDealWith instanceof BlazeEntity) {

                numberOfProblematicEntities += 1;
            } else if (toDealWith instanceof DrownedEntity && ((DrownedEntity) toDealWith).getMainHandStack().getItem() == Items.TRIDENT) {
                // Drowned with tridents are also REALLY dangerous, maybe we should increase this??
                numberOfProblematicEntities += 5;
            }
        }
        return numberOfProblematicEntities;
    }

    @Override
    public float getPriority() {
        if (adris.altoclef.tasks.pvp.PvpTask.anyActive()) return 0;
        float before = cachedLastPriority;
        why = "?";
        float inner = getPriorityInner();
        cachedLastPriority = S222holdBudget(inner);
        // S292: s291t flipped 80<->(<50) every 0.5s under skeleton fire with no logged exit. Name the path.
        if (before >= 65 && cachedLastPriority < 50) {
            adris.altoclef.tasks.speedrun.testrun2.T2History.note("S292 mobdef drop " + (int) before + "->" + cachedLastPriority + " inner=" + inner + " why=" + why
                    + " run=" + (runAwayTask != null) + " hold=" + (fleeHoldUntilMs - System.currentTimeMillis()));
        }
        prevHealth = AltoClef.getInstance().getPlayer().getHealth();
        return cachedLastPriority;
    }

    // S222: MAX_ENGAGE_MS only bounded the "annoying hostiles" branch. The lock-on chase
    // (needsChangeOnAttack) and the runaway branches had no budget at all, so run carrytable
    // idled the run task for ~4 minutes chasing a bouncing magma cube until it killed us --
    // and T2Deadman (S214) counts a non-user chain as healthy, so nothing noticed.
    // Bound ANY continuous hold; only real low-HP danger may break the cooldown.
    private static final long MAX_HOLD_MS = 40_000L;
    private static final long HOLD_COOLDOWN_MS = 20_000L;
    private long holdStartMs = 0;
    private long holdCooldownUntilMs = 0;

    private float S222holdBudget(float pri) {
        AltoClef mod = AltoClef.getInstance();
        long now = System.currentTimeMillis();
        boolean lowHp = mod.getPlayer() != null && mod.getPlayer().getHealth() <= 6;
        if (pri <= 0 || Float.isInfinite(pri)) { holdStartMs = 0; return pri; }
        if (holdCooldownUntilMs > now && !lowHp) { clearEngagement(mod); return 0; }
        if (holdStartMs == 0) holdStartMs = now;
        if (now - holdStartMs > MAX_HOLD_MS && !lowHp) {
            T2Log.warn("S222", "mob-defense held priority " + (now - holdStartMs) / 1000 + "s pri=" + pri
                    + " task=" + (mainTask == null ? "-" : mainTask.getClass().getSimpleName())
                    + " target=" + (lockedOnEntity == null ? "-" : lockedOnEntity.getType().getTranslationKey())
                    + " hp=" + (int) mod.getPlayer().getHealth() + " food=" + mod.getPlayer().getHungerManager().getFoodLevel()
                    + " danger=" + getUniversallyDangerousMob(mod).map(e -> e.getType().getTranslationKey()).orElse("-")
                    + " - forcing hand-back");
            holdStartMs = 0;
            holdCooldownUntilMs = now + HOLD_COOLDOWN_MS;
            engageStartMs = 0;
            clearEngagement(mod);
            return 0;
        }
        return pri;
    }

    private void stopShielding(AltoClef mod) {
        if (shielding) {
            ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
            if (ItemVer.isFood(cursor)) {
                Optional<Slot> toMoveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false).or(() -> StorageHelper.getGarbageSlot(mod));
                if (toMoveTo.isPresent()) {
                    Slot garbageSlot = toMoveTo.get();
                    mod.getSlotHandler().clickSlot(garbageSlot, 0, SlotActionType.PICKUP);
                }
            }
            mod.getInputControls().release(Input.SNEAK);
            mod.getInputControls().release(Input.CLICK_RIGHT);
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
            shielding = false;
        }
    }

    public boolean isShielding() {
        return shielding || killAura.isShielding();
    }

    private boolean escapeDragonBreath(AltoClef mod) {
        dragonBreathTracker.updateBreath(mod);
        for (BlockPos playerIn : WorldHelper.getBlocksTouchingPlayer()) {
            if (dragonBreathTracker.isTouchingDragonBreath(playerIn)) {
                return true;
            }
        }
        return false;
    }

    private float getPriorityInner() {
        if (!AltoClef.inGame()) {
            return Float.NEGATIVE_INFINITY;
        }
        AltoClef mod = AltoClef.getInstance();

        if (!mod.getModSettings().isMobDefense()) {
            return Float.NEGATIVE_INFINITY;
        }

        if (mod.getWorld().getDifficulty() == Difficulty.PEACEFUL) return Float.NEGATIVE_INFINITY;

        if (needsChangeOnAttack && (mod.getPlayer().getHealth() < prevHealth || killAura.attackedLastTick)) {
            needsChangeOnAttack = false;
        }

        // Put out fire if we're standing on one like an idiot
        BlockPos fireBlock = isInsideFireAndOnFire(mod);
        if (fireBlock != null) {
            putOutFire(mod, fireBlock);
            wasPuttingOutFire = true;
        } else {
            // Stop putting stuff out if we no longer need to put out a fire.
            mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
            wasPuttingOutFire = false;
        }

        // Run away if a weird mob is close by.
        Optional<Entity> universallyDangerous = getUniversallyDangerousMob(mod);
        // S276: a hoglin hits for ~6; waiting until hp<=10 left one or two hits of margin.
        // S297: s296t fled a hoglin on flat netherrack; knockback kept throwing it off the flee path
        // ("too far from path", "No path found") until it died. Hoglins cannot climb 2 blocks, so pillar up.
        if (universallyDangerous.isPresent() && (universallyDangerous.get() instanceof HoglinEntity || universallyDangerous.get() instanceof ZoglinEntity)) {
            int py = mod.getPlayer().getBlockY();
            boolean blocks = mod.getItemStorage().getItemCount(Items.NETHERRACK, Items.COBBLESTONE, Items.DIRT, Items.BLACKSTONE, Items.BASALT) >= 3;
            if (pillarTask != null && !pillarTask.isFinished() && py < pillarY) {
                setTask(pillarTask);
                return 70;
            }
            if (blocks && universallyDangerous.get().distanceTo(mod.getPlayer()) < 7
                    && mod.getPlayer().getY() - universallyDangerous.get().getY() < 2) {
                pillarY = py + 3;
                pillarTask = new adris.altoclef.tasks.movement.GetToYTask(pillarY);
                adris.altoclef.tasks.speedrun.testrun2.T2History.note("S297 pillar from " + universallyDangerous.get().getType().getTranslationKey() + " y=" + py + "->" + pillarY);
                setTask(pillarTask);
                return 70;
            }
            // Safely above it: wait instead of walking back down.
            // S317: s313t idled on a hoglin pillar for 3.5 minutes in the open until a ghast killed it.
            // Cap the perch wait at 20s and never wait while a ghast is around.
            long nowMs = System.currentTimeMillis();
            if (perchSinceMs == 0) perchSinceMs = nowMs;
            boolean ghast = !mod.getEntityTracker().getTrackedEntities(net.minecraft.entity.mob.GhastEntity.class).isEmpty();
            if ((nowMs - perchSinceMs > 20_000 || ghast) && perchSinceMs > 0) {
                if (nowMs - perchSinceMs < 20_300) adris.altoclef.tasks.speedrun.testrun2.T2History.note("S317 perch wait over ghast=" + ghast);
            } else if (mod.getPlayer().getY() - universallyDangerous.get().getY() >= 2 && universallyDangerous.get().distanceTo(mod.getPlayer()) < 10) {
                if (!(mainTask instanceof adris.altoclef.tasks.movement.IdleTask)) setTask(new adris.altoclef.tasks.movement.IdleTask());
                return 70;
            }
        }
        if (!universallyDangerous.isPresent() || !(universallyDangerous.get() instanceof HoglinEntity || universallyDangerous.get() instanceof ZoglinEntity)) perchSinceMs = 0;
        if (universallyDangerous.isPresent() && mod.getPlayer().getHealth() <= 14 && !fleeStreakTooLong("danger " + universallyDangerous.get().getType().getTranslationKey())) {
            runAwayTask = keepRunAway();
            setTask(runAwayTask);
            return 70;
        }

        doingFunkyStuff = false;
        PlayerSlot offhandSlot = PlayerSlot.OFFHAND_SLOT;
        Item offhandItem = StorageHelper.getItemStackInSlot(offhandSlot).getItem();
        // Run away from creepers
        CreeperEntity blowingUp = getClosestFusingCreeper(mod);
        if (blowingUp == null && System.currentTimeMillis() < creeperFleeUntilMs && runAwayTask instanceof RunAwayFromCreepersTask && !runAwayTask.isFinished()) {
            setTask(runAwayTask);
            return 85;
        }
        // S327 (owner: "why not just kill the creeper?"): with a sword and decent hp, fight it. Sword hits knock
        // it back and reset the approach; a stone sword kills in 4 hits. Flee only unarmed, hurt, or late fuse.
        if (blowingUp != null && mod.getItemStorage().getItemCount(net.minecraft.item.Items.STONE_SWORD, net.minecraft.item.Items.IRON_SWORD, net.minecraft.item.Items.DIAMOND_SWORD) > 0
                && mod.getPlayer().getHealth() >= 10 && blowingUp.getClientFuseTime(1) < 0.5f) {
            if (creeperKill == null || creeperKillId != blowingUp.getId()) {
                creeperKill = new adris.altoclef.tasks.entity.KillEntityTask(blowingUp);
                creeperKillId = blowingUp.getId();
            }
            creeperFleeUntilMs = 0;
            setTask(creeperKill);
            return 85;
        }
        if (blowingUp != null) {
            if ((!mod.getFoodChain().needsToEat() || mod.getPlayer().getHealth() < 9)
                    && hasShield(mod)
                    && !mod.getEntityTracker().entityFound(PotionEntity.class)
                    && !mod.getPlayer().getItemCooldownManager().isCoolingDown(
                    //#if MC >= 12102
                    //$$ new net.minecraft.item.ItemStack(offhandItem)
                    //#else
                    offhandItem
                    //#endif
                    )
                    && mod.getClientBaritone().getPathingBehavior().isSafeToCancel()
                    && blowingUp.getClientFuseTime(blowingUp.getFuseSpeed()) > 0.5) {
                LookHelper.lookAt(mod, blowingUp.getEyePos());
                ItemStack shieldSlot = StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT);
                if (shieldSlot.getItem() != Items.SHIELD) {
                    mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
                } else {
                    startShielding(mod);
                }
            } else {
                doingFunkyStuff = true;
                runAwayTask = new RunAwayFromCreepersTask(CREEPER_KEEP_DISTANCE);
                setTask(runAwayTask);
                // S326: s325o alternated this (55-65) with RunAwayFromHostiles (65/80) 44 times in 5s, each
                // cancelling the other path, and stood still until blown up. Creeper flee wins and holds 1.5s.
                creeperFleeUntilMs = System.currentTimeMillis() + 1500;
                return Math.max(85, 55 + blowingUp.getClientFuseTime(1) * 50);
            }
        }
        synchronized (BaritoneHelper.MINECRAFT_LOCK) {
            // Block projectiles with shield
            if (mod.getModSettings().isDodgeProjectiles()
                    && hasShield(mod)
                    && !mod.getPlayer().getItemCooldownManager().isCoolingDown(
                    //#if MC >= 12102
                    //$$ new net.minecraft.item.ItemStack(offhandItem)
                    //#else
                    offhandItem
                    //#endif
                    )
                    && mod.getClientBaritone().getPathingBehavior().isSafeToCancel()
                    && !mod.getEntityTracker().entityFound(PotionEntity.class) && isProjectileClose(mod)) {
                ItemStack shieldSlot = StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT);
                if (shieldSlot.getItem() != Items.SHIELD) {
                    mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
                } else {
                    startShielding(mod);
                }
                return 60;
            }
            if (blowingUp == null && !isProjectileClose(mod)) {
                stopShielding(mod);
            }
        }

        // S335: s333o stood down to eat at hp<12 with a zombie in melee range; the zombie hit every bite
        // and killed it. With a hostile within 5 blocks and hp<=12, don't yield to eating — fall through to the
        // eatingHurt flee so the bite happens while running.
        boolean meleeThreat = mod.getPlayer().getHealth() <= 12 && mod.getEntityTracker().getTrackedEntities(net.minecraft.entity.mob.HostileEntity.class)
                .stream().anyMatch(h -> h.isAlive() && h.distanceTo(mod.getPlayer()) < 5);
        // S354: s353o stood down to eat rotten flesh while a skeleton shot it from range (hp 20->6, dead).
        // A recent hit or a visible ranged mob within 20 blocks is a threat too.
        if (!meleeThreat && mod.getPlayer().getHealth() <= 16) {
            // S356: s355o still ate between arrows - hurtTime lasts 10 ticks. Any hit in the last 6s counts.
            if (mod.getPlayer().hurtTime > 0) lastHitMs = System.currentTimeMillis();
            meleeThreat = System.currentTimeMillis() - lastHitMs < 6000 || mod.getEntityTracker().getTrackedEntities(net.minecraft.entity.mob.AbstractSkeletonEntity.class)
                    .stream().anyMatch(h -> h.isAlive() && h.distanceTo(mod.getPlayer()) < 20 && mod.getPlayer().canSee(h));
        }
        if ((mod.getFoodChain().needsToEat() && !meleeThreat) || mod.getMLGBucketChain().isFalling(mod)
                || !mod.getMLGBucketChain().doneMLG() || mod.getMLGBucketChain().isChorusFruiting()) {
            killAura.stopShielding(mod);
            stopShielding(mod);
            why = "eat/mlg eat=" + mod.getFoodChain().needsToEat() + " fall=" + mod.getMLGBucketChain().isFalling(mod);
            return Float.NEGATIVE_INFINITY;
        }

        // S285: s283t died twice at hp 6-7 holding food: the force field swapped to the weapon and
        // swung every tick, cancelling each bite. When hurt and eating, let the bite finish and keep fleeing.
        // S334: s332o fled 2.5 min / 330 blocks at hp 6 with food=0 into deep water and a drowned. A bite that
        // can never happen (no food) must not hold the flee; and no 70-flee streak runs past 30s.
        boolean eatingHurt = (mod.getFoodChain().isTryingToEat() || mod.getFoodChain().needsToEat()) && mod.getFoodChain().hasFood() && mod.getPlayer().getHealth() <= 12
                && !fleeStreakTooLong("eating");
        if (eatingHurt) {
            runAwayTask = keepRunAway();
            setTask(runAwayTask);
            return 70;
        }
        // Force field
        doForceField(mod);

        // Dodge projectiles
        if (mod.getPlayer().getHealth() <= 10 && !hasShield(mod) && mod.getModSettings().isDodgeProjectiles() && isProjectileClose(mod)) { // S223: only dodge when something is actually incoming

            if (StorageHelper.getNumberOfThrowawayBlocks(mod) > 0 && !mod.getFoodChain().needsToEat()
                    && mod.getModSettings().isDodgeProjectiles() && isProjectileClose(mod)) {
                doingFunkyStuff = true;
                setTask(new ProjectileProtectionWallTask(mod));
                return 65;
            }

            runAwayTask = new DodgeProjectilesTask(ARROW_KEEP_DISTANCE_HORIZONTAL, ARROW_KEEP_DISTANCE_VERTICAL);
            setTask(runAwayTask);
            return 65;
        }
        // Dodge all mobs cause we boutta die son
        if (isInDanger(mod) && !escapeDragonBreath(mod) && !mod.getFoodChain().isShouldStop()) {
            if (targetEntity == null || WorldHelper.isSurroundedByHostiles()) {
                runAwayTask = keepRunAway();
                setTask(runAwayTask);
                return 70;
            }
        }

        if (mod.getModSettings().shouldDealWithAnnoyingHostiles()) {
            // Deal with hostiles because they are annoying.
            List<LivingEntity> hostiles = mod.getEntityTracker().getHostiles();

            List<LivingEntity> toDealWithList = new ArrayList<>();

            synchronized (BaritoneHelper.MINECRAFT_LOCK) {
                for (LivingEntity hostile : hostiles) {
                    // S304: don't hunt drowned in water - chasing them underwater costs air and time;
                    // the bot just keeps moving and they fall behind.
                    if (hostile instanceof DrownedEntity && (hostile.isTouchingWater() || mod.getPlayer().isTouchingWater())) continue;
                    // S313: s309t wore a gold helm, mob defense still swung at a piglin (pri 65), the
                    // group turned hostile and shot it from 19hp to death. Piglins are neutral to a
                    // gold wearer; hitting one is what makes them hostile. Never pick them as targets.
                    if (hostile instanceof PiglinEntity && mod.getPlayer().getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).getItem() == Items.GOLDEN_HELMET) continue;
                    boolean isRangedOrPoisonous = (hostile instanceof SkeletonEntity
                            || hostile instanceof WitchEntity || hostile instanceof PillagerEntity
                            || hostile instanceof PiglinEntity || hostile instanceof StrayEntity
                            || hostile instanceof CaveSpiderEntity);
                    int annoyingRange = 10;

                    if (isRangedOrPoisonous) {
                        annoyingRange = 20;
                        if (!hasShield(mod)) {
                            annoyingRange = 35;
                        }
                    }

                    // Give each hostile a timer, if they're close for too long deal with them.
                    if (hostile.isInRange(mod.getPlayer(), annoyingRange) && LookHelper.seesPlayer(hostile, mod.getPlayer(), annoyingRange)) {

                        boolean isIgnored = false;
                        for (Class<? extends Entity> ignored : ignoredMobs) {
                            if (ignored.isInstance(hostile)) {
                                isIgnored = true;
                                break;
                            }
                        }

                        // do not go and "attack" these mobs, just hit them if on low HP, or they are close
                        if (isIgnored) {
                            if (mod.getPlayer().getHealth() <= 10) {
                                toDealWithList.add(hostile);
                            }
                        } else {
                            toDealWithList.add(hostile);
                        }
                    }
                }
            }

            // attack entities closest to the player first
            toDealWithList.sort(Comparator.comparingDouble((entity) -> mod.getPlayer().distanceTo(entity)));

            if (!toDealWithList.isEmpty()) {

                // S155: hand priority back if we are still inside the disengage cooldown or have
                // spent our engagement budget. Genuine danger is handled above (creepers,
                // projectiles, isInDanger) so this only stops optional chasing.
                long nowMs = System.currentTimeMillis();
                if (disengageUntilMs > nowMs) {
                    why = "disengage-cooldown";
                    clearEngagement(mod);
                    return 0;
                }
                if (engageStartMs == 0) engageStartMs = nowMs;
                if (nowMs - engageStartMs > MAX_ENGAGE_MS) {
                    disengage(mod, "timeout");
                    return 0;
                }

                // Depending on our weapons/armor, we may choose to straight up kill hostiles if we're not dodging their arrows.
                // Melee damage for fight/flee gate may count axe; KillAura/equip still prefers sword.
                float damage = getBestMeleeAttackDamage(mod);

                int armor = mod.getPlayer().getArmor();

                int shield = hasShield(mod) && damage > 0 ? 3 : 0;

                int canDealWith = (int) Math.ceil((armor * 3.6 / 20.0) + (damage * 0.8) + (shield));
                // Early-game floor: always be willing to fight at least a couple of melee mobs
                // (zombies/spiders). Running away in caves with no sword used to mean death.
                if (damage > 0) {
                    canDealWith = Math.max(canDealWith, 2);
                } else {
                    // Fist/fallback Ã¢â‚¬â€ still try one zombie rather than infinite flee
                    canDealWith = Math.max(canDealWith, 1);
                }
                // Prefer fighting zombies/spiders over fleeing when only melee hostiles
                boolean onlySimpleMelee = toDealWithList.stream().allMatch(e ->
                        (e instanceof ZombieEntity && !(e instanceof net.minecraft.entity.mob.DrownedEntity dd && dd.getMainHandStack().getItem() == Items.TRIDENT)) || e instanceof SpiderEntity || e instanceof SilverfishEntity);
                if (onlySimpleMelee) {
                    canDealWith = Math.max(canDealWith, toDealWithList.size());
                }

                // S346: s345o fled (RunAwayFromHostiles) for 20s while a spider chewed it from hp 10 to 2 and
                // killed it. Spiders outrun the player; running is futile. A spider in reach: turn and kill it.
                Entity closeSpider = toDealWithList.stream().filter(e -> e instanceof SpiderEntity && e.isAlive()
                        && e.squaredDistanceTo(mod.getPlayer()) < 16).findFirst().orElse(null);
                if (closeSpider != null && damage > 0) {
                    runAwayTask = null;
                    fleeHoldUntilMs = 0;
                    lockedOnEntity = closeSpider;
                    setTask(scopedKillTask(mod, closeSpider));
                    return 80;
                }

                // S280: s279t chased a skeleton bare-handed (pick=0, no sword) from hp 19 to death in 15s.
                // Without a real weapon, or once hurt, never chase a ranged mob; break line of sight instead.
                Entity nearest = toDealWithList.get(0);
                boolean rangedTarget = nearest instanceof net.minecraft.entity.mob.AbstractSkeletonEntity
                        || nearest instanceof WitchEntity || nearest instanceof PillagerEntity
                        // S284: s283t charged a blaze at hp 6 while on fire and burned to death.
                        || nearest instanceof net.minecraft.entity.mob.BlazeEntity
                        // S288: s287t punched a creeper at 2:08 (no weapon) from full hp and was blown up.
                        || nearest instanceof CreeperEntity
                        // S349: s348o was impaled by a Drowned; it is a ZombieEntity, so it counted as simple melee.
                        || (nearest instanceof net.minecraft.entity.mob.DrownedEntity d
                            && d.getMainHandStack().getItem() == Items.TRIDENT);
                // S312: s308t fled a skeleton inside a dark cave with only a pickaxe (damage<4) and was
                // shot from 20hp to death; running in a cave never breaks line of sight. Underground,
                // with any tool and hp>12, close in and kill the skeleton instead.
                boolean caveSkeleton = nearest instanceof net.minecraft.entity.mob.AbstractSkeletonEntity
                        && damage >= 2 && mod.getPlayer().getHealth() > 12
                        && mod.getWorld().getLightLevel(net.minecraft.world.LightType.SKY, mod.getPlayer().getBlockPos()) <= 0;
                // S347: s346o flip-flopped a portal-relocate wander with this 5s flee for 2 min at full hp (a
                // skeleton behind cave rock), stood still and finally dug into lava. No line of sight, no flee.
                // S351: s350o was shot 19->2 digging a 1x1 hole: canSee failed from inside the hole, the flee was
                // skipped and the kill branch charged the skeleton with a wooden pick. Recent damage counts as seen,
                // and an unseen, harmless ranged mob is ignored rather than charged.
                boolean rangedSeen = mod.getPlayer().canSee(nearest) || mod.getPlayer().hurtTime > 0
                        || mod.getPlayer().getRecentDamageSource() != null;
                boolean weakVsRanged = damage < 4 || mod.getPlayer().getHealth() <= 10;
                if (rangedTarget && !caveSkeleton && weakVsRanged && !rangedSeen) {
                    runAwayTask = null;
                    return 0;
                }
                if (rangedTarget && !caveSkeleton && rangedSeen && weakVsRanged) {
                    needsChangeOnAttack = false;
                    fleeHoldUntilMs = System.currentTimeMillis() + 5000;
                    runAwayTask = keepRunAway();
                    setTask(runAwayTask);
                    return 80;
                }
                // S293: s292t flee(80) -> a zombie became nearest -> kill branch nulled the flee -> idle(0),
                // twice a second while the skeleton kept shooting. While the flee hold runs, keep fleeing.
                if (System.currentTimeMillis() < fleeHoldUntilMs) {
                    runAwayTask = keepRunAway();
                    setTask(runAwayTask);
                    return 80;
                }
                if (canDealWith >= getDangerousnessScore(toDealWithList) || needsChangeOnAttack) {
                    // we just decided to attack, so we should either get it, or hit something before running away again
                    if (!(mainTask instanceof KillEntitiesTask)) {
                        needsChangeOnAttack = true;
                    }

                    // We can deal with it.
                    runAwayTask = null;
                    Entity toKill = toDealWithList.get(0);
                    lockedOnEntity = toKill;

                    setTask(scopedKillTask(mod, toKill));
                    return 65;
                } else {
                    // We can't deal with it
                    runAwayTask = keepRunAway();
                    setTask(runAwayTask);
                    return 80;
                }
            } else {
                // No annoying hostiles left in range — reset the engagement budget.
                engageStartMs = 0;
            }
        }
        // By default, if we aren't "immediately" in danger but were running away, keep
        // running away until we're good.
        // S290: s289t fled a skeleton, lost line of sight a tick later, the flee finished and the run
        // task walked straight back into its arrows (80<->50 flip every second until death). Hold 5s.
        if (System.currentTimeMillis() < fleeHoldUntilMs) {
            if (runAwayTask == null || runAwayTask.isFinished()) runAwayTask = new RunAwayFromHostilesTask(DANGER_KEEP_DISTANCE, true);
            setTask(runAwayTask);
            return 65;
        }
        if (runAwayTask != null && !runAwayTask.isFinished()) {
            setTask(runAwayTask);
            return cachedLastPriority;
        } else {
            runAwayTask = null;
        }

        if (needsChangeOnAttack && lockedOnEntity != null && lockedOnEntity.isAlive()
                && mod.getPlayer() != null
                && lockedOnEntity.squaredDistanceTo(mod.getPlayer()) < ENGAGE_CHASE_RANGE * ENGAGE_CHASE_RANGE) {
            setTask(scopedKillTask(mod, lockedOnEntity));
            return 65;
        } else {
            needsChangeOnAttack = false;
            lockedOnEntity = null;
        }

        // No immediate threat — drop KillEntities / runaway so UserTaskChain
        // (priority 50) resumes the prior resource/speedrun goal instead of idling.
        if (mainTask != null) {
            mainTask.stop();
            mainTask = null;
        }
        why = "idle";
        runAwayTask = null;
        return 0;
    }

    private static boolean hasShield(AltoClef mod) {
        return mod.getItemStorage().hasItem(Items.SHIELD) || mod.getItemStorage().hasItemInOffhand(Items.SHIELD);
    }

    /**
     * S155: kill only hostiles that stay within {@link #ENGAGE_CHASE_RANGE}. Passing a bare
     * entity class made {@link KillEntitiesTask} target every entity of that type anywhere in
     * the tracker, so one unreachable mob suspended the run indefinitely.
     */
    private Task scopedKillTask(AltoClef mod, Entity target) {
        Predicate<Entity> near = e -> {
            if (e == null || !e.isAlive() || mod.getPlayer() == null) return false;
            return e.squaredDistanceTo(mod.getPlayer()) < ENGAGE_CHASE_RANGE * ENGAGE_CHASE_RANGE;
        };
        return new KillEntitiesTask(near, target.getClass());
    }

    /**
     * S155: abandon the optional hostile engagement and start a cooldown so the run task
     * actually gets a sustained window instead of being re-preempted on the next tick.
     */
    private void disengage(AltoClef mod, String why) {
        engageStartMs = 0;
        disengageUntilMs = System.currentTimeMillis() + DISENGAGE_COOLDOWN_MS;
        float hp = mod.getPlayer() == null ? -1f : mod.getPlayer().getHealth();
        T2Log.warn("S155", "mob-defense disengage (" + why + ") hp=" + hp
                + " - handing priority back to the run task");
        clearEngagement(mod);
    }

    private void clearEngagement(AltoClef mod) {
        needsChangeOnAttack = false;
        lockedOnEntity = null;
        runAwayTask = null;
        if (mainTask != null) {
            mainTask.stop();
            mainTask = null;
        }
    }

    private static Item getBestSword(AltoClef mod) {
        Item best = null;
        float bestDamage = 0;
        for (ItemStack stack : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
            float damage = ItemHelper.meleeDamageOf(stack.getItem());
            if (damage > bestDamage) {
                best = stack.getItem();
                bestDamage = damage;
            }
        }
        return best;
    }

    /** Best melee damage from sword or axe (axe for fight/flee gate only; combat equip prefers sword). */
    private static float getBestMeleeAttackDamage(AltoClef mod) {
        float best = 0;
        for (ItemStack stack : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
            float dmg = ItemHelper.meleeDamageOf(stack.getItem()) + 1;
            if (dmg > best) best = dmg;
        }
        return best;
    }

    private BlockPos isInsideFireAndOnFire(AltoClef mod) {
        boolean onFire = mod.getPlayer().isOnFire();
        if (!onFire) return null;
        BlockPos p = mod.getPlayer().getBlockPos();
        BlockPos[] toCheck = new BlockPos[]{
                p,
                p.add(1,0,0),
                p.add(1,0,-1),
                p.add(0,0,-1),
                p.add(-1,0,-1),
                p.add(-1,0,0),
                p.add(-1,0,1),
                p.add(0,0,1),
                p.add(1,0,1)
        };
        for (BlockPos check : toCheck) {
            Block b = mod.getWorld().getBlockState(check).getBlock();
            if (b instanceof AbstractFireBlock) {
                return check;
            }
        }
        return null;
    }

    private void putOutFire(AltoClef mod, BlockPos pos) {
        Optional<Rotation> reach = LookHelper.getReach(pos);
        if (reach.isPresent()) {
            Baritone b = mod.getClientBaritone();
            if (LookHelper.isLookingAt(mod, pos)) {
                b.getPathingBehavior().requestPause();
                b.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                return;
            }
            LookHelper.lookAt(reach.get());
        }
    }

    private void doForceField(AltoClef mod) {
        killAura.tickStart();

        // Hit all hostiles close to us.
        List<Entity> entities = mod.getEntityTracker().getCloseEntities();
        try {
            for (Entity entity : entities) {
                boolean shouldForce = false;
                if (mod.getBehaviour().shouldExcludeFromForcefield(entity)) continue;
                if (entity instanceof MobEntity) {
                    if (EntityHelper.isProbablyHostileToPlayer(mod, entity)) {
                        if (LookHelper.seesPlayer(entity, mod.getPlayer(), 10)) {
                            shouldForce = true;
                        }
                    }
                } else if (entity instanceof FireballEntity) {
                    // Ghast ball
                    shouldForce = true;
                }

                if (shouldForce) {
                    killAura.applyAura(entity);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        killAura.tickEnd(mod);
    }


    private long creeperFleeUntilMs;
    private Task creeperKill;
    private int creeperKillId;

    private CreeperEntity getClosestFusingCreeper(AltoClef mod) {
        double worstSafety = Float.POSITIVE_INFINITY;
        CreeperEntity target = null;
        try {
            List<CreeperEntity> creepers = mod.getEntityTracker().getTrackedEntities(CreeperEntity.class);
            for (CreeperEntity creeper : creepers) {
                if (creeper == null) continue;
                // S321: s318t let a creeper walk up (17 -> 0 blocks in 7s) and only fled once the fuse lit, 1s before
                // the blast. Treat any creeper within 4.5 blocks as the threat so the run-away starts pre-fuse.
                if (creeper.getClientFuseTime(1) < 0.001 && creeper.squaredDistanceTo(mod.getPlayer()) > 4.5 * 4.5) continue;

                // We want to pick the closest creeper, but FIRST pick creepers about to blow
                // At max fuse, the cost goes to basically zero.
                double safety = getCreeperSafety(mod.getPlayer().getPos(), creeper);
                if (safety < worstSafety) {
                    target = creeper;                    worstSafety = safety;
                }
            }
        } catch (ConcurrentModificationException | ArrayIndexOutOfBoundsException | NullPointerException e) {
            // IDK why but these exceptions happen sometimes. It's extremely bizarre and I
            // have no idea why.
            Debug.logWarning("Weird Exception caught and ignored while scanning for creepers: " + e.getMessage());
            return target;
        }
        return target;
    }

    private boolean isProjectileClose(AltoClef mod) {
        List<CachedProjectile> projectiles = mod.getEntityTracker().getProjectiles();
        try {
            for (CachedProjectile projectile : projectiles) {
                if (projectile.position.squaredDistanceTo(mod.getPlayer().getPos()) < 150) {
                    boolean isGhastBall = projectile.projectileType == FireballEntity.class;
                    if (isGhastBall) {
                        Optional<Entity> ghastBall = mod.getEntityTracker().getClosestEntity(FireballEntity.class);
                        Optional<Entity> ghast = mod.getEntityTracker().getClosestEntity(GhastEntity.class);
                        if (ghastBall.isPresent() && ghast.isPresent() && runAwayTask == null
                                && mod.getClientBaritone().getPathingBehavior().isSafeToCancel()) {
                            mod.getClientBaritone().getPathingBehavior().requestPause();
                            LookHelper.lookAt(mod, ghast.get().getEyePos());
                        }
                        return false;
                        // Ignore ghast balls
                    }
                    if (projectile.projectileType == DragonFireballEntity.class) {
                        // Ignore dragon fireballs
                        continue;
                    }
                    if (projectile.projectileType == ArrowEntity.class || projectile.projectileType == SpectralArrowEntity.class || projectile.projectileType == SmallFireballEntity.class) {
                        // check if the projectile is going away from us
                        // not so fancy math... this should work better than the previous approach (I hope just adding the velocity doesn't cause any issues..)
                        PlayerEntity player = mod.getPlayer();
                        if (player.squaredDistanceTo(projectile.position) < player.squaredDistanceTo(projectile.position.add(projectile.velocity))) {
                            continue;
                        }
                    }

                    Vec3d expectedHit = ProjectileHelper.calculateArrowClosestApproach(projectile, mod.getPlayer());

                    Vec3d delta = mod.getPlayer().getPos().subtract(expectedHit);

                    double horizontalDistanceSq = delta.x * delta.x + delta.z * delta.z;
                    double verticalDistance = Math.abs(delta.y);
                    if (horizontalDistanceSq < ARROW_KEEP_DISTANCE_HORIZONTAL * ARROW_KEEP_DISTANCE_HORIZONTAL
                            && verticalDistance < ARROW_KEEP_DISTANCE_VERTICAL) {
                        if (mod.getClientBaritone().getPathingBehavior().isSafeToCancel()
                                && hasShield(mod)) {
                            mod.getClientBaritone().getPathingBehavior().requestPause();
                            LookHelper.lookAt(mod, projectile.position.add(0, 0.3, 0));
                        }
                        return true;
                    }
                }
            }

        } catch (ConcurrentModificationException e) {
            Debug.logWarning(e.getMessage());
        }

        // TODO refactor this into something more reliable for all mobs
        for (SkeletonEntity skeleton : mod.getEntityTracker().getTrackedEntities(SkeletonEntity.class)) {
            if (skeleton.distanceTo(mod.getPlayer()) > 10 || !skeleton.canSee(mod.getPlayer())) continue;

            // when the skeleton is about to shoot (it takes 5 ticks to raise the shield)
            if (skeleton.getItemUseTime() > 15) {
                return true;
            }
        }

        return false;
    }

    private Optional<Entity> getUniversallyDangerousMob(AltoClef mod) {
        // Wither skeletons are dangerous because of the wither effect. Oof kinda obvious.
        // If we merely force field them, we will run into them and get the wither effect which will kill us.

        Class<?>[] dangerousMobs = new Class[]{Entities.WARDEN, WitherEntity.class, WitherSkeletonEntity.class,
                HoglinEntity.class, ZoglinEntity.class, Entities.PIGLIN_BRUTE, VindicatorEntity.class};

        double range = SAFE_KEEP_DISTANCE - 2;

        for (Class<?> dangerous : dangerousMobs) {
            Optional<Entity> entity = mod.getEntityTracker().getClosestEntity(dangerous);

            if (entity.isPresent()) {
                if (entity.get().squaredDistanceTo(mod.getPlayer()) < range * range && EntityHelper.isAngryAtPlayer(mod, entity.get())) {
                    return entity;
                }
            }
        }

        return Optional.empty();
    }

    private boolean isInDanger(AltoClef mod) {
        boolean witchNearby = mod.getEntityTracker().entityFound(WitchEntity.class);

        float health = mod.getPlayer().getHealth();
        // S225: low HP alone is not danger - with nothing hostile nearby, running away just
        // blocks eating/regen (runawaydiag: hp=10 food=6 danger=- held pri 70 for minutes).
        if (health <= 10 && !witchNearby && angryHostileNear(mod)) {
            return true;
        }
        if (mod.getPlayer().hasStatusEffect(StatusEffects.WITHER) ||
                (mod.getPlayer().hasStatusEffect(StatusEffects.POISON) && !witchNearby)) {
            return true;
        }
        if (WorldHelper.isVulnerable()) {
            // If hostile mobs are nearby...
            try {
                ClientPlayerEntity player = mod.getPlayer();
                List<LivingEntity> hostiles = mod.getEntityTracker().getHostiles();

                synchronized (BaritoneHelper.MINECRAFT_LOCK) {
                    for (Entity entity : hostiles) {
                        if (entity.isInRange(player, SAFE_KEEP_DISTANCE)
                                && !mod.getBehaviour().shouldExcludeFromForcefield(entity)
                                && EntityHelper.isAngryAtPlayer(mod, entity)) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                Debug.logWarning("Weird multithread exception. Will fix later. " + e.getMessage());
            }
        }
        return false;
    }

    private boolean angryHostileNear(AltoClef mod) {
        try {
            ClientPlayerEntity player = mod.getPlayer();
            synchronized (BaritoneHelper.MINECRAFT_LOCK) {
                for (Entity entity : mod.getEntityTracker().getHostiles()) {
                    // S226: skeletons/pillagers hurt from range; SAFE_KEEP_DISTANCE alone let them snipe us to death
                    boolean ranged = entity instanceof net.minecraft.entity.mob.AbstractSkeletonEntity
                            || entity instanceof net.minecraft.entity.mob.PillagerEntity;
                    if (entity.isInRange(player, ranged ? 16 : SAFE_KEEP_DISTANCE)
                            && !mod.getBehaviour().shouldExcludeFromForcefield(entity)
                            && EntityHelper.isAngryAtPlayer(mod, entity)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Debug.logWarning("S225 hostile scan: " + e.getMessage());
        }
        return false;
    }

    public void setTargetEntity(Entity entity) {
        targetEntity = entity;
    }

    public void resetTargetEntity() {
        targetEntity = null;
    }

    public void setForceFieldRange(double range) {
        killAura.setRange(range);
    }

    public void resetForceField() {
        killAura.setRange(Double.POSITIVE_INFINITY);
    }

    public boolean isDoingAcrobatics() {
        return doingFunkyStuff;
    }

    public boolean isPuttingOutFire() {
        return wasPuttingOutFire;
    }

    @Override
    public boolean isActive() {
        // We're always checking for mobs
        return true;
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {
        // Must clear — leaving a finished KillEntitiesTask as mainTask kept the
        // defense chain "busy" and blocked clean resume of the user/resource task.
        mainTask = null;
        lockedOnEntity = null;
        needsChangeOnAttack = false;
        runAwayTask = null;
    }

    @Override
    public String getName() {
        return "Mob Defense";
    }
}
