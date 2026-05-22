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
 * Nichirin Dynasty integration.
 *
 * <p>Scaffolded in R3 batch 3 to host event-handler code for Strength-tree perks
 * gated on this mod (NICHIRIN_BLADE, DRAGON_BONE_MASTERY — see RegistryPerks).
 * Effect code lands in R3 batch 4; the {@code onLivingHurt} stub below is the
 * intended landing site.
 *
 * <p>Loaded reflectively via {@code RunicSkills.tryLoadIntegration("nichirin_dynasty", …)}
 * so the JVM never resolves Nichirin Dynasty API types when the mod is absent.
 */
public class NichirinDynastyIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "nichirin_dynasty";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public NichirinDynastyIntegration() {
        LOGGER.debug("Nichirin Dynasty integration scaffold loaded (R3 batch 3); perk effects land in batch 4.");
    }

    /**
     * Attacker-side hook for Strength-tree perks gated on Nichirin Dynasty.
     * Currently wires NICHIRIN_BLADE (bonus damage when wielding a Nichirin-namespace
     * weapon). DRAGON_BONE_MASTERY is gated on Ice and Fire, so its handler lives
     * in IceAndFireIntegration, not here.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!isModLoaded()) return;
        Entity src = event.getSource().getEntity();
        if (!(src instanceof Player player) || player.isCreative() || player instanceof FakePlayer) return;

        // NICHIRIN_BLADE — bonus % damage when wielding any item from the nichirin_dynasty namespace.
        // Detection by namespace match (reflective / no API import) keeps the build dep-free.
        if (RegistryPerks.NICHIRIN_BLADE != null && RegistryPerks.NICHIRIN_BLADE.get().isEnabled(player)
                && IntegrationHelpers.itemFromMod(player.getMainHandItem(), MOD_ID)) {
            double pct = RegistryPerks.NICHIRIN_BLADE.get().getActiveValue(player)[0];
            float dmg = event.getAmount();
            event.setAmount(dmg + dmg * (float) (pct / 100.0));
        }
    }
}
