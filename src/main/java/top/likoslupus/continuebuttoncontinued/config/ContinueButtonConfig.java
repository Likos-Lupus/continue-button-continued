package top.likoslupus.continuebuttoncontinued.config;

import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.likoslupus.continuebuttoncontinued.ContinueButtonConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Encapsulates the persisted "last played target" state. The previous implementation exposed this
 * as public mutable static fields on the client entrypoint, which made it hard to reason about and
 * shared mutable state across threads.
 */
public final class ContinueButtonConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContinueButtonConstants.MOD_ID);

    private boolean lastLocal = true;
    private String serverName = "";
    private String serverAddress = "";

    private ContinueButtonConfig() {
    }

    public static ContinueButtonConfig load() {
        var config = new ContinueButtonConfig();
        config.migrateLegacyIfNeeded();

        var file = getConfigFile();
        var properties = read(file);
        config.lastLocal = Boolean.parseBoolean(
                properties.getProperty(
                        "last-local",
                        "true"
                )
        );
        config.serverName = safe(
                properties.getProperty(
                        "server-name",
                        ""
                )
        );
        config.serverAddress = safe(
                properties.getProperty(
                        "server-address",
                        ""
                )
        );

        if (!Files.exists(file)) {
            config.save();
        }

        return config;
    }

    private void migrateLegacyIfNeeded() {
        var legacyConfig = Platform.getConfigFolder()
                .resolve(ContinueButtonConstants.OLD_MOD_ID)
                .resolve(ContinueButtonConstants.CONFIG_FILE_NAME);
        var newConfig = getConfigFile();

        if (Files.exists(newConfig)
                || !Files.exists(legacyConfig)
        ) {
            return;
        }

        try {
            Files.createDirectories(newConfig.getParent());
            Files.copy(legacyConfig, newConfig);
            LOGGER.info(
                    "Migrated legacy Continue Button config from {} to {}",
                    legacyConfig,
                    newConfig
            );
        } catch (IOException exception) {
            LOGGER.warn(
                    "Failed to migrate legacy Continue Button config from {} to {}",
                    legacyConfig,
                    newConfig,
                    exception
            );
        }
    }

    private static Path getConfigFile() {
        return Platform.getConfigFolder()
                .resolve(ContinueButtonConstants.MOD_ID)
                .resolve(ContinueButtonConstants.CONFIG_FILE_NAME);
    }

    private static Properties read(Path file) {
        var properties = new Properties();

        if (!Files.exists(file)) {
            return properties;
        }

        try (var stream = Files.newInputStream(file)) {
            properties.load(stream);
        } catch (IOException exception) {
            LOGGER.warn("Failed to load config from {}", file, exception);
        }

        return properties;
    }

    private static String safe(String value) {
        return value == null
                ? ""
                : value;
    }

    public synchronized void save() {
        var file = getConfigFile();
        var properties = read(file);

        properties.setProperty("last-local", Boolean.toString(lastLocal));
        properties.setProperty("server-name", safe(serverName));
        properties.setProperty("server-address", safe(serverAddress));

        try {
            Files.createDirectories(file.getParent());
            try (var stream = Files.newOutputStream(file)) {
                properties.store(stream, "Continue Button Continued config");
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to save config to {}", file, exception);
        }
    }

    public boolean lastLocal() {
        return lastLocal;
    }

    public String serverName() {
        return serverName;
    }

    public String serverAddress() {
        return serverAddress;
    }

    public void setLocalTarget(String name, String address) {
        this.lastLocal = true;
        this.serverName = safe(name);
        this.serverAddress = safe(address);
    }

    public void setRemoteTarget(String name, String address) {
        this.lastLocal = false;
        this.serverName = safe(name);
        this.serverAddress = safe(address);
    }

    public void clear() {
        this.lastLocal = true;
        this.serverName = "";
        this.serverAddress = "";
        save();
    }

}
