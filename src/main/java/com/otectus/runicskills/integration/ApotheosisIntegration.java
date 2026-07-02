package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.util.ApothGateMath;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.packet.client.NoticeOverlayCP;
import com.otectus.runicskills.network.packet.client.SkillOverlayCP;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.event.GetItemSocketsEvent;
import dev.shadowsoffire.apotheosis.adventure.event.ItemSocketingEvent;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemInstance;
import dev.shadowsoffire.placebo.events.GetEnchantmentLevelEvent;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles integration with Apotheosis mod.
 * Provides: affix rarity gating, gem socket bonus, gem attunement perk.
 */
public class ApotheosisIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();

    // B4 fix: bounded recent-interactor cache keyed by player UUID. Replaces the
    // single-field `lastInteractingPlayer` to scope socketing-player attribution
    // to a short interaction window and tolerate concurrent interactions across
    // multiple players. Entries past the window are pruned periodically.
    private static final Map<UUID, Long> recentInteractors = new ConcurrentHashMap<>();
    private static final long INTERACTION_WINDOW_TICKS = 20L; // 1 second @ 20 tps

    // B3 fix: rarities Apotheosis recognises by canonical path name. Unknown
    // paths default-deny on rarity gating and log once to surface API drift.
    private static final Set<String> UNKNOWN_RARITIES_LOGGED = ConcurrentHashMap.newKeySet();

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("apotheosis");
    }

    private static void recordInteraction(Player p) {
        if (p == null || p.level() == null) return;
        recentInteractors.put(p.getUUID(), p.level().getGameTime());
    }

    /**
     * Returns the most-recent interactor whose interaction is still inside
     * {@link #INTERACTION_WINDOW_TICKS}, or null if none. Used to attribute
     * Apotheosis events that lack a player context (e.g. ItemSocketingEvent).
     */
    private static Player resolveInteractor() {
        if (RunicSkills.server == null) return null;
        long now = RunicSkills.server.getTickCount();
        UUID best = null;
        long bestTime = Long.MIN_VALUE;
        for (Map.Entry<UUID, Long> e : recentInteractors.entrySet()) {
            long age = now - e.getValue();
            if (age > INTERACTION_WINDOW_TICKS) continue;
            if (e.getValue() > bestTime) {
                bestTime = e.getValue();
                best = e.getKey();
            }
        }
        if (best == null) return null;
        return RunicSkills.server.getPlayerList().getPlayer(best);
    }

    /** Periodic prune to keep the map bounded. Called from onPlayerTickPhase2a. */
    private static void pruneInteractors() {
        if (RunicSkills.server == null) return;
        long now = RunicSkills.server.getTickCount();
        recentInteractors.entrySet().removeIf(e -> now - e.getValue() > INTERACTION_WINDOW_TICKS);
    }

    private static void warnOnceForUnknownRarity(ResourceLocation id) {
        String key = id == null ? "null" : id.toString();
        if (UNKNOWN_RARITIES_LOGGED.add(key)) {
            LOGGER.warn("Apotheosis rarity '{}' is not mapped in HandlerCommonConfig — defaulting to deny. " +
                    "Update apothRarity*Level config fields if this rarity should be allowed.", key);
        }
    }

    // ── Affix Rarity Gating ──

    /**
     * Gets the required Fortune level for an item based on its affix rarity.
     * Returns 0 if the item has no affixes or rarity gating is disabled.
     *
     * B3 fix: matches rarity by ResourceLocation path (canonical name) instead
     * of ordinal. Apotheosis's LootRarity is an Apotheosis-reload-managed enum
     * whose ordinal shifts if upstream reorders or inserts tiers; matching by
     * name keeps gating correct across Apotheosis updates and default-denies
     * unknown tiers (returns Integer.MAX_VALUE) so a new rarity can never be
     * silently equipped without a config update.
     */
    /** Shared rarity → required Fortune level mapping (used by both affix-gear and gem gating). */
    private int rarityLevel(ResourceLocation rarityId) {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        return switch (rarityId.getPath()) {
            case "common" -> 0;
            case "uncommon" -> config.apothRarityUncommonLevel;
            case "rare" -> config.apothRarityRareLevel;
            case "epic" -> config.apothRarityEpicLevel;
            case "mythic" -> config.apothRarityMythicLevel;
            case "ancient" -> config.apothRarityAncientLevel;
            default -> {
                warnOnceForUnknownRarity(rarityId);
                yield ApothGateMath.UNMAPPED; // default-deny, reported as "unconfigured rarity"
            }
        };
    }

    /** Display name of an item's affix rarity, or "Unknown" if not bound. */
    private static String rarityNameOf(ItemStack stack) {
        DynamicHolder<LootRarity> rarityHolder = AffixHelper.getRarity(stack);
        return rarityHolder.isBound() ? rarityHolder.get().toComponent().getString() : "Unknown";
    }

    private int getRequiredFortuneLevel(ItemStack stack) {
        if (!HandlerCommonConfig.HANDLER.instance().apothEnableAffixRarityGating) return 0;
        if (!AffixHelper.hasAffixes(stack)) return 0;

        DynamicHolder<LootRarity> rarityHolder = AffixHelper.getRarity(stack);
        if (!rarityHolder.isBound()) return 0;

        ResourceLocation rarityId = rarityHolder.getId();
        if (rarityId == null) return 0;
        return rarityLevel(rarityId);
    }

    /**
     * Required Fortune level to socket a gem, scaled by the gem's rarity (i.e. how powerful it is).
     * Returns 0 (ungated) for non-gems, common gems, or when gem gating is disabled. Gems aren't
     * affix items, so the affix path above never covers them — this reads the gem's own LootRarity.
     */
    private int getRequiredFortuneLevelForGem(ItemStack stack) {
        if (!HandlerCommonConfig.HANDLER.instance().apothEnableGemRarityGating) return 0;
        GemInstance gem = GemInstance.unsocketed(stack);
        if (!gem.isValidUnsocketed()) return 0;
        DynamicHolder<LootRarity> rarityHolder = gem.rarity();
        if (!rarityHolder.isBound() || rarityHolder.getId() == null) return 0;
        return rarityLevel(rarityHolder.getId());
    }

    /** B3 fix: canonical rarity names treated as "rare or higher" for affix-affinity counting. */
    private static final Set<String> RARE_OR_HIGHER = Set.of("rare", "epic", "mythic", "ancient");

    private boolean canPlayerUseAffixItem(Player player, ItemStack stack) {
        int requiredLevel = getRequiredFortuneLevel(stack);
        if (requiredLevel <= 0) return true;

        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return true; // capability race — fail open

        int fortuneLevel = capability.getSkillLevel(RegistrySkills.FORTUNE.get());
        return !ApothGateMath.decide(requiredLevel, fortuneLevel).blocks();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEquipAffixItem(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isCreative() || player instanceof FakePlayer) return;
        if (event.getSlot().getType() != EquipmentSlot.Type.ARMOR) return;

        recordInteraction(player);

        ItemStack item = event.getTo();
        // Compute the gate decision (required level + rarity name) BEFORE any mutation of the
        // stack. The previous code recomputed the required level AFTER item.setCount(0) had
        // emptied the stack, so getRequiredFortuneLevel returned 0 -> the bogus "Fortune 0"
        // denial. A required level of 0 means "no gate" and must never block or message.
        int required = getRequiredFortuneLevel(item);
        if (required <= 0) return;
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return; // capability race — fail open
        int fortune = capability.getSkillLevel(RegistrySkills.FORTUNE.get());
        ApothGateMath.Decision decision = ApothGateMath.decide(required, fortune);
        if (!decision.blocks()) return;

        String rarityName = rarityNameOf(item);
        player.drop(item.copy(), false);
        item.setCount(0);
        if (player instanceof ServerPlayer serverPlayer) {
            if (decision.outcome() == ApothGateMath.Outcome.UNMAPPED_RARITY) {
                // Default-deny for a rarity with no apothRarity*Level mapping — report it as
                // unconfigured rather than as a meaningless "Fortune 2147483647".
                NoticeOverlayCP.send(serverPlayer, "overlay.runicskills.affix_rarity_unmapped", rarityName);
            } else {
                // "Requires Fortune %s to use %s Apotheosis-affixed gear." -> level + rarity name.
                NoticeOverlayCP.send(serverPlayer, "overlay.runicskills.affix_rarity_gated",
                        String.valueOf(decision.requiredLevel()), rarityName);
            }
        }
    }

    // Client-only affix-gate tooltip. ItemTooltipEvent fires only on the client, and this class is
    // only registered when Apotheosis is loaded, so referencing AffixHelper stays server-safe. The
    // line identifies the gate source so players can tell it apart from manual / integration locks.
    @SubscribeEvent
    public void onItemTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (!HandlerCommonConfig.HANDLER.instance().apothEnableAffixRarityGating) return;
        if (!AffixHelper.hasAffixes(stack)) return;
        int required = getRequiredFortuneLevel(stack);
        if (required <= 0) return;
        String rarityName = rarityNameOf(stack);
        if (required == ApothGateMath.UNMAPPED) {
            event.getToolTip().add(net.minecraft.network.chat.Component.translatable(
                    "tooltip.runicskills.affix_gate_unmapped", rarityName)
                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
        } else {
            event.getToolTip().add(net.minecraft.network.chat.Component.translatable(
                    "tooltip.runicskills.affix_gate", required, rarityName)
                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onUseAffixItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.isCreative() || player instanceof FakePlayer) return;

        recordInteraction(player);

        if (!canPlayerUseAffixItem(player, event.getItemStack())) {
            event.setCanceled(true);
            if (player instanceof ServerPlayer serverPlayer) {
                int required = getRequiredFortuneLevel(event.getItemStack());
                SkillOverlayCP.send(serverPlayer, new ResourceLocation("apotheosis", "affix_item").toString());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        recordInteraction(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttackWithAffixItem(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.isCreative() || player instanceof FakePlayer) return;

        recordInteraction(player);

        if (!canPlayerUseAffixItem(player, player.getMainHandItem())) {
            event.setCanceled(true);
            if (player instanceof ServerPlayer serverPlayer) {
                SkillOverlayCP.send(serverPlayer, new ResourceLocation("apotheosis", "affix_item").toString());
            }
        }
    }

    // ── Gem rarity gating — block socketing a gem too powerful for the player's Fortune level ──
    // Gems aren't affix items, so the affix-rarity gating above never covers them. CanSocket is an
    // @HasResult event consumed by Apotheosis's SocketingRecipe; setting DENY blocks the socket.
    @SubscribeEvent
    public void onCanSocketGem(ItemSocketingEvent.CanSocket event) {
        Player player = resolveInteractor();
        if (player == null || player.isRemoved() || player.isCreative() || player instanceof FakePlayer) return;
        int required = getRequiredFortuneLevelForGem(event.getInputGem());
        if (required <= 0) return;
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return; // capability race — fail open, matching the affix-gating path
        if (cap.getSkillLevel(RegistrySkills.FORTUNE.get()) >= required) return;
        event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        if (player instanceof ServerPlayer serverPlayer) {
            DynamicHolder<LootRarity> rarityHolder = GemInstance.unsocketed(event.getInputGem()).rarity();
            String rarityName = rarityHolder.isBound() ? rarityHolder.get().toComponent().getString() : "Unknown";
            // "You need Fortune %s to socket %s gems!" → required level + gem rarity name.
            NoticeOverlayCP.send(serverPlayer, "overlay.runicskills.gem_rarity_gated", String.valueOf(required), rarityName);
        }
    }

    // ── Gem Socket Bonus ──

    @SubscribeEvent
    public void onGetItemSockets(GetItemSocketsEvent event) {
        ItemStack stack = event.getStack();
        if (stack.isEmpty()) return;
        Player owner = findItemOwner(stack);
        if (owner == null) return;

        // Legacy Fortune-threshold bonus socket.
        if (HandlerCommonConfig.HANDLER.instance().apothEnableGemBonusSlot) {
            SkillCapability cap = SkillCapability.get(owner);
            if (cap != null) {
                int fortuneLevel = cap.getSkillLevel(RegistrySkills.FORTUNE.get());
                if (fortuneLevel >= HandlerCommonConfig.HANDLER.instance().apothGemBonusSlotThreshold) {
                    event.setSockets(event.getSockets() + 1);
                }
            }
        }

        // Socket Virtuoso perk: explicit +N sockets regardless of Fortune level.
        if (RegistryPerks.SOCKET_VIRTUOSO != null && RegistryPerks.SOCKET_VIRTUOSO.get().isEnabled(owner)) {
            int bonus = HandlerCommonConfig.HANDLER.instance().socketVirtuosoBonus;
            if (bonus > 0) event.setSockets(event.getSockets() + bonus);
        }

        // Apothic Apprentice perk (1.2.0): higher-tier socket bonus, additive with Socket Virtuoso.
        if (RegistryPerks.APOTHIC_APPRENTICE != null && RegistryPerks.APOTHIC_APPRENTICE.get().isEnabled(owner)) {
            int bonus = HandlerCommonConfig.HANDLER.instance().apothicApprenticeBonus;
            if (bonus > 0) event.setSockets(event.getSockets() + bonus);
        }
    }

    // ── APOTHEOSIS_WISDOM — raise the effective enchantment cap on the holder's gear ──
    // Apotheosis/Placebo route every effective-enchantment-level query through Placebo's
    // GetEnchantmentLevelEvent (this is the same event Apotheosis itself uses to apply its
    // own cap extensions). Bumping the present enchantments' levels here raises the effective
    // cap for that query — the faithful "Apotheosis enchantment cap increased by %s." The cap
    // mechanic itself is global, so a per-player effect has no other hook; the event carries
    // only the stack, so we attribute via the existing recent-interactor owner lookup.
    @SubscribeEvent
    public void onGetEnchantmentLevel(GetEnchantmentLevelEvent event) {
        if (RegistryPerks.APOTHEOSIS_WISDOM == null) return;
        ItemStack stack = event.getStack();
        if (stack.isEmpty() || event.getEnchantments().isEmpty()) return;
        int add = Math.round(HandlerCommonConfig.HANDLER.instance().apotheosisWisdomAmplifier);
        if (add <= 0) return;
        Player owner = findItemOwner(stack);
        if (owner == null || owner instanceof FakePlayer || !RegistryPerks.APOTHEOSIS_WISDOM.get().isEnabled(owner)) return;
        // Only raise enchantments already present (lvl > 0); never introduce new ones.
        event.getEnchantments().replaceAll((ench, lvl) -> lvl > 0 ? lvl + add : lvl);
    }

    // ── 1.2.0: Gem-Threaded Armor — apply transient ARMOR modifier scaling with equipped socket count ──
    private static final UUID APOTH_GEM_THREADED_UUID = UUID.fromString("3a8b1c5d-9f7e-4d2a-8b1c-5d9f7e4d2a8b");

    @SubscribeEvent
    public void onEquipChangeGemThreaded(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || player instanceof FakePlayer) return;
        if (RegistryPerks.GEM_THREADED_ARMOR == null) return;
        AttributeInstance armor = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (armor == null) return;

        AttributeModifier existing = armor.getModifier(APOTH_GEM_THREADED_UUID);
        if (!RegistryPerks.GEM_THREADED_ARMOR.get().isEnabled(player)) {
            if (existing != null) armor.removeModifier(existing);
            return;
        }

        int totalSockets = countEquippedSockets(player);
        double bonus = totalSockets * HandlerCommonConfig.HANDLER.instance().gemThreadedArmorPerSocket;
        if (bonus > 0) {
            if (existing == null || existing.getAmount() != bonus) {
                if (existing != null) armor.removeModifier(existing);
                armor.addTransientModifier(new AttributeModifier(APOTH_GEM_THREADED_UUID,
                        "runicskills:gem_threaded_armor", bonus,
                        AttributeModifier.Operation.ADDITION));
            }
        } else if (existing != null) {
            armor.removeModifier(existing);
        }
    }

    /** Counts total sockets across equipped Apotheosis-affixed items (sums all slots). */
    static int countEquippedSockets(Player player) {
        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !AffixHelper.hasAffixes(stack)) continue;
            count += dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper.getSockets(stack);
        }
        return count;
    }

    /**
     * Finds the player who has the given ItemStack in their equipment slots.
     * Uses reference equality to match the exact ItemStack instance.
     *
     * B4 fix: fast-path uses the recent-interactor cache (bounded, per-UUID)
     * instead of a single static field; falls back to scanning all online
     * players if the cached interactor doesn't own the stack.
     */
    private Player findItemOwner(ItemStack stack) {
        // Fast path: any recent interactor whose equipment matches the stack.
        Player recent = resolveInteractor();
        if (recent != null && !recent.isRemoved()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (recent.getItemBySlot(slot) == stack) {
                    return recent;
                }
            }
        }

        // Fallback: search all online players
        if (RunicSkills.server == null) return null;
        for (ServerPlayer player : RunicSkills.server.getPlayerList().getPlayers()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (player.getItemBySlot(slot) == stack) {
                    return player;
                }
            }
        }
        return null;
    }

    // ── Gem Attunement Perk ──

    @SubscribeEvent
    public void onItemSocketing(ItemSocketingEvent.ModifyResult event) {
        if (RegistryPerks.GEM_ATTUNEMENT == null) return;

        // B4 fix: attribute socketing to the most-recent interactor within the 1-second
        // window. Two-player concurrent interactions no longer cross-pollinate; if no
        // recent interactor exists, default-deny the perk-aware branch and fall through
        // to vanilla Apotheosis behavior.
        Player player = resolveInteractor();
        if (player == null || player.isRemoved()) return;
        if (player.isCreative() || player instanceof FakePlayer) return;

        if (!RegistryPerks.GEM_ATTUNEMENT.get().isEnabled(player)) return;

        // Probability roll: 1 in X chance to preserve the gem
        int probability = (int) RegistryPerks.GEM_ATTUNEMENT.get().getActiveValue(player)[0];
        if (probability <= 0) return;

        int roll = (int) Math.floor(Math.random() * probability);
        if (roll == 0) {
            // Give the gem back to the player
            ItemStack gem = event.getInputGem().copy();
            if (player instanceof ServerPlayer serverPlayer) {
                if (!serverPlayer.getInventory().add(gem)) {
                    serverPlayer.drop(gem, false);
                }
                serverPlayer.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("overlay.runicskills.gem_attunement_saved"),
                        true);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ── Phase 2a: Affix Affinity + interactor pruning ──
    // ════════════════════════════════════════════════════════════════════════
    // The ten Apothic Attributes (attributeslib) perks that used to live in this tick handler
    // moved to ApothicAttributesPerksIntegration (1.5.3, M-5) so an AttributesLib version
    // mismatch can no longer take down affix-rarity / gem gating with it. This handler keeps
    // only Apotheosis-typed work: Affix Affinity (vanilla ATTACK_DAMAGE, but counts affixed
    // gear via AffixHelper) and the periodic recentInteractors prune.

    private static final UUID APOTH_AFFIX_AFFINITY_DMG_UUID = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0f");

    @SubscribeEvent
    public void onPlayerTickPhase2a(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if ((player.tickCount % 10) != 0) return;

        // B4 fix: prune the interactor map periodically so it stays bounded even
        // if the server runs for days with no socketing events.
        if ((player.tickCount % 100) == 0) pruneInteractors();

        HandlerCommonConfig c = HandlerCommonConfig.HANDLER.instance();

        // Affix Affinity → count rare+ equipped items, apply per-item damage bonus
        // on ATTACK_DAMAGE. Reduction side is handled in onLivingHurtAffixAffinity.
        if (RegistryPerks.AFFIX_AFFINITY != null && RegistryPerks.AFFIX_AFFINITY.get().isEnabled(player)) {
            int rareCount = countRareAffixItems(player);
            double bonus = rareCount * (c.affixAffinityDamagePercent / 100.0);
            AttributeInstance attack = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (attack != null) {
                AttributeModifier existing = attack.getModifier(APOTH_AFFIX_AFFINITY_DMG_UUID);
                if (bonus > 0) {
                    if (existing == null || existing.getAmount() != bonus) {
                        if (existing != null) attack.removeModifier(existing);
                        attack.addTransientModifier(new AttributeModifier(APOTH_AFFIX_AFFINITY_DMG_UUID,
                                "runicskills:affix_affinity", bonus,
                                AttributeModifier.Operation.MULTIPLY_BASE));
                    }
                } else if (existing != null) {
                    attack.removeModifier(existing);
                }
            }
        }
    }

    /** Counts equipped items of Rare rarity or higher. B3 fix: name-keyed instead of ordinal. */
    private static int countRareAffixItems(Player player) {
        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !AffixHelper.hasAffixes(stack)) continue;
            DynamicHolder<LootRarity> rarityHolder = AffixHelper.getRarity(stack);
            if (!rarityHolder.isBound()) continue;
            ResourceLocation id = rarityHolder.getId();
            if (id != null && RARE_OR_HIGHER.contains(id.getPath())) {
                count++;
            }
        }
        return count;
    }

    /** Affix Affinity damage-reduction side: apply on incoming damage. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHurtAffixAffinity(LivingHurtEvent event) {
        if (RegistryPerks.AFFIX_AFFINITY == null) return;
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;
        if (!RegistryPerks.AFFIX_AFFINITY.get().isEnabled(player)) return;
        int count = countRareAffixItems(player);
        if (count <= 0) return;
        double reduction = Math.min(0.9, count
                * (HandlerCommonConfig.HANDLER.instance().affixAffinityReductionPercent / 100.0));
        event.setAmount((float) (event.getAmount() * (1.0 - reduction)));
    }
}
