package com.otectus.runicskills.client.tooltip;

import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.registry.passive.Passive;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public final class PassiveTooltip {

    private PassiveTooltip() {}

    public static List<Component> tooltip(Passive passive) {
        DecimalFormat df = new DecimalFormat("0.##");
        String valuePerLevel = df.format(passive.getValue() / passive.levelsRequired.length);
        String valueActualLevel = df.format(passive.getValue() / passive.levelsRequired.length * passive.getLevel());
        String valueMaxLevel = df.format(passive.getValue());

        List<Component> list = new ArrayList<>();
        list.add(Component.translatable("tooltip.passive.title").append(Component.translatable(passive.getKey())).withStyle(ChatFormatting.GREEN));
        list.add(Component.translatable("tooltip.passive.description.passive_level", passive.getLevel(), passive.levelsRequired.length).withStyle(ChatFormatting.GRAY));
        list.add(Component.empty());
        if (Screen.hasShiftDown()) {
            list.add(Component.empty()
                    .append(Component.translatable(passive.getKey()).withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.UNDERLINE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable(passive.getDescription()).withStyle(ChatFormatting.GRAY)));
            list.add(Component.empty());
            list.add(Component.translatable("tooltip.passive.description.other_info").withStyle(ChatFormatting.GRAY));
            list.add(Component.literal(" ").append(Component.translatable("tooltip.passive.description.level", valuePerLevel)).withStyle(ChatFormatting.DARK_GREEN));
            list.add(Component.literal(" ").append(Component.translatable("tooltip.passive.description.actual_level", valueActualLevel)).withStyle(ChatFormatting.DARK_GREEN));
            list.add(Component.literal(" ").append(Component.translatable("tooltip.passive.description.max_level", valueMaxLevel)).withStyle(ChatFormatting.DARK_GREEN));
            list.add(Component.empty());
            list.add(Component.translatable("tooltip.passive.description.level_requirement").withStyle(ChatFormatting.DARK_PURPLE));
            if (passive.getLevel() < passive.levelsRequired.length) {
                list.add(Component.literal(" ").append(Component.translatable("tooltip.passive.description.passive_required", Component.translatable(passive.getSkill().getKey()).withStyle(ChatFormatting.GREEN),
                        Component.literal(String.valueOf(passive.getNextLevelUp())).withStyle(ChatFormatting.GREEN),
                        Component.literal(String.valueOf(passive.getLevel() + 1)).withStyle(ChatFormatting.GREEN))).withStyle(ChatFormatting.DARK_AQUA));
            } else {
                list.add(Component.literal(" ").append(Component.translatable("tooltip.passive.description.passive_max_level")).withStyle(ChatFormatting.DARK_AQUA));
            }
        } else {
            list.add(Component.translatable("tooltip.general.description.more_information").withStyle(ChatFormatting.YELLOW));
        }
        if (HandlerConfigClient.showPerkModName.get()) {
            list.add(Component.literal(Utils.getModName(passive.getMod())).withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.ITALIC));
        }

        return list;
    }
}
