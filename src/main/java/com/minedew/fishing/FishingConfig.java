package com.minedew.fishing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.loader.api.FabricLoader;

/** File-backed settings, read once at startup and written back when an op changes one. */
public final class FishingConfig {
    private FishingConfig() {}

    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("minedew-fishing.properties");

    private static FishingDifficulty difficulty = FishingDifficulty.NORMAL;

    private static final String DEFAULT_CONFIG = """
            # Minedew Fishing Configuration
            # Delete this file to regenerate with defaults.

            # How hard the fight with a hooked fish is: easiest, easy, normal or hard.
            # Easier gives a taller bar, a slower fish and a meter that drains slower;
            # harder, a shorter bar and a quicker fish. An op can set it for one player
            # with /fishing difficulty player <name> <level>.
            difficulty=normal
            """;

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            try {
                Files.createDirectories(CONFIG_PATH.getParent());
                Files.writeString(CONFIG_PATH, DEFAULT_CONFIG);
            } catch (IOException e) {
                MinedewFishing.LOGGER.error("[{}] Failed to write default config: {}", MinedewFishing.MOD_ID, e.getMessage());
            }
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
        } catch (IOException e) {
            MinedewFishing.LOGGER.error("[{}] Failed to read config, using defaults: {}", MinedewFishing.MOD_ID, e.getMessage());
            return;
        }

        String value = props.getProperty("difficulty");
        if (value != null) {
            FishingDifficulty named = FishingDifficulty.named(value);
            if (named != null) difficulty = named;
            else MinedewFishing.LOGGER.warn("[{}] Config 'difficulty' is not one of easiest/easy/normal/hard, using normal",
                MinedewFishing.MOD_ID);
        }
    }

    public static FishingDifficulty difficulty() {
        return difficulty;
    }

    public static void setDifficulty(FishingDifficulty chosen) {
        difficulty = chosen;
        store("difficulty", chosen.getSerializedName());
    }

    /** The line for the key is replaced where it stands, or added when missing; comments stay. */
    private static void store(String key, String value) {
        try {
            List<String> lines = Files.exists(CONFIG_PATH) ? new ArrayList<>(Files.readAllLines(CONFIG_PATH)) : new ArrayList<>();
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith(key + "=") || line.startsWith(key + " =")) {
                    lines.set(i, key + "=" + value);
                    found = true;
                }
            }
            if (!found) lines.add(key + "=" + value);
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, lines);
        } catch (IOException e) {
            MinedewFishing.LOGGER.error("[{}] Could not write config: {}", MinedewFishing.MOD_ID, e.getMessage());
        }
    }

    /** The difficulty in the mod menu, for ops. */
    public static void menu() {
        Map<String, String> levels = new LinkedHashMap<>();
        for (FishingDifficulty level : FishingDifficulty.values()) levels.put(level.getSerializedName(), level.label);
        PandoricalApi.settings().serverGroup(MinedewFishing.MOD_ID, "Minedew Fishing")
            .choice("difficulty", "Fishing difficulty", levels, "normal")
            .describe("For everyone an op has not set one for, with /fishing difficulty")
            .backedBy(player -> difficulty().getSerializedName(), (player, v) -> setDifficulty(FishingDifficulty.named(v)));
    }
}
