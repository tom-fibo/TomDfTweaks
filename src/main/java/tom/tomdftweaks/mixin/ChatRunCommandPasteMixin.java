package tom.tomdftweaks.mixin;

import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.InputConstants;

@Mixin(ChatScreen.class)
public abstract class ChatRunCommandPasteMixin {

    // DiamondFire sends plot commands as a custom click event rather than vanilla
    // run_command.
    private static final Identifier PLOT_COMMAND_ID = Identifier.fromNamespaceAndPath("hypercube", "plot_command");

    @Shadow
    protected EditBox input;

    @Shadow
    public abstract void insertText(String text, boolean overwrite);

    @Shadow
    public abstract void handleChatInput(String message, boolean addToHistory);

    @Shadow
    public abstract String normalizeChatMessage(String message);

    // Shift+click on a command chat link pastes the command instead of running it.
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void onHandleClickEvent(Style style, boolean insert, CallbackInfoReturnable<Boolean> cir) {
        if (!insert)
            return;

        String command = getCommand(style.getClickEvent());
        if (command != null) {
            this.insertText(command, true);
            cir.setReturnValue(true);
        }
    }

    // Shift+Enter sends the chat text as a command; Ctrl+Enter sends it as a
    // hypercube:plot_command,
    // bypassing the normal chat message/command validation that kicks on illegal
    // characters.
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (input.hasControlDown() && input.key() == InputConstants.KEY_7) {
            insertRaw("§");
            cir.setReturnValue(true);
            return;
        }
        if (input.hasControlDown() && input.key() == InputConstants.KEY_N) {
            insertRaw("\n");
            cir.setReturnValue(true);
            return;
        }
        if (input.isConfirmation() && (input.hasShiftDown() != input.hasControlDown()) && input.hasAltDown()) {
            String text = this.normalizeChatMessage(this.input.getValue());
            if (text.isEmpty())
                return;

            if (input.hasShiftDown()) {
                this.handleChatInput(text.startsWith("/") ? text : "/" + text, true);
            } else {
                sendPlotCommand(text);
            }

            // Clear the field so the sent text isn't kept as a draft when chat is reopened.
            this.input.setValue("");
            Minecraft.getInstance().gui.setScreen(null);
            cir.setReturnValue(true);
        }
    }

    // EditBox.insertText() strips "invalid" chars (including § and \n) via
    // StringUtil.filterText;
    // setValue() has no such filtering, so splice the text ourselves and go through
    // that instead.
    private void insertRaw(String toInsert) {
        int cursor = this.input.getCursorPosition();
        String current = this.input.getValue();
        this.input.setValue(current.substring(0, cursor) + toInsert + current.substring(cursor));
        this.input.moveCursorTo(cursor + toInsert.length(), false);
    }

    private static void sendPlotCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null)
            return;

        CompoundTag payload = new CompoundTag();
        payload.putString("command", command);
        client.player.connection.send(new ServerboundCustomClickActionPacket(PLOT_COMMAND_ID, Optional.of(payload)));
        client.gui.hud.getChat().addRecentChat(command);
    }

    private static String getCommand(ClickEvent clickEvent) {
        if (clickEvent instanceof ClickEvent.RunCommand runCommand) {
            return runCommand.command();
        }
        if (clickEvent instanceof ClickEvent.Custom custom && custom.id().equals(PLOT_COMMAND_ID)) {
            return custom.payload()
                    .filter(CompoundTag.class::isInstance)
                    .map(CompoundTag.class::cast)
                    .flatMap(nbt -> nbt.getString("command"))
                    .orElse(null);
        }
        return null;
    }
}
