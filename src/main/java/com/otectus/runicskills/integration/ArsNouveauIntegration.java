package com.otectus.runicskills.integration;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import com.hollingsworth.arsnouveau.api.event.*;
import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellSchools;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

public class ArsNouveauIntegration {

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("ars_nouveau");
    }

    // ── Spell Gating: gate spells by glyph count ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellResolve(SpellResolveEvent.Pre event) {
        if (!(event.shooter instanceof Player player)) return;
        if (player.isCreative()) return;

        SkillCapability provider = SkillCapability.get(player);
        if (provider == null) return;

        if (HandlerCommonConfig.HANDLER.instance().arsEnableSpellGating) {
            Skill magicSkill = RegistrySkills.MAGIC.get();
            int magicLevel = provider.getSkillLevel(magicSkill);
            int glyphCount = event.spell.recipe.size();
            int requiredLevel = (int) (HandlerCommonConfig.HANDLER.instance().arsBaseSpellGatingLevel
                    + (glyphCount - 1) * HandlerCommonConfig.HANDLER.instance().arsSpellComplexityScaleFactor);

            if (magicLevel < requiredLevel) {
                if (player instanceof ServerPlayer) {
                    player.sendSystemMessage(Component.translatable("overlay.runicskills.ars_spell_gated",
                            Component.translatable(magicSkill.getKey()), requiredLevel));
                }
                event.setCanceled(true);
            }
        }
    }

    // ── Spell Damage Scaling + Arcane Ward ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellDamage(SpellDamageEvent.Pre event) {
        // Scale outgoing damage for caster
        if (HandlerCommonConfig.HANDLER.instance().arsEnableSpellDamageScaling) {
            if (event.caster instanceof Player caster && !caster.isCreative()) {
                SkillCapability casterCap = SkillCapability.get(caster);
                if (casterCap != null) {
                    int magicLevel = casterCap.getSkillLevel(RegistrySkills.MAGIC.get());
                    float bonus = (magicLevel - 1) * HandlerCommonConfig.HANDLER.instance().arsSpellDamageScalePerLevel;
                    if (bonus > 0) {
                        event.damage = event.damage * (1.0f + bonus);
                    }

                    // Cross-mod synergy: Wisdom adds a flat spell damage bonus
                    if (HandlerCommonConfig.HANDLER.instance().enableWisdomSpellDamageBonus) {
                        int wisdomLevel = casterCap.getSkillLevel(RegistrySkills.WISDOM.get());
                        float wisdomBonus = wisdomLevel * HandlerCommonConfig.HANDLER.instance().wisdomSpellDamagePerLevel;
                        if (wisdomBonus > 0) {
                            event.damage = event.damage + wisdomBonus;
                        }
                    }
                }
            }
        }

        // Cross-mod synergy: Constitution provides passive spell defense
        if (event.target instanceof Player target && !target.isCreative()) {
            if (HandlerCommonConfig.HANDLER.instance().enableConstitutionSpellDefense) {
                SkillCapability targetCap = SkillCapability.get(target);
                if (targetCap != null) {
                    int conLevel = targetCap.getSkillLevel(RegistrySkills.CONSTITUTION.get());
                    float reduction = conLevel * HandlerCommonConfig.HANDLER.instance().constitutionSpellDefensePerLevel;
                    float maxReduction = HandlerCommonConfig.HANDLER.instance().maxConstitutionSpellDefense;
                    if (reduction > 0) {
                        event.damage = event.damage * (1.0f - Math.min(reduction, maxReduction));
                    }
                }
            }

            // Arcane Ward: perk-based reduction (stacks after Constitution passive)
            if (RegistryPerks.ARCANE_WARD != null && RegistryPerks.ARCANE_WARD.get().isEnabled(target)) {
                int percent = HandlerCommonConfig.HANDLER.instance().arsArcaneWardPercent;
                event.damage = event.damage * (1.0f - percent / 100.0f);
            }
        }

        // ── Phase 2b: Form Focus: Touch — damage bonus for touch-form spells ──
        if (event.caster instanceof Player caster2 && !caster2.isCreative()
                && RegistryPerks.ARS_FORM_TOUCH != null
                && RegistryPerks.ARS_FORM_TOUCH.get().isEnabled(caster2)
                && event.context != null
                && event.context.getSpell() != null
                && event.context.getSpell().getCastMethod() == MethodTouch.INSTANCE) {
            float bonus = HandlerCommonConfig.HANDLER.instance().arsFormTouchPercent / 100.0f;
            event.damage = event.damage * (1.0f + bonus);
        }

        // ── Phase 2c: per-school damage bonuses ──
        if (event.caster instanceof Player schoolCaster && !schoolCaster.isCreative()
                && event.context != null && event.context.getSpell() != null) {
            Spell spell = event.context.getSpell();
            HandlerCommonConfig c = HandlerCommonConfig.HANDLER.instance();
            event.damage = applySchoolDamage(schoolCaster, spell, event.damage,
                    RegistryPerks.ARS_HEDGEWITCH, SpellSchools.ELEMENTAL_WATER, c.arsHedgewitchDamagePercent);
            event.damage = applySchoolDamage(schoolCaster, spell, event.damage,
                    RegistryPerks.ARS_EMBERFORGED, SpellSchools.ELEMENTAL_FIRE, c.arsEmberforgedDamagePercent);
            event.damage = applySchoolDamage(schoolCaster, spell, event.damage,
                    RegistryPerks.ARS_STORMCALLER, SpellSchools.ELEMENTAL_AIR, c.arsStormcallerDamagePercent);
            event.damage = applySchoolDamage(schoolCaster, spell, event.damage,
                    RegistryPerks.ARS_GEOMANCER, SpellSchools.ELEMENTAL_EARTH, c.arsGeomancerDamagePercent);
            event.damage = applySchoolDamage(schoolCaster, spell, event.damage,
                    RegistryPerks.ARS_ABJURER, SpellSchools.ABJURATION, c.arsAbjurerPercent);
            event.damage = applySchoolDamage(schoolCaster, spell, event.damage,
                    RegistryPerks.ARS_ARCANE_WEAVER, SpellSchools.MANIPULATION, c.arsArcaneWeaverPercent);
        }
    }

    private static float applySchoolDamage(Player caster, Spell spell, float current,
                                           net.minecraftforge.registries.RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk,
                                           com.hollingsworth.arsnouveau.api.spell.SpellSchool school,
                                           int percent) {
        if (perk == null || !perk.get().isEnabled(caster)) return current;
        if (!spellContainsSchool(spell, school)) return current;
        return current * (1.0f + percent / 100.0f);
    }

    // ── Mana Cost Reduction: Arcane Efficiency Perk ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellCostCalc(SpellCostCalcEvent event) {
        if (event.context == null) return;
        if (!(event.context.getUnwrappedCaster() instanceof Player player) || player.isCreative()) return;

        if (RegistryPerks.ARCANE_EFFICIENCY != null && RegistryPerks.ARCANE_EFFICIENCY.get().isEnabled(player)) {
            int percent = HandlerCommonConfig.HANDLER.instance().arsArcaneEfficiencyPercent;
            int reducedCost = (int) (event.currentCost * (1.0 - percent / 100.0));
            event.currentCost = Math.max(reducedCost, 0);
        }

        // ── Phase 2b: Form Focus: Projectile / Self + Wild Manipulation ──
        Spell spell = event.context.getSpell();
        if (spell == null) return;
        AbstractCastMethod form = spell.getCastMethod();
        HandlerCommonConfig c = HandlerCommonConfig.HANDLER.instance();

        // Form Focus: Projectile — cost reduction for projectile-form spells
        if (form == MethodProjectile.INSTANCE
                && RegistryPerks.ARS_FORM_PROJECTILE != null
                && RegistryPerks.ARS_FORM_PROJECTILE.get().isEnabled(player)) {
            int reduced = (int) (event.currentCost * (1.0 - c.arsFormProjectilePercent / 100.0));
            event.currentCost = Math.max(reduced, 1);
        }

        // Form Focus: Self — cost reduction for self-form spells
        if (form == MethodSelf.INSTANCE
                && RegistryPerks.ARS_FORM_SELF != null
                && RegistryPerks.ARS_FORM_SELF.get().isEnabled(player)) {
            int reduced = (int) (event.currentCost * (1.0 - c.arsFormSelfPercent / 100.0));
            event.currentCost = Math.max(reduced, 1);
        }

        // Wild Manipulation — cost reduction for any spell containing a
        // Manipulation-school glyph. Floors at 1 so Archmage + Discount stacks
        // can't drive total cost below the single-point minimum (per design
        // doc guidance).
        if (RegistryPerks.ARS_WILD_MANIPULATION != null
                && RegistryPerks.ARS_WILD_MANIPULATION.get().isEnabled(player)
                && spellContainsSchool(spell, SpellSchools.MANIPULATION)) {
            int reduced = (int) (event.currentCost * (1.0 - c.arsWildManipulationPercent / 100.0));
            event.currentCost = Math.max(reduced, 1);
        }

        // ── Phase 2c: per-school cost reductions (Hedgewitch, Conjurer) ──
        if (RegistryPerks.ARS_HEDGEWITCH != null
                && RegistryPerks.ARS_HEDGEWITCH.get().isEnabled(player)
                && spellContainsSchool(spell, SpellSchools.ELEMENTAL_WATER)) {
            int reduced = (int) (event.currentCost * (1.0 - c.arsHedgewitchCostPercent / 100.0));
            event.currentCost = Math.max(reduced, 1);
        }
        if (RegistryPerks.ARS_CONJURER != null
                && RegistryPerks.ARS_CONJURER.get().isEnabled(player)
                && spellContainsSchool(spell, SpellSchools.CONJURATION)) {
            int reduced = (int) (event.currentCost * (1.0 - c.arsConjurerPercent / 100.0));
            event.currentCost = Math.max(reduced, 1);
        }
    }

    private static boolean spellContainsSchool(Spell spell,
                                               com.hollingsworth.arsnouveau.api.spell.SpellSchool school) {
        if (spell == null || spell.recipe == null) return false;
        for (AbstractSpellPart part : spell.recipe) {
            if (part == null || part.spellSchools == null) continue;
            if (part.spellSchools.contains(school)) return true;
        }
        return false;
    }

    // ── Mana Regeneration Bonus ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onManaRegenCalc(ManaRegenCalcEvent event) {
        if (!HandlerCommonConfig.HANDLER.instance().arsEnableManaRegen) return;
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;

        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        int magicLevel = cap.getSkillLevel(RegistrySkills.MAGIC.get());
        double bonus = magicLevel * HandlerCommonConfig.HANDLER.instance().arsManaRegenPerMagicLevel;
        if (bonus > 0) {
            event.setRegen(event.getRegen() + bonus);
        }

        // Cross-mod synergy: Intelligence adds secondary mana regen
        if (HandlerCommonConfig.HANDLER.instance().enableIntelligenceManaRegen) {
            int intLevel = cap.getSkillLevel(RegistrySkills.INTELLIGENCE.get());
            double intBonus = intLevel * HandlerCommonConfig.HANDLER.instance().intelligenceManaRegenPerLevel;
            if (intBonus > 0) {
                event.setRegen(event.getRegen() + intBonus);
            }
        }
    }

    // ── Max Mana Bonus ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMaxManaCalc(MaxManaCalcEvent event) {
        if (!HandlerCommonConfig.HANDLER.instance().arsEnableMaxManaBonus) return;
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;

        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        int magicLevel = cap.getSkillLevel(RegistrySkills.MAGIC.get());
        int intLevel = cap.getSkillLevel(RegistrySkills.INTELLIGENCE.get());
        int bonus = (int) (magicLevel * HandlerCommonConfig.HANDLER.instance().arsMaxManaPerMagicLevel
                + intLevel * HandlerCommonConfig.HANDLER.instance().arsMaxManaPerIntelligenceLevel);
        if (bonus > 0) {
            event.setMax(event.getMax() + bonus);
        }
    }

    // ── Familiar Gating ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onFamiliarSummon(FamiliarSummonEvent event) {
        if (!HandlerCommonConfig.HANDLER.instance().arsEnableFamiliarGating) return;
        if (!(event.owner instanceof Player player) || player.isCreative()) return;

        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        Skill magicSkill = RegistrySkills.MAGIC.get();
        int magicLevel = cap.getSkillLevel(magicSkill);
        int required = HandlerCommonConfig.HANDLER.instance().arsFamiliarRequiredMagicLevel;

        if (magicLevel < required) {
            if (player instanceof ServerPlayer) {
                player.sendSystemMessage(Component.translatable("overlay.runicskills.ars_familiar_gated",
                        Component.translatable(magicSkill.getKey()), required));
            }
            event.setCanceled(true);
        }
    }

    // ── Spell Modifier Enhancement: Glyph Mastery Perk ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellModifier(SpellModifierEvent event) {
        if (!(event.caster instanceof Player player) || player.isCreative()) return;

        if (RegistryPerks.GLYPH_MASTERY != null && RegistryPerks.GLYPH_MASTERY.get().isEnabled(player)) {
            double amplification = HandlerCommonConfig.HANDLER.instance().arsGlyphMasteryAmplification;
            event.builder.addAmplification(amplification);
        }
    }
}
