package top.likoslupus.continuebuttoncontinued;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;
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
        properties.setProperty("server-name", serverName == null ? "" : serverName);
        properties.setProperty("server-address", serverAddress == null ? "" : serverAddress);

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

    private static void saveIntegratedServer(MinecraftClient client) {
        if (client.getServer() == null) {
            LOGGER.warn("Integrated server was expected but client server was null.");
            return;
        }

        lastLocal = true;
        serverName = client.getServer().getSaveProperties().getLevelName();
        var pathToSave = Path.of(com.google.common.io.Files.simplifyPath(
                client.getServer()
                        .getSavePath(WorldSavePath.ROOT)
                        .toString()
        ));
        serverAddress = pathToSave.normalize().toFile().getName();
    }

    private static void saveRemoteServer(MinecraftClient client) {
        var serverInfo = client.getCurrentServerEntry();
        if (serverInfo == null) {
            LOGGER.warn("Unable to save last remote server because the current server entry is null.");
            return;
        }

        lastLocal = false;
        serverName = serverInfo.name == null ? "" : serverInfo.name;
        serverAddress = serverInfo.address == null ? "" : serverInfo.address;
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

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.isIntegratedServerRunning()) {
                saveIntegratedServer(client);
            } else {
                saveRemoteServer(client);
            }

            saveConfig();
        });
    }

}
