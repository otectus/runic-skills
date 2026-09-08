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
        // This handler had no logical-side guard at all, so every branch below also ran on the
        // client: it dropped and zeroed item stacks client-side, producing ghost items and a
        // desync that lasted until the next inventory packet (RS-060).
        if (event.side != LogicalSide.SERVER) return;
        if (!(event.player instanceof ServerPlayer serverPlayer)) return;
        Player player = serverPlayer;

        if (!player.isCreative() && HandlerCommonConfig.HANDLER.instance().dropLockedItems) {
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

        serverPlayer.getCapability(RegistryCapabilities.SKILL).ifPresent(provider -> {
            provider.tickCooldowns();
            if (RegistryPerks.COUNTER_ATTACK == null
                    || !RegistryPerks.COUNTER_ATTACK.get().isEnabled(serverPlayer)) {
                provider.clearCounterAttack();
            }
            // The Counter Attack window is now the cooldown itself, so tickCooldowns() expires
            // it. All that is left here is retiring the retaliation bonus when it closes.
            // amplifyAttribute reports whether it actually changed anything, so the resync
            // happens once on the transition rather than every tick (RS-011).
            if (!provider.getCounterAttack()
                    && new RegistryAttributes.RegisterAttribute(serverPlayer, Attributes.ATTACK_DAMAGE,
                            0.0F, RegistryAttributes.COUNTER_ATTACK_UUID).amplifyAttribute(false)) {
                SyncSkillCapabilityCP.send(serverPlayer);
            }
        });

        // These two rebuild an attribute modifier every tick. They are cheap now only because
        // amplifyAttribute returns early when the value is unchanged — before that, each call
        // removed and re-added the modifier, marking ARMOR and ATTACK_DAMAGE dirty and queueing
        // a clientbound attribute packet every tick, per player, for perks that rarely change
        // state (RS-010).
        if (RegistryPerks.ONE_HANDED != null) {
            new RegistryAttributes.RegisterAttribute(serverPlayer, Attributes.ATTACK_DAMAGE, (float) RegistryPerks.ONE_HANDED.get().getActiveValue(serverPlayer)[0], RegistryAttributes.ONE_HANDED_UUID).amplifyAttribute((serverPlayer.getOffhandItem().getCount() == 0 && RegistryPerks.ONE_HANDED.get().isEnabled(serverPlayer)));
        }
        if (RegistryPerks.DIAMOND_SKIN != null) {
            new RegistryAttributes.RegisterAttribute(serverPlayer, Attributes.ARMOR, (float) RegistryPerks.DIAMOND_SKIN.get().getActiveValue(serverPlayer)[1], RegistryAttributes.DIAMOND_SKIN_UUID).amplifyAttribute((serverPlayer.isShiftKeyDown() && RegistryPerks.DIAMOND_SKIN.get().isEnabled(serverPlayer)));
        }

        if (serverPlayer.getHealth() > serverPlayer.getMaxHealth())
            serverPlayer.setHealth(serverPlayer.getMaxHealth());

        if (RegistryPerks.CAT_EYES != null) {
            new RegistryEffects.AddEffect(serverPlayer, RegistryPerks.CAT_EYES.get().isEnabled(player), MobEffects.NIGHT_VISION).add(210);
        }
        if (RegistryPerks.DIAMOND_SKIN != null) {
            new RegistryEffects.AddEffect(serverPlayer, RegistryPerks.DIAMOND_SKIN.get().isEnabled(player), MobEffects.DAMAGE_RESISTANCE).add(210, (int) (RegistryPerks.DIAMOND_SKIN.get().getActiveValue(serverPlayer)[0] - 1.0D));
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
