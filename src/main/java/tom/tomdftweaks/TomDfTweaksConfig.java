package tom.tomdftweaks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TomDfTweaksConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // Saves to .minecraft/config/mymod_settings.json
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("tom_df_tweaks_settings.json");

    public static Map<String, Map<String, Boolean>> settings = new HashMap<>();

    public static void save() {
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(settings, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        File file = CONFIG_PATH.toFile();
        if (!file.exists())
            return;

        try (Reader reader = new FileReader(file)) {
            settings = GSON.fromJson(reader, new TypeToken<Map<String, Map<String, Boolean>>>() {
            }.getType());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}