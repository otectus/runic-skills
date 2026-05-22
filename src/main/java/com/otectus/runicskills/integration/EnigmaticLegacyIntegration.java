package com.otectus.runicskills.integration;

import com.mojang.logging.LogUtils;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Enigmatic Legacy integration.
 *
 * <p>Scaffolded in R3 batch 3 to host event-handler code for Strength-tree perks
 * gated on this mod (ANCIENT_STRENGTH, CATACLYSMS_WRATH, CURSE_WARD — see
 * RegistryPerks). The integration will also host R4–R12 cross-tree perks
 * (ARMOR_OF_FAITH, ARTIFACT_HUNTER, ENIGMATIC_*, MYSTIC_ANALYSIS, SAGES_FOCUS,
 * SOUL_SUSTENANCE — ~11 total). Effect code lands in R3 batch 4 (for Strength
 * entries) and later releases (for the rest).
 *
 * <p>Loaded reflectively via {@code RunicSkills.tryLoadIntegration("enigmaticlegacy", …)}
 * so the JVM never resolves Enigmatic Legacy API types when the mod is absent.
 */
public class EnigmaticLegacyIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "enigmaticlegacy";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public EnigmaticLegacyIntegration() {
        LOGGER.debug("Enigmatic Legacy integration scaffold loaded (R3 batch 3); perk effects land in batch 4+.");
    }

    /**
     * Attacker-side hook for Strength-tree perks gated on Enigmatic Legacy.
     * Currently wires ANCIENT_STRENGTH (bonus damage with Enigmatic-namespace
     * weapons). CATACLYSMS_WRATH is gated on Cataclysm not Enigmatic Legacy —
     * its handler will land in CataclysmIntegration in a later batch.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!isModLoaded()) return;
        Entity src = event.getSource().getEntity();
        if (!(src instanceof Player player) || player.isCreative() || player instanceof FakePlayer) return;

        // ANCIENT_STRENGTH — bonus % damage when wielding any item from the
        // enigmaticlegacy namespace. The lang is "Relic and ancient weapons"; the
        // mod's curio/relic items live in the same namespace, so namespace-only
        // matching covers the intent without a per-id allow-list.
        if (RegistryPerks.ANCIENT_STRENGTH != null && RegistryPerks.ANCIENT_STRENGTH.get().isEnabled(player)
                && IntegrationHelpers.itemFromMod(player.getMainHandItem(), MOD_ID)) {
            double pct = RegistryPerks.ANCIENT_STRENGTH.get().getActiveValue(player)[0];
            float dmg = event.getAmount();
            event.setAmount(dmg + dmg * (float) (pct / 100.0));
        }
    }
}
