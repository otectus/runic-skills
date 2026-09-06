package com.otectus.runicskills.integration.tconstruct.addons;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import vectorwing.farmersdelight.common.registry.ModEffects;

/**
 * Tinkers' Delight: Banquet of Cinders reads the food effect the add-on actually applies.
 *
 * <p><b>Which effect, and how that was decided.</b> Tinkers' Delight 2.0.3 registers no mob effect
 * of its own: its {@code DelicaciesModifier} — the edible-tool trait its foods and knives carry —
 * applies Farmer's Delight's own {@code farmersdelight:nourishment}. That was read out of the
 * published jar, not out of a tooltip, which is §12.2's rule and §12.6's warning that "the native
 * recipe returns another mod's food" is the normal case rather than the exception. The capability is
 * therefore keyed on both mod ids: without Farmer's Delight there is no effect to observe, and
 * without Tinkers' Delight there is nothing making Runic call it a Tinkers' meal.
 *
 * <p><b>It contributes, it does not replace.</b> §10.3 is explicit: the effect keeps its own native
 * damage and weakness behaviour, the perk adds one small scalar to accepted primary melee damage,
 * and it does not apply the food effect again, duplicate a meal, amplify every potion, or reach the
 * add-on's ordinary metal knives unless their native role already qualifies. The share is handed to
 * the shared melee sum in {@code TConstructPerkHandler}, which already requires a native melee
 * weapon and an accepted primary hit before anything in that sum is paid.
 */
public final class TinkersDelightAdapter {

    private TinkersDelightAdapter() {
    }

    /** Adds Banquet of Cinders' share to the one melee-damage sum. */
    public static void install() {
        TcAddonHooks.addMeleeDamageContributor(TinkersDelightAdapter::banquetBonus);
    }

    /**
     * Banquet of Cinders' share of the melee channel, or zero.
     *
     * <p>No cap of its own: the caller sums this with the core perks' contributions and clamps the
     * total once with {@code tconstructNewDamageBonusCap} (§13.2).
     */
    private static double banquetBonus(ServerPlayer player) {
        if (!TcAddonHooks.active(player, RegistryPerks.TC_BANQUET_OF_CINDERS,
                Capability.ADDON_CULINARY_EFFECT)) {
            return 0.0;
        }
        MobEffect nourishment = ModEffects.NOURISHMENT.get();
        if (nourishment == null || !player.hasEffect(nourishment)) return 0.0;
        return HandlerCommonConfig.HANDLER.instance().tcBanquetOfCindersPercent / 100.0;
    }
}
