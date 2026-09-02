package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Workshop and homestead perks — salvage, farming and mitigation — all previously registered with
 * config, tooltips and textures but no runtime effect at all (RS10-004).
 */
public class WorkshopPerkHandler {

    // ── Lucky Charm ─────────────────────────────────────────────────────────────────────────

    /**
     * Lucky Charm — "All negative effects have shorter duration".
     *
     * <p>Applied as the effect arrives, which is the only point its duration is still open. Re-added
     * rather than mutated: {@code MobEffectInstance}'s duration is not safely writable from a
     * handler, and re-adding is the same path any other source would take.
     */
    @SubscribeEvent
    public void onHarmfulEffect(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        MobEffectInstance added = event.getEffectInstance();
        if (added == null || added.isInfiniteDuration()) return;
        if (added.getEffect().getCategory() != MobEffectCategory.HARMFUL) return;
        if (RegistryPerks.LUCKY_CHARM == null || !RegistryPerks.LUCKY_CHARM.get().isEnabled(player)) {
            return;
        }
        double cut = Math.min(0.90, HandlerCommonConfig.HANDLER.instance().luckyCharmPercent / 100.0);
        if (cut <= 0) return;

        int shortened = (int) (added.getDuration() * (1.0 - cut));
        if (shortened >= added.getDuration()) return;
        if (shortened <= 0) {
            player.removeEffect(added.getEffect());
            return;
        }
        player.addEffect(new MobEffectInstance(added.getEffect(), shortened,
                added.getAmplifier(), added.isAmbient(), added.isVisible()));
    }

    // ── Salvage ─────────────────────────────────────────────────────────────────────────────

    /**
     * Disassembler and Salvage Master — recover materials from an item that has just broken.
     *
     * <p>"Recovering components" needs a moment where an item ceases to exist and its make-up is
     * still known. Vanilla gives exactly one: the tool that broke in your hand. Both perks stack, so
     * a player who has taken both recovers proportionally more.
     */
    @SubscribeEvent
    public void onItemDestroyed(PlayerDestroyItemEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        ItemStack broken = event.getOriginal();
        if (broken.isEmpty()) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double chance = 0.0;
        if (RegistryPerks.DISASSEMBLER != null && RegistryPerks.DISASSEMBLER.get().isEnabled(player)) {
            chance += config.disassemblerPercent / 100.0;
        }
        if (RegistryPerks.SALVAGE_MASTER != null && RegistryPerks.SALVAGE_MASTER.get().isEnabled(player)) {
            chance += config.salvageMasterPercent / 100.0;
        }
        if (chance <= 0) return;

        for (ItemStack ingredient : ingredientsOf(player.level(), broken)) {
            if (player.getRandom().nextDouble() >= Math.min(1.0, chance)) continue;
            ItemStack recovered = ingredient.copy();
            recovered.setCount(salvagedCount(player));
            if (!player.getInventory().add(recovered)) player.drop(recovered, false);
        }
    }

    /**
     * Resource Efficiency and Salvage Expert — breaking a workstation returns part of what built it.
     *
     * <p>Only blocks the player crafted rather than found, since "returns materials" means the
     * materials that went in. The recipe lookup is what decides that: a stone block has no crafting
     * recipe of interest, a crafting table does.
     */
    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double chance = 0.0;
        if (RegistryPerks.RESOURCE_EFFICIENCY != null
                && RegistryPerks.RESOURCE_EFFICIENCY.get().isEnabled(player)) {
            chance += config.resourceEfficiencyPercent / 100.0;
        }
        if (RegistryPerks.SALVAGE_EXPERT != null
                && RegistryPerks.SALVAGE_EXPERT.get().isEnabled(player)) {
            chance += config.salvageExpertPercent / 100.0;
        }
        if (chance <= 0) return;

        BlockState state = event.getState();
        ItemStack asItem = new ItemStack(state.getBlock());
        if (asItem.isEmpty()) return;

        for (ItemStack ingredient : ingredientsOf(level, asItem)) {
            if (player.getRandom().nextDouble() >= Math.min(1.0, chance)) continue;
            ItemStack recovered = ingredient.copy();
            recovered.setCount(salvagedCount(player));
            Block.popResource(level, event.getPos(), recovered);
        }
    }

    /**
     * How many of a recovered material a single successful salvage hands back.
     *
     * <p>Salvage Luck — "Salvaging items yields more materials" — is the whole of this: without it
     * every recovery is one item, and with it each recovery has a proportional chance of being two.
     * It multiplies the yield rather than the odds on purpose, so it reads differently from
     * Disassembler and Salvage Master, which are what decide whether a material comes back at all.
     *
     * <p>Rolled rather than rounded, so a bonus below 100% is not lost: a 15% perk means three
     * doubled recoveries in twenty, not zero.
     */
    private static int salvagedCount(Player player) {
        if (RegistryPerks.SALVAGE_LUCK == null || !RegistryPerks.SALVAGE_LUCK.get().isEnabled(player)) {
            return 1;
        }
        double extra = HandlerCommonConfig.HANDLER.instance().salvageLuckPercent / 100.0;
        if (extra <= 0) return 1;
        int guaranteed = (int) extra;
        double remainder = extra - guaranteed;
        return 1 + guaranteed + (player.getRandom().nextDouble() < remainder ? 1 : 0);
    }

    /**
     * One example of each ingredient of the recipe that produces {@code result}, or empty if nothing
     * crafts it.
     *
     * <p>Takes the first matching recipe: an item with several recipes is being valued, not
     * reconstructed, and averaging across variants would be arbitrary in a different way.
     */
    private static List<ItemStack> ingredientsOf(Level level, ItemStack result) {
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            ItemStack produced = recipe.getResultItem(level.registryAccess());
            if (produced.isEmpty() || !produced.is(result.getItem())) continue;
            List<ItemStack> parts = new java.util.ArrayList<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                ItemStack[] options = ingredient.getItems();
                if (options.length > 0 && !options[0].isEmpty()) parts.add(options[0]);
            }
            return parts;
        }
        return List.of();
    }

    // ── Farming ─────────────────────────────────────────────────────────────────────────────

    /**
     * Farmer's Hand and Irrigation Expert — crops near their tender grow faster.
     *
     * <p>A crop has no owner, so the perk is attributed to a player standing near it — the same
     * "whoever is actually working this" attribution the brewing and smelting perks use, and what
     * the tooltip implies. Growth is granted by letting an extra tick through rather than by setting
     * the age directly, so every rule vanilla owns about what a crop needs still applies.
     */
    @SubscribeEvent
    public void onCropGrow(BlockEvent.CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double chance = 0.0;
        if (RegistryPerks.FARMERS_HAND != null && tendedBy(level, pos, RegistryPerks.FARMERS_HAND)) {
            chance += config.farmersHandPercent / 100.0;
        }
        if (RegistryPerks.IRRIGATION_EXPERT != null && isWaterAdjacent(level, pos)
                && tendedBy(level, pos, RegistryPerks.IRRIGATION_EXPERT)) {
            chance += config.irrigationExpertPercent / 100.0;
        }
        if (chance <= 0) return;

        if (level.getRandom().nextDouble() < Math.min(1.0, chance)) {
            // ALLOW forces this growth tick through, which is the extra growth the perk grants.
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
        }
    }

    /** How far a player may stand from a crop and still count as tending it. */
    private static final double TENDING_REACH = 12.0;

    private static boolean tendedBy(ServerLevel level, BlockPos pos,
                                    net.minecraftforge.registries.RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk) {
        AABB nearby = new AABB(pos).inflate(TENDING_REACH);
        for (Player player : level.getEntitiesOfClass(Player.class, nearby)) {
            if (perk.get().isEnabled(player)) return true;
        }
        return false;
    }

    private static boolean isWaterAdjacent(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!level.getFluidState(below.relative(direction)).isEmpty()) return true;
        }
        return !level.getFluidState(below).isEmpty();
    }
}
