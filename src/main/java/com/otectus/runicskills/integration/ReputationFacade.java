package com.otectus.runicskills.integration;

import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * The standing values Runic Skills reads, with no MCA: Reputation type anywhere in its signature.
 *
 * <p>{@code McaReputationIntegration} is the only class in the mod that names
 * {@code dev.otectus.mcareputation.*}, and it is loaded reflectively through
 * {@code RunicSkills.tryLoadIntegration} — so it must never be referenced from code that runs on a
 * server without Reputation installed. Everything else (today: {@code StandingTierCondition})
 * therefore goes through this facade, which holds a nullable provider the integration installs on
 * itself at construction and which stays {@code null} on every other install.
 *
 * <p>While no provider is installed every accessor answers {@link #ABSENT_TIER_INDEX}, i.e. the
 * bottom of the ladder — a title requiring standing is simply never earned, rather than the
 * condition failing loudly on a pack that does not run Reputation.
 */
public final class ReputationFacade {

    /** What every accessor returns while no provider is installed: the bottom tier. */
    public static final int ABSENT_TIER_INDEX = 0;

    /** Implemented by the integration class; its methods may name Reputation types, this cannot. */
    public interface Provider {

        /** Highest tier index across every community the player has a record with. */
        int bestTierIndex(ServerPlayer player);

        /** Tier index with the village the player is standing in, or the bottom tier outside one. */
        int tierIndexHere(ServerPlayer player);
    }

    private static volatile @Nullable Provider provider;

    private ReputationFacade() {}

    /** Installed once, by the integration, only after its API-version check passes. */
    public static void install(Provider installed) {
        provider = installed;
    }

    /** True once the Reputation integration has installed itself. */
    public static boolean isActive() {
        return provider != null;
    }

    public static int bestTierIndex(ServerPlayer player) {
        Provider installed = provider;
        return installed == null ? ABSENT_TIER_INDEX : installed.bestTierIndex(player);
    }

    public static int tierIndexHere(ServerPlayer player) {
        Provider installed = provider;
        return installed == null ? ABSENT_TIER_INDEX : installed.tierIndexHere(player);
    }
}
