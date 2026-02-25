package tom.tomdftweaks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;

public class TomDfTweaksClient implements ClientModInitializer {

	public MinecraftClient client = MinecraftClient.getInstance();

	public String currentPlot = "";

	public int commandSendCountdown = 0;
	public ArrayList<String> commandsToSend = new ArrayList<String>(0);
	public ArrayList<Integer> delaysToSend = new ArrayList<Integer>(0);

	public Map<String, Boolean> noteBlockSounds = new HashMap<String, Boolean>();

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			TomDfTweaksConfig.load();
			noteBlockSounds = TomDfTweaksConfig.settings.get("noteBlockSounds");
			if (noteBlockSounds == null) {
				noteBlockSounds = new HashMap<String, Boolean>();
			}
			client.player.sendMessage(net.minecraft.text.Text.literal("§7[§2TomDfTweaks§7] §aSettings loaded."), false);

			client.options.getSoundVolumeOption(SoundCategory.RECORDS)
					.setValue(1.0);
			client.options.write();
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			TomDfTweaksConfig.settings.put("noteBlockSounds", noteBlockSounds);
			TomDfTweaksConfig.save();
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			// 'overlay' is true if the message appears above the hotbar instead of in chat
			if (!overlay) {
				String messageStr = message.getString();
				//TomDfTweaks.LOGGER.info("Received game message: " + messageStr);
				Matcher joinPlot = Pattern.compile("» Joined game: (.+) by (.+)\\.").matcher(messageStr);
				if (messageStr.contains("Welcome back to DiamondFire!")) {
					currentPlot = "";
					commandsToSend.add("support queue");
					delaysToSend.add(20);
				} else if (messageStr.contains("Use /tip to show your appreciation and receive a")) {
					commandsToSend.add("tip");
					delaysToSend.add(100);
				} else if (joinPlot.matches()) {
					currentPlot = joinPlot.group(1) + "|" + joinPlot.group(2);
					client.inGameHud.getChatHud()
							.addMessage(Text.literal("§7[§2TomDfTweaks§7] §aJoined plot \"§r" + currentPlot + "§a\"."));

					boolean enableNoteBlockSounds = (!noteBlockSounds.containsKey(currentPlot)
							|| noteBlockSounds.get(currentPlot));
					TomDfTweaks.LOGGER.info("enablenoteblocksounds: " + enableNoteBlockSounds);
					client.options.getSoundVolumeOption(SoundCategory.RECORDS)
							.setValue(enableNoteBlockSounds ? 1.0 : 0.0);
					client.options.write();
					client.inGameHud.getChatHud().addMessage(Text.literal("§7[§2TomDfTweaks§7] §6Note blocks "
							+ (enableNoteBlockSounds ? "§eenabled" : "§csilenced") + "§6."));
				}
			}
		});

		KeyBinding toggleNoteBlockKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.noteblocktoggle.toggle", // 1. Translation Key (Name)
				InputUtil.Type.KEYSYM, // 2. Input Type
				GLFW.GLFW_KEY_M, // 3. The Key Code (int)
				Category.MISC // 4. Translation Key (Category)
		));

		//send a message: `client.inGameHud.getChatHud().addMessage(Text.literal("[message]"));`
		ClientTickEvents.END_CLIENT_TICK.register((a) -> {
			if (commandsToSend.size() > 0) {
				commandSendCountdown++;
				if (commandSendCountdown >= delaysToSend.get(0)) {
					commandSendCountdown = 0;
					client.getNetworkHandler().sendChatCommand(commandsToSend.remove(0));
					delaysToSend.remove(0);
				}
			}
			while (toggleNoteBlockKey.wasPressed()) {
				if (client.player != null) {
					float currentVolume = client.options.getSoundVolume(SoundCategory.RECORDS);
					double newVolume = (currentVolume > 0.1f) ? 0.0 : 1.0;

					noteBlockSounds.put(currentPlot, newVolume > 0.1f);

					client.options.getSoundVolumeOption(SoundCategory.RECORDS).setValue(newVolume);
					client.options.write();

					String status = (newVolume > 0.1) ? "§eenabled" : "§csilenced";
					client.player.sendMessage(
							Text.literal(
									"§7[§2TomDfTweaks§7] §6Note blocks for §r" + currentPlot + " " + status + "§6."),
							true);
				}
			}
		});
	}
}
