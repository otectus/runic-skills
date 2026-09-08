package com.otectus.runicskills.integration.tconstruct.addons;

import com.otectus.runicskills.common.util.DurationMath;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.TcAddonPresence;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.integration.tconstruct.TConstructEquipmentAdapter;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * Tinkers' Jewelry: four perks keyed on its materials and on the one modifier that has behaviour.
 *
 * <p><b>No compile dependency, and no stub source set either.</b> Everything the perks need is
 * addressed the way Tinkers' addresses it — a material id, a modifier id, an item in
 * {@code tconstruct:modifiable/durability} — so the add-on's own types never appear. That matters
 * more here than elsewhere: Modrinth's newest 1.20.1 Forge build of this mod is 1.1.0 while the
 * pack this was read against runs 1.2.0, so anything compiled against the published artifact would
 * be compiled against a build no one is running.
 *
 * <p><b>What was read, out of {@code tinkersjewelry-1.2.0.jar}:</b>
 * <ul>
 *   <li>{@code data/tinkersjewelry/tinkering/materials/definition/} — 37 gem materials,
 *       {@code amethyst} through {@code xychorium_gem_red}, all carrying the
 *       {@code tinkersjewelry:gem} stat type. {@code tinkersjewelry:ring} is the only item, and it
 *       registers itself into {@code tconstruct:modifiable/durability}.</li>
 *   <li>{@code DamageItemEvents.undying(LivingDeathEvent)} — scans the wearer's Curios slots for a
 *       {@code ModifiableItem} that is unbroken and carries {@code tinkersjewelry:undying}, calls
 *       {@code ToolDamageUtil.damage} on it and cancels the death. That call is the H1 seam, so the
 *       durability the save costs arrives at this mod's existing wear stage with no new
 *       injection.</li>
 *   <li>{@code data/tinkersjewelry/tinkering/modifiers/polish.json} — <b>one
 *       {@code tconstruct:modifier_slot} grant of a single upgrade slot, and nothing else.</b> It
 *       has no repair semantics whatsoever. Polished Facet therefore keys on the repaired piece
 *       being a jewelry-material item, which is what a station repair of one actually is; keying it
 *       on the modifier would have been a perk named after a mechanic that does not exist.</li>
 *   <li>{@code data/tinkersjewelry/tinkering/modifiers/subspace.json} plus
 *       {@code capability.SubSpaceCapability} and {@code menu.SubSpaceMenu} — private inventory
 *       storage, sized by the {@code tinkersjewelry:subspace} attribute. There is no durability,
 *       damage, repair or progression quantity in it, so {@code tc_subspace_reserve} is a reserved,
 *       unregistered id rather than a selectable no-op.</li>
 * </ul>
 */
public final class TinkersJewelryAdapter {

    private TinkersJewelryAdapter() {
    }

    /** Registers the four contributions and the one notification they need. */
    public static void install() {
        TcAddonHooks.addStationTakeObserver(TinkersJewelryAdapter::onStationTake);
        TcAddonHooks.addWearAvoidanceContributor(TinkersJewelryAdapter::jewelryAvoidance);
        TcAddonHooks.addMeleeDamageContributor(TinkersJewelryAdapter::gemAttunementBonus);
        TcAddonHooks.addRepairBonusContributor(TinkersJewelryAdapter::polishedFacetShare);
    }

    /**
     * Jeweler's Setting's trigger: a station take that hands the player a jewelry piece.
     *
     * <p>Any take, not only a repair. Setting a stone is the moment the piece is finished, and a
     * first assembly is that moment more than a top-up is; Repair Memory already owns "you paid to
     * mend this", needs a five-percent restoration to arm, and is a different perk on a different
     * skill.
     */
    private static void onStationTake(ServerPlayer player, ItemStack delivered) {
        if (!TcAddonHooks.active(player, RegistryPerks.TC_JEWELER_SETTING,
                Capability.ADDON_JEWELRY_MATERIAL)) {
            return;
        }
        if (!isJewelryPiece(delivered)) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        TcAddonState.Player state = TcAddonState.of(player.getUUID());
        state.jewelerSettingUntil = TcAddonHooks.now(player)
                + DurationMath.secondsToTicks(config.tcJewelerSettingSeconds);
        state.jewelerSettingPiece = TcAddonState.reference(delivered);
    }

    /**
     * The jewelry share of the add-on wear-avoidance channel: Jeweler's Setting and Undying Lustre.
     *
     * <p>One contributor for both because both are questions about the same stack at the same
     * stage, and registering two would only make the sum harder to read. Neither has a cap of its
     * own; the caller clamps the add-on total once with {@code tconstructNewWearAvoidanceCap}.
     */
    private static double jewelryAvoidance(ServerPlayer player, ItemStack stack) {
        if (!isJewelryPiece(stack)) return 0.0;
        double share = 0.0;

        if (TcAddonHooks.active(player, RegistryPerks.TC_JEWELER_SETTING,
                Capability.ADDON_JEWELRY_MATERIAL)) {
            TcAddonState.Player state = TcAddonState.peek(player.getUUID());
            if (state != null && state.jewelerSettingUntil > 0L) {
                if (TcAddonHooks.now(player) > state.jewelerSettingUntil) {
                    state.jewelerSettingUntil = 0L;
                    state.jewelerSettingPiece = TcAddonState.reference(ItemStack.EMPTY);
                } else if (TcAddonState.isSameTool(state.jewelerSettingPiece, stack)) {
                    share += HandlerCommonConfig.HANDLER.instance().tcJewelerSettingPercent / 100.0;
                }
            }
        }

        // Undying Lustre. The window is what makes this the save's cost rather than any other wear
        // the ring takes: DamageItemEvents also damages jewelry on a block break and on an attack,
        // and those are ordinary use that this perk has nothing to say about.
        if (TcAddonHooks.inDeathResolution(player)
                && TcAddonHooks.active(player, RegistryPerks.TC_UNDYING_LUSTRE,
                        Capability.ADDON_JEWELRY_UNDYING)
                && TraitFeatureRegistry.present(ToolStack.from(stack),
                        TraitFeatureRegistry.Feature.JEWELRY_UNDYING)) {
            share += HandlerCommonConfig.HANDLER.instance().tcUndyingLustrePercent / 100.0;
        }
        return share;
    }

    /**
     * Gem Attunement's share of the one melee channel, or zero.
     *
     * <p>Counts equipped Curios slots, the add-on's actual equip path, plus native equipment slots.
     * A ring in an ordinary inventory slot contributes nothing. Curios calls live in a nested
     * class reached only after a presence check, keeping the optional API out of the outer class.
     */
    private static double gemAttunementBonus(ServerPlayer player) {
        if (!TcAddonHooks.active(player, RegistryPerks.TC_GEM_ATTUNEMENT,
                Capability.ADDON_JEWELRY_GEM_ATTRIBUTES)) {
            return 0.0;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            if (isJewelryPiece(player.getItemBySlot(slot))) {
                return HandlerCommonConfig.HANDLER.instance().tcGemAttunementPercent / 100.0;
            }
        }
        if (TcAddonPresence.isLoaded(TcAddonPresence.CURIOS) && CuriosEquipment.hasJewelry(player)) {
            return HandlerCommonConfig.HANDLER.instance().tcGemAttunementPercent / 100.0;
        }
        return 0.0;
    }

    /** Loaded only when Curios is present. No dependency on the Jewelry add-on's own classes. */
    private static final class CuriosEquipment {
        private static boolean hasJewelry(ServerPlayer player) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .map(inventory -> inventory.findFirstCurio(TinkersJewelryAdapter::isJewelryPiece).isPresent())
                    .orElse(false);
        }
    }

    /** Only the save-cost perk contributes outside ordinary use; other avoidance perks do not. */
    public static int reduceDeathWear(ServerPlayer player, ItemStack stack, int amount) {
        if (amount <= 0 || !TcAddonHooks.inDeathResolution(player) || !isJewelryPiece(stack)) return amount;
        if (!TcAddonHooks.active(player, RegistryPerks.TC_UNDYING_LUSTRE, Capability.ADDON_JEWELRY_UNDYING)
                || !TraitFeatureRegistry.present(ToolStack.from(stack), TraitFeatureRegistry.Feature.JEWELRY_UNDYING)) {
            return amount;
        }
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double chance = Math.min(0.90, Math.min(config.tconstructNewWearAvoidanceCap,
                config.tcUndyingLustrePercent / 100.0));
        if (!Double.isFinite(chance) || chance <= 0.0) return amount;
        // Stochastic rounding preserves small costs without a loop proportional to addon damage.
        double expected = amount * chance;
        int spared = (int) Math.floor(expected);
        if (player.getRandom().nextDouble() < expected - spared) spared++;
        return Math.max(0, amount - spared);
    }

    /**
     * Polished Facet's share of the existing paid-repair channel, or zero.
     *
     * <p>Bounded with Material Harmony and the repair Powers by {@code tconstructRepairBonusCap},
     * and by the damage actually left on the piece, both of which the caller already applies.
     */
    private static double polishedFacetShare(ServerPlayer player, ItemStack delivered) {
        if (!TcAddonHooks.active(player, RegistryPerks.TC_POLISHED_FACET,
                Capability.ADDON_JEWELRY_POLISH)) {
            return 0.0;
        }
        if (!isJewelryPiece(delivered)) return 0.0;
        return HandlerCommonConfig.HANDLER.instance().tcPolishedFacetPercent / 100.0;
    }

    /**
     * Whether {@code stack} is a native tool built from at least one Tinkers' Jewelry material.
     *
     * <p>The material rather than the item id, because the item id would answer for
     * {@code tinkersjewelry:ring} only and stop being true the moment the add-on or a pack added a
     * second piece — while the material namespace is the add-on's own statement about what its
     * content is made of.
     */
    private static boolean isJewelryPiece(ItemStack stack) {
        if (!TConstructEquipmentAdapter.isNativeTool(stack)) return false;
        ToolStack tool = ToolStack.from(stack);
        if (tool.isBroken()) return false;
        MaterialNBT materials = tool.getMaterials();
        for (MaterialVariant variant : materials) {
            if (variant == null || variant.isEmpty() || variant.isUnknown()) continue;
            MaterialVariantId id = variant.getVariant();
            if (id == null || id.getId() == null) continue;
            if (TraitFeatureRegistry.JEWELRY_MATERIAL_NAMESPACE.equals(id.getId().getNamespace())) {
                return true;
            }
        }
        return false;
    }
}
