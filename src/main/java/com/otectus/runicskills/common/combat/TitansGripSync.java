package com.otectus.runicskills.common.combat;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.client.TitansGripSyncCP;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Tells everyone who can see a player whether that player holds Titan's Grip.
 *
 * <p>The perk's whole visible effect is a shield that Better Combat would otherwise hide, and
 * {@code TwoHandedExemption} can only reveal it for a player whose perk state the deciding client
 * knows. The owning client knows its own; no client knew anybody else's, so the shield appeared on
 * the wielder's screen and on nobody else's. This is the missing half: the same fact, sent to the
 * clients that are drawing that player.
 *
 * <p><b>Two send points, and they are complementary.</b> {@link #broadcast} answers "the value
 * changed, tell the people watching"; {@link #tracking} answers "somebody new is watching, tell
 * them the value". Neither covers the other, and between them there is no window in which a client
 * is rendering a player it has not been told about.
 *
 * <p><b>Where the change point is.</b> Every capability edit that can move this perk — a rank
 * bought or toggled, a skill level gained, a respec, a respawn clone, a datapack reload, an operator
 * command — funnels through {@code SyncSkillCapabilityCP.send} to reach the owner's own client, at
 * fifty-odd call sites. That single funnel is where {@link #broadcast} is called from rather than at
 * each of them: a future edit path that forgot to notify trackers would also have forgotten to
 * notify the owner, which is a bug that shows up immediately. A player who has simply become
 * visible has changed nothing and needs {@link #tracking} instead, which is also what covers a
 * client that has only just joined.
 *
 * <p><b>Cost.</b> {@link #broadcast} computes one boolean and compares it to the last one sent
 * ({@code SkillCapability#markTitansGripSent}), so a sync for an unrelated reason — the common case,
 * several a second across a busy server — sends nothing. An actual change sends a two-byte payload
 * to the players in tracking range.
 *
 * <p>Registration is unconditional because it has to be: the perk exists only with Better Combat or
 * Spartan Weaponry installed, and on a pack with neither, {@link RegistryPerks#TITANS_GRIP} is null
 * and both methods return before touching the network.
 */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public final class TitansGripSync {

    private TitansGripSync() {
    }

    /**
     * Whether {@code player} holds the perk, independent of what they are carrying.
     *
     * <p>Deliberately not the full {@code TwoHandedExemption.applies} rule: the weapon and shield
     * halves are already replicated to every tracking client by vanilla equipment sync, so sending
     * them again would be redundant and would turn every hotbar scroll into a packet. The client
     * re-derives those two from the inventory it already has.
     */
    public static boolean held(Player player) {
        if (player == null || RegistryPerks.TITANS_GRIP == null) return false;
        return RegistryPerks.TITANS_GRIP.get().isEnabled(player);
    }

    /**
     * Sends the current value to this player's trackers and to the player, if it changed.
     *
     * <p>{@code TRACKING_ENTITY_AND_SELF} rather than {@code TRACKING_ENTITY}: the owner's own
     * client evaluates the exemption from its real synced capability, so the flag is redundant
     * there — but it is also harmless, it keeps the two sides of the predicate from diverging if the
     * owner's capability sync is ever delayed, and it is the distributor
     * {@code RunicGuard} already uses for per-entity state.
     *
     * @return whether a packet was sent
     */
    public static boolean broadcast(Player player) {
        if (RegistryPerks.TITANS_GRIP == null) return false;
        if (!(player instanceof ServerPlayer server) || player instanceof FakePlayer) return false;
        // An offline or test player is not in the server's tracking set; the same guard RunicGuard
        // applies before handing an entity to PacketDistributor.
        if (server.connection == null || ServerNetworking.instance == null) return false;
        SkillCapability capability = SkillCapability.get(server);
        if (capability == null) return false;
        boolean now = held(server);
        if (!capability.markTitansGripSent(now)) return false;
        ServerNetworking.instance.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> server),
                new TitansGripSyncCP(server.getId(), now));
        return true;
    }

    /**
     * Brings one newly-tracking client up to date about one player.
     *
     * <p>Only a {@code true} needs sending: the client's default for a capability it has just
     * attached to a newly-visible entity is already {@code false}, and the entity it attached that
     * capability to is the one this id addresses.
     */
    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (RegistryPerks.TITANS_GRIP == null) return;
        if (!(event.getEntity() instanceof ServerPlayer viewer) || viewer.connection == null) return;
        if (!(event.getTarget() instanceof ServerPlayer target) || target instanceof FakePlayer) return;
        if (!held(target)) return;
        ServerNetworking.sendToPlayer(new TitansGripSyncCP(target.getId(), true), viewer);
    }
}
