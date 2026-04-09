package com.otectus.runicskills.integration;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import io.redspace.ironsspellbooks.api.events.*;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

public class IronsSpellbooksIntegration {

    // School-to-attunement-perk mapping (lazy init to handle null RegistryObjects)
    private static Map<String, RegistryObject<com.otectus.runicskills.registry.perks.Perk>> schoolPerkMap;

    private static Map<String, RegistryObject<com.otectus.runicskills.registry.perks.Perk>> getSchoolPerkMap() {
        if (schoolPerkMap == null) {
            schoolPerkMap = new HashMap<>();
            if (RegistryPerks.FIRE_ATTUNEMENT != null) schoolPerkMap.put("fire", RegistryPerks.FIRE_ATTUNEMENT);
            if (RegistryPerks.ICE_ATTUNEMENT != null) schoolPerkMap.put("ice", RegistryPerks.ICE_ATTUNEMENT);
            if (RegistryPerks.LIGHTNING_ATTUNEMENT != null) schoolPerkMap.put("lightning", RegistryPerks.LIGHTNING_ATTUNEMENT);
            if (RegistryPerks.HOLY_ATTUNEMENT != null) schoolPerkMap.put("holy", RegistryPerks.HOLY_ATTUNEMENT);
            if (RegistryPerks.NATURE_ATTUNEMENT != null) schoolPerkMap.put("nature", RegistryPerks.NATURE_ATTUNEMENT);
            if (RegistryPerks.BLOOD_ATTUNEMENT != null) schoolPerkMap.put("blood", RegistryPerks.BLOOD_ATTUNEMENT);
            if (RegistryPerks.ENDER_ATTUNEMENT != null) schoolPerkMap.put("ender", RegistryPerks.ENDER_ATTUNEMENT);
            if (RegistryPerks.EVOCATION_ATTUNEMENT != null) schoolPerkMap.put("evocation", RegistryPerks.EVOCATION_ATTUNEMENT);
        }
        return schoolPerkMap;
    }

    // School-to-secondary-skill mapping for school-specific bonuses
    private static final Map<String, RegistryObject<Skill>> SCHOOL_SKILL_MAP = Map.of(
            "fire", RegistrySkills.STRENGTH,
            "ice", RegistrySkills.ENDURANCE,
            "lightning", RegistrySkills.DEXTERITY,
            "holy", RegistrySkills.WISDOM,
            "nature", RegistrySkills.CONSTITUTION,
            "blood", RegistrySkills.CONSTITUTION,
            "ender", RegistrySkills.INTELLIGENCE,
            "evocation", RegistrySkills.WISDOM
    );

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("irons_spellbooks");
    }

    // ── Phase 1: Spell Gating + Existing Lock Item Check ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellPreCast(SpellPreCastEvent event) {
        Player player = event.getEntity();

        String spellId = event.getSpellId();

        if (HandlerCommonConfig.HANDLER.instance().logSpellIds) {
            player.sendSystemMessage(Component.literal(String.format("[Runic Skills] >> Spell ID: %s", spellId)));
        }

        if (player.isCreative()) return;

        SkillCapability provider = SkillCapability.get(player);
        if (provider == null) return;

        // School Attunement: require the school's attunement perk
        if (HandlerCommonConfig.HANDLER.instance().ironsEnableSchoolAttunement) {
            AbstractSpell spell = SpellRegistry.getSpell(new ResourceLocation(spellId));
            SchoolType school = (spell != null) ? spell.getSchoolType() : null;
            if (school != null) {
                String schoolName = school.getId().getPath();
                RegistryObject<com.otectus.runicskills.registry.perks.Perk> attunementPerk = getSchoolPerkMap().get(schoolName);
                if (attunementPerk != null && !attunementPerk.get().isEnabled(player)) {
                    if (player instanceof ServerPlayer) {
                        player.sendSystemMessage(Component.translatable("overlay.runicskills.school_locked",
                                Component.translatable("school.runicskills." + schoolName)));
                    }
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // School-based gating: check Magic skill level against spell level formula
        if (HandlerCommonConfig.HANDLER.instance().ironsEnableSchoolGating) {
            Skill magicSkill = RegistrySkills.MAGIC.get();
            int magicLevel = provider.getSkillLevel(magicSkill);
            int spellLevel = event.getSpellLevel();
            int requiredLevel = (int) (HandlerCommonConfig.HANDLER.instance().ironsBaseSpellGatingLevel
                    + (spellLevel - 1) * HandlerCommonConfig.HANDLER.instance().ironsSpellLevelScaleFactor);

            if (magicLevel < requiredLevel) {
                if (player instanceof ServerPlayer) {
                    player.sendSystemMessage(Component.translatable("overlay.runicskills.spell_gated",
                            Component.translatable(magicSkill.getKey()), requiredLevel));
                }
                event.setCanceled(true);
                return;
            }
        }

        // Existing individual spell lock item check
        if (!provider.canUseSpecificID(player, spellId)) {
            event.setCanceled(true);
        }
    }

    // ── Phase 3: Mana Efficiency Perk ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellCast(SpellOnCastEvent event) {
        Player player = event.getEntity();
        if (player == null || player.isCreative()) return;

        // Mana Efficiency: reduce mana cost
        if (RegistryPerks.MANA_EFFICIENCY != null && RegistryPerks.MANA_EFFICIENCY.get().isEnabled(player)) {
            int percent = HandlerCommonConfig.HANDLER.instance().manaEfficiencyPercent;
            int reducedCost = (int) (event.getManaCost() * (1.0 - percent / 100.0));
            event.setManaCost(Math.max(reducedCost, 0));
        }

        // Spell Echo: probability-based mana refund
        if (RegistryPerks.SPELL_ECHO != null && RegistryPerks.SPELL_ECHO.get().isEnabled(player)) {
            int probability = HandlerCommonConfig.HANDLER.instance().spellEchoProbability;
            if (probability > 0) {
                int roll = (int) Math.floor(Math.random() * probability);
                if (roll == 0) {
                    int refund = (int) (event.getManaCost() * 0.5);
                    event.setManaCost(Math.max(event.getManaCost() - refund, 0));
                }
            }
        }
    }

    // ── Phase 3 + 4: Arcane Shield + Spell Damage Scaling ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellDamage(SpellDamageEvent event) {
        // Spell Damage Scaling: if the caster is a player, scale damage up
        if (HandlerCommonConfig.HANDLER.instance().ironsEnableSpellDamageScaling) {
            Entity sourceEntity = event.getSpellDamageSource().getEntity();
            if (sourceEntity instanceof Player caster && !caster.isCreative()) {
                SkillCapability casterCap = SkillCapability.get(caster);
                if (casterCap != null) {
                    int magicLevel = casterCap.getSkillLevel(RegistrySkills.MAGIC.get());
                    float bonus = (magicLevel - 1) * HandlerCommonConfig.HANDLER.instance().ironsSpellDamageScalePerLevel;
                    if (bonus > 0) {
                        event.setAmount(event.getAmount() * (1.0f + bonus));
                    }

                    // Cross-mod synergy: Wisdom adds a flat spell damage bonus
                    if (HandlerCommonConfig.HANDLER.instance().enableWisdomSpellDamageBonus) {
                        int wisdomLevel = casterCap.getSkillLevel(RegistrySkills.WISDOM.get());
                        float wisdomBonus = wisdomLevel * HandlerCommonConfig.HANDLER.instance().wisdomSpellDamagePerLevel;
                        if (wisdomBonus > 0) {
                            event.setAmount(event.getAmount() + wisdomBonus);
                        }
                    }

                    // School-specific secondary skill bonus
                    if (HandlerCommonConfig.HANDLER.instance().ironsEnableSchoolBonuses) {
                        SpellDamageSource spellDs = event.getSpellDamageSource();
                        if (spellDs != null && spellDs.spell() != null) {
                            SchoolType school = spellDs.spell().getSchoolType();
                            if (school != null) {
                                ResourceLocation schoolId = school.getId();
                                RegistryObject<Skill> secondarySkillObj = SCHOOL_SKILL_MAP.get(schoolId.getPath());
                                if (secondarySkillObj != null) {
                                    int secondaryLevel = casterCap.getSkillLevel(secondarySkillObj.get());
                                    float schoolBonus = secondaryLevel * HandlerCommonConfig.HANDLER.instance().ironsSchoolBonusPerLevel;
                                    if (schoolBonus > 0) {
                                        event.setAmount(event.getAmount() * (1.0f + schoolBonus));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Cross-mod synergy: Constitution provides passive spell defense
        if (event.getEntity() instanceof Player target && !target.isCreative()) {
            if (HandlerCommonConfig.HANDLER.instance().enableConstitutionSpellDefense) {
                SkillCapability targetCap = SkillCapability.get(target);
                if (targetCap != null) {
                    int conLevel = targetCap.getSkillLevel(RegistrySkills.CONSTITUTION.get());
                    float reduction = conLevel * HandlerCommonConfig.HANDLER.instance().constitutionSpellDefensePerLevel;
                    float maxReduction = HandlerCommonConfig.HANDLER.instance().maxConstitutionSpellDefense;
                    if (reduction > 0) {
                        event.setAmount(event.getAmount() * (1.0f - Math.min(reduction, maxReduction)));
                    }
                }
            }

            // Arcane Shield: perk-based reduction (stacks after Constitution passive)
            if (RegistryPerks.ARCANE_SHIELD != null && RegistryPerks.ARCANE_SHIELD.get().isEnabled(target)) {
                int percent = HandlerCommonConfig.HANDLER.instance().arcaneShieldPercent;
                event.setAmount(event.getAmount() * (1.0f - percent / 100.0f));
            }
        }
    }

    // ── Phase 5: Spell Level Bonuses ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onModifySpellLevel(ModifySpellLevelEvent event) {
        if (!HandlerCommonConfig.HANDLER.instance().ironsEnableSpellLevelBonus) return;
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;

        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        int magicLevel = cap.getSkillLevel(RegistrySkills.MAGIC.get());
        int threshold2 = HandlerCommonConfig.HANDLER.instance().ironsSpellLevelBonusThreshold2;
        int threshold1 = HandlerCommonConfig.HANDLER.instance().ironsSpellLevelBonusThreshold;

        if (magicLevel >= threshold2) {
            event.addLevels(2);
        } else if (magicLevel >= threshold1) {
            event.addLevels(1);
        }
    }

    // ── Phase 5: Mana Regeneration ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChangeMana(ChangeManaEvent event) {
        if (!HandlerCommonConfig.HANDLER.instance().ironsEnableManaRegen) return;

        // Only boost mana regeneration (when mana is going up), not mana spending
        if (event.getNewMana() <= event.getOldMana()) return;

        Player player = event.getEntity();
        if (player.isCreative()) return;

        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        int magicLevel = cap.getSkillLevel(RegistrySkills.MAGIC.get());
        float bonus = magicLevel * HandlerCommonConfig.HANDLER.instance().ironsManaRegenPerMagicLevel;
        if (bonus > 0) {
            event.setNewMana(event.getNewMana() + bonus);
        }

        // Cross-mod synergy: Intelligence adds secondary mana regen
        if (HandlerCommonConfig.HANDLER.instance().enableIntelligenceManaRegen) {
            int intLevel = cap.getSkillLevel(RegistrySkills.INTELLIGENCE.get());
            float intBonus = intLevel * HandlerCommonConfig.HANDLER.instance().intelligenceManaRegenPerLevel;
            if (intBonus > 0) {
                event.setNewMana(event.getNewMana() + intBonus);
            }
        }
    }
}
