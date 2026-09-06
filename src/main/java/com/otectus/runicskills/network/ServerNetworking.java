package com.otectus.runicskills.network;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.network.packet.client.*;
import com.otectus.runicskills.network.packet.common.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;


public class ServerNetworking {
    private static int packetId = 0;
    // 1.3.6: bumped "4" -> "5" to match the 1.1.0 CHANGELOG claim. The 1.1.0 release notes
    // documented the three new Powers packets (PowerOverridesSyncCP, PowerProcCP, PowerEquipSP)
    // and the protocol bump, but neither the registerMessage calls nor the version constant
    // were ever landed in code. The missing registration caused IllegalArgumentException:
    // "Invalid message PowerOverridesSyncCP" on player join, surfaced after the 1.3.5 title-NPE
    // hotfix.
    // 1.3.9: bumped "5" -> "6" with the addition of NoticeOverlayCP (over-GUI denial banner
    // for spell-gating and Apotheosis affix gating). New packet => new channel registration =>
    // protocol bump so mismatched client/server pairs are rejected cleanly rather than
    // desyncing on an unknown message id.
    // 1.5.3: bumped "6" -> "7" — the common config packet gained the maxPerkBudgetCap field, so a
    // 1.5.2 client would have read the stream one int short of where the server wrote it.
    // 1.6.0: bumped "7" -> "8" — the common config packet gained disabledPowers plus the
    // hideDisabledPerks/Passives/Powers flags. Same rule: payload change => protocol bump.
    // 1.7.0: bumped "8" -> "9" — the dynamic config packet moved the sixteen passive-level arrays
    // from a packed string to length-prefixed varint arrays (RS-027/RS-153).
    // 2.0.0: bumped "9" -> "10" — a breaking bundle, taken once rather than field by field:
    //   * CommonConfigSyncCP + DynamicConfigSyncCP are replaced by GameplayConfigCP, whose payload
    //     is generated from the config class instead of hand-listing 128 of 1,133 fields;
    //   * PassiveLevelUpSP/PassiveLevelDownSP are replaced by the batched AdjustPassiveSP, because
    //     the rate limiter silently discarded every packet after the first in a bulk click;
    //   * every server-bound content id is decoded as a length-bounded ResourceLocation instead of
    //     a 32,767-character string.
    // Protocol 9 peers are refused outright rather than tolerated: the payload semantics differ,
    // so a partial handshake would be worse than a clear refusal (RS10-005, RS10-007, RS10-020).
    // 2.0.7: bumped "11" -> "12" — the Tinker's Construct workshop layer adds three registrations
    // (WorkshopFocusSP, StationQuoteCP, WorkshopStatusCP). New message ids on the channel means an
    // 11 peer would decode a stream whose ids it has no handler for, so the pair is refused at
    // negotiation instead (spec §15.3: "update the channel predicate, documentation and consistency
    // checks together"). The mod version and this number are separate values and always have been.
    private static final String PROTOCOL_VERSION = "12";
    public static SimpleChannel instance;

    /**
     * Channel-acceptance predicate (since 1.2.0). Wraps the previous {@code PROTOCOL_VERSION::equals}
     * with a clear log on mismatch — Forge's player-facing disconnect message remains generic
     * because the kick is initiated by Forge's negotiation layer, not our channel, but operators
     * now have a server-log line with the exact required vs reported versions.
     *
     * <p>Since 1.2.2: prefix-matches the {@code NetworkRegistry.ABSENT} ("ABSENT 🤔") and
     * {@code NetworkRegistry.ACCEPTVANILLA} ("ALLOWVANILLA 👍") sentinels that Forge passes
     * during periodic channel-acceptance probes (LAN advertising, ping handlers) every
     * ~5 seconds. The 1.2.1 fix used {@code .equals(...)} against the constants but didn't
     * match at runtime — Forge 47.3.0's value evidently drifts from the open-source value
     * (likely the trailing emoji), or the predicate receives a wrapped peerVersion. The
     * textual prefix is invariant across Forge versions, so prefix-match is defensive.
     * We still log a WARN when a real peer reports an actual mismatched version string.
     */
    private static boolean acceptsVersion(String peerVersion) {
        if (PROTOCOL_VERSION.equals(peerVersion)) return true;
        if (peerVersion != null
                && (peerVersion.startsWith("ABSENT")
                    || peerVersion.startsWith("ALLOWVANILLA"))) {
            return false;
        }
        RunicSkills.getLOGGER().warn(
            "Runic Skills network protocol mismatch: this side requires PROTOCOL_VERSION={}, peer reports {}. " +
            "Update both client and server to the same Runic Skills release.",
            PROTOCOL_VERSION, peerVersion
        );
        return false;
    }

    public static void init() {
        instance = NetworkRegistry.ChannelBuilder.named(new ResourceLocation(RunicSkills.MOD_ID, "network")).networkProtocolVersion(() -> PROTOCOL_VERSION).clientAcceptedVersions(ServerNetworking::acceptsVersion).serverAcceptedVersions(ServerNetworking::acceptsVersion).simpleChannel();

        instance.registerMessage(packetId++, ConfigSyncCP.class, ConfigSyncCP::toBytes, ConfigSyncCP::new, ConfigSyncCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // One generated payload replaces CommonConfigSyncCP + DynamicConfigSyncCP, which together
        // hand-listed 128 of the config's 1,133 fields and left the rest resolved from whatever
        // file the CLIENT happened to have (RS10-005).
        instance.registerMessage(packetId++, GameplayConfigCP.class, GameplayConfigCP::toBytes, GameplayConfigCP::new, GameplayConfigCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, SyncSkillCapabilityCP.class, SyncSkillCapabilityCP::toBytes, SyncSkillCapabilityCP::new, SyncSkillCapabilityCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, PlayerMessagesCP.class, PlayerMessagesCP::toBytes, PlayerMessagesCP::new, PlayerMessagesCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, SkillOverlayCP.class, SkillOverlayCP::toBytes, SkillOverlayCP::new, SkillOverlayCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, NoticeOverlayCP.class, NoticeOverlayCP::toBytes, NoticeOverlayCP::new, NoticeOverlayCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, TitleOverlayCP.class, TitleOverlayCP::toBytes, TitleOverlayCP::new, TitleOverlayCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, PerkGroupsSyncCP.class, PerkGroupsSyncCP::toBytes, PerkGroupsSyncCP::new, PerkGroupsSyncCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        instance.registerMessage(packetId++, SkillLevelUpSP.class, SkillLevelUpSP::toBytes, SkillLevelUpSP::new, SkillLevelUpSP::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // One signed adjustment replaces the two single-level packets: the rate limiter admits one
        // packet of a type every two ticks, so a bulk click sent as N packets lost N-1 of them
        // (RS10-007).
        instance.registerMessage(packetId++, AdjustPassiveSP.class, AdjustPassiveSP::toBytes, AdjustPassiveSP::new, AdjustPassiveSP::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        instance.registerMessage(packetId++, TogglePerkSP.class, TogglePerkSP::toBytes, TogglePerkSP::new, TogglePerkSP::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // CounterAttackSP removed: damage modifier is now computed entirely server-side in CombatEventHandler
        instance.registerMessage(packetId++, SetPlayerTitleSP.class, SetPlayerTitleSP::toBytes, SetPlayerTitleSP::new, SetPlayerTitleSP::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        instance.registerMessage(packetId++, OpenEnderChestSP.class, OpenEnderChestSP::toBytes, OpenEnderChestSP::new, OpenEnderChestSP::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Powers system packets (1.1.0 CHANGELOG documented them but they were never
        // registered — caused the 1.3.5 world-join crash once the title-NPE was fixed).
        // PowerOverridesSyncCP is sent at PlayerLoggedInEvent (see PlayerLifecycleHandler:62).
        instance.registerMessage(packetId++, PowerOverridesSyncCP.class, PowerOverridesSyncCP::toBytes, PowerOverridesSyncCP::new, PowerOverridesSyncCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, PowerProcCP.class, PowerProcCP::toBytes, PowerProcCP::new, PowerProcCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, PowerEquipSP.class, PowerEquipSP::toBytes, PowerEquipSP::new, PowerEquipSP::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Tinker's Construct workshop layer (2.0.7). The two clientbound packets carry presentation
        // only — a quote this player would get, and where their own focus stands — and the one
        // serverbound packet carries an intent, never a number the server then trusts.
        instance.registerMessage(packetId++, StationQuoteCP.class, StationQuoteCP::toBytes, StationQuoteCP::new, StationQuoteCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, WorkshopStatusCP.class, WorkshopStatusCP::toBytes, WorkshopStatusCP::new, WorkshopStatusCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, WorkshopFocusSP.class, WorkshopFocusSP::toBytes, WorkshopFocusSP::new, WorkshopFocusSP::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendToServer(Object message) {
        instance.sendToServer(message);
    }

    public static void sendToPlayer(Object message, ServerPlayer serverPlayer) {
        instance.send(PacketDistributor.PLAYER.with(() -> serverPlayer), message);
    }

    public static void sendToAllClients(Object message) {
        instance.send(PacketDistributor.ALL.noArg(), message);
    }

    /**
     * Sends to every client tracking a point in one dimension.
     *
     * <p>Added for Power proc presentation, which is the first thing this mod sends to anyone but
     * the acting player: a proc is something bystanders should see, and a radius keeps that from
     * becoming a server-wide broadcast.
     */
    public static void sendNear(Object message, Vec3 origin, double radius,
                                ResourceKey<Level> dimension) {
        instance.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                origin.x, origin.y, origin.z, radius, dimension)), message);
    }
}


