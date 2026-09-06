package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.crafting.RecyclingIndex;
import com.otectus.runicskills.common.crafting.RecyclingRule;
import com.otectus.runicskills.common.util.ContainerInteraction;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Workshop and homestead perks — salvage, farming and mitigation — all previously registered with
 * config, tooltips and textures but no runtime effect at all (RS10-004).
 */
public class WorkshopPerkHandler {

    // ── Salvage ─────────────────────────────────────────────────────────────────────────────

    /**
     * Disassembler and Salvage Master — recover materials from an item that has just broken.
     *
     * <p>What comes back is read from {@link RecyclingIndex}, a datapack allowlist, rather than
     * derived from whatever recipe happens to produce the item. Deriving it meant walking the whole
     * recipe manager per event and treating "there is a recipe" as "this is worth its ingredients",
     * which is not the same statement and is not one any pack ever made (RS207-03).
     *
     * <p>This one stays a trigger rather than moving to the grindstone: the item is genuinely gone,
     * consumed by its own breaking, so there is nothing here to consume twice.
     */
    @SubscribeEvent
    public void onItemDestroyed(PlayerDestroyItemEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        if (!config.recyclingEnabled) return;

        RecyclingRule rule = RecyclingIndex.get().ruleFor(event.getOriginal());
        // An item with no rule yields nothing. Silence is the correct answer: a pack that wants a
        // broken pickaxe to return scrap says so in a rule.
        if (rule == null) return;

        double chance = 0.0;
        if (RegistryPerks.DISASSEMBLER != null && RegistryPerks.DISASSEMBLER.get().isEnabled(player)) {
            chance += config.disassemblerPercent / 100.0;
        }
        if (RegistryPerks.SALVAGE_MASTER != null && RegistryPerks.SALVAGE_MASTER.get().isEnabled(player)) {
            chance += config.salvageMasterPercent / 100.0;
        }
        if (chance <= 0) return;

        for (ItemStack recovered : recover(rule, player, chance)) {
            if (!player.getInventory().add(recovered)) player.drop(recovered, false);
        }
    }

    /**
     * Resource Efficiency and Salvage Expert — a workstation can be taken apart for part of what
     * built it, at a grindstone.
     *
     * <p><b>Why the grindstone and not a block break (RS207-02).</b> Both perks used to pay out on
     * {@code BlockEvent.BreakEvent}, which consumes nothing: the block dropped as an item as usual,
     * <em>and</em> its materials came back. Placing and breaking one crafting table in a loop
     * therefore produced planks forever. A salvage has to cost the thing being salvaged, and the
     * grindstone is the vanilla surface that already does exactly that — Forge's
     * {@code GrindstoneEvent} lets a mod set the output, and vanilla's own take path then clears
     * the inputs. The payout <em>is</em> the output, so closing the menu returns the input and
     * produces nothing.
     *
     * <p>The event carries no player, which is what {@link ContainerInteraction} exists for: the
     * click being processed knows who is clicking.
     */
    @SubscribeEvent
    public void onGrindstoneChange(GrindstoneEvent.OnPlaceItem event) {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        if (!config.recyclingEnabled) return;
        // Bottom slot empty: with something in it the player is combining or disenchanting, and
        // vanilla's own result is what they asked for.
        if (!event.getBottomItem().isEmpty()) return;

        RecyclingRule rule = RecyclingIndex.get().ruleFor(event.getTopItem());
        if (rule == null) return;

        if (!(ContainerInteraction.currentPlayer() instanceof ServerPlayer player)
                || player instanceof FakePlayer) {
            return;
        }
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

        List<ItemStack> recovered = recover(rule, player, chance);
        if (recovered.isEmpty()) return;

        // One output slot, so one stack. The first recovered material is what the grindstone
        // offers; a rule that wants several materials states them in the order it wants them
        // offered.
        event.setOutput(recovered.get(0));
        // Vanilla's grindstone experience is the enchantments it is stripping. There are none here,
        // and paying experience for a salvage would make the perk an XP source as well as a
        // material one.
        event.setXp(0);
    }

    /**
     * What one salvage of {@code rule} returns for {@code player}, each output rolled separately.
     *
     * <p>Rolled with {@link AnvilPerkHandler#stableRoll} rather than from the player's random
     * source. {@code GrindstoneEvent.OnPlaceItem} fires on every change to the inputs, so a live
     * roll would let a player take the item out and put it back until the salvage succeeded, and
     * would flicker the preview in between. Seeding on the item and the player means the only route
     * to a different answer is a different item — which costs something.
     */
    private static List<ItemStack> recover(RecyclingRule rule, Player player, double chance) {
        List<ItemStack> recovered = new ArrayList<>();
        ItemStack seedStack = new ItemStack(rule.input());
        int total = 0;
        for (RecyclingRule.Output output : rule.outputs()) {
            String salt = "salvage:" + rule.id() + ":" + output.item() + ":" + player.getUUID();
            if (!AnvilPerkHandler.stableRoll(seedStack, ItemStack.EMPTY, salt, Math.min(1.0, chance))) {
                continue;
            }
            int count = output.count() * salvagedCount(rule, player, output);
            // The rule's own ceiling, applied across every output together, so a yield perk cannot
            // multiply one salvage past what the pack said the item was worth.
            count = Math.min(count, Math.max(0, rule.maxRecovery() - total));
            if (count <= 0) continue;
            ItemStack stack = new ItemStack(output.item());
            stack.setCount(count);
            recovered.add(stack);
            total += count;
        }
        return recovered;
    }

    /**
     * How many of a recovered material a single successful salvage hands back.
     *
     * <p>Salvage Luck — "Salvaging items yields more materials" — is the whole of this: without it
     * every recovery is the rule's stated count, and with it each recovery has a proportional
     * chance of being doubled. It multiplies the yield rather than the odds on purpose, so it reads
     * differently from Disassembler and Salvage Master, which are what decide whether a material
     * comes back at all.
     *
     * <p>Rolled rather than rounded, so a bonus below 100% is not lost: a 15% perk means three
     * doubled recoveries in twenty, not zero. Rolled <em>stably</em>, for the reason given on
     * {@link #recover}.
     */
    private static int salvagedCount(RecyclingRule rule, Player player, RecyclingRule.Output output) {
        if (RegistryPerks.SALVAGE_LUCK == null || !RegistryPerks.SALVAGE_LUCK.get().isEnabled(player)) {
            return 1;
        }
        double extra = HandlerCommonConfig.HANDLER.instance().salvageLuckPercent / 100.0;
        if (extra <= 0) return 1;
        int guaranteed = (int) extra;
        double remainder = extra - guaranteed;
        String salt = "salvage-luck:" + rule.id() + ":" + output.item() + ":" + player.getUUID();
        boolean doubled = remainder > 0 && AnvilPerkHandler.stableRoll(
                new ItemStack(rule.input()), ItemStack.EMPTY, salt, remainder);
        return 1 + guaranteed + (doubled ? 1 : 0);
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
