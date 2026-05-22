package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.packet.client.PlayerMessagesCP;
import com.otectus.runicskills.network.packet.client.SkillOverlayCP;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.event.GetItemSocketsEvent;
import dev.shadowsoffire.apotheosis.adventure.event.ItemSocketingEvent;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.attributeslib.api.ALObjects;
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
    private int getRequiredFortuneLevel(ItemStack stack) {
        if (!HandlerCommonConfig.HANDLER.instance().apothEnableAffixRarityGating) return 0;
        if (!AffixHelper.hasAffixes(stack)) return 0;

        DynamicHolder<LootRarity> rarityHolder = AffixHelper.getRarity(stack);
        if (!rarityHolder.isBound()) return 0;

        ResourceLocation rarityId = rarityHolder.getId();
        if (rarityId == null) return 0;
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
                yield Integer.MAX_VALUE; // default-deny
            }
        };
    }

    /** B3 fix: canonical rarity names treated as "rare or higher" for affix-affinity counting. */
    private static final Set<String> RARE_OR_HIGHER = Set.of("rare", "epic", "mythic", "ancient");

    private boolean canPlayerUseAffixItem(Player player, ItemStack stack) {
        int requiredLevel = getRequiredFortuneLevel(stack);
        if (requiredLevel <= 0) return true;

        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return true;

        int fortuneLevel = capability.getSkillLevel(RegistrySkills.FORTUNE.get());
        return fortuneLevel >= requiredLevel;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEquipAffixItem(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isCreative() || player instanceof FakePlayer) return;
        if (event.getSlot().getType() != EquipmentSlot.Type.ARMOR) return;

        recordInteraction(player);

        ItemStack item = event.getTo();
        if (!canPlayerUseAffixItem(player, item)) {
            player.drop(item.copy(), false);
            item.setCount(0);
            if (player instanceof ServerPlayer serverPlayer) {
                int required = getRequiredFortuneLevel(item);
                DynamicHolder<LootRarity> rarityHolder = AffixHelper.getRarity(item);
                String rarityName = rarityHolder.isBound() ? rarityHolder.get().toComponent().getString() : "Unknown";
                PlayerMessagesCP.send(serverPlayer, "overlay.runicskills.affix_rarity_gated", required);
            }
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
    // ── Phase 2a: Apothic Attributes combat perks ──
    // ════════════════════════════════════════════════════════════════════════
    // Ten pure-attribute perks reconciled on a throttled tick against
    // ALObjects.Attributes.* plus Affix Affinity (count rare+ equipped items
    // and inject transient damage / damage-reduction).

    // Stable UUIDs for each permanent modifier on attributeslib attributes.
    private static final UUID APOTH_CRIT_CHANCE_UUID   = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c01");
    private static final UUID APOTH_CRIT_DAMAGE_UUID   = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c02");
    private static final UUID APOTH_LIFE_STEAL_UUID    = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c03");
    private static final UUID APOTH_CURR_HP_DMG_UUID   = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c04");
    private static final UUID APOTH_DODGE_UUID         = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c05");
    private static final UUID APOTH_ARROW_DMG_UUID     = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c06");
    private static final UUID APOTH_ARROW_VEL_UUID     = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c07");
    private static final UUID APOTH_MINING_SPEED_UUID  = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c08");
    private static final UUID APOTH_XP_GAINED_UUID     = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c09");
    private static final UUID APOTH_PROT_PIERCE_UUID   = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0a");
    private static final UUID APOTH_PROT_SHRED_UUID    = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0b");
    private static final UUID APOTH_GHOST_HP_UUID      = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0c");
    private static final UUID APOTH_HEAL_RECV_UUID     = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0d");
    private static final UUID APOTH_OVERHEAL_UUID      = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0e");
    private static final UUID APOTH_AFFIX_AFFINITY_DMG_UUID = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0f");

    /** Idempotent permanent-modifier reconciliation, mirroring the ISS integration helper. */
    private static void reconcile(Player player, RegistryObject<Attribute> attrObj, UUID uuid,
                                  String name, boolean wanted, double value,
                                  AttributeModifier.Operation op) {
        if (attrObj == null || !attrObj.isPresent()) return;
        AttributeInstance inst = player.getAttribute(attrObj.get());
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(uuid);
        if (wanted) {
            if (existing != null && existing.getAmount() == value && existing.getOperation() == op) return;
            if (existing != null) inst.removeModifier(existing);
            inst.addPermanentModifier(new AttributeModifier(uuid, name, value, op));
        } else if (existing != null) {
            inst.removeModifier(existing);
        }
    }

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

        // Apothic Critical Mastery → CRIT_CHANCE + CRIT_DAMAGE
        boolean critMastery = RegistryPerks.APOTHIC_CRITICAL_MASTERY != null
                && RegistryPerks.APOTHIC_CRITICAL_MASTERY.get().isEnabled(player);
        reconcile(player, ALObjects.Attributes.CRIT_CHANCE, APOTH_CRIT_CHANCE_UUID,
                "runicskills:apoth_crit_chance", critMastery, c.apothCriticalMasteryChancePercent / 100.0,
                AttributeModifier.Operation.ADDITION);
        reconcile(player, ALObjects.Attributes.CRIT_DAMAGE, APOTH_CRIT_DAMAGE_UUID,
                "runicskills:apoth_crit_damage", critMastery, c.apothCriticalMasteryDamagePercent / 100.0,
                AttributeModifier.Operation.ADDITION);

        // Vampiric Fangs → LIFE_STEAL
        reconcileSimple(player, RegistryPerks.VAMPIRIC_FANGS, ALObjects.Attributes.LIFE_STEAL,
                APOTH_LIFE_STEAL_UUID, "runicskills:vampiric_fangs",
                c.vampiricFangsPercent / 100.0);

        // Reaper's Edge → CURRENT_HP_DAMAGE
        reconcileSimple(player, RegistryPerks.REAPERS_EDGE, ALObjects.Attributes.CURRENT_HP_DAMAGE,
                APOTH_CURR_HP_DMG_UUID, "runicskills:reapers_edge",
                c.reapersEdgePercent / 100.0);

        // Evasive → DODGE_CHANCE
        reconcileSimple(player, RegistryPerks.EVASIVE, ALObjects.Attributes.DODGE_CHANCE,
                APOTH_DODGE_UUID, "runicskills:evasive",
                c.evasivePercent / 100.0);

        // Arrow Mastery → ARROW_DAMAGE + ARROW_VELOCITY (multiplicative)
        boolean arrowMastery = RegistryPerks.ARROW_MASTERY != null
                && RegistryPerks.ARROW_MASTERY.get().isEnabled(player);
        reconcile(player, ALObjects.Attributes.ARROW_DAMAGE, APOTH_ARROW_DMG_UUID,
                "runicskills:arrow_mastery_dmg", arrowMastery, c.arrowMasteryDamagePercent / 100.0,
                AttributeModifier.Operation.MULTIPLY_BASE);
        reconcile(player, ALObjects.Attributes.ARROW_VELOCITY, APOTH_ARROW_VEL_UUID,
                "runicskills:arrow_mastery_vel", arrowMastery, c.arrowMasteryVelocityPercent / 100.0,
                AttributeModifier.Operation.MULTIPLY_BASE);

        // Earthbreaker → MINING_SPEED (multiplicative)
        reconcileSimpleMul(player, RegistryPerks.EARTHBREAKER, ALObjects.Attributes.MINING_SPEED,
                APOTH_MINING_SPEED_UUID, "runicskills:earthbreaker",
                c.earthbreakerPercent / 100.0);

        // Apothic Scholar → EXPERIENCE_GAINED (multiplicative)
        reconcileSimpleMul(player, RegistryPerks.APOTHIC_SCHOLAR, ALObjects.Attributes.EXPERIENCE_GAINED,
                APOTH_XP_GAINED_UUID, "runicskills:apoth_scholar",
                c.apothScholarPercent / 100.0);

        // Spectral Ward → PROT_PIERCE (flat) + PROT_SHRED (percent 0..1)
        boolean spectralWard = RegistryPerks.SPECTRAL_WARD != null
                && RegistryPerks.SPECTRAL_WARD.get().isEnabled(player);
        reconcile(player, ALObjects.Attributes.PROT_PIERCE, APOTH_PROT_PIERCE_UUID,
                "runicskills:spectral_ward_pierce", spectralWard, c.spectralWardPierce,
                AttributeModifier.Operation.ADDITION);
        reconcile(player, ALObjects.Attributes.PROT_SHRED, APOTH_PROT_SHRED_UUID,
                "runicskills:spectral_ward_shred", spectralWard, c.spectralWardShredPercent / 100.0,
                AttributeModifier.Operation.ADDITION);

        // Ghostbound → GHOST_HEALTH (flat)
        reconcileSimpleFlat(player, RegistryPerks.GHOSTBOUND, ALObjects.Attributes.GHOST_HEALTH,
                APOTH_GHOST_HP_UUID, "runicskills:ghostbound",
                c.ghostboundBonus);

        // Heart of the Healer → HEALING_RECEIVED + OVERHEAL (multiplicative/percent)
        boolean heartHealer = RegistryPerks.HEART_OF_THE_HEALER != null
                && RegistryPerks.HEART_OF_THE_HEALER.get().isEnabled(player);
        reconcile(player, ALObjects.Attributes.HEALING_RECEIVED, APOTH_HEAL_RECV_UUID,
                "runicskills:heart_healer_recv", heartHealer, c.heartHealerReceivedPercent / 100.0,
                AttributeModifier.Operation.ADDITION);
        reconcile(player, ALObjects.Attributes.OVERHEAL, APOTH_OVERHEAL_UUID,
                "runicskills:heart_healer_overheal", heartHealer, c.heartHealerOverhealPercent / 100.0,
                AttributeModifier.Operation.ADDITION);

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

    private static void reconcileSimple(Player player, RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk,
                                        RegistryObject<Attribute> attr, UUID uuid, String name, double value) {
        boolean enabled = perk != null && perk.get().isEnabled(player);
        reconcile(player, attr, uuid, name, enabled, enabled ? value : 0,
                AttributeModifier.Operation.ADDITION);
    }

    private static void reconcileSimpleMul(Player player, RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk,
                                           RegistryObject<Attribute> attr, UUID uuid, String name, double value) {
        boolean enabled = perk != null && perk.get().isEnabled(player);
        reconcile(player, attr, uuid, name, enabled, enabled ? value : 0,
                AttributeModifier.Operation.MULTIPLY_BASE);
    }

    private static void reconcileSimpleFlat(Player player, RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk,
                                            RegistryObject<Attribute> attr, UUID uuid, String name, double value) {
        boolean enabled = perk != null && perk.get().isEnabled(player);
        reconcile(player, attr, uuid, name, enabled, enabled ? value : 0,
                AttributeModifier.Operation.ADDITION);
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
