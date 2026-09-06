package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.common.actions.ActionOrigin;
import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.common.advancements.RunicCriteriaTriggers;
import com.otectus.runicskills.common.crafting.CraftOperationContext;
import com.otectus.runicskills.common.crafting.CraftOperationKind;
import com.otectus.runicskills.common.crafting.CraftResultTransformer;
import com.otectus.runicskills.common.crafting.CraftingExecutionGuard;
import com.otectus.runicskills.common.scripting.TinkerScriptHooks;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.registry.events.CraftRewardDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.ToolBuildingRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.repairing.IModifierRepairRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.repairing.ISpecializedRepairRecipe;
import slimeknights.tconstruct.tables.block.entity.inventory.LazyResultContainer.ILazyCrafter;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tables.menu.slot.LazyResultSlot;
import slimeknights.tconstruct.tables.recipe.TinkerStationPartSwapping;
import slimeknights.tconstruct.tables.recipe.TinkerStationRepairRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One take from a native crafting station, described, transformed once, and paid at most once.
 *
 * <p><b>Why a bridge and not the ordinary crafting handler.</b> The station fires vanilla's
 * {@code ItemCraftedEvent}, so every crafting perk this mod owns already sees it — with an
 * inventory that is not a crafting grid, no matching vanilla recipe, and a result that may be the
 * same tool the player put in, repaired. {@code CraftOperationContext} would classify all of that
 * as {@code UNKNOWN}, which denies rewards correctly but describes the operation wrongly: the
 * station knows whether it just assembled a tool, repaired one, swapped a part or renamed it,
 * because the recipe says so. This bridge asks the recipe and hands the answer to the same policy
 * every vanilla craft goes through, then tells the vanilla dispatcher to stay out of it
 * ({@link CraftRewardDispatcher#yieldTo}) so the take cannot be paid twice.
 *
 * <p><b>What the bytecode of 3.11.2.166 actually does</b>, since two of these decisions are only
 * defensible against the real flow:
 *
 * <ul>
 *   <li>{@code TinkerStationBlockEntity.onCraft(Player, ItemStack, int)} takes the player as a
 *       parameter and fires the crafting event <em>before</em> shrinking its inputs. So the actor
 *       never has to be recovered from the container interaction here, and the reward is decided
 *       while the inputs are still there to be described.</li>
 *   <li>Mantle's {@code MultiModuleContainerMenu.quickMoveStack} — the shift-click path, since
 *       neither Tinkers' menu overrides it — moves a copy through its own helpers and reaches
 *       {@code Slot.onTake} only if at least one item actually moved. A player whose inventory has
 *       no room gets an early return before any craft, take or consumption. <b>That is why there is
 *       no pending-result escrow here (E12):</b> the native path refuses before it commits rather
 *       than committing into nowhere, so there is no orphaned output to hold. An escrow would be a
 *       second delivery path competing with a native one that already works.</li>
 * </ul>
 *
 * <p><b>The delivered stack is transformed exactly once, and never the preview.</b>
 * {@code LazyResultContainer} caches one result computed with a {@code null} player and hands out
 * {@code copy()} on removal; Mantle's quick-move takes its own copy of the slot's item. Both copies
 * are made from the same cache, which is shared by everyone with the station open — so the transform
 * runs on each copy as it is created, for the player who is taking it, and the cache itself is left
 * exactly as Tinkers' computed it. See {@code MixLazyResultContainer}.
 */
public final class TConstructStationBridge {

    /** The kind a keystone fitting is published under, which is §14.5's own example. */
    private static final String KEYSTONE_SERVICE_KIND = "keystone_service";

    /** Claim name for the single payout of one station take, per root action. */
    private static final String REWARD_CLAIM = "tconstruct:station-reward";

    /** Quote revisions, monotonic for the server session, shared by every station. */
    private static final AtomicLong NEXT_REVISION = new AtomicLong(1L);

    private TConstructStationBridge() {
    }

    /**
     * Whether this container belongs to a native station.
     *
     * <p>Installed into the vanilla dispatcher by the bootstrap as a {@code Predicate<Container>},
     * which is what keeps the {@code instanceof} on this side of the integration boundary. The test
     * is against the recipe container interface rather than the concrete wrapper so the anvil and
     * any other station sharing the machinery are recognised too.
     */
    public static boolean isStationContainer(Container container) {
        return container instanceof ITinkerStationContainer;
    }

    /**
     * Whether this slot is a native station's result.
     *
     * <p>Installed into {@code ForeignResultSlots} by the bootstrap, so the lock refusal that
     * guards a crafting table's output guards a station's too — §6.2 step 1's "validate permission
     * and player eligibility before consuming or delivering anything", using the seam vanilla
     * already asks that question at.
     */
    public static boolean isResultSlot(Slot slot) {
        return slot instanceof LazyResultSlot;
    }

    /**
     * Whether {@code player} may take what this station is currently offering.
     *
     * <p>Installed into {@code ForeignResultSlots} by the bootstrap, so it is asked at
     * {@code Slot.mayPickup} — the one point a click and a shift-click both pass through, before
     * either has consumed an input or delivered a stack. Everything but the keystone service is
     * allowed through untouched: this is a refusal for one operation, not a second lock system.
     *
     * <p>Reaching the station from the slot goes through {@link StationCrafterView}, which
     * {@code MixLazyResultContainer} adds to the result container. Asking the station for its
     * current recipe rather than inspecting the result stack matters: a keystoned tool being
     * repaired also produces a stack carrying the modifier, and refusing that would stop players
     * repairing tools other smiths had worked on.
     */
    public static boolean allowsTake(Slot slot, Player player) {
        if (!(slot.container instanceof StationCrafterView view)) return true;
        if (!(view.runicskillsStationCrafter() instanceof TinkerStationBlockEntity station)) return true;
        if (!(station.getLastRecipe() instanceof KeystoneStationRecipe)) return true;

        Component refusal = KeystoneService.refusal(player);
        if (refusal == null) return scriptAllowsKeystone(player, station);
        // The station's error line is computed for whoever the preview was computed for, which is
        // usually nobody, so the reason has to be said to the player who actually tried.
        if (player instanceof ServerPlayer actor) actor.displayClientMessage(refusal, true);
        return false;
    }

    /**
     * Whether a server script permits this keystone fitting.
     *
     * <p>Asked at {@code mayPickup}, which is the last point before the station consumes the
     * netherite the service costs — §14.5's "before any input or resource consumption". A denial
     * refuses only this service: the tool stays in the station, nothing is spent, and every other
     * station operation is unaffected, which is why the gate is here and not around the craft.
     *
     * <p>Only a keystone take reaches this, so an ordinary repair or part swap posts nothing and a
     * pack pays no script cost for having the listener installed.
     */
    private static boolean scriptAllowsKeystone(Player player, TinkerStationBlockEntity station) {
        if (!(player instanceof ServerPlayer actor)) return true;
        TinkerScriptHooks.Operation operation = new TinkerScriptHooks.Operation(
                KEYSTONE_SERVICE_KIND, station.getLastRecipe().getId(),
                RunicActionContext.rootActionId(),
                TinkerScriptHooks.itemId(station.getItem(TinkerStationBlockEntity.TINKER_SLOT)), 1,
                TinkerScriptHooks.summarise(consumedInputs(station)), 0, 0);
        TinkerScriptHooks.Veto veto = TinkerScriptHooks.postOperationCheck(actor, operation);
        if (!veto.denied()) return true;
        actor.displayClientMessage(denial(veto), true);
        return false;
    }

    /** The sentence a denied script veto shows, or this mod's own when the script gave none. */
    private static Component denial(TinkerScriptHooks.Veto veto) {
        return veto.message() == null
                ? Component.translatable("message.runicskills.tconstruct.script_denied")
                : Component.literal(veto.message());
    }

    /**
     * What a native station recipe does, in this mod's vocabulary.
     *
     * <p>Classified from the recipe's own type, never from comparing inputs to outputs: a repair
     * and a part swap both hand back "the tool you put in, changed", and only the recipe knows
     * which change it made. Anything unrecognised stays {@link CraftOperationKind#UNKNOWN}, which
     * the reward policy denies — a new station recipe from an add-on is not something this release
     * can vouch for.
     *
     * <p>A pure rename never reaches here: {@code onCraft} returns immediately when the station has
     * no matching recipe, so {@link CraftOperationKind#RENAME} is not produced by this bridge in
     * 3.11. Renaming while another operation is pending is that operation, plus a name.
     */
    public static CraftOperationKind kindOf(ITinkerStationRecipe recipe) {
        if (recipe == null) return CraftOperationKind.UNKNOWN;
        if (recipe instanceof ToolBuildingRecipe) return CraftOperationKind.ASSEMBLY;
        if (recipe instanceof TinkerStationRepairRecipe
                || recipe instanceof ISpecializedRepairRecipe
                || recipe instanceof IModifierRepairRecipe) {
            return CraftOperationKind.REPAIR;
        }
        if (recipe instanceof TinkerStationPartSwapping) return CraftOperationKind.PART_SWAP;
        // Everything the modifier machinery does — adding, removing, extracting, sorting, dyeing —
        // is a change to an item that already exists, and none of it may be rewarded with a copy.
        String type = recipe.getClass().getName();
        if (type.startsWith("slimeknights.tconstruct.library.recipe.modifiers.")
                || type.startsWith("slimeknights.tconstruct.tools.recipe.")) {
            return CraftOperationKind.MODIFY;
        }
        return CraftOperationKind.UNKNOWN;
    }

    /** What the station in front of {@code crafter} is currently set up to do. */
    public static CraftOperationKind kindOf(ILazyCrafter crafter) {
        return crafter instanceof TinkerStationBlockEntity station
                ? kindOf(station.getLastRecipe()) : CraftOperationKind.UNKNOWN;
    }

    /**
     * Applies this mod's changes to one stack that is about to be handed to {@code player}.
     *
     * <p>Called once per delivered copy. Idempotent anyway — the durability stamp keeps the larger
     * value and the restore is bounded by the damage that is there — but the copy is fresh from an
     * untouched cache every time, so it never has to be.
     */
    public static void transformDelivered(Player player, ItemStack delivered, ILazyCrafter crafter) {
        if (!(player instanceof ServerPlayer actor) || player instanceof FakePlayer) return;
        if (delivered == null || delivered.isEmpty()) return;
        if (!TConstructCompatibilityStatus.current().supports(Capability.STATION_TRANSACTIONS)) return;
        CraftOperationKind kind = kindOf(crafter);
        CraftResultTransformer.transform(actor, delivered, kind);
        // The repair perks act here rather than at the commit callback for the same reason the
        // transform does: this is the one point reached identically by a click and a shift-click,
        // and it is the copy the player will actually hold. The station is still holding the tool
        // that went in, so the native restoration is readable as a difference rather than guessed.
        if (kind == CraftOperationKind.REPAIR && crafter instanceof TinkerStationBlockEntity station) {
            TConstructPerkHandler.onStationRepair(actor, delivered, station);
        }
        // Every take, not only a repair: an add-on perk may care about a first assembly, and this is
        // the only point that sees both without the adapter naming this bridge.
        TConstructPerkHandler.onStationDelivered(actor, delivered);
    }

    /**
     * Opens the action scope for one station take.
     *
     * <p>A station craft is a root action in its own right (§4.3 lists repair and modify among the
     * root kinds), and it needs an id for the same reason a swing does: the payout below is claimed
     * against it, so a re-entrant {@code onCraft} — a modifier that crafts, a mod that replays the
     * take — cannot be paid a second time for the same transaction.
     *
     * @return whether a frame was actually pushed; the caller must pass this to {@link #endCraft}
     */
    public static boolean beginCraft(Player player) {
        if (player == null) return false;
        return RunicActionContext.enter(ActionOrigin.STATION_CRAFT, player.getUUID());
    }

    /** Closes the scope {@link #beginCraft} opened, if it opened one. */
    public static void endCraft(boolean opened) {
        if (opened) RunicActionContext.exit();
    }

    /**
     * Describes and pays one committed station take.
     *
     * <p>Called from the head of {@code onCraft}, which in 3.11 is after the result is decided and
     * before the inputs shrink — so the inputs are still readable and the take has already been
     * accepted by the native menu. §6.2 step 5 wants the Runic commit to happen only once
     * consumption and delivery succeeded; the ordering here is the closest honest point, because
     * the native code past this line does not fail.
     */
    public static void onStationCraft(Player player, ItemStack result, ILazyCrafter crafter,
                                      Container inputs) {
        if (!(player instanceof ServerPlayer actor) || player instanceof FakePlayer) return;
        ItemStack crafted = committedResult(result, crafter);
        if (crafted.isEmpty()) return;
        if (!TConstructCompatibilityStatus.current().supports(Capability.STATION_TRANSACTIONS)) return;
        // One payout per station take. The claim is what makes that true even if the native take is
        // re-entered by something this mod did not write.
        if (!RunicActionContext.claim(REWARD_CLAIM)) return;
        if (CraftingExecutionGuard.isReentrant()) return;

        ITinkerStationRecipe recipe = crafter instanceof TinkerStationBlockEntity station
                ? station.getLastRecipe() : null;
        CraftOperationKind operationKind = kindOf(recipe);
        List<ItemStack> consumed = consumedInputs(inputs);
        int restored = nativeRestored(crafted, crafter, operationKind);

        // §14.5's pre-commit gate. The native take has already been accepted by the station and is
        // not this mod's to refuse; what a denial stops is everything below — the payout, the
        // Power triggers and the completion report — with nothing charged for any of them.
        TinkerScriptHooks.Operation operation = new TinkerScriptHooks.Operation(
                operationKind.name().toLowerCase(java.util.Locale.ROOT),
                recipe == null ? null : recipe.getId(), RunicActionContext.rootActionId(),
                TinkerScriptHooks.itemId(crafted), crafted.getCount(),
                TinkerScriptHooks.summarise(consumed), restored, 0);
        TinkerScriptHooks.Veto veto = TinkerScriptHooks.postOperationCheck(actor, operation);
        if (veto.denied()) {
            actor.displayClientMessage(denial(veto), true);
            return;
        }

        int copies;
        try (CraftingExecutionGuard.Scope scope = CraftingExecutionGuard.enter()) {
            copies = CraftRewardDispatcher.pay(actor, new CraftOperationContext(
                    actor.getUUID(), false, operationKind,
                    recipe == null ? null : recipe.getId(),
                    consumed, List.of(), crafted.copy()));
        }

        // The two quest-pack triggers this seam owns (§14.5). Fired from the committed, claimed
        // point rather than from the transform, so a preview, a hopper or a second delivery of the
        // same take cannot advance an advancement the pack meant to mark a real first.
        if (operationKind == CraftOperationKind.ASSEMBLY) {
            RunicCriteriaTriggers.TINKER_ASSEMBLY.trigger(actor, crafted);
        } else if (operationKind == CraftOperationKind.REPAIR && restored > 0) {
            RunicCriteriaTriggers.TINKER_PAID_REPAIR.trigger(actor, crafted);
        }

        // The two station operations an Artifice Power turns on. Taken here rather than at the
        // transform because this is the claimed, once-per-take point: an assembly counted twice
        // would advance a Crown's sequence on one tool, and a part swap armed twice would refresh
        // a pending change the player only made once. The tool slot still holds the input at this
        // point — 3.11 shrinks it after the callback — so the swap is readable as a difference.
        if (crafter instanceof TinkerStationBlockEntity station) {
            CraftOperationKind kind = kindOf(recipe);
            if (kind == CraftOperationKind.ASSEMBLY) {
                TConstructPowerDispatcher.onStationAssembly(actor, station);
            } else if (kind == CraftOperationKind.PART_SWAP) {
                ItemStack before = station.getItem(TinkerStationBlockEntity.TINKER_SLOT);
                if (TConstructEquipmentAdapter.isNativeTool(before)
                        && TConstructEquipmentAdapter.isNativeTool(crafted)) {
                    TConstructPowerDispatcher.onStationPartSwap(actor, crafted,
                            ToolStack.from(before), ToolStack.from(crafted), station);
                }
            }
        }

        // One report per root take, last, so what it says about the payout is what was actually
        // paid. Nothing a listener does can change any of it (§14.5).
        TinkerScriptHooks.postOperationCompleted(actor, operation.completed(restored, copies));
    }

    /**
     * What the native operation itself restored, or {@code 0} when it restored nothing.
     *
     * <p>Read the way the repair perks read it — the damage on the tool still sitting in the
     * station minus the damage on the stack coming out — because 3.11 shrinks the inputs after this
     * callback. It is reported to scripts as the native cost summary §14.5 asks for, and it is what
     * decides whether a repair was <em>paid</em> for the advancement trigger above.
     */
    private static int nativeRestored(ItemStack crafted, ILazyCrafter crafter,
                                      CraftOperationKind kind) {
        if (kind != CraftOperationKind.REPAIR) return 0;
        if (!(crafter instanceof TinkerStationBlockEntity station)) return 0;
        ItemStack before = station.getItem(TinkerStationBlockEntity.TINKER_SLOT);
        if (!TConstructEquipmentAdapter.isNativeTool(before)
                || !TConstructEquipmentAdapter.isNativeTool(crafted)) {
            return 0;
        }
        return Math.max(0, ToolStack.from(before).getDamage() - ToolStack.from(crafted).getDamage());
    }

    /**
     * The result this take actually committed.
     *
     * <p>Not always the stack the callback is handed. On a shift-click the result has already been
     * moved into the inventory by Mantle, and what reaches {@code onCraft} is the emptied leftover —
     * so a bridge that trusted the parameter would describe every click and no shift-click, which is
     * precisely the asymmetry RS207-05 was. The station's own cached result is still present at this
     * point (it is cleared immediately after the callback returns), and that is the base item the
     * take produced.
     */
    private static ItemStack committedResult(ItemStack result, ILazyCrafter crafter) {
        if (result != null && !result.isEmpty()) return result;
        if (crafter instanceof TinkerStationBlockEntity station) {
            ItemStack cached = station.getCraftingResult().getResult();
            if (cached != null) return cached;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Issues {@code player} a private quote for what the station in front of them would produce.
     *
     * <p>Nothing about the station is mutated: the base result is copied first and this mod's
     * changes are applied to the copy, which is exactly what happens again, independently, when the
     * result is actually taken. Two players looking at one station therefore get two answers and
     * neither can take the other's.
     */
    public static StationQuote quote(ServerPlayer player, int menuId, ItemStack baseResult,
                                     ILazyCrafter crafter, Container inputs) {
        Objects.requireNonNull(player, "player");
        ITinkerStationRecipe recipe = crafter instanceof TinkerStationBlockEntity station
                ? station.getLastRecipe() : null;
        CraftOperationKind kind = kindOf(recipe);
        ItemStack preview = baseResult == null ? ItemStack.EMPTY : baseResult.copy();
        if (!preview.isEmpty()) CraftResultTransformer.transform(player, preview, kind);
        return new StationQuote(player.getUUID(), menuId,
                recipe == null ? null : recipe.getId(), kind,
                fingerprint(inputs), fingerprintOf(baseResult),
                NEXT_REVISION.getAndIncrement(), preview);
    }

    /**
     * A copy of everything the station holds, tool slot included.
     *
     * <p>Read through the plain {@link Container} interface the station block entity already
     * implements, rather than through the recipe container: the recipe wrapper is a private field
     * of the block entity, and shadowing a private field to reach the same items through a second
     * interface would buy nothing but a way to break on a refactor.
     */
    private static List<ItemStack> consumedInputs(Container inputs) {
        List<ItemStack> consumed = new ArrayList<>();
        if (inputs == null) return consumed;
        for (int slot = 0; slot < inputs.getContainerSize(); slot++) {
            ItemStack input = inputs.getItem(slot);
            if (!input.isEmpty()) consumed.add(input.copy());
        }
        return consumed;
    }

    /** A cheap hash of what the station holds, for detecting a changed quote. */
    private static int fingerprint(Container inputs) {
        int hash = 1;
        for (ItemStack input : consumedInputs(inputs)) hash = hash * 31 + fingerprintOf(input);
        return hash;
    }

    private static int fingerprintOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int hash = stack.getItem().hashCode() * 31 + stack.getCount();
        return stack.getTag() == null ? hash : hash * 31 + stack.getTag().hashCode();
    }
}
