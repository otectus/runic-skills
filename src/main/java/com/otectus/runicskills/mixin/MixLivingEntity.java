package com.otectus.runicskills.mixin;

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

    // ── Which item is being consumed ────────────────────────────────────────────────────────

    /**
     * The consumable this entity is in the act of finishing, or empty.
     *
     * <p>Alchemy Manipulation and the beneficial-effect duration bonus are about <em>drinking a
     * potion</em>, and the only honest way to know that an effect arrived because of a drink is to
     * know that a drink is being completed right now. {@code isUsingItem()} is true for the whole
     * animation and answers a much weaker question.
     *
     * <p>Set and cleared around {@code completeUsingItem}, which is the call that runs
     * {@code finishUsingItem} — the method that adds a potion's effects. Anything added inside that
     * window came from the item; anything added outside it did not.
     */
    @Unique
    private ItemStack runicskills$finishing = ItemStack.EMPTY;

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void runicskills$beginFinishingItem(CallbackInfo ci) {
        this.runicskills$finishing = this.runicskills$self.getUseItem();
    }

    @Inject(method = "completeUsingItem", at = @At("RETURN"))
    private void runicskills$endFinishingItem(CallbackInfo ci) {
        this.runicskills$finishing = ItemStack.EMPTY;
    }

    /**
     * Whether a potion this entity is drinking is the reason an effect is arriving.
     *
     * <p>The {@code isUsingItem} conjunct is a belt-and-braces guard rather than the test itself: if
     * {@code completeUsingItem} ever escaped by exception the marker would not be cleared, and this
     * keeps a stale marker from amplifying effects for the rest of the session.
     */
    @Unique
    private boolean runicskills$isDrinkingAPotion() {
        return this.runicskills$self.isUsingItem()
                && !this.runicskills$finishing.isEmpty()
                && this.runicskills$finishing.getItem() instanceof PotionItem;
    }

    // ── Effect perks ────────────────────────────────────────────────────────────────────────

    /**
     * Adjusts an incoming effect for the three perks that change one, and nothing else.
     *
     * <p>Lion Heart shortens harmful effects; Alchemy Manipulation strengthens a drunk potion, and
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

        int duration = incoming.getDuration();
        int amplifier = incoming.getAmplifier();
        MobEffectCategory category = incoming.getEffect().getCategory();

        // Lion Heart — harmful effects run their course sooner. Infinite effects are left alone:
        // a share of "forever" is meaningless, and shortening one would be a different perk.
        if (category == MobEffectCategory.HARMFUL
                && !incoming.isInfiniteDuration()
                && RegistryPerks.LION_HEART != null
                && RegistryPerks.LION_HEART.get().isEnabled(player)) {
            double cut = RegistryPerks.LION_HEART.get().getActiveValue(player)[0] / 100.0;
            if (cut > 0) duration -= (int) (duration * Math.min(1.0, cut));
        }

        if (category == MobEffectCategory.BENEFICIAL && runicskills$isDrinkingAPotion()) {
            if (RegistryPerks.ALCHEMY_MANIPULATION != null
                    && RegistryPerks.ALCHEMY_MANIPULATION.get().isEnabled(player)) {
                amplifier += (int) RegistryPerks.ALCHEMY_MANIPULATION.get().getActiveValue(player)[0];
            }
            if (!incoming.isInfiniteDuration()) {
                double bonusSeconds =
                        player.getAttributeValue(RegistryAttributes.BENEFICIAL_EFFECT.get());
                if (bonusSeconds > 0) duration += (int) bonusSeconds * 20;
            }
        }

        if (duration == incoming.getDuration() && amplifier == incoming.getAmplifier()) {
            // Nothing to change: hand back the very instance vanilla was given. This is what makes
            // "both perks off behaves exactly like vanilla" true by construction rather than by
            // careful copying.
            return incoming;
        }
        return runicskills$respan(incoming, Math.max(0, duration), Math.max(0, amplifier));
    }

    /**
     * A copy of {@code original} with a new duration and amplifier and everything else intact.
     *
     * <p>The eight-argument constructor is used rather than the three-argument one precisely
     * because of what the short one drops: ambient state decides whether a beacon's effect renders
     * as a faint overlay, {@code visible} and {@code showIcon} decide whether the player sees
     * particles and an icon at all, and Forge's curative list decides what cures it. The hidden
     * effect is passed through for completeness even though an <em>incoming</em> instance never
     * carries one — vanilla builds that chain inside {@code update}, on the instance already
     * active, which this never touches.
     */
    @Unique
    private static MobEffectInstance runicskills$respan(MobEffectInstance original,
                                                        int duration, int amplifier) {
        MobEffectInstance copy = new MobEffectInstance(
                original.getEffect(), duration, amplifier,
                original.isAmbient(), original.isVisible(), original.showIcon(),
                null, original.getFactorData());
        copy.setCurativeItems(new java.util.ArrayList<>(original.getCurativeItems()));
        return copy;
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
