package com.otectus.runicskills.integration.tconstruct.addons;

import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.common.durability.WearAvoidance;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.common.util.DurationMath;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.TcAddonPresence;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RunicAttributeModifiers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import java.util.UUID;

/**
 * TCIntegrations: four perks that observe what that add-on already does, and add nothing to it.
 *
 * <p>§12.2 divides the work explicitly. TCIntegrations owns its materials, modifiers, mana charging
 * and offhand attacks; Runic adds character-skill interaction <em>around</em> real operations. So
 * every effect here hangs off something the add-on has already decided to do: a mana request it is
 * about to make, an armour repair it has already paid for, an attack it has already landed.
 *
 * <p><b>Four capabilities, not one.</b> TCIntegrations' Botania, Ars, Create and Malum links are
 * optional dependencies of its own, so the same jar supplies a mana charge on one install and
 * nothing on the next. Each perk is gated on its own capability, which
 * {@link TcAddonRegistry#describe} keys on the companion mod id — installed is not capable (§12.1).
 *
 * <p><b>The Ars listener lives elsewhere.</b> A Forge event subscriber's parameter types are
 * resolved when the bus scans it, so a method taking an Ars event would fail to register on an
 * install without Ars. {@link TcIntegrationsArsListener} carries that one method and is registered
 * only after the mod id is confirmed.
 */
public final class TcIntegrationsAdapter {

    /** How often the transient knockback modifier is reconciled. Twice a second. */
    private static final int ATTRIBUTE_INTERVAL_TICKS = 10;

    /** Claim name, so one root tool use spends one Clockwork Alternation window. */
    private static final String CLAIM_CLOCKWORK = "tcintegrations:clockwork-alternation";

    /** Internal cooldown names, sharing {@code PowerRuntime}'s per-player map. */
    private static final String COOLDOWN_CLOCKWORK = "tc_clockwork_alternation";
    private static final String COOLDOWN_SOULSTEEL = "tc_soulsteel_resolve";
    static final String COOLDOWN_SOURCE_TEMPERING = "tc_source_tempering";

    /**
     * The one modifier id Soulsteel Resolve ever adds, so a second hit replaces rather than stacks.
     *
     * <p>Declared in {@code RunicAttributeModifiers} with every other owned id, not here: the purge
     * and the appliers must read one list, and an id hidden inside an optional integration is the
     * one a purge misses.
     */
    private static final UUID SOULSTEEL_MODIFIER = RunicAttributeModifiers.TC_SOULSTEEL_RESOLVE;

    private TcIntegrationsAdapter() {
    }

    /**
     * Installs the four perks' seams.
     *
     * <p>Called reflectively by {@link TcAddonRegistry}; the name and signature are that contract.
     */
    public static void install() {
        WearAvoidance.addContributor(TcIntegrationsAdapter::clockworkAvoidance);
        MinecraftForge.EVENT_BUS.register(new TcIntegrationsAdapter.Lifecycle());
        if (TcAddonPresence.isLoaded(TcAddonPresence.ARS_NOUVEAU)) {
            MinecraftForge.EVENT_BUS.register(new TcIntegrationsArsListener());
        }
        verifySeams();
    }

    /**
     * Checks that the two seams this release cannot boot-test still have the shape it read.
     *
     * <p>Mana Polisher and Source Tempering inject into methods that only exist on an install that
     * also has Botania or Ars Nouveau, so neither can be exercised by the gametest profiles. Their
     * injectors are therefore declared optional — an add-on update that moved the call site must
     * make the perk inert on somebody else's server, never crash it — and this is what stops the
     * diagnostic from reporting an inert perk as working: the same method shapes the mixins target,
     * asked reflectively, so a changed one is reported as {@code UPSTREAM_INCOMPATIBLE} with the
     * signature that went missing.
     */
    private static void verifySeams() {
        verify(TcAddonRegistry.SEAM_BOTANIA_CHARGE,
                "tcintegrations.items.modifiers.traits.ManaModifier", "getManaPerDamage",
                ServerPlayer.class);
        verify(TcAddonRegistry.SEAM_ARS_REPAIR,
                "tcintegrations.items.modifiers.ArsNouveauBaseModifier", "onInventoryTick",
                IToolStackView.class, slimeknights.tconstruct.library.modifiers.ModifierEntry.class,
                net.minecraft.world.level.Level.class, LivingEntity.class,
                int.class, boolean.class, boolean.class, ItemStack.class);
    }

    /** Reports whether {@code owner.method(parameters)} is still declared on the installed jar. */
    private static void verify(String seam, String owner, String method, Class<?>... parameters) {
        try {
            Class.forName(owner, false, TcIntegrationsAdapter.class.getClassLoader())
                    .getMethod(method, parameters);
            TcAddonRegistry.reportSeam(seam, true, null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            TcAddonRegistry.reportSeam(seam, false, owner + '.' + method
                    + " is not declared as this release read it: " + e);
        }
    }

    // -- Mana Polisher (Botania) ------------------------------------------------------------------

    /**
     * The mana a repair tick should actually request, given who is holding the tool.
     *
     * <p>Called from {@code MixManaModifier} at the argument of TCIntegrations' own
     * {@code requestManaExactForTool} call, which is the only place §10.3's "apply before the
     * existing exact-charge request" can be honoured: the add-on asks for an exact amount and then
     * sets tool damage itself, so a repair listener would see the durability and never the price,
     * and refunding afterwards is the "charge then refund" the same sentence forbids.
     *
     * <p>A positive cost stays positive. A discount that could reach zero would turn a paid repair
     * into a free one, which is a different feature.
     */
    public static int discountManaCharge(ServerPlayer player, int cost) {
        if (cost <= 0) return cost;
        if (!TcAddonHooks.active(player, RegistryPerks.TC_MANA_POLISHER,
                Capability.ADDON_BOTANIA_REPAIR_CHARGE)) {
            return cost;
        }
        int percent = HandlerCommonConfig.HANDLER.instance().tcManaPolisherPercent;
        if (percent <= 0) return cost;
        int discount = (int) Math.floor(cost * (percent / 100.0));
        return Math.max(1, cost - discount);
    }

    // -- Source Tempering (Ars Nouveau) -----------------------------------------------------------

    /**
     * The damage a native Ars armour repair started from, on this thread.
     *
     * <p>A thread local rather than a field on the mixin: the modifier is a singleton and the same
     * method runs on the client thread too, so a plain field could be clobbered between the two
     * halves of one observation. Cleared by {@link #endArsRepair} on every path.
     */
    private static final ThreadLocal<Integer> ARS_DAMAGE_BEFORE = new ThreadLocal<>();

    /** Records the damage before TCIntegrations' Ars repair tick runs. */
    public static void beginArsRepair(IToolStackView tool) {
        ARS_DAMAGE_BEFORE.set(tool == null ? null : tool.getDamage());
    }

    /**
     * Arms the charge if that tick actually spent source and restored durability.
     *
     * <p>The add-on removes mana and only then reduces damage, so a positive difference proves both
     * halves of §10.3's condition at once — and a tick that found full mana and a full-durability
     * tool restores nothing and arms nothing, which is the "do not infer success from a periodic
     * tick" rule made arithmetic.
     */
    public static void endArsRepair(LivingEntity wearer, IToolStackView tool) {
        Integer before = ARS_DAMAGE_BEFORE.get();
        ARS_DAMAGE_BEFORE.remove();
        if (before == null || tool == null) return;
        if (!(wearer instanceof ServerPlayer player)) return;
        armSourceTempering(player, before - tool.getDamage());
    }

    /**
     * Arms one spell charge after native armour repair has actually spent source.
     *
     * <p>Called from the add-on's Ars armour repair seam with the durability it restored. Both
     * conditions matter and neither is inferred from the other: §10.3 forbids arming on a periodic
     * tick that found nothing to do, and forbids arming when the mana check failed. A positive
     * restoration proves both — the add-on removes mana and only then reduces damage.
     *
     * <p>One charge per player, not per armour piece: a full set repairing in the same tick arms the
     * same charge four times, which is once.
     */
    static void armSourceTempering(ServerPlayer player, int restored) {
        if (restored <= 0) return;
        if (!TcAddonHooks.active(player, RegistryPerks.TC_SOURCE_TEMPERING,
                Capability.ADDON_ARS_ARMOR_REPAIR)) {
            return;
        }
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        long tick = TcAddonHooks.now(player);
        if (!PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), COOLDOWN_SOURCE_TEMPERING,
                tick, config.tcSourceTemperingCooldownTicks)) {
            return;
        }
        TcAddonState.of(player.getUUID()).sourceTemperingUntil =
                tick + DurationMath.secondsToTicks(config.tcSourceTemperingWindowSeconds);
    }

    /**
     * Spends the armed charge on one accepted spell, and reports its share of the damage.
     *
     * <p>Returns the multiplier once and clears the window, so a spell that hits several targets is
     * one spell: the charge is consumed at the first accepted damage event of that cast, and every
     * secondary or chained cast within the window gets nothing, which is §10.3's "no
     * secondary-cast recursion".
     */
    static float spendSourceTempering(ServerPlayer caster) {
        TcAddonState.Player state = TcAddonState.peek(caster == null ? null : caster.getUUID());
        if (state == null || state.sourceTemperingUntil <= 0) return 0.0f;
        if (!TcAddonHooks.active(caster, RegistryPerks.TC_SOURCE_TEMPERING,
                Capability.ADDON_ARS_ARMOR_REPAIR)) {
            return 0.0f;
        }
        long tick = TcAddonHooks.now(caster);
        if (tick > state.sourceTemperingUntil) {
            state.sourceTemperingUntil = 0L;
            return 0.0f;
        }
        state.sourceTemperingUntil = 0L;
        return HandlerCommonConfig.HANDLER.instance().tcSourceTemperingPercent / 100.0f;
    }

    // -- Clockwork Alternation (Create) and Soulsteel Resolve (Malum) ------------------------------

    /**
     * One native melee hit that Tinkers' has just landed, with the hand it was landed from.
     *
     * <p>Called from {@code MixToolAttackUtil}. That is the one place the hand is still known: by
     * the time a {@code LivingHurtEvent} is posted, an offhand attack from TCIntegrations'
     * Mechanical Arm is indistinguishable from a main-hand one, and guessing from which slot holds
     * a weapon would count a two-weapon player's every swing as an alternation. Runic never
     * synthesises an offhand attack; it only observes the one the add-on granted (§12.2).
     */
    public static void onNativeMeleeHit(LivingEntity attacker, InteractionHand hand,
                                        IToolStackView tool) {
        if (!(attacker instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (tool == null) return;
        boolean offhand = hand == InteractionHand.OFF_HAND;
        recordAlternation(player, tool, offhand);
        grantSoulsteelResolve(player, tool, offhand);
    }

    /**
     * Records the hand this hit came from and arms the window when it alternated.
     *
     * <p>The alternation has to be genuine on both sides: the previous hit must have been recent
     * (§10.3's five seconds) and from the other hand, and the tool must be one the add-on actually
     * granted an offhand attack to — which is what {@link TraitFeatureRegistry.Feature#OFFHAND_MELEE}
     * asks, rather than assuming every weapon can do it because Create is installed.
     */
    private static void recordAlternation(ServerPlayer player, IToolStackView tool, boolean offhand) {
        if (!TcAddonHooks.active(player, RegistryPerks.TC_CLOCKWORK_ALTERNATION,
                Capability.ADDON_OFFHAND_MELEE)) {
            return;
        }
        if (!TraitFeatureRegistry.present(tool, TraitFeatureRegistry.Feature.OFFHAND_MELEE)) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        long tick = TcAddonHooks.now(player);
        long window = DurationMath.secondsToTicks(config.tcClockworkAlternationWindowSeconds);
        TcAddonState.Player state = TcAddonState.of(player.getUUID());

        boolean alternated = state.clockworkLastOffhand != null
                && state.clockworkLastOffhand != offhand
                && tick - state.clockworkLastTick <= window;
        state.clockworkLastOffhand = offhand;
        state.clockworkLastTick = tick;
        if (!alternated) return;
        if (!PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), COOLDOWN_CLOCKWORK,
                tick, config.tcClockworkAlternationCooldownTicks)) {
            return;
        }
        state.clockworkArmedUntil = tick + window;
    }

    /**
     * Clockwork Alternation's share of the single wear-avoidance sum.
     *
     * <p>Joins {@code WearAvoidance}'s existing sum under its existing 0.90 clamp (§13.2) rather
     * than reducing wear itself. The window is spent on the first ordinary use that reaches the wear
     * stage, and the per-root claim is what makes a nine-block hammer swing one use rather than
     * nine.
     */
    private static double clockworkAvoidance(ServerPlayer player, ItemStack stack) {
        if (!TcAddonHooks.active(player, RegistryPerks.TC_CLOCKWORK_ALTERNATION,
                Capability.ADDON_OFFHAND_MELEE)) {
            return 0.0;
        }
        TcAddonState.Player state = TcAddonState.peek(player.getUUID());
        if (state == null || state.clockworkArmedUntil <= 0) return 0.0;
        long tick = TcAddonHooks.now(player);
        if (tick > state.clockworkArmedUntil) {
            state.clockworkArmedUntil = 0L;
            return 0.0;
        }
        if (!RunicActionContext.isOrdinaryUseBy(player.getUUID())) return 0.0;
        // Claimed rather than cleared: the children of one root action must all see the same
        // window, and only a second root action may find it spent.
        if (!RunicActionContext.hasClaimed(CLAIM_CLOCKWORK) && !RunicActionContext.claim(CLAIM_CLOCKWORK)) {
            return 0.0;
        }
        return HandlerCommonConfig.HANDLER.instance().tcClockworkAlternationPercent / 100.0;
    }

    /**
     * Soulsteel Resolve — a primary hit from soul-stained gear steadies the smith for a moment.
     *
     * <p>Primary means main hand: §10.3 is explicit that the modifier's own magical secondary damage
     * does not become a Runic hit, and an offhand swing is a different hit rather than a secondary
     * of this one. The bonus is exactly the declared transient knockback resistance — no spirits, no
     * Soul Ward, nothing of Malum's own behaviour is touched.
     */
    private static void grantSoulsteelResolve(ServerPlayer player, IToolStackView tool, boolean offhand) {
        if (offhand) return;
        if (!TcAddonHooks.active(player, RegistryPerks.TC_SOULSTEEL_RESOLVE,
                Capability.ADDON_SOUL_STAINED)) {
            return;
        }
        if (!TraitFeatureRegistry.present(tool, TraitFeatureRegistry.Feature.SOUL_STAINED)) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        long tick = TcAddonHooks.now(player);
        if (!PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), COOLDOWN_SOULSTEEL,
                tick, config.tcSoulsteelResolveCooldownTicks)) {
            return;
        }
        AttributeInstance instance = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (instance == null) return;
        instance.removeModifier(SOULSTEEL_MODIFIER);
        instance.addTransientModifier(new AttributeModifier(SOULSTEEL_MODIFIER,
                "Runic Skills Soulsteel Resolve", config.tcSoulsteelResolveAmount,
                AttributeModifier.Operation.ADDITION));
        TcAddonState.of(player.getUUID()).soulsteelUntil =
                tick + DurationMath.secondsToTicks(config.tcSoulsteelResolveSeconds);
    }

    /** Takes the transient modifier off once its four seconds are up, or the perk stops applying. */
    private static void reconcileSoulsteel(ServerPlayer player) {
        TcAddonState.Player state = TcAddonState.peek(player.getUUID());
        boolean expired = state == null || state.soulsteelUntil <= 0
                || TcAddonHooks.now(player) > state.soulsteelUntil;
        if (!expired && TcAddonHooks.active(player, RegistryPerks.TC_SOULSTEEL_RESOLVE,
                Capability.ADDON_SOUL_STAINED)) {
            return;
        }
        if (state != null) state.soulsteelUntil = 0L;
        AttributeInstance instance = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (instance != null) instance.removeModifier(SOULSTEEL_MODIFIER);
    }

    /** Drops one player's add-on memory and any modifier it left behind. */
    private static void forget(net.minecraft.world.entity.player.Player player) {
        if (player == null) return;
        AttributeInstance instance = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (instance != null) instance.removeModifier(SOULSTEEL_MODIFIER);
        TcAddonState.clear(player.getUUID());
    }

    /**
     * The FORGE-bus half: the transient modifier's expiry, and §10.1's lifecycle rules.
     *
     * <p>A separate class from the adapter so the adapter itself stays a static seam that a mixin
     * can call without an instance, and so the bus never scans a method whose parameter types come
     * from an add-on.
     */
    public static final class Lifecycle {

        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (!(event.player instanceof ServerPlayer player)) return;
            if (player.tickCount % ATTRIBUTE_INTERVAL_TICKS != 0) return;
            reconcileSoulsteel(player);
        }

        @SubscribeEvent
        public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            forget(event.getEntity());
        }

        @SubscribeEvent
        public void onClone(PlayerEvent.Clone event) {
            forget(event.getEntity());
        }

        @SubscribeEvent
        public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            forget(event.getEntity());
        }

        @SubscribeEvent
        public void onServerStopped(ServerStoppedEvent event) {
            TcAddonState.clearAll();
        }
    }
}
