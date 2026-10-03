package adris.altoclef.mixins;

import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.SlotClickChangedEvent;
import com.google.common.collect.ImmutableList;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Mixin(ScreenHandler.class)
public abstract class SlotClickMixin {

//#if MC >= 260000
//$$    @Unique
//$$    private List<net.minecraft.world.item.ItemStack> altoclefBeforeStacks;
//$$
//$$    @Inject(method = "clicked", at = @At("HEAD"))
//$$    private void slotClickBefore(int slotIndex, int button, net.minecraft.world.inventory.ContainerInput actionType, net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
//$$        net.minecraft.world.inventory.AbstractContainerMenu self = (net.minecraft.world.inventory.AbstractContainerMenu) (Object) this;
//$$        altoclefBeforeStacks = new ArrayList<>(self.slots.size());
//$$        for (net.minecraft.world.inventory.Slot slot : self.slots) {
//$$            altoclefBeforeStacks.add(slot.getItem().copy());
//$$        }
//$$    }
//$$
//$$    @Inject(method = "clicked", at = @At("TAIL"))
//$$    private void slotClickAfter(int slotIndex, int button, net.minecraft.world.inventory.ContainerInput actionType, net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
//$$        net.minecraft.world.inventory.AbstractContainerMenu self = (net.minecraft.world.inventory.AbstractContainerMenu) (Object) this;
//$$        List<net.minecraft.world.item.ItemStack> beforeStacks = altoclefBeforeStacks;
//$$        if (beforeStacks == null) return;
//$$        altoclefBeforeStacks = null;
//$$        for (int i = 0; i < beforeStacks.size() && i < self.slots.size(); ++i) {
//$$            net.minecraft.world.item.ItemStack before = beforeStacks.get(i);
//$$            net.minecraft.world.item.ItemStack after = self.slots.get(i).getItem();
//$$            if (!net.minecraft.world.item.ItemStack.matches(before, after)) {
//$$                adris.altoclef.util.slots.Slot slot = adris.altoclef.util.slots.Slot.getFromCurrentScreen(i);
//$$                EventBus.publish(new SlotClickChangedEvent(slot, before, after));
//$$            }
//$$        }
//$$    }
//#elseif MC >= 11701
    @Redirect(
            method = "internalOnSlotClick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/ScreenHandler;internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V")
    )
    private void slotClick(ScreenHandler self, int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        List<Slot> afterSlots = self.slots;
        List<ItemStack> beforeStacks = new ArrayList<>(afterSlots.size());
        for (Slot slot : afterSlots) {
            beforeStacks.add(slot.getStack().copy());
        }
        self.onSlotClick(slotIndex, button, actionType, player);
        for (int i = 0; i < beforeStacks.size(); ++i) {
            ItemStack before = beforeStacks.get(i);
            ItemStack after = afterSlots.get(i).getStack();
            if (!ItemStack.areEqual(before, after)) {
                adris.altoclef.util.slots.Slot slot = adris.altoclef.util.slots.Slot.getFromCurrentScreen(i);
                EventBus.publish(new SlotClickChangedEvent(slot, before, after));
            }
        }
    }
//#endif

}