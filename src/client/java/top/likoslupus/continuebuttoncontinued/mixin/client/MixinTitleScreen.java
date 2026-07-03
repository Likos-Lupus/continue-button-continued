package top.likoslupus.continuebuttoncontinued.mixin.client;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.likoslupus.continuebuttoncontinued.ContinueButtonClient;
import top.likoslupus.continuebuttoncontinued.ContinueButtonConstants;

import java.util.stream.IntStream;

@Mixin(value = TitleScreen.class, priority = 1001)
public abstract class MixinTitleScreen extends Screen {

    @Unique
    private final ServerStatusPinger continueButtonContinued$serverListPinger = new ServerStatusPinger();
    @Unique
    private ServerData continueButtonContinued$serverData;
    @Unique
    private Button continueButtonContinued$continueButton;
    @Unique
    private Thread continueButtonContinued$serverLookupThread;

    protected MixinTitleScreen(Component title) {
        super(title);
    }

    @Inject(
            method = "init",
            at = @At("HEAD")
    )
    private void continueButtonContinued$resetState(CallbackInfo ci) {
        continueButtonContinued$serverData = null;
        continueButtonContinued$continueButton = null;
    }

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void continueButtonContinued$addContinueButton(CallbackInfo ci) {
        var singleplayerButton = continueButtonContinued$findSingleplayerButton();
        var y = singleplayerButton == null ? this.height / 4 + 48 : singleplayerButton.getY();

        if (singleplayerButton != null) {
            singleplayerButton.setX(this.width / 2 + 2);
            singleplayerButton.setWidth(98);
        }

        var builder = Button.builder(
                        Component.translatable(ContinueButtonConstants.TRANSLATION_CONTINUE_BUTTON_TITLE),
                        _ -> continueButtonContinued$openLastTarget()
                )
                .bounds(this.width / 2 - 100, y, 98, 20);

        var tooltip = continueButtonContinued$createLocalTooltip();
        if (tooltip != null) {
            builder.tooltip(tooltip);
        }

        continueButtonContinued$continueButton = this.addRenderableWidget(builder.build());
        continueButtonContinued$refreshLastServerInBackground();
    }

    @Unique
    private Button continueButtonContinued$findSingleplayerButton() {
        var singleplayerMessage = Component.translatable("menu.singleplayer");

        for (var widget : Screens.getWidgets(this)) {
            if (widget instanceof Button button
                    && button.visible
                    && singleplayerMessage.equals(button.getMessage())) {
                return button;
            }
        }

        return null;
    }

    @Unique
    private void continueButtonContinued$openLastTarget() {
        if (ContinueButtonClient.lastLocal) {
            if (ContinueButtonClient.serverAddress.isEmpty()
                    || !this.minecraft.getLevelSource().levelExists(ContinueButtonClient.serverAddress)) {
                this.minecraft.setScreenAndShow(new SelectWorldScreen(new TitleScreen()));
                return;
            }

            this.minecraft.createWorldOpenFlows()
                    .openWorld(
                            ContinueButtonClient.serverAddress,
                            () -> this.minecraft.setScreenAndShow(new TitleScreen())
                    );
            return;
        }

        var server = continueButtonContinued$serverData;
        if (server == null) {
            server = continueButtonContinued$findServerInList();
        }

        if (server == null) {
            ContinueButtonClient.clearSavedTarget();
            this.minecraft.setScreenAndShow(new JoinMultiplayerScreen(new TitleScreen()));
            return;
        }

        ConnectScreen.startConnecting(
                new JoinMultiplayerScreen(new TitleScreen()),
                this.minecraft,
                ServerAddress.parseString(server.ip),
                server,
                false,
                null
        );
    }

    @Unique
    private Tooltip continueButtonContinued$createLocalTooltip() {
        if (!ContinueButtonClient.lastLocal) return null;

        if (ContinueButtonClient.serverAddress.isEmpty()) {
            return Tooltip.create(Component.translatable("selectWorld.create"));
        }

        return Tooltip.create(Component.translatable("menu.singleplayer")
                .append(Component.literal(" " + ContinueButtonClient.serverName)));
    }

    @Unique
    private void continueButtonContinued$refreshLastServerInBackground() {
        if (ContinueButtonClient.lastLocal || ContinueButtonClient.serverAddress.isEmpty()) {
            return;
        }

        if (continueButtonContinued$serverLookupThread != null
                && continueButtonContinued$serverLookupThread.isAlive()) {
            return;
        }

        continueButtonContinued$serverLookupThread = new Thread(() -> {
            var serverInList = continueButtonContinued$findServerInList();
            var minecraft = this.minecraft;

            if (Thread.currentThread().isInterrupted()) return;

            minecraft.execute(() -> {
                if (serverInList == null) {
                    ContinueButtonClient.clearSavedTarget();
                    return;
                }

                continueButtonContinued$serverData = serverInList;
                ContinueButtonClient.lastLocal = false;
                ContinueButtonClient.serverName = serverInList.name;
                ContinueButtonClient.serverAddress = serverInList.ip;
                ContinueButtonClient.saveConfig();

                if (continueButtonContinued$continueButton != null) {
                    continueButtonContinued$continueButton.setTooltip(continueButtonContinued$createRemoteTooltip(serverInList));
                }

                continueButtonContinued$pingServer(serverInList);
            });
        }, "Continue Button Continued Server Lookup");

        continueButtonContinued$serverLookupThread.setDaemon(true);
        continueButtonContinued$serverLookupThread.start();
    }

    @Unique
    private ServerData continueButtonContinued$findServerInList() {
        var serverList = new ServerList(this.minecraft);
        serverList.load();

        var exactMatch = serverList.get(ContinueButtonClient.serverAddress);
        if (exactMatch != null) {
            return exactMatch;
        }

        return IntStream.range(0, serverList.size())
                .mapToObj(serverList::get)
                .filter(entry ->
                        entry.ip.equalsIgnoreCase(ContinueButtonClient.serverAddress)
                )
                .findFirst()
                .orElse(null);
    }

    @Unique
    private Tooltip continueButtonContinued$createRemoteTooltip(ServerData server) {
        var title = server.name.isEmpty()
                ? Component.literal(server.ip)
                : Component.literal(server.name);

        return Tooltip.create(title.copy()
                .append(Component.literal("\n"))
                .append(server.motd));
    }

    @Unique
    private void continueButtonContinued$pingServer(ServerData server) {
        server.motd = Component.translatable("multiplayer.status.pinging");

        try {
            continueButtonContinued$serverListPinger.pingServer(
                    server,
                    () -> {
                        if (continueButtonContinued$continueButton != null) {
                            continueButtonContinued$continueButton.setTooltip(continueButtonContinued$createRemoteTooltip(server));
                        }
                    },
                    () -> {
                        if (continueButtonContinued$continueButton != null) {
                            continueButtonContinued$continueButton.setTooltip(continueButtonContinued$createRemoteTooltip(server));
                        }
                    },
                    EventLoopGroupHolder.remote(this.minecraft.options.useNativeTransport())
            );
        } catch (Exception exception) {
            ContinueButtonClient.LOGGER.warn("Failed to ping last server {}", server.ip, exception);
        }
    }

    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    private void continueButtonContinued$tick(CallbackInfo ci) {
        continueButtonContinued$serverListPinger.tick();
    }

    @Inject(
            method = "removed",
            at = @At("HEAD")
    )
    private void continueButtonContinued$removed(CallbackInfo ci) {
        if (continueButtonContinued$serverLookupThread != null) {
            continueButtonContinued$serverLookupThread.interrupt();
        }

        continueButtonContinued$serverListPinger.removeAll();
    }

}
