package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.util.ExperienceMath;
import com.otectus.runicskills.event.SkillLevelUpEvent;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.quests.RunicQuestBridge;
import com.otectus.runicskills.network.PacketRateLimiter;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
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

                // At skillMaxLevel the storage clamp makes addSkillLevel a no-op, but without
                // this check the packet still consumed XP, fired SkillLevelUpEvent, and notified
                // the quest bridge for a level-up that never happened. Same cap the admin
                // command path enforces via its argument range.
                if (!com.otectus.runicskills.common.util.SkillLevelUpMath.canLevelUp(
                        skillLevel, HandlerCommonConfig.HANDLER.instance().skillMaxLevel)) {
                    SyncSkillCapabilityCP.send(player);
                    return;
                }

                int requiredPoints = requiredPoints(skillLevel);

                // XP points are the single authoritative currency. spendableXp derives the player's
                // real balance from experienceLevel + experienceProgress (getPlayerXP), never the
                // raw totalExperience field or the displayed level count — the OR-across-currencies
                // gate this replaces let a player with enough levels but too few points overspend
                // into negative XP.
                boolean canLevelUpSkill = com.otectus.runicskills.common.util.SkillLevelUpMath.canAfford(
                        player.isCreative(), getPlayerXP(player), requiredPoints);

                if (!canLevelUpSkill){
                    RunicSkills.getLOGGER().info("Received level up packet without the required EXP needed to level up, skipping packet...");
                    // Resync so a tampered/stale client can't stay in a misleading state.
                    SyncSkillCapabilityCP.send(player);
                    return;
                }

                // Fire public Forge event (since 1.2.0). Subscribers may cancel to abort
                // the level-up without consuming XP or syncing back to the client.
                if (MinecraftForge.EVENT_BUS.post(new SkillLevelUpEvent(player, skillPlayer, skillLevel, skillLevel + 1))) {
                    return;
                }

                capability.addSkillLevel(skillPlayer, 1);
                RunicQuestBridge.onSkillLevelChanged(player, skillPlayer, skillLevel, skillLevel + 1);
                SyncSkillCapabilityCP.send(player);
                if (!player.isCreative()) {
                    addPlayerXP(player, requiredPoints * -1);
                }
            }
        });
        context.setPacketHandled(true);
    }

    /** The player's current spendable XP-point balance (authoritative currency for level-up cost). */
    public static int getPlayerXP(Player player) {
        return ExperienceMath.spendableXp(player.experienceLevel, player.experienceProgress);
    }

    /**
     * Adds {@code amount} XP points (negative to spend), clamping the resulting total to a valid
     * non-negative state and recomputing experienceLevel/experienceProgress consistently so XP can
     * never go negative or desync.
     */
    public void addPlayerXP(Player player, int amount) {
        int experience = Math.max(0, getPlayerXP(player) + amount);
        player.totalExperience = experience;
        player.experienceLevel = ExperienceMath.getLevelForExperience(experience);
        player.experienceProgress = ExperienceMath.progressForTotal(experience, player.experienceLevel);
    }

    /** XP-point cost to raise a skill at {@code skillLevel} by one, in the currency the server spends. */
    public static int requiredPoints(int skillLevel) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        return ExperienceMath.requiredPoints(skillLevel, cfg.skillFirstCostLevel,
                cfg.skillLevelUpCostMultiplier, cfg.skillLevelUpMinCost);
    }

    public static void send(Skill skill) {
        ServerNetworking.sendToServer(new SkillLevelUpSP(skill));
    }
}


