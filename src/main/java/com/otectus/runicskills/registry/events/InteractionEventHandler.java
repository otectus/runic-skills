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

    // `item` is the acting-hand stack: PlayerInteractEvent fires once per hand and
    // event.getItemStack() returns player.getItemInHand(event.getHand()). So off-hand use
    // (shield raise, off-hand food, off-hand right-click) is gated by the same call — the
    // off-hand event arrives with the off-hand stack here. No separate off-hand lookup needed.
    public static boolean shouldCancelInteraction(Player player, ItemStack item, Block block, Entity target) {
        return shouldCancelInteraction(player, item, block, target, com.otectus.runicskills.integration.lock.LockAction.USE);
    }

    public static boolean shouldCancelInteraction(Player player, ItemStack item, Block block, Entity target,
            com.otectus.runicskills.integration.lock.LockAction action) {
        SkillCapability provider = SkillCapability.get(player);
        if (provider == null) return false;

        // getKey() returns null for an unregistered/removed-mod item. requireNonNull threw an NPE
        // mid-interaction; an unregistered item can't match any lock, so allow it (same fix as
        // SkillCapability.canUseItem).
        ResourceLocation location = ForgeRegistries.ITEMS.getKey(item.getItem());
        if (location == null) return false;
        if (!provider.canUseItem(player, item, action)) return true;
        if (block != null && !provider.canUseBlock(player, block, blockActionFor(action))) return true;
        if (target != null && !provider.canUseEntity(player, target)) return true;
        return false;
    }

    /**
     * The question to ask about the <em>block</em> when the player is doing {@code action} with the
     * item in their hand.
     *
     * <p>They are two different rules about two different things and one swing can be subject to
     * both: {@code MINE} is about the tool, {@code MINE_BLOCK} about what is being dug. A typed
     * block rule can therefore gate operating a workstation without also forbidding breaking it,
     * which one entry in the action-blind id table could never express. {@code null} for an action
     * with no block half, which asks the historical question and gets the historical answer.
     */
    private static com.otectus.runicskills.integration.lock.LockAction blockActionFor(
            com.otectus.runicskills.integration.lock.LockAction action) {
        return switch (action) {
            case MINE -> com.otectus.runicskills.integration.lock.LockAction.MINE_BLOCK;
            case USE -> com.otectus.runicskills.integration.lock.LockAction.INTERACT_BLOCK;
            default -> null;
        };
    }

    /**
     * Placement, which no other seam sees.
     *
     * <p>Forge's {@code EntityPlaceEvent} is cancelable and fires before the block state is
     * committed, so a denial leaves the world and the player's inventory exactly as they were. Off
     * by default via {@code autoGatePlacement}: an inferred rule only carries {@code PLACE_BLOCK}
     * when that toggle is on, so with the shipped defaults this handler finds no rule and cancels
     * nothing. An authored datapack rule naming {@code place_block} is enforced either way, because
     * that is a rule somebody wrote rather than one the engine proposed.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlaceBlock(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isCreative() || player instanceof FakePlayer) return;
        if (player.level().isClientSide()) return;
        SkillCapability provider = SkillCapability.get(player);
        if (provider == null) return;
        Block placed = event.getPlacedBlock().getBlock();
        if (!provider.canUseBlock(player, placed,
                com.otectus.runicskills.integration.lock.LockAction.PLACE_BLOCK)) {
            event.setCanceled(true);
        }
    }

    /**
     * Harvesting the targeted block, as opposed to the tool doing it.
     *
     * <p>{@code CombatEventHandler.onBreakBlockLockGate} already asks whether the held tool may be
     * used; this asks whether the block may be taken. Silent, for the same anti-spam reason: the
     * discrete left-click event above has already sent one overlay warning. Off by default via
     * {@code autoGateHarvestBlocks}, on the same terms as placement.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHarvestBlock(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isCreative() || player instanceof FakePlayer) return;
        if (player.level().isClientSide()) return;
        SkillCapability provider = SkillCapability.get(player);
        if (provider == null) return;
        Block broken = event.getState().getBlock();
        if (!provider.canUseBlock(player, broken,
                com.otectus.runicskills.integration.lock.LockAction.MINE_BLOCK)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (player.isCreative() || player instanceof FakePlayer) return;
        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (shouldCancelInteraction(player, event.getItemStack(), block, null, com.otectus.runicskills.integration.lock.LockAction.MINE)) event.setCanceled(true);
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

                if (!provider.canUseItem(player, item, com.otectus.runicskills.integration.lock.LockAction.EQUIP)) {
                    player.drop(item.copy(), false);
                    item.setCount(0);
                }
            }
        }
    }
}
