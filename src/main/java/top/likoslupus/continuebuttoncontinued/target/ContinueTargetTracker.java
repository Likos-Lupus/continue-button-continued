package top.likoslupus.continuebuttoncontinued.target;

import dev.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.likoslupus.continuebuttoncontinued.ContinueButtonConstants;
import top.likoslupus.continuebuttoncontinued.config.ContinueButtonConfig;

/**
 * Records the world/server the player last joined. Uses Architectury's cross-loader player event
 * instead of the previous Fabric-only {@code ClientPlayConnectionEvents.JOIN}.
 */
public final class ContinueTargetTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContinueButtonConstants.MOD_ID);

    private ContinueTargetTracker() {
    }

    public static void register(ContinueButtonConfig config) {
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(_ ->
                update(
                        config,
                        Minecraft.getInstance()
                )
        );
    }

    private static void update(
            ContinueButtonConfig config,
            Minecraft minecraft
    ) {
        if (minecraft.hasSingleplayerServer()) {
            saveIntegratedServer(config, minecraft);
        } else {
            saveRemoteServer(config, minecraft);
        }

        config.save();
    }

    private static void saveIntegratedServer(
            ContinueButtonConfig config,
            Minecraft minecraft
    ) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            LOGGER.warn(
                    "Integrated server was expected but Minecraft#getSingleplayerServer returned null."
            );
            return;
        }

        var path = server.getWorldPath(LevelResource.ROOT).normalize();
        config.setLocalTarget(
                server.getWorldData().getLevelName(),
                path.toFile().getName()
        );
    }

    private static void saveRemoteServer(
            ContinueButtonConfig config,
            Minecraft minecraft
    ) {
        var serverData = minecraft.getCurrentServer();
        if (serverData == null) {
            LOGGER.warn(
                    "Unable to save last remote server because Minecraft#getCurrentServer returned null."
            );
            return;
        }

        config.setRemoteTarget(serverData.name, serverData.ip);
    }

}
