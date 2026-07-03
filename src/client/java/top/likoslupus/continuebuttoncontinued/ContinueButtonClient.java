package top.likoslupus.continuebuttoncontinued;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ContinueButtonClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ContinueButtonContinued");

    public static boolean lastLocal = true;
    public static String serverName = "";
    public static String serverAddress = "";

    public static synchronized void loadConfig() {
        migrateLegacyConfigIfNeeded();

        var configFile = getConfigFile();
        var properties = loadProperties(configFile);

        lastLocal = Boolean.parseBoolean(properties.getProperty("last-local", "true"));
        serverName = properties.getProperty("server-name", "");
        serverAddress = properties.getProperty("server-address", "");

        if (!Files.exists(configFile)) {
            saveConfig();
        }
    }

    public static synchronized void clearSavedTarget() {
        lastLocal = true;
        serverName = "";
        serverAddress = "";
        saveConfig();
    }

    public static synchronized void saveConfig() {
        var configFile = getConfigFile();
        var properties = loadProperties(configFile);

        properties.setProperty("last-local", Boolean.toString(lastLocal));
        properties.setProperty("server-name", safeString(serverName));
        properties.setProperty("server-address", safeString(serverAddress));

        try {
            Files.createDirectories(configFile.getParent());
            try (var stream = Files.newOutputStream(configFile)) {
                properties.store(stream, "Continue Button Continued config");
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to save config to {}", configFile, exception);
        }
    }

    private static Path getConfigFile() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(ContinueButtonConstants.MOD_ID)
                .resolve(ContinueButtonConstants.CONFIG_FILE_NAME);
    }

    private static Properties loadProperties(Path configFile) {
        var properties = new Properties();

        if (!Files.exists(configFile)) {
            return properties;
        }

        try (var stream = Files.newInputStream(configFile)) {
            properties.load(stream);
        } catch (IOException exception) {
            LOGGER.warn("Failed to load config from {}", configFile, exception);
        }

        return properties;
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static void saveIntegratedServer(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            LOGGER.warn("Integrated server was expected but Minecraft#getSingleplayerServer returned null.");
            return;
        }

        lastLocal = true;
        serverName = safeString(server.getWorldData().getLevelName());
        var pathToSave = Path.of(com.google.common.io.Files.simplifyPath(
                server.getWorldPath(LevelResource.ROOT).toString()
        ));
        serverAddress = pathToSave.normalize().toFile().getName();
    }

    private static void saveRemoteServer(Minecraft minecraft) {
        var serverData = minecraft.getCurrentServer();
        if (serverData == null) {
            LOGGER.warn("Unable to save last remote server because Minecraft#getCurrentServer returned null.");
            return;
        }

        lastLocal = false;
        serverName = safeString(serverData.name);
        serverAddress = safeString(serverData.ip);
    }

    private static void migrateLegacyConfigIfNeeded() {
        var legacyConfig = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(ContinueButtonConstants.OLD_MOD_ID)
                .resolve(ContinueButtonConstants.CONFIG_FILE_NAME);
        var newConfig = getConfigFile();

        if (Files.exists(newConfig) || !Files.exists(legacyConfig)) return;

        try {
            Files.createDirectories(newConfig.getParent());
            Files.copy(legacyConfig, newConfig);
            LOGGER.info("Migrated legacy Continue Button config from {} to {}", legacyConfig, newConfig);
        } catch (IOException exception) {
            LOGGER.warn("Failed to migrate legacy Continue Button config from {} to {}", legacyConfig, newConfig, exception);
        }
    }

    @Override
    public void onInitializeClient() {
        loadConfig();

        ClientPlayConnectionEvents.JOIN.register((_, _, client) -> {
            if (client.hasSingleplayerServer()) {
                saveIntegratedServer(client);
            } else {
                saveRemoteServer(client);
            }

            saveConfig();
        });
    }

}
