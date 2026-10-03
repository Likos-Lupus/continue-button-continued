package top.likoslupus.continuebuttoncontinued;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.gui.screens.TitleScreen;
import top.likoslupus.continuebuttoncontinued.config.ContinueButtonConfig;
import top.likoslupus.continuebuttoncontinued.target.ContinueTargetTracker;
import top.likoslupus.continuebuttoncontinued.ui.ContinueButtonController;

/**
 * Loader-neutral composition root. Called once by the Fabric/NeoForge entrypoints.
 */
public final class ContinueButtonClient {

    private static ContinueButtonConfig config;
    private static ContinueButtonController controller;

    private ContinueButtonClient() {
    }

    public static void init() {
        if (controller != null) {
            return;
        }

        config = ContinueButtonConfig.load();
        controller = new ContinueButtonController(config);

        ContinueTargetTracker.register(config);

        ClientGuiEvent.INIT_POST.register((screen, access) -> {
            if (screen instanceof TitleScreen) {
                controller.attach(screen, access);
            }
        });

        ClientGuiEvent.SCREEN_CLOSING.register(screen -> {
            if (screen instanceof TitleScreen) {
                controller.dispose();
            }
        });

        ClientTickEvent.CLIENT_POST.register(client -> controller.tickIfActive(client));
    }

}
