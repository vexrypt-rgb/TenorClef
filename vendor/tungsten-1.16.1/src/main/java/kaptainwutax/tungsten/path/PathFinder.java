package kaptainwutax.tungsten.path;

import kaptainwutax.tungsten.TungstenModDataContainer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridge-compatible pathfinder. Slim 1.16.1: direct-walk via PathExecutor.
 * Full A* Agent physics: see ../java-fullport-wip (WIP).
 */
public class PathFinder {
	public final AtomicBoolean active = new AtomicBoolean(false);
	public final AtomicBoolean stop = new AtomicBoolean(false);

	public synchronized void find(WorldView world, Vec3d target, PlayerEntity player) {
		stop.set(false);
		active.set(true);
		if (TungstenModDataContainer.EXECUTOR != null) {
			TungstenModDataContainer.EXECUTOR.startGoTo(target);
		}
	}
}
