package com.otectus.runicskills.client.tooltip;

import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.registry.RegistryPerks;
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
        SkillCapability localCap = SkillCapability.getLocal();

        // Three states, not two. canPerk() is false both for a perk the player cannot have yet and
        // for one they have unlocked but never switched on, and rendering a single red "Perk
        // Disabled" for both told a player at Tinkering 10 that a level-2 perk was unavailable —
        // the state that actually applied was "unspent", and nothing on the tooltip said so.
        boolean perkActive = perk.canPerk();
        boolean unavailable = perk.requiredLevel < 1 || RegistryPerks.isDisabled(perk);
        boolean levelMet = !unavailable && perk.getToggle();
        int playerLevel = localCap != null ? localCap.getSkillLevel(perk.getSkill()) : 0;

        int currentRank = perkActive ? perk.getPlayerRank() : 0;

        // Rank 0 used to render as "I" here (Math.max(1, currentRank)) while the rank line below
        // rendered "-", so one tooltip gave two answers for the same perk. An unspent perk now
        // carries no numeral at all.
        list.add(Component.translatable("tooltip.perk.title").append(Component.translatable(perk.getKey()))
                .append(perk.getMaxRank() > 1 && currentRank > 0
                        ? Component.literal(" " + Utils.intToRoman(currentRank)).withStyle(ChatFormatting.LIGHT_PURPLE)
                        : Component.empty())
                .withStyle(ChatFormatting.AQUA));

        String statusKey;
        ChatFormatting statusColour;
        if (perkActive) {
            statusKey = "tooltip.perk.description.on";
            statusColour = ChatFormatting.GREEN;
        } else if (unavailable) {
            statusKey = "tooltip.perk.description.off";
            statusColour = ChatFormatting.RED;
        } else if (levelMet) {
            // The case the player is overwhelmingly likely to be looking at: earned, not yet spent.
            statusKey = "tooltip.perk.description.unspent";
            statusColour = ChatFormatting.YELLOW;
        } else {
            statusKey = "tooltip.perk.description.locked";
            statusColour = ChatFormatting.RED;
        }
        list.add(Component.translatable(statusKey).withStyle(statusColour));

        // Active-perk cap feedback. Only shown when a cap is actually in effect (flat maxActivePerks
        // and/or the scaled perksPerGlobalLevel, scaled by EARNED global level). effectivePerkCap
        // returns 0 when unlimited.
        if (localCap != null) {
            int effectiveCap = RegistryPerks.effectivePerkCap(localCap);
            if (effectiveCap > 0) {
                int active = RegistryPerks.countEnabledPerks(localCap);
                list.add(Component.translatable("tooltip.perk.active_cap", active, effectiveCap)
                        .withStyle(active >= effectiveCap ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
                if (active > effectiveCap) {
                    // Over budget (e.g. config lowered): perk activation is frozen until respec.
                    list.add(Component.translatable("tooltip.perk.over_budget").withStyle(ChatFormatting.RED));
                } else {
                    // When the EARNED-global-level scaled cap is the binding, still-growing constraint,
                    // show the earned global level that unlocks the next perk slot.
                    com.otectus.runicskills.handler.HandlerCommonConfig cfg =
                            com.otectus.runicskills.handler.HandlerCommonConfig.HANDLER.instance();
                    float ratio = cfg.perksPerGlobalLevel;
                    boolean flatBinds = cfg.maxActivePerks > 0 && effectiveCap >= cfg.maxActivePerks;
                    boolean ceilingBinds = cfg.maxPerkBudgetCap > 0 && effectiveCap >= cfg.maxPerkBudgetCap;
                    if (ratio > 0f && !flatBinds && !ceilingBinds) {
                        int nextThreshold = (int) Math.ceil((effectiveCap + 1) / (double) ratio);
                        list.add(Component.translatable("tooltip.perk.next_slot", nextThreshold)
                                .withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
            }
        }
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
                // Whether the requirement is satisfied was never stated, so "Available at level 2"
                // read as a pending gate to a player who had passed it eight levels ago.
                list.add(Component.literal(" ").append(Component.translatable(
                        levelMet ? "tooltip.perk.description.requirement_met"
                                 : "tooltip.perk.description.requirement_unmet",
                        String.valueOf(playerLevel)))
                        .withStyle(levelMet ? ChatFormatting.GREEN : ChatFormatting.RED));
            } else {
                list.add(Component.translatable("tooltip.perk.description.off").withStyle(ChatFormatting.RED));
            }

            if (perk.getMaxRank() > 1) {
                list.add(Component.empty());
                String currentRoman = currentRank > 0 ? Utils.intToRoman(currentRank) : "-";
                String maxRoman = Utils.intToRoman(perk.getMaxRank());
                list.add(Component.translatable("tooltip.perk.rank", currentRoman, maxRoman).withStyle(ChatFormatting.LIGHT_PURPLE));
                if (currentRank < perk.getMaxRank()) {
                    int nextLevel = perk.getLevelForRank(currentRank + 1);
                    list.add(Component.translatable("tooltip.perk.next_rank", nextLevel).withStyle(ChatFormatting.GRAY));
                }
            }
        } else {
            list.add(Component.translatable("tooltip.general.description.more_information").withStyle(ChatFormatting.YELLOW));
        }
        if (HandlerConfigClient.showPerkModName.get()) {
            list.add(Component.literal(Utils.getModName(perk.getMod())).withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.ITALIC));
        }
        // Clamp wide translation strings so the tooltip doesn't overflow at GUI scale 4 / 4K.
        return TooltipWrap.wrap(list, 200);
    }
}
