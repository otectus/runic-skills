package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Stealth perks from the Dexterity tree, previously registered with no effect at all (RS10-004).
 *
 * <p>Both are about mobs failing to notice you, which vanilla expresses through targeting rather
 * than through any "detection range" value — so that is where they act.
 */
public class StealthPerkHandler {

    /**
     * Silent Step — "Mob detection range reduced when sneaking".
     *
     * <p>Vanilla has no detection radius to shrink: a mob acquires a target and then keeps it. The
     * observable effect of a shorter range is that a mob far enough away never acquires you at all,
     * so the perk refuses the acquisition at proportionally greater distances. Already-hostile mobs
     * are unaffected, which matches the wording: this is about being noticed, not about escaping.
     */
    @SubscribeEvent
    public void onMobPicksTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (player.level().isClientSide() || !player.isCrouching()) return;
        if (RegistryPerks.SILENT_STEP == null || !RegistryPerks.SILENT_STEP.get().isEnabled(player)) return;

        double reduction = Math.min(0.95, HandlerCommonConfig.HANDLER.instance().silentStepPercent / 100.0);
        if (reduction <= 0) return;

        // Compare against the mob's own follow range, so a perk tuned once behaves sensibly for
        // everything from a zombie to a warden rather than against a hardcoded distance.
        double follow = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        double effective = follow * (1.0 - reduction);
        if (mob.distanceToSqr(player) > effective * effective) {
            event.setCanceled(true);
        }
    }

    /**
     * Silent Kill — "Stealth kills have a chance to not alert nearby mobs".
     *
     * <p>Killing from stealth normally pulls in everything that saw it happen. On a successful roll
     * the witnesses lose the thread: any mob nearby that had just locked onto the killer drops the
     * target, which is what "not alerted" means in vanilla's terms.
     */
    // Wait for resurrection handlers before changing witnesses' targets for a completed kill.
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onStealthKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        if (killer.level().isClientSide() || !killer.isCrouching()) return;
        if (RegistryPerks.SILENT_KILL == null || !RegistryPerks.SILENT_KILL.get().isEnabled(killer)) return;

        double chance = HandlerCommonConfig.HANDLER.instance().silentKillPercent / 100.0;
        if (chance <= 0 || killer.getRandom().nextDouble() >= chance) return;

        LivingEntity victim = event.getEntity();
        AABB witnesses = victim.getBoundingBox().inflate(16.0);
        for (Mob mob : victim.level().getEntitiesOfClass(Mob.class, witnesses)) {
            if (mob == victim) continue;
            if (mob.getTarget() == killer) mob.setTarget(null);
        }
    }
}
