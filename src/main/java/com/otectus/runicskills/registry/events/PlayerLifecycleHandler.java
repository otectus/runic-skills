package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.capability.LazySkillCapability;
import com.otectus.runicskills.common.command.*;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.PacketRateLimiter;
import com.otectus.runicskills.network.packet.client.*;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.perks.PerkGroupsReloadListener;
import com.otectus.runicskills.registry.title.Title;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public class PlayerLifecycleHandler {

    @SubscribeEvent
    public void onPlayerNameFormat(PlayerEvent.NameFormat event) {
        if (RunicSkills.server != null && HandlerCommonConfig.HANDLER.instance().displayTitlesAsPrefix) {
            ServerPlayer serverPlayer = RunicSkills.server.getPlayerList().getPlayer(event.getEntity().getUUID());
            if (serverPlayer == null) return;
            SkillCapability capability = SkillCapability.get(serverPlayer);
            if (capability == null) return;
            Title titleKey = RegistryTitles.getTitle(capability.getPlayerTitle());
            String title = (titleKey != null) ? Component.translatable(RegistryTitles.getTitle(capability.getPlayerTitle()).getKey()).getString() : "";

            event.setDisplayname(Component.literal(String.format("[%s] %s",
                    title.isEmpty()
                            ? Component.translatable(RegistryTitles.TITLELESS.get().getKey()).getString()
                            : title,
                    event.getDisplayname().getString())));
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) {
            if (player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer)) {
                ConfigSyncCP.sendToPlayer(serverPlayer);
                CommonConfigSyncCP.sendToPlayer(serverPlayer);
                DynamicConfigSyncCP.sendToPlayer(serverPlayer);
                PerkGroupsSyncCP.sendToPlayer(serverPlayer);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        PacketRateLimiter.clearPlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStarting(final ServerStartingEvent event) {
        RunicSkills.server = event.getServer();
    }

    @SubscribeEvent
    public void onServerStopped(final ServerStoppedEvent event) {
        RunicSkills.server = null;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SkillLevelCommand.register(event.getDispatcher());
        TitleCommand.register(event.getDispatcher());
        SkillsReloadCommand.register(event.getDispatcher());
        RegisterItem.register(event.getDispatcher());
        GlobalLimitCommand.register(event.getDispatcher());
        UpdateSkillLevelCommand.register(event.getDispatcher());
        RespecCommand.register(event.getDispatcher());
        ListSkillsCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player && !(event.getObject() instanceof FakePlayer)) {
            SkillCapability skillCapability = new SkillCapability();
            LazySkillCapability lazySkillCapability = new LazySkillCapability(skillCapability);
            event.addCapability(new ResourceLocation(RunicSkills.MOD_ID, "skills"), lazySkillCapability);
        }
    }

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(SkillCapability.class);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PerkGroupsReloadListener());
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayerNew) {
            player = event.getOriginal();
            if (player instanceof ServerPlayer serverPlayerOld) {
                serverPlayerOld.reviveCaps();
                serverPlayerOld.getCapability(RegistryCapabilities.SKILL).ifPresent((oldAbilities) -> {
                    serverPlayerNew.getCapability(RegistryCapabilities.SKILL).ifPresent((newAbilities) -> {
                        newAbilities.copyFrom(oldAbilities);
                    });
                });
                RegistryAttributes.modifierAttributes(serverPlayerNew);
                RegistryTitles.syncTitles(serverPlayerNew);
                if (!serverPlayerOld.isDeadOrDying()) {
                    serverPlayerNew.setHealth(serverPlayerOld.getHealth());
                } else {
                    serverPlayerNew.setHealth(serverPlayerOld.getMaxHealth());
                }
                serverPlayerOld.invalidateCaps();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoinWorld(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            Entity entity = event.getEntity();
            if (entity instanceof FakePlayer) return;
            if (entity instanceof ServerPlayer serverPlayer) {
                SyncSkillCapabilityCP.send(serverPlayer);
                RegistryAttributes.modifierAttributes(serverPlayer);
                RegistryTitles.syncTitles(serverPlayer);

                if (HandlerCommonConfig.HANDLER.instance().checkForUpdates
                        && RunicSkills.UpdatesAvailable.left) {
                    if (serverPlayer.hasPermissions(2)) {
                        Component component = Component.literal(String.format("[Runic Skills] Version %s is available, it's recommended to update!", RunicSkills.UpdatesAvailable.right));
                        serverPlayer.sendSystemMessage(component);
                    }
                }
            }
        }
    }
}
