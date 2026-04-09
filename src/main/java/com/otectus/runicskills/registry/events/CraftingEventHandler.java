package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.packet.client.PlayerMessagesCP;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.perks.ConvergencePerk;
import com.otectus.runicskills.registry.perks.TreasureHunterPerk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public class CraftingEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerBreakBlock(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player instanceof FakePlayer) return;
        if (RegistryPerks.TREASURE_HUNTER != null && player != null &&
                event.getState().is(RegistryTags.Blocks.DIRT) && RegistryPerks.TREASURE_HUNTER.get().isEnabled(player)) {
            Level level = player.level();
            BlockPos pos = event.getPos();
            ItemStack stack = TreasureHunterPerk.drop();
            if (stack != null) {
                ItemEntity itemEntity = new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                enqueueTask(level, () -> level.addFreshEntity(itemEntity), 0);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerCraft(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player != null && RegistryPerks.CONVERGENCE != null) {
            if (player instanceof FakePlayer) return;
            int randomizer = ThreadLocalRandom.current().nextInt((int) RegistryPerks.CONVERGENCE.get().getValue()[0]);
            if (RegistryPerks.CONVERGENCE.get().isEnabled(player) && (RegistryPerks.CONVERGENCE.get().getValue()[0] >= 100 || randomizer == 1)) {
                ItemStack convergenceItem = ConvergencePerk.drop(event.getCrafting());
                if (convergenceItem != null) {
                    player.drop(convergenceItem, false);
                }
            }
        }

        if (player != null && RegistryPerks.MASTER_TINKERER != null && RegistryPerks.MASTER_TINKERER.get().isEnabled(player)) {
            if (!(player instanceof FakePlayer)) {
                ItemStack crafted = event.getCrafting();
                if (crafted.isDamageableItem()) {
                    int bonusDurability = (int) (crafted.getMaxDamage() * HandlerCommonConfig.HANDLER.instance().masterTinkererPercent / 100.0);
                    if (bonusDurability > 0 && crafted.getDamageValue() > 0) {
                        crafted.setDamageValue(Math.max(0, crafted.getDamageValue() - bonusDurability));
                    }
                }
            }
        }

        if (player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer)) {
            double craftingLuck = serverPlayer.getAttributeValue(RegistryAttributes.CRAFTING_LUCK.get());
            if (craftingLuck > 0) {
                int chance = ThreadLocalRandom.current().nextInt(100);
                if (chance < (int) craftingLuck && event.getCrafting().getMaxStackSize() > 1) {
                    ItemStack bonus = event.getCrafting().copy();
                    bonus.setCount(1);
                    player.drop(bonus, false);
                }
            }
        }
    }

    @SubscribeEvent
    public void onAnvilRepair(AnvilRepairEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            double efficiency = serverPlayer.getAttributeValue(RegistryAttributes.REPAIR_EFFICIENCY.get());
            if (efficiency > 0) {
                event.setBreakChance(Math.max(0.0f, event.getBreakChance() - (float) (efficiency * 0.01)));
            }
        }
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer)) {
            if (RegistryPerks.LOCKSMITH != null && RegistryPerks.LOCKSMITH.get().isEnabled(player)) {
                int random = ThreadLocalRandom.current().nextInt((int) RegistryPerks.LOCKSMITH.get().getValue()[0]);
                if (random == 0) {
                    int bonusXp = 5;
                    if (RegistryPerks.SAFE_CRACKER != null && RegistryPerks.SAFE_CRACKER.get().isEnabled(player)) {
                        bonusXp += (int) RegistryPerks.SAFE_CRACKER.get().getValue()[0];
                    }
                    serverPlayer.giveExperiencePoints(bonusXp);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPickupXp(PlayerXpEvent.PickupXp event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer sp && RegistryPerks.LORE_MASTERY != null &&
                RegistryPerks.LORE_MASTERY.get().isEnabled(sp)) {
            if (sp.containerMenu instanceof net.minecraft.world.inventory.GrindstoneMenu) {
                int originalXp = event.getOrb().getValue();
                int bonusXp = (int) (originalXp * (RegistryPerks.LORE_MASTERY.get().getValue()[0] - 1.0));
                if (bonusXp > 0) {
                    sp.giveExperiencePoints(bonusXp);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityDrops(LivingDropsEvent event) {
        if (event.getEntity() != null) {
            if (!(event.getEntity() instanceof Player)) {
                Entity entity1 = event.getSource().getEntity();
                if (entity1 instanceof ServerPlayer player) {
                    if (RegistryPerks.FIGHTING_SPIRIT != null) {
                        new RegistryEffects.AddEffect(player, RegistryPerks.FIGHTING_SPIRIT.get().isEnabled(player), MobEffects.DAMAGE_BOOST).add((int) (10.0D + 20.0D * RegistryPerks.FIGHTING_SPIRIT.get().getValue()[1]), (int) (RegistryPerks.FIGHTING_SPIRIT.get().getValue()[0] - 1.0D));
                    }
                }
            }

            Entity entity = event.getSource().getEntity();
            if (entity instanceof Player player) {
                if (RegistryPerks.LIFE_EATER != null && RegistryPerks.LIFE_EATER.get().isEnabled(player)) {
                    player.heal((float) RegistryPerks.LIFE_EATER.get().getValue()[0]);
                }
            }

            if (!(event.getEntity() instanceof Player)) {
                entity = event.getSource().getEntity();
                if (entity instanceof Player player) {
                    if (RegistryPerks.LUCKY_DROP != null && RegistryPerks.LUCKY_DROP.get().isEnabled(player)) {
                        int random = ThreadLocalRandom.current().nextInt((int) RegistryPerks.LUCKY_DROP.get().getValue()[0]);
                        if (random == 0) {
                            List<ItemStack> equipment = new ArrayList<>();
                            for (ItemStack next : event.getEntity().getAllSlots()) {
                                equipment.add(next);
                            }

                            BlockPos pos = event.getEntity().blockPosition();
                            enqueueTask(event.getEntity().level(), () -> {
                                List<ItemEntity> dropEntities = new ArrayList<>();
                                Iterator<Entity> var5 = event.getEntity().level().getEntities(null, new AABB((pos.getX() - 1), (pos.getY() - 1), (pos.getZ() - 1), (pos.getX() + 1), (pos.getY() + 1), (pos.getZ() + 1))).iterator();
                                while (var5.hasNext()) {
                                    Entity ea = var5.next();
                                    if (ea instanceof ItemEntity) dropEntities.add((ItemEntity) ea);
                                }
                                var5 = (Iterator) dropEntities.iterator();
                                while (var5.hasNext()) {
                                    ItemEntity dropEntity = (ItemEntity) var5.next();
                                    int tickCount = dropEntity.tickCount;
                                    if (tickCount <= 1) {
                                        ItemStack itemStack = dropEntity.getItem();
                                        if (!equipment.contains(itemStack)) {
                                            if (itemStack.getMaxStackSize() > 1)
                                                itemStack.setCount(itemStack.getCount() * (int) RegistryPerks.LUCKY_DROP.get().getValue()[1]);
                                            PlayerMessagesCP.send(player, "overlay.perk.runicskills.lucky_drop", (int) RegistryPerks.LUCKY_DROP.get().getValue()[1]);
                                            dropEntity.setItem(itemStack);
                                        }
                                    }
                                }
                            }, 0);
                        }
                    }
                }
            }
        }
    }

    public static void enqueueTask(Level world, Runnable task, int delay) {
        if (!(world instanceof ServerLevel)) return;
        MinecraftServer server = ((ServerLevel) world).getServer();
        server.submit(new TickTask(server.getTickCount() + delay, task));
    }
}
