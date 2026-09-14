package com.otectus.runicskills.validation;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Opt-in client smoke check in the separate validation jar, never in release artifacts. */
@Mod.EventBusSubscriber(modid = "runicskills_validation", value = Dist.CLIENT)
public final class ClientPackChecks {
    private static int ticks;
    private static boolean reported;

    private ClientPackChecks() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("runicskills.clientValidation") || Boolean.getBoolean("runicskills.tabValidation") || reported
                || event.phase != TickEvent.Phase.END) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (++ticks < 400) return;
        reported = true;
        var logger = com.otectus.runicskills.RunicSkills.getLOGGER();
        try {
            if (ModList.get().isLoaded("roaring")) {
                var roaring = Class.forName("net.mcreator.roaring.entity.RoaringKnightEntity");
                var kaizo = Class.forName("com.example.chaotic_world_content.roaring.kaizo.KaizoKnightEntity");
                if (!roaring.isAssignableFrom(kaizo)) throw new IllegalStateException("Kaizo superclass mismatch");
            }
            logger.info("RUNIC_CLIENT_VALIDATION PASS world_ticked_400 roaring={}", ModList.get().isLoaded("roaring"));
        } catch (Throwable failure) {
            logger.error("RUNIC_CLIENT_VALIDATION FAIL native_entity_linkage", failure);
        } finally {
            minecraft.stop();
        }
    }
}
