package tom.tomdftweaks.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import tom.tomdftweaks.TomDfTweaksClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightCoordsUtil.class)
public class FullbrightMixin {
    /**
     * pack() is the method that combines block light and sky light into a UV coordinate.
     * Block 15, Sky 15 = 15728880.
     */
    @Inject(method = "pack", at = @At("HEAD"), cancellable = true)
    private static void onPack(int blockLight, int skyLight, CallbackInfoReturnable<Integer> cir) {
        if (TomDfTweaksClient.currentFullbright) {
            // Force return maximum light coordinates (15 << 4 | 15 << 20 basically)
            cir.setReturnValue(15728880);
        }
    }

    @Inject(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private static void onGetLightCoords(LightCoordsUtil.BrightnessGetter brightnessGetter, BlockAndLightGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (TomDfTweaksClient.currentFullbright) {
            cir.setReturnValue(15728880);
        }
    }
}
