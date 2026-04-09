package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public class TickEventHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (!player.isCreative()) {
            if (HandlerCommonConfig.HANDLER.instance().dropLockedItems) {
                player.getCapability(RegistryCapabilities.SKILL).ifPresent(provider -> {
                    ItemStack hand = player.getMainHandItem();
                    ItemStack offHand = player.getOffhandItem();
                    if (!provider.canUseItem(player, hand)) {
                        player.drop(hand.copy(), false);
                        hand.setCount(0);
                    }
                    if (!provider.canUseItem(player, offHand)) {
                        player.drop(offHand.copy(), false);
                        offHand.setCount(0);
                    }
                });
            }
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(RegistryCapabilities.SKILL).ifPresent(provider -> {
                if (provider.getCounterAttack()) {
                    provider.setCounterAttackTimer(provider.getCounterAttackTimer() + 1);
                    if (RegistryPerks.COUNTER_ATTACK != null && provider.getCounterAttackTimer() >= RegistryPerks.COUNTER_ATTACK.get().getValue()[0] * 40.0D) {
                        provider.setCounterAttack(false);
                        provider.setCounterAttackTimer(0);
                        new RegistryAttributes.RegisterAttribute(serverPlayer, Attributes.ATTACK_DAMAGE, 0.0F, RegistryAttributes.COUNTER_ATTACK_UUID).amplifyAttribute(false);
                        SyncSkillCapabilityCP.send(serverPlayer);
                    }
                }
                provider.tickCooldowns();
            });
            if (RegistryPerks.ONE_HANDED != null) {
                new RegistryAttributes.RegisterAttribute(serverPlayer, Attributes.ATTACK_DAMAGE, (float) RegistryPerks.ONE_HANDED.get().getValue()[0], RegistryAttributes.ONE_HANDED_UUID).amplifyAttribute((serverPlayer.getOffhandItem().getCount() == 0 && RegistryPerks.ONE_HANDED.get().isEnabled(serverPlayer)));
            }
            if (RegistryPerks.DIAMOND_SKIN != null) {
                new RegistryAttributes.RegisterAttribute(serverPlayer, Attributes.ARMOR, (float) RegistryPerks.DIAMOND_SKIN.get().getValue()[1], RegistryAttributes.DIAMOND_SKIN_UUID).amplifyAttribute((serverPlayer.isShiftKeyDown() && RegistryPerks.DIAMOND_SKIN.get().isEnabled(serverPlayer)));
            }

            if (serverPlayer.getHealth() > serverPlayer.getMaxHealth())
                serverPlayer.setHealth(serverPlayer.getMaxHealth());

            if (RegistryPerks.CAT_EYES != null) {
                new RegistryEffects.AddEffect(serverPlayer, RegistryPerks.CAT_EYES.get().isEnabled(player), MobEffects.NIGHT_VISION).add(210);
            }
            if (RegistryPerks.DIAMOND_SKIN != null) {
                new RegistryEffects.AddEffect(serverPlayer, RegistryPerks.DIAMOND_SKIN.get().isEnabled(player), MobEffects.DAMAGE_RESISTANCE).add(210, (int) (RegistryPerks.DIAMOND_SKIN.get().getValue()[0] - 1.0D));
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTickLow(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.side == LogicalSide.SERVER) {
            Player player = event.player;
            if (player instanceof ServerPlayer serverPlayer && serverPlayer.tickCount % 200 == 0) {
                RegistryTitles.syncTitles(serverPlayer);
            }
        }
    }
}
