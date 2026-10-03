package top.likoslupus.continuebuttoncontinued.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import top.likoslupus.continuebuttoncontinued.ContinueButtonClient;
import top.likoslupus.continuebuttoncontinued.ContinueButtonConstants;

@Mod(
        value = ContinueButtonConstants.MOD_ID,
        dist = Dist.CLIENT
)
public final class ContinueButtonNeoForge {

    public ContinueButtonNeoForge() {
        ContinueButtonClient.init();
    }

}
