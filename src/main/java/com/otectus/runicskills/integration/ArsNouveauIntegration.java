package com.otectus.runicskills.integration;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.packet.client.NoticeOverlayCP;
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
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

public class ArsNouveauIntegration {

    /**
     * Whether this integration should do anything right now: Ars Nouveau is installed
     * <em>and</em> {@code enableArsNouveauIntegration} is on in the configuration in force.
     *
     * <p>The toggle used to be read once, in the mod constructor, to decide whether to register
     * this subscriber at all — so turning it off on a running server left the handlers registered
     * and firing, and turning it on could not register a subscriber that had been skipped
     * (RS10-011). The adapter is now registered whenever its upstream mod is present and every
     * entry point asks this instead, which makes the toggle work live in both directions.
     */
    public static boolean isActive() {
        return isModLoaded() && HandlerCommonConfig.HANDLER.instance().enableArsNouveauIntegration;
    }


    public static boolean isModLoaded() {
        return ModList.get().isLoaded("ars_nouveau");
    }

    // ── Spell Gating: gate spells by glyph count ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellResolve(SpellResolveEvent.Pre event) {
        if (!isActive()) return;
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
                // Over-GUI banner (was sendSystemMessage -> chat, hidden behind open screens).
                NoticeOverlayCP.send(player, "overlay.runicskills.ars_spell_gated",
                        magicSkill.getKey(), String.valueOf(requiredLevel));
                event.setCanceled(true);
            }
        }
    }

    // ── Spell Damage Scaling + Arcane Ward ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellDamage(SpellDamageEvent.Pre event) {
        if (!isActive()) return;
        // Two independent bonuses, each behind its own switch. The Wisdom synergy used to sit
        // inside arsEnableSpellDamageScaling, which describes the Magic-level scaling and nothing
        // else, so turning that off silently took the synergy with it (MEDIUM-02).
        if (event.caster instanceof Player caster && !caster.isCreative()) {
            SkillCapability casterCap = SkillCapability.get(caster);
            if (casterCap != null) {
                // Scale outgoing damage for caster
                if (HandlerCommonConfig.HANDLER.instance().arsEnableSpellDamageScaling) {
                    int magicLevel = casterCap.getSkillLevel(RegistrySkills.MAGIC.get());
                    float bonus = (magicLevel - 1) * HandlerCommonConfig.HANDLER.instance().arsSpellDamageScalePerLevel;
                    if (bonus > 0) {
                        event.damage = event.damage * (1.0f + bonus);
                    }
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
            // B7 fix: read Hedgewitch damage% from perk.getActiveValue (Value[1]) so
            // the same source-of-truth flows into tooltips and gameplay.
            int hedgewitchDmg = RegistryPerks.ARS_HEDGEWITCH != null
                    ? (int) RegistryPerks.ARS_HEDGEWITCH.get().getActiveValue(schoolCaster)[1]
                    : 0;
            event.damage = applySchoolDamage(schoolCaster, spell, event.damage,
                    RegistryPerks.ARS_HEDGEWITCH, SpellSchools.ELEMENTAL_WATER, hedgewitchDmg);
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

    // ════════════════════════════════════════════════════════════════════════
    // ── Phase 3: Schoolbridges + Unified Arcana ──
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Schoolbridges — read the caster's ISS per-school spell_power attribute
     * and bleed a configurable fraction of it into the matching Ars school's
     * damage multiplier. Gated on ISS being loaded; if it isn't, the perks
     * are null-registered upstream so this branch never fires.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSpellDamageSchoolbridge(SpellDamageEvent.Pre event) {
        if (!isActive()) return;
        if (!IronsSpellbooksIntegration.isModLoaded()) return;
        if (!(event.caster instanceof Player caster) || caster.isCreative()) return;
        if (event.context == null || event.context.getSpell() == null) return;
        Spell spell = event.context.getSpell();
        HandlerCommonConfig c = HandlerCommonConfig.HANDLER.instance();

        event.damage = applyBridge(caster, spell, event.damage,
                RegistryPerks.SCHOOLBRIDGE_FIRE, SpellSchools.ELEMENTAL_FIRE,
                io.redspace.ironsspellbooks.api.registry.AttributeRegistry.FIRE_SPELL_POWER.get(),
                c.xSchoolbridgeFirePercent);
        event.damage = applyBridge(caster, spell, event.damage,
                RegistryPerks.SCHOOLBRIDGE_WATER, SpellSchools.ELEMENTAL_WATER,
                io.redspace.ironsspellbooks.api.registry.AttributeRegistry.ICE_SPELL_POWER.get(),
                c.xSchoolbridgeWaterPercent);
        event.damage = applyBridge(caster, spell, event.damage,
                RegistryPerks.SCHOOLBRIDGE_AIR, SpellSchools.ELEMENTAL_AIR,
                io.redspace.ironsspellbooks.api.registry.AttributeRegistry.LIGHTNING_SPELL_POWER.get(),
                c.xSchoolbridgeAirPercent);
        event.damage = applyBridge(caster, spell, event.damage,
                RegistryPerks.SCHOOLBRIDGE_EARTH, SpellSchools.ELEMENTAL_EARTH,
                io.redspace.ironsspellbooks.api.registry.AttributeRegistry.NATURE_SPELL_POWER.get(),
                c.xSchoolbridgeEarthPercent);
        event.damage = applyBridge(caster, spell, event.damage,
                RegistryPerks.SCHOOLBRIDGE_ABJ, SpellSchools.ABJURATION,
                io.redspace.ironsspellbooks.api.registry.AttributeRegistry.HOLY_SPELL_POWER.get(),
                c.xSchoolbridgeAbjPercent);
        event.damage = applyBridge(caster, spell, event.damage,
                RegistryPerks.SCHOOLBRIDGE_MANIP, SpellSchools.MANIPULATION,
                io.redspace.ironsspellbooks.api.registry.AttributeRegistry.ENDER_SPELL_POWER.get(),
                c.xSchoolbridgeManipPercent);
    }

    private static float applyBridge(Player caster, Spell spell, float current,
                                     net.minecraftforge.registries.RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk,
                                     com.hollingsworth.arsnouveau.api.spell.SpellSchool school,
                                     net.minecraft.world.entity.ai.attributes.Attribute issAttr,
                                     int percent) {
        if (perk == null || !perk.get().isEnabled(caster)) return current;
        if (!spellContainsSchool(spell, school)) return current;
        var inst = caster.getAttribute(issAttr);
        if (inst == null) return current;
        // ISS *_spell_power attributes are unit percentages (0.20 = +20%).
        float issValue = (float) inst.getValue();
        float bleed = issValue * (percent / 100.0f);
        return current * (1.0f + bleed);
    }

    /**
     * Unified Arcana — on a successful Ars cast, refund a percent of the
     * Source cost to the caster's ISS mana pool. Effectively lets a
     * high-level player top-up ISS mana via Ars casts.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onSpellResolveUnifiedArcana(SpellResolveEvent.Post event) {
        if (!isActive()) return;
        if (!IronsSpellbooksIntegration.isModLoaded()) return;
        if (!(event.shooter instanceof Player caster) || caster.isCreative()) return;
        if (RegistryPerks.UNIFIED_ARCANA == null
                || !RegistryPerks.UNIFIED_ARCANA.get().isEnabled(caster)) return;

        int cost = event.spell != null ? event.spell.getCost() : 0;
        if (cost <= 0) return;
        float refund = cost * (HandlerCommonConfig.HANDLER.instance().xUnifiedArcanaPercent / 100.0f);
        if (refund <= 0) return;
        io.redspace.ironsspellbooks.api.magic.MagicData magic =
                io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(caster);
        if (magic != null) magic.addMana(refund);
    }

    // ── Mana Cost Reduction: Arcane Efficiency Perk ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellCostCalc(SpellCostCalcEvent event) {
        if (!isActive()) return;
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
        // B7 fix: Hedgewitch cost% read from perk.getActiveValue (Value[0]).
        if (RegistryPerks.ARS_HEDGEWITCH != null
                && RegistryPerks.ARS_HEDGEWITCH.get().isEnabled(player)
                && spellContainsSchool(spell, SpellSchools.ELEMENTAL_WATER)) {
            double hwCostPercent = RegistryPerks.ARS_HEDGEWITCH.get().getActiveValue(player)[0];
            int reduced = (int) (event.currentCost * (1.0 - hwCostPercent / 100.0));
            event.currentCost = Math.max(reduced, 1);
        }
        if (RegistryPerks.ARS_CONJURER != null
                && RegistryPerks.ARS_CONJURER.get().isEnabled(player)
                && spellContainsSchool(spell, SpellSchools.CONJURATION)) {
            int reduced = (int) (event.currentCost * (1.0 - c.arsConjurerPercent / 100.0));
            event.currentCost = Math.max(reduced, 1);
        }

        // Arcane Scholar - "Ars Nouveau spell complexity limit increased".
        //
        // The limit itself is not per-player and cannot be: Ars validates spell length with a
        // validator built once from the server config, so there is nothing for one player's perk to
        // raise. What a scholar can be given instead is the thing the limit exists to ration -
        // complexity is expensive, and long spells are what the cost curve punishes. Each glyph
        // beyond the first now costs the scholar less, so the same spellbook reaches further on the
        // same pool, and the tooltip says exactly that.
        if (RegistryPerks.ARCANE_SCHOLAR != null
                && RegistryPerks.ARCANE_SCHOLAR.get().isEnabled(player)
                && spell.recipe != null) {
            int glyphs = Math.max(0, spell.recipe.size() - 1);
            int perGlyph = Math.round(c.arcaneScholarAmplifier);
            if (glyphs > 0 && perGlyph > 0) {
                event.currentCost = Math.max(event.currentCost - glyphs * perGlyph, 1);
            }
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
        if (!isActive()) return;
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;

        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        // arsEnableManaRegen names the Magic-level bonus, so it gates only that. The Intelligence
        // synergy has a switch of its own and Source Well is a perk; both used to be unreachable
        // whenever this one switch was off (MEDIUM-02).
        if (HandlerCommonConfig.HANDLER.instance().arsEnableManaRegen) {
            int magicLevel = cap.getSkillLevel(RegistrySkills.MAGIC.get());
            double bonus = magicLevel * HandlerCommonConfig.HANDLER.instance().arsManaRegenPerMagicLevel;
            if (bonus > 0) {
                event.setRegen(event.getRegen() + bonus);
            }
        }

        // Cross-mod synergy: Intelligence adds secondary mana regen
        if (HandlerCommonConfig.HANDLER.instance().enableIntelligenceManaRegen) {
            int intLevel = cap.getSkillLevel(RegistrySkills.INTELLIGENCE.get());
            double intBonus = intLevel * HandlerCommonConfig.HANDLER.instance().intelligenceManaRegenPerLevel;
            if (intBonus > 0) {
                event.setRegen(event.getRegen() + intBonus);
            }
        }

        // Source Well - "Ars Nouveau source generation increased". Ars calls the pool mana and the
        // player calls it source; this is the rate at which it refills, which is what generation
        // means. Applied as a share of whatever the rate already is, so it compounds correctly with
        // the level-scaled bonuses above rather than swamping them with a flat number.
        if (RegistryPerks.SOURCE_WELL != null && RegistryPerks.SOURCE_WELL.get().isEnabled(player)) {
            double share = HandlerCommonConfig.HANDLER.instance().sourceWellPercent / 100.0;
            if (share > 0) event.setRegen(event.getRegen() * (1.0 + share));
        }
    }

    // ── Max Mana Bonus ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMaxManaCalc(MaxManaCalcEvent event) {
        if (!isActive()) return;
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;

        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        // arsEnableMaxManaBonus names the level-scaled pool bonus. Source Attunement is a perk and
        // runs on its own enablement below, rather than being switched off with it (MEDIUM-02).
        if (HandlerCommonConfig.HANDLER.instance().arsEnableMaxManaBonus) {
            int magicLevel = cap.getSkillLevel(RegistrySkills.MAGIC.get());
            int intLevel = cap.getSkillLevel(RegistrySkills.INTELLIGENCE.get());
            int bonus = (int) (magicLevel * HandlerCommonConfig.HANDLER.instance().arsMaxManaPerMagicLevel
                    + intLevel * HandlerCommonConfig.HANDLER.instance().arsMaxManaPerIntelligenceLevel);
            if (bonus > 0) {
                event.setMax(event.getMax() + bonus);
            }
        }

        // Source Attunement - "Ars Nouveau source pool increased". Applied after the level bonuses
        // and to the total, so the perk enlarges the pool the player has actually built rather than
        // a share of Ars's base figure.
        if (RegistryPerks.SOURCE_ATTUNEMENT != null
                && RegistryPerks.SOURCE_ATTUNEMENT.get().isEnabled(player)) {
            double share = HandlerCommonConfig.HANDLER.instance().sourceAttunementPercent / 100.0;
            if (share > 0) event.setMax((int) Math.round(event.getMax() * (1.0 + share)));
        }
    }

    // ── Familiar Gating ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onFamiliarSummon(FamiliarSummonEvent event) {
        if (!isActive()) return;
        if (!HandlerCommonConfig.HANDLER.instance().arsEnableFamiliarGating) return;
        if (!(event.owner instanceof Player player) || player.isCreative()) return;

        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        Skill magicSkill = RegistrySkills.MAGIC.get();
        int magicLevel = cap.getSkillLevel(magicSkill);
        int required = HandlerCommonConfig.HANDLER.instance().arsFamiliarRequiredMagicLevel;

        if (magicLevel < required) {
            // Over-GUI banner (was sendSystemMessage -> chat, hidden behind open screens).
            NoticeOverlayCP.send(player, "overlay.runicskills.ars_familiar_gated",
                    magicSkill.getKey(), String.valueOf(required));
            event.setCanceled(true);
        }
    }

    // ── Spell Modifier Enhancement: Glyph Mastery Perk ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellModifier(SpellModifierEvent event) {
        if (!isActive()) return;
        if (!(event.caster instanceof Player player) || player.isCreative()) return;

        if (RegistryPerks.GLYPH_MASTERY != null && RegistryPerks.GLYPH_MASTERY.get().isEnabled(player)) {
            double amplification = HandlerCommonConfig.HANDLER.instance().arsGlyphMasteryAmplification;
            event.builder.addAmplification(amplification);
        }

        // Ward Master - "Protective wards last longer". Ars's protective magic is the Abjuration
        // school, and this event fires once per glyph with that glyph's own schools attached, so
        // the extension lands on the warding part of a spell and not on whatever else is bolted to
        // it. Duration is the modifier that matters: a ward's whole value is how long it stands.
        if (RegistryPerks.WARD_MASTER != null && RegistryPerks.WARD_MASTER.get().isEnabled(player)
                && event.spellPart != null && event.spellPart.spellSchools != null
                && event.spellPart.spellSchools.contains(SpellSchools.ABJURATION)) {
            double longer = HandlerCommonConfig.HANDLER.instance().wardMasterPercent / 100.0;
            if (longer > 0) event.builder.addDurationModifier(longer);
        }
    }

    // -- Familiars --

    /**
     * The three familiar perks, all of which are about a creature you summoned rather than about
     * you: Familiar Bond and Golem Commander on what it deals, Ars Savant on what it survives.
     *
     * <p>Ars familiars implement {@link com.hollingsworth.arsnouveau.api.familiar.IFamiliar} and
     * carry their owner's UUID, so "your familiar" is a question the mod already answers - no
     * proximity guessing and no ownership heuristics. A familiar belonging to another player is
     * correctly untouched.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onFamiliarCombat(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!isActive()) return;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();

        // Attacking side: a familiar you own is hitting something.
        if (event.getSource().getEntity() instanceof com.hollingsworth.arsnouveau.api.familiar.IFamiliar attacker) {
            Player owner = ownerOf(attacker);
            if (owner != null) {
                double bonus = 0.0;
                if (RegistryPerks.FAMILIAR_BOND != null
                        && RegistryPerks.FAMILIAR_BOND.get().isEnabled(owner)) {
                    bonus += config.familiarBondPercent / 100.0;
                }
                // "Golem familiars" - matched on the familiar's own registry id, so the amethyst
                // golem and any addon's golem both count without naming either.
                if (RegistryPerks.GOLEM_COMMANDER != null
                        && RegistryPerks.GOLEM_COMMANDER.get().isEnabled(owner)
                        && isGolem(attacker)) {
                    bonus += config.golemCommanderPercent / 100.0;
                }
                if (bonus > 0) event.setAmount((float) (event.getAmount() * (1.0 + bonus)));
            }
        }

        // Defending side: something is hitting a familiar you own.
        if (event.getEntity() instanceof com.hollingsworth.arsnouveau.api.familiar.IFamiliar victim) {
            Player owner = ownerOf(victim);
            if (owner != null && RegistryPerks.ARS_SAVANT != null
                    && RegistryPerks.ARS_SAVANT.get().isEnabled(owner)) {
                // "Familiar abilities improved" named no ability in particular, and Ars gives each
                // familiar a different one - a starbuncle fetches, a drygmy harvests, a wixie
                // brews - with no shared number to raise. What every familiar has in common is that
                // it dies and has to be re-summoned, so the perk buys the one improvement that
                // helps all of them: staying alive long enough to keep doing whatever they do.
                double reduction = Math.min(0.90, config.arsSavantPercent / 100.0);
                if (reduction > 0) event.setAmount((float) (event.getAmount() * (1.0 - reduction)));
            }
        }
    }

    /** The player who owns a familiar, or {@code null} if they are absent or it is unowned. */
    private static Player ownerOf(com.hollingsworth.arsnouveau.api.familiar.IFamiliar familiar) {
        java.util.UUID ownerId = familiar.getOwnerID();
        if (ownerId == null) return null;
        net.minecraft.world.entity.Entity self = familiar.getThisEntity();
        if (self == null || self.level() == null) return null;
        return self.level().getPlayerByUUID(ownerId);
    }

    private static boolean isGolem(com.hollingsworth.arsnouveau.api.familiar.IFamiliar familiar) {
        net.minecraft.world.entity.Entity self = familiar.getThisEntity();
        if (self == null) return false;
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(self.getType());
        return id != null && id.getPath().contains("golem");
    }
}
