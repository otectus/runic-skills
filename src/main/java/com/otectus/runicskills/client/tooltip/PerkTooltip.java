package com.otectus.runicskills.client.tooltip;

import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class PerkTooltip {

    private PerkTooltip() {}

    public static List<Component> tooltip(Perk perk) {
        List<Component> list = new ArrayList<>();
        int currentRank = perk.canPerk() ? perk.getPlayerRank() : 0;

        list.add(Component.translatable("tooltip.perk.title").append(Component.translatable(perk.getKey()))
                .append(perk.getMaxRank() > 1 ? Component.literal(" " + Utils.intToRoman(Math.max(1, currentRank))).withStyle(ChatFormatting.LIGHT_PURPLE) : Component.empty())
                .withStyle(ChatFormatting.AQUA));
        list.add(Component.translatable("tooltip.perk.description." + (perk.canPerk() ? "on" : "off")).withStyle(perk.canPerk() ? ChatFormatting.GREEN : ChatFormatting.RED));
        list.add(Component.empty());
        if (Screen.hasShiftDown()) {
            list.add(Component.empty()
                    .append(Component.translatable(perk.getKey()).withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.UNDERLINE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(perk.getMutableDescription(perk.getDescription()).withStyle(ChatFormatting.GRAY)));
            list.add(Component.empty());
            list.add(Component.translatable("tooltip.perk.description.level_requirement").withStyle(ChatFormatting.DARK_PURPLE));
            if (perk.requiredLevel > 0) {
                list.add(Component.literal(" ").append(Component.translatable("tooltip.perk.description.available", Component.literal(String.valueOf(perk.getLvl())).withStyle(ChatFormatting.GREEN))).withStyle(ChatFormatting.DARK_AQUA));
            } else {
                list.add(Component.translatable("tooltip.perk.description.off").withStyle(ChatFormatting.RED));
            }

            if (perk.getMaxRank() > 1) {
                list.add(Component.empty());
                list.add(Component.literal("Rank: " + (currentRank > 0 ? Utils.intToRoman(currentRank) : "-") + "/" + Utils.intToRoman(perk.getMaxRank())).withStyle(ChatFormatting.LIGHT_PURPLE));
                if (currentRank < perk.getMaxRank()) {
                    int nextLevel = perk.getLevelForRank(currentRank + 1);
                    list.add(Component.literal(" Next rank at level " + nextLevel).withStyle(ChatFormatting.GRAY));
                }
            }
        } else {
            list.add(Component.translatable("tooltip.general.description.more_information").withStyle(ChatFormatting.YELLOW));
        }
        if (HandlerConfigClient.showPerkModName.get()) {
            list.add(Component.literal(Utils.getModName(perk.getMod())).withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.ITALIC));
        }
        return list;
    }
}
