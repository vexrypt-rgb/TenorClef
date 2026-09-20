package kaptainwutax.tungsten;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * 1.16.1 Tungsten entry (bridge-compatible). Full physics Agent port lives in
 * java-fullport-wip/; this slim build provides PathFinder/FollowEntityTask APIs
 * expected by AltoClef TungstenBridge.
 */
public class TungstenMod implements ClientModInitializer {
	public static final String MOD_ID = "tungsten";
	public static MinecraftClient mc;
	public static Vec3d TARGET = new Vec3d(0.5D, 10.0D, 0.5D);

	@Override
	public void onInitializeClient() {
		mc = MinecraftClient.getInstance();
		TungstenModDataContainer.EXECUTOR = new kaptainwutax.tungsten.path.PathExecutor();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.world == null) return;
			mc = client;
			if (TungstenModDataContainer.EXECUTOR != null) {
				TungstenModDataContainer.EXECUTOR.tick(client);
			}
			kaptainwutax.tungsten.task.FollowEntityTask.clientTick(client);
		});
	}
}
