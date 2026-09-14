package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.effects.IncomingEffectPolicy;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistryTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The three player-facing adjustments this mod makes to {@link LivingEntity}.
 *
 * <p><b>What this class used to do, and why it stopped (RS10-008).</b> The effect perks were
 * implemented by cancelling <em>both</em> public {@code addEffect} overloads for every player and
 * reimplementing vanilla's body — the {@code canBeAffected} gate, the Forge event, the
 * {@code activeEffects} map, the added/updated callbacks — inside this file. That had four
 * consequences, none of them intended:
 *
 * <ul>
 *   <li>The rebuilt instance used a three-argument constructor, so <b>ambient state, particle
 *       visibility, icon visibility, the hidden-effect chain and Forge's curative-item list were
 *       silently dropped</b> from every effect a player received. A beacon's ambient effect arrived
 *       non-ambient; an effect flagged icon-hidden arrived with its icon.</li>
 *   <li>The affected player was passed to Forge's {@code MobEffectEvent.Added} as the source, so a
 *       splash potion thrown by somebody else <b>reported its victim as its cause</b>.</li>
 *   <li>Cancelling meant no other mixin later in the method, and no future Forge or vanilla change
 *       to the method, could participate at all.</li>
 *   <li>The potion perks keyed off {@code player.isUsingItem()}, which is true for the whole
 *       drinking animation — so a beacon pulse that happened to land during a drink was amplified
 *       as though it were the potion.</li>
 * </ul>
 *
 * <p>All of it is replaced by one narrow argument modification at the head of the two-argument
 * overload — which the one-argument overload delegates to, so a single hook covers both. Vanilla's
 * body then runs exactly as written, with the original source entity, and this mod's only
 * involvement is the instance it is handed. When neither perk applies the argument is returned
 * untouched, so a player with both perks off is byte-for-byte vanilla.
 */
@Mixin(LivingEntity.class)
public abstract class MixLivingEntity {

    /** Runs only after LivingDrops listeners decline ownership (for example a grave). */
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "dropAllDeathLoot",
            at = @At(value = "INVOKE", target = "Ljava/util/Collection;forEach(Ljava/util/function/Consumer;)V", remap = false))
    private void runicskills$recoverRefusedDeathDrops(java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops,
            java.util.function.Consumer<net.minecraft.world.entity.item.ItemEntity> spawn,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (!((Object) this instanceof Player player)) { original.call(drops, spawn); return; }
        original.call(drops, (java.util.function.Consumer<net.minecraft.world.entity.item.ItemEntity>) entity -> {
            spawn.accept(entity);
            if (!entity.isAddedToWorld()) com.otectus.runicskills.common.inventory.InventoryReconciliation.recover(player, entity.getItem());
        });
    }

    @Unique
    private final LivingEntity runicskills$self = (LivingEntity) (Object) this;

    /**
     * The hard ceiling on magic resistance, whatever the configuration says.
     *
     * <p>The formula is {@code damage - damage * resist}, so a resistance above 1 produces negative
     * damage — which is healing. The attribute's own range allows 1024, and the config field that
     * feeds it allowed 10,000 (RS10-009). A clamp at the point of use is the only place that
     * catches every route to the number, including another mod's modifier on the same attribute.
     */
    @Unique
    private static final double RUNICSKILLS$MAX_MAGIC_RESISTANCE = 0.95;

    // ── Magic resistance ────────────────────────────────────────────────────────────────────

    /**
     * Applies the Magic Resist passive to damage that is actually magical.
     *
     * <p>The test used to be {@code DamageSource#isIndirect()}, which is not a magic classifier at
     * all: it is true for a mundane arrow and a thrown trident, and false for a mob's direct magical
     * touch. Players were resisting archery and taking full damage from spells (RS10-009).
     *
     * <p>What counts is now a damage-type tag this mod ships and a pack or an integration can
     * extend, so a modded spell can opt in without a code change and the tooltip has something
     * concrete to name.
     */
    @ModifyVariable(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), argsOnly = true)
    private float runicskills$reduceMagicDamage(float damage, DamageSource source) {
        if (damage <= 0.0F || source == null) return damage;
        if (!source.is(RegistryTags.DamageTypes.AFFECTED_BY_MAGIC_RESISTANCE)) return damage;

        AttributeInstance magicResist =
                this.runicskills$self.getAttribute(RegistryAttributes.MAGIC_RESIST.get());
        if (magicResist == null) return damage;

        double resist = magicResist.getValue();
        // A hand-edited config, a broken modifier or an arithmetic accident can all produce these;
        // none of them should be able to make a hit heal (RS10-009).
        if (!Double.isFinite(resist) || resist <= 0.0) return damage;
        resist = Math.min(RUNICSKILLS$MAX_MAGIC_RESISTANCE, resist);

        float reduced = (float) (damage * (1.0 - resist));
        return Float.isFinite(reduced) ? Math.max(0.0F, reduced) : 0.0F;
    }

    /** Scope only the native food effect call, including exception/nesting cleanup. */
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "addEatEffect", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private boolean runicskills$foodEffect(LivingEntity target, MobEffectInstance effect,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        return com.otectus.runicskills.common.effects.EffectApplicationContext.apply(target, effect,
                com.otectus.runicskills.common.effects.EffectApplicationContext.Origin.FOOD,
                () -> original.call(target, effect));
    }

    // ── Effect perks ────────────────────────────────────────────────────────────────────────

    /**
     * Adjusts an incoming effect for the four perks that change one, and nothing else.
     *
     * <p>Lion Heart and Lucky Charm shorten harmful effects; Alchemy Manipulation strengthens a
     * drunk potion, and
     * the beneficial-effect attribute lengthens it. Each reads the instance vanilla is about to
     * process and hands back a replacement, so vanilla's own merge — including the Forge event, its
     * source entity, and every other listener — happens exactly as it would have.
     */
    @ModifyVariable(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;"
                    + "Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private MobEffectInstance runicskills$adjustIncomingEffect(MobEffectInstance incoming) {
        if (incoming == null) return incoming;
        if (!(this.runicskills$self instanceof Player player)) return incoming;

        if (player.level().isClientSide() || player instanceof net.minecraftforge.common.util.FakePlayer) return incoming;
        if (incoming.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
            return runicskills$shortenHarmful(player, incoming);
        }
        if (incoming.getEffect().getCategory() != MobEffectCategory.BENEFICIAL) return incoming;
        var cfg = HandlerCommonConfig.HANDLER.instance();
        double percent = 0;
        if (RegistryPerks.TEMPORAL_WISDOM.get().isEnabled(player)
                && com.otectus.runicskills.registry.events.EnchantingLorePerkHandler.inCombat(player)) {
            percent += Math.max(0, cfg.temporalWisdomPercent);
        }
        if (incoming.getEffect() == net.minecraft.world.effect.MobEffects.LUCK
                && RegistryPerks.BLESSING_OF_LUCK.get().isEnabled(player)) {
            percent += Math.max(0, cfg.blessingOfLuckPercent);
        }
        var origin = com.otectus.runicskills.common.effects.EffectApplicationContext.origin(player, incoming);
        if (origin == com.otectus.runicskills.common.effects.EffectApplicationContext.Origin.FOOD
                && RegistryPerks.HEARTY_FEAST != null && RegistryPerks.HEARTY_FEAST.get().isEnabled(player)) percent += Math.max(0, cfg.heartyFeastPercent);
        double flatTicks = 0;
        int amplifier = 0;
        if (origin == com.otectus.runicskills.common.effects.EffectApplicationContext.Origin.POTION) {
            flatTicks = player.getAttributeValue(RegistryAttributes.BENEFICIAL_EFFECT.get()) * 20.0;
            if (RegistryPerks.ALCHEMY_MANIPULATION.get().isEnabled(player)) {
                amplifier = (int) RegistryPerks.ALCHEMY_MANIPULATION.get().getActiveValue(player)[0];
            }
        }
        return com.otectus.runicskills.common.effects.EffectApplicationContext.transformed(player, incoming,
                IncomingEffectPolicy.extend(incoming, percent, flatTicks, amplifier));
    }

    /**
     * Lion Heart and Lucky Charm — harmful effects run their course sooner.
     *
     * <p><b>Summed, then applied once.</b> Two perks that each take a quarter off take half between
     * them, not 43.75%: applying one reduction to the output of the other is how a stack of
     * duration perks stops being legible, and it is why {@link IncomingEffectPolicy#shorten} is
     * called with one total rather than called twice.
     *
     * <p><b>Why Lucky Charm is here and not in an event handler (RS207-04).</b> It used to listen to
     * {@code MobEffectEvent.Added} and call {@code addEffect} again with a rebuilt instance. That
     * fires before the active-map merge, after vanilla has run {@code canBeAffected}, posted the event to every
     * other listener (before merging into the active map), so every observer saw the full
     * duration and then a second, shorter application of the same effect arriving from this mod —
     * and the rebuild used the three-argument constructor, which drops the ambient flag, the icon
     * and particle flags, the curative items and the factor data. HEAD of the two-argument
     * {@code addEffect} is the one point that precedes all of it, so there is exactly one
     * application and everybody sees the same one.
     *
     * <p>Lucky Charm is capped at 90% on its own; Lion Heart is not, because its configured value
     * is a perk rank rather than a raw percentage and 100% is a legitimate top rank for it.
     */
    @Unique
    private static MobEffectInstance runicskills$shortenHarmful(Player player, MobEffectInstance incoming) {
        // Infinite effects are left alone: a share of "forever" is meaningless, and shortening one
        // would be a different perk.
        if (incoming.isInfiniteDuration()) return incoming;

        double cut = 0.0;
        if (RegistryPerks.LION_HEART != null && RegistryPerks.LION_HEART.get().isEnabled(player)) {
            cut += RegistryPerks.LION_HEART.get().getActiveValue(player)[0] / 100.0;
        }
        if (RegistryPerks.LUCKY_CHARM != null && RegistryPerks.LUCKY_CHARM.get().isEnabled(player)) {
            cut += Math.min(0.90,
                    HandlerCommonConfig.HANDLER.instance().luckyCharmPercent / 100.0);
        }
        if (cut <= 0) return incoming;
        return IncomingEffectPolicy.shorten(incoming, cut);
    }

    // ── Stealth ─────────────────────────────────────────────────────────────────────────────

    @Inject(method = "getVisibilityPercent", at = @At("TAIL"), cancellable = true)
    public void getVisibilityPercent(Entity source, CallbackInfoReturnable<Double> cir) {
        double visibilityPercent = cir.getReturnValue();
        if (this.runicskills$self instanceof ServerPlayer player) {
            if (RegistryPerks.STEALTH_MASTERY != null
                    && RegistryPerks.STEALTH_MASTERY.get().isEnabled(player)) {
                double[] values = RegistryPerks.STEALTH_MASTERY.get().getActiveValue(player);
                // Value[] order (RegistryPerks): [0] = standing, [1] = crouching.
                double visibilityFactor = player.isShiftKeyDown() ? values[1] : values[0];
                cir.setReturnValue(visibilityPercent * visibilityFactor / 100.0D);
            }
        }
    }
}
