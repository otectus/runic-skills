package com.otectus.runicskills.mixin;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The enchanting-table perks.
 *
 * <p><b>The compounding bug (RS10-015).</b> Enchanter's Insight used to discount {@code costs[id]}
 * at the head of {@code clickMenuButton}, writing the reduced number back into the array vanilla
 * validates and charges from. Every click discounted the already-discounted value, so repeating a
 * rejected button — which a client can do freely — walked any offer down to 1 before vanilla ever
 * checked whether the action was legal. Worse, the discount was invisible: the client had been sent
 * the original number, so the price shown and the price paid disagreed.
 *
 * <p>Both problems have the same cause and the same fix. Discounting belongs where the offers are
 * <em>generated</em>, not where they are spent: {@code slotsChanged} recomputes all three costs from
 * scratch whenever the input changes, so a discount applied there cannot accumulate, and the reduced
 * value is the one synced to the client and the one charged. The clue enchantments are derived
 * before this runs, so a discount makes enchanting cheaper without making it weaker.
 */
@Mixin(EnchantmentMenu.class)
public abstract class MixEnchantmentMenu {

    @Shadow
    @Final
    public int[] costs;

    @Shadow
    @Final
    private Container enchantSlots;

    /** Whoever opened this table. One menu belongs to one player for its whole lifetime. */
    @Unique
    private Player runicskills$user;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("RETURN"))
    private void runicskills$captureUser(int containerId, Inventory inventory,
                                         ContainerLevelAccess access, CallbackInfo ci) {
        this.runicskills$user = inventory.player;
    }

    /**
     * The Enchanting Power passive, which had a registered attribute, a texture and a tooltip and
     * was read by nothing (HIGH-05).
     *
     * <p>Vanilla derives the three offers from one number — the bookshelf count — inside the
     * {@code slotsChanged} lambda, and hands it to {@code getEnchantmentCost} as the {@code power}
     * argument. Adding the passive there is what "counts as extra bookshelves" means: the costs,
     * the clue enchantments and the final enchantment list all follow from it, so a single argument
     * change moves every downstream number consistently.
     *
     * <p>The synthetic lambda is the only place power feeds offer generation, hence the target. Both
     * of its names are spelled out because the annotation processor cannot help here: synthetic
     * methods are invisible to {@code javax.lang.model}, so no refmap entry is generated for the
     * selector and a single name would resolve in exactly one of the two environments. The
     * development name came from {@code javap -p} on the mapped {@code EnchantmentMenu}; the
     * production name is its SRG counterpart, {@code m_39483_}, read from
     * {@code srg_to_official_1.20.1.tsrg} against the same descriptor. {@code remap = false} keeps
     * the processor from rewriting the selector; the {@code @At} target is remapped as usual.
     *
     * <p>Vanilla clamps power to 15 immediately afterwards, so the passive only matters below a
     * full shelf ring. Clamping here as well, to the attribute's own 0..1024 range, keeps a hostile
     * config from overflowing the addition.
     */
    @ModifyArg(method = {
                    "lambda$slotsChanged$0(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
                    "m_39483_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"
            },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentCost(Lnet/minecraft/util/RandomSource;IILnet/minecraft/world/item/ItemStack;)I",
                    remap = true),
            index = 2,
            remap = false,
            require = 1)
    private int runicskills$addEnchantingPower(int power) {
        Player player = this.runicskills$user;
        if (player == null) return power;
        double bonus = player.getAttributeValue(RegistryAttributes.ENCHANTING_POWER.get());
        if (!(bonus > 0)) return power;
        return power + (int) Math.min(1024.0, Math.floor(bonus));
    }

    /**
     * Applies the cost perks to the three offers, once, as they are produced.
     *
     * <p>Enchanter's Insight and Experienced Enchanter stack additively and are clamped well below
     * free: an offer that cost nothing would let a player enchant indefinitely at level zero.
     */
    @Inject(method = "slotsChanged", at = @At("RETURN"))
    private void runicskills$discountOffers(Container container, CallbackInfo ci) {
        Player player = this.runicskills$user;
        if (player == null) return;

        double reduction = 0.0;
        if (RegistryPerks.ENCHANTERS_INSIGHT != null
                && RegistryPerks.ENCHANTERS_INSIGHT.get().isEnabled(player)) {
            double[] values = RegistryPerks.ENCHANTERS_INSIGHT.get().getActiveValue(player);
            if (values.length > 0) reduction += values[0] / 100.0;
        }
        if (RegistryPerks.EXPERIENCED_ENCHANTER != null
                && RegistryPerks.EXPERIENCED_ENCHANTER.get().isEnabled(player)) {
            reduction += HandlerCommonConfig.HANDLER.instance().experiencedEnchanterPercent / 100.0;
        }
        if (reduction <= 0.0) return;
        reduction = Math.min(0.80, reduction);

        for (int slot = 0; slot < this.costs.length; slot++) {
            if (this.costs[slot] <= 0) continue;   // an empty offer stays empty
            this.costs[slot] = Math.max(1, (int) Math.round(this.costs[slot] * (1.0 - reduction)));
        }
    }

    /**
     * Lapis Conservation and Elder Knowledge, both of which pay out only on an enchant that actually
     * happened — hence {@code RETURN} with the result checked, rather than the head of the method.
     */
    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void runicskills$refundAfterEnchanting(Player player, int id,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (player == null || player.level().isClientSide()) return;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();

        // Lapis Conservation — vanilla has already consumed id+1 lapis; roll each one back
        // independently so the perk reads as a per-lapis chance rather than all-or-nothing.
        if (RegistryPerks.LAPIS_CONSERVATION != null
                && RegistryPerks.LAPIS_CONSERVATION.get().isEnabled(player)
                && !player.getAbilities().instabuild) {
            double chance = config.lapisConservationPercent / 100.0;
            int spent = id + 1;
            int refunded = 0;
            for (int i = 0; i < spent; i++) {
                if (player.getRandom().nextDouble() < chance) refunded++;
            }
            if (refunded > 0) {
                ItemStack lapis = new ItemStack(Items.LAPIS_LAZULI, refunded);
                if (!player.getInventory().add(lapis)) player.drop(lapis, false);
            }
        }

        // Elder Knowledge — a share of the levels spent comes back. Read from costs[id], which is
        // the discounted figure the player was actually charged, so the refund cannot exceed it.
        if (RegistryPerks.ELDER_KNOWLEDGE != null
                && RegistryPerks.ELDER_KNOWLEDGE.get().isEnabled(player)
                && id >= 0 && id < this.costs.length) {
            double share = Math.min(1.0, config.elderKnowledgePercent / 100.0);
            int levels = (int) Math.floor(this.costs[id] * share);
            if (levels > 0) player.giveExperienceLevels(levels);
        }

        // The two perks that change what the table produced. The enchanted item is in slot 0 — for
        // a book vanilla has already swapped it for an enchanted one — and only ever has
        // enchantments on it if the enchant actually happened, so an empty or bare result means
        // there is nothing to improve and both perks correctly do nothing.
        ItemStack result = this.enchantSlots.getItem(0);
        // isEnchanted() is the proof the enchant happened: clickMenuButton returns true even when
        // the table produced no enchantment list, and without this both perks would enchant an
        // untouched item for nothing.
        if (result.isEmpty() || !result.isEnchanted()) return;
        runicskills$addExtraEnchantments(player, result, config);
        runicskills$deepenBookEnchantment(player, result, config);
    }

    /**
     * Enchantment Insight — "Enchanting table shows extra enchantment options".
     *
     * <p>The table has exactly three offers, laid out in the screen's own texture, and no mod can
     * add a fourth without replacing the GUI. What a player wants from more options is a better
     * result, so the perk delivers the result instead: the enchant they chose comes out carrying
     * additional enchantments it could legitimately have had.
     *
     * <p>Only enchantments the item can accept and that do not conflict with what it already
     * carries, so the perk cannot produce a combination the game would otherwise refuse — the same
     * rule the anvil's Enchantment Stacking follows.
     */
    @Unique
    private static void runicskills$addExtraEnchantments(Player player, ItemStack result,
                                                         HandlerCommonConfig config) {
        if (RegistryPerks.ENCHANTMENT_INSIGHT == null
                || !RegistryPerks.ENCHANTMENT_INSIGHT.get().isEnabled(player)) {
            return;
        }
        int extra = (int) config.enchantmentInsightAmplifier;
        if (extra <= 0) return;

        boolean book = result.is(Items.ENCHANTED_BOOK);
        for (int added = 0; added < extra; added++) {
            java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> present =
                    net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(result);
            java.util.List<net.minecraft.world.item.enchantment.Enchantment> candidates =
                    new java.util.ArrayList<>();
            for (net.minecraft.world.item.enchantment.Enchantment candidate
                    : net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS) {
                if (present.containsKey(candidate)) continue;
                if (candidate.isCurse() || candidate.isTreasureOnly()) continue;
                if (!book && !candidate.canEnchant(result)) continue;
                if (present.keySet().stream().anyMatch(p -> !p.isCompatibleWith(candidate))) continue;
                candidates.add(candidate);
            }
            if (candidates.isEmpty()) return;

            net.minecraft.world.item.enchantment.Enchantment chosen =
                    candidates.get(player.getRandom().nextInt(candidates.size()));
            if (book) {
                net.minecraft.world.item.EnchantedBookItem.addEnchantment(result,
                        new net.minecraft.world.item.enchantment.EnchantmentInstance(chosen, 1));
            } else {
                result.enchant(chosen, 1);
            }
        }
    }

    /**
     * Tome of Knowledge — "Books store more enchantment levels".
     *
     * <p>Books, and only books: the perk names the container, and an enchanted book is the one item
     * whose whole purpose is to hold a level for later. One enchantment on it goes up by one, never
     * past the enchantment's own maximum, so a tome can hold more than the table would have written
     * but never more than the enchantment can be.
     */
    @Unique
    private static void runicskills$deepenBookEnchantment(Player player, ItemStack result,
                                                          HandlerCommonConfig config) {
        if (RegistryPerks.TOME_OF_KNOWLEDGE == null
                || !RegistryPerks.TOME_OF_KNOWLEDGE.get().isEnabled(player)) {
            return;
        }
        if (!result.is(Items.ENCHANTED_BOOK)) return;
        double chance = config.tomeOfKnowledgePercent / 100.0;
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) return;

        java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> present =
                net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(result);
        for (java.util.Map.Entry<net.minecraft.world.item.enchantment.Enchantment, Integer> entry
                : present.entrySet()) {
            if (entry.getValue() >= entry.getKey().getMaxLevel()) continue;
            entry.setValue(entry.getValue() + 1);
            net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(present, result);
            return;
        }
    }
}
