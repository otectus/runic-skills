package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.common.util.PacketBounds;
import io.netty.handler.codec.DecoderException;
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
        this.skill = buffer.readUtf(PacketBounds.MAX_CONTENT_ID_CHARS);
        if (!PacketBounds.isContentIdValid(this.skill)) {
            throw new DecoderException("SkillLevelUpSP: malformed skill id");
        }
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.skill, PacketBounds.MAX_CONTENT_ID_CHARS);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        // Admission control runs on the network thread, BEFORE enqueueWork. Rate limiting used
        // to happen inside the scheduled task, so a flooding client still allocated a lambda and
        // queued a main-thread task for every packet — the limiter discarded the work only after
        // the server had already paid to schedule it (RS-150).
        ServerPlayer sender = context.getSender();
        if (sender == null || !PacketRateLimiter.allow(sender, "skill_level_up", 2)) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
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

                // The global level cap was enforced ONLY in the client GUI, so a patched or
                // modified client could keep sending this packet and walk straight past a limit
                // operators explicitly tune with /globallimit. Because the perk budget scales from
                // earned global level, exceeding it also granted extra perk slots — the cap is a
                // balance rule, and a rule only the client enforces is not a rule (RS-008).
                int globalCap = HandlerCommonConfig.HANDLER.instance().playersMaxGlobalLevel;
                if (globalCap > 0 && capability.getGlobalLevel() >= globalCap) {
                    SyncSkillCapabilityCP.send(player);
                    return;
                }

                int requiredPoints = requiredPoints(player, skillPlayer, skillLevel);

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

                // One mutation path for the purchase and for every command form (RS10-013).
                // ProgressionService clamps against the live configuration, fires the public
                // SkillLevelUpEvent — which a subscriber may still cancel — and reconciles
                // attributes, titles and quests once before syncing.
                com.otectus.runicskills.common.progression.ProgressionService.Outcome outcome =
                        com.otectus.runicskills.common.progression.ProgressionService.addSkillLevels(
                                player, skillPlayer, 1,
                                com.otectus.runicskills.common.progression.ProgressionService.Cause.PURCHASE);
                if (!outcome.changed()) {
                    // Cancelled, capped, or otherwise refused: charge nothing, and resync so the
                    // client stops displaying the level it optimistically drew (RS-156).
                    SyncSkillCapabilityCP.send(player);
                    return;
                }
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
    public static void addPlayerXP(Player player, int amount) {
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

    /**
     * XP-point cost to raise {@code skill} by one, after the perks that make skill XP go further.
     *
     * <p>Enlightenment and Quick Learner both promise increased <em>skill</em> XP gain. This mod has
     * no separate skill-XP pool — skill levels are bought with vanilla XP points — so the faithful
     * reading of "your skill XP goes further" is that each level costs proportionally less. Both
     * used to add to {@code LivingExperienceDropEvent} instead, which is a general mob-XP bonus:
     * it also fed enchanting, anvils and mending, and did nothing whatever for a player levelling
     * from XP they had already banked (RS10-004).
     *
     * <p>Grand Sage is here for the same reason and takes the skill argument this method exists to
     * carry. "All wisdom-based bonuses are amplified" named no mechanic the game has: there is no
     * central multiplier every Wisdom perk passes through, and inventing one that some perk sites
     * consulted and others did not would have been a bonus the player could not predict. What the
     * Wisdom tree does have is a price, so the perk lowers it — for Wisdom alone, which is what
     * makes it different from the two general discounts above.
     *
     * <p>Client and server call this same method, so the number on the button is the number
     * charged.
     */
    public static int requiredPoints(Player player, Skill skill, int skillLevel) {
        int base = requiredPoints(skillLevel);
        if (player == null) return base;

        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        double discount = 0.0;
        if (RegistryPerks.ENLIGHTENMENT != null && RegistryPerks.ENLIGHTENMENT.get().isEnabled(player)) {
            discount += cfg.enlightenmentPercent / 100.0;
        }
        if (RegistryPerks.QUICK_LEARNER != null && RegistryPerks.QUICK_LEARNER.get().isEnabled(player)) {
            discount += cfg.quickLearnerPercent / 100.0;
        }
        if (skill != null && RegistrySkills.WISDOM.isPresent() && skill == RegistrySkills.WISDOM.get()
                && RegistryPerks.GRAND_SAGE != null && RegistryPerks.GRAND_SAGE.get().isEnabled(player)) {
            discount += cfg.grandSagePercent / 100.0;
        }
        if (discount <= 0.0) return base;

        // Never free, and never below the configured floor: a discount that could reach 100% would
        // make every skill instantly maxable, which no configuration should grant by accident.
        discount = Math.min(0.90, discount);
        return Math.max(cfg.skillLevelUpMinCost, (int) Math.ceil(base * (1.0 - discount)));
    }

    public static void send(Skill skill) {
        ServerNetworking.sendToServer(new SkillLevelUpSP(skill));
    }
}


