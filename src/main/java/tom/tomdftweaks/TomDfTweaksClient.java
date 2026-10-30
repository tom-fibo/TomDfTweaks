package tom.tomdftweaks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;

public class TomDfTweaksClient implements ClientModInitializer {

	public static Minecraft client = Minecraft.getInstance();

	public static String currentPlot = "";
	public static boolean inDevMode = false;

	private int commandSendCountdown = 0;
	public static ArrayList<String> commandsToSend = new ArrayList<String>(0);
	public static ArrayList<Integer> delaysToSend = new ArrayList<Integer>(0);

	private boolean spectatorWasPressed = false;
	private GameType previousGameMode = null;

	public static Map<String, Boolean> noteBlockSounds = new HashMap<String, Boolean>();
	public static Map<String, Boolean> fullbright = new HashMap<String, Boolean>();
	public static boolean currentFullbright = false;

	public static long lastDeathTime = 0;
	public static boolean shouldRestoreSprint = false;
	public static boolean awaitingRespawn = false;

	private static BlockPos devLocation = null;
	private static BlockPos playLocation = null;

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
				client.gui.chatListener().handleSystemMessage(
						Component.literal("§7[§2TomDfTweaks§7] §aSettings loaded."), false);

				client.options.getSoundSourceOptionInstance(SoundSource.RECORDS)
						.set(1.0);
				client.options.save();
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			TomDfTweaksConfig.settings.put("noteBlockSounds", noteBlockSounds);
			TomDfTweaksConfig.settings.put("fullbright", fullbright);
			TomDfTweaksConfig.save();

			lastDeathTime = 0;
			shouldRestoreSprint = false;
			awaitingRespawn = false;
			devLocation = null;
			playLocation = null;
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			// 'overlay' is true if the message appears above the hotbar instead of in chat
			if (!overlay) {
				String messageStr = message.getString();
				// TomDfTweaks.LOGGER.info("Received game message: " + messageStr);
				Matcher joinPlot = Pattern.compile("» Joined game: (.+) by (.+)\\.").matcher(messageStr);
				/*
				 * if (messageStr.contains("Welcome back to DiamondFire!")) {
				 * currentPlot = "";
				 * commandsToSend.add("support queue");
				 * delaysToSend.add(20);
				 * } else
				 */if (messageStr.contains("Use /tip to show your appreciation and receive a")) {
					commandsToSend.add("tip");
					delaysToSend.add(60);
				} else if (messageStr.contains("You are now in dev mode.")) {
					inDevMode = true;
					client.gui.hud.getChat()
							.addClientSystemMessage(Component.literal("§7[§2TomDfTweaks§7] §aNow in dev mode."));
				} else if (joinPlot.matches()) {
					String newPlot = joinPlot.group(1) + "|" + joinPlot.group(2);
					if (!newPlot.equals(currentPlot)) {
						devLocation = null;
						playLocation = null;
					}
					currentPlot = newPlot;
					inDevMode = false;
					client.gui.hud.getChat()
							.addClientSystemMessage(Component
									.literal("§7[§2TomDfTweaks§7] §aJoined plot \"§r" + currentPlot + "§a\"."));

					boolean enableNoteBlockSounds = (!noteBlockSounds.containsKey(currentPlot)
							|| noteBlockSounds.get(currentPlot));
					// TomDfTweaks.LOGGER.info("enablenoteblocksounds: " + enableNoteBlockSounds);
					client.options.getSoundSourceOptionInstance(SoundSource.RECORDS)
							.set(enableNoteBlockSounds ? 1.0 : 0.0);
					client.options.save();
					client.gui.hud.getChat()
							.addClientSystemMessage(Component.literal("§7[§2TomDfTweaks§7] §6Note blocks "
									+ (enableNoteBlockSounds ? "§eenabled" : "§csilenced") + "§6."));

					currentFullbright = (fullbright.containsKey(currentPlot)
							&& fullbright.get(currentPlot));
					client.gui.hud.getChat()
							.addClientSystemMessage(Component.literal("§7[§2TomDfTweaks§7] §6Fullbright "
									+ (currentFullbright ? "§eenabled" : "§cdisabled") + "§6."));
				}
			}
		});

		KeyMapping toggleNoteBlockKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.tomdftweaks.noteblocks", // 1. Translation Key (Name)
				InputConstants.KEY_M, // 2. Keycode (int)
				Category.MISC // 3. Translation Key (Category)
		));

		KeyMapping toggleFullbrightKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.tomdftweaks.fullbright",
				InputConstants.KEY_N,
				Category.MISC));

		KeyMapping spectatorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.tomdftweaks.spectator",
				InputConstants.KEY_V,
				Category.MISC));

		KeyMapping modeSwitchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.tomdftweaks.modeswitch",
				InputConstants.KEY_B,
				Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register((a) -> {
			// Auto-respawn: clear flag once player is alive again
			if (awaitingRespawn && client.player != null && client.player.isAlive()) {
				awaitingRespawn = false;
			}

			// Restore sprint toggle after respawning
			if (shouldRestoreSprint && client.player != null && client.player.isAlive()) {
				client.options.keySprint.setDown(true);
				shouldRestoreSprint = false;
			}

			// Send commands, if queued
			if (commandsToSend.size() > 0) {
				commandSendCountdown++;
				if (commandSendCountdown >= delaysToSend.get(0)) {
					commandSendCountdown = 0;
					client.getConnection().sendCommand(commandsToSend.remove(0));
					delaysToSend.remove(0);
				}
			}

			// Toggle note blocks
			while (toggleNoteBlockKey.consumeClick()) {
				if (client.player != null) {
					float currentVolume = client.options.getSoundSourceVolume(SoundSource.RECORDS);
					double newVolume = (currentVolume > 0.1f) ? 0.0 : 1.0;

					noteBlockSounds.put(currentPlot, newVolume > 0.1f);

					client.options.getSoundSourceOptionInstance(SoundSource.RECORDS).set(newVolume);
					client.options.save();

					String status = (newVolume > 0.1) ? "§eenabled" : "§csilenced";
					client.gui.chatListener().handleSystemMessage(
							Component.literal(
									"§7[§2TomDfTweaks§7] §6Note blocks for §r" + currentPlot + " " + status + "§6."),
							true);
				}
			}

			// Toggle fullbright
			while (toggleFullbrightKey.consumeClick()) {
				if (client.player != null) {
					currentFullbright = !currentFullbright;

					fullbright.put(currentPlot, currentFullbright);
					if (client.levelExtractor != null) {
						// This forces the game to throw away the old "dark" chunk meshes
						// and rebuild them using the new Fullbright 'pack' logic.
						client.levelExtractor.allChanged();
					}

					String status = (currentFullbright) ? "§eenabled" : "§cdisabled";
					client.gui.chatListener().handleSystemMessage(
							Component.literal(
									"§7[§2TomDfTweaks§7] §6Fullbright for §r" + currentPlot + " " + status + "§6."),
							true);
				}
			}

			// Mode switch (dev/play) with location preservation
			while (modeSwitchKey.consumeClick()) {
				if (client.player != null) {
					BlockPos pos = client.player.blockPosition();

					if (inDevMode) {
						devLocation = pos;
						commandsToSend.add("play");
						delaysToSend.add(1);
						if (playLocation != null) {
							commandsToSend.add("ptp " + playLocation.getX() + " " + playLocation.getY() + " "
									+ playLocation.getZ());
							delaysToSend.add(10);
						}
					} else {
						playLocation = pos;
						commandsToSend.add("dev");
						delaysToSend.add(1);
						if (devLocation != null) {
							commandsToSend.add(
									"ptp " + devLocation.getX() + " " + devLocation.getY() + " " + devLocation.getZ());
							delaysToSend.add(10);
						}
					}
				}
			}

			// Spectator mode
			if (!inDevMode) {
				boolean spectatorIsPressed = spectatorKey.isDown();

				if (spectatorIsPressed && !spectatorWasPressed) {
					GameType current = client.gameMode.getPlayerMode();
					if (current != GameType.SPECTATOR) {
						previousGameMode = current;
						client.player.connection.sendCommand("gamemode spectator");
					}
					spectatorWasPressed = true;
				} else if (!spectatorIsPressed && spectatorWasPressed) {
					if (previousGameMode != null) {
						client.player.connection
								.sendCommand("gamemode " + previousGameMode.getSerializedName());
						previousGameMode = null;
					}
					spectatorWasPressed = false;
				}
			}
		});
	}
}
