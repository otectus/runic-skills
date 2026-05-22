package com.otectus.runicskills.integration;

import com.mojang.logging.LogUtils;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Saints' Dragons integration.
 *
 * <p>Scaffolded in R3 batch 3 to host event-handler code for Strength-tree perks
 * gated on this mod (DRACONIC_FURY, GLADIATOR, TROPHY_HUNTER — see RegistryPerks).
 * Effect code for those perks is wired in R3 batch 4; the {@code onLivingHurt}
 * stub below is the intended landing site.
 *
 * <p>Loaded reflectively via {@code RunicSkills.tryLoadIntegration("saintsdragons", …)}
 * so the JVM never resolves Saints' Dragons API types when the mod is absent.
 *
 * <p>The {@link #isModLoaded()} guard inside the handler is defensive: the
 * bootstrap won't instantiate the class unless the mod is loaded, but the
 * R0 B6 hardening pattern keeps the guard inline as belt-and-braces against a
 * future refactor that changes the registration path.
 */
public class SaintsDragonsIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "saintsdragons";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public SaintsDragonsIntegration() {
        LOGGER.debug("Saints' Dragons integration scaffold loaded (R3 batch 3); perk effects land in batch 4.");
    }

    /**
     * Attacker-side hook for Strength-tree perks gated on Saints' Dragons.
     * Currently wires DRACONIC_FURY (bonus damage + secondary fire ignition with
     * a Saints'-Dragons draconic weapon). GLADIATOR and TROPHY_HUNTER are NOT
     * mod-gated — they live in CombatEventHandler (always-available).
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!isModLoaded()) return;
        Entity src = event.getSource().getEntity();
        if (!(src instanceof Player player) || player.isCreative() || player instanceof FakePlayer) return;
        LivingEntity target = event.getEntity();
        if (target == null) return;

        // DRACONIC_FURY — bonus % damage AND a secondary fire ignition when wielding
        // a Saints' Dragons draconic weapon. The lang is "Dragon-type weapon attacks
        // gain %s bonus fire damage" — interpreted as percent damage scaling + visible
        // fire effect on target. Detection by namespace + path-contains-draconic-substring
        // starts permissive (any Saints' Dragons item with a dragon-anatomy suffix); the
        // exact set can tighten after a first dev-client item enumeration.
        if (RegistryPerks.DRACONIC_FURY != null && RegistryPerks.DRACONIC_FURY.get().isEnabled(player)) {
            ItemStack main = player.getMainHandItem();
            if (IntegrationHelpers.itemFromMod(main, MOD_ID) && isDraconicWeapon(main)) {
                double pct = RegistryPerks.DRACONIC_FURY.get().getActiveValue(player)[0];
                float dmg = event.getAmount();
                event.setAmount(dmg + dmg * (float) (pct / 100.0));
                // Fire-secondary: ignite for at least 2 s, scaling with rank percent.
                int seconds = Math.max(2, 2 + (int) (pct / 25.0));
                target.setSecondsOnFire(seconds);
            }
        }
    }

    /**
     * True if the item path looks like a draconic-anatomy weapon (fang, claw, horn,
     * tooth, wing, scale). Intentionally a wide net so future Saints' Dragons weapon
     * additions don't silently regress; can tighten to an allow-list after a first
     * dev-client run enumerates real ids.
     */
    private static boolean isDraconicWeapon(ItemStack stack) {
        return IntegrationHelpers.itemPathContains(stack, "fang")
                || IntegrationHelpers.itemPathContains(stack, "claw")
                || IntegrationHelpers.itemPathContains(stack, "horn")
                || IntegrationHelpers.itemPathContains(stack, "tooth")
                || IntegrationHelpers.itemPathContains(stack, "wing")
                || IntegrationHelpers.itemPathContains(stack, "scale")
                || IntegrationHelpers.itemPathContains(stack, "dragon");
    }
}
