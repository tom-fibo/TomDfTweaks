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
import net.minecraft.world.GameMode;

public class TomDfTweaksClient implements ClientModInitializer {

	public static MinecraftClient client = MinecraftClient.getInstance();

	public static String currentPlot = "";
	public static boolean inDevMode = false;

	private int commandSendCountdown = 0;
	public static ArrayList<String> commandsToSend = new ArrayList<String>(0);
	public static ArrayList<Integer> delaysToSend = new ArrayList<Integer>(0);

	private boolean spectatorWasPressed = false;
	private GameMode previousGameMode = null;

	public static Map<String, Boolean> noteBlockSounds = new HashMap<String, Boolean>();
	public static Map<String, Boolean> fullbright = new HashMap<String, Boolean>();
	public static boolean currentFullbright = false;

	public static long lastDeathTime = 0;
	public static boolean shouldRestoreSprint = false;
	public static boolean awaitingRespawn = false;

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (TomDfTweaksConfig.load()) {
				noteBlockSounds = TomDfTweaksConfig.settings.get("noteBlockSounds");
				if (noteBlockSounds == null) {
					noteBlockSounds = new HashMap<String, Boolean>();
				}
				fullbright = TomDfTweaksConfig.settings.get("fullbright");
				if (fullbright == null) {
					fullbright = new HashMap<String, Boolean>();
				}
				client.player.sendMessage(net.minecraft.text.Text.literal("§7[§2TomDfTweaks§7] §aSettings loaded."),
						false);

				client.options.getSoundVolumeOption(SoundCategory.RECORDS)
						.setValue(1.0);
				client.options.write();
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			TomDfTweaksConfig.settings.put("noteBlockSounds", noteBlockSounds);
			TomDfTweaksConfig.settings.put("fullbright", fullbright);
			TomDfTweaksConfig.save();

			lastDeathTime = 0;
			shouldRestoreSprint = false;
			awaitingRespawn = false;
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
					delaysToSend.add(80);
				} else if (messageStr.contains("You are now in dev mode.")) {
					inDevMode = true;
					client.inGameHud.getChatHud()
							.addMessage(Text.literal("§7[§2TomDfTweaks§7] §aNow in dev mode."));
				} else if (joinPlot.matches()) {
					currentPlot = joinPlot.group(1) + "|" + joinPlot.group(2);
					inDevMode = false;
					client.inGameHud.getChatHud()
							.addMessage(Text.literal("§7[§2TomDfTweaks§7] §aJoined plot \"§r" + currentPlot + "§a\"."));

					boolean enableNoteBlockSounds = (!noteBlockSounds.containsKey(currentPlot)
							|| noteBlockSounds.get(currentPlot));
					//TomDfTweaks.LOGGER.info("enablenoteblocksounds: " + enableNoteBlockSounds);
					client.options.getSoundVolumeOption(SoundCategory.RECORDS)
							.setValue(enableNoteBlockSounds ? 1.0 : 0.0);
					client.options.write();
					client.inGameHud.getChatHud().addMessage(Text.literal("§7[§2TomDfTweaks§7] §6Note blocks "
							+ (enableNoteBlockSounds ? "§eenabled" : "§csilenced") + "§6."));

					currentFullbright = (fullbright.containsKey(currentPlot)
							&& fullbright.get(currentPlot));
					client.inGameHud.getChatHud().addMessage(Text.literal("§7[§2TomDfTweaks§7] §6Fullbright "
							+ (currentFullbright ? "§eenabled" : "§cdisabled") + "§6."));
				}
			}
		});

		KeyBinding toggleNoteBlockKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tomdftweaks.noteblocks", // 1. Translation Key (Name)
				InputUtil.Type.KEYSYM, // 2. Input Type
				GLFW.GLFW_KEY_M, // 3. The Key Code (int)
				Category.MISC // 4. Translation Key (Category)
		));

		KeyBinding toggleFullbrightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tomdftweaks.fullbright", // 1. Translation Key (Name)
				InputUtil.Type.KEYSYM, // 2. Input Type
				GLFW.GLFW_KEY_N, // 3. The Key Code (int)
				Category.MISC // 4. Translation Key (Category)
		));

		KeyBinding spectatorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tomdftweaks.spectator",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_V,
				Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register((a) -> {
			// Auto-respawn: clear flag once player is alive again
			if (awaitingRespawn && client.player != null && client.player.isAlive()) {
				awaitingRespawn = false;
			}

			// Restore sprint toggle after respawning
			if (shouldRestoreSprint && client.player != null && client.player.isAlive()) {
				client.options.sprintKey.setPressed(true);
				shouldRestoreSprint = false;
			}

			//Send commands, if queued
			if (commandsToSend.size() > 0) {
				commandSendCountdown++;
				if (commandSendCountdown >= delaysToSend.get(0)) {
					commandSendCountdown = 0;
					client.getNetworkHandler().sendChatCommand(commandsToSend.remove(0));
					delaysToSend.remove(0);
				}
			}

			//Toggle note blocks
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

			//Toggle fullbright
			while (toggleFullbrightKey.wasPressed()) {
				if (client.player != null) {
					currentFullbright = !currentFullbright;

					fullbright.put(currentPlot, currentFullbright);
					if (client.worldRenderer != null) {
						// This forces the game to throw away the old "dark" chunk meshes
						// and rebuild them using the new Fullbright 'pack' logic.
						client.worldRenderer.reload();
					}

					String status = (currentFullbright) ? "§eenabled" : "§cdisabled";
					client.player.sendMessage(
							Text.literal(
									"§7[§2TomDfTweaks§7] §6Fullbright for §r" + currentPlot + " " + status + "§6."),
							true);
				}
			}

			//Spectator mode
			if (!inDevMode) {
				boolean spectatorIsPressed = spectatorKey.isPressed();

				if (spectatorIsPressed && !spectatorWasPressed) {
					GameMode current = client.interactionManager.getCurrentGameMode();
					if (current != GameMode.SPECTATOR) {
						previousGameMode = current;
						client.player.networkHandler.sendChatCommand("gamemode spectator");
					}
					spectatorWasPressed = true;
				} else if (!spectatorIsPressed && spectatorWasPressed) {
					if (previousGameMode != null) {
						client.player.networkHandler
								.sendChatCommand("gamemode " + previousGameMode.asString());
						previousGameMode = null;
					}
					spectatorWasPressed = false;
				}
			}
		});
	}
}
