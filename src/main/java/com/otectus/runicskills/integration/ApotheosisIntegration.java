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
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

/**
 * Handles integration with Apotheosis mod.
 * Provides: affix rarity gating, gem socket bonus, gem attunement perk.
 */
public class ApotheosisIntegration {

    // Track the last player who interacted with items, used to provide player
    // context for Apotheosis events that lack it. Safe because the Minecraft
    // server is single-threaded.
    private static Player lastInteractingPlayer = null;

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("apotheosis");
    }

    // ── Affix Rarity Gating ──

    /**
     * Gets the required Fortune level for an item based on its affix rarity.
     * Returns 0 if the item has no affixes or rarity gating is disabled.
     */
    private int getRequiredFortuneLevel(ItemStack stack) {
        if (!HandlerCommonConfig.HANDLER.instance().apothEnableAffixRarityGating) return 0;
        if (!AffixHelper.hasAffixes(stack)) return 0;

        DynamicHolder<LootRarity> rarityHolder = AffixHelper.getRarity(stack);
        if (!rarityHolder.isBound()) return 0;

        LootRarity rarity = rarityHolder.get();
        int ordinal = rarity.ordinal();
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();

        // ordinal 0 = common (no gate), 1 = uncommon, 2 = rare, 3 = epic, 4 = mythic, 5 = ancient
        return switch (ordinal) {
            case 1 -> config.apothRarityUncommonLevel;
            case 2 -> config.apothRarityRareLevel;
            case 3 -> config.apothRarityEpicLevel;
            case 4 -> config.apothRarityMythicLevel;
            case 5 -> config.apothRarityAncientLevel;
            default -> 0;
        };
    }

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

        lastInteractingPlayer = player;

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

        lastInteractingPlayer = player;

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
        lastInteractingPlayer = event.getEntity();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttackWithAffixItem(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.isCreative() || player instanceof FakePlayer) return;

        lastInteractingPlayer = player;

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
        if (!HandlerCommonConfig.HANDLER.instance().apothEnableGemBonusSlot) return;

        ItemStack stack = event.getStack();
        if (stack.isEmpty()) return;

        // Find the player who owns this item by checking equipped slots
        Player owner = findItemOwner(stack);
        if (owner == null) return;

        SkillCapability cap = SkillCapability.get(owner);
        if (cap == null) return;

        int fortuneLevel = cap.getSkillLevel(RegistrySkills.FORTUNE.get());
        if (fortuneLevel >= HandlerCommonConfig.HANDLER.instance().apothGemBonusSlotThreshold) {
            event.setSockets(event.getSockets() + 1);
        }
    }

    /**
     * Finds the player who has the given ItemStack in their equipment slots.
     * Uses reference equality to match the exact ItemStack instance.
     */
    private Player findItemOwner(ItemStack stack) {
        // First check the last known interacting player (fast path)
        if (lastInteractingPlayer != null && !lastInteractingPlayer.isRemoved()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (lastInteractingPlayer.getItemBySlot(slot) == stack) {
                    return lastInteractingPlayer;
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

        // Use the last interacting player as the socketing player
        Player player = lastInteractingPlayer;
        if (player == null || player.isRemoved()) return;
        if (player.isCreative() || player instanceof FakePlayer) return;

        if (!RegistryPerks.GEM_ATTUNEMENT.get().isEnabled(player)) return;

        // Probability roll: 1 in X chance to preserve the gem
        int probability = (int) RegistryPerks.GEM_ATTUNEMENT.get().getValue()[0];
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
}
