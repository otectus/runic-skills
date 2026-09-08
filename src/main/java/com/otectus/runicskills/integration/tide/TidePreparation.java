package com.otectus.runicskills.integration.tide;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

public final class TidePreparation {
    private TidePreparation() {}
    public static boolean available() {
        return IntegrationRuntime.check(IntegrationModule.TIDE, Feature.PERKS, Capability.CAST_PREPARATION).available();
    }
    public static int duration(int nativeTicks, ItemStack rod, LivingEntity user) {
        if (!(user instanceof Player player) || player instanceof FakePlayer) return nativeTicks;
        // Tide also replaces minecraft:fishing_rod. Native class provenance owns this
        // action; an output/item namespace alone cannot identify the fishing subsystem.
        if (rod.isEmpty() || !TideFishingOrigin.isNativeRod(rod.getItem())) return nativeTicks;
        // Both native client cast-bar prediction and server release use this method and server config.
        double reduction = TidePowers.preparation(player) + TideManyWaters.preparation(player) + TideKeeperOfTheBanks.preparation(player) + TideEmberAndStar.preparation(player);
        if (available() && RegistryPerks.TIDE_MEASURED_CAST.get().isEnabled(player))
            reduction += HandlerCommonConfig.HANDLER.instance().tideMeasuredCastPercent / 100.0;
        return reduction <= 0 ? nativeTicks : IntegrationLimits.preparation(nativeTicks, reduction);
    }
}
