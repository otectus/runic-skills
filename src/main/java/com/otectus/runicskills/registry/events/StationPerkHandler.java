package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.util.ContainerInteraction;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RunicAttributeModifiers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;


/**
 * Perks tied to a workstation or to a mount, all of which were registered with no runtime effect at
 * all — visible, selectable, and doing nothing (RS10-004).
 */
public class StationPerkHandler {

    /**
     * Disenchant Mastery — "The grindstone returns more XP".
     *
     * <p>Forge's {@code GrindstoneEvent} exposes the payout and lets a mod change it, but carries no
     * player — so on its own it cannot gate a per-player perk. The player is not actually missing
     * from the game, only from the event: the vanilla method underneath receives it directly, and
     * {@link ContainerInteraction} publishes it for the duration of the click. Attributing the perk
     * to whoever happened to be standing nearby would have been the wrong answer to a question that
     * has a right one.
     *
     * <p>Adjusts the number the block is about to award rather than granting XP separately, which
     * would double-count for anything else listening to the same event.
     */
    @SubscribeEvent
    public void onGrindstoneTake(GrindstoneEvent.OnTakeItem event) {
        Player player = ContainerInteraction.currentPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (RegistryPerks.DISENCHANT_MASTERY == null
                || !RegistryPerks.DISENCHANT_MASTERY.get().isEnabled(player)) {
            return;
        }
        double bonus = HandlerCommonConfig.HANDLER.instance().disenchantMasteryPercent / 100.0;
        if (bonus <= 0) return;
        event.setXp((int) Math.round(event.getXp() * (1.0 + bonus)));
    }

    /**
     * Dragon Rider — "Mount speed increased".
     *
     * <p>Applied to the mount rather than to the rider: a movement-speed modifier on a passenger
     * does nothing, because the vehicle is what moves. Reconciled once a second on the same clock as
     * the rest of the attribute work, and removed the moment the perk stops applying — the modifier
     * is transient, so a mount that unloads mid-ride never carries it into the save either.
     */
    @SubscribeEvent
    public void onRiderTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % 20 != 0) return;
        if (RegistryPerks.DRAGON_RIDER == null) return;
        if (!(player.getVehicle() instanceof LivingEntity mount)) return;

        AttributeInstance speed = mount.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        double amount = RegistryPerks.DRAGON_RIDER.get().isEnabled(player)
                ? HandlerCommonConfig.HANDLER.instance().dragonRiderPercent / 100.0
                : 0.0;
        AttributeModifier existing = speed.getModifier(RunicAttributeModifiers.DRAGON_RIDER_MOUNT);
        if (amount <= 0.0) {
            if (existing != null) speed.removeModifier(existing);
            return;
        }
        if (existing != null && existing.getAmount() == amount) return;
        if (existing != null) speed.removeModifier(existing);
        speed.addTransientModifier(new AttributeModifier(RunicAttributeModifiers.DRAGON_RIDER_MOUNT,
                "runicskills:dragon_rider", amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }
}
