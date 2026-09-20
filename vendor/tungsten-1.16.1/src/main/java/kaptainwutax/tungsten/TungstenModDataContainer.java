package kaptainwutax.tungsten;

import kaptainwutax.tungsten.path.PathExecutor;
import kaptainwutax.tungsten.path.PathFinder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public class TungstenModDataContainer {
	public static PlayerEntity player;
	public static PathExecutor EXECUTOR;
	public static PathFinder PATHFINDER = new PathFinder();
	public static World world;
}
