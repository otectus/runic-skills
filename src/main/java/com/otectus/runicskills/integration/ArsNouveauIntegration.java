package com.otectus.runicskills.integration;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import com.hollingsworth.arsnouveau.api.event.*;
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
