package com.otectus.runicskills.registry.events;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.List;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.capability.LazySkillCapability;
import com.otectus.runicskills.common.command.*;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.PacketRateLimiter;
import com.otectus.runicskills.network.packet.client.*;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.perks.PerkGroupsReloadListener;
import com.otectus.runicskills.registry.powers.PowerOverridesReloadListener;
import com.otectus.runicskills.registry.title.Title;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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

    /**
     * Composes the title as a prefix on the player's display name.
     *
     * <p>This is the only place a title reaches a name, and it is deliberately the only one: the
     * event exists so a mod can decorate a name without owning it, and whatever the previous
     * listener produced is appended intact rather than flattened.
     *
     * <p>The previous version called {@code getString()} on the incoming display name and rebuilt
     * it with {@code String.format}, which threw away every style, hover event, click event and
     * translatable child a nickname mod had put there — and did the same to the title itself
     * (RS10-010). {@code Component} concatenation keeps all of it.
     */
    @SubscribeEvent
    public void onPlayerNameFormat(PlayerEvent.NameFormat event) {
        if (RunicSkills.server == null) return;
        if (!HandlerCommonConfig.HANDLER.instance().displayTitlesAsPrefix) return;

        ServerPlayer serverPlayer = RunicSkills.server.getPlayerList().getPlayer(event.getEntity().getUUID());
        if (serverPlayer == null) return;
        SkillCapability capability = SkillCapability.get(serverPlayer);
        if (capability == null) return;

        // A stored id that no longer resolves falls back to the titleless title rather than to an
        // empty bracket. NameFormat fires on chat and the tab list, so this runs often.
        Title selected = RegistryTitles.getTitle(capability.getPlayerTitle());
        String titleKey = selected != null
                ? selected.getKey()
                : RegistryTitles.TITLELESS.get().getKey();

        event.setDisplayname(Component.empty()
                .append(Component.literal("["))
                .append(Component.translatable(titleKey))
                .append(Component.literal("] "))
                .append(event.getDisplayname()));
    }

    @SubscribeEvent
    public void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) {
            if (player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer)) {
                // One-shot cleanup for saves written before 2.0.0, when the title was stamped into
                // the player's vanilla custom name. Deleting the code that wrote it does not delete
                // what it wrote, and only a name this mod can prove it set is touched (RS10-010).
                if (RegistryTitles.clearLegacyTitleCustomName(serverPlayer)) {
                    RunicSkills.getLOGGER().debug(
                            "Cleared a title left in {}'s custom name by a pre-2.0.0 version.",
                            serverPlayer.getGameProfile().getName());
                }
                ConfigSyncCP.sendToPlayer(serverPlayer);
                GameplayConfigCP.sendToPlayer(serverPlayer);
                PerkGroupsSyncCP.sendToPlayer(serverPlayer);
                PowerOverridesSyncCP.sendToPlayer(serverPlayer);
                // A Power cooldown that the save carried as remaining ticks becomes a deadline
                // against the clock this session is running (§15.1). Done at login rather than at
                // capability load because only here is the tick count the debt must be measured
                // against the one the runtime map will be read with.
                com.otectus.runicskills.common.powers.PowerCooldownDebt.restore(serverPlayer);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        PacketRateLimiter.clearPlayer(event.getEntity().getUUID());
        PerkEffectsHandler.clearPlayer(event.getEntity().getUUID());
        FortunePerkHandler.clearPlayer(event.getEntity().getUUID());
        EnchantingLorePerkHandler.clearPlayer(event.getEntity().getUUID());
        // Auto Repair's unspent fraction is credit earned in a session that has ended. It is never
        // written to NBT, and it must not survive the logout in memory either: the perk promises
        // repair while you play, not a balance you accrue and collect later (RS207-06).
        com.otectus.runicskills.common.durability.RepairBudget.clear(event.getEntity().getUUID());
        // A workshop focus is a claim on a block made by a player who is standing at it. Both
        // halves of that stop being true at logout, and §6.4 says the claim stops with them.
        com.otectus.runicskills.common.workshop.WorkshopFocusService.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStarting(final ServerStartingEvent event) {
        RunicSkills.server = event.getServer();
    }

    @SubscribeEvent
    public void onServerStopped(final ServerStoppedEvent event) {
        RunicSkills.server = null;
        // Reset every static tick baseline. These are keyed on server.getTickCount(), which
        // restarts at 0 with the server — so in a single JVM that hosts more than one world (a
        // singleplayer player returning to the main menu and loading a different save), stale
        // baselines from the previous run were all in the future. That silently disabled combat-
        // memory pruning and made the packet rate limiter reject everything, for the rest of the
        // JVM's life (RS-132).
        CombatEventHandler.resetTickBaselines();
        com.otectus.runicskills.network.PacketRateLimiter.clear();
        com.otectus.runicskills.common.util.ContainerRewardLedger.clear();
        com.otectus.runicskills.common.powers.PowerRuntime.clearAll();
        com.otectus.runicskills.common.durability.RepairBudget.clearAll();
        PerkEffectsHandler.clearAll();
        FortunePerkHandler.clearAll();
        EnchantingLorePerkHandler.clearAll();
        // Both caches are keyed on server-owned data — recipes and the enchantment registry — so
        // in a JVM that hosts a second world they must not answer for the first one's content.
        EnchantingLorePerkHandler.clearCache();
        com.otectus.runicskills.common.crafting.MasterResearcherRecipeIndex.invalidate();
        com.otectus.runicskills.common.crafting.RecyclingIndex.clear();
        com.otectus.runicskills.common.workshop.WorkshopFocusService.clearAll();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        List<LiteralCommandNode<CommandSourceStack>> roots = List.of(
                SkillLevelCommand.register(dispatcher),
                TitleCommand.register(dispatcher),
                SkillsReloadCommand.register(dispatcher),
                RegisterItem.register(dispatcher),
                GlobalLimitCommand.register(dispatcher),
                UpdateSkillLevelCommand.register(dispatcher),
                RespecCommand.register(dispatcher),
                ListSkillsCommand.register(dispatcher),
                PowersCommand.register(dispatcher));
        registerNamespacedAliases(dispatcher, roots);
    }

    /**
     * Mirrors every command under a {@code /runicskills} root.
     *
     * <p>All nine literals are generic words — {@code skills}, {@code titles}, {@code powers},
     * {@code respec} — registered at the top level, and Brigadier resolves a collision by letting
     * whichever mod registered last own the name. In a large progression-heavy pack that is a real
     * possibility, and when it happens the losing mod's commands are simply unreachable with no
     * diagnostic (RS-122).
     *
     * <p>The short forms stay exactly as they were, so nothing anyone has typed or scripted breaks;
     * this only adds an unambiguous path alongside them. Each alias redirects to the real node
     * rather than rebuilding the tree, so the two can never describe different commands, and it
     * carries the same permission predicate — the alias must not become a way around
     * {@code requires}.
     */
    private static void registerNamespacedAliases(CommandDispatcher<CommandSourceStack> dispatcher,
                                                  List<LiteralCommandNode<CommandSourceStack>> roots) {
        LiteralArgumentBuilder<CommandSourceStack> namespaced = Commands.literal(RunicSkills.MOD_ID);
        for (LiteralCommandNode<CommandSourceStack> root : roots) {
            LiteralArgumentBuilder<CommandSourceStack> alias =
                    Commands.literal(root.getName()).requires(root.getRequirement());
            // A redirect only forwards when there is more input to consume, so a command that is
            // executable at its own root (/respec) needs its action copied across as well.
            if (root.getCommand() != null) alias.executes(root.getCommand());
            if (!root.getChildren().isEmpty()) alias.redirect(root);
            namespaced.then(alias);
        }
        dispatcher.register(namespaced);
    }

    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player && !(event.getObject() instanceof FakePlayer)) {
            SkillCapability skillCapability = new SkillCapability();
            LazySkillCapability lazySkillCapability = new LazySkillCapability(skillCapability);
            event.addCapability(new ResourceLocation(RunicSkills.MOD_ID, "skills"), lazySkillCapability);
            // Tie the LazyOptional's lifetime to the entity's. Without this listener, an optional
            // already handed to a caller kept resolving to this player's capability after the
            // entity was discarded on death or dimension change (RS-007).
            event.addListener(lazySkillCapability::invalidate);
        }
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PerkGroupsReloadListener());
        event.addListener(new com.otectus.runicskills.registry.skill.SkillVisualsReloadListener());
        event.addListener(new PowerOverridesReloadListener());
        // Master Researcher's recipe index is a snapshot of the recipe manager, and /reload
        // replaces every recipe in it. Nothing to prepare, so the listener is just the drop.
        event.addListener((ResourceManagerReloadListener)
                manager -> com.otectus.runicskills.common.crafting.MasterResearcherRecipeIndex.invalidate());
        // The salvage allowlist. Unlike the index above this one is REPLACED rather than dropped:
        // clearing first and rebuilding second would leave a window in which salvage silently does
        // nothing, and a datapack that failed to parse would leave that window open.
        event.addListener(new com.otectus.runicskills.common.crafting.RecyclingRuleLoader());
        // The pack rule set (§13.3). Registered whether or not Tinker's Construct is installed:
        // the craft-reward half of the schema speaks about any item, the loader names no
        // slimeknights type, and a rule file that a server cannot yet resolve is skipped by its own
        // requires_mods rather than by never being read.
        event.addListener(new com.otectus.runicskills.common.rules.TConstructRulesLoader());
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
                com.otectus.runicskills.integration.quests.RunicQuestBridge.refreshAll(serverPlayerNew);
                if (!serverPlayerOld.isDeadOrDying()) {
                    serverPlayerNew.setHealth(serverPlayerOld.getHealth());
                } else {
                    serverPlayerNew.setHealth(serverPlayerOld.getMaxHealth());
                }
                // A death ends the in-combat windows the old body was carrying. Cooldowns are not
                // touched: a survive-lethal perk or a Chaos Roll that death made ready again would
                // turn dying into the cheapest way to use it.
                if (event.isWasDeath()) {
                    PerkEffectsHandler.clearCombatWindows(serverPlayerOld.getUUID());
                    EnchantingLorePerkHandler.clearCombatWindows(serverPlayerOld.getUUID());
                    // A death ends the repair budget too. The gear that earned the fraction is on
                    // the ground, and a fraction carried onto the replacement body would be credit
                    // for wear that the new body has not done.
                    com.otectus.runicskills.common.durability.RepairBudget.clear(serverPlayerOld.getUUID());
                    // And the workshop: a player who has just died is not operating anything.
                    com.otectus.runicskills.common.workshop.WorkshopFocusService.clear(serverPlayerOld.getUUID());
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
                com.otectus.runicskills.integration.quests.RunicQuestBridge.refreshAll(serverPlayer);

                // Operators are told once, in chat, only when Forge itself has decided a newer
                // version exists — `VersionChecker` compares real versions rather than testing for
                // inequality, so an older or development build no longer produces a notice at all
                // (RS10-018).
                if (HandlerCommonConfig.HANDLER.instance().checkForUpdates
                        && serverPlayer.hasPermissions(2)) {
                    String available = RunicSkills.availableUpdate();
                    if (available != null) {
                        serverPlayer.sendSystemMessage(Component.translatable(
                                "message.runicskills.update_available", available));
                    }
                }
            }
        }
    }
}
