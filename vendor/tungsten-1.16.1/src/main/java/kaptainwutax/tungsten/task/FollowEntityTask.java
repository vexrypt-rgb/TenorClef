package kaptainwutax.tungsten.task;

import kaptainwutax.tungsten.TungstenModDataContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bridge-compatible follow task (start/stop/isActive).
 */
public final class FollowEntityTask {
	private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
	private static final AtomicReference<Entity> TARGET = new AtomicReference<>();
	private static double maintainDistance = 2.0;

	private FollowEntityTask() {}

	public static void start(Entity entity, double distance) {
		TARGET.set(entity);
		maintainDistance = distance;
		ACTIVE.set(true);
		if (TungstenModDataContainer.PATHFINDER != null) {
			TungstenModDataContainer.PATHFINDER.active.set(true);
		}
	}

	public static void stop() {
		ACTIVE.set(false);
		TARGET.set(null);
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null && client.player.input != null) {
			client.player.input.movementForward = 0;
			client.player.input.movementSideways = 0;
		}
		if (TungstenModDataContainer.PATHFINDER != null) {
			TungstenModDataContainer.PATHFINDER.active.set(false);
		}
	}

	public static boolean isActive() {
		return ACTIVE.get();
	}

	public static void clientTick(MinecraftClient client) {
		if (!ACTIVE.get() || client.player == null) return;
		Entity e = TARGET.get();
		if (e == null || !e.isAlive() || e.removed) {
			stop();
			return;
		}
		Vec3d p = client.player.getPos();
		Vec3d t = e.getPos();
		double dx = t.x - p.x;
		double dz = t.z - p.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist <= maintainDistance) {
			client.player.input.movementForward = 0;
			return;
		}
		float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		client.player.yaw = yaw;
		client.player.headYaw = yaw;
		client.player.input.movementForward = 1.0F;
		client.player.setSprinting(dist > maintainDistance + 2);
	}
}
