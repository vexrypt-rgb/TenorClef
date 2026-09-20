package kaptainwutax.tungsten.agent;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RayTraceContext;

/** 1.16.1 RayTraceContext takes Entity, not ShapeContext. */
public class AgentRaycastContext extends RayTraceContext {
	public AgentRaycastContext(Vec3d start, Vec3d end, ShapeType shapeType, FluidHandling fluidHandling, Agent agent) {
		super(start, end, shapeType, fluidHandling, entityOrPlayer(agent));
	}

	private static Entity entityOrPlayer(Agent agent) {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc != null ? mc.player : null;
	}
}
