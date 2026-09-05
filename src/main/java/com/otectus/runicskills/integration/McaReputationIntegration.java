package com.otectus.runicskills.integration;

import com.mojang.logging.LogUtils;
import com.otectus.runicskills.common.progression.ProgressionService;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import dev.otectus.mcareputation.api.McaReputationApi;
import dev.otectus.mcareputation.api.event.ReputationTierChangedEvent;
import dev.otectus.mcareputation.community.CommunityKey;
import dev.otectus.mcareputation.community.CommunityResolver;
import dev.otectus.mcareputation.reputation.ReputationTiers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * MCA: Reputation integration — the only class in this mod that names
 * {@code dev.otectus.mcareputation} types.
 *
 * <p>Loaded reflectively via {@code RunicSkills.tryLoadIntegration("mcareputation", …)} so the JVM
 * never resolves a Reputation type when the mod is absent. Everything else in Runic Skills reads
 * standing through {@link ReputationFacade}, whose provider this class installs on itself — and
 * only after {@link McaReputationApi#getApiVersion()} reports the surface this build was written
 * against. On any other version the instance disables itself: the facade keeps answering the
 * bottom tier and the event handler returns immediately, rather than risking a
 * {@code NoSuchMethodError} in gameplay.
 *
 * <p>Reward side: a first-time upward tier crossing grants skill levels. Runic Skills has no skill
 * XP pool — {@code ProgressionService} moves levels — so "grant XP" is expressed as levels, and the
 * default skill id is blank so no pack gains free progression by installing both mods.
 */
public class McaReputationIntegration implements ReputationFacade.Provider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "mcareputation";

    /** The Reputation API surface this build compiles against; anything else disables the bridge. */
    private static final int SUPPORTED_API_VERSION = 1;

    private final boolean active;

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public McaReputationIntegration() {
        boolean usable;
        try {
            usable = McaReputationApi.getApiVersion() == SUPPORTED_API_VERSION;
            if (!usable) {
                LOGGER.warn("MCA: Reputation reports API version {}, this build targets {}; the standing "
                        + "bridge is disabled.", McaReputationApi.getApiVersion(), SUPPORTED_API_VERSION);
            }
        } catch (Throwable t) {
            // Same reasoning as every other optional bridge: an upstream that moved must degrade to
            // "standing does not gate titles", never to "the mod does not load".
            LOGGER.warn("MCA: Reputation API could not be queried; the standing bridge is disabled.", t);
            usable = false;
        }
        this.active = usable;
        if (active) {
            ReputationFacade.install(this);
            LOGGER.debug("MCA: Reputation integration loaded; standing conditions and tier rewards active.");
        }
    }

    /** Live master toggle, read at every entry point so /skillsreload takes effect both ways. */
    private boolean isActive() {
        return active && isModLoaded() && HandlerCommonConfig.HANDLER.instance().enableMcaReputationIntegration;
    }

    // ------------------------------------------------------------------
    // Reward: a new personal-best tier with a community
    // ------------------------------------------------------------------

    /**
     * Reputation posts this on every tier crossing in both directions; only an upward crossing that
     * beats the player's recorded high-water mark ({@code firstTime}) is a milestone, so re-earning
     * a tier the player has already held never pays out again. No bookkeeping of our own is needed
     * because the event carries that flag.
     */
    @SubscribeEvent
    public void onTierChanged(ReputationTierChangedEvent event) {
        if (!isActive()) return;
        if (!event.upward() || !event.firstTime()) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        List<String> skillIds = config.mcaReputationTierSkillIds;
        int levels = config.mcaReputationTierSkillLevels;
        if (skillIds == null || skillIds.isEmpty() || levels <= 0) return;

        Optional<ServerPlayer> player = event.player();
        if (player.isEmpty()) return;

        for (String skillId : skillIds) {
            if (skillId == null || skillId.isBlank()) continue;
            Skill skill = RegistrySkills.getSkill(skillId.trim().toLowerCase(Locale.ROOT));
            if (skill == null) {
                LOGGER.error(">> Unknown skill '{}' in mcaReputationTierSkillIds; no standing reward granted.", skillId);
                continue;
            }
            // COMMAND is the closest existing cause: an external system, not a purchase or a respec.
            ProgressionService.addSkillLevels(player.get(), skill, levels, ProgressionService.Cause.COMMAND);
        }
    }

    // ------------------------------------------------------------------
    // Reads, for ReputationFacade
    // ------------------------------------------------------------------

    @Override
    public int bestTierIndex(ServerPlayer player) {
        if (!isActive() || player == null) return ReputationFacade.ABSENT_TIER_INDEX;
        MinecraftServer server = player.getServer();
        if (server == null) return ReputationFacade.ABSENT_TIER_INDEX;
        try {
            int best = ReputationFacade.ABSENT_TIER_INDEX;
            for (CommunityKey community : McaReputationApi.knownCommunities(server, player.getUUID())) {
                best = Math.max(best, tierIndex(server, player.getUUID(), community));
            }
            return best;
        } catch (Throwable t) {
            LOGGER.debug("MCA: Reputation best-tier lookup failed; treating as the bottom tier.", t);
            return ReputationFacade.ABSENT_TIER_INDEX;
        }
    }

    @Override
    public int tierIndexHere(ServerPlayer player) {
        if (!isActive() || player == null) return ReputationFacade.ABSENT_TIER_INDEX;
        MinecraftServer server = player.getServer();
        if (server == null) return ReputationFacade.ABSENT_TIER_INDEX;
        try {
            return CommunityResolver.resolveNearest(player.serverLevel(), player.blockPosition())
                    .map(community -> tierIndex(server, player.getUUID(), community))
                    .orElse(ReputationFacade.ABSENT_TIER_INDEX);
        } catch (Throwable t) {
            LOGGER.debug("MCA: Reputation nearest-village lookup failed; treating as the bottom tier.", t);
            return ReputationFacade.ABSENT_TIER_INDEX;
        }
    }

    /**
     * Ladder position of the player's current tier with one community. Reputation's own API answers
     * with a tier id; the index comes from the default ladder, and an id that ladder does not know
     * (a datapack that renamed its tiers) reads as the bottom rather than as -1.
     */
    private static int tierIndex(MinecraftServer server, UUID player, CommunityKey community) {
        String tierId = McaReputationApi.getTierId(server, player, community);
        return Math.max(ReputationFacade.ABSENT_TIER_INDEX, ReputationTiers.getDefault().indexOf(tierId));
    }
}
