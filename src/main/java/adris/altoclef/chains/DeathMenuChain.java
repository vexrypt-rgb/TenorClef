package adris.altoclef.chains;

import adris.altoclef.multiversion.ScreenVer;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.mixins.DeathScreenAccessor;
import adris.altoclef.multiversion.ConnectScreenVer;
import adris.altoclef.multiversion.entity.PlayerVer;
import adris.altoclef.tasksystem.TaskChain;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.time.TimerGame;
import adris.altoclef.util.time.TimerReal;
import net.minecraft.client.MinecraftClient;
//#if MC < 260000
import net.minecraft.client.gui.screen.*;
//#else
//$$ import net.minecraft.client.gui.screens.DeathScreen;
//$$ import net.minecraft.client.gui.screens.DisconnectedScreen;
//$$ import net.minecraft.client.gui.screens.Screen;
//$$ import net.minecraft.client.gui.screens.TitleScreen;
//#endif
//#if MC < 260000
import net.minecraft.client.gui.screen.multiplayer.*;
//#else
//$$ import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
//#endif
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

public class DeathMenuChain extends TaskChain {

    // Sometimes we fuck up, so we might want to retry considering the death screen.
    private final TimerReal deathRetryTimer = new TimerReal(8);
    private final TimerGame reconnectTimer = new TimerGame(1);
    private int respawnAttempts;
    private final TimerGame waitOnDeathScreenBeforeRespawnTimer = new TimerGame(2);
    private ServerInfo prevServerEntry = null;
    private boolean reconnecting = false;
    private int deathCount = 0;
    private Class<? extends Screen> prevScreen = null;
    private final DeathCommandQueue deathCommands = new DeathCommandQueue();
    private Object deathCommandWorld; // the world the pending death commands belong to


    public DeathMenuChain(TaskRunner runner) {
        super(runner);
    }

    private boolean shouldAutoRespawn() {
        return AltoClef.getInstance().getModSettings().isAutoRespawn();
    }

    private boolean shouldAutoReconnect() {
        return AltoClef.getInstance().getModSettings().isAutoReconnect();
    }

    @Override
    protected void onStop() {

    }

    @Override
    public void onInterrupt(TaskChain other) {

    }

    @Override
    protected void onTick() {

    }

    @Override
    public float getPriority() {
        //MinecraftClient.getInstance().getCurrentServerEntry().address;
//        MinecraftClient.getInstance().
        Screen screen = ScreenVer.current(MinecraftClient.getInstance());

        // This might fix Weird fail to respawn that happened only once
        if (prevScreen == DeathScreen.class) {
            if (deathRetryTimer.elapsed()) {
                Debug.logMessage("(RESPAWN RETRY WEIRD FIX...)");
                deathRetryTimer.reset();
                prevScreen = null;
            }
        } else {
            deathRetryTimer.reset();
        }
        // Keep track of the last server we were on so we can re-connect.
        if (AltoClef.inGame()) {
            prevServerEntry = MinecraftClient.getInstance().getCurrentServerEntry();
        }

        if (!(screen instanceof DeathScreen) && AltoClef.inGame()
                && MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.isAlive()) {
            respawnAttempts = 0;
        }
        if (screen instanceof DeathScreen) {
            AltoClef mod = AltoClef.getInstance();

            if (waitOnDeathScreenBeforeRespawnTimer.elapsed()) {
                waitOnDeathScreenBeforeRespawnTimer.reset();
                if (shouldAutoRespawn()) {
                    deathCount++;
                    Debug.logMessage("RESPAWNING... (this is death #" + deathCount + ")");
                    assert MinecraftClient.getInstance().player != null;
                    Text screenMessage = ((DeathScreenAccessor) screen).getMessage();
                    String deathMessage = screenMessage != null ? screenMessage.getString() : "Unknown"; //"(not implemented yet)"; //screen.children().toString();
                    // A world saved with the player at 0 HP loads "dead" without the server ever running death,
                    // so the respawn request is ignored; if the screen is still up after an attempt, really kill them.
                    if (respawnAttempts++ >= 1) {
                        PlayerVer.sendChatCommand(MinecraftClient.getInstance().player, "kill @s");
                    }
                    MinecraftClient.getInstance().player.requestRespawn();
                    MinecraftClient.getInstance().setScreen(null);
                    // Was `while (player.isAlive());` per command, on the render thread: a player reading alive here
                    // (healed or respawned before this tick) hung the client forever. Queued; run below, never blocking.
                    java.util.List<String> commands = new java.util.ArrayList<>();
                    for (String i : mod.getModSettings().getDeathCommand().split(" & ")) {
                        String command = i.replace("{deathmessage}", deathMessage);
                        if (!command.isEmpty()) commands.add(command);
                    }
                    deathCommands.schedule(commands);
                    deathCommandWorld = MinecraftClient.getInstance().world;
                } else {
                    // Cancel if we die and are not auto-respawning.
                    mod.cancelUserTask();
                }
            }
        } else {
            if (AltoClef.inGame()) {
                waitOnDeathScreenBeforeRespawnTimer.reset();
            }
            if (screen instanceof DisconnectedScreen) {
                if (shouldAutoReconnect()) {
                    Debug.logMessage("RECONNECTING: Going to Multiplayer Screen");
                    reconnecting = true;
                    MinecraftClient.getInstance().setScreen(new MultiplayerScreen(new TitleScreen()));
                } else {
                    // Cancel if we disconnect and are not auto-reconnecting.
                    AltoClef.getInstance().cancelUserTask();
                }
            } else if (screen instanceof MultiplayerScreen && reconnecting && reconnectTimer.elapsed()) {
                reconnectTimer.reset();
                Debug.logMessage("RECONNECTING: Going ");
                reconnecting = false;

                if (prevServerEntry == null) {
                    Debug.logWarning("Failed to re-connect to server, no server entry cached.");
                } else {
                    MinecraftClient client = MinecraftClient.getInstance();
                    ConnectScreenVer.connect(screen, client, ServerAddress.parse(prevServerEntry.address), prevServerEntry, false);
                    //ConnectScreen.connect(screen, client, ServerAddress.parse(_prevServerEntry.address), _prevServerEntry);
                    //client.setScreen(new ConnectScreen(screen, client, _prevServerEntry));
                }
            }
        }
        if (deathCommands.hasPending()) {
            String outcome = deathCommands.tick(deathCommandState(), this::runDeathCommand);
            if (outcome.equals("discarded")) Debug.logMessage("Death commands discarded: left the world before they ran.");
            else if (outcome.equals("ran-after-hold")) Debug.logMessage("Death commands ran after respawn.");
        }
        if (screen != null)
            prevScreen = screen.getClass();
        return Float.NEGATIVE_INFINITY;
    }

    private DeathCommandQueue.PlayerState deathCommandState() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!AltoClef.inGame() || client.player == null || client.world != deathCommandWorld) return DeathCommandQueue.PlayerState.NO_PLAYER;
        return client.player.isAlive() ? DeathCommandQueue.PlayerState.ALIVE : DeathCommandQueue.PlayerState.DEAD;
    }

    private void runDeathCommand(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (command.startsWith(AltoClef.getInstance().getModSettings().getCommandPrefix())) {
            AltoClef.getCommandExecutor().execute(command, () -> {
            }, Throwable::printStackTrace);
        } else if (command.startsWith("/")) {
            PlayerVer.sendChatCommand(client.player, command.substring(1));
        } else {
            PlayerVer.sendChatMessage(client.player, command);
        }
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public String getName() {
        return "Death Menu Respawn Handling";
    }
}
