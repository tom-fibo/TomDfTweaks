package tom.tomdftweaks.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tom.tomdftweaks.TomDfTweaksClient;

@Mixin(MinecraftClient.class)
public class AutoRespawnMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (!(screen instanceof DeathScreen)) return;

        MinecraftClient client = (MinecraftClient) (Object) this;

        // Save sprint toggle state before setScreen's unpressAll() clears it
        if (client.options.sprintKey.isPressed()) {
            TomDfTweaksClient.shouldRestoreSprint = true;
        }

        // Suppress duplicate death screens while waiting for respawn packet
        if (TomDfTweaksClient.awaitingRespawn) {
            ci.cancel();
            return;
        }

        long now = System.currentTimeMillis();
        boolean rapidDeath = (now - TomDfTweaksClient.lastDeathTime) < 5000;
        TomDfTweaksClient.lastDeathTime = now;

        if (!rapidDeath) {
            TomDfTweaksClient.awaitingRespawn = true;
            if (client.player != null) {
                client.player.requestRespawn();
            }
            ci.cancel();
        }
    }
}
