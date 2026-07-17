package tom.tomdftweaks.mixin;

import java.util.Optional;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.common.CustomClickActionC2SPacket;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatRunCommandPasteMixin {

    // DiamondFire sends plot commands as a custom click event rather than vanilla run_command.
    private static final Identifier PLOT_COMMAND_ID = Identifier.of("hypercube", "plot_command");

    @Shadow
    protected TextFieldWidget chatField;

    @Shadow
    public abstract void insertText(String text, boolean overwrite);

    @Shadow
    public abstract void sendMessage(String message, boolean addToHistory);

    @Shadow
    public abstract String normalize(String message);

    // Shift+click on a command chat link pastes the command instead of running it.
    @Inject(method = "handleClickEvent", at = @At("HEAD"), cancellable = true)
    private void onHandleClickEvent(Style style, boolean insert, CallbackInfoReturnable<Boolean> cir) {
        if (!insert)
            return;

        String command = getCommand(style.getClickEvent());
        if (command != null) {
            this.insertText(command, true);
            cir.setReturnValue(true);
        }
    }

    // Shift+Enter sends the chat text as a command; Ctrl+Enter sends it as a hypercube:plot_command,
    // bypassing the normal chat message/command validation that kicks on illegal characters.
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (input.hasCtrl() && input.key() == GLFW.GLFW_KEY_7) {
            insertRaw("§");
            cir.setReturnValue(true);
            return;
        }
        if (input.hasCtrl() && input.key() == GLFW.GLFW_KEY_N) {
            insertRaw("\n");
            cir.setReturnValue(true);
            return;
        }
        if (input.isEnter() && input.hasShift() != input.hasCtrl()) {
            String text = this.normalize(this.chatField.getText());
            if (text.isEmpty())
                return;

            if (input.hasShift()) {
                this.sendMessage(text.startsWith("/") ? text : "/" + text, true);
            } else {
                sendPlotCommand(text);
            }

            // Clear the field so the sent text isn't kept as a draft when chat is reopened.
            this.chatField.setText("");
            MinecraftClient.getInstance().setScreen(null);
            cir.setReturnValue(true);
        }
    }

    // TextFieldWidget.write() strips "invalid" chars (including § and \n) via StringHelper.stripInvalidChars;
    // setText() has no such filtering, so splice the text ourselves and go through that instead.
    private void insertRaw(String toInsert) {
        int cursor = this.chatField.getCursor();
        String current = this.chatField.getText();
        this.chatField.setText(current.substring(0, cursor) + toInsert + current.substring(cursor));
        this.chatField.setCursor(cursor + toInsert.length(), false);
    }

    private static void sendPlotCommand(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null)
            return;

        NbtCompound payload = new NbtCompound();
        payload.putString("command", command);
        client.player.networkHandler.sendPacket(new CustomClickActionC2SPacket(PLOT_COMMAND_ID, Optional.of(payload)));
        client.inGameHud.getChatHud().addToMessageHistory(command);
    }

    private static String getCommand(ClickEvent clickEvent) {
        if (clickEvent instanceof ClickEvent.RunCommand runCommand) {
            return runCommand.command();
        }
        if (clickEvent instanceof ClickEvent.Custom custom && custom.id().equals(PLOT_COMMAND_ID)) {
            return custom.payload()
                    .filter(NbtCompound.class::isInstance)
                    .map(NbtCompound.class::cast)
                    .flatMap(nbt -> nbt.getString("command"))
                    .orElse(null);
        }
        return null;
    }
}
