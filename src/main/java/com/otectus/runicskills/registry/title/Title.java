package com.otectus.runicskills.registry.title;

import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.network.packet.client.TitleOverlayCP;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class Title {
    private final ResourceLocation key;
    public final boolean Requirement;
    public final boolean HideRequirements;

    public Title(ResourceLocation key, boolean requirement, boolean hideRequirements) {
        this.key = key;
        this.Requirement = requirement;
        this.HideRequirements = hideRequirements;
    }

    public Title get() {
        return this;
    }


    public String getMod() {
        return this.key.getNamespace();
    }

    public String getName() {
        return this.key.getPath();
    }

    public String getKey() {
        return "title." + this.key.toLanguageKey();
    }

    public String getDescription() {
        return getKey() + ".description";
    }

    public boolean getRequirement() {
        return SkillCapability.getLocal().getLockTitle(this);
    }

    public boolean getRequirement(Player player) {
        return SkillCapability.get(player).getLockTitle(this);
    }

    public void setRequirement(ServerPlayer serverPlayer, boolean check) {
        if (!getRequirement(serverPlayer) && check) {
            TitleOverlayCP.send(serverPlayer, this);
            SkillCapability.get(serverPlayer).setUnlockTitle(this, true);
            SyncSkillCapabilityCP.send(serverPlayer);
        }
    }

    public List<Component> tooltip() {
        List<Component> list = new ArrayList<>();
        list.add(Component.empty().append(Component.translatable("title.runicskills.requirement_description").withStyle(ChatFormatting.GOLD)).append(Component.translatable(getDescription()).withStyle(ChatFormatting.GRAY)));
        if (HandlerConfigClient.showTitleModName.get())
            list.add(Component.literal(Utils.getModName(getMod())).withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.ITALIC));
        return list;
    }
}


