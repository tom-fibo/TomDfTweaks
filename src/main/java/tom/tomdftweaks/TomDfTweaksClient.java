package tom.tomdftweaks;

import java.util.ArrayList;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;

public class TomDfTweaksClient implements ClientModInitializer {

	public int commandSendCountdown = 20;
	public ArrayList<String> commandsToSend = new ArrayList<String>(0);

	@Override
	public void onInitializeClient() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			// 'overlay' is true if the message appears above the hotbar instead of in chat
			if (!overlay) {
				String messageStr = message.getString();
				//TomDfTweaks.LOGGER.info("Received game message: " + messageStr);
				if (messageStr.contains("Welcome back to DiamondFire!")) {
					commandsToSend.add("support queue");
				} else if (messageStr.contains("Use /tip to show your appreciation and receive a")) {
					commandsToSend.add("tip");
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register((a) -> {
			if (commandsToSend.size() > 0) {
				commandSendCountdown--;
				if (commandSendCountdown <= 0) {
					commandSendCountdown = 20;
					MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(commandsToSend.remove(0));
				}
			}
		});
	}
}
