package tom.tomdftweaks.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tom.tomdftweaks.TomDfTweaksClient;

@Mixin(ClientPlayerEntity.class)
public class RequestRespawnMixin {

    // requestRespawn() always calls KeyBinding.untoggleStickyKeys() right after
    // sending the respawn packet, which is what actually turns off Toggle Sprint.
    // This runs for every respawn path (death screen button, our own AutoRespawnMixin,
    // and vanilla's own instant-respawn handling when showsDeathScreen() is false),
    // so hooking here catches every case instead of only screen-triggered ones.
    @Inject(method = "requestRespawn", at = @At("HEAD"))
    private void onRequestRespawn(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.sprintKey.isPressed()) {
            TomDfTweaksClient.shouldRestoreSprint = true;
        }
    }
}
