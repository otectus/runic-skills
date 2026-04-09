package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;

@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public class InteractionEventHandler {

    public static boolean shouldCancelInteraction(Player player, ItemStack item, Block block, Entity target) {
        SkillCapability provider = SkillCapability.get(player);
        if (provider == null) return false;

        ResourceLocation location = Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item.getItem()));
        if (!provider.canUseItem(player, location)) return true;
        if (block != null && !provider.canUseBlock(player, block)) return true;
        if (target != null && !provider.canUseEntity(player, target)) return true;
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (player.isCreative() || player instanceof FakePlayer) return;
        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (shouldCancelInteraction(player, event.getItemStack(), block, null)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.isCreative() || player instanceof FakePlayer) return;
        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (shouldCancelInteraction(player, event.getItemStack(), block, null)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.isCreative() || player instanceof FakePlayer) return;
        if (shouldCancelInteraction(player, event.getItemStack(), null, null)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.isCreative() || player instanceof FakePlayer) return;
        if (shouldCancelInteraction(player, event.getItemStack(), null, event.getTarget())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChangeEquipment(LivingEquipmentChangeEvent event) {
        LivingEntity livingEntity = event.getEntity();
        if (livingEntity instanceof Player player) {
            if (!player.isCreative() && event.getSlot().getType() == EquipmentSlot.Type.ARMOR) {
                SkillCapability provider = SkillCapability.get(player);
                if (provider == null) return;
                ItemStack item = event.getTo();

                if (!provider.canUseItem(player, item)) {
                    player.drop(item.copy(), false);
                    item.setCount(0);
                }
            }
        }
    }
}
