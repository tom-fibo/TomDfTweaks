package tom.tomdftweaks.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import tom.tomdftweaks.TomDfTweaksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class WorldEditAxeMixin {

    @Shadow
    public LocalPlayer player;
    @Shadow
    public HitResult hitResult;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onLeftClick(CallbackInfoReturnable<Boolean> cir) {
        if (shouldClickOrigin()) {
            clickOrigin(true);
            cir.setReturnValue(false); // Cancel normal swing
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void onRightClick(CallbackInfo ci) {
        if (shouldClickOrigin()) {
            clickOrigin(false);
        }
    }

    private boolean shouldClickOrigin() {
        return TomDfTweaksClient.inDevMode
                && player != null
                && player.isCreative()
                && player.getMainHandItem().getItem() == Items.WOODEN_AXE
                && (hitResult == null || hitResult.getType() == HitResult.Type.MISS);
    }

    private void clickOrigin(boolean isLeftClick) {
        BlockPos pos = player.blockPosition();
        Minecraft client = Minecraft.getInstance();

        if (isLeftClick) {
            // Simulate breaking the block at player's feet
            client.gameMode.startDestroyBlock(pos, net.minecraft.core.Direction.UP);
        } else {
            // Simulate "placing" the item (interact with block)
            // Note: Since we are in the air/non-block, we manually trigger the placement logic
            client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(player.position(),
                            net.minecraft.core.Direction.UP,
                            pos, false));
        }

        // Spawn Particle: Structure Void (Block Marker)
        spawnPar(pos);
    }

    private void spawnPar(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        player.level().addParticle(
                new BlockParticleOption(ParticleTypes.BLOCK_MARKER, Blocks.TARGET.defaultBlockState()),
                center.x, center.y, center.z,
                0, 0, 0);
        player.playSound(
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_SNARE.value(),
                1.0f,
                1.414214f);
    }
}