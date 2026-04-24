package com.otectus.runicskills.client.event;

import com.otectus.runicskills.common.model.Skills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;


/**
 * registry CLIENT events into the forge mod bus.
 */
public class RegistryClientEvents {
    @SubscribeEvent
    public void onTooltipDisplay(ItemTooltipEvent event) {
        if ((Minecraft.getInstance()).player != null && (Minecraft.getInstance()).player.isAlive()) {
            List<Component> tooltips = event.getToolTip();
            ItemStack itemStack = event.getItemStack();

            ResourceLocation location = Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(itemStack.getItem()));
            List<Skills> list = HandlerSkill.getValue(location.toString());
            if (list != null && HandlerCommonConfig.HANDLER.instance().enableItemLocks) {
                // Capability may not be synced yet when a tooltip renders pre-join; treat
                // as "requirement not met" and colour red rather than NPE the tooltip.
                SkillCapability localCap = SkillCapability.getLocal();
                tooltips.add(Component.empty());
                tooltips.add(Component.translatable("tooltip.skill.item_requirement").withStyle(ChatFormatting.DARK_PURPLE));
                for (Skills skills : list) {
                    Skill skill = skills.getSkill();
                    if (skill != null) {
                        int level = localCap == null ? 0 : localCap.getSkillLevel(skill);
                        ChatFormatting colour = (level >= skills.getSkillLvl()) ? ChatFormatting.GREEN : ChatFormatting.RED;
                        tooltips.add(Component.translatable("tooltip.skill.item_requirements", Component.translatable(skill.getKey()), Component.literal(String.valueOf(skills.getSkillLvl())).withStyle(colour)));
                    }
                }
            }
        }
    }
}


