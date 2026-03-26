package tom.tomdftweaks.mixin;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import tom.tomdftweaks.TomDfTweaksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class WorldEditAxeMixin {

    @Shadow
    public ClientPlayerEntity player;
    @Shadow
    public HitResult crosshairTarget;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void onLeftClick(CallbackInfoReturnable<Boolean> cir) {
        if (shouldClickOrigin()) {
            clickOrigin(true);
            cir.setReturnValue(false); // Cancel normal swing
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void onRightClick(CallbackInfo ci) {
        if (shouldClickOrigin()) {
            clickOrigin(false);
        }
    }

    private boolean shouldClickOrigin() {
        return TomDfTweaksClient.inDevMode
                && player != null
                && player.isCreative()
                && player.getMainHandStack().isOf(Items.WOODEN_AXE)
                && (crosshairTarget == null || crosshairTarget.getType() == HitResult.Type.MISS);
    }

    private void clickOrigin(boolean isLeftClick) {
        BlockPos pos = player.getBlockPos();
        MinecraftClient client = MinecraftClient.getInstance();

        if (isLeftClick) {
            // Simulate breaking the block at player's feet
            client.interactionManager.attackBlock(pos, net.minecraft.util.math.Direction.UP);
        } else {
            // Simulate "placing" the item (interact with block)
            // Note: Since we are in the air/non-block, we manually trigger the placement logic
            client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(player.getEntityPos(),
                            net.minecraft.util.math.Direction.UP,
                            pos, false));
        }

        // Spawn Particle: Structure Void (Block Marker)
        spawnPar(pos);
    }

    private void spawnPar(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        player.getEntityWorld().addParticleClient(
                new BlockStateParticleEffect(ParticleTypes.BLOCK_MARKER, Blocks.TARGET.getDefaultState()),
                center.x, center.y, center.z,
                0, 0, 0);
        player.playSound(
                net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_SNARE.value(),
                1.0f,
                1.414214f);
    }
}