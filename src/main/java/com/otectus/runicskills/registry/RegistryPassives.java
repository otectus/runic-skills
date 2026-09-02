package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.util.DisabledContentMatcher;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.integration.ApothicAttributesIntegration;
import com.otectus.runicskills.integration.ApothicPassiveHelper;
import com.otectus.runicskills.integration.ArsNouveauIntegration;
import com.otectus.runicskills.integration.ArsNouveauPassiveHelper;
import com.otectus.runicskills.integration.IronsSpellbooksIntegration;
import com.otectus.runicskills.integration.IronsSpellsPassiveHelper;
import com.otectus.runicskills.registry.skill.Skill;
import com.otectus.runicskills.registry.passive.Passive;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class RegistryPassives {
    public static final ResourceKey<Registry<Passive>> PASSIVES_KEY = ResourceKey.createRegistryKey(new ResourceLocation(RunicSkills.MOD_ID, "passives"));
    public static final DeferredRegister<Passive> PASSIVES = DeferredRegister.create(PASSIVES_KEY, RunicSkills.MOD_ID);
    // disableSync(): contents are config-derived and must not join the login handshake.
    // See RegistryPerks for the full rationale (RS-015).
    public static final Supplier<IForgeRegistry<Passive>> PASSIVES_REGISTRY = PASSIVES.makeRegistry(() -> new RegistryBuilder<Passive>().disableSaving().disableSync());

    /** See {@code RegistryPerks.REBUILDERS} — same reason, same mechanism. */
    private static final java.util.Map<String, Supplier<Passive>> REBUILDERS =
            new java.util.concurrent.ConcurrentHashMap<>();
    // Declared BEFORE the registration fields below, deliberately: static initialisers run in
    // textual order, so a map declared after them is still null when the first one calls
    // registerPassive() — which takes mod construction down with a NullPointerException.

    public static final RegistryObject<Passive> ATTACK_DAMAGE = registerPassive("attack_damage", () -> register("attack_damage", RegistrySkills.STRENGTH::get, HandlerResources.create("textures/skill/strength/passive_attack_damage.png"), Attributes.ATTACK_DAMAGE, "96a891fe-5919-418d-8205-f50464391500", HandlerCommonConfig.HANDLER.instance().attackDamageValue, HandlerCommonConfig.HANDLER.instance().attackPassiveLevels));

    public static final RegistryObject<Passive> ATTACK_KNOCKBACK = registerPassive("attack_knockback", () -> register("attack_knockback", RegistrySkills.STRENGTH::get, HandlerResources.create("textures/skill/strength/passive_attack_knockback.png"), Attributes.ATTACK_KNOCKBACK, "96a891fe-5919-418d-8205-f50464391501", HandlerCommonConfig.HANDLER.instance().attackKnockbackValue, HandlerCommonConfig.HANDLER.instance().attackKnockbackPassiveLevels));

    public static final RegistryObject<Passive> MAX_HEALTH = registerPassive("max_health", () -> register("max_health", RegistrySkills.CONSTITUTION::get, HandlerResources.create("textures/skill/constitution/passive_max_health.png"), Attributes.MAX_HEALTH, "96a891fe-5919-418d-8205-f50464391502", HandlerCommonConfig.HANDLER.instance().maxHealthValue, HandlerCommonConfig.HANDLER.instance().maxHealthPassiveLevels));

    public static final RegistryObject<Passive> KNOCKBACK_RESISTANCE = registerPassive("knockback_resistance", () -> register("knockback_resistance", RegistrySkills.CONSTITUTION::get, HandlerResources.create("textures/skill/constitution/passive_knockback_resistance.png"), Attributes.KNOCKBACK_RESISTANCE, "96a891fe-5919-418d-8205-f50464391503", HandlerCommonConfig.HANDLER.instance().knockbackResistanceValue, HandlerCommonConfig.HANDLER.instance().knockbackResistancePassiveLevels));

    public static final RegistryObject<Passive> MOVEMENT_SPEED = registerPassive("movement_speed", () -> register("movement_speed", RegistrySkills.DEXTERITY::get, HandlerResources.create("textures/skill/dexterity/passive_movement_speed.png"), Attributes.MOVEMENT_SPEED, "96a891fe-5919-418d-8205-f50464391504", HandlerCommonConfig.HANDLER.instance().movementSpeedValue, HandlerCommonConfig.HANDLER.instance().movementSpeedPassiveLevels));

    public static final RegistryObject<Passive> PROJECTILE_DAMAGE = registerPassive("projectile_damage", () -> register("projectile_damage", RegistrySkills.DEXTERITY::get, HandlerResources.create("textures/skill/dexterity/passive_projectile_damage.png"), RegistryAttributes.getEffectiveProjectileDamage(), "96a891fe-5919-418d-8205-f50464391505", HandlerCommonConfig.HANDLER.instance().projectileDamageValue, HandlerCommonConfig.HANDLER.instance().projectileDamagePassiveLevels));

    public static final RegistryObject<Passive> ARMOR = registerPassive("armor", () -> register("armor", RegistrySkills.ENDURANCE::get, HandlerResources.create("textures/skill/endurance/passive_armor.png"), Attributes.ARMOR, "96a891fe-5919-418d-8205-f50464391506", HandlerCommonConfig.HANDLER.instance().armorValue, HandlerCommonConfig.HANDLER.instance().armorPassiveLevels));

    public static final RegistryObject<Passive> ARMOR_TOUGHNESS = registerPassive("armor_toughness", () -> register("armor_toughness", RegistrySkills.ENDURANCE::get, HandlerResources.create("textures/skill/endurance/passive_armor_toughness.png"), Attributes.ARMOR_TOUGHNESS, "96a891fe-5919-418d-8205-f50464391507", HandlerCommonConfig.HANDLER.instance().armorToughnessValue, HandlerCommonConfig.HANDLER.instance().armorToughnessPassiveLevels));

    public static final RegistryObject<Passive> ATTACK_SPEED = registerPassive("attack_speed", () -> register("attack_speed", RegistrySkills.INTELLIGENCE::get, HandlerResources.create("textures/skill/intelligence/passive_attack_speed.png"), Attributes.ATTACK_SPEED, "96a891fe-5919-418d-8205-f50464391508", HandlerCommonConfig.HANDLER.instance().attackSpeedValue, HandlerCommonConfig.HANDLER.instance().attackSpeedPassiveLevels));

    public static final RegistryObject<Passive> ENTITY_REACH = registerPassive("entity_reach", () -> register("entity_reach", RegistrySkills.INTELLIGENCE::get, HandlerResources.create("textures/skill/intelligence/passive_entity_reach.png"), ForgeMod.ENTITY_REACH.get(), "96a891fe-5919-418d-8205-f50464391509", HandlerCommonConfig.HANDLER.instance().entityReachValue, HandlerCommonConfig.HANDLER.instance().entityReachPassiveLevels));

    // Building passives
    public static final RegistryObject<Passive> BLOCK_REACH = registerPassive("block_reach", () -> register("block_reach", RegistrySkills.BUILDING::get, HandlerResources.create("textures/skill/building/passive_block_reach.png"), ForgeMod.BLOCK_REACH.get(), "96a891fe-5919-418d-8205-f50464391510", HandlerCommonConfig.HANDLER.instance().blockReachValue, HandlerCommonConfig.HANDLER.instance().blockReachPassiveLevels));

    public static final RegistryObject<Passive> BREAK_SPEED = registerPassive("break_speed", () -> register("break_speed", RegistrySkills.BUILDING::get, HandlerResources.create("textures/skill/building/passive_break_speed.png"), RegistryAttributes.getEffectiveBreakSpeed(), "96a891fe-5919-418d-8205-f50464391511", HandlerCommonConfig.HANDLER.instance().breakSpeedValue, HandlerCommonConfig.HANDLER.instance().breakSpeedPassiveLevels));

    // Wisdom base passives
    public static final RegistryObject<Passive> ENCHANTING_POWER = registerPassive("enchanting_power", () -> register("enchanting_power", RegistrySkills.WISDOM::get, HandlerResources.create("textures/skill/wisdom/passive_enchanting_power.png"), RegistryAttributes.ENCHANTING_POWER.get(), "96a891fe-5919-418d-8205-f50464391540", HandlerCommonConfig.HANDLER.instance().enchantingPowerValue, HandlerCommonConfig.HANDLER.instance().enchantingPowerPassiveLevels));

    public static final RegistryObject<Passive> XP_BONUS = registerPassive("xp_bonus", () -> register("xp_bonus", RegistrySkills.WISDOM::get, HandlerResources.create("textures/skill/wisdom/passive_xp_bonus.png"), RegistryAttributes.XP_BONUS.get(), "96a891fe-5919-418d-8205-f50464391541", HandlerCommonConfig.HANDLER.instance().xpBonusValue, HandlerCommonConfig.HANDLER.instance().xpBonusPassiveLevels));

    public static final RegistryObject<Passive> BENEFICIAL_EFFECT = registerPassive("beneficial_effect", () -> register("beneficial_effect", RegistrySkills.MAGIC::get, HandlerResources.create("textures/skill/magic/passive_beneficial_effect.png"), RegistryAttributes.BENEFICIAL_EFFECT.get(), "96a891fe-5919-418d-8205-f50464391512", HandlerCommonConfig.HANDLER.instance().beneficialEffectValue, HandlerCommonConfig.HANDLER.instance().beneficialEffectPassiveLevels));

    public static final RegistryObject<Passive> MAGIC_RESIST = registerPassive("magic_resist", () -> register("magic_resist", RegistrySkills.MAGIC::get, HandlerResources.create("textures/skill/magic/passive_magic_resist.png"), RegistryAttributes.MAGIC_RESIST.get(), "96a891fe-5919-418d-8205-f50464391513", HandlerCommonConfig.HANDLER.instance().magicResistValue, HandlerCommonConfig.HANDLER.instance().magicResistPassiveLevels));

    public static final RegistryObject<Passive> CRITICAL_DAMAGE = registerPassive("critical_damage", () -> register("critical_damage", RegistrySkills.FORTUNE::get, HandlerResources.create("textures/skill/fortune/passive_critical_damage.png"), RegistryAttributes.getEffectiveCritDamage(), "96a891fe-5919-418d-8205-f50464391515", HandlerCommonConfig.HANDLER.instance().criticalDamageValue, HandlerCommonConfig.HANDLER.instance().criticalDamagePassiveLevels));

    public static final RegistryObject<Passive> FORTUNE = registerPassive("fortune", () -> register("fortune", RegistrySkills.FORTUNE::get, HandlerResources.create("textures/skill/fortune/passive_luck.png"), Attributes.LUCK, "96a891fe-5919-418d-8205-f50464391514", HandlerCommonConfig.HANDLER.instance().luckValue, HandlerCommonConfig.HANDLER.instance().luckPassiveLevels));

    // Iron's Spells 'n Spellbooks Integration - Conditional passives
    public static final RegistryObject<Passive> SPELL_POWER = !IronsSpellbooksIntegration.isModLoaded() ? null : registerPassive("spell_power", IronsSpellsPassiveHelper::createSpellPowerPassive);
    public static final RegistryObject<Passive> MAX_MANA = !IronsSpellbooksIntegration.isModLoaded() ? null : registerPassive("max_mana", IronsSpellsPassiveHelper::createMaxManaPassive);
    public static final RegistryObject<Passive> CAST_TIME_REDUCTION = !IronsSpellbooksIntegration.isModLoaded() ? null : registerPassive("cast_time_reduction", IronsSpellsPassiveHelper::createCastTimeReductionPassive);

    // Ars Nouveau Integration - Conditional passives
    public static final RegistryObject<Passive> ARS_SPELL_DAMAGE = !ArsNouveauIntegration.isModLoaded() ? null : registerPassive("ars_spell_damage", ArsNouveauPassiveHelper::createArsSpellDamagePassive);
    public static final RegistryObject<Passive> ARS_FLAT_MANA = !ArsNouveauIntegration.isModLoaded() ? null : registerPassive("ars_flat_mana", ArsNouveauPassiveHelper::createArsFlatManaPassive);
    public static final RegistryObject<Passive> ARS_WARDING = !ArsNouveauIntegration.isModLoaded() ? null : registerPassive("ars_warding", ArsNouveauPassiveHelper::createArsWardingPassive);

    // Apothic Attributes Integration - Conditional passives
    public static final RegistryObject<Passive> APOTHIC_LIFE_STEAL = !ApothicAttributesIntegration.isModLoaded() ? null : registerPassive("life_steal", ApothicPassiveHelper::createLifeStealPassive);
    public static final RegistryObject<Passive> APOTHIC_HEALING_RECEIVED = !ApothicAttributesIntegration.isModLoaded() ? null : registerPassive("healing_received", ApothicPassiveHelper::createHealingReceivedPassive);
    public static final RegistryObject<Passive> APOTHIC_DRAW_SPEED = !ApothicAttributesIntegration.isModLoaded() ? null : registerPassive("draw_speed", ApothicPassiveHelper::createDrawSpeedPassive);
    public static final RegistryObject<Passive> APOTHIC_DODGE_CHANCE = !ApothicAttributesIntegration.isModLoaded() ? null : registerPassive("dodge_chance", ApothicPassiveHelper::createDodgeChancePassive);
    public static final RegistryObject<Passive> APOTHIC_EXPERIENCE_GAINED = !ApothicAttributesIntegration.isModLoaded() ? null : registerPassive("experience_gained", ApothicPassiveHelper::createExperienceGainedPassive);
    public static final RegistryObject<Passive> APOTHIC_MINING_SPEED = !ApothicAttributesIntegration.isModLoaded() ? null : registerPassive("mining_speed", ApothicPassiveHelper::createMiningSpeedPassive);
    public static final RegistryObject<Passive> APOTHIC_COLD_DAMAGE = !ApothicAttributesIntegration.isModLoaded() ? null : registerPassive("cold_damage", ApothicPassiveHelper::createColdDamagePassive);
    public static final RegistryObject<Passive> APOTHIC_CRIT_CHANCE = !ApothicAttributesIntegration.isModLoaded() ? null : registerPassive("crit_chance", ApothicPassiveHelper::createCritChancePassive);
    public static final RegistryObject<Passive> APOTHIC_FIRE_DAMAGE = !ApothicAttributesIntegration.isModLoaded() ? null : registerPassive("fire_damage", ApothicPassiveHelper::createFireDamagePassive);
    public static final RegistryObject<Passive> APOTHIC_ARROW_VELOCITY = !ApothicAttributesIntegration.isModLoaded() ? null : registerPassive("arrow_velocity", ApothicPassiveHelper::createArrowVelocityPassive);
    public static final RegistryObject<Passive> APOTHIC_ARMOR_PIERCE = !ApothicAttributesIntegration.isModLoaded() ? null : registerPassive("armor_pierce", ApothicPassiveHelper::createArmorPiercePassive);

    // Forge native attributes
    public static final RegistryObject<Passive> SWIM_SPEED = registerPassive("swim_speed", () -> register("swim_speed", RegistrySkills.CONSTITUTION::get, HandlerResources.create("textures/skill/constitution/passive_swim_speed.png"), ForgeMod.SWIM_SPEED.get(), "96a891fe-5919-418d-8205-f50464391553", HandlerCommonConfig.HANDLER.instance().swimSpeedValue, HandlerCommonConfig.HANDLER.instance().swimSpeedPassiveLevels));

    // Tinkering skill passives
    public static final RegistryObject<Passive> REPAIR_EFFICIENCY = registerPassive("repair_efficiency", () -> register("repair_efficiency", RegistrySkills.TINKERING::get, HandlerResources.create("textures/skill/tinkering/passive_repair_efficiency.png"), RegistryAttributes.REPAIR_EFFICIENCY.get(), "96a891fe-5919-418d-8205-f50464391560", HandlerCommonConfig.HANDLER.instance().repairEfficiencyValue, HandlerCommonConfig.HANDLER.instance().repairEfficiencyPassiveLevels));
    public static final RegistryObject<Passive> CRAFTING_LUCK = registerPassive("crafting_luck", () -> register("crafting_luck", RegistrySkills.TINKERING::get, HandlerResources.create("textures/skill/tinkering/passive_crafting_luck.png"), RegistryAttributes.CRAFTING_LUCK.get(), "96a891fe-5919-418d-8205-f50464391561", HandlerCommonConfig.HANDLER.instance().craftingLuckValue, HandlerCommonConfig.HANDLER.instance().craftingLuckPassiveLevels));

    private static Passive register(String name, Supplier<Skill> skillSupplier, ResourceLocation texture, Attribute attribute, String attributeUuid, Object attributeValue, int... levelsRequired) {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, name);
        return new Passive(key, skillSupplier, texture, attribute, attributeUuid, attributeValue, levelsRequired);
    }


    /** Registers a passive and records how to rebuild it from the current configuration. */
    private static RegistryObject<Passive> registerPassive(String path, Supplier<Passive> factory) {
        REBUILDERS.put(path, factory);
        return PASSIVES.register(path, factory);
    }

    /**
     * Re-reads every passive's per-level value and requirement array from the configuration in
     * force right now, so a reload changes the levels players can actually buy rather than only
     * the numbers the attribute pass happens to read live (RS10-005).
     *
     * @return how many passives were refreshed
     */
    public static int refreshFromConfig() {
        int refreshed = 0;
        for (Passive passive : getCachedValues()) {
            Supplier<Passive> factory = REBUILDERS.get(passive.getName());
            if (factory == null) continue;
            try {
                passive.adoptTunables(factory.get());
                refreshed++;
            } catch (RuntimeException e) {
                RunicSkills.getLOGGER().warn("Could not refresh passive {} from config: {}",
                        passive.getName(), e.toString());
            }
        }
        return refreshed;
    }

    public static void load(IEventBus eventBus) {
        PASSIVES.register(eventBus);
    }

    private static volatile List<Passive> cachedValues;
    private static volatile Map<String, Passive> cachedByName;

    public static List<Passive> getCachedValues() {
        if (cachedValues == null) {
            cachedValues = List.copyOf(PASSIVES_REGISTRY.get().getValues());
        }
        return cachedValues;
    }

    @org.jetbrains.annotations.Nullable
    public static Passive getPassive(String passiveName) {
        if (cachedByName == null) {
            cachedByName = getCachedValues().stream()
                    .collect(Collectors.toUnmodifiableMap(Passive::getName, Passive::get));
        }
        return cachedByName.get(passiveName);
    }

    // Disabled-via-config support. Accepts either a bare registry path ("attack_damage") or
    // a full id ("runicskills:attack_damage"); matches both against the disabledPassives list.
    public static boolean isDisabled(String passiveName) {
        return DisabledContentMatcher.matches(passiveName, RunicSkills.MOD_ID,
                HandlerCommonConfig.HANDLER.instance().disabledPassives);
    }

    public static boolean isDisabled(Passive passive) {
        if (passive == null) return false;
        if (isDisabled(passive.getName())) return true;
        return isDisabled(passive.getMod() + ":" + passive.getName());
    }

    // UI visibility: hidden only when disabled AND hideDisabledPassives is on.
    public static boolean isHiddenFromUi(Passive passive) {
        return HandlerCommonConfig.HANDLER.instance().hideDisabledPassives && isDisabled(passive);
    }
}


