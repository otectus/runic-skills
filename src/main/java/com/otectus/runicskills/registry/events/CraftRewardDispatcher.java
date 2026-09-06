package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.crafting.CraftOperationContext;
import com.otectus.runicskills.common.crafting.CraftRewardPolicy;
import com.otectus.runicskills.common.crafting.CraftingExecutionGuard;
import com.otectus.runicskills.common.util.ProcRoll;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Predicate;

/**
 * The single place a craft is paid a bonus copy of its result.
 *
 * <p>Five perks and one attribute used to do this in two handlers a file apart, each copying the
 * event's stack on its own terms: {@code PerkEffectsHandler} put its copies in the inventory,
 * {@code CraftingEventHandler} dropped Crafting Luck's on the floor, and neither knew how many the
 * other had already granted. Nothing bounded the total, and nothing asked what the craft had been —
 * so a repair-by-crafting or an ingot/block cycle with several crafting perks taken paid several
 * copies for an operation that created no material at all (RS207-01, RS207-10).
 *
 * <p>Now one dispatcher classifies the craft once, asks {@link CraftRewardPolicy} whether it may be
 * rewarded, and spends a single budget across every perk in the order they were previously
 * evaluated. Each perk still rolls its own chance — the budget caps the payout, it does not
 * guarantee one.
 *
 * <p>Runs at {@code HIGHEST} so the budget is spent before any listener that might cancel or alter
 * the craft downstream, matching where these payouts fired before.
 *
 * <p><b>Foreign stations yield to their own bridge.</b> {@code ItemCraftedEvent} is not vanilla's
 * private business: Tinker's Construct fires it from its station's {@code onCraft} with its own
 * container, and that container is not a crafting grid, so everything this dispatcher knows how to
 * ask about a craft would be answered "unknown" — the safe answer, but the wrong one, because the
 * station knows exactly what its operation was. {@link #yieldContainers} lets the integration that
 * owns such a menu claim it, and the classification then comes from the recipe rather than from a
 * guess about a grid that is not there. The predicate lives here as a hook rather than an
 * {@code instanceof}, so no {@code slimeknights} type is named by common code.
 */
public class CraftRewardDispatcher {

    /**
     * Containers whose crafts are paid by an integration rather than here.
     *
     * <p>Volatile and replaced whole. It is installed once during mod construction, long before any
     * craft, and read on the server thread from then on.
     */
    private static volatile Predicate<Container> yieldContainers = container -> false;

    /**
     * Hands responsibility for {@code containers} to whoever installed the predicate.
     *
     * <p>Additive: a second caller's containers are recognised alongside the first's, so two
     * integrations claiming their own menus cannot silently unclaim each other's.
     */
    public static void yieldTo(Predicate<Container> containers) {
        if (containers == null) return;
        Predicate<Container> previous = yieldContainers;
        yieldContainers = container -> previous.test(container) || containers.test(container);
    }

    /** Whether this container's crafts are paid elsewhere. */
    public static boolean yieldsToIntegration(Container container) {
        return container != null && yieldContainers.test(container);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        // ItemCraftedEvent fires on both logical sides, and a shift-click fires it twice: once with
        // the full pre-move copy and once from onTake with the emptied original. Requiring a
        // ServerPlayer rejects the client firing; the isEmpty check discards the second.
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (event.getCrafting().isEmpty()) return;
        // A reward inserted below can be observed by another mod's menu as a craft of its own.
        if (CraftingExecutionGuard.isReentrant()) return;
        // The station bridge has already been paid for this take, from a seam that knows the recipe.
        if (yieldsToIntegration(event.getInventory())) return;

        try (CraftingExecutionGuard.Scope scope = CraftingExecutionGuard.enter()) {
            pay(player, CraftOperationContext.fromVanillaCraftEvent(player, event));
        }
    }

    /**
     * Pays a described craft, if the policy allows it at all.
     *
     * <p>Public because the vanilla event is not the only place a craft is committed. An
     * integration that owns its own station builds the context from its own recipe and calls this,
     * so the budget, the policy and the perk order are one implementation rather than one per menu
     * — which is the whole reason RS207-01 was a defect in the first place.
     *
     * <p>The caller is responsible for the re-entrancy guard: the vanilla path enters it around a
     * dispatch, while a station bridge holds a wider scope of its own.
     *
     * @return how many copies were actually paid, which is what a station reports to a script
     */
    public static int pay(ServerPlayer player, CraftOperationContext context) {
        if (player == null || context == null || context.result().isEmpty()) return 0;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int budget = CraftRewardPolicy.allowedExtraOutputs(context, config);
        if (budget <= 0) return 0;
        return distribute(player, context.result(), budget, config);
    }

    /**
     * Spends up to {@code budget} copies among the perks that roll successfully.
     *
     * <p>Evaluation order is the order these perks were checked before the merge, so a player with
     * several of them keeps the same perk paying out first. Each successful roll costs one copy and
     * the loop stops at the cap, which is what makes the total bounded rather than the sum of
     * however many crafting perks happen to be taken.
     */
    private static int distribute(ServerPlayer player, ItemStack result, int budget,
                                  HandlerCommonConfig config) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(result.getItem());
        int paid = 0;

        if (paid < budget && on(RegistryPerks.ASSEMBLY_LINE, player)
                && ProcRoll.rollsPercent(config.assemblyLinePercent)) paid++;
        if (paid < budget && on(RegistryPerks.MASS_PRODUCTION, player)
                && ProcRoll.rollsPercent(config.massProductionPercent)) paid++;
        if (paid < budget && on(RegistryPerks.ALLOY_MASTER, player) && path(id, "ingot")
                && ProcRoll.rollsPercent(config.alloyMasterPercent)) paid++;
        if (paid < budget && on(RegistryPerks.MASTER_WOODWORKER, player) && path(id, "planks")
                && ProcRoll.rollsPercent(config.masterWoodworkerPercent)) paid++;
        if (paid < budget && on(RegistryPerks.MEDIEVAL_ARCHITECTURE, player)
                && result.getItem() instanceof BlockItem
                && ProcRoll.rollsPercent(config.medievalArchitecturePercent)) paid++;

        // Crafting Luck is an attribute rather than a perk, so any source may grant it — but it
        // buys a copy of the same result out of the same budget, not a copy of its own beside it.
        if (paid < budget) {
            double luck = player.getAttributeValue(RegistryAttributes.CRAFTING_LUCK.get());
            if (luck > 0 && ProcRoll.rollsPercent(luck)) paid++;
        }

        for (int copy = 0; copy < paid; copy++) {
            ItemStack bonus = result.copy();
            bonus.setCount(1);
            player.getInventory().placeItemBackInInventory(bonus);
        }
        return paid;
    }

    private static boolean on(RegistryObject<Perk> perk, ServerPlayer player) {
        return perk != null && perk.get() != null && perk.get().isEnabled(player);
    }

    private static boolean path(ResourceLocation id, String segment) {
        return id != null && id.getPath().contains(segment);
    }
}
