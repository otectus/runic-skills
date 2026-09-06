package com.otectus.runicskills.integration.tconstruct.addons;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The extension points the add-on adapters fill in, so nothing outside this package names them.
 *
 * <p>Exactly the shape {@code WearAvoidance} already uses for the same problem one layer down: the
 * caller — {@code TConstructPerkHandler}'s melee stage — must not reference a class that imports
 * TCIntegrations or Tinkers' Delight, because that class is unloadable on an install without the
 * add-on. So the adapters push a contribution in when they are installed, and the caller pulls the
 * sum out without knowing who supplied it.
 *
 * <p><b>It is a contribution, not a channel.</b> The value returned here joins the existing
 * outgoing-damage sum in {@code TConstructPerkHandler.onMeleeDamage} and is capped with it, once,
 * by {@code tconstructNewDamageBonusCap} (§13.2). Nothing in this class applies anything to
 * anything; a second cap here is exactly the duplicate summation §13.2 forbids.
 */
public final class TcAddonHooks {

    /** One add-on's share of the new outgoing melee-damage channel. */
    @FunctionalInterface
    public interface MeleeDamageContributor {

        /** The extra share this contributor adds for {@code player}; {@code 0} for none. */
        double bonus(ServerPlayer player);
    }

    /**
     * One add-on's share of the new durability-avoidance channel.
     *
     * <p>Its own interface rather than a reuse of {@link MeleeDamageContributor} because it needs
     * the stack: "the ring this save is charging" and "the gear you are still standing in" are both
     * questions about a particular item, and a contributor that could not see one would have to
     * guess from what the player happens to be holding.
     */
    @FunctionalInterface
    public interface WearAvoidanceContributor {

        /** The probability this contributor adds for {@code player} on {@code stack}; {@code 0} for none. */
        double bonus(ServerPlayer player, ItemStack stack);
    }

    /**
     * One add-on's share of the new experience-recovery channel.
     *
     * <p>A share of an amount another mod consumed, never an amount of its own: the composition
     * point multiplies it by the orb that was destroyed, so a contributor cannot mint experience
     * that no orb was worth.
     */
    @FunctionalInterface
    public interface ExperienceContributor {

        /** The share of the consumed award this contributor returns to {@code player}; {@code 0} for none. */
        double bonus(ServerPlayer player);
    }

    /**
     * One add-on's share of the existing paid-repair channel.
     *
     * <p>Not a new channel: the value joins the share {@code TConstructPerkHandler.applyRepairBonus}
     * already composes for Material Harmony and the repair Powers, and is bounded with them once by
     * {@code tconstructRepairBonusCap}. A second cap here would be the duplicate summation the
     * melee javadoc above forbids for the same reason.
     */
    @FunctionalInterface
    public interface RepairBonusContributor {

        /** The share this contributor adds on {@code delivered}; {@code 0} for none. */
        double bonus(ServerPlayer player, ItemStack delivered);
    }

    /** Something an add-on adapter wants to know about a station take it did not observe itself. */
    @FunctionalInterface
    public interface StationTakeObserver {

        /** {@code delivered} is the copy the player is about to be handed. */
        void onDelivered(ServerPlayer player, ItemStack delivered);
    }

    /** Something an add-on adapter wants to know about a death that has finished resolving. */
    @FunctionalInterface
    public interface DeathResolutionObserver {

        /** {@code survived} is whether the death was cancelled by the time every handler had run. */
        void onDeathResolved(ServerPlayer player, boolean survived);
    }

    /**
     * Installed contributors, in registration order.
     *
     * <p>Copy-on-write: registration happens once during mod loading, reads happen on every accepted
     * melee hit by every player.
     */
    private static final List<MeleeDamageContributor> MELEE = new CopyOnWriteArrayList<>();

    /** Installed wear contributors. Copy-on-write for the same reason as {@link #MELEE}. */
    private static final List<WearAvoidanceContributor> WEAR = new CopyOnWriteArrayList<>();

    /** Installed experience contributors. Copy-on-write for the same reason. */
    private static final List<ExperienceContributor> EXPERIENCE = new CopyOnWriteArrayList<>();

    /** Installed repair contributors. Copy-on-write for the same reason. */
    private static final List<RepairBonusContributor> REPAIR = new CopyOnWriteArrayList<>();

    /** Installed station observers. Copy-on-write for the same reason. */
    private static final List<StationTakeObserver> STATION = new CopyOnWriteArrayList<>();

    /** Installed death observers. Copy-on-write for the same reason. */
    private static final List<DeathResolutionObserver> DEATHS = new CopyOnWriteArrayList<>();

    /**
     * Players whose {@code LivingDeathEvent} is being resolved right now.
     *
     * <p>Tinkers' Jewelry spends the durability its undying save costs from inside its own
     * {@code LivingDeathEvent} handler, so that wear arrives at the H1 seam with nothing on the
     * stack to say what it was for. The composition point brackets the event, and this set is what
     * lets the adapter tell "the save is charging this ring" from "you hit something with it".
     * Server-thread only; the bracket clears it even when the death was cancelled.
     */
    private static final java.util.Set<java.util.UUID> RESOLVING_DEATH =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private TcAddonHooks() {
    }

    /** Adds {@code contributor} to the melee sum. Called from an add-on adapter's installer. */
    public static void addMeleeDamageContributor(MeleeDamageContributor contributor) {
        if (contributor != null) MELEE.add(contributor);
    }

    /** The add-on share of the melee bonus for {@code player}, before the shared cap. */
    public static double meleeDamageBonus(ServerPlayer player) {
        if (player == null || MELEE.isEmpty()) return 0.0;
        double total = 0.0;
        for (MeleeDamageContributor contributor : MELEE) {
            total += Math.max(0.0, contributor.bonus(player));
        }
        return total;
    }

    /** Adds {@code contributor} to the wear-avoidance sum. Called from an add-on adapter's installer. */
    public static void addWearAvoidanceContributor(WearAvoidanceContributor contributor) {
        if (contributor != null) WEAR.add(contributor);
    }

    /**
     * The add-on share of the avoidance probability for {@code player} on {@code stack}, before the
     * shared cap.
     */
    public static double wearAvoidanceBonus(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty() || WEAR.isEmpty()) return 0.0;
        double total = 0.0;
        for (WearAvoidanceContributor contributor : WEAR) {
            total += Math.max(0.0, contributor.bonus(player, stack));
        }
        return total;
    }

    /** Adds {@code contributor} to the experience-recovery sum. Called from an adapter's installer. */
    public static void addExperienceContributor(ExperienceContributor contributor) {
        if (contributor != null) EXPERIENCE.add(contributor);
    }

    /** The add-on share of a consumed experience award for {@code player}, before the shared cap. */
    public static double experienceBonus(ServerPlayer player) {
        if (player == null || EXPERIENCE.isEmpty()) return 0.0;
        double total = 0.0;
        for (ExperienceContributor contributor : EXPERIENCE) {
            total += Math.max(0.0, contributor.bonus(player));
        }
        return total;
    }

    /** Adds {@code contributor} to the paid-repair sum. Called from an adapter's installer. */
    public static void addRepairBonusContributor(RepairBonusContributor contributor) {
        if (contributor != null) REPAIR.add(contributor);
    }

    /** The add-on share of a paid repair of {@code delivered}, before the shared cap. */
    public static double repairBonusShare(ServerPlayer player, ItemStack delivered) {
        if (player == null || delivered == null || delivered.isEmpty() || REPAIR.isEmpty()) return 0.0;
        double total = 0.0;
        for (RepairBonusContributor contributor : REPAIR) {
            total += Math.max(0.0, contributor.bonus(player, delivered));
        }
        return total;
    }

    /** Adds {@code observer} to the station-take notification. Called from an adapter's installer. */
    public static void addStationTakeObserver(StationTakeObserver observer) {
        if (observer != null) STATION.add(observer);
    }

    /** Tells every observer about one delivered station take. Called from the composition point. */
    public static void stationTakeDelivered(ServerPlayer player, ItemStack delivered) {
        if (player == null || delivered == null || delivered.isEmpty()) return;
        for (StationTakeObserver observer : STATION) {
            observer.onDelivered(player, delivered);
        }
    }

    /** Adds {@code observer} to the death notification. Called from an adapter's installer. */
    public static void addDeathResolutionObserver(DeathResolutionObserver observer) {
        if (observer != null) DEATHS.add(observer);
    }

    /** Opens the death-resolution bracket for {@code player}. Called from the composition point. */
    public static void beginDeathResolution(ServerPlayer player) {
        if (player != null) RESOLVING_DEATH.add(player.getUUID());
    }

    /**
     * Closes the bracket and tells every observer how the death ended.
     *
     * <p>The set entry is dropped first, so an observer that spends durability of its own is not
     * paid the save's own discount.
     */
    public static void endDeathResolution(ServerPlayer player, boolean survived) {
        if (player == null) return;
        RESOLVING_DEATH.remove(player.getUUID());
        for (DeathResolutionObserver observer : DEATHS) {
            observer.onDeathResolved(player, survived);
        }
    }

    /** Whether {@code player}'s death is being resolved right now. */
    public static boolean inDeathResolution(ServerPlayer player) {
        return player != null && RESOLVING_DEATH.contains(player.getUUID());
    }

    /** Forgets one player's bracket, on logout or server stop. */
    public static void forget(java.util.UUID player) {
        if (player != null) RESOLVING_DEATH.remove(player);
    }

    /**
     * Whether {@code perk} may act for {@code player} right now — the same three gates the core
     * Tinkers' perks pass, asked in the same order.
     *
     * <p>{@code enableTConstructPerks} covers the add-on perks too: they are Tinkers' perks that
     * happen to need a second jar, and an operator who has switched the Tinkers' perks off has not
     * asked for seven more. The capability gate is what makes an unavailable add-on produce a
     * dormant perk and an honest diagnostic rather than a silent no-op (§10.3).
     */
    public static boolean active(ServerPlayer player, RegistryObject<Perk> perk, Capability capability) {
        if (player == null || player instanceof FakePlayer || perk == null) return false;
        if (!HandlerCommonConfig.HANDLER.instance().enableTConstructPerks) return false;
        if (!TConstructCompatibilityStatus.current().supports(capability)) return false;
        return perk.get().isEnabled(player);
    }

    /** The tick count of the server {@code player} is on, or {@code 0} when there is none. */
    public static long now(ServerPlayer player) {
        return player == null || player.getServer() == null ? 0L : player.getServer().getTickCount();
    }
}
