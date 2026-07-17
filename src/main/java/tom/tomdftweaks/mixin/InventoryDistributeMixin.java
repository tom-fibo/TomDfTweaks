package tom.tomdftweaks.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tom.tomdftweaks.TomDfTweaksClient;

@Mixin(HandledScreen.class)
public class InventoryDistributeMixin {

    @Shadow
    protected Slot focusedSlot;
    @Shadow
    protected ScreenHandler handler;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyInput keyInput, CallbackInfoReturnable<Boolean> cir) {
        if (!TomDfTweaksClient.inDevMode)
            return;
        if (keyInput.key() != GLFW.GLFW_KEY_R)
            return;
        if (this.focusedSlot == null || !this.focusedSlot.hasStack())
            return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null)
            return;

        if (!this.handler.getCursorStack().isEmpty())
            return;

        int syncId = this.handler.syncId;

        if (this.focusedSlot.inventory instanceof PlayerInventory) {
            // If clicking inventory slot, move 1 to container.

            // Find first empty container slot (not in player inventory)
            Slot targetSlot = null;
            for (Slot slot : this.handler.slots) {
                if (slot.inventory instanceof PlayerInventory)
                    continue;
                if (!slot.hasStack() && slot.canInsert(this.focusedSlot.getStack())) {
                    targetSlot = slot;
                    break;
                }
            }
            if (targetSlot == null)
                return;

            // Pick up source stack
            client.interactionManager.clickSlot(syncId, this.focusedSlot.id, 0, SlotActionType.PICKUP, client.player);
            // Place 1 item into target (right-click places 1)
            client.interactionManager.clickSlot(syncId, targetSlot.id, 1, SlotActionType.PICKUP, client.player);
            // Put remaining back in source
            client.interactionManager.clickSlot(syncId, this.focusedSlot.id, 0, SlotActionType.PICKUP, client.player);
        } else {
            // If clicking container slot, copy to inventory.

            // Find first empty inventory slot
            int targetSlot = -1;
            for (int checkSlot = 0; checkSlot < 36; checkSlot++) {
                ItemStack currentStack = client.player.getInventory().getStack(checkSlot);
                if (currentStack.getItem() == Items.AIR && targetSlot == -1) {
                    targetSlot = checkSlot;
                }
                if (ItemStack.areItemsAndComponentsEqual(currentStack, this.focusedSlot.getStack())
                        && currentStack.getCount() < currentStack.getMaxCount()) {
                    targetSlot = checkSlot;
                    break;
                }
            }
            if (targetSlot < 0)
                return;

            // Copy container stack
            ItemStack duplicateStack = this.focusedSlot.getStack()
                    .copyWithCount(client.player.getInventory().getStack(targetSlot).getCount() + 1);
            client.player.networkHandler
                    .sendPacket(new CreativeInventoryActionC2SPacket(targetSlot + (targetSlot < 9 ? 36 : 0),
                            duplicateStack));
            client.player.getInventory().setStack(targetSlot, duplicateStack);
        }

        cir.setReturnValue(true);
    }
}
