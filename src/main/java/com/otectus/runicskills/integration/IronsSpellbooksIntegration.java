package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import io.redspace.ironsspellbooks.api.events.*;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
            if (RegistryPerks.ELDRITCH_ATTUNEMENT != null) schoolPerkMap.put("eldritch", RegistryPerks.ELDRITCH_ATTUNEMENT);
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

        // Spellweaver: every Nth cast inside the combo window is free. The counter
        // increments on every cast; reaching N zeroes the cost and resets the counter.
        if (RegistryPerks.SPELLWEAVER != null && RegistryPerks.SPELLWEAVER.get().isEnabled(player)) {
            UUID uuid = player.getUUID();
            long now = player.level().getGameTime();
            int windowTicks = HandlerCommonConfig.HANDLER.instance().spellweaverComboWindow * 20;
            int required = Math.max(2, HandlerCommonConfig.HANDLER.instance().spellweaverComboCount);

            long last = spellweaverLastCast.getOrDefault(uuid, Long.MIN_VALUE);
            int count = (now - last <= windowTicks) ? spellweaverCount.getOrDefault(uuid, 0) + 1 : 1;
            spellweaverLastCast.put(uuid, now);

            if (count >= required) {
                event.setManaCost(0);
                count = 0;
            }
            spellweaverCount.put(uuid, count);
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

        // Phase 1a caster-side modifiers (Resonant Casting + Long Channel).
        Entity sourceEntity2 = event.getSpellDamageSource().getEntity();
        if (sourceEntity2 instanceof Player caster && !caster.isCreative()) {
            // Resonant Casting: while above mana threshold, +damage.
            if (RegistryPerks.RESONANT_CASTING != null && RegistryPerks.RESONANT_CASTING.get().isEnabled(caster)) {
                MagicData magic = MagicData.getPlayerMagicData(caster);
                AttributeInstance maxMana = caster.getAttribute(AttributeRegistry.MAX_MANA.get());
                if (magic != null && maxMana != null && maxMana.getValue() > 0) {
                    double manaPct = magic.getMana() / maxMana.getValue() * 100.0;
                    double threshold = HandlerCommonConfig.HANDLER.instance().resonantCastingManaThreshold;
                    if (manaPct >= threshold) {
                        double bonus = HandlerCommonConfig.HANDLER.instance().resonantCastingPercent / 100.0;
                        event.setAmount((float) (event.getAmount() * (1.0 + bonus)));
                    }
                }
            }

            // Long Channel: bonus damage on LONG casts only.
            if (RegistryPerks.LONG_CHANNEL != null && RegistryPerks.LONG_CHANNEL.get().isEnabled(caster)) {
                SpellDamageSource spellDs = event.getSpellDamageSource();
                if (spellDs != null && spellDs.spell() != null && spellDs.spell().getCastType() == CastType.LONG) {
                    double bonus = HandlerCommonConfig.HANDLER.instance().longChannelPercent / 100.0;
                    event.setAmount((float) (event.getAmount() * (1.0 + bonus)));
                }
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

        // Imbued Focus: flat +N bonus level on every cast.
        if (RegistryPerks.IMBUED_FOCUS != null && RegistryPerks.IMBUED_FOCUS.get().isEnabled(player)) {
            int bonus = HandlerCommonConfig.HANDLER.instance().imbuedFocusLevels;
            if (bonus > 0) event.addLevels(bonus);
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

        // ── Phase 1a: Arcane Reprieve — instant refill when mana hits zero ──
        if (RegistryPerks.ARCANE_REPRIEVE != null && RegistryPerks.ARCANE_REPRIEVE.get().isEnabled(player)
                && event.getNewMana() <= 0.0f && event.getOldMana() > 0.0f) {
            long now = player.level().getGameTime();
            long lastUse = arcaneReprieveLastUse.getOrDefault(player.getUUID(), Long.MIN_VALUE);
            int cdTicks = HandlerCommonConfig.HANDLER.instance().arcaneReprieveCooldown * 20;
            if (now - lastUse >= cdTicks) {
                double[] values = RegistryPerks.ARCANE_REPRIEVE.get().getValue();
                double pct = values.length > 0 ? values[0] : 40.0;
                AttributeInstance maxMana = player.getAttribute(AttributeRegistry.MAX_MANA.get());
                float restore = (float) ((maxMana != null ? maxMana.getValue() : 100.0) * pct / 100.0);
                event.setNewMana(restore);
                arcaneReprieveLastUse.put(player.getUUID(), now);
            }
        }

        // ── Phase 1a: Continuous Flow — reduced per-tick drain on CONTINUOUS casts ──
        if (RegistryPerks.CONTINUOUS_FLOW != null && RegistryPerks.CONTINUOUS_FLOW.get().isEnabled(player)
                && event.getNewMana() < event.getOldMana()) {
            MagicData magic = event.getMagicData();
            if (magic != null && magic.isCasting() && magic.getCastType() == CastType.CONTINUOUS) {
                float drain = event.getOldMana() - event.getNewMana();
                double pct = HandlerCommonConfig.HANDLER.instance().continuousFlowPercent / 100.0;
                float savings = (float) (drain * pct);
                event.setNewMana(event.getNewMana() + savings);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ── Phase 1a: Generic mana & casting perks ──
    // ════════════════════════════════════════════════════════════════════════
    // Sixteen perks from MAGIC-RUNIC-SKILLS.md §A1. Four are pure attribute
    // modifiers reconciled on a throttled tick; the remainder hook into ISS
    // cast / mana / damage events. Per-player transient state (combo counters,
    // reprieve cooldowns) lives in Maps keyed by Player UUID — entries are
    // intentionally not cleaned up on logout; they cap at online-player count
    // and drop on restart, which is acceptable given the tiny per-entry cost.

    // Stable UUIDs for each permanent modifier so we can idempotently
    // add/remove per-perk. Generated once; do NOT change — loaded worlds
    // store these UUIDs in player attribute NBT.
    private static final UUID WELLSPRING_UUID     = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-1e6b4f9a3c8d");
    private static final UUID QUICKENING_UUID     = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-2e6b4f9a3c8d");
    private static final UUID RESERVOIR_UUID      = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-3e6b4f9a3c8d");
    private static final UUID TEMPO_UUID          = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-4e6b4f9a3c8d");
    private static final UUID MANA_SURGE_SP_UUID  = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-5e6b4f9a3c8d");
    private static final UUID MANA_SURGE_MR_UUID  = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-6e6b4f9a3c8d");

    // Phase 1b: per-school mancer/warded modifier UUIDs. Two per school, nine
    // schools = 18 stable UUIDs. Do NOT reorder — stored in player attribute NBT.
    private static final UUID FIRE_MANCER_UUID       = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c01");
    private static final UUID FIRE_WARDED_UUID       = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c02");
    private static final UUID ICE_MANCER_UUID        = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c03");
    private static final UUID ICE_WARDED_UUID        = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c04");
    private static final UUID LIGHTNING_MANCER_UUID  = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c05");
    private static final UUID LIGHTNING_WARDED_UUID  = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c06");
    private static final UUID HOLY_MANCER_UUID       = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c07");
    private static final UUID HOLY_WARDED_UUID       = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c08");
    private static final UUID ENDER_MANCER_UUID      = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c09");
    private static final UUID ENDER_WARDED_UUID      = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c0a");
    private static final UUID BLOOD_MANCER_UUID      = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c0b");
    private static final UUID BLOOD_WARDED_UUID      = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c0c");
    private static final UUID EVOCATION_MANCER_UUID  = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c0d");
    private static final UUID EVOCATION_WARDED_UUID  = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c0e");
    private static final UUID NATURE_MANCER_UUID     = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c0f");
    private static final UUID NATURE_WARDED_UUID     = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c10");
    private static final UUID ELDRITCH_MANCER_UUID   = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c11");
    private static final UUID ELDRITCH_WARDED_UUID   = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c12");

    // Per-player transient state.
    private static final Map<UUID, Integer> spellweaverCount = new HashMap<>();
    private static final Map<UUID, Long> spellweaverLastCast = new HashMap<>();
    private static final Map<UUID, Long> arcaneReprieveLastUse = new HashMap<>();

    /**
     * Idempotently reconciles a permanent attribute modifier with the perk's
     * current enabled state. If enabled, ensures the modifier is present with
     * the given value and operation; if disabled, removes it.
     */
    private static void reconcileModifier(Player player, RegistryObject<Attribute> attrObj, UUID uuid,
                                          String name, boolean wanted, double value,
                                          AttributeModifier.Operation op) {
        if (attrObj == null || !attrObj.isPresent()) return;
        AttributeInstance inst = player.getAttribute(attrObj.get());
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(uuid);
        if (wanted) {
            if (existing != null && existing.getAmount() == value && existing.getOperation() == op) return;
            if (existing != null) inst.removeModifier(existing);
            inst.addPermanentModifier(new AttributeModifier(uuid, name, value, op));
        } else if (existing != null) {
            inst.removeModifier(existing);
        }
    }

    /** Tick-throttled reconciliation of permanent & transient attribute perks. */
    @SubscribeEvent
    public void onPlayerTickPhase1a(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        // Throttle to every 10 ticks (0.5s). Cheap enough that we can go full
        // rate, but keeps parity with BotaniaIntegration#onPlayerTick.
        if ((player.tickCount % 10) != 0) return;

        // Wellspring → MAX_MANA flat bonus
        boolean wellspring = RegistryPerks.WELLSPRING != null && RegistryPerks.WELLSPRING.get().isEnabled(player);
        double wellspringValue = wellspring
                ? HandlerCommonConfig.HANDLER.instance().wellspringManaBonus : 0;
        reconcileModifier(player, AttributeRegistry.MAX_MANA, WELLSPRING_UUID,
                "runicskills:wellspring", wellspring, wellspringValue,
                AttributeModifier.Operation.ADDITION);

        // Quickening → CAST_TIME_REDUCTION (values are 0..1 percent units)
        boolean quickening = RegistryPerks.QUICKENING != null && RegistryPerks.QUICKENING.get().isEnabled(player);
        double quickeningValue = quickening
                ? HandlerCommonConfig.HANDLER.instance().quickeningPercent / 100.0 : 0;
        reconcileModifier(player, AttributeRegistry.CAST_TIME_REDUCTION, QUICKENING_UUID,
                "runicskills:quickening", quickening, quickeningValue,
                AttributeModifier.Operation.ADDITION);

        // Reservoir → MANA_REGEN (base 1.0; ADDITION of 0.20 = +20%)
        boolean reservoir = RegistryPerks.RESERVOIR != null && RegistryPerks.RESERVOIR.get().isEnabled(player);
        double reservoirValue = reservoir
                ? HandlerCommonConfig.HANDLER.instance().reservoirPercent / 100.0 : 0;
        reconcileModifier(player, AttributeRegistry.MANA_REGEN, RESERVOIR_UUID,
                "runicskills:reservoir", reservoir, reservoirValue,
                AttributeModifier.Operation.ADDITION);

        // Tempo → COOLDOWN_REDUCTION (values are 0..1 percent units)
        boolean tempo = RegistryPerks.TEMPO != null && RegistryPerks.TEMPO.get().isEnabled(player);
        double tempoValue = tempo
                ? HandlerCommonConfig.HANDLER.instance().tempoPercent / 100.0 : 0;
        reconcileModifier(player, AttributeRegistry.COOLDOWN_REDUCTION, TEMPO_UUID,
                "runicskills:tempo", tempo, tempoValue,
                AttributeModifier.Operation.ADDITION);

        // Mana Surge → transient SPELL_POWER + MANA_REGEN while HP% < threshold
        boolean manaSurgeActive = false;
        if (RegistryPerks.MANA_SURGE != null && RegistryPerks.MANA_SURGE.get().isEnabled(player)) {
            float hpPct = player.getHealth() / player.getMaxHealth() * 100f;
            if (hpPct < HandlerCommonConfig.HANDLER.instance().manaSurgeHpThreshold) {
                manaSurgeActive = true;
            }
        }
        double surgeSpValue = manaSurgeActive
                ? HandlerCommonConfig.HANDLER.instance().manaSurgeSpellPowerPercent / 100.0 : 0;
        double surgeMrValue = manaSurgeActive
                ? HandlerCommonConfig.HANDLER.instance().manaSurgeRegenPercent / 100.0 : 0;
        reconcileModifier(player, AttributeRegistry.SPELL_POWER, MANA_SURGE_SP_UUID,
                "runicskills:mana_surge_sp", manaSurgeActive, surgeSpValue,
                AttributeModifier.Operation.ADDITION);
        reconcileModifier(player, AttributeRegistry.MANA_REGEN, MANA_SURGE_MR_UUID,
                "runicskills:mana_surge_mr", manaSurgeActive, surgeMrValue,
                AttributeModifier.Operation.ADDITION);

        // Phase 1b: per-school mancer (+X_SPELL_POWER) and warded (+X_MAGIC_RESIST)
        // attribute reconciliation. Percent values live in 0..1 units, so "+20%"
        // = ADDITION of 0.20 on an attribute defaulting to 0.
        HandlerCommonConfig c = HandlerCommonConfig.HANDLER.instance();

        reconcileMancer(player, RegistryPerks.FIRE_MANCER, AttributeRegistry.FIRE_SPELL_POWER, FIRE_MANCER_UUID,
                "runicskills:fire_mancer", c.fireMancerPercent);
        reconcileMancer(player, RegistryPerks.FIRE_WARDED, AttributeRegistry.FIRE_MAGIC_RESIST, FIRE_WARDED_UUID,
                "runicskills:fire_warded", c.fireWardedPercent);
        reconcileMancer(player, RegistryPerks.ICE_MANCER, AttributeRegistry.ICE_SPELL_POWER, ICE_MANCER_UUID,
                "runicskills:ice_mancer", c.iceMancerPercent);
        reconcileMancer(player, RegistryPerks.ICE_WARDED, AttributeRegistry.ICE_MAGIC_RESIST, ICE_WARDED_UUID,
                "runicskills:ice_warded", c.iceWardedPercent);
        reconcileMancer(player, RegistryPerks.LIGHTNING_MANCER, AttributeRegistry.LIGHTNING_SPELL_POWER, LIGHTNING_MANCER_UUID,
                "runicskills:lightning_mancer", c.lightningMancerPercent);
        reconcileMancer(player, RegistryPerks.LIGHTNING_WARDED, AttributeRegistry.LIGHTNING_MAGIC_RESIST, LIGHTNING_WARDED_UUID,
                "runicskills:lightning_warded", c.lightningWardedPercent);
        reconcileMancer(player, RegistryPerks.HOLY_MANCER, AttributeRegistry.HOLY_SPELL_POWER, HOLY_MANCER_UUID,
                "runicskills:holy_mancer", c.holyMancerPercent);
        reconcileMancer(player, RegistryPerks.HOLY_WARDED, AttributeRegistry.HOLY_MAGIC_RESIST, HOLY_WARDED_UUID,
                "runicskills:holy_warded", c.holyWardedPercent);
        reconcileMancer(player, RegistryPerks.ENDER_MANCER, AttributeRegistry.ENDER_SPELL_POWER, ENDER_MANCER_UUID,
                "runicskills:ender_mancer", c.enderMancerPercent);
        reconcileMancer(player, RegistryPerks.ENDER_WARDED, AttributeRegistry.ENDER_MAGIC_RESIST, ENDER_WARDED_UUID,
                "runicskills:ender_warded", c.enderWardedPercent);
        reconcileMancer(player, RegistryPerks.BLOOD_MANCER, AttributeRegistry.BLOOD_SPELL_POWER, BLOOD_MANCER_UUID,
                "runicskills:blood_mancer", c.bloodMancerPercent);
        reconcileMancer(player, RegistryPerks.BLOOD_WARDED, AttributeRegistry.BLOOD_MAGIC_RESIST, BLOOD_WARDED_UUID,
                "runicskills:blood_warded", c.bloodWardedPercent);
        reconcileMancer(player, RegistryPerks.EVOCATION_MANCER, AttributeRegistry.EVOCATION_SPELL_POWER, EVOCATION_MANCER_UUID,
                "runicskills:evocation_mancer", c.evocationMancerPercent);
        reconcileMancer(player, RegistryPerks.EVOCATION_WARDED, AttributeRegistry.EVOCATION_MAGIC_RESIST, EVOCATION_WARDED_UUID,
                "runicskills:evocation_warded", c.evocationWardedPercent);
        reconcileMancer(player, RegistryPerks.NATURE_MANCER, AttributeRegistry.NATURE_SPELL_POWER, NATURE_MANCER_UUID,
                "runicskills:nature_mancer", c.natureMancerPercent);
        reconcileMancer(player, RegistryPerks.NATURE_WARDED, AttributeRegistry.NATURE_MAGIC_RESIST, NATURE_WARDED_UUID,
                "runicskills:nature_warded", c.natureWardedPercent);
        reconcileMancer(player, RegistryPerks.ELDRITCH_MANCER, AttributeRegistry.ELDRITCH_SPELL_POWER, ELDRITCH_MANCER_UUID,
                "runicskills:eldritch_mancer", c.eldritchMancerPercent);
        reconcileMancer(player, RegistryPerks.ELDRITCH_WARDED, AttributeRegistry.ELDRITCH_MAGIC_RESIST, ELDRITCH_WARDED_UUID,
                "runicskills:eldritch_warded", c.eldritchWardedPercent);
    }

    /** Shared wrapper around reconcileModifier for null-safe school mancer/warded perks. */
    private static void reconcileMancer(Player player,
                                        net.minecraftforge.registries.RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk,
                                        RegistryObject<Attribute> attr,
                                        UUID uuid, String name, int configPercent) {
        boolean enabled = perk != null && perk.get().isEnabled(player);
        double value = enabled ? configPercent / 100.0 : 0;
        reconcileModifier(player, attr, uuid, name, enabled, value, AttributeModifier.Operation.ADDITION);
    }

    /** Arcane Recovery — restore mana on kill, scaled by victim max HP. */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (RegistryPerks.ARCANE_RECOVERY == null) return;
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof Player player) || player.isCreative()) return;
        if (!RegistryPerks.ARCANE_RECOVERY.get().isEnabled(player)) return;

        LivingEntity victim = event.getEntity();
        double pct = HandlerCommonConfig.HANDLER.instance().arcaneRecoveryPercent / 100.0;
        int cap = HandlerCommonConfig.HANDLER.instance().arcaneRecoveryCap;
        float gained = (float) Math.min(victim.getMaxHealth() * pct, cap);
        if (gained <= 0) return;
        MagicData magic = MagicData.getPlayerMagicData(player);
        if (magic != null) {
            magic.addMana(gained);
        }
    }

    /** Focus — 1-in-N chance to ignore a damage-source's cast-interrupt on a LONG cast. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingAttackFocus(LivingAttackEvent event) {
        if (RegistryPerks.FOCUS == null) return;
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;
        if (!RegistryPerks.FOCUS.get().isEnabled(player)) return;

        MagicData magic = MagicData.getPlayerMagicData(player);
        if (magic == null || !magic.isCasting()) return;
        if (magic.getCastType() != CastType.LONG) return;

        int probability = HandlerCommonConfig.HANDLER.instance().focusProbability;
        if (probability <= 0) return;
        if (player.getRandom().nextInt(probability) == 0) {
            // ISS cancels the cast off the damage-applied path, not here. Cancelling the
            // LivingAttackEvent means the damage never lands, which also preserves the cast.
            event.setCanceled(true);
        }
    }

    /**
     * Phase 1b catalyst handler for SELF-BUFF schools (Lightning, Holy, Ender,
     * Evocation, Nature, Eldritch). On each cast of a matching school, roll the
     * perk's probability; on success apply the signature effect to the caster.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onSpellCastBuffCatalyst(SpellOnCastEvent event) {
        Player caster = event.getEntity();
        if (caster == null || caster.isCreative()) return;
        SchoolType school = event.getSchoolType();
        if (school == null) return;
        String schoolName = school.getId().getPath();

        HandlerCommonConfig c = HandlerCommonConfig.HANDLER.instance();
        switch (schoolName) {
            case "lightning" -> tryCatalyst(caster, RegistryPerks.LIGHTNING_CATALYST,
                    MobEffectRegistry.CHARGED.get(), c.lightningCatalystProbability, c.lightningCatalystDuration * 20);
            case "holy" -> tryCatalyst(caster, RegistryPerks.HOLY_CATALYST,
                    MobEffectRegistry.FORTIFY.get(), c.holyCatalystProbability, c.holyCatalystDuration * 20);
            case "ender" -> tryCatalyst(caster, RegistryPerks.ENDER_CATALYST,
                    MobEffectRegistry.PLANAR_SIGHT.get(), c.enderCatalystProbability, c.enderCatalystDuration * 20);
            case "evocation" -> tryCatalyst(caster, RegistryPerks.EVOCATION_CATALYST,
                    MobEffectRegistry.ECHOING_STRIKES.get(), c.evocationCatalystProbability, c.evocationCatalystDuration * 20);
            case "nature" -> tryCatalyst(caster, RegistryPerks.NATURE_CATALYST,
                    MobEffectRegistry.OAKSKIN.get(), c.natureCatalystProbability, c.natureCatalystDuration * 20);
            case "eldritch" -> tryCatalyst(caster, RegistryPerks.ELDRITCH_CATALYST,
                    MobEffectRegistry.ABYSSAL_SHROUD.get(), c.eldritchCatalystProbability, c.eldritchCatalystDuration);
            default -> { /* no-op for offensive-school casts */ }
        }
    }

    /**
     * Phase 1b catalyst handler for DEBUFF schools (Fire, Ice, Blood). On each
     * damage-dealing spell tick of a matching school, roll the probability and
     * apply the signature effect to the victim.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onSpellDamageDebuffCatalyst(SpellDamageEvent event) {
        Entity casterEntity = event.getSpellDamageSource().getEntity();
        if (!(casterEntity instanceof Player caster) || caster.isCreative()) return;
        LivingEntity victim = event.getEntity();
        if (victim == null) return;
        SpellDamageSource spellDs = event.getSpellDamageSource();
        if (spellDs == null || spellDs.spell() == null) return;
        SchoolType school = spellDs.spell().getSchoolType();
        if (school == null) return;
        String schoolName = school.getId().getPath();

        HandlerCommonConfig c = HandlerCommonConfig.HANDLER.instance();
        switch (schoolName) {
            case "fire" -> tryCatalystOn(caster, victim, RegistryPerks.FIRE_CATALYST,
                    MobEffectRegistry.IMMOLATE.get(), c.fireCatalystProbability, c.fireCatalystDuration * 20);
            case "ice" -> tryCatalystOn(caster, victim, RegistryPerks.ICE_CATALYST,
                    MobEffectRegistry.CHILLED.get(), c.iceCatalystProbability, c.iceCatalystDuration * 20);
            case "blood" -> tryCatalystOn(caster, victim, RegistryPerks.BLOOD_CATALYST,
                    MobEffectRegistry.REND.get(), c.bloodCatalystProbability, c.bloodCatalystDuration * 20);
            default -> { /* other schools buff via SpellOnCastEvent branch */ }
        }
    }

    private static void tryCatalyst(Player caster,
                                    net.minecraftforge.registries.RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk,
                                    MobEffect effect, int probability, int durationTicks) {
        if (perk == null || !perk.get().isEnabled(caster)) return;
        if (effect == null || probability <= 0) return;
        if (caster.getRandom().nextInt(probability) == 0) {
            caster.addEffect(new MobEffectInstance(effect, durationTicks, 0, true, true));
        }
    }

    private static void tryCatalystOn(Player caster, LivingEntity victim,
                                      net.minecraftforge.registries.RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk,
                                      MobEffect effect, int probability, int durationTicks) {
        if (perk == null || !perk.get().isEnabled(caster)) return;
        if (effect == null || probability <= 0) return;
        if (caster.getRandom().nextInt(probability) == 0) {
            victim.addEffect(new MobEffectInstance(effect, durationTicks, 0, true, true), caster);
        }
    }

    /** Quickcast — cooldown reduction applied only to INSTANT-type spells. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSpellCooldownQuickcast(SpellCooldownAddedEvent.Pre event) {
        if (RegistryPerks.QUICKCAST == null) return;
        Player player = event.getEntity();
        if (player == null || player.isCreative()) return;
        if (!RegistryPerks.QUICKCAST.get().isEnabled(player)) return;
        AbstractSpell spell = event.getSpell();
        if (spell == null || spell.getCastType() != CastType.INSTANT) return;

        double pct = HandlerCommonConfig.HANDLER.instance().quickcastPercent / 100.0;
        int reduced = (int) Math.max(0, event.getEffectiveCooldown() * (1.0 - pct));
        event.setEffectiveCooldown(reduced);
    }

    /** Mana Bulwark — redirect a % of incoming damage into mana at a 2:1 conversion. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHurtBulwark(LivingHurtEvent event) {
        if (RegistryPerks.MANA_BULWARK == null) return;
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;
        if (!RegistryPerks.MANA_BULWARK.get().isEnabled(player)) return;

        MagicData magic = MagicData.getPlayerMagicData(player);
        if (magic == null || magic.getMana() <= 0) return;

        double pct = HandlerCommonConfig.HANDLER.instance().manaBulwarkPercent / 100.0;
        int ratio = Math.max(1, HandlerCommonConfig.HANDLER.instance().manaBulwarkManaPerDamage);
        float absorbed = (float) (event.getAmount() * pct);
        float manaCost = absorbed * ratio;
        if (manaCost > magic.getMana()) {
            // Cap absorption to whatever the pool can actually pay for.
            absorbed = magic.getMana() / ratio;
            manaCost = magic.getMana();
        }
        if (absorbed > 0 && manaCost > 0) {
            magic.setMana(Math.max(0f, magic.getMana() - manaCost));
            event.setAmount(event.getAmount() - absorbed);
        }
    }
}
