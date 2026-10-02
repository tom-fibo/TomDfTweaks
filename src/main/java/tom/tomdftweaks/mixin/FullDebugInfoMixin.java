package tom.tomdftweaks.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class FullDebugInfoMixin {

    @Inject(method = "showOnlyReducedInfo", at = @At("HEAD"), cancellable = true)
    private void alwaysFullDebugInfo(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
