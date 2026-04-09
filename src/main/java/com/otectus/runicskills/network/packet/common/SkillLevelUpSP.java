package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.PacketRateLimiter;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SkillLevelUpSP {
    private final String skill;

    public SkillLevelUpSP(Skill skill) {
        this.skill = skill.getName();
    }

    public SkillLevelUpSP(FriendlyByteBuf buffer) {
        this.skill = buffer.readUtf();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.skill);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                if (!PacketRateLimiter.allow(player, "skill_level_up", 2)) return;

                SkillCapability capability = SkillCapability.get(player);
                if (capability == null) return;

                Skill skillPlayer = RegistrySkills.getSkill(this.skill);
                if (skillPlayer == null) return;

                int skillLevel = capability.getSkillLevel(skillPlayer);

                boolean canLevelUpSkill = (player.isCreative()
                        || SkillLevelUpSP.requiredPoints(skillLevel) <= player.totalExperience
                        || SkillLevelUpSP.requiredExperienceLevels(skillLevel) <= player.experienceLevel);

                if (!canLevelUpSkill){
                    RunicSkills.getLOGGER().info("Received level up packet without the required EXP needed to level up, skipping packet...");
                    return;
                }

                int requiredPoints = requiredPoints(skillLevel);

                capability.addSkillLevel(skillPlayer, 1);
                SyncSkillCapabilityCP.send(player);
                if (!player.isCreative()) {
                    addPlayerXP(player, requiredPoints * -1);
                }
            }
        });
        context.setPacketHandled(true);
    }

    public static int getPlayerXP(Player player) {
        return (int)(getExperienceForLevel(player.experienceLevel) + (player.experienceProgress * player.getXpNeededForNextLevel()));
    }

    public static int xpBarCap(int level) {
        if (level >= 30)
            return 112 + (level - 30) * 9;

        if (level >= 15)
            return 37 + (level - 15) * 5;

        return 7 + level * 2;
    }

    public void addPlayerXP(Player player, int amount) {
        int experience = getPlayerXP(player) + amount;
        player.totalExperience = experience;
        player.experienceLevel = getLevelForExperience(experience);
        int expForLevel = getExperienceForLevel(player.experienceLevel);
        player.experienceProgress = (experience - expForLevel) / (float)player.getXpNeededForNextLevel();
    }

    public static int getLevelForExperience(int targetXp) {
        int level = 0;
        while (true) {
            final int xpToNextLevel = xpBarCap(level);
            if (targetXp < xpToNextLevel) return level;
            level++;
            targetXp -= xpToNextLevel;
        }
    }

    public static int requiredPoints(int skillLevel) {
        return getExperienceForLevel(skillLevel + HandlerCommonConfig.HANDLER.instance().skillFirstCostLevel - 1);
    }

    public static int requiredExperienceLevels(int skillLevel) {
        return skillLevel + HandlerCommonConfig.HANDLER.instance().skillFirstCostLevel - 1;
    }

    public static int getExperienceForLevel(int level) {
        if (level == 0) return 0;
        if (level <= 15) return sum(level, 7, 2);
        if (level <= 30) return 315 + sum(level - 15, 37, 5);
        return 1395 + sum(level - 30, 112, 9);
    }

    private static int sum(int n, int a0, int d) {
        return n * (2 * a0 + (n - 1) * d) / 2;
    }

    public static void send(Skill skill) {
        ServerNetworking.sendToServer(new SkillLevelUpSP(skill));
    }
}


