package com.otectus.runicskills.integration.simplyswords;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.List;

/** One shared presentation builder; server tests exercise the same native reads as the client. */
public final class ResonantReading {
    private ResonantReading() {}
    public static boolean eligible(Player player, ItemStack stack) {
        var id = stack == null || stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && "simplyswords".equals(id.getNamespace()) && player != null && !(player instanceof FakePlayer)
                && stack.getItem() instanceof net.minecraft.world.item.SwordItem
                && player.isAlive() && RegistryPerks.SS_RESONANT_READING.get().isEnabled(player);
    }
    public static List<Component> details(Player player, ItemStack stack) {
        return details(player, stack, true);
    }
    public static List<Component> details(Player player, ItemStack stack, boolean includeRequirements) {
        if (!eligible(player, stack)) return List.of();
        var result = SwordsInspection.read(stack);
        if (result.isEmpty()) return List.of(line("unavailable"));
        var data = result.get();
        List<Component> lines = new ArrayList<>();
        lines.add(line("title").copy().withStyle(ChatFormatting.AQUA));
        lines.add(data.progression() ? line("awakening", data.level(), data.unlockLevel(), state(data.abilityUnlocked()))
                : line("no_progression"));
        if (!data.socketData()) lines.add(line("socket_unknown"));
        else {
            lines.add(line("runic_socket", slot(data.runicSlot(), data.runicPower(), data.runicRecognized())));
            lines.add(line("nether_socket", slot(data.netherSlot(), data.netherPower(), data.netherRecognized())));
            lines.add(line("gem_active", state(data.gemsActive())));
        }
        lines.add(data.implicit() == null ? line("implicit_unknown")
                : line("implicit", data.implicit().toString(), data.weaponType().toString(), data.implicitValue()));
        // Existing base tooltips already show numeric item requirements. Add only unmet facts here
        // when that tooltip is not present (e.g. an integration's standalone inspection screen).
        var cap = SkillCapability.get(player);
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (includeRequirements && cap != null && id != null && HandlerCommonConfig.HANDLER.instance().enableItemLocks) {
            var requirements = HandlerSkill.getValue(id.toString());
            if (requirements != null) for (var requirement : requirements) {
                var skill = requirement.getSkill();
                if (skill != null && cap.getSkillLevel(skill) < requirement.getSkillLvl() && lines.size() < 18)
                    lines.add(line("unmet", Component.translatable(skill.getKey()), requirement.getSkillLvl(), cap.getSkillLevel(skill))
                            .copy().withStyle(ChatFormatting.RED));
            }
        }
        return List.copyOf(lines);
    }
    private static Component slot(boolean exists, net.minecraft.resources.ResourceLocation power, boolean recognized) {
        if (!exists) return power == null ? line("closed") : line("closed_stored", power.toString());
        return power == null ? line("empty") : !recognized ? line("unknown_power", power.toString()) : Component.literal(power.toString());
    }
    private static Component state(boolean value) { return line(value ? "yes" : "no"); }
    public static Component line(String key, Object... args) { return Component.translatable("tooltip.runicskills.ss_reading." + key, args); }
}
