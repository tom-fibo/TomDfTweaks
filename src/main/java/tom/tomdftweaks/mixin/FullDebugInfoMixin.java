package tom.tomdftweaks.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class FullDebugInfoMixin {

    @Inject(method = "hasReducedDebugInfo", at = @At("HEAD"), cancellable = true)
    private void alwaysFullDebugInfo(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
