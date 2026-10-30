package tom.tomdftweaks.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.InputConstants;

import tom.tomdftweaks.TomDfTweaksClient;

@Mixin(AbstractContainerScreen.class)
public class InventoryDistributeMixin {

    @Shadow
    protected Slot hoveredSlot;
    @Shadow
    protected AbstractContainerMenu menu;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent keyInput, CallbackInfoReturnable<Boolean> cir) {
        if (!TomDfTweaksClient.inDevMode)
            return;
        if (keyInput.key() != InputConstants.KEY_R)
            return;
        if (this.hoveredSlot == null || !this.hoveredSlot.hasItem())
            return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null)
            return;

        if (!this.menu.getCarried().isEmpty())
            return;

        int syncId = this.menu.containerId;

        if (this.hoveredSlot.container instanceof Inventory) {
            // If clicking inventory slot, move 1 to container.

            // Find first empty container slot (not in player inventory)
            Slot targetSlot = null;
            for (Slot slot : this.menu.slots) {
                if (slot.container instanceof Inventory)
                    continue;
                if (!slot.hasItem() && slot.mayPlace(this.hoveredSlot.getItem())) {
                    targetSlot = slot;
                    break;
                }
            }
            if (targetSlot == null)
                return;

            // Pick up source stack
            client.gameMode.handleContainerInput(syncId, this.hoveredSlot.index, 0, ContainerInput.PICKUP,
                    client.player);
            // Place 1 item into target (right-click places 1)
            client.gameMode.handleContainerInput(syncId, targetSlot.index, 1, ContainerInput.PICKUP, client.player);
            // Put remaining back in source
            client.gameMode.handleContainerInput(syncId, this.hoveredSlot.index, 0, ContainerInput.PICKUP,
                    client.player);
        } else {
            // If clicking container slot, copy to inventory.

            // Find first empty inventory slot
            int targetSlot = -1;
            for (int checkSlot = 0; checkSlot < 36; checkSlot++) {
                ItemStack currentStack = client.player.getInventory().getItem(checkSlot);
                if (currentStack.getItem() == Items.AIR && targetSlot == -1) {
                    targetSlot = checkSlot;
                }
                if (ItemStack.isSameItemSameComponents(currentStack, this.hoveredSlot.getItem())
                        && currentStack.getCount() < currentStack.getMaxStackSize()) {
                    targetSlot = checkSlot;
                    break;
                }
            }
            if (targetSlot < 0)
                return;

            // Copy container stack
            ItemStack duplicateStack = this.hoveredSlot.getItem()
                    .copyWithCount(client.player.getInventory().getItem(targetSlot).getCount() + 1);
            client.player.connection
                    .send(new ServerboundSetCreativeModeSlotPacket(targetSlot + (targetSlot < 9 ? 36 : 0),
                            duplicateStack));
            client.player.getInventory().setItem(targetSlot, duplicateStack);
        }

        cir.setReturnValue(true);
    }
}
