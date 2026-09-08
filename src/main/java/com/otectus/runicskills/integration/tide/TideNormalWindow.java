package com.otectus.runicskills.integration.tide;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import java.lang.reflect.Method;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Changes the native server packet's normal area. The independent 0.1 perfect threshold is untouched. */
public final class TideNormalWindow {
    private static Method externalProvider;
    private TideNormalWindow() {}
    public static void probe() throws ReflectiveOperationException {
        externalProvider = Class.forName("com.li64.tide.compat.CompatHelper").getMethod("useStarcatcherMinigame");
    }
    public static Result availability(boolean power) {
        var result = IntegrationRuntime.check(IntegrationModule.TIDE, power ? Feature.POWERS : Feature.PERKS, Capability.NORMAL_WINDOW);
        if (!result.available()) return result;
        result = IntegrationRuntime.check(IntegrationModule.TIDE, Feature.MINIGAME, Capability.NORMAL_WINDOW);
        if (!result.available()) return result;
        try {
            return externalProvider != null && !(boolean) externalProvider.invoke(null) ? result
                    : new Result(State.UNAVAILABLE, "Normal-window assistance requires Tide's native minigame provider.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new Result(State.UNAVAILABLE, "Native minigame provider could not be resolved.");
        }
    }
    public static float area(ServerPlayer player, float nativeArea) {
        if (player instanceof FakePlayer || !player.isAlive()) return nativeArea;
        try {
            var hook = TideNativeAccess.active(player);
            var cap = SkillCapability.get(player);
            if (hook == null || cap == null || !cap.canUseItemSilent(player, TideNativeAccess.rod(hook))) return nativeArea;
            double extra = TideEmberAndStar.window(player, hook);
            if (RegistryPerks.TIDE_SURE_LINE.get().isEnabled(player))
                extra += HandlerCommonConfig.HANDLER.instance().tideSureLineHundredths / 100.0;
            return (float) IntegrationLimits.normalWindow(nativeArea, extra);
        } catch (RuntimeException e) { return nativeArea; }
    }
}
