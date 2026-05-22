package com.otectus.runicskills.integration;

import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;

/**
 * Class-load-isolated wrapper for every {@code io.redspace.ironsspellbooks.*} symbol
 * touched by the Powers dispatcher. Lives in the same package as {@link BotaniaCompat}
 * / {@link IronsSpellbooksIntegration} and follows the same rule: this is the only
 * file where Powers code may import ISS types. Callers guard with
 * {@link IronsSpellbooksIntegration#isModLoaded()} before invoking, so
 * {@code NoClassDefFoundError} stays quarantined here.
 */
public final class IronsSpellbooksPowerCompat {

    private IronsSpellbooksPowerCompat() {}

    // ── School identification ───────────────────────────────────────────────────────

    /** Returns the school ResourceLocation for a spell-id string, or null if unknown. */
    @Nullable
    public static ResourceLocation schoolIdFor(String spellId) {
        if (spellId == null) return null;
        try {
            AbstractSpell spell = SpellRegistry.getSpell(new ResourceLocation(spellId));
            if (spell == null) return null;
            SchoolType school = spell.getSchoolType();
            return school == null ? null : school.getId();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Convenience: does the spell belong to the given school (by ResourceLocation)? */
    public static boolean spellIsSchool(String spellId, ResourceLocation schoolId) {
        if (schoolId == null) return false;
        ResourceLocation actual = schoolIdFor(spellId);
        return schoolId.equals(actual);
    }

    /** Read from SpellOnCastEvent without bleeding ISS types to the caller. */
    @Nullable
    public static ResourceLocation schoolOf(SpellOnCastEvent event) {
        if (event == null) return null;
        SchoolType school = event.getSchoolType();
        return school == null ? null : school.getId();
    }

    @Nullable
    public static ResourceLocation schoolOf(SpellDamageEvent event) {
        if (event == null) return null;
        try {
            var spellDs = event.getSpellDamageSource();
            if (spellDs == null || spellDs.spell() == null) return null;
            SchoolType school = spellDs.spell().getSchoolType();
            return school == null ? null : school.getId();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Source/caster entity on a SpellDamageEvent, or null. */
    @Nullable
    public static net.minecraft.world.entity.Entity sourceEntityOf(SpellDamageEvent event) {
        if (event == null) return null;
        try {
            var spellDs = event.getSpellDamageSource();
            return spellDs == null ? null : spellDs.getEntity();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Direct (immediate) damage-causing entity on a SpellDamageEvent — typically the
     * projectile entity for projectile-form spells, or the caster for touch/AoE spells.
     * Used by Arcanist's Barrage to gate on projectile hits without leaking ISS types.
     */
    @Nullable
    public static net.minecraft.world.entity.Entity directEntityOf(SpellDamageEvent event) {
        if (event == null) return null;
        try {
            var spellDs = event.getSpellDamageSource();
            return spellDs == null ? null : spellDs.getDirectEntity();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    public static String spellIdOf(SpellOnCastEvent event) {
        if (event == null) return null;
        return event.getSpellId();
    }

    // ── Cast-type detection ─────────────────────────────────────────────────────────

    /**
     * Returns the spell's CastType name (one of {@code INSTANT}, {@code LONG},
     * {@code CONTINUOUS}, {@code CHARGE}, {@code NONE}) or null if the spell can't
     * be looked up. Returned as a string so the dispatcher needn't import {@link CastType}.
     */
    @Nullable
    public static String castTypeOf(SpellOnCastEvent event) {
        if (event == null) return null;
        try {
            AbstractSpell spell = SpellRegistry.getSpell(new ResourceLocation(event.getSpellId()));
            if (spell == null) return null;
            CastType ct = spell.getCastType();
            return ct == null ? null : ct.name();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * True iff the entity is currently mid-cast on a CONTINUOUS-type spell. Used by
     * The Long Note to validate that a sustained channel is still in progress before
     * applying its chain-target effect.
     */
    public static boolean isCastingContinuous(LivingEntity entity) {
        if (entity == null) return false;
        try {
            MagicData magic = MagicData.getPlayerMagicData(entity);
            return magic != null && magic.isCasting() && magic.getCastType() == CastType.CONTINUOUS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ── Mana reads ──────────────────────────────────────────────────────────────────

    public static int currentMana(LivingEntity entity) {
        if (entity == null) return 0;
        try {
            MagicData data = MagicData.getPlayerMagicData(entity);
            return data == null ? 0 : (int) data.getMana();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static int maxMana(LivingEntity entity) {
        if (entity == null) return 0;
        try {
            AttributeInstance attr = entity.getAttribute(AttributeRegistry.MAX_MANA.get());
            return attr == null ? 0 : (int) attr.getValue();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static void addMana(LivingEntity entity, int amount) {
        if (entity == null || amount == 0) return;
        try {
            MagicData data = MagicData.getPlayerMagicData(entity);
            if (data == null) return;
            int max = maxMana(entity);
            float next = (float) Math.max(0, Math.min(max, data.getMana() + amount));
            data.setMana(next);
        } catch (Throwable ignored) { }
    }

    public static float manaFraction(LivingEntity entity) {
        int max = maxMana(entity);
        return max <= 0 ? 0f : (float) currentMana(entity) / max;
    }

    // ── MobEffect lookups ───────────────────────────────────────────────────────────

    /**
     * Look up a Powers-relevant ISS effect by its registry path. Returns null if ISS hasn't
     * registered it. Effect names observed in ISS 3.15.x: {@code immolate}, {@code chilled},
     * {@code charged}, {@code fortify}, {@code oakskin}, {@code planar_sight},
     * {@code echoing_strikes}, {@code abyssal_shroud}, {@code guiding_bolt}, {@code rend}.
     * Effects not present in current ISS (frozen/root/bleed/angel_wings/ascension/evasion)
     * return null; callers should null-check and fall back to vanilla {@code MobEffects}.
     */
    @Nullable
    public static MobEffect effect(String path) {
        if (path == null) return null;
        try {
            RegistryObject<? extends MobEffect> ro = switch (path) {
                // Doc "Ignited" maps to ISS IMMOLATE; doc "Bleeding" maps to REND.
                case "immolate", "ignited", "ember" -> MobEffectRegistry.IMMOLATE;
                case "chilled", "frozen"            -> MobEffectRegistry.CHILLED;
                case "charged"                      -> MobEffectRegistry.CHARGED;
                case "fortify"                      -> MobEffectRegistry.FORTIFY;
                case "oakskin"                      -> MobEffectRegistry.OAKSKIN;
                case "planar_sight"                 -> MobEffectRegistry.PLANAR_SIGHT;
                case "echoing_strikes"              -> MobEffectRegistry.ECHOING_STRIKES;
                case "abyssal_shroud"               -> MobEffectRegistry.ABYSSAL_SHROUD;
                case "guiding_bolt"                 -> MobEffectRegistry.GUIDING_BOLT;
                case "rend", "bleeding", "blooded"  -> MobEffectRegistry.REND;
                default -> null;
            };
            return ro == null ? null : ro.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean hasEffect(LivingEntity entity, String path) {
        if (entity == null) return false;
        MobEffect eff = effect(path);
        return eff != null && entity.hasEffect(eff);
    }

    public static void applyEffect(LivingEntity entity, String path, int durationTicks, int amplifier) {
        if (entity == null) return;
        MobEffect eff = effect(path);
        if (eff == null) return;
        entity.addEffect(new MobEffectInstance(eff, durationTicks, amplifier));
    }
}
