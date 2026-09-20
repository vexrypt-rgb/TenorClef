package adris.altoclef.tasks.speedrun.testrun2.combat;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasks.speedrun.testrun2.SpeedrunOpt;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.TungstenHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Fight one nearby living entity with whatever is in the inventory.
 *
 * Not a wallhack aura. Look, walk in, wait the 1.9 cooldown, crit if falling,
 * raise shield vs projectiles, back off creepers.
 */
public class AnyWeaponCombatTask extends Task {

    public static final double REACH = 3.1;
    public static final double CHASE = 16.0;
    public static final double CREEPER_BACK = 5.5;

    private final Predicate<LivingEntity> filter;
    private final int maxTicks;
    private LivingEntity target;
    private int ticks;
    private int hits;
    private int drawTicks;
    private boolean done;

    public AnyWeaponCombatTask(Predicate<LivingEntity> filter) {
        this(filter, 20 * 40);
    }

    public AnyWeaponCombatTask(Predicate<LivingEntity> filter, int maxTicks) {
        this.filter = filter;
        this.maxTicks = maxTicks;
    }

    @Override
    protected void onStart() {
        ticks = 0;
        hits = 0;
        done = false;
        target = null;
        try { TungstenHelper.stop(); } catch (Throwable ignored) {}
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        PlayerEntity me = mod.getPlayer();
        if (me == null || mod.getWorld() == null) return null;
        ticks++;
        Task fix = adris.altoclef.tasks.speedrun.testrun2.T2Brain.help(mod, "FIGHT", this);
        if (fix != null && fix != this) return fix;
        if (ticks >= maxTicks) {
            done = true;
            return null;
        }

        if (target == null || !target.isAlive() || McCompat.gone(target)) {
            target = pick(mod, me);
        }
        if (target == null) {
            done = true;
            return null;
        }

        if (target instanceof EnderDragonEntity) {
            if (ZeroCycle.tick(mod, target)) return null;
        }

        AttributeSwap.Pair pair = AttributeSwap.plan(mod);
        if (!(target instanceof EnderDragonEntity) && SpeedrunOpt.ATTRIBUTE_SWAP && !pair.same()) {
            AttributeSwap.equip(mod, pair.charger());
        } else if (!(target instanceof EnderDragonEntity)) {
            equipBest(mod);
        }
        if (ProjectileDodge.tick(mod)) {
            try {
                lookAt(mod, BowLead.aimPoint(me, target, me.getMainHandStack(), bowCharge(me)));
            } catch (Throwable ignored) {}
            return null;
        }
        ItemStack hand = ItemStack.EMPTY;
        try { hand = me.getMainHandStack(); } catch (Throwable ignored) {}
        lookAt(mod, BowLead.aimPoint(me, target, hand, bowCharge(me)));

        double dist = me.getPos().distanceTo(target.getPos());
        boolean creeper = target instanceof CreeperEntity;
        boolean ranged = target instanceof SkeletonEntity || target instanceof WitchEntity;

        if (holdingBow(mod) && dist > REACH + 1 && dist < 28) {
            drawBow(mod);
            return null;
        }

        if (creeper && dist < CREEPER_BACK) {
            retreat(mod, target);
            return null;
        }
        // Never raise shield here. useKey + offhand shield + SurviveTick release
        // is the raise/lower pump that froze sprint.

        if (dist > REACH) {
            approach(mod, target);
            return null;
        }

        if (cooldownReady(me)) {
            boolean falling = !me.isOnGround() && me.getVelocity().y < -0.08;
            if (AttributeSwap.shouldMaceSmash(mod, me)) {
                Item mace = McCompat.item("MACE");
                if (mace != null) AttributeSwap.equip(mod, mace);
            } else if (SpeedrunOpt.ATTRIBUTE_SWAP && !pair.same()) {
                // cooldown was charged on the fast item; damage is read from this one
                AttributeSwap.equip(mod, pair.hitter());
            }
            attack(mod, target);
            hits++;
            if (SpeedrunOpt.ATTRIBUTE_SWAP && !pair.same()) {
                AttributeSwap.equip(mod, pair.charger());
            }
            if (!falling) {
                tryReleaseForward(mod);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private LivingEntity pick(AltoClef mod, PlayerEntity me) {
        try {
            List<LivingEntity> list = mod.getEntityTracker().getTrackedEntities(LivingEntity.class);
            if (list == null) return null;
            return list.stream()
                    .filter(e -> e != me && e.isAlive() && !McCompat.gone(e))
                    .filter(filter)
                    .filter(e -> me.getPos().distanceTo(e.getPos()) <= CHASE)
                    .min(Comparator.comparingDouble(e -> me.getPos().squaredDistanceTo(e.getPos())))
                    .orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private void equipBest(AltoClef mod) {
        Item best = Items.AIR;
        double bestDps = -1;
        try {
            for (ItemStack stack : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
                if (stack == null || stack.isEmpty()) continue;
                double d = WeaponPicker.dps(stack.getItem());
                if (d > bestDps) {
                    bestDps = d;
                    best = stack.getItem();
                }
            }
        } catch (Throwable ignored) {}
        if (best == Items.AIR) return;
        try {
            Class<?> slots = Class.forName("adris.altoclef.util.helpers.StorageHelper");
            // forceEquipItemToSlot / SlotHandler — try a few fork names
            var handler = AltoClef.getInstance().getSlotHandler();
            handler.getClass().getMethod("forceEquipItem", Item.class).invoke(handler, best);
        } catch (Throwable t) {
            try {
                AltoClef.getInstance().getSlotHandler()
                        .getClass()
                        .getMethod("forceEquipItem", net.minecraft.item.Item[].class)
                        .invoke(AltoClef.getInstance().getSlotHandler(), (Object) new Item[]{best});
            } catch (Throwable ignored) {}
        }
    }

    private boolean hasShield(AltoClef mod) {
        try {
            return mod.getItemStorage().hasItem(Items.SHIELD);
        } catch (Throwable t) {
            return false;
        }
    }

    private void raiseShield(AltoClef mod) {
        try {
            var handler = mod.getSlotHandler();
            handler.getClass().getMethod("forceEquipItemToOffhand", Item.class).invoke(handler, Items.SHIELD);
        } catch (Throwable ignored) {}
        try {
            var opts = mod.getPlayer().input;
            // 1.21 PlayerInput is a record; older is PlayerInput with holdingBackwards etc.
        } catch (Throwable ignored) {}
        try {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            mc.options.useKey.setPressed(true);
        } catch (Throwable ignored) {}
    }

    private boolean cooldownReady(PlayerEntity me) {
        try {
            return me.getAttackCooldownProgress(0.5f) >= 0.92f;
        } catch (Throwable t) {
            return ticks % 12 == 0;
        }
    }

    private void attack(AltoClef mod, LivingEntity e) {
        try {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.interactionManager != null) {
                mc.interactionManager.attackEntity(mod.getPlayer(), e);
                mod.getPlayer().swingHand(Hand.MAIN_HAND);
            }
        } catch (Throwable t) {
            try {
                mod.getPlayer().attack(e);
                mod.getPlayer().swingHand(Hand.MAIN_HAND);
            } catch (Throwable ignored) {}
        }
    }

    private void lookAt(AltoClef mod, Vec3d pos) {
        try {
            Class<?> look = Class.forName("adris.altoclef.util.helpers.LookHelper");
            look.getMethod("lookAt", AltoClef.class, Vec3d.class).invoke(null, mod, pos);
        } catch (Throwable ignored) {}
    }

    private void approach(AltoClef mod, LivingEntity e) {
        try {
            TungstenHelper.tryPathTo(e.getPos());
        } catch (Throwable ignored) {}
    }

    private void retreat(AltoClef mod, LivingEntity e) {
        Vec3d away = mod.getPlayer().getPos().subtract(e.getPos()).normalize().multiply(4);
        Vec3d dest = mod.getPlayer().getPos().add(away);
        try {
            TungstenHelper.tryPathTo(dest);
        } catch (Throwable ignored) {}
        try {
            net.minecraft.client.MinecraftClient.getInstance().options.jumpKey.setPressed(true);
        } catch (Throwable ignored) {}
    }

    private boolean holdingBow(AltoClef mod) {
        try {
            ItemStack st = mod.getPlayer().getMainHandStack();
            return st != null && (st.getItem() == Items.BOW || st.getItem() == Items.CROSSBOW);
        } catch (Throwable t) {
            return false;
        }
    }

    private float bowCharge(PlayerEntity me) {
        try {
            ItemStack st = me.getMainHandStack();
            if (BowLead.isCrossbow(st)) return 1f;
            int used = me.getItemUseTime();
            // vanilla bow full at 20 ticks
            return Math.min(1f, used / 20f);
        } catch (Throwable t) {
            return drawTicks / 20f;
        }
    }

    private void drawBow(AltoClef mod) {
        try {
            var opts = net.minecraft.client.MinecraftClient.getInstance().options;
            ItemStack st = mod.getPlayer().getMainHandStack();
            if (BowLead.isCrossbow(st)) {
                if (BowLead.charged(st)) {
                    opts.useKey.setPressed(true);
                    drawTicks++;
                    if (drawTicks >= 3) {
                        opts.useKey.setPressed(false);
                        drawTicks = 0;
                    }
                } else {
                    opts.useKey.setPressed(true);
                    drawTicks++;
                }
                return;
            }
            drawTicks++;
            if (drawTicks < 22) {
                opts.useKey.setPressed(true);
            } else {
                opts.useKey.setPressed(false);
                drawTicks = 0;
            }
        } catch (Throwable ignored) {}
    }

    private void tryReleaseForward(AltoClef mod) {
        try {
            net.minecraft.client.MinecraftClient.getInstance().options.forwardKey.setPressed(false);
            net.minecraft.client.MinecraftClient.getInstance().options.sprintKey.setPressed(false);
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isFinished() {
        if (done) return true;
        if (target != null && !target.isAlive()) {
            done = true;
            return true;
        }
        return false;
    }

    @Override
    protected void onStop(Task interruptTask) {
        try {
            var opts = net.minecraft.client.MinecraftClient.getInstance().options;
            opts.useKey.setPressed(false);
            opts.jumpKey.setPressed(false);
        } catch (Throwable ignored) {}
        try { TungstenHelper.stop(); } catch (Throwable ignored) {}
        if (hits > 0) Debug.logMessage("TESRUN2 combat hits=" + hits);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof AnyWeaponCombatTask;
    }

    @Override
    protected String toDebugString() {
        return "fight-any " + (target == null ? "?" : target.getName().getString());
    }
}
