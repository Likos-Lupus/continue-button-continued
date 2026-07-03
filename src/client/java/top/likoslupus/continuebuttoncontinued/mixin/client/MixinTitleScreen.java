package top.likoslupus.continuebuttoncontinued.mixin.client;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.network.NetworkingBackend;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.likoslupus.continuebuttoncontinued.ContinueButtonClient;
import top.likoslupus.continuebuttoncontinued.ContinueButtonConstants;

import java.util.ArrayList;
import java.util.stream.IntStream;

@Mixin(value = TitleScreen.class, priority = 1001)
public abstract class MixinTitleScreen extends Screen {

    @Unique
    private final MultiplayerServerListPinger continueButtonContinued$serverListPinger = new MultiplayerServerListPinger();
    @Unique
    private ServerInfo continueButtonContinued$serverInfo;
    @Unique
    private boolean continueButtonContinued$firstRender = true;
    @Unique
    private ButtonWidget continueButtonContinued$continueButton;
    @Unique
    private Thread continueButtonContinued$serverLookupThread;

    protected MixinTitleScreen(Text title) {
        super(title);
    }

    @Inject(
            at = @At("HEAD"),
            method = "addNormalWidgets(II)I"
    )
    private void continueButtonContinued$addContinueButton(
            int y,
            int spacingY,
            CallbackInfoReturnable<Integer> cir
    ) {
        var tooltip = continueButtonContinued$createLocalTooltip();

        continueButtonContinued$continueButton = this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable(ContinueButtonConstants.TRANSLATION_CONTINUE_BUTTON_TITLE),
                        button -> continueButtonContinued$openLastTarget()
                )
                .dimensions(
                        this.width / 2 - 100,
                        y,
                        98,
                        20
                )
                .tooltip(tooltip)
                .build());
    }

    @Unique
    private Tooltip continueButtonContinued$createLocalTooltip() {
        if (!ContinueButtonClient.lastLocal) return null;

        if (ContinueButtonClient.serverAddress.isEmpty()) {
            return Tooltip.of(Text.translatable("selectWorld.create"));
        }

        return Tooltip.of(Text.translatable("menu.singleplayer")
                .append(Text.literal(" " + ContinueButtonClient.serverName)));
    }

    @Unique
    private void continueButtonContinued$openLastTarget() {
        if (this.client == null) return;

        if (ContinueButtonClient.lastLocal) {
            if (ContinueButtonClient.serverAddress.isEmpty()
                    || !this.client.getLevelStorage().levelExists(ContinueButtonClient.serverAddress)) {
                this.client.setScreen(new SelectWorldScreen(new TitleScreen()));
                return;
            }

            this.client.createIntegratedServerLoader()
                    .start(
                            ContinueButtonClient.serverAddress,
                            () -> this.client.setScreen(new TitleScreen())
                    );

            return;
        }

        var server = continueButtonContinued$serverInfo;
        if (server == null || ContinueButtonClient.serverAddress.isEmpty()) {
            this.client.setScreen(new MultiplayerScreen(new TitleScreen()));
            return;
        }

        var serverAddress = ServerAddress.parse(ContinueButtonClient.serverAddress);
        ConnectScreen.connect(
                new MultiplayerScreen(new TitleScreen()),
                this.client,
                serverAddress,
                server,
                true,
                null
        );
    }

    @Inject(
            at = @At("HEAD"),
            method = "init()V"
    )
    private void continueButtonContinued$initAtHead(CallbackInfo info) {
        continueButtonContinued$firstRender = true;
        continueButtonContinued$serverInfo = null;
    }

    @Inject(
            at = @At("TAIL"),
            method = "init()V"
    )
    private void continueButtonContinued$adjustVanillaButtons(CallbackInfo info) {
        for (var button : Screens.getButtons(this)) {
            if (button.visible && !button.getMessage()
                    .equals(Text.translatable(ContinueButtonConstants.TRANSLATION_CONTINUE_BUTTON_TITLE))) {
                button.setX(this.width / 2 + 2);
                button.setWidth(98);
                break;
            }
        }
    }

    @Inject(
            at = @At("HEAD"),
            method = "render"
    )
    private void continueButtonContinued$renderAtHead(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        if (continueButtonContinued$firstRender) {
            continueButtonContinued$firstRender = false;
            continueButtonContinued$refreshLastServerInBackground();
        }
    }

    @Unique
    private void continueButtonContinued$refreshLastServerInBackground() {
        if (ContinueButtonClient.lastLocal
                || ContinueButtonClient.serverAddress.isEmpty()
                || this.client == null) {
            return;
        }

        if (continueButtonContinued$serverLookupThread != null
                && continueButtonContinued$serverLookupThread.isAlive()) {
            return;
        }

        continueButtonContinued$serverLookupThread = new Thread(() -> {
            var serverInList = continueButtonContinued$findServerInList();

            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            if (serverInList == null) {
                ContinueButtonClient.clearSavedTarget();
                return;
            }

            continueButtonContinued$serverInfo = serverInList;
            ContinueButtonClient.lastLocal = false;
            ContinueButtonClient.serverName = serverInList.name == null
                    ? ""
                    : serverInList.name;
            ContinueButtonClient.serverAddress = serverInList.address == null
                    ? ""
                    : serverInList.address;
            ContinueButtonClient.saveConfig();

            continueButtonContinued$pingServer(serverInList);
        }, "Continue Button Continued Server Lookup");

        continueButtonContinued$serverLookupThread.setDaemon(true);
        continueButtonContinued$serverLookupThread.start();
    }

    @Unique
    private ServerInfo continueButtonContinued$findServerInList() {
        var serverList = new ServerList(this.client);
        serverList.loadFile();

        var exactMatch = serverList.get(ContinueButtonClient.serverAddress);
        if (exactMatch != null) {
            return exactMatch;
        }

        return IntStream.range(0, serverList.size())
                .mapToObj(serverList::get)
                .filter(entry ->
                        entry != null
                                && entry.address != null
                                && entry.address.equalsIgnoreCase(ContinueButtonClient.serverAddress))
                .findFirst()
                .orElse(null);
    }

    @Unique
    private void continueButtonContinued$pingServer(ServerInfo server) {
        server.label = Text.translatable("multiplayer.status.pinging");

        try {
            continueButtonContinued$serverListPinger.add(
                    server,
                    () -> {
                    },
                    () -> {
                    },
                    NetworkingBackend.remote(false)
            );
        } catch (Exception exception) {
            ContinueButtonClient.LOGGER.warn("Failed to ping last server {}", server.address, exception);
        }
    }

    @Inject(
            at = @At("TAIL"),
            method = "render"
    )
    private void continueButtonContinued$renderAtTail(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        if (continueButtonContinued$continueButton == null
                || !continueButtonContinued$continueButton.isHovered()) {
            return;
        }

        if (ContinueButtonClient.lastLocal
                || continueButtonContinued$serverInfo == null
                || continueButtonContinued$serverInfo.label == null) {
            return;
        }

        var tooltipLines = new ArrayList<>(this.client.textRenderer.wrapLines(continueButtonContinued$serverInfo.label, 270));
        tooltipLines.addFirst(
                Text.literal(continueButtonContinued$serverInfo.name)
                        .formatted(Formatting.GRAY)
                        .asOrderedText()
        );
        context.drawOrderedTooltip(this.textRenderer, tooltipLines, mouseX, mouseY);
    }

    @Inject(
            at = @At("RETURN"),
            method = "tick()V"
    )
    private void continueButtonContinued$tick(CallbackInfo info) {
        continueButtonContinued$serverListPinger.tick();
    }

    @Inject(
            at = @At("RETURN"),
            method = "removed()V"
    )
    private void continueButtonContinued$removed(CallbackInfo info) {
        continueButtonContinued$serverListPinger.cancel();
        if (continueButtonContinued$serverLookupThread != null) {
            continueButtonContinued$serverLookupThread.interrupt();
            continueButtonContinued$serverLookupThread = null;
        }
    }

}
