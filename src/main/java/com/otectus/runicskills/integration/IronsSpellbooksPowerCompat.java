package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole;
import io.redspace.ironsspellbooks.entity.spells.creeper_head.CreeperHeadProjectile;
import io.redspace.ironsspellbooks.entity.spells.ice_block.IceBlockProjectile;
import io.redspace.ironsspellbooks.entity.spells.icicle.IcicleProjectile;
import io.redspace.ironsspellbooks.entity.spells.magic_arrow.MagicArrowProjectile;
import io.redspace.ironsspellbooks.entity.spells.magic_missile.MagicMissileProjectile;
import io.redspace.ironsspellbooks.entity.spells.magma_ball.FireField;
import io.redspace.ironsspellbooks.entity.spells.shield.ShieldEntity;
import io.redspace.ironsspellbooks.entity.spells.snowball.Snowball;
import io.redspace.ironsspellbooks.entity.spells.wall_of_fire.WallOfFireEntity;
import io.redspace.ironsspellbooks.entity.mobs.SummonedPolarBear;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;

/**
 * Class-load-isolated wrapper for every {@code io.redspace.ironsspellbooks.*} symbol
 * touched by the Powers dispatcher. Lives in the same package as
 * {@link IronsSpellbooksIntegration} and follows the same rule: this is the only
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
        } catch (Throwable t) {
            // Every other guard in this class is on a method that returns a value, so a failure
            // shows up as a zero the caller can reason about. This one is void: without a line
            // here a Power that grants mana would simply stop granting it, silently, for as long
            // as the incompatibility lasted.
            RunicSkills.getLOGGER().debug("Iron's Spells mana grant of {} failed: {}", amount, t.toString());
        }
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
     * {@code echoing_strikes}, {@code abyssal_shroud}, {@code guiding_bolt}, {@code rend},
     * {@code angel_wings}, {@code blight}, {@code slowed}.
     * Effects not present in current ISS (frozen/root/bleed/ascension/evasion) return null;
     * callers should null-check and fall back to vanilla {@code MobEffects}. angel_wings used to
     * be listed among the absent ones and is not: Wings of Judgment reads it.
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
                case "angel_wings"                  -> MobEffectRegistry.ANGEL_WINGS;
                case "blight"                       -> MobEffectRegistry.BLIGHT;
                case "slowed"                       -> MobEffectRegistry.SLOWED;
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

    // ── School Powers: spell identity, resist, cast time ────────────────────────────

    /** School of a SpellPreCastEvent, or null. */
    @Nullable
    public static ResourceLocation schoolOf(SpellPreCastEvent event) {
        if (event == null) return null;
        try {
            SchoolType school = event.getSchoolType();
            return school == null ? null : school.getId();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** The full spell id ({@code irons_spellbooks:magic_missile}) behind a SpellDamageEvent. */
    @Nullable
    public static String spellIdOf(SpellDamageEvent event) {
        if (event == null) return null;
        try {
            SpellDamageSource ds = event.getSpellDamageSource();
            if (ds == null || ds.spell() == null) return null;
            return ds.spell().getSpellId();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Whether a spell id names one of the spells the school Powers key off, by short key.
     *
     * <p>The comparison goes through {@link SpellRegistry} rather than a hard-coded
     * {@code irons_spellbooks:chain_lightning} literal, so a spell Iron's Spells renames turns
     * into a Power that stops firing rather than a Power that fires on the wrong spell.
     */
    public static boolean isSpell(@Nullable String spellId, String key) {
        if (spellId == null || key == null) return false;
        try {
            RegistryObject<AbstractSpell> ro = switch (key) {
                case "magic_missile"     -> SpellRegistry.MAGIC_MISSILE_SPELL;
                case "magic_arrow"       -> SpellRegistry.MAGIC_ARROW_SPELL;
                case "chain_creeper"     -> SpellRegistry.CHAIN_CREEPER_SPELL;
                case "fang_strike"       -> SpellRegistry.FANG_STRIKE_SPELL;
                case "fang_ward"         -> SpellRegistry.FANG_WARD_SPELL;
                case "shield"            -> SpellRegistry.SHIELD_SPELL;
                case "sunbeam"           -> SpellRegistry.SUNBEAM_SPELL;
                case "guiding_bolt"      -> SpellRegistry.GUIDING_BOLT_SPELL;
                case "chain_lightning"   -> SpellRegistry.CHAIN_LIGHTNING_SPELL;
                case "lightning_bolt"    -> SpellRegistry.LIGHTNING_BOLT_SPELL;
                case "lightning_lance"   -> SpellRegistry.LIGHTNING_LANCE_SPELL;
                case "ball_lightning"    -> SpellRegistry.BALL_LIGHTNING_SPELL;
                case "poison_arrow"      -> SpellRegistry.POISON_ARROW_SPELL;
                case "poison_breath"     -> SpellRegistry.POISON_BREATH_SPELL;
                case "poison_splash"     -> SpellRegistry.POISON_SPLASH_SPELL;
                case "sonic_boom"        -> SpellRegistry.SONIC_BOOM_SPELL;
                case "eldritch_blast"    -> SpellRegistry.ELDRITCH_BLAST_SPELL;
                case "telekinesis"       -> SpellRegistry.TELEKINESIS_SPELL;
                default -> null;
            };
            if (ro == null || !ro.isPresent()) return false;
            return spellId.equals(ro.get().getSpellId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Whether a damage source is an Iron's Spells spell-damage source. */
    public static boolean isSpellDamage(@Nullable DamageSource source) {
        try {
            return source instanceof SpellDamageSource;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * The school-resist multiplier already folded into a SpellDamageEvent's amount:
     * {@code 1.0} means unresisted, below 1 resisted, above 1 vulnerable. Piercing Insight
     * divides it back out to hand half of the resisted fraction back to the caster.
     * Returns 1.0 when the multiplier can't be determined, which makes that Power a no-op.
     */
    public static float resistMultiplier(SpellDamageEvent event) {
        if (event == null) return 1.0f;
        try {
            SpellDamageSource ds = event.getSpellDamageSource();
            if (ds == null || ds.spell() == null) return 1.0f;
            LivingEntity victim = event.getEntity();
            if (victim == null) return 1.0f;
            return DamageSources.getResist(victim, ds.spell().getSchoolType());
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    /** Iron's Spells' cast-time-reduction attribute, or null when it isn't registered. */
    @Nullable
    public static Attribute castTimeReductionAttribute() {
        try {
            return AttributeRegistry.CAST_TIME_REDUCTION.isPresent()
                    ? AttributeRegistry.CAST_TIME_REDUCTION.get() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Adds freeze ticks the way Iron's Spells' own ice spells do. */
    public static void addFreezeTicks(LivingEntity entity, int ticks) {
        if (entity == null || ticks <= 0) return;
        try {
            io.redspace.ironsspellbooks.api.util.Utils.addFreezeTicks(entity, ticks);
        } catch (Throwable t) {
            RunicSkills.getLOGGER().debug("Iron's Spells freeze application failed: {}", t.toString());
        }
    }

    /**
     * The living entity a player is currently holding with Telekinesis, or null.
     *
     * <p>Read straight off {@code MagicData}'s additional cast data — the spell stores its held
     * target there as a {@code TargetEntityCastData}, so no mixin into the spell is needed.
     */
    @Nullable
    public static LivingEntity telekinesisTarget(Player player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return null;
        try {
            MagicData magic = MagicData.getPlayerMagicData(player);
            if (magic == null || !magic.isCasting()) return null;
            if (!isSpell(magic.getCastingSpellId(), "telekinesis")) return null;
            if (!(magic.getAdditionalCastData() instanceof TargetEntityCastData data)) return null;
            return data.getTarget(level);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ── Spell-entity classification ────────────────────────────────────────────────

    public static final String KIND_FIRE_FIELD        = "fire_field";
    public static final String KIND_WALL_OF_FIRE      = "wall_of_fire";
    public static final String KIND_BLACK_HOLE        = "black_hole";
    public static final String KIND_SHIELD            = "shield";
    public static final String KIND_CREEPER_HEAD      = "creeper_head";
    public static final String KIND_ICE_PROJECTILE    = "ice_projectile";
    public static final String KIND_MAGIC_MISSILE     = "magic_missile";
    public static final String KIND_MAGIC_ARROW       = "magic_arrow";
    public static final String KIND_POLAR_BEAR_SUMMON = "polar_bear_summon";

    /**
     * Classifies an Iron's Spells entity into one of the {@code KIND_*} strings, or null when it
     * is not one the Powers care about. Strings rather than an enum so the dispatcher never has
     * to name an Iron's Spells class.
     */
    @Nullable
    public static String entityKind(@Nullable Entity entity) {
        if (entity == null) return null;
        try {
            if (entity instanceof FireField) return KIND_FIRE_FIELD;
            if (entity instanceof WallOfFireEntity) return KIND_WALL_OF_FIRE;
            if (entity instanceof BlackHole) return KIND_BLACK_HOLE;
            if (entity instanceof ShieldEntity) return KIND_SHIELD;
            if (entity instanceof CreeperHeadProjectile) return KIND_CREEPER_HEAD;
            if (entity instanceof IcicleProjectile || entity instanceof Snowball
                    || entity instanceof IceBlockProjectile) return KIND_ICE_PROJECTILE;
            if (entity instanceof MagicMissileProjectile) return KIND_MAGIC_MISSILE;
            if (entity instanceof MagicArrowProjectile) return KIND_MAGIC_ARROW;
            if (entity instanceof SummonedPolarBear) return KIND_POLAR_BEAR_SUMMON;
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Who owns a spell entity. {@code WallOfFireEntity} is not a {@code Projectile} — it is a
     * multipart shield — so it carries its own owner field and needs its own branch.
     */
    @Nullable
    public static Entity ownerOf(@Nullable Entity entity) {
        if (entity == null) return null;
        try {
            if (entity instanceof WallOfFireEntity wall) return wall.getOwner();
            if (entity instanceof Projectile projectile) return projectile.getOwner();
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** The summoner of an {@code IMagicSummon}, or null when the entity is not one. */
    @Nullable
    public static Entity summonerOf(@Nullable Entity entity) {
        if (entity == null) return null;
        try {
            return entity instanceof IMagicSummon summon ? summon.getSummoner() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ── AoE duration / radius ───────────────────────────────────────────────────────

    /** Remaining lifetime of an AoE spell entity in ticks, or -1 when it has none to read. */
    public static int aoeDuration(@Nullable Entity entity) {
        try {
            if (entity instanceof AoeEntity aoe) return aoe.getDuration();
            if (entity instanceof BlackHole hole) return hole.getDuration();
            return -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * Sets an AoE spell entity's lifetime.
     *
     * @return false when the entity has no settable duration. {@code WallOfFireEntity} is the
     *         case that matters: Iron's Spells keeps its lifetime in a private field with no
     *         setter, so Scorched Earth extends fire fields but not walls of fire.
     */
    public static boolean setAoeDuration(@Nullable Entity entity, int ticks) {
        try {
            if (entity instanceof AoeEntity aoe) { aoe.setDuration(ticks); return true; }
            if (entity instanceof BlackHole hole) { hole.setDuration(ticks); return true; }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Radius of an AoE spell entity in blocks, or -1 when it has none to read. */
    public static float aoeRadius(@Nullable Entity entity) {
        try {
            if (entity instanceof AoeEntity aoe) return aoe.getRadius();
            if (entity instanceof BlackHole hole) return hole.getRadius();
            return -1f;
        } catch (Throwable ignored) {
            return -1f;
        }
    }

    // ── Spawning derivative spell entities ──────────────────────────────────────────

    /**
     * Persistent-data keys marking an entity this mod spawned rather than a spell.
     *
     * <p>Every derivative below re-enters the same event that produced it — a phantom missile
     * fires {@code SpellDamageEvent}, an extra creeper head fires {@code EntityJoinLevelEvent},
     * a second shield panel fires both. The stamp is the recursion guard, and it is checked
     * before every spawn.
     */
    public static final String PHANTOM_ARCANE_ECHO   = "rs_arcane_echo_phantom";
    public static final String PHANTOM_CASCADE_EXTRA = "rs_cascade_extra";
    public static final String PHANTOM_SHIELD_PANEL  = "rs_shield_wall_panel";

    /** Whether {@code entity} carries the given mod-spawned stamp. */
    public static boolean isPhantom(@Nullable Entity entity, String key) {
        return entity != null && entity.getPersistentData().getBoolean(key);
    }

    private static void stamp(Entity entity, String key) {
        entity.getPersistentData().putBoolean(key, true);
    }

    /**
     * Spawns a weakened copy of a Magic Missile / Magic Arrow, homing on the victim the original
     * just hit. Returns false when the original is neither of those.
     */
    public static boolean spawnEchoProjectile(ServerLevel level, LivingEntity owner,
                                              @Nullable Entity original, LivingEntity target,
                                              float damage) {
        if (level == null || owner == null || original == null || target == null) return false;
        try {
            AbstractMagicProjectile phantom;
            if (original instanceof MagicMissileProjectile) {
                phantom = new MagicMissileProjectile(level, owner);
            } else if (original instanceof MagicArrowProjectile) {
                phantom = new MagicArrowProjectile(level, owner);
            } else {
                return false;
            }
            phantom.setDamage(damage);
            phantom.setPos(original.position());
            phantom.setHomingTarget(target);
            phantom.shoot(target.getEyePosition().subtract(original.position()).normalize()
                    .scale(phantom.getSpeed()));
            stamp(phantom, PHANTOM_ARCANE_ECHO);
            return level.addFreshEntity(phantom);
        } catch (Throwable t) {
            RunicSkills.getLOGGER().debug("Arcane Echo phantom spawn failed: {}", t.toString());
            return false;
        }
    }

    /** Spawns an extra Chain Creeper head at {@code pos}, homing on {@code homingTarget}. */
    public static boolean spawnCreeperHead(ServerLevel level, LivingEntity owner, Vec3 pos,
                                           @Nullable LivingEntity homingTarget, float damage) {
        if (level == null || owner == null || pos == null) return false;
        try {
            Vec3 aim = homingTarget != null
                    ? homingTarget.getEyePosition().subtract(pos).normalize().scale(0.75)
                    : new Vec3(0.0, 0.75, 0.0);
            CreeperHeadProjectile head = new CreeperHeadProjectile(owner, level, aim, damage);
            head.setPos(pos);
            if (homingTarget != null) head.setHomingTarget(homingTarget);
            stamp(head, PHANTOM_CASCADE_EXTRA);
            return level.addFreshEntity(head);
        } catch (Throwable t) {
            RunicSkills.getLOGGER().debug("Creeper Cascade extra head spawn failed: {}", t.toString());
            return false;
        }
    }

    /**
     * Adds {@code delta} to a Chain Creeper head's remaining hop count.
     *
     * <p>{@code chainCount} is protected with a public setter but no getter, so the read goes
     * through {@link com.otectus.runicskills.mixin.MixCreeperHeadProjectile}. When Iron's Spells
     * is absent that mixin never applies and the {@code instanceof} simply fails.
     */
    public static void bumpCreeperChain(@Nullable Entity head, int delta) {
        if (!(head instanceof CreeperHeadProjectile creeperHead) || delta == 0) return;
        try {
            if (!(head instanceof com.otectus.runicskills.mixin.MixCreeperHeadProjectile access)) return;
            creeperHead.setChainCount(Math.max(0, access.runicskills$getChainCount() + delta));
        } catch (Throwable t) {
            RunicSkills.getLOGGER().debug("Creeper Cascade chain bump failed: {}", t.toString());
        }
    }

    /** Spawns a second Shield panel carrying the original's health, facing {@code yaw}. */
    public static boolean spawnShieldPanel(ServerLevel level, @Nullable Entity original,
                                           Vec3 pos, float yaw) {
        if (level == null || pos == null) return false;
        try {
            float health = original instanceof ShieldEntity shield ? shield.getHealth() : 10.0f;
            ShieldEntity panel = new ShieldEntity(level, health);
            panel.setPos(pos);
            panel.setRotation(0.0f, yaw);
            stamp(panel, PHANTOM_SHIELD_PANEL);
            return level.addFreshEntity(panel);
        } catch (Throwable t) {
            RunicSkills.getLOGGER().debug("Shield Wall panel spawn failed: {}", t.toString());
            return false;
        }
    }
}
