package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.common.actions.ActionOrigin;
import com.otectus.runicskills.common.actions.BlockBreakCommittedEvent;
import com.otectus.runicskills.common.actions.ProjectileSnapshot;
import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.common.durability.WearAvoidance;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.common.workshop.WorkshopFocusService;
import com.otectus.runicskills.common.advancements.RunicCriteriaTriggers;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerDispatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.entity.player.Player;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;

import java.util.Map;
import java.util.UUID;

/**
 * What the twelve Artifice Powers actually do, at the seams stages S2 and S3 built.
 *
 * <p><b>No new proc bus (§11.5).</b> Every trigger here goes through the same three facilities the
 * seventy-five older Powers use: {@link PowerDispatch#isEquipped} for eligibility, which re-runs the
 * whole of {@code PowerEligibility} on every proc; the {@code PowerRuntime} cooldown map, extended
 * by {@link PowerCooldownDebt} so a Crown's cooldown survives a relog; and
 * {@link PowerDispatch#fireProc} for presentation, fired only once the effect has committed.
 *
 * <p><b>Nothing here owns a cap.</b> Damage, mining speed, incoming reduction, paid repair and
 * workshop progress each have exactly one composition point, and all five of them are somewhere
 * else — in {@link TConstructPerkHandler} or {@link TConstructWorkshopBridge}, where the perks
 * already compose. This class contributes a share to those sums and never applies one itself, which
 * is what §13.2 means by new content sharing the existing channels: First Heat at 10%, Hammer and
 * Tongs at 15% and Tempered Edge at 6% total 31% and are cut to the 30% cap once, together.
 *
 * <p><b>Only accepted events build counters (§11.1).</b> A cancelled hit never reaches the damage
 * stage, a refused block break never posts {@link BlockBreakCommittedEvent}, a repair that restored
 * nothing is not a repair, and a cast that consumed no fluid is not a cast. Each trigger below sits
 * past the point where native code committed, so a counter that advanced is a thing that happened.
 */
public final class TConstructPowerDispatcher {

    /** Claim names, so one root action can pay one proc however deeply it is nested. */
    private static final String CLAIM_PLUMB_LINE = "tcpower:plumb-line";
    private static final String CLAIM_LAUNCH = "tcpower:resonant-launch";
    private static final String CLAIM_RESONANT_HIT = "tcpower:resonant-hit";

    /**
     * Installs the two pull-shaped effects: the wear stage's extra avoidance and its one clamp.
     *
     * <p>Called once, from the bootstrap, exactly as {@link TConstructPerkHandler#install} is. The
     * contributions land in the shared sum under the shared 0.90 ceiling rather than in a second
     * Tinkers-only cap, which §13.2 forbids.
     */
    static void install() {
        WearAvoidance.addContributor(TConstructPowerDispatcher::wearAvoidanceShare);
        WearAvoidance.addClamp(TConstructPowerDispatcher::lastTemperClamp);
    }

    // -- Gates ---------------------------------------------------------------------------------

    /** Whether {@code power} may act for {@code player} right now. */
    private static boolean active(ServerPlayer player, RegistryObject<Power> power) {
        if (player == null || player instanceof FakePlayer || power == null) return false;
        return PowerDispatch.isEquipped(player, power);
    }

    /** The Power behind a {@link RegistryObject}, or {@code null} if it is not registered. */
    private static Power of(RegistryObject<Power> power) {
        return power == null || !power.isPresent() ? null : power.get();
    }

    /** The tick count of the server {@code player} is on, or {@code 0} when there is none. */
    private static long now(ServerPlayer player) {
        return player.getServer() == null ? 0L : player.getServer().getTickCount();
    }

    /** Starts {@code power}'s cooldown, recording the debt so a relog cannot clear it. */
    private static boolean spendCooldown(ServerPlayer player, Power power, long tick) {
        return PowerCooldownDebt.checkAndStart(player, power, tick, TConstructPowers.icdTicks(power));
    }

    /** Whether {@code power} is off cooldown, without starting one. */
    private static boolean ready(ServerPlayer player, Power power, long tick) {
        return PowerRuntime.InternalCooldowns.isAvailable(player.getUUID(), power.getName(), tick);
    }

    // -- Melee: First Heat, Hammer and Tongs ----------------------------------------------------

    /**
     * The Artifice share of the new outgoing-damage channel for one accepted primary melee hit.
     *
     * <p>Called from {@link TConstructPerkHandler}'s melee composition point, which has already
     * established everything both Powers depend on: a real {@code Player#attack}, a native melee
     * weapon, an open {@link ActionOrigin#MELEE} root, and one claim per swing. So "primary hit"
     * here is not a guess — a sweep child, a modifier secondary and a reflected hit never arrive.
     *
     * <p>Both prepared strikes can land on the same swing; they are summed and the caller caps the
     * sum. Each consumes its own preparation and starts its own cooldown, on the hit that was
     * actually enhanced.
     */
    public static double meleeDamageBonus(ServerPlayer player, long tick) {
        double bonus = 0.0;
        bonus += firstHeat(player, tick);
        bonus += hammerAndTongs(player, tick);
        return bonus;
    }

    /**
     * First Heat — three well-timed swings prepare the fourth.
     *
     * <p>Readiness is the vanilla attack-strength scale, so a spammed click is not a well-timed
     * swing; the enhanced hit resets the count and does not itself count towards the next sequence,
     * which is what stops a permanent 10%.
     */
    private static double firstHeat(ServerPlayer player, long tick) {
        Power power = of(RegistryPowers.TC_FIRST_HEAT);
        if (power == null || !active(player, RegistryPowers.TC_FIRST_HEAT)) return 0.0;
        TConstructPowerState.Player state = TConstructPowerState.of(player.getUUID());

        if (state.firstHeatPreparedUntil > tick) {
            state.firstHeatPreparedUntil = 0L;
            state.firstHeatHits = 0;
            if (!spendCooldown(player, power, tick)) return 0.0;
            PowerDispatch.fireProc(player, power);
            return TConstructPowers.value(power, "damage_percent") / 100.0;
        }
        state.firstHeatPreparedUntil = 0L;

        if (RunicActionContext.attackStrength(player.getUUID(), player.getAttackStrengthScale(0.5F))
                < TConstructPowers.value(power, "readiness")) {
            return 0.0;
        }
        long window = TConstructPowers.ticks(power, "window_seconds");
        if (state.firstHeatHits == 0 || tick - state.firstHeatWindowStart > window) {
            state.firstHeatWindowStart = tick;
            state.firstHeatHits = 0;
        }
        state.firstHeatHits++;
        if (state.firstHeatHits < TConstructPowers.intValue(power, "hits")) return 0.0;

        state.firstHeatHits = 0;
        // Prepared only when the cooldown has elapsed: the charge is the scarce thing, and arming
        // one that cannot be spent would let a player bank a strike through the cooldown.
        if (ready(player, power, tick)) {
            state.firstHeatPreparedUntil = tick + TConstructPowers.ticks(power, "prepared_seconds");
        }
        return 0.0;
    }

    /** Hammer and Tongs — a real block prepares a real answer. */
    private static double hammerAndTongs(ServerPlayer player, long tick) {
        Power power = of(RegistryPowers.TC_HAMMER_AND_TONGS);
        if (power == null || !active(player, RegistryPowers.TC_HAMMER_AND_TONGS)) return 0.0;
        TConstructPowerState.Player state = TConstructPowerState.peek(player.getUUID());
        if (state == null || state.hammerPreparedUntil <= tick) {
            if (state != null) state.hammerPreparedUntil = 0L;
            return 0.0;
        }
        state.hammerPreparedUntil = 0L;
        if (!spendCooldown(player, power, tick)) return 0.0;
        PowerDispatch.fireProc(player, power);
        return TConstructPowers.value(power, "damage_percent") / 100.0;
    }

    /**
     * Arms Hammer and Tongs from a shield block that actually prevented something.
     *
     * <p>{@code LOWEST} priority and the ordinary non-cancelled delivery: a listener that cancels
     * the block makes it as if the shield had not been eligible, and this must not have counted it
     * by then. The blocked amount is read after every other listener has had its say, so a mod that
     * reduces the block to nothing reduces it below the threshold too.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        Power power = of(RegistryPowers.TC_HAMMER_AND_TONGS);
        if (power == null || !active(player, RegistryPowers.TC_HAMMER_AND_TONGS)) return;
        // The slot that is actually blocking. A shield in the inventory, or a use item that is not
        // a shield, is not a block whatever the event says about damage.
        if (!player.isBlocking() || !player.getUseItem().is(net.minecraft.world.item.Items.SHIELD)
                && !player.getUseItem().is(TinkerTags.Items.SHIELDS)) {
            return;
        }
        if (event.getBlockedDamage() < TConstructPowers.value(power, "min_blocked")) return;

        long tick = now(player);
        if (!ready(player, power, tick)) return;
        TConstructPowerState.of(player.getUUID()).hammerPreparedUntil =
                tick + TConstructPowers.ticks(power, "window_seconds");
    }

    // -- Mining: Plumb Line, The Great Work -----------------------------------------------------

    /**
     * The Artifice share of the new mining-speed channel.
     *
     * <p>Called from {@link TConstructPerkHandler}'s break-speed composition point, after the
     * existing handlers have produced the speed, so native penalties and block effectiveness are
     * already in the number this multiplies.
     */
    public static double miningSpeedBonus(ServerPlayer player) {
        long tick = now(player);
        double bonus = 0.0;

        Power plumbLine = of(RegistryPowers.TC_PLUMB_LINE);
        if (plumbLine != null && active(player, RegistryPowers.TC_PLUMB_LINE)) {
            TConstructPowerState.Player state = TConstructPowerState.peek(player.getUUID());
            if (state != null && state.plumbLinePreparedUntil > tick) {
                bonus += TConstructPowers.value(plumbLine, "mining_percent") / 100.0;
            } else if (state != null) {
                state.plumbLinePreparedUntil = 0L;
            }
        }

        Power greatWork = of(RegistryPowers.TC_GREAT_WORK);
        if (greatWork != null && active(player, RegistryPowers.TC_GREAT_WORK)
                && inspired(player, tick)) {
            bonus += TConstructPowers.value(greatWork, "mining_percent") / 100.0;
        }
        return bonus;
    }

    /**
     * Counts one committed mining action towards Plumb Line.
     *
     * <p>The claim is against the root action, so a hammer that breaks nine blocks is one action —
     * §11.2 says so explicitly — and a block a protection mod refused never reaches this event at
     * all. Grounded is checked here rather than at the speed, because the rule is about the action
     * that was performed, not about where the player happens to be standing a moment later.
     */
    @SubscribeEvent
    public void onBlockBreakCommitted(BlockBreakCommittedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null || player instanceof FakePlayer) return;
        Power power = of(RegistryPowers.TC_PLUMB_LINE);
        if (power == null || !active(player, RegistryPowers.TC_PLUMB_LINE)) return;
        if (!player.onGround()) return;
        if (!player.getMainHandItem().is(TinkerTags.Items.HARVEST)) return;
        if (!RunicActionContext.claim(CLAIM_PLUMB_LINE)) return;

        long tick = now(player);
        TConstructPowerState.Player state = TConstructPowerState.of(player.getUUID());
        long window = TConstructPowers.ticks(power, "window_seconds");
        if (state.plumbLineActions == 0 || tick - state.plumbLineWindowStart > window) {
            state.plumbLineWindowStart = tick;
            state.plumbLineActions = 0;
        }
        state.plumbLineActions++;
        if (state.plumbLineActions < TConstructPowers.intValue(power, "actions")) return;

        state.plumbLineActions = 0;
        if (!spendCooldown(player, power, tick)) return;
        state.plumbLinePreparedUntil = tick + TConstructPowers.ticks(power, "prepared_seconds");
        PowerDispatch.fireProc(player, power);
    }

    // -- Incoming damage: Quench, Workshop Aegis ------------------------------------------------

    /**
     * The Artifice share of the new incoming-reduction channel for one accepted hit.
     *
     * <p>Called from {@link TConstructPerkHandler}'s incoming composition point, which caps the sum
     * once and applies it to the accepted amount. Neither Power grants immunity and neither touches
     * a bypass rule: Quench is scoped to fire-tagged damage, and Workshop Aegis explicitly excludes
     * anything that bypasses invulnerability, which is the family §11.3 names as out of scope.
     */
    public static double incomingReduction(ServerPlayer player, DamageSource source) {
        if (source == null) return 0.0;
        long tick = now(player);
        double reduction = 0.0;

        Power quench = of(RegistryPowers.TC_QUENCH);
        if (quench != null && source.is(DamageTypeTags.IS_FIRE)
                && active(player, RegistryPowers.TC_QUENCH)) {
            TConstructPowerState.Player state = TConstructPowerState.peek(player.getUUID());
            if (state != null && state.quenchUntil > tick) {
                reduction += TConstructPowers.value(quench, "reduction_percent") / 100.0;
            } else if (state != null) {
                state.quenchUntil = 0L;
            }
        }

        Power aegis = of(RegistryPowers.TC_WORKSHOP_AEGIS);
        if (aegis != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                && active(player, RegistryPowers.TC_WORKSHOP_AEGIS)) {
            TConstructPowerState.Player state = TConstructPowerState.peek(player.getUUID());
            // "while remaining within the workshop's valid range": the focus is that range, and
            // losing it ends the benefit rather than merely stopping it from being renewed.
            if (state != null && state.aegisUntil > tick
                    && WorkshopFocusService.activeFocus(player) != null) {
                reduction += TConstructPowers.value(aegis, "reduction_percent") / 100.0;
            } else if (state != null && (state.aegisUntil <= tick
                    || WorkshopFocusService.activeFocus(player) == null)) {
                state.aegisUntil = 0L;
            }
        }
        return reduction;
    }

    // -- Wear: Temper Reserve, The Great Work, Last Temper ---------------------------------------

    /**
     * The extra avoidance the two wear Powers contribute to the shared sum.
     *
     * <p>Percentage <em>points</em>, as §11.3 and §11.4 word them, added to the same total that
     * Lucky Break and Precision Tools feed and clamped with them at 0.90.
     */
    private static double wearAvoidanceShare(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return 0.0;
        long tick = now(player);
        double share = 0.0;

        Power temper = of(RegistryPowers.TC_TEMPER_RESERVE);
        if (temper != null && active(player, RegistryPowers.TC_TEMPER_RESERVE)) {
            TConstructPowerState.Player state = TConstructPowerState.peek(player.getUUID());
            if (state != null && state.temperReserveUntil > tick
                    && TConstructPerkState.isSameTool(state.temperReserveTool, stack)) {
                share += TConstructPowers.value(temper, "avoidance_points") / 100.0;
            } else if (state != null && state.temperReserveUntil <= tick) {
                state.temperReserveUntil = 0L;
                state.temperReserveTool = TConstructPerkState.reference(ItemStack.EMPTY);
            }
        }

        Power greatWork = of(RegistryPowers.TC_GREAT_WORK);
        if (greatWork != null && active(player, RegistryPowers.TC_GREAT_WORK)
                && inspired(player, tick)) {
            share += TConstructPowers.value(greatWork, "avoidance_points") / 100.0;
        }
        return share;
    }

    /**
     * Last Temper — the one bounded exception to probabilistic wear (§13.2).
     *
     * <p>Runs after the probabilistic stage, on the points that would actually be spent, and only
     * for a tool that is currently usable with more than one durability left. It reduces the
     * pending loss to leave exactly one; it never repairs, never touches a broken or one-durability
     * tool, and never fires for native unbreakability — a tool Tinkers' will not damage never
     * reaches a wear stage, so the cooldown cannot be spent on one. The cooldown is spent only when
     * the clamp actually bites.
     */
    private static int lastTemperClamp(ServerPlayer player, ItemStack stack, int spending) {
        if (player == null || player instanceof FakePlayer || spending <= 0) return spending;
        if (!TConstructEquipmentAdapter.isNativeTool(stack)) return spending;
        Power power = of(RegistryPowers.TC_LAST_TEMPER);
        if (power == null || !active(player, RegistryPowers.TC_LAST_TEMPER)) return spending;

        ToolStack tool = ToolStack.from(stack);
        if (tool.isBroken() || tool.isUnbreakable()) return spending;
        int durability = tool.getStats().getInt(ToolStats.DURABILITY) - tool.getDamage();
        // More than one left, and this loss would take it to zero or below: the only case §11.4
        // describes. A tool already at one, or one that survives the hit, is left alone.
        if (durability <= 1 || spending < durability) return spending;

        long tick = now(player);
        if (!spendCooldown(player, power, tick)) return spending;
        PowerDispatch.fireProc(player, power);
        return durability - 1;
    }

    /** Whether Inspired is running for this player. */
    private static boolean inspired(ServerPlayer player, long tick) {
        TConstructPowerState.Player state = TConstructPowerState.peek(player.getUUID());
        if (state == null || state.greatWorkInspiredUntil <= 0L) return false;
        if (state.greatWorkInspiredUntil <= tick) {
            state.greatWorkInspiredUntil = 0L;
            return false;
        }
        return true;
    }

    // -- Station: Quench, Temper Reserve, Working Memory, The Great Work -------------------------

    /**
     * The Artifice share of the paid-repair channel for the repair being delivered.
     *
     * <p>Called from {@link TConstructPerkHandler}'s repair composition point, which sums it with
     * Material Harmony and applies {@code tconstructRepairBonusCap} to the total once. Working
     * Memory is <em>consumed</em> here, because this is the moment its bonus is actually paid.
     */
    public static double repairBonusShare(ServerPlayer player, ItemStack delivered,
                                          TinkerStationBlockEntity station) {
        return repairBonusShare(player, delivered, station, true);
    }

    public static double previewRepairBonusShare(ServerPlayer player, ItemStack delivered,
                                                 TinkerStationBlockEntity station) {
        return repairBonusShare(player, delivered, station, false);
    }

    private static double repairBonusShare(ServerPlayer player, ItemStack delivered,
                                           TinkerStationBlockEntity station, boolean commit) {
        if (player == null || player instanceof FakePlayer) return 0.0;
        long tick = now(player);
        double share = 0.0;

        Power workingMemory = of(RegistryPowers.TC_WORKING_MEMORY);
        if (workingMemory != null && active(player, RegistryPowers.TC_WORKING_MEMORY)) {
            TConstructPowerState.Player state = TConstructPowerState.peek(player.getUUID());
            if (state != null && state.workingMemoryUntil > tick
                    && station.getBlockPos().equals(state.workingMemoryStation)
                    && sameRepairedTool(state.workingMemoryTool,
                            station.getItem(TinkerStationBlockEntity.TINKER_SLOT))) {
                if (commit ? spendCooldown(player, workingMemory, tick) : ready(player, workingMemory, tick)) {
                    share += TConstructPowers.value(workingMemory, "repair_percent") / 100.0;
                    if (commit) {
                        clearWorkingMemory(state);
                        PowerDispatch.fireProc(player, workingMemory);
                    }
                }
            } else if (commit && state != null && state.workingMemoryUntil <= tick) {
                clearWorkingMemory(state);
            }
        }

        Power manyHands = of(RegistryPowers.TC_MANY_HANDS);
        if (manyHands != null && !RegistryPowers.isDisabled(manyHands)
                && TConstructPowers.unavailable(manyHands) == null) {
            TConstructPowerState.Player state = TConstructPowerState.peek(player.getUUID());
            if (state != null && state.manyHandsBonusUntil > tick) {
                share += TConstructPowers.value(manyHands, "repair_percent") / 100.0;
            } else if (commit && state != null) {
                state.manyHandsBonusUntil = 0L;
            }
        }
        return share;
    }

    /**
     * One committed native station repair, offered to the Powers that turn on one.
     *
     * <p>{@code restored} is the native paid amount — the difference between the tool that went in
     * and the tool coming out, before anything this mod added — which is what §11.2 and §11.3 mean
     * by "a qualifying paid repair". A one-point top-up therefore cannot renew either benefit.
     */
    static void onStationRepair(ServerPlayer player, ItemStack delivered, ToolStack output,
                                int restored) {
        if (player == null || player instanceof FakePlayer || restored <= 0) return;
        long tick = now(player);
        int max = output.getStats().getInt(ToolStats.DURABILITY);

        Power quench = of(RegistryPowers.TC_QUENCH);
        if (quench != null && active(player, RegistryPowers.TC_QUENCH)
                && restored >= threshold(quench, max)
                && spendCooldown(player, quench, tick)) {
            TConstructPowerState.of(player.getUUID()).quenchUntil =
                    tick + TConstructPowers.ticks(quench, "duration_seconds");
            PowerDispatch.fireProc(player, quench);
        }

        Power temper = of(RegistryPowers.TC_TEMPER_RESERVE);
        if (temper != null && active(player, RegistryPowers.TC_TEMPER_RESERVE)
                && restored >= threshold(temper, max)
                && spendCooldown(player, temper, tick)) {
            TConstructPowerState.Player state = TConstructPowerState.of(player.getUUID());
            state.temperReserveTool = TConstructPerkState.reference(delivered);
            state.temperReserveUntil = tick + TConstructPowers.ticks(temper, "duration_seconds");
            PowerDispatch.fireProc(player, temper);
        }

        greatWorkStep(player, GreatWorkStep.REPAIR, tick);
    }

    /**
     * The restoration a Power's trigger requires: a floor in points, or a share of the pool when
     * that is larger, and never more than the pool itself.
     */
    private static int threshold(Power power, int maxDurability) {
        int floor = TConstructPowers.intValue(power, "min_restored_points");
        int share = (int) Math.ceil(maxDurability * TConstructPowers.value(power, "min_restored_percent") / 100.0);
        return Math.min(Math.max(floor, share), Math.max(1, maxDurability));
    }

    /**
     * A committed part swap, offered to Working Memory.
     *
     * <p>"A paid, meaningful change to a native material part": the recipe classified the operation
     * as a part swap, which is the native definition of meaningful — a cosmetic finish and a swap
     * for the same material both leave the material list unchanged, and that is what is compared.
     *
     * <p>The pending tool is identified by the station it is in and the item it is, rather than by
     * stack identity: a station hands out a fresh copy on every take, so an identity reference
     * would be stale before the next repair could use it. Taking the tool elsewhere loses the
     * pending change, which is the conservative direction.
     */
    static void onStationPartSwap(ServerPlayer player, ItemStack delivered, ToolStack input,
                                  ToolStack output, TinkerStationBlockEntity station) {
        if (player == null || player instanceof FakePlayer) return;
        Power power = of(RegistryPowers.TC_WORKING_MEMORY);
        if (power == null || !active(player, RegistryPowers.TC_WORKING_MEMORY)) return;
        if (input.getMaterials().equals(output.getMaterials())) return;

        long tick = now(player);
        // One pending tool per player (§11.2): a second swap replaces the first rather than
        // banking both.
        TConstructPowerState.Player state = TConstructPowerState.of(player.getUUID());
        state.workingMemoryStation = station.getBlockPos().immutable();
        state.workingMemoryTool = repairIdentity(delivered);
        state.workingMemoryUntil = tick + TConstructPowers.ticks(power, "window_seconds");
    }

    /** A committed native tool assembly, offered to the two Crowns that count one. */
    static void onStationAssembly(ServerPlayer player, TinkerStationBlockEntity station) {
        if (player == null || player instanceof FakePlayer) return;
        long tick = now(player);
        greatWorkStep(player, GreatWorkStep.ASSEMBLY, tick);
        manyHandsContribution(player, station.getBlockPos(), tick);
    }

    private static void clearWorkingMemory(TConstructPowerState.Player state) {
        state.workingMemoryStation = null;
        state.workingMemoryTool = ItemStack.EMPTY;
        state.workingMemoryUntil = 0L;
    }

    // -- The Great Work -------------------------------------------------------------------------

    /** The three distinct operations §11.4 requires, in any order, within the window. */
    private enum GreatWorkStep {
        ASSEMBLY, REPAIR, CAST
    }

    /**
     * Records one of the three operations, and awards Inspired once all three are inside the window.
     *
     * <p>Distinct operations, not three of anything: each step keeps its own tick, an operation
     * repeated only refreshes its own, and the sequence is cleared on the proc so the next Inspired
     * needs three new operations rather than one.
     */
    private static void greatWorkStep(ServerPlayer player, GreatWorkStep step, long tick) {
        Power power = of(RegistryPowers.TC_GREAT_WORK);
        if (power == null || !active(player, RegistryPowers.TC_GREAT_WORK)) return;
        TConstructPowerState.Player state = TConstructPowerState.of(player.getUUID());
        switch (step) {
            case ASSEMBLY -> state.greatWorkAssemblyAt = tick;
            case REPAIR -> state.greatWorkRepairAt = tick;
            case CAST -> state.greatWorkCastAt = tick;
        }

        long window = TConstructPowers.ticks(power, "sequence_seconds");
        long oldest = Math.min(state.greatWorkAssemblyAt,
                Math.min(state.greatWorkRepairAt, state.greatWorkCastAt));
        if (oldest <= 0L || tick - oldest > window) return;

        state.greatWorkAssemblyAt = 0L;
        state.greatWorkRepairAt = 0L;
        state.greatWorkCastAt = 0L;
        if (!spendCooldown(player, power, tick)) return;
        state.greatWorkInspiredUntil = tick + TConstructPowers.ticks(power, "duration_seconds");
        PowerDispatch.fireProc(player, power);
        // §14.5's third quest-pack trigger, fired where the sequence actually completed: the
        // Power procced, so all three operations happened, in the window, for this player.
        RunicCriteriaTriggers.GREAT_WORK.trigger(player, player.getMainHandItem());
    }

    // -- Workshop: Workshop Aegis, Heart of the Foundry, Many Hands ------------------------------

    /**
     * One completed cast at a focused workshop, offered to the three Powers that turn on one.
     *
     * <p>Called from the casting seam for a cast that finished under a live focus, which is
     * §11.3's "attributed cast": the fluid was consumed by native code before this point, and the
     * player named is the one who claimed the workshop.
     */
    public static void onCastCompleted(Level level, BlockPos pos, ServerPlayer player) {
        if (player == null || player instanceof FakePlayer) return;
        long tick = now(player);

        Power aegis = of(RegistryPowers.TC_WORKSHOP_AEGIS);
        if (aegis != null && active(player, RegistryPowers.TC_WORKSHOP_AEGIS)
                && spendCooldown(player, aegis, tick)) {
            TConstructPowerState.of(player.getUUID()).aegisUntil =
                    tick + TConstructPowers.ticks(aegis, "duration_seconds");
            PowerDispatch.fireProc(player, aegis);
        }

        greatWorkStep(player, GreatWorkStep.CAST, tick);
        manyHandsContribution(player, pos, tick);
    }

    /**
     * The Artifice share of the workshop-progress channel.
     *
     * <p>Read by {@link TConstructWorkshopBridge}'s eligibility measurement, so it joins Thermal
     * Rhythm and the two older workshop perks under the same {@code tconstructWorkshopBonusCap}
     * ceiling — §13.2's worked example is precisely this sum.
     */
    public static double workshopProgressBonus(ServerPlayer player) {
        Power power = of(RegistryPowers.TC_FOUNDRY_HEART);
        if (power == null || !active(player, RegistryPowers.TC_FOUNDRY_HEART)) return 0.0;
        TConstructPowerState.Player state = TConstructPowerState.peek(player.getUUID());
        if (state == null || state.foundryUntil <= 0L) return 0.0;
        if (state.foundryUntil <= now(player)) {
            state.foundryUntil = 0L;
            return 0.0;
        }
        return TConstructPowers.value(power, "progress_percent") / 100.0;
    }

    /**
     * Records that {@code player} personally put an item into a melting slot they have focused.
     *
     * <p>The attribution W04 asks for, taken where it is unambiguous: a hopper, a servo or another
     * mod's automation does not run inside a container click, so it produces no record and can
     * never build the charge. The records are bounded at ten and are keyed by slot, so a stack
     * inserted once is one record however many operations native code gets out of it.
     */
    public static void onManualMeltingInsert(Level level, BlockPos controller, int slot,
                                             ServerPlayer player) {
        if (player == null || player instanceof FakePlayer || level == null) return;
        Power power = of(RegistryPowers.TC_FOUNDRY_HEART);
        if (power == null || !active(player, RegistryPowers.TC_FOUNDRY_HEART)) return;
        WorkshopFocusService.Focus focus = WorkshopFocusService.activeFocus(player);
        if (focus == null || !player.getUUID().equals(WorkshopFocusService.holderOf(level, controller))) {
            return;
        }
        TConstructPowerState.Player state = TConstructPowerState.of(player.getUUID());
        if (state.foundryInputs.size() >= TConstructPowerState.MAX_FOUNDRY_INPUTS) return;
        state.foundryInputs.add(TConstructPowerState.meltingKey(
                level.dimension().location().toString(), controller, slot));
    }

    /**
     * Counts one completed melting operation whose input this player personally inserted.
     *
     * <p>The record is spent here, so one insertion is one operation towards the ten however many
     * times native code melts out of the same stack.
     */
    public static void onMeltingCompleted(Level level, BlockPos controller, int slot) {
        if (level == null || level.getServer() == null) return;
        UUID holder = WorkshopFocusService.holderOf(level, controller);
        if (holder == null) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(holder);
        if (player == null) return;
        Power power = of(RegistryPowers.TC_FOUNDRY_HEART);
        if (power == null || !active(player, RegistryPowers.TC_FOUNDRY_HEART)) return;

        TConstructPowerState.Player state = TConstructPowerState.peek(player.getUUID());
        String key = TConstructPowerState.meltingKey(
                level.dimension().location().toString(), controller, slot);
        if (state == null || !state.foundryInputs.remove(key)) return;

        long tick = now(player);
        state.foundryOperations++;
        if (state.foundryOperations < TConstructPowers.intValue(power, "operations")) return;

        state.foundryOperations = 0;
        if (!spendCooldown(player, power, tick)) return;
        state.foundryUntil = tick + TConstructPowers.ticks(power, "duration_seconds");
        PowerDispatch.fireProc(player, power);
        // The bonus is read from the focus snapshot, which is refreshed on the next heartbeat;
        // asking for one now means the workshop speeds up on the proc rather than a second later.
        WorkshopFocusService.heartbeat(player);
    }

    /**
     * Records one qualifying contribution at a focused workshop, and awards Many Hands when the
     * owner and at least one consenting ally have both made one inside the window.
     *
     * <p>"Consenting allied player" is the mod's existing ally rule — a shared team — because that
     * is the only consent this mod can actually observe; standing nearby is not consent and is not
     * counted. The owner must have contributed personally, contributors are capped at four, and the
     * award goes to the actual contributors rather than to everyone present.
     */
    private static void manyHandsContribution(ServerPlayer contributor, BlockPos workshop, long tick) {
        MinecraftServer server = contributor.getServer();
        if (server == null) return;
        WorkshopFocusService.Focus focus = WorkshopFocusService.focusAt(contributor.level(), workshop);
        // Stations do not occupy a controller/associated-casting anchor. Attribute a station
        // assembly to the nearest active allied workshop whose configured radius covers it.
        if (focus == null) {
            double bestDistance = Double.MAX_VALUE;
            double radius = HandlerCommonConfig.HANDLER.instance().tconstructWorkshopFocusRadius;
            for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
                if (candidate.level() != contributor.level()) continue;
                if (candidate != contributor && !PowerRuntime.AllyDetector.isAlly(candidate, contributor)) continue;
                if (!active(candidate, RegistryPowers.TC_MANY_HANDS)) continue;
                WorkshopFocusService.Focus candidateFocus = WorkshopFocusService.activeFocus(candidate);
                if (candidateFocus == null) continue;
                double distance = candidateFocus.controller().distSqr(workshop);
                if (distance <= radius * radius && distance < bestDistance) {
                    focus = candidateFocus;
                    bestDistance = distance;
                }
            }
        }
        if (focus == null) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(focus.player());
        if (owner == null) return;
        Power power = of(RegistryPowers.TC_MANY_HANDS);
        if (power == null || !active(owner, RegistryPowers.TC_MANY_HANDS)) return;

        TConstructPowerState.Player state = TConstructPowerState.of(owner.getUUID());
        BlockPos anchor = focus.controller();
        if (!anchor.equals(state.manyHandsWorkshop)) {
            state.manyHandsWorkshop = anchor;
            state.manyHandsOwnerAt = 0L;
            state.manyHandsContributors.clear();
        }

        long window = TConstructPowers.ticks(power, "window_seconds");
        // Expire before applying the capacity limit, or three departed allies can fill the
        // collection and prevent a new contributor from completing the award.
        state.manyHandsContributors.entrySet().removeIf(entry -> tick - entry.getValue() > window);
        if (contributor.getUUID().equals(owner.getUUID())) {
            state.manyHandsOwnerAt = tick;
        } else if (PowerRuntime.AllyDetector.isAlly(owner, contributor)) {
            if (state.manyHandsContributors.size() < TConstructPowerState.MAX_CONTRIBUTORS - 1
                    || state.manyHandsContributors.containsKey(contributor.getUUID())) {
                state.manyHandsContributors.put(contributor.getUUID(), tick);
            }
        } else {
            return;
        }

        if (state.manyHandsOwnerAt <= 0L || tick - state.manyHandsOwnerAt > window) return;
        if (state.manyHandsContributors.isEmpty()) return;
        if (!spendCooldown(owner, power, tick)) return;

        int duration = TConstructPowers.ticks(power, "duration_seconds");
        award(owner, tick, duration);
        for (Map.Entry<UUID, Long> entry : state.manyHandsContributors.entrySet()) {
            ServerPlayer ally = server.getPlayerList().getPlayer(entry.getKey());
            if (ally != null && ally.level() == owner.level()
                    && PowerRuntime.AllyDetector.isAlly(owner, ally)) award(ally, tick, duration);
        }
        state.manyHandsOwnerAt = 0L;
        state.manyHandsContributors.clear();
        PowerDispatch.fireProc(owner, power);
    }

    /** Grants one contributor their paid-repair bonus, keeping the longest legitimate expiry. */
    private static void award(ServerPlayer player, long tick, int duration) {
        TConstructPowerState.Player state = TConstructPowerState.of(player.getUUID());
        state.manyHandsBonusUntil = Math.max(state.manyHandsBonusUntil, tick + duration);
    }

    // -- Ranged: Resonant Return ----------------------------------------------------------------

    /** Arms Resonant Return from a genuine native return of a thrown tool. */
    public static void onProjectileReturned(ServerPlayer player) {
        Power power = of(RegistryPowers.TC_RESONANT_RETURN);
        if (power == null || !active(player, RegistryPowers.TC_RESONANT_RETURN)) return;
        long tick = now(player);
        if (!ready(player, power, tick)) return;
        TConstructPowerState.of(player.getUUID()).resonantArmedUntil =
                tick + TConstructPowers.ticks(power, "window_seconds");
    }

    /**
     * The bonus one launch carries, recorded on the projectile itself.
     *
     * <p>Captured at launch, so switching or dropping the launcher afterwards cannot change what
     * the projectile is worth (C03). Claimed once per launch, so a multishot cannot turn one
     * prepared throw into three enhanced hits.
     */
    public static double launchBonus(ServerPlayer player) {
        Power power = of(RegistryPowers.TC_RESONANT_RETURN);
        if (power == null || !active(player, RegistryPowers.TC_RESONANT_RETURN)) return 0.0;
        TConstructPowerState.Player state = TConstructPowerState.peek(player.getUUID());
        if (state == null || state.resonantArmedUntil <= 0L) return 0.0;
        long tick = now(player);
        if (state.resonantArmedUntil <= tick) {
            state.resonantArmedUntil = 0L;
            return 0.0;
        }
        if (!RunicActionContext.claim(CLAIM_LAUNCH)) return 0.0;
        state.resonantArmedUntil = 0L;
        // The cooldown begins on the launch, per §11.3 — a shot that misses does not refund it.
        if (!spendCooldown(player, power, tick)) return 0.0;
        return TConstructPowers.value(power, "damage_percent") / 100.0;
    }

    /**
     * Pays a prepared launch on the first entity it actually hits.
     *
     * <p>The snapshot is the authority on ownership and on the amount, and the per-projectile claim
     * is what makes it the <em>first</em> accepted hit rather than every bounce. The share is capped
     * with the new outgoing-damage channel it belongs to.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onProjectileHit(LivingHurtEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof Projectile projectile)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (player instanceof FakePlayer) return;
        Power power = of(RegistryPowers.TC_RESONANT_RETURN);
        if (power == null || !active(player, RegistryPowers.TC_RESONANT_RETURN)) return;

        double bonus = ProjectileSnapshot.read(projectile)
                .filter(snapshot -> player.getUUID().equals(snapshot.owner()))
                .map(ProjectileSnapshot.Snapshot::bonus)
                .orElse(0.0);
        if (bonus <= 0.0) return;
        if (!ProjectileSnapshot.claim(projectile, CLAIM_RESONANT_HIT)) return;

        double capped = Math.min(bonus,
                HandlerCommonConfig.HANDLER.instance().tconstructNewDamageBonusCap);
        event.setAmount((float) (event.getAmount() * (1.0 + capped)));
        PowerDispatch.fireProc(player, power, event.getEntity());
    }

    // -- Lifecycle ------------------------------------------------------------------------------

    /** §11.5: a temporary charge does not survive a logout. The cooldown debt does, elsewhere. */
    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TConstructPowerState.clear(event.getEntity().getUUID());
    }

    /** Nor a death or a respec — {@code Clone} covers both — and nor a dimension change. */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        TConstructPowerState.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        TConstructPowerState.clear(event.getEntity().getUUID());
    }

    /** Everything, on server stop, so a second world in this JVM starts clean. */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        TConstructPowerState.clearAll();
    }

    /** Drops one player's temporary state. Called by the respec path and by tests. */
    public static void forget(Player player) {
        if (player != null) TConstructPowerState.clear(player.getUUID());
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static ItemStack repairIdentity(ItemStack stack) {
        if (!TConstructEquipmentAdapter.isNativeTool(stack)) return ItemStack.EMPTY;
        ItemStack identity = stack.copy();
        ToolStack.from(identity).setDamage(0);
        return identity;
    }

    private static boolean sameRepairedTool(ItemStack expected, ItemStack candidate) {
        return !expected.isEmpty() && ItemStack.isSameItemSameTags(expected, repairIdentity(candidate));
    }

}
