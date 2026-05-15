package com.otectus.runicskills.network;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.network.packet.client.*;
import com.otectus.runicskills.network.packet.common.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;


public class ServerNetworking {
    private static int packetId = 0;
    private static final String PROTOCOL_VERSION = "5";
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
        instance.registerMessage(packetId++, DynamicConfigSyncCP.class, DynamicConfigSyncCP::toBytes, DynamicConfigSyncCP::new, DynamicConfigSyncCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, CommonConfigSyncCP.class, CommonConfigSyncCP::toBytes, CommonConfigSyncCP::new, CommonConfigSyncCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, SyncSkillCapabilityCP.class, SyncSkillCapabilityCP::toBytes, SyncSkillCapabilityCP::new, SyncSkillCapabilityCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, PlayerMessagesCP.class, PlayerMessagesCP::toBytes, PlayerMessagesCP::new, PlayerMessagesCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, SkillOverlayCP.class, SkillOverlayCP::toBytes, SkillOverlayCP::new, SkillOverlayCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, TitleOverlayCP.class, TitleOverlayCP::toBytes, TitleOverlayCP::new, TitleOverlayCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        instance.registerMessage(packetId++, PerkGroupsSyncCP.class, PerkGroupsSyncCP::toBytes, PerkGroupsSyncCP::new, PerkGroupsSyncCP::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        instance.registerMessage(packetId++, SkillLevelUpSP.class, SkillLevelUpSP::toBytes, SkillLevelUpSP::new, SkillLevelUpSP::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        instance.registerMessage(packetId++, PassiveLevelUpSP.class, PassiveLevelUpSP::toBytes, PassiveLevelUpSP::new, PassiveLevelUpSP::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        instance.registerMessage(packetId++, PassiveLevelDownSP.class, PassiveLevelDownSP::toBytes, PassiveLevelDownSP::new, PassiveLevelDownSP::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        instance.registerMessage(packetId++, TogglePerkSP.class, TogglePerkSP::toBytes, TogglePerkSP::new, TogglePerkSP::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // CounterAttackSP removed: damage modifier is now computed entirely server-side in CombatEventHandler
        instance.registerMessage(packetId++, SetPlayerTitleSP.class, SetPlayerTitleSP::toBytes, SetPlayerTitleSP::new, SetPlayerTitleSP::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        instance.registerMessage(packetId++, OpenEnderChestSP.class, OpenEnderChestSP::toBytes, OpenEnderChestSP::new, OpenEnderChestSP::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
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
}


