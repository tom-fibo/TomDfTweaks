package tom.tomdftweaks.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tom.tomdftweaks.TomDfTweaksClient;

@Mixin(LocalPlayer.class)
public class RequestRespawnMixin {

    // respawn() always calls KeyMapping.resetToggleKeys() right after
    // sending the respawn packet, which is what actually turns off Toggle Sprint.
    // This runs for every respawn path (death screen button, our own AutoRespawnMixin,
    // and vanilla's own instant-respawn handling when shouldShowDeathScreen() is false),
    // so hooking here catches every case instead of only screen-triggered ones.
    @Inject(method = "respawn", at = @At("HEAD"))
    private void onRequestRespawn(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.keySprint.isDown()) {
            TomDfTweaksClient.shouldRestoreSprint = true;
        }
    }
}
