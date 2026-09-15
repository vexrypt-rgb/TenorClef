package adris.altoclef.tasks.speedrun.stronghold;

import net.minecraft.client.MinecraftClient;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EyeOfEnderEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Locate stronghold using Ninjabrain-style math.
 *
 * Flow:
 * 1. Throw eye of ender
 * 2. Track EyeOfEnderEntity, record position + exact direction to entity
 * 3. Feed throw into NinjabrainCalculator
 * 4. After 1–2 throws (bots get clean angles), path to best prediction
 * 5. Optionally second throw if confidence is low
 */
public class NinjabrainLocateTask extends Task {

    private enum Stage {
        THROW,
        TRACK_EYE,
        COMPUTE,
        TRAVEL,
        DONE
    }

    private final AltoClef mod;
    private final NinjabrainCalculator calc = new NinjabrainCalculator();
    private final double minConfidence;

    private Stage stage = Stage.THROW;
    private Task travelTask;
    private StrongholdPrediction best;
    private int throwsDone = 0;
    private final int maxThrows;

    private final TimerGame throwCooldown = new TimerGame(1.5);
    private Vec3d throwOrigin = null;
    private int trackTicks = 0;

    public NinjabrainLocateTask(AltoClef mod) {
        this(mod, 2, 0.55);
    }

    public NinjabrainLocateTask(AltoClef mod, int maxThrows, double minConfidence) {
        this.mod = mod;
        this.maxThrows = Math.max(1, maxThrows);
        this.minConfidence = minConfidence;
        // Bot angles are precise
        calc.setSigmaDegrees(0.08);
    }

    @Override
    protected void onStart() {
        stage = Stage.THROW;
        calc.clearThrows();
        throwsDone = 0;
        best = null;
        travelTask = null;
        setDebugState("Ninjabrain locate – ready to throw");
    }

    @Override
    protected Task onTick() {
        switch (stage) {
            case THROW:
                return doThrow();
            case TRACK_EYE:
                return trackEye();
            case COMPUTE:
                return compute();
            case TRAVEL:
                return travel();
            default:
                return null;
        }
    }

    private Task doThrow() {
        if (StorageHelper.getItemCount(mod, Items.ENDER_EYE) < 1) {
            setDebugState("Ninjabrain – no eyes left");
            stage = Stage.DONE;
            return null;
        }

        // If we already have a high-confidence prediction, travel
        if (best != null && best.probability >= minConfidence && throwsDone >= 1) {
            stage = Stage.TRAVEL;
            return null;
        }

        if (throwsDone >= maxThrows && best != null) {
            stage = Stage.TRAVEL;
            return null;
        }

        if (!throwCooldown.elapsed() && throwsDone > 0) {
            return null;
        }

        setDebugState("Ninjabrain – throwing eye #" + (throwsDone + 1));
        mod.getSlotHandler().forceEquipItem(Items.ENDER_EYE);

        // Throw upward-ish
        mod.getPlayer().setPitch(-20);
        try {
            net.minecraft.client.MinecraftClient.getInstance().interactionManager.interactItem(
                    mod.getPlayer(),
                    Hand.MAIN_HAND
            );
            mod.getPlayer().swingHand(Hand.MAIN_HAND);
        } catch (Exception e) {
            setDebugState("Throw failed: " + e.getMessage());
            return null;
        }

        throwOrigin = mod.getPlayer().getPos();
        throwCooldown.reset();
        trackTicks = 0;
        stage = Stage.TRACK_EYE;
        return null;
    }

    private Task trackEye() {
        trackTicks++;
        Optional<Entity> eye = mod.getEntityTracker().getClosestEntity(
                mod.getPlayer().getPos(),
                e -> e instanceof EyeOfEnderEntity,
                EyeOfEnderEntity.class
        );

        if (eye.isEmpty()) {
            if (trackTicks > 40) {
                // Missed the entity – try again
                setDebugState("Ninjabrain – lost eye entity, retry throw");
                stage = Stage.THROW;
            }
            return null;
        }

        EyeOfEnderEntity eoe = (EyeOfEnderEntity) eye.get();
        Vec3d eyePos = eoe.getPos();
        Vec3d from = throwOrigin != null ? throwOrigin : mod.getPlayer().getPos();

        double dx = eyePos.x - from.x;
        double dz = eyePos.z - from.z;
        // Convert direction to Minecraft yaw
        // dirX = -sin(yaw), dirZ = cos(yaw)
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));

        // Wait a few ticks for the eye to settle on its path
        if (trackTicks < 8) {
            setDebugState("Ninjabrain – tracking eye…");
            // Look at eye for aesthetics / consistency
            LookHelper.lookAt(mod, eyePos);
            return null;
        }

        EyeThrow measurement = new EyeThrow(from.x, from.z, yaw);
        calc.addThrow(measurement);
        throwsDone++;
        setDebugState(String.format("Ninjabrain – recorded throw #%d yaw=%.3f", throwsDone, yaw));

        stage = Stage.COMPUTE;
        return null;
    }

    private Task compute() {
        double px = mod.getPlayer().getX();
        double pz = mod.getPlayer().getZ();
        List<StrongholdPrediction> preds = calc.predict(px, pz, 5);

        if (preds.isEmpty()) {
            setDebugState("Ninjabrain – no candidates, throw again");
            stage = Stage.THROW;
            return null;
        }

        best = preds.get(0);
        StringBuilder sb = new StringBuilder("Ninjabrain top: ");
        for (int i = 0; i < Math.min(3, preds.size()); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(preds.get(i).toString());
        }
        setDebugState(sb.toString());

        if (best.probability >= minConfidence || throwsDone >= maxThrows) {
            stage = Stage.TRAVEL;
        } else {
            // Move sideways a bit for a better second baseline, then throw again
            stage = Stage.THROW;
        }
        return null;
    }

    private Task travel() {
        if (best == null) {
            stage = Stage.THROW;
            return null;
        }

        BlockPos target = best.eightEight();
        setDebugState("Ninjabrain – traveling to " + best);

        double distSq = mod.getPlayer().squaredDistanceTo(
                target.getX() + 0.5, mod.getPlayer().getY(), target.getZ() + 0.5
        );
        if (distSq < 16 * 16) {
            // Close enough – stronghold search continues in parent task
            stage = Stage.DONE;
            return null;
        }

        if (travelTask == null || travelTask.isFinished()) {
            travelTask = new GetToBlockTask(target, false);
        }
        return travelTask;
    }

    public StrongholdPrediction getBestPrediction() {
        return best;
    }

    public NinjabrainCalculator getCalculator() {
        return calc;
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof NinjabrainLocateTask;
    }

    @Override
    protected String toDebugString() {
        return "NinjabrainLocateTask[" + stage + " throws=" + throwsDone + "]";
    }

    @Override
    public boolean isFinished() {
        return stage == Stage.DONE;
    }
}
