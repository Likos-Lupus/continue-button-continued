package top.likoslupus.continuebuttoncontinued.fabric;

import net.fabricmc.api.ClientModInitializer;
import top.likoslupus.continuebuttoncontinued.ContinueButtonClient;

public final class ContinueButtonFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ContinueButtonClient.init();
    }

}
