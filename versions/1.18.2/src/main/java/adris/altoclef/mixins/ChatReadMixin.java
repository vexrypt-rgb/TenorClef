package adris.altoclef.mixins;

import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.ChatMessageEvent;
import adris.altoclef.eventbus.events.GameMessageEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudListener;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.MessageType;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;


@Mixin(ChatHudListener.class)
public final class ChatReadMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(
            method = "onChatMessage",
            at = @At("HEAD")
    )
    private void onChatMessage(MessageType messageType, Text message, UUID senderUuid, CallbackInfo ci) {
        // The server's own lines: typed as such, or chat that no player sent (how proxies and plugins write).
        if (messageType == MessageType.SYSTEM || (messageType == MessageType.CHAT && senderUuid != null
                && senderUuid.getMostSignificantBits() == 0L && senderUuid.getLeastSignificantBits() == 0L)) {
            EventBus.publish(new GameMessageEvent(message.getString()));
        }
        String senderName = resolveSender(senderUuid);
        String msg;

        if (message instanceof TranslatableText translatable) {
            msg = "";

            for (Object obj : translatable.getArgs()) {
                if (!(obj instanceof Text text) || obj instanceof TranslatableText) continue;
                String str = text.getString();
                if (!senderName.isEmpty() && str.equals(senderName)) continue;
                msg += str;
            }
        } else {
            msg = message.getString();
        }

        ChatMessageEvent evt = new ChatMessageEvent(msg, senderName, messageType);
        EventBus.publish(evt);
    }

    /** Tab list, not ClientWorld — world entries vanish out of tracking range. */
    private String resolveSender(UUID senderUuid) {
        if (senderUuid != null && client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(senderUuid);
            if (entry != null && entry.getProfile() != null && entry.getProfile().getName() != null) {
                return entry.getProfile().getName();
            }
        }
        PlayerEntity self = MinecraftClient.getInstance().player;
        if (self != null && (senderUuid == null || senderUuid.equals(self.getUuid()))) {
            return self.getName().asString();
        }
        return "";
    }
}
