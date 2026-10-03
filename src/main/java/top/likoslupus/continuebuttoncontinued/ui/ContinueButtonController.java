package top.likoslupus.continuebuttoncontinued.ui;

import dev.architectury.hooks.client.screen.ScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.likoslupus.continuebuttoncontinued.ContinueButtonConstants;
import top.likoslupus.continuebuttoncontinued.config.ContinueButtonConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static net.minecraft.network.chat.Component.literal;
import static net.minecraft.network.chat.Component.translatable;

/**
 * Owns all of the Continue button behaviour for the title screen: layout, tooltip, opening the last
 * target and the (background) server ping. It is a mod-wide singleton so that ping state survives
 * title-screen re-initialisation (e.g. window resizes) without re-pinging or leaking threads.
 *
 * <p>The previous implementation did all of this inside a title-screen mixin and, importantly, ran
 * the ping on the render thread. {@code ServerStatusPinger} performs blocking DNS resolution and
 * {@code Connection#connectToServer} blocks on the TCP connect, so a down server froze the main
 * menu. The ping is now submitted to a daemon executor instead, matching vanilla behaviour.</p>
 */
public final class ContinueButtonController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContinueButtonConstants.MOD_ID);
    private static final Component CANNOT_CONNECT =
            translatable(
                    "multiplayer.status.cannot_connect"
            );
    private static final Component PINGING =
            translatable(
                    "multiplayer.status.pinging"
            );
    private static final int PING_TIMEOUT_TICKS = 400;
    private final ContinueButtonConfig config;
    private final ExecutorService pingExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                var thread = new Thread(runnable, "Continue Button Continued Ping");
                thread.setDaemon(true);
                return thread;
            });
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private Screen screen;
    private Button continueButton;
    private ServerData serverData;
    private PingState pingState = PingState.IDLE;
    private boolean pingReachable;
    private int pingTicks;

    public ContinueButtonController(ContinueButtonConfig config) {
        this.config = config;
    }

    /**
     * Called for every {@code TitleScreen} init. Re-runs on resize, so it must be idempotent and
     * must not restart an in-flight ping.
     */
    public void attach(Screen screen, ScreenAccess access) {
        this.screen = screen;
        this.continueButton = null;

        var singleplayerButton = findSingleplayerButton(access);
        var y = singleplayerButton != null
                ? singleplayerButton.getY()
                : screen.height / 4 + 48;

        if (singleplayerButton != null) {
            singleplayerButton.setX(screen.width / 2 + ContinueButtonConstants.HALF_ROW_OFFSET);
            singleplayerButton.setWidth(ContinueButtonConstants.BUTTON_WIDTH);
        }

        var builder = Button.builder(
                        translatable(ContinueButtonConstants.TRANSLATION_CONTINUE_BUTTON_TITLE),
                        _ -> openLastTarget()
                )
                .bounds(
                        screen.width / 2 - 100,
                        y,
                        ContinueButtonConstants.BUTTON_WIDTH,
                        ContinueButtonConstants.BUTTON_HEIGHT
                );

        var tooltip = currentTooltip();
        if (tooltip != null) {
            builder.tooltip(tooltip);
        }

        this.continueButton = access.addRenderableWidget(builder.build());

        if (!config.lastLocal()
                && !config.serverAddress().isEmpty()
        ) {
            startRemotePing();
        }
    }

    private Button findSingleplayerButton(ScreenAccess access) {
        var singleplayerMessage = translatable(ContinueButtonConstants.TRANSLATION_MENU_SINGLEPLAYER);
        return access.getRenderables().stream()
                .filter(renderable -> renderable instanceof Button button
                        && button.visible
                        && singleplayerMessage.equals(button.getMessage())
                )
                .map(renderable -> (Button) renderable)
                .findFirst()
                .orElse(null);
    }

    private void openLastTarget() {
        var minecraft = Minecraft.getInstance();

        if (config.lastLocal()) {
            if (config.serverAddress().isEmpty()
                    || !minecraft.getLevelSource().levelExists(config.serverAddress())
            ) {
                minecraft.setScreenAndShow(new SelectWorldScreen(new TitleScreen()));
                return;
            }

            minecraft.createWorldOpenFlows().openWorld(
                    config.serverAddress(),
                    () -> minecraft.setScreenAndShow(new TitleScreen())
            );
            return;
        }

        var server = serverData;
        if (server == null) {
            server = new ServerData(
                    config.serverName(),
                    config.serverAddress(),
                    ServerData.Type.OTHER
            );
        }

        // The ping already told us this server is down. ConnectScreen would otherwise surface the
        // raw Netty exception ("finishConnect(..) failed with error(-111): Connection refused")
        // to the player, so show a clean failure screen instead.
        if (pingState == PingState.DONE && !pingReachable) {
            minecraft.setScreenAndShow(
                    new DisconnectedScreen(
                            new TitleScreen(),
                            CommonComponents.CONNECT_FAILED,
                            translatable("multiplayer.status.cannot_connect")
                    )
            );
            return;
        }

        ConnectScreen.startConnecting(
                new JoinMultiplayerScreen(new TitleScreen()),
                minecraft,
                ServerAddress.parseString(server.ip),
                server,
                false,
                null
        );
    }

    private Tooltip currentTooltip() {
        if (config.lastLocal()) {
            return createLocalTooltip();
        }

        if (serverData != null) {
            return Tooltip.create(remoteTooltipText(serverData));
        }

        return null;
    }

    private void startRemotePing() {
        if (pingState != PingState.IDLE) {
            return;
        }

        var target = config.serverAddress();
        pingState = PingState.PINGING;
        pingTicks = 0;

        // The saved name/address are enough to ping; there is no need to re-read servers.dat.
        final var server = new ServerData(
                config.serverName(),
                target,
                ServerData.Type.OTHER
        );
        this.serverData = server;

        pingExecutor.submit(() -> {
            // Runs off the render thread on purpose: DNS + TCP connect block.
            try {
                pinger.pingServer(
                        server,
                        () -> {
                        },
                        () -> Minecraft.getInstance().execute(this::onPong),
                        EventLoopGroupHolder.remote(Minecraft.getInstance().options.useNativeTransport())
                );
            } catch (Exception exception) {
                LOGGER.warn("Could not reach the last server {}", target);
                Minecraft.getInstance().execute(this::failPing);
            }
        });
    }

    private Tooltip createLocalTooltip() {
        if (config.serverAddress().isEmpty()) {
            return Tooltip.create(translatable("selectWorld.create"));
        }

        return Tooltip.create(translatable("menu.singleplayer")
                .append(literal(" " + config.serverName())));
    }

    private Component remoteTooltipText(ServerData data) {
        var title = data.name == null || data.name.isEmpty()
                ? literal(data.ip)
                : literal(data.name);

        return title.copy()
                .append(literal("\n"))
                .append(data.motd != null
                        ? data.motd
                        : Component.empty()
                );
    }

    private void onPong() {
        pingState = PingState.DONE;
        pingReachable = true;
        updateRemoteTooltip();
    }

    private void failPing() {
        if (pingState == PingState.DONE) {
            return;
        }

        pingState = PingState.DONE;
        pingReachable = false;

        var button = this.continueButton;
        if (button == null || serverData == null) {
            return;
        }

        button.setTooltip(Tooltip.create(remoteTooltipText(serverData)
                .copy()
                .append(literal("\n"))
                .append(CANNOT_CONNECT)));
    }

    private void updateRemoteTooltip() {
        var button = this.continueButton;
        var data = this.serverData;
        if (button == null || data == null) {
            return;
        }

        if (data.motd == null || data.motd.getString().equals(PINGING.getString())) {
            return;
        }

        button.setTooltip(Tooltip.create(remoteTooltipText(data)));
    }

    public void tickIfActive(Minecraft client) {
        //? if >=26.2 {
        if (client.gui.screen() != screen) {
            return;
        }
        //?} else {
        /*if (client.screen != screen) {
            return;
        }*/
        //?}

        pinger.tick();

        if (pingState == PingState.PINGING) {
            pingTicks++;
            if (pingTicks > PING_TIMEOUT_TICKS || hasPingFailed()) {
                failPing();
            }
        }
    }

    private boolean hasPingFailed() {
        var data = this.serverData;
        return data != null
                && data.motd != null
                && data.motd.getString().equals(CANNOT_CONNECT.getString());
    }

    public void dispose() {
        pinger.removeAll();
        this.screen = null;
        this.continueButton = null;
        this.serverData = null;
        this.pingState = PingState.IDLE;
        this.pingReachable = false;
        this.pingTicks = 0;
    }

    private enum PingState {

        IDLE,
        PINGING,
        DONE

    }

}
