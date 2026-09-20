package kaptainwutax.tungsten.path;

import kaptainwutax.tungsten.TungstenModDataContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes a direct-walk toward a target. Keeps PathExecutor.isRunning()/stop
 * shape expected by TungstenBridge reflection.
 */
public class PathExecutor {
	public boolean stop = false;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private Vec3d target;

	public PathExecutor() {}

	/** Overloaded ctor kept for tip-compat call sites that pass a boolean. */
	public PathExecutor(boolean ignored) {}

	public void startGoTo(Vec3d target) {
		this.target = target;
		this.stop = false;
		running.set(true);
		if (TungstenModDataContainer.PATHFINDER != null) {
			TungstenModDataContainer.PATHFINDER.active.set(true);
			TungstenModDataContainer.PATHFINDER.stop.set(false);
		}
	}

	public boolean isRunning() {
		return running.get();
	}

	public void tick(MinecraftClient client) {
		if (!running.get() || stop) {
			finish();
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || target == null) {
			finish();
			return;
		}
		double dx = target.x - player.getX();
		double dz = target.z - player.getZ();
		double dy = target.y - player.getY();
		double horiz = Math.sqrt(dx * dx + dz * dz);
		if (horiz < 0.6 && Math.abs(dy) < 1.5) {
			finish();
			return;
		}
		float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		player.yaw = yaw;
		player.headYaw = yaw;
		player.bodyYaw = yaw;
		player.input.movementForward = 1.0F;
		player.input.movementSideways = 0.0F;
		player.setSprinting(horiz > 2.0);
		if (dy > 0.6 && player.isOnGround()) {
			player.jump();
		}
		if (TungstenModDataContainer.PATHFINDER != null && TungstenModDataContainer.PATHFINDER.stop.get()) {
			finish();
		}
	}

	private void finish() {
		running.set(false);
		stop = false;
		target = null;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null && client.player.input != null) {
			client.player.input.movementForward = 0;
			client.player.input.movementSideways = 0;
		}
		if (TungstenModDataContainer.PATHFINDER != null) {
			TungstenModDataContainer.PATHFINDER.active.set(false);
		}
	}
}
