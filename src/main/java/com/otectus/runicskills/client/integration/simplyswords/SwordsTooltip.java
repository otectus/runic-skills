package com.otectus.runicskills.client.integration.simplyswords;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.integration.simplyswords.ResonantReading;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, value = Dist.CLIENT)
public final class SwordsTooltip {
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        if (!ResonantReading.eligible(event.getEntity(), event.getItemStack())) return;
        if (Screen.hasShiftDown()) event.getToolTip().addAll(ResonantReading.details(event.getEntity(), event.getItemStack(), false));
        else event.getToolTip().add(ResonantReading.line("hint").copy().withStyle(ChatFormatting.DARK_AQUA));
    }
}
