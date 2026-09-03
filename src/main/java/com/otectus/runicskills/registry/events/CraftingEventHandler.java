package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.util.ContainerRewardLedger;
import com.otectus.runicskills.common.util.ProcRoll;
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
            ItemStack stack = TreasureHunterPerk.drop(player);
            if (stack != null) {
                ItemEntity itemEntity = new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                enqueueTask(level, () -> level.addFreshEntity(itemEntity), 0);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerCraft(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        // ItemCraftedEvent fires on BOTH logical sides. Rolling RNG here on the client produced
        // ghost bonus items the server never granted, and the durability edit below mutated a
        // client-side copy that the next inventory sync discarded (RS-061).
        if (player == null || player.level().isClientSide) return;
        if (player instanceof FakePlayer) return;
        if (RegistryPerks.CONVERGENCE != null && RegistryPerks.CONVERGENCE.get().isEnabled(player)) {
            // The roll now sits INSIDE the enablement check. It used to run for every craft by
            // every player, so a configured probability of 0 threw out of this handler and broke
            // crafting server-wide for people who had never taken the perk (RS-029).
            double chance = RegistryPerks.CONVERGENCE.get().getActiveValue(player)[0];
            if (chance >= 100 || ProcRoll.rolls(chance)) {
                ItemStack convergenceItem = ConvergencePerk.drop(event.getCrafting());
                if (convergenceItem != null) {
                    player.drop(convergenceItem, false);
                }
            }
        }

        if (RegistryPerks.MASTER_TINKERER != null && RegistryPerks.MASTER_TINKERER.get().isEnabled(player)) {
            ItemStack crafted = event.getCrafting();
            if (crafted.isDamageableItem()) {
                int bonusDurability = (int) (crafted.getMaxDamage() * HandlerCommonConfig.HANDLER.instance().masterTinkererPercent / 100.0);
                if (bonusDurability > 0 && crafted.getDamageValue() > 0) {
                    crafted.setDamageValue(Math.max(0, crafted.getDamageValue() - bonusDurability));
                }
            }
        }

        if (player instanceof ServerPlayer serverPlayer) {
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
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer) return;
        if (RegistryPerks.LOCKSMITH == null || !RegistryPerks.LOCKSMITH.get().isEnabled(player)) return;

        // Only *block* containers can be rewarded. PlayerContainerEvent.Open also fires for the
        // player's own inventory, a horse, a villager trade screen and any modded menu with no
        // world position — none of which a "found a locked container" reward should pay for, and
        // all of which are reopenable at will (RS-001).
        BlockPos opened = findOpenedContainer(serverPlayer);
        if (opened == null) return;

        HandlerCommonConfig c = HandlerCommonConfig.HANDLER.instance();
        long container = ContainerRewardLedger.key(
                serverPlayer.level().dimension().location().toString(), opened.asLong());
        // Each distinct container pays out at most once per window, with a floor between any two
        // payouts. Without this, holding right-click on a single crafting table produced unbounded
        // vanilla XP — the currency the whole skill progression is bought with (RS-001).
        if (!ContainerRewardLedger.claim(serverPlayer.getUUID(), container,
                serverPlayer.level().getGameTime(),
                Math.max(0L, (long) c.locksmithContainerCooldownMinutes * 60L * 20L),
                Math.max(0L, (long) c.locksmithMinSecondsBetweenRewards * 20L))) {
            return;
        }

        if (!ProcRoll.rolls(RegistryPerks.LOCKSMITH.get().getActiveValue(player)[0])) return;
        int bonusXp = 5;
        if (RegistryPerks.SAFE_CRACKER != null && RegistryPerks.SAFE_CRACKER.get().isEnabled(player)) {
            bonusXp += (int) RegistryPerks.SAFE_CRACKER.get().getActiveValue(player)[0];
        }
        serverPlayer.giveExperiencePoints(bonusXp);
    }

    /**
     * Resolves the world position of the block container a player just opened, or {@code null}
     * when the menu is not backed by one. Forge exposes no accessor for a menu's
     * {@code ContainerLevelAccess}, so this reads the position the player is interacting with:
     * the block they are looking at, verified to actually carry a block entity.
     */
    private static BlockPos findOpenedContainer(ServerPlayer player) {
        net.minecraft.world.phys.HitResult hit = player.pick(6.0D, 0.0F, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult block)) return null;
        BlockPos pos = block.getBlockPos();
        return player.level().getBlockEntity(pos) == null ? null : pos;
    }

    @SubscribeEvent
    public void onPickupXp(PlayerXpEvent.PickupXp event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp) || player instanceof FakePlayer) return;
        if (RegistryPerks.LORE_MASTERY == null || !RegistryPerks.LORE_MASTERY.get().isEnabled(sp)) return;
        if (!(sp.containerMenu instanceof net.minecraft.world.inventory.GrindstoneMenu)) return;

        // Require a freshly-spawned orb. A grindstone drops its experience at the player's feet
        // and it is collected within a tick or two; orbs arriving from an XP farm have travelled
        // and are older. Without this the perk multiplied ANY orb picked up while a grindstone
        // screen happened to be open, which turned "stand in your XP farm with a grindstone
        // menu up" into a flat XP multiplier (RS-058).
        if (event.getOrb().tickCount > 2) return;

        int originalXp = event.getOrb().getValue();
        int bonusXp = (int) (originalXp * (RegistryPerks.LORE_MASTERY.get().getActiveValue(sp)[0] - 1.0));
        if (bonusXp > 0) {
            sp.giveExperiencePoints(bonusXp);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityDrops(LivingDropsEvent event) {
        if (event.getEntity() != null) {
            if (!(event.getEntity() instanceof Player)) {
                Entity entity1 = event.getSource().getEntity();
                if (entity1 instanceof ServerPlayer player) {
                    if (RegistryPerks.FIGHTING_SPIRIT != null) {
                        new RegistryEffects.AddEffect(player, RegistryPerks.FIGHTING_SPIRIT.get().isEnabled(player), MobEffects.DAMAGE_BOOST).add(com.otectus.runicskills.common.util.DurationMath.secondsToTicks(RegistryPerks.FIGHTING_SPIRIT.get().getActiveValue(player)[1]), (int) (RegistryPerks.FIGHTING_SPIRIT.get().getActiveValue(player)[0] - 1.0D));
                    }
                }
            }

            Entity entity = event.getSource().getEntity();
            if (entity instanceof Player player) {
                if (RegistryPerks.LIFE_EATER != null && RegistryPerks.LIFE_EATER.get().isEnabled(player)) {
                    player.heal((float) RegistryPerks.LIFE_EATER.get().getActiveValue(player)[0]);
                }
            }

            if (!(event.getEntity() instanceof Player)) {
                entity = event.getSource().getEntity();
                if (entity instanceof ServerPlayer player && !(entity instanceof FakePlayer)) {
                    if (RegistryPerks.LUCKY_DROP != null && RegistryPerks.LUCKY_DROP.get().isEnabled(player)
                            && ProcRoll.rolls(RegistryPerks.LUCKY_DROP.get().getActiveValue(player)[0])) {
                        // Multiply THIS death's drops, which the event hands us directly. The old
                        // implementation waited a tick and then re-scanned the world for any
                        // ItemEntity with tickCount <= 1 inside a 2x2x2 box around the corpse,
                        // which meant a stack the player manually dropped at the right moment was
                        // multiplied too — a trivially automatable duplicator (RS-003).
                        int multiplier = (int) RegistryPerks.LUCKY_DROP.get().getActiveValue(player)[1];
                        if (multiplier > 1) {
                            List<ItemStack> equipment = new ArrayList<>();
                            for (ItemStack next : event.getEntity().getAllSlots()) {
                                equipment.add(next);
                            }
                            boolean multiplied = false;
                            for (ItemEntity dropEntity : event.getDrops()) {
                                ItemStack itemStack = dropEntity.getItem();
                                // Unstackable items are the mob's own equipment; multiplying them
                                // is both meaningless and the usual route to duplicating gear.
                                if (itemStack.getMaxStackSize() <= 1) continue;
                                if (equipment.contains(itemStack)) continue;
                                itemStack.setCount(itemStack.getCount() * multiplier);
                                dropEntity.setItem(itemStack);
                                multiplied = true;
                            }
                            if (multiplied) {
                                PlayerMessagesCP.send(player, "overlay.perk.runicskills.lucky_drop", multiplier);
                            }
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
