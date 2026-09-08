package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.common.actions.ActionOrigin;
import com.otectus.runicskills.common.actions.BlockBreakCommittedEvent;
import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.common.combat.DamageContext;
import com.otectus.runicskills.common.crafting.CraftResultTransformer;
import com.otectus.runicskills.common.durability.WearAvoidance;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.common.util.DurationMath;
import com.otectus.runicskills.common.util.ProcRoll;
import com.otectus.runicskills.common.workshop.WorkshopFocusService;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.integration.tconstruct.addons.TcAddonHooks;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RunicAttributeModifiers;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.client.TConstructMiningCP;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.smeltery.block.entity.controller.AlloyerBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.MelterBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.FuelModule;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.modifiers.slotless.OverslimeModifier;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * What the sixteen Tinker's Construct perks actually do, at the seams stages S2 and S3 built.
 *
 * <p><b>Nothing here invents a hook.</b> Every effect is attached to something a native seam
 * already publishes: the wear stage for the two avoidance perks, the ordinary Runic damage stage
 * for the melee ones, {@code PlayerEvent.BreakSpeed} for mining, the focus service for the workshop
 * ones, and the station bridge for the repair ones. That is what makes the perks composable with
 * the 470 that came before instead of a parallel system with its own caps.
 *
 * <p><b>Three gates, in order.</b> A perk does nothing unless (1) {@code enableTConstructPerks} is
 * on — this class is that flag's only reader, so switching it off makes the {@code tc_} perks inert
 * and disturbs nothing else; (2) the capability its seam depends on is
 * {@link TConstructCompatibilityStatus.Status#SUPPORTED}, so an unavailable seam produces a dormant
 * perk and an honest diagnostic rather than a silent no-op; and (3) the player holds the perk.
 *
 * <p><b>Caps are applied to the sum, once (§13.2).</b> Damage, mining speed, action speed and
 * incoming reduction each have one ceiling and one composition point. Two perks that both raise
 * melee damage are summed and then clamped; they never multiply, and neither of them re-reads the
 * damage the other produced.
 *
 * <p><b>Everything temporary is server memory.</b> See {@link TConstructPerkState}: charges,
 * windows and streaks clear on logout, death, dimension change and server stop, because §10.1 says
 * a personal temporary benefit does not survive any of those and does not travel with an item.
 */
public final class TConstructPerkHandler {
    private static final java.util.Map<UUID, TConstructMiningCP> LAST_MINING = new java.util.HashMap<>();

    /** How often the two attribute perks are reconciled. Twice a second is well inside a swing. */
    private static final int ATTRIBUTE_INTERVAL_TICKS = 10;

    /** Claim names, so an effect is paid once per root action however deeply it is nested. */
    private static final String CLAIM_REPAIR_MEMORY = "tconstruct:repair-memory";
    private static final String CLAIM_MELEE = "tconstruct:melee-bonus";

    /** Internal cooldown names, sharing {@code PowerRuntime}'s per-player map. */
    private static final String COOLDOWN_TEMPERED_EDGE = "tc_tempered_edge";
    private static final String COOLDOWN_ADAPTIVE_GRIP = "tc_adaptive_grip";
    private static final String COOLDOWN_RETURNING_HAND = "tc_returning_hand";
    private static final String COOLDOWN_WORKSHOP_CADENCE = "tc_workshop_cadence";

    /**
     * Installs the effects that are pulled rather than pushed.
     *
     * <p>Two wear contributions and one crafting-result adjustment, all of them extension points
     * that common code owns and that this class fills in because the questions behind them —
     * "has this tool run out of overslime", "did this recipe repair a native tool" — need a
     * {@code slimeknights} type to answer. Called once, from the bootstrap.
     */
    static void install() {
        WearAvoidance.addContributor(TConstructPerkHandler::repairMemoryAvoidance);
        WearAvoidance.addContributor(TConstructPerkHandler::slimeStewardAvoidance);
        WearAvoidance.addContributor(TConstructPerkHandler::addonWearAvoidance);
        CraftResultTransformer.addGridAdjustment(TConstructPerkHandler::fieldServiceRepairBonus);
    }

    // -- Gates ------------------------------------------------------------------------------------

    /** Whether {@code perk} may act for {@code player} right now, capability and config included. */
    static boolean active(Player player, net.minecraftforge.registries.RegistryObject<Perk> perk,
                                  Capability capability) {
        if (player == null || player instanceof FakePlayer || perk == null) return false;
        if (!HandlerCommonConfig.HANDLER.instance().enableTConstructPerks) return false;
        if (!TConstructCompatibilityStatus.current().supports(capability)) return false;
        return perk.get().isEnabled(player);
    }

    /** The tick count of the server {@code player} is on, or {@code 0} when there is none. */
    private static long now(ServerPlayer player) {
        return player.getServer() == null ? 0L : player.getServer().getTickCount();
    }

    // -- Wear avoidance: Repair Memory, Slime Steward ---------------------------------------------

    /**
     * Repair Memory — the tool you just paid to repair spends less durability for a while.
     *
     * <p>Called from inside {@link WearAvoidance#avoidance}, which is reached on a wear attempt, so
     * "consumes a charge only if a root action attempts ordinary wear" is a property of where this
     * runs. The root action id is what makes it <em>one</em> charge: a hammer swing that breaks nine
     * blocks is nine wear attempts and one root, so the first attempt spends the charge and the
     * other eight are granted the same bonus free, exactly as §4.3 asks.
     */
    private static double repairMemoryAvoidance(ServerPlayer player, ItemStack stack) {
        if (!active(player, RegistryPerks.TC_REPAIR_MEMORY, Capability.WEAR_AVOIDANCE)) return 0.0;
        TConstructPerkState.Player state = TConstructPerkState.peek(player.getUUID());
        if (state == null) return 0.0;
        if (!TConstructPerkState.isSameTool(state.repairMemoryTool, stack)) return 0.0;

        long tick = now(player);
        if (tick > state.repairMemoryExpiry) {
            clearRepairMemory(state);
            return 0.0;
        }
        if (!RunicActionContext.isOrdinaryUseBy(player.getUUID())) return 0.0;

        long root = RunicActionContext.rootActionId();
        if (root != state.repairMemoryChargedRoot) {
            if (state.repairMemoryCharges <= 0) return 0.0;
            if (!RunicActionContext.claim(CLAIM_REPAIR_MEMORY)) return 0.0;
            state.repairMemoryChargedRoot = root;
            state.repairMemoryCharges--;
        }
        return HandlerCommonConfig.HANDLER.instance().tcRepairMemoryPercent / 100.0;
    }

    /**
     * Slime Steward — a tool whose overslime is spent wears more slowly until it is refilled.
     *
     * <p>Reads the native shield amount and never writes it: §10.2 forbids creating, refilling or
     * intercepting overslime, so this asks whether the tool <em>supports</em> overslime (it carries
     * the modifier) and whether it has none left, and contributes only then. A tool that never had
     * overslime is not eligible, which is what stops it from being a blanket wear perk with a
     * slime-themed tooltip.
     */
    private static double slimeStewardAvoidance(ServerPlayer player, ItemStack stack) {
        if (!active(player, RegistryPerks.TC_SLIME_STEWARD, Capability.WEAR_AVOIDANCE)) return 0.0;
        if (!TConstructEquipmentAdapter.isNativeTool(stack)) return 0.0;

        ToolStack tool = ToolStack.from(stack);
        if (tool.isUnbreakable() || tool.isBroken()) return 0.0;
        OverslimeModifier overslime = TinkerModifiers.overslime.get();
        if (tool.getModifierLevel(overslime) <= 0) return 0.0;
        if (overslime.getShield(tool) > 0) return 0.0;
        return HandlerCommonConfig.HANDLER.instance().tcSlimeStewardPercent / 100.0;
    }

    /**
     * The one composition point for the add-on durability-avoidance channel (§13.2).
     *
     * <p>The sibling of {@link #onMeleeDamage}'s melee sum, one channel over. Every add-on
     * contribution is summed here and clamped once by {@code tconstructNewWearAvoidanceCap}, and
     * the clamped figure is then a single term in the one avoidance sum {@code WearAvoidance}
     * composes for every wear perk in the mod — where it meets that class's own {@code MAX_AVOIDANCE}
     * ceiling. Two clamps, because they bound two different things: this one bounds what the new
     * add-on content may contribute, and that one bounds what any item may avoid in total.
     *
     * <p>An add-on effect must not be folded into the melee sum because a channel was missing. This
     * is the channel.
     */
    private static double addonWearAvoidance(ServerPlayer player, ItemStack stack) {
        double bonus = TcAddonHooks.wearAvoidanceBonus(player, stack);
        if (!Double.isFinite(bonus) || bonus <= 0.0) return 0.0;
        return Math.min(bonus, HandlerCommonConfig.HANDLER.instance().tconstructNewWearAvoidanceCap);
    }

    // -- Death resolution: the bracket the add-on death perks are observed inside ------------------

    /**
     * Opens the death-resolution bracket.
     *
     * <p>{@code HIGHEST} so it is open before any handler that might refuse the death runs. Tinkers'
     * Jewelry's {@code DamageItemEvents.undying} is registered at default priority and spends the
     * ring's durability from inside its own handler; without a bracket that wear is
     * indistinguishable at the wear seam from a block break with the same ring on.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDeathResolutionBegin(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        TcAddonHooks.beginDeathResolution(player);
    }

    /**
     * Closes the bracket and reports how the death ended.
     *
     * <p>{@code LOWEST} with {@code receiveCanceled} so it runs whether or not something refused the
     * death — a bracket that only closed on an uncancelled death would leak the open state for
     * exactly the case it exists to cover.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onDeathResolutionEnd(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        TcAddonHooks.endDeathResolution(player, event.isCanceled());
    }

    /**
     * The one composition point for the add-on experience-recovery channel (§13.2).
     *
     * <p><b>Only when the pickup was actually consumed.</b> {@code receiveCanceled} is what lets
     * this see a cancelled pickup at all, and {@code isCanceled} is what keeps it from paying on one
     * the player collected normally — paying then would be experience out of nothing, on top of the
     * orb they already got. Because the share multiplies the orb another mod destroyed, the return
     * is bounded above by what the player would have had without that mod, so this is a partial
     * recovery and never a gain.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onAddonExperiencePickup(PlayerXpEvent.PickupXp event) {
        if (!event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (event.getOrb() == null || !event.getOrb().isRemoved()) return;
        int consumed = event.getOrb().getValue();
        if (consumed <= 0) return;

        double share = TcAddonHooks.experienceBonus(player);
        if (!Double.isFinite(share) || share <= 0.0) return;
        share = Math.min(share, HandlerCommonConfig.HANDLER.instance().tconstructNewExperienceBonusCap);
        int returned = (int) Math.floor(consumed * share);
        if (returned <= 0) return;
        player.giveExperiencePoints(returned);
    }

    /**
     * Offers one delivered station take to the add-on adapters.
     *
     * <p>Called after an accepted station take has resolved the actual cursor/inventory stack.
     * Merely requesting a result preview cannot arm an add-on benefit.
     */
    static void onStationDelivered(ServerPlayer player, ItemStack delivered) {
        if (player == null || player instanceof FakePlayer) return;
        TcAddonHooks.stationTakeDelivered(player, delivered);
    }

    /** Drops a spent or lapsed Repair Memory charge set. */
    private static void clearRepairMemory(TConstructPerkState.Player state) {
        state.repairMemoryCharges = 0;
        state.repairMemoryExpiry = 0L;
        state.repairMemoryTool = TConstructPerkState.reference(ItemStack.EMPTY);
        state.repairMemoryChargedRoot = 0L;
    }

    // -- Station repair: Repair Memory trigger, Material Harmony ----------------------------------

    /**
     * Everything the repair perks do to one native station repair, at the moment it is delivered.
     *
     * <p>Called from {@link TConstructStationBridge#transformDelivered}, which runs once per copy
     * the player is actually handed — the only point that is reached identically by a click and a
     * shift-click. The native amount is read as the difference between the tool that went in and
     * the tool coming out, which is §10.1's "paid repair" definition made arithmetic: consumed
     * material, positive native restoration, and nothing this mod added counted towards it.
     *
     * @param delivered the copy about to be handed over, mutated in place
     */
    static void applyStationRepairBonus(ServerPlayer player, ItemStack delivered, TinkerStationBlockEntity station) {
        if (player == null || player instanceof FakePlayer) return;
        if (!TConstructEquipmentAdapter.isNativeTool(delivered)) return;
        if (!TConstructCompatibilityStatus.current().supports(Capability.REPAIR)) return;

        ItemStack before = station.getItem(TinkerStationBlockEntity.TINKER_SLOT);
        if (!TConstructEquipmentAdapter.isNativeTool(before)) return;
        ToolStack input = ToolStack.from(before);
        ToolStack output = ToolStack.from(delivered);
        int restored = input.getDamage() - output.getDamage();
        if (restored <= 0) return;

        applyRepairBonus(player, delivered, output, restored, station);
    }

    /** Only an accepted take can arm benefits or spend a prepared repair Power's cooldown. */
    static void onStationRepair(ServerPlayer player, ItemStack delivered, TinkerStationBlockEntity station,
                                int restored) {
        if (player == null || player instanceof FakePlayer || restored <= 0
                || !TConstructEquipmentAdapter.isNativeTool(delivered)) return;
        ToolStack output = ToolStack.from(delivered);
        ItemStack base = station.getCraftingResult().getResult();
        if (TConstructEquipmentAdapter.isNativeTool(base)
                && ToolStack.from(base).getDamage() > output.getDamage()) {
            TConstructPowerDispatcher.repairBonusShare(player, delivered, station);
        }
        armRepairMemory(player, delivered, output, restored);
        // The Powers that turn on a paid repair are offered the native amount, after the bonus has
        // been paid and before anything else can change the tool: Quench, Temper Reserve and the
        // repair leg of The Great Work all key on what the station actually restored.
        TConstructPowerDispatcher.onStationRepair(player, delivered, output, restored);
    }

    /**
     * Material Harmony — a tool made of several materials takes a repair better.
     *
     * <p>Distinct non-cosmetic material ids, counted once each: §10.2 is explicit that repeated
     * parts must not inflate the count, and a {@link Set} of the variant ids is that rule rather
     * than a comment about it. The bonus joins the paid-repair channel and is bounded by
     * {@code tconstructRepairBonusCap}, so it composes with Repair Expert instead of stacking past
     * it.
     */
    private static void applyRepairBonus(ServerPlayer player, ItemStack delivered, ToolStack output,
                                         int restored, TinkerStationBlockEntity station) {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double share = 0.0;
        if (active(player, RegistryPerks.TC_MATERIAL_HARMONY, Capability.STATION_TRANSACTIONS)
                && distinctMaterials(output) >= 3) {
            share += config.tcMaterialHarmonyPercent / 100.0;
        }
        // Working Memory and Many Hands pay into the same channel, so the cap is applied to the sum
        // once (§13.2) rather than to each contribution on its own.
        share += TConstructPowerDispatcher.previewRepairBonusShare(player, delivered, station);
        // And the add-on perks: Polished Facet is a repair contribution like any other, so it is
        // summed here and bounded by the same cap rather than applied on its own.
        share += TcAddonHooks.repairBonusShare(player, delivered);
        if (share <= 0.0) return;
        share = Math.min(share, config.tconstructRepairBonusCap);
        int extra = TConstructRepairMath.paidBonus(restored, output.getDamage(), share);
        // Bounded by the damage that is actually left: §13.2 limits a paid-repair bonus by the
        // remaining native damage, and a repair past zero is not a bonus, it is a different item.
        extra = Math.min(extra, output.getDamage());
        if (extra <= 0) return;
        TConstructRepairBridge.paidRepairBonus(delivered, extra);
    }

    /** How many distinct materials a tool is built from, counting each variant id once. */
    private static int distinctMaterials(ToolStack tool) {
        MaterialNBT materials = tool.getMaterials();
        Set<String> distinct = new HashSet<>();
        for (MaterialVariant variant : materials) {
            // Empty and unknown are parts that have not been given a material, or one this install
            // no longer has; neither is a material the smith chose. The <em>material</em> id is what
            // is counted, not the variant, so two cosmetic finishes of one metal are one material.
            if (variant == null || variant.isEmpty() || variant.isUnknown()) continue;
            MaterialVariantId id = variant.getVariant();
            if (id == null || id.getId() == null) continue;
            distinct.add(id.getId().toString());
        }
        return distinct.size();
    }

    /**
     * Repair Memory — the charges a qualifying repair grants.
     *
     * <p>The size gate is §10.1's: at least {@code max(10, ceil(maxDurability * 0.05))} restored, or
     * the tool's whole pool when that is smaller. It is what stops a one-point top-up from renewing
     * the benefit indefinitely. A new repair <em>refreshes</em> the count rather than adding to it,
     * so repeated repairs cannot bank charges.
     */
    public static void armRepairMemory(ServerPlayer player, ItemStack delivered, int restored) {
        if (!TConstructEquipmentAdapter.isNativeTool(delivered)) return;
        armRepairMemory(player, delivered, ToolStack.from(delivered), restored);
    }

    private static void armRepairMemory(ServerPlayer player, ItemStack delivered, ToolStack output,
                                        int restored) {
        if (!active(player, RegistryPerks.TC_REPAIR_MEMORY, Capability.STATION_TRANSACTIONS)) return;
        if (delivered.getMaxStackSize() > 1) return;

        int max = output.getStats().getInt(slimeknights.tconstruct.library.tools.stat.ToolStats.DURABILITY);
        int threshold = Math.min(Math.max(10, (int) Math.ceil(max * 0.05)), Math.max(1, max));
        if (restored < threshold) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        TConstructPerkState.Player state = TConstructPerkState.of(player.getUUID());
        state.repairMemoryCharges = config.tcRepairMemoryCharges;
        state.repairMemoryExpiry = now(player) + DurationMath.secondsToTicks(config.tcRepairMemorySeconds);
        state.repairMemoryTool = TConstructPerkState.reference(delivered);
        state.repairMemoryChargedRoot = 0L;
    }

    // -- Melee: Tempered Edge, Adaptive Grip ------------------------------------------------------

    /**
     * The one composition point for the new outgoing-damage channel (§13.2).
     *
     * <p>{@code NORMAL} priority, alongside the existing Strength handlers, so vanilla armour and
     * crit have already run and this layers on the accepted hit. Three exclusions are enforced by
     * asking rather than by guessing: {@link DamageContext#allowsStandardOutgoingModifiers()} rules
     * out a hit this mod itself emitted, the open {@link ActionOrigin#MELEE} scope rules out a
     * modifier secondary, a reflected hit and an automated attack — none of which come through
     * {@code Player#attack} — and the per-action claim rules out a native sweep child being paid a
     * second time for the same swing.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onMeleeDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (player instanceof FakePlayer || player.isCreative()) return;
        LivingEntity target = event.getEntity();
        if (target == null || target == player) return;
        if (!DamageContext.allowsStandardOutgoingModifiers()) return;

        RunicActionContext.Frame frame = RunicActionContext.current();
        if (frame.origin() != ActionOrigin.MELEE || !player.getUUID().equals(frame.actor())) return;

        ItemStack weapon = player.getMainHandItem();
        if (!usable(weapon) || !weapon.is(TinkerTags.Items.MELEE)) return;
        if (!RunicActionContext.claim(CLAIM_MELEE)) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        long tick = now(player);
        double bonus = 0.0;

        if (active(player, RegistryPerks.TC_TEMPERED_EDGE, Capability.CLASSIFICATION)
                && RunicActionContext.attackStrength(player.getUUID(), player.getAttackStrengthScale(0.5F)) >= 0.9F
                && PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), COOLDOWN_TEMPERED_EDGE,
                        tick, config.tcTemperedEdgeCooldownTicks)) {
            bonus += config.tcTemperedEdgePercent / 100.0;
        }

        // Recorded before it is spent, so a swing that <em>is</em> the role switch pays on itself.
        // The mining side cannot do that — the speed is asked for before the block is committed —
        // so there it lands on the next action, which is the same "next committed root action" the
        // window exists to cover.
        recordAdaptiveGripAction(player, weapon, TConstructPerkState.Role.MELEE);
        if (consumeAdaptiveGrip(player, weapon, TConstructPerkState.Role.MELEE)) {
            bonus += config.tcAdaptiveGripPercent / 100.0;
        }

        // The Artifice Powers share this channel rather than opening a second one: §13.2's worked
        // example sums First Heat, Hammer and Tongs and Tempered Edge and caps the total once.
        bonus += TConstructPowerDispatcher.meleeDamageBonus(player, tick);
        // And the add-on perks share it too: Banquet of Cinders is a melee contribution like any
        // other, so it is summed here and capped once rather than applied on its own (§13.2).
        bonus += TcAddonHooks.meleeDamageBonus(player);

        if (bonus <= 0.0) return;
        double capped = Math.min(bonus, config.tconstructNewDamageBonusCap);
        event.setAmount((float) (event.getAmount() * (1.0 + capped)));
    }

    // -- Mining: Precision Footing, Adaptive Grip -------------------------------------------------

    /**
     * The one composition point for the new mining-speed channel (§13.2).
     *
     * <p>{@code LOW} priority so the existing {@code HIGHEST} handler — locks, the break-speed
     * attribute, Obsidian Smasher — has already produced the speed this multiplies. Multiplying the
     * final figure once is what preserves native penalties and block effectiveness: water, no
     * ground, the wrong tool and a broken tool are all already in the number.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player instanceof FakePlayer) return;
        ItemStack tool = player.getMainHandItem();
        if (!usable(tool) || !tool.is(TinkerTags.Items.HARVEST)) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double bonus = 0.0;

        // Grounded, walking, and actually using the tool for what it is for. Sprinting and jumping
        // are excluded by the perk's own text, not as a proxy for anything else.
        if (active(player, RegistryPerks.TC_PRECISION_FOOTING, Capability.CLASSIFICATION)
                && player.onGround() && !player.isSprinting()
                && tool.isCorrectToolForDrops(event.getState())) {
            bonus += config.tcPrecisionFootingPercent / 100.0;
        }

        bonus += player instanceof ServerPlayer serverPlayer
                ? temporaryMiningBonus(serverPlayer)
                : TConstructMiningState.bonus(player.getInventory().selected);

        if (bonus <= 0.0) return;
        double capped = Math.min(bonus, config.tconstructNewMiningBonusCap);
        event.setNewSpeed((float) (event.getNewSpeed() * (1.0 + capped)));
    }

    private static float temporaryMiningBonus(ServerPlayer player) {
        ItemStack tool = player.getMainHandItem();
        if (!usable(tool) || !tool.is(TinkerTags.Items.HARVEST)) return 0.0F;
        double bonus = TConstructPowerDispatcher.miningSpeedBonus(player);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        if (adaptiveGripReady(player, tool)) bonus += config.tcAdaptiveGripPercent / 100.0;
        return (float) Math.max(0.0, Math.min(config.tconstructNewMiningBonusCap, bonus));
    }

    private static void syncMining(ServerPlayer player) {
        if (player.connection == null) return;
        TConstructMiningCP next = new TConstructMiningCP(temporaryMiningBonus(player), player.getInventory().selected);
        TConstructMiningCP previous = LAST_MINING.put(player.getUUID(), next);
        if (!next.equals(previous)) ServerNetworking.sendToPlayer(next, player);
    }

    /** Records a committed mined block as Adaptive Grip's previous action. */
    @SubscribeEvent
    public void onBlockBreakCommitted(BlockBreakCommittedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null || player instanceof FakePlayer) return;
        if (RunicActionContext.current().actor() != null
                && !RunicActionContext.claim("tconstruct:adaptive-mining")) return;
        // BreakSpeed is queried repeatedly while one block is being mined. Spend only once the
        // block actually broke, before recording a new role switch for the following action.
        consumeAdaptiveGrip(player, player.getMainHandItem(), TConstructPerkState.Role.MINING);
        recordAdaptiveGripAction(player, player.getMainHandItem(), TConstructPerkState.Role.MINING);
    }

    // -- Adaptive Grip ----------------------------------------------------------------------------

    /**
     * Notes what the player just did with which tool, so a later switch can be recognised.
     *
     * <p>Recording the <em>stack instance</em> rather than the item is what enforces "the same valid
     * hybrid native tool": swapping to a second, identical hammer is a different tool and does not
     * continue the sequence, and neither does taking the same one out of a chest, because the stack
     * that comes back is a copy.
     */
    private static void recordAdaptiveGripAction(ServerPlayer player, ItemStack tool,
                                                 TConstructPerkState.Role role) {
        if (!active(player, RegistryPerks.TC_ADAPTIVE_GRIP, Capability.CLASSIFICATION)) return;
        if (!isHybridTool(tool)) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        TConstructPerkState.Player state = TConstructPerkState.of(player.getUUID());
        long tick = now(player);

        // A switch arms the bonus; a repeat of the same role only moves the clock forward. This is
        // where "no switching items in place to generate a charge" lives: the previous action has
        // to have been a real committed action with this same stack.
        boolean switched = state.adaptiveGripLastRole != null
                && state.adaptiveGripLastRole != role
                && TConstructPerkState.isSameTool(state.adaptiveGripTool, tool)
                && tick - state.adaptiveGripLastTick <= DurationMath.secondsToTicks(config.tcAdaptiveGripSwitchSeconds);

        if (switched && PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(),
                COOLDOWN_ADAPTIVE_GRIP, tick, config.tcAdaptiveGripCooldownTicks)) {
            state.adaptiveGripArmedUntil = tick + DurationMath.secondsToTicks(config.tcAdaptiveGripWindowSeconds);
        }

        state.adaptiveGripLastRole = role;
        state.adaptiveGripLastTick = tick;
        state.adaptiveGripTool = TConstructPerkState.reference(tool);
    }

    /** Spends an armed Adaptive Grip bonus on this action, if one is armed for this tool. */
    private static boolean consumeAdaptiveGrip(ServerPlayer player, ItemStack tool,
                                               TConstructPerkState.Role role) {
        if (!adaptiveGripReady(player, tool)) return false;
        TConstructPerkState.peek(player.getUUID()).adaptiveGripArmedUntil = 0L;
        return role != null;
    }

    /** A speed query observes preparation without consuming a committed-action benefit. */
    private static boolean adaptiveGripReady(ServerPlayer player, ItemStack tool) {
        if (!active(player, RegistryPerks.TC_ADAPTIVE_GRIP, Capability.CLASSIFICATION)) return false;
        if (!isHybridTool(tool)) return false;
        TConstructPerkState.Player state = TConstructPerkState.peek(player.getUUID());
        if (state == null || state.adaptiveGripArmedUntil <= 0L) return false;
        if (!TConstructPerkState.isSameTool(state.adaptiveGripTool, tool)) return false;
        if (now(player) > state.adaptiveGripArmedUntil) {
            state.adaptiveGripArmedUntil = 0L;
            return false;
        }
        return true;
    }

    /** Whether a tool can do both jobs the perk alternates between. */
    private static boolean isHybridTool(ItemStack stack) {
        return usable(stack)
                && stack.is(TinkerTags.Items.HARVEST) && stack.is(TinkerTags.Items.MELEE);
    }

    private static boolean usable(ItemStack stack) {
        return TConstructEquipmentAdapter.isNativeTool(stack) && !ToolStack.from(stack).isBroken();
    }

    // -- Incoming damage: Ember Guard -------------------------------------------------------------

    /**
     * The one composition point for the new incoming-reduction channel (§13.2).
     *
     * <p>Ember Guard is the perk half: standing at your own lit forge takes some of the fire out of
     * a burn. Three conditions, all of them real state rather than proximity — the damage is
     * fire-tagged, the player holds a focus, and the workshop that focus names is actually burning
     * fuel. No armour is edited and no immunity is granted.
     *
     * <p>Quench and Workshop Aegis reduce the same channel, so they are summed here rather than
     * applied separately: two handlers each multiplying the amount by {@code 1 - r} would compose
     * to a deeper reduction than the cap the two of them are supposed to share. The handler
     * therefore runs for every accepted hit and each term decides for itself whether this
     * particular damage is one it applies to.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onIncomingDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double reduction = 0.0;

        if (event.getSource().is(DamageTypeTags.IS_FIRE)
                && active(player, RegistryPerks.TC_EMBER_GUARD, Capability.WORKSHOP)) {
            WorkshopFocusService.Focus focus = WorkshopFocusService.activeFocus(player);
            if (focus != null && isHeated(player.level(), focus.controller())) {
                reduction += config.tcEmberGuardPercent / 100.0;
            }
        }

        reduction += TConstructPowerDispatcher.incomingReduction(player, event.getSource());

        if (reduction <= 0.0) return;
        double capped = Math.min(reduction, config.tconstructNewDamageReductionCap);
        event.setAmount((float) (event.getAmount() * (1.0 - capped)));
    }

    /**
     * Whether the controller at {@code pos} is burning fuel right now.
     *
     * <p>Asked of the three controller types the focus service already recognises, through their
     * own fuel module. A structure that is cold, out of fuel or not assembled answers no, which is
     * what makes Ember Guard a property of a working forge rather than of a block being nearby.
     */
    public static boolean isHeated(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) return false;
        BlockEntity entity = level.getBlockEntity(pos);
        FuelModule fuel = null;
        if (entity instanceof HeatingStructureBlockEntity structure) fuel = structure.getFuelModule();
        else if (entity instanceof MelterBlockEntity melter) fuel = melter.getFuelModule();
        else if (entity instanceof AlloyerBlockEntity alloyer) fuel = alloyer.getFuelModule();
        return fuel != null && fuel.hasFuel() && fuel.getTemperature() > 0;
    }

    // -- Crafting-table repair kit: Field Service -------------------------------------------------

    /**
     * Field Service — a repair kit used in the field goes further than the kit alone would.
     *
     * <p>Attached to the crafting grid rather than to a right-click, because Tinkers' portable
     * repair is a real crafting recipe: {@code CraftingTableRepairKitRecipe} extends vanilla's
     * {@code CustomRecipe} and runs through the ordinary 3x3 menu. That is also why the recipe class
     * has to be checked by identity — at the event level a repair-kit craft is indistinguishable
     * from manufacturing a new item, and paying a manufacturing bonus on a repair is exactly the
     * misclassification §6 warns about.
     *
     * <p>The station is deliberately not covered here: §10.2 assigns station repairs to Repair
     * Expert so that one kit path never earns two bonuses.
     */
    private static void fieldServiceRepairBonus(ServerPlayer player, CraftingContainer inputs,
                                                Recipe<?> recipe, ItemStack result) {
        if (!(recipe instanceof slimeknights.tconstruct.tables.recipe.CraftingTableRepairKitRecipe)) return;
        if (!active(player, RegistryPerks.TC_FIELD_SERVICE, Capability.REPAIR)) return;
        if (!TConstructEquipmentAdapter.isNativeTool(result)) return;

        ItemStack before = ItemStack.EMPTY;
        for (int slot = 0; slot < inputs.getContainerSize(); slot++) {
            ItemStack candidate = inputs.getItem(slot);
            if (TConstructEquipmentAdapter.isNativeTool(candidate)) {
                before = candidate;
                break;
            }
        }
        if (before.isEmpty()) return;

        ToolStack input = ToolStack.from(before);
        ToolStack output = ToolStack.from(result);
        int restored = input.getDamage() - output.getDamage();
        if (restored <= 0) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double share = Math.min(config.tcFieldServicePercent / 100.0, config.tconstructRepairBonusCap);
        int extra = TConstructRepairMath.paidBonus(restored, output.getDamage(), share);
        if (extra <= 0) return;
        TConstructRepairBridge.paidRepairBonus(result, extra);
    }

    // -- Workshop: Thermal Rhythm, Workshop Cadence, Cast Keeper ----------------------------------

    /** Thermal Rhythm's share of the melting figure, before the workshop cap is applied. */
    public static double thermalRhythmBonus(ServerPlayer player) {
        if (!active(player, RegistryPerks.TC_THERMAL_RHYTHM, Capability.WORKSHOP)) return 0.0;
        return HandlerCommonConfig.HANDLER.instance().tcThermalRhythmPercent / 100.0;
    }

    /** Workshop Cadence's share of the cooling figure while its window is open. */
    public static double workshopCadenceBonus(ServerPlayer player) {
        if (!active(player, RegistryPerks.TC_WORKSHOP_CADENCE, Capability.WORKSHOP)) return 0.0;
        TConstructPerkState.Player state = TConstructPerkState.peek(player.getUUID());
        if (state == null || state.cadenceBonusUntil <= 0L) return 0.0;
        if (now(player) > state.cadenceBonusUntil) {
            state.cadenceBonusUntil = 0L;
            return 0.0;
        }
        return HandlerCommonConfig.HANDLER.instance().tcWorkshopCadencePercent / 100.0;
    }

    /**
     * One completed cast at a focused workshop, offered to the two perks that care.
     *
     * <p>Called from the casting seam with the recipe that finished and the cast it consumed, and
     * only for a cast whose block is covered by a live focus — so "manually completes" is the focus
     * the player claimed, and an unattended machine that nobody focused reaches neither perk.
     *
     * @param consumedCast the cast the recipe used up, or empty when it kept it
     * @return the cast to put back, or {@link ItemStack#EMPTY} when nothing is returned
     */
    public static ItemStack onCastingCompleted(Level level, BlockPos pos, String recipeId, ItemStack consumedCast) {
        WorkshopFocusService.Focus focus = WorkshopFocusService.focusAt(level, pos);
        if (focus == null || level.getServer() == null) return ItemStack.EMPTY;
        // The holder must still be online: a focus outlives a disconnect by up to one revalidation
        // pass, and paying a player who is not there is not attribution.
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(focus.player());
        if (player == null) return ItemStack.EMPTY;

        advanceCadence(player, recipeId);
        return castKeeperReturn(player, consumedCast);
    }

    /**
     * Workshop Cadence — three casts of one recipe in a row buy a spell of faster cooling.
     *
     * <p>The streak is per recipe and resets on a different one, on the window elapsing, and on the
     * trigger itself, so the bonus cannot be held permanently by a player who simply keeps casting.
     */
    private static void advanceCadence(ServerPlayer player, String recipeId) {
        if (!active(player, RegistryPerks.TC_WORKSHOP_CADENCE, Capability.WORKSHOP)) return;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        TConstructPerkState.Player state = TConstructPerkState.of(player.getUUID());
        long tick = now(player);
        long window = DurationMath.secondsToTicks(config.tcWorkshopCadenceWindowSeconds);

        if (!java.util.Objects.equals(state.cadenceRecipe, recipeId)
                || tick - state.cadenceStreakStart > window) {
            state.cadenceRecipe = recipeId;
            state.cadenceStreakStart = tick;
            state.cadenceCasts = 0;
        }
        state.cadenceCasts++;
        if (state.cadenceCasts < config.tcWorkshopCadenceCasts) return;

        state.cadenceCasts = 0;
        state.cadenceStreakStart = tick;
        if (!PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), COOLDOWN_WORKSHOP_CADENCE,
                tick, config.tcWorkshopCadenceCooldownTicks)) {
            return;
        }
        state.cadenceBonusUntil = tick + DurationMath.secondsToTicks(config.tcWorkshopCadenceSeconds);
    }

    /** Cast Keeper — the roll for returning one consumed disposable cast. */
    private static ItemStack castKeeperReturn(ServerPlayer player, ItemStack consumedCast) {
        if (consumedCast == null || consumedCast.isEmpty()) return ItemStack.EMPTY;
        if (!active(player, RegistryPerks.TC_CAST_KEEPER, Capability.WORKSHOP)) return ItemStack.EMPTY;
        // Single-use casts only. A gold cast is reusable and is never consumed, so it can never
        // reach here; the tag test keeps a pack's own consumed item out unless it declared it one.
        if (!consumedCast.is(TinkerTags.Items.CASTS)) return ItemStack.EMPTY;
        if (!ProcRoll.rollsPercent(HandlerCommonConfig.HANDLER.instance().tcCastKeeperPercent)) {
            return ItemStack.EMPTY;
        }
        ItemStack returned = consumedCast.copy();
        returned.setCount(1);
        return returned;
    }

    // -- Ranged: Measured Draw, Returning Hand ----------------------------------------------------

    /**
     * Measured Draw — the multiplier a fully charged native launch's inaccuracy is scaled by.
     *
     * <p>Never below zero and never above one: a shot that was already perfectly accurate has an
     * inaccuracy of zero and stays there, which is §10.2's "does not improve a zero-inaccuracy
     * shot" expressed as multiplication rather than as a special case.
     */
    public static float measuredDrawFactor(LivingEntity shooter) {
        if (!(shooter instanceof ServerPlayer player)) return 1.0F;
        if (!active(player, RegistryPerks.TC_MEASURED_DRAW, Capability.PROJECTILES)) return 1.0F;
        int percent = HandlerCommonConfig.HANDLER.instance().tcMeasuredDrawPercent;
        return (float) (1.0 - Math.max(0.0, Math.min(1.0, percent / 100.0)));
    }

    /** Records a genuine native return, arming Returning Hand's next launch. */
    public static void onThrownToolReturned(ServerPlayer player) {
        if (!active(player, RegistryPerks.TC_RETURNING_HAND, Capability.PROJECTILES)) return;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        long tick = now(player);
        if (!PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), COOLDOWN_RETURNING_HAND,
                tick, config.tcReturningHandCooldownTicks)) {
            return;
        }
        TConstructPerkState.of(player.getUUID()).returningHandUntil =
                tick + DurationMath.secondsToTicks(config.tcReturningHandSeconds);
    }

    /**
     * Returning Hand — the charge multiplier for one prepared thrown-tool launch.
     *
     * <p>Draw speed expressed where the draw is actually read: the native launch converts held time
     * into a charge, so a tool drawn 10% faster is one whose charge is 10% further along. Clamped
     * to one, because a charge above full is not a faster draw, it is a different weapon. Consumed
     * only when a legal launch begins — a launch that never reaches this seam leaves the window
     * intact without extending it.
     */
    public static float returningHandCharge(LivingEntity thrower, float charge) {
        if (!(thrower instanceof ServerPlayer player)) return charge;
        if (!active(player, RegistryPerks.TC_RETURNING_HAND, Capability.PROJECTILES)) return charge;
        TConstructPerkState.Player state = TConstructPerkState.peek(player.getUUID());
        if (state == null || state.returningHandUntil <= 0L) return charge;
        if (now(player) > state.returningHandUntil) {
            state.returningHandUntil = 0L;
            return charge;
        }
        state.returningHandUntil = 0L;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double share = Math.min(config.tcReturningHandPercent / 100.0,
                config.tconstructNewActionSpeedBonusCap);
        return (float) Math.min(1.0, charge * (1.0 + share));
    }

    // -- Attributes: Counterweight, Plate Discipline ----------------------------------------------

    /**
     * Reconciles the two attribute perks against what the player is currently wearing and holding.
     *
     * <p>Polled rather than event-driven because both conditions are continuous — a weapon in hand,
     * three pieces of armour worn — and there is no single Forge event that covers every way either
     * can change (a hotbar scroll, a broken piece, a curio swap, a modifier rebuild). Removal is
     * unconditional and happens first, so an ineligible player never keeps a modifier for a tick
     * after the condition lapses.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (!HandlerCommonConfig.HANDLER.instance().enableTConstructPerks) {
            TConstructPerkState.clear(player.getUUID());
            TcAddonHooks.forget(player.getUUID());
        }
        syncMining(player);
        if (player.tickCount % ATTRIBUTE_INTERVAL_TICKS != 0) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        applyModifier(player, Attributes.ATTACK_SPEED, RunicAttributeModifiers.TC_COUNTERWEIGHT,
                "runicskills:tc_counterweight", AttributeModifier.Operation.MULTIPLY_BASE,
                counterweightAmount(player, config));
        applyModifier(player, Attributes.KNOCKBACK_RESISTANCE, RunicAttributeModifiers.TC_PLATE_DISCIPLINE,
                "runicskills:tc_plate_discipline", AttributeModifier.Operation.ADDITION,
                plateDisciplineAmount(player, config));
    }

    /**
     * Counterweight's share of the action-speed channel, or zero.
     *
     * <p>"Broad" is the native definition's own word for it — {@code tconstruct:broad_tools} is the
     * tag Tinkers' puts on hammers, excavators, cleavers and scythes — rather than a guess from how
     * many parts a tool has. And it is read from the hand that would swing, so a hammer in the
     * inventory changes nothing.
     */
    private static double counterweightAmount(ServerPlayer player, HandlerCommonConfig config) {
        if (!active(player, RegistryPerks.TC_COUNTERWEIGHT, Capability.CLASSIFICATION)) return 0.0;
        ItemStack weapon = player.getMainHandItem();
        if (!usable(weapon) || !weapon.is(TinkerTags.Items.MELEE)
                || !weapon.is(TinkerTags.Items.BROAD_TOOLS)) return 0.0;
        return Math.min(config.tcCounterweightPercent / 100.0, config.tconstructNewActionSpeedBonusCap);
    }

    /**
     * Plate Discipline's knockback resistance, or zero.
     *
     * <p>Counted over the four armour slots only, so a spare chestplate in a backpack contributes
     * nothing (C07), and granted once as a single modifier rather than once per piece.
     */
    private static double plateDisciplineAmount(ServerPlayer player, HandlerCommonConfig config) {
        if (!active(player, RegistryPerks.TC_PLATE_DISCIPLINE, Capability.CLASSIFICATION)) return 0.0;
        int worn = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            ItemStack piece = player.getItemBySlot(slot);
            if (usable(piece) && piece.is(TinkerTags.Items.ARMOR)) worn++;
        }
        return worn >= 3 ? config.tcPlateDisciplineAmount : 0.0;
    }

    /**
     * Sets one transient modifier to {@code amount}, removing it entirely at zero.
     *
     * <p>Transient, never permanent: every id here is in {@link RunicAttributeModifiers}'s player
     * table precisely so it is re-derived rather than saved, which is the rule that stopped
     * integration bonuses outliving the integration that granted them.
     */
    private static void applyModifier(ServerPlayer player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                      UUID id, String name, AttributeModifier.Operation operation,
                                      double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier existing = instance.getModifier(id);
        if (amount <= 0.0) {
            if (existing != null) instance.removeModifier(id);
            return;
        }
        if (existing != null) {
            if (Math.abs(existing.getAmount() - amount) < 1.0E-6) return;
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
    }

    // -- Lifecycle --------------------------------------------------------------------------------

    /** §10.1: a temporary benefit does not survive a logout. */
    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_MINING.remove(event.getEntity().getUUID());
        TConstructPerkState.clear(event.getEntity().getUUID());
        TcAddonHooks.forget(event.getEntity().getUUID());
    }

    /** Nor a death or a dimension change — {@code Clone} covers both, and a respec goes through it. */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        LAST_MINING.remove(event.getEntity().getUUID());
        TConstructPerkState.clear(event.getEntity().getUUID());
        TcAddonHooks.forget(event.getEntity().getUUID());
    }

    /** Nor a dimension change, which does not clone the player. */
    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        LAST_MINING.remove(event.getEntity().getUUID());
        TConstructPerkState.clear(event.getEntity().getUUID());
        TcAddonHooks.forget(event.getEntity().getUUID());
    }

    /** Everything, on server stop, so a second world in this JVM starts clean. */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        LAST_MINING.clear();
        TConstructPerkState.clearAll();
        TcAddonHooks.forgetAll();
    }

    /** Drops one player's memory. Called by the respec path and by tests. */
    public static void forget(Player player) {
        if (player != null) {
            TConstructPerkState.clear(player.getUUID());
            TcAddonHooks.forget(player.getUUID());
        }
    }
}
