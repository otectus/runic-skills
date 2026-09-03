package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.util.DurationMath;
import com.otectus.runicskills.common.util.GameTimeWindow;
import com.otectus.runicskills.common.util.TransientModifiers;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.packet.client.NoticeOverlayCP;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RunicAttributeModifiers;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import io.redspace.ironsspellbooks.api.events.*;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IronsSpellbooksIntegration {

    /**
     * Whether this integration should do anything right now: Iron's Spells 'n Spellbooks is installed
     * <em>and</em> {@code enableIronsSpellbooksIntegration} is on in the configuration in force.
     *
     * <p>The toggle used to be read once, in the mod constructor, to decide whether to register
     * this subscriber at all — so turning it off on a running server left the handlers registered
     * and firing, and turning it on could not register a subscriber that had been skipped
     * (RS10-011). The adapter is now registered whenever its upstream mod is present and every
     * entry point asks this instead, which makes the toggle work live in both directions.
     */
    public static boolean isActive() {
        return isModLoaded() && HandlerCommonConfig.HANDLER.instance().enableIronsSpellbooksIntegration;
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
        if (!isActive()) return;
        Player player = event.getEntity();

        String spellId = event.getSpellId();

        if (HandlerCommonConfig.HANDLER.instance().logSpellIds) {
            player.sendSystemMessage(Component.literal(String.format("[Runic Skills] >> Spell ID: %s", spellId)));
        }

        if (player.isCreative()) return;

        SkillCapability provider = SkillCapability.get(player);
        if (provider == null) return;

        // School-based gating: check Magic skill level against spell level formula
        if (HandlerCommonConfig.HANDLER.instance().ironsEnableSchoolGating) {
            Skill magicSkill = RegistrySkills.MAGIC.get();
            int magicLevel = provider.getSkillLevel(magicSkill);
            int spellLevel = event.getSpellLevel();
            int requiredLevel = (int) (HandlerCommonConfig.HANDLER.instance().ironsBaseSpellGatingLevel
                    + (spellLevel - 1) * HandlerCommonConfig.HANDLER.instance().ironsSpellLevelScaleFactor);

            if (magicLevel < requiredLevel) {
                // Over-GUI banner (was sendSystemMessage -> chat, hidden behind open screens).
                NoticeOverlayCP.send(player, "overlay.runicskills.spell_gated",
                        magicSkill.getKey(), String.valueOf(requiredLevel));
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
        if (!isActive()) return;
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
            int windowTicks = DurationMath.secondsToTicks(HandlerCommonConfig.HANDLER.instance().spellweaverComboWindow);
            int required = Math.max(2, HandlerCommonConfig.HANDLER.instance().spellweaverComboCount);

            int count = GameTimeWindow.within(now, spellweaverLastCast.get(uuid), windowTicks)
                    ? spellweaverCount.getOrDefault(uuid, 0) + 1 : 1;
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
        if (!isActive()) return;
        // Three independent bonuses on one event, each behind its own switch. All three used to
        // sit inside ironsEnableSpellDamageScaling, so turning off the Magic-level scaling also
        // turned off the Wisdom synergy and the school bonus — neither of which that switch
        // describes, and both of which have a switch of their own (MEDIUM-02).
        Entity sourceEntity = event.getSpellDamageSource().getEntity();
        if (sourceEntity instanceof Player caster && !caster.isCreative()) {
            SkillCapability casterCap = SkillCapability.get(caster);
            if (casterCap != null) {
                // Spell Damage Scaling: if the caster is a player, scale damage up
                if (HandlerCommonConfig.HANDLER.instance().ironsEnableSpellDamageScaling) {
                    int magicLevel = casterCap.getSkillLevel(RegistrySkills.MAGIC.get());
                    float bonus = (magicLevel - 1) * HandlerCommonConfig.HANDLER.instance().ironsSpellDamageScalePerLevel;
                    if (bonus > 0) {
                        event.setAmount(event.getAmount() * (1.0f + bonus));
                    }
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

            // Charge Mastery: ISS 3.x has no CastType.CHARGE — treat CastType.LONG as the
            // "held cast" equivalent and boost damage so partial-charge releases approximate
            // full-charge damage. Multiplicative on top of Long Channel by design.
            if (RegistryPerks.CHARGE_MASTERY != null && RegistryPerks.CHARGE_MASTERY.get().isEnabled(caster)) {
                SpellDamageSource spellDs = event.getSpellDamageSource();
                if (spellDs != null && spellDs.spell() != null && spellDs.spell().getCastType() == CastType.LONG) {
                    double bonus = HandlerCommonConfig.HANDLER.instance().chargeMasteryPercent / 100.0;
                    event.setAmount((float) (event.getAmount() * (1.0 + bonus)));
                }
            }
        }
    }

    // ── Phase 5: Spell Level Bonuses ──

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onModifySpellLevel(ModifySpellLevelEvent event) {
        if (!isActive()) return;
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;

        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        // ironsEnableSpellLevelBonus names the Magic-threshold bonus, so it gates only that.
        // Imbued Focus is a perk with its own enablement, and used to be unreachable whenever this
        // unrelated switch was off (MEDIUM-02).
        if (HandlerCommonConfig.HANDLER.instance().ironsEnableSpellLevelBonus) {
            int magicLevel = cap.getSkillLevel(RegistrySkills.MAGIC.get());
            int threshold2 = HandlerCommonConfig.HANDLER.instance().ironsSpellLevelBonusThreshold2;
            int threshold1 = HandlerCommonConfig.HANDLER.instance().ironsSpellLevelBonusThreshold;

            if (magicLevel >= threshold2) {
                event.addLevels(2);
            } else if (magicLevel >= threshold1) {
                event.addLevels(1);
            }
        }

        // Imbued Focus: flat +N bonus level on every cast.
        if (RegistryPerks.IMBUED_FOCUS != null && RegistryPerks.IMBUED_FOCUS.get().isEnabled(player)) {
            int bonus = HandlerCommonConfig.HANDLER.instance().imbuedFocusLevels;
            if (bonus > 0) event.addLevels(bonus);
        }
    }

    // ── Phase 5: Mana Regeneration ──

    /**
     * Every mana adjustment this mod makes, sorted by the direction the mana was already moving.
     *
     * <p>The handler used to open with {@code ironsEnableManaRegen} and an "only when mana is going
     * up" return, which made three unrelated features conditional on a regeneration switch: the
     * Intelligence synergy has a switch of its own, and Continuous Flow and Arcane Reprieve are
     * perks that fire on mana being <em>spent</em>, so the early return meant neither could ever run
     * at all (HIGH-01, MEDIUM-02). Each delta now stands on its own gate.
     *
     * <p>Both readings are taken once, up front, because the branches below rewrite the event as
     * they go: Continuous Flow has to see the drain Iron's proposed rather than one an earlier
     * branch already softened, and Arcane Reprieve has to fire on that proposal reaching zero even
     * though Continuous Flow may since have pulled the number back above it.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChangeMana(ChangeManaEvent event) {
        if (!isActive()) return;

        Player player = event.getEntity();
        if (player.isCreative()) return;

        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        float oldMana = event.getOldMana();
        float preNew = event.getNewMana();

        // ── Mana going up: the level-scaled regeneration bonuses ──
        if (preNew > oldMana) {
            if (HandlerCommonConfig.HANDLER.instance().ironsEnableManaRegen) {
                int magicLevel = cap.getSkillLevel(RegistrySkills.MAGIC.get());
                float bonus = magicLevel * HandlerCommonConfig.HANDLER.instance().ironsManaRegenPerMagicLevel;
                if (bonus > 0) {
                    event.setNewMana(event.getNewMana() + bonus);
                }
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

        // ── Mana going down: Continuous Flow — reduced per-tick drain on CONTINUOUS casts ──
        if (preNew < oldMana
                && RegistryPerks.CONTINUOUS_FLOW != null
                && RegistryPerks.CONTINUOUS_FLOW.get().isEnabled(player)) {
            MagicData magic = event.getMagicData();
            if (magic != null && magic.isCasting() && magic.getCastType() == CastType.CONTINUOUS) {
                float drain = oldMana - preNew;
                double pct = HandlerCommonConfig.HANDLER.instance().continuousFlowPercent / 100.0;
                float savings = (float) (drain * pct);
                event.setNewMana(event.getNewMana() + savings);
            }
        }

        // ── Mana crossing zero: Arcane Reprieve — instant refill when the pool runs out ──
        // Judged on preNew, so a Continuous Flow saving cannot hide the moment the player ran dry.
        if (oldMana > 0.0f && preNew <= 0.0f
                && RegistryPerks.ARCANE_REPRIEVE != null
                && RegistryPerks.ARCANE_REPRIEVE.get().isEnabled(player)) {
            long now = player.level().getGameTime();
            int cdTicks = DurationMath.secondsToTicks(HandlerCommonConfig.HANDLER.instance().arcaneReprieveCooldown);
            if (GameTimeWindow.ready(now, arcaneReprieveLastUse.get(player.getUUID()), cdTicks)) {
                double[] values = RegistryPerks.ARCANE_REPRIEVE.get().getActiveValue(player);
                double pct = values.length > 0 ? values[0] : 40.0;
                AttributeInstance maxMana = player.getAttribute(AttributeRegistry.MAX_MANA.get());
                float restore = (float) ((maxMana != null ? maxMana.getValue() : 100.0) * pct / 100.0);
                event.setNewMana(restore);
                arcaneReprieveLastUse.put(player.getUUID(), now);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ── Phase 1a: Generic mana & casting perks ──
    // ════════════════════════════════════════════════════════════════════════
    // Sixteen perks from MAGIC-RUNIC-SKILLS.md §A1. Four are pure attribute
    // modifiers reconciled on a throttled tick; the remainder hook into ISS
    // cast / mana / damage events. Per-player transient state (combo counters,
    // reprieve cooldowns) lives in Maps keyed by Player UUID and is cleared by
    // the lifecycle handlers just below them.

    // Stable UUIDs for each permanent modifier so we can idempotently
    // add/remove per-perk. Generated once; do NOT change — loaded worlds
    // store these UUIDs in player attribute NBT.
    private static final UUID MANA_REGENERATION_UUID    = RunicAttributeModifiers.MANA_REGENERATION;
    private static final UUID SPELL_QUICKENING_UUID     = RunicAttributeModifiers.SPELL_QUICKENING;
    private static final UUID SPELLCRAFT_KNOWLEDGE_UUID = RunicAttributeModifiers.SPELLCRAFT_KNOWLEDGE;
    private static final UUID ARCANE_LINGUIST_UUID      = RunicAttributeModifiers.ARCANE_LINGUIST;
    private static final UUID WELLSPRING_UUID     = RunicAttributeModifiers.WELLSPRING;
    private static final UUID QUICKENING_UUID     = RunicAttributeModifiers.QUICKENING;
    private static final UUID RESERVOIR_UUID      = RunicAttributeModifiers.RESERVOIR;
    private static final UUID TEMPO_UUID          = RunicAttributeModifiers.TEMPO;
    private static final UUID MANA_SURGE_SP_UUID  = RunicAttributeModifiers.MANA_SURGE_SP;
    private static final UUID MANA_SURGE_MR_UUID  = RunicAttributeModifiers.MANA_SURGE_MR;
    private static final UUID IRONS_COOLDOWN_SCALING_UUID = RunicAttributeModifiers.IRONS_COOLDOWN_SCALING;

    // Phase 1b: per-school mancer/warded modifier UUIDs. Two per school, nine
    // schools = 18 stable UUIDs. Do NOT reorder — stored in player attribute NBT.
    private static final UUID FIRE_MANCER_UUID       = RunicAttributeModifiers.FIRE_MANCER;
    private static final UUID FIRE_WARDED_UUID       = RunicAttributeModifiers.FIRE_WARDED;
    private static final UUID ICE_MANCER_UUID        = RunicAttributeModifiers.ICE_MANCER;
    private static final UUID ICE_WARDED_UUID        = RunicAttributeModifiers.ICE_WARDED;
    private static final UUID LIGHTNING_MANCER_UUID  = RunicAttributeModifiers.LIGHTNING_MANCER;
    private static final UUID LIGHTNING_WARDED_UUID  = RunicAttributeModifiers.LIGHTNING_WARDED;
    private static final UUID HOLY_MANCER_UUID       = RunicAttributeModifiers.HOLY_MANCER;
    private static final UUID HOLY_WARDED_UUID       = RunicAttributeModifiers.HOLY_WARDED;
    private static final UUID ENDER_MANCER_UUID      = RunicAttributeModifiers.ENDER_MANCER;
    private static final UUID ENDER_WARDED_UUID      = RunicAttributeModifiers.ENDER_WARDED;
    private static final UUID BLOOD_MANCER_UUID      = RunicAttributeModifiers.BLOOD_MANCER;
    private static final UUID BLOOD_WARDED_UUID      = RunicAttributeModifiers.BLOOD_WARDED;
    private static final UUID EVOCATION_MANCER_UUID  = RunicAttributeModifiers.EVOCATION_MANCER;
    private static final UUID EVOCATION_WARDED_UUID  = RunicAttributeModifiers.EVOCATION_WARDED;
    private static final UUID NATURE_MANCER_UUID     = RunicAttributeModifiers.NATURE_MANCER;
    private static final UUID NATURE_WARDED_UUID     = RunicAttributeModifiers.NATURE_WARDED;
    private static final UUID ELDRITCH_MANCER_UUID   = RunicAttributeModifiers.ELDRITCH_MANCER;
    private static final UUID ELDRITCH_WARDED_UUID   = RunicAttributeModifiers.ELDRITCH_WARDED;

    // Phase 1c
    private static final UUID LORD_OF_THE_DEAD_UUID  = RunicAttributeModifiers.LORD_OF_THE_DEAD;

    // Per-player transient state.
    private static final Map<UUID, Integer> spellweaverCount = new HashMap<>();
    private static final Map<UUID, Long> spellweaverLastCast = new HashMap<>();
    private static final Map<UUID, Long> arcaneReprieveLastUse = new HashMap<>();

    /**
     * Forgets everything remembered about one player.
     *
     * <p>The three maps above were never emptied. A UUID went in on the first cast and stayed for
     * the life of the process, so a long-running server accumulated an entry for every player who
     * had ever cast a spell, and a returning player resumed a combo window from a previous session
     * (MEDIUM-06).
     */
    public static void clearPlayer(UUID uuid) {
        if (uuid == null) return;
        spellweaverCount.remove(uuid);
        spellweaverLastCast.remove(uuid);
        arcaneReprieveLastUse.remove(uuid);
    }

    /**
     * Empties the maps entirely, so leaving a singleplayer world does not carry its timers into the
     * next one loaded in the same process.
     */
    public static void clearAll() {
        spellweaverCount.clear();
        spellweaverLastCast.clear();
        arcaneReprieveLastUse.clear();
    }

    /**
     * Logout clears everything: a player who is gone has neither a combo running nor a cooldown to
     * serve, and these entries are what leaked.
     *
     * <p>This and the two handlers below it sit on the Iron's-typed integration class rather than in
     * the common lifecycle handler on purpose. The instance is registered on the FORGE bus by
     * {@code RunicSkills.tryLoadIntegration} only when Iron's Spells is installed, so common code
     * never has to name a class that would drag Iron's types into its constant pool.
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        clearAll();
    }

    /**
     * A death respawn ends the combo window and nothing else.
     *
     * <p>Spellweaver counts casts inside a few seconds, and dying plainly ends that. Arcane
     * Reprieve's cooldown deliberately survives: a cooldown that death resets is a cooldown a player
     * can shorten by dying on purpose.
     */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        UUID uuid = event.getOriginal().getUUID();
        spellweaverCount.remove(uuid);
        spellweaverLastCast.remove(uuid);
    }

    /**
     * Idempotently reconciles a transient attribute modifier with the perk's current enabled
     * state. If enabled, ensures the modifier is present with the given value and operation; if
     * disabled, removes it.
     *
     * <p>Transient, not permanent. A permanent modifier is serialised into the player's own
     * attribute NBT, and this handler is only registered when Iron's Spells is installed and the
     * integration toggle is on — so disabling either one used to strand the bonus in the save with
     * nothing left able to remove it. That is the whole of RS10-002. Nothing needs to persist:
     * every value here is re-derived on the reconciliation tick below, and
     * {@code RegistryAttributes} re-derives on login.
     */
    private static void reconcileModifier(Player player, RegistryObject<Attribute> attrObj, UUID uuid,
                                          String name, boolean wanted, double value,
                                          AttributeModifier.Operation op) {
        // Gated here rather than by an early return in the tick handlers that call this: turning
        // the integration off has to actively REMOVE the modifiers it owns, and a handler that
        // returned early would strand them until the player next logged out (RS10-011).
        if (attrObj == null || !attrObj.isPresent()) return;
        TransientModifiers.reconcile(player, attrObj.get(), uuid, name, wanted && isActive(), value, op);
    }

    /** Tick-throttled reconciliation of permanent & transient attribute perks. */
    @SubscribeEvent
    public void onPlayerTickPhase1a(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        // Throttle to every 10 ticks (0.5s). Cheap enough that we can go full rate.
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

        // Magic level → COOLDOWN_REDUCTION. ironsEnableCooldownReduction, ironsCooldownReductionPerLevel
        // and ironsMaxCooldownReduction were three public, documented config fields that nothing read
        // (HIGH-06). The scaling lands on the same attribute as Tempo but under its own id, so the
        // passive and the perk stack rather than one silently replacing the other.
        boolean cooldownScaling = HandlerCommonConfig.HANDLER.instance().ironsEnableCooldownReduction;
        SkillCapability cooldownCap = SkillCapability.get(player);
        double cooldownScalingValue = 0;
        if (cooldownScaling && cooldownCap != null) {
            int magicLevel = cooldownCap.getSkillLevel(RegistrySkills.MAGIC.get());
            cooldownScalingValue = Math.min(
                    HandlerCommonConfig.HANDLER.instance().ironsMaxCooldownReduction,
                    Math.max(0, magicLevel - 1)
                            * HandlerCommonConfig.HANDLER.instance().ironsCooldownReductionPerLevel);
        }
        reconcileModifier(player, AttributeRegistry.COOLDOWN_REDUCTION, IRONS_COOLDOWN_SCALING_UUID,
                "runicskills:irons_cooldown_scaling", cooldownScalingValue > 0, cooldownScalingValue,
                AttributeModifier.Operation.ADDITION);

        // The four perks completed in 2.0.0. Each was registered with a config value, a texture and
        // a tooltip naming an Iron's Spells stat, and none of them touched that stat or anything
        // else (RS10-004). They reconcile on the same clock and through the same helper as the
        // perks above, so switching the integration off removes them rather than stranding them.

        // Mana Regeneration → MANA_REGEN (base 1.0; ADDITION of 0.15 = +15%)
        boolean manaRegeneration = RegistryPerks.MANA_REGENERATION != null
                && RegistryPerks.MANA_REGENERATION.get().isEnabled(player);
        reconcileModifier(player, AttributeRegistry.MANA_REGEN, MANA_REGENERATION_UUID,
                "runicskills:mana_regeneration", manaRegeneration,
                manaRegeneration ? HandlerCommonConfig.HANDLER.instance().manaRegenerationPercent / 100.0 : 0,
                AttributeModifier.Operation.ADDITION);

        // Spell Quickening and Spellcraft Knowledge → CAST_TIME_REDUCTION. Both tooltips describe
        // the same thing from opposite ends — "cast time reduced" and "casting speed increased" —
        // so both land on the one number Iron's Spells has for it and stack, rather than one of them
        // being handed a different stat purely to keep the two looking distinct.
        boolean spellQuickening = RegistryPerks.SPELL_QUICKENING != null
                && RegistryPerks.SPELL_QUICKENING.get().isEnabled(player);
        reconcileModifier(player, AttributeRegistry.CAST_TIME_REDUCTION, SPELL_QUICKENING_UUID,
                "runicskills:spell_quickening", spellQuickening,
                spellQuickening ? HandlerCommonConfig.HANDLER.instance().spellQuickeningPercent / 100.0 : 0,
                AttributeModifier.Operation.ADDITION);

        boolean spellcraftKnowledge = RegistryPerks.SPELLCRAFT_KNOWLEDGE != null
                && RegistryPerks.SPELLCRAFT_KNOWLEDGE.get().isEnabled(player);
        reconcileModifier(player, AttributeRegistry.CAST_TIME_REDUCTION, SPELLCRAFT_KNOWLEDGE_UUID,
                "runicskills:spellcraft_knowledge", spellcraftKnowledge,
                spellcraftKnowledge ? HandlerCommonConfig.HANDLER.instance().spellcraftKnowledgePercent / 100.0 : 0,
                AttributeModifier.Operation.ADDITION);

        // Arcane Linguist → SPELL_POWER. "Reading spell types grants bonus effectiveness" is the
        // scholar's version of the caster perks: what a spell is worth once it lands, which in
        // Iron's Spells is spell power and nothing else.
        boolean arcaneLinguist = RegistryPerks.ARCANE_LINGUIST != null
                && RegistryPerks.ARCANE_LINGUIST.get().isEnabled(player);
        reconcileModifier(player, AttributeRegistry.SPELL_POWER, ARCANE_LINGUIST_UUID,
                "runicskills:arcane_linguist", arcaneLinguist,
                arcaneLinguist ? HandlerCommonConfig.HANDLER.instance().arcaneLinguistPercent / 100.0 : 0,
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

        // Phase 1c: Lord of the Dead — summon_damage bonus on the caster side.
        boolean lotd = RegistryPerks.LORD_OF_THE_DEAD != null
                && RegistryPerks.LORD_OF_THE_DEAD.get().isEnabled(player);
        double lotdValue = lotd ? c.lordOfTheDeadDamagePercent / 100.0 : 0;
        reconcileModifier(player, AttributeRegistry.SUMMON_DAMAGE, LORD_OF_THE_DEAD_UUID,
                "runicskills:lord_of_the_dead", lotd, lotdValue,
                AttributeModifier.Operation.ADDITION);
    }

    /**
     * Lord of the Dead — summon HP bonus. When an ISS summon joins the world,
     * if its summoner has the perk enabled, scale its max health. This runs
     * once per spawn rather than on-tick, matching the "on create" semantics
     * the attribute-modifier approach requires.
     */
    @SubscribeEvent
    public void onSummonJoinLevel(EntityJoinLevelEvent event) {
        if (!isActive()) return;
        if (RegistryPerks.LORD_OF_THE_DEAD == null) return;
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof IMagicSummon summon)) return;
        if (!(event.getEntity() instanceof LivingEntity livingSummon)) return;
        if (!(summon.getSummoner() instanceof Player summoner)) return;
        if (summoner.isCreative()) return;
        if (!RegistryPerks.LORD_OF_THE_DEAD.get().isEnabled(summoner)) return;

        AttributeInstance maxHp = livingSummon.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHp == null) return;
        double bonus = HandlerCommonConfig.HANDLER.instance().lordOfTheDeadHealthPercent / 100.0;
        // MULTIPLY_BASE: final = base * (1 + sum(bonus)). Use a stable UUID so
        // re-spawning the same summon (e.g. via /reload) won't stack.
        //
        // The one deliberately PERMANENT modifier this mod applies, and the reason
        // RunicAttributeModifiers has an OWNED_ENTITY scope: the target is a summoned entity, not
        // a player. Its max health has to survive its own chunk save/load cycle independently of
        // whether the summoner is online, and nothing re-derives it per tick the way the player
        // paths do. Never route a player through here.
        if (maxHp.getModifier(LORD_OF_THE_DEAD_UUID) == null) {
            maxHp.addPermanentModifier(new AttributeModifier(LORD_OF_THE_DEAD_UUID,
                    "runicskills:lord_of_the_dead_hp", bonus,
                    AttributeModifier.Operation.MULTIPLY_BASE));
            livingSummon.setHealth(livingSummon.getMaxHealth());
        }
    }

    /**
     * Life Leech Bound — when a player-summoned IMagicSummon damages something,
     * return a percent of that damage as mana to the summoner.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onSummonHurt(LivingHurtEvent event) {
        if (!isActive()) return;
        if (RegistryPerks.LIFE_LEECH_BOUND == null) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof IMagicSummon summon)) return;
        if (!(summon.getSummoner() instanceof Player summoner)) return;
        if (summoner.isCreative()) return;
        if (!RegistryPerks.LIFE_LEECH_BOUND.get().isEnabled(summoner)) return;

        double pct = HandlerCommonConfig.HANDLER.instance().lifeLeechBoundPercent / 100.0;
        float mana = (float) (event.getAmount() * pct);
        if (mana <= 0) return;
        MagicData magic = MagicData.getPlayerMagicData(summoner);
        if (magic != null) magic.addMana(mana);
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
        if (!isActive()) return;
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
        if (!isActive()) return;
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
        if (!isActive()) return;
        Player caster = event.getEntity();
        if (caster == null || caster.isCreative()) return;
        SchoolType school = event.getSchoolType();
        if (school == null) return;
        String schoolName = school.getId().getPath();

        HandlerCommonConfig c = HandlerCommonConfig.HANDLER.instance();
        switch (schoolName) {
            case "lightning" -> tryCatalyst(caster, RegistryPerks.LIGHTNING_CATALYST,
                    MobEffectRegistry.CHARGED.get(), c.lightningCatalystProbability, DurationMath.secondsToTicks(c.lightningCatalystDuration));
            case "holy" -> tryCatalyst(caster, RegistryPerks.HOLY_CATALYST,
                    MobEffectRegistry.FORTIFY.get(), c.holyCatalystProbability, DurationMath.secondsToTicks(c.holyCatalystDuration));
            case "ender" -> tryCatalyst(caster, RegistryPerks.ENDER_CATALYST,
                    MobEffectRegistry.PLANAR_SIGHT.get(), c.enderCatalystProbability, DurationMath.secondsToTicks(c.enderCatalystDuration));
            case "evocation" -> tryCatalyst(caster, RegistryPerks.EVOCATION_CATALYST,
                    MobEffectRegistry.ECHOING_STRIKES.get(), c.evocationCatalystProbability, DurationMath.secondsToTicks(c.evocationCatalystDuration));
            case "nature" -> tryCatalyst(caster, RegistryPerks.NATURE_CATALYST,
                    MobEffectRegistry.OAKSKIN.get(), c.natureCatalystProbability, DurationMath.secondsToTicks(c.natureCatalystDuration));
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
        if (!isActive()) return;
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
                    MobEffectRegistry.IMMOLATE.get(), c.fireCatalystProbability, DurationMath.secondsToTicks(c.fireCatalystDuration));
            case "ice" -> tryCatalystOn(caster, victim, RegistryPerks.ICE_CATALYST,
                    MobEffectRegistry.CHILLED.get(), c.iceCatalystProbability, DurationMath.secondsToTicks(c.iceCatalystDuration));
            case "blood" -> tryCatalystOn(caster, victim, RegistryPerks.BLOOD_CATALYST,
                    MobEffectRegistry.REND.get(), c.bloodCatalystProbability, DurationMath.secondsToTicks(c.bloodCatalystDuration));
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

    // ════════════════════════════════════════════════════════════════════════
    // ── Phase 3: Triple Threat + Affix Focus ──
    // ════════════════════════════════════════════════════════════════════════

    // Stable UUIDs for the three Triple Threat modifiers.
    private static final UUID TT_MAX_MANA_UUID = RunicAttributeModifiers.TRIPLE_THREAT_MAX_MANA;
    private static final UUID TT_MANA_REGEN_UUID = RunicAttributeModifiers.TRIPLE_THREAT_MANA_REGEN;
    private static final UUID TT_SPELL_POWER_UUID = RunicAttributeModifiers.TRIPLE_THREAT_SPELL_POWER;

    /**
     * Triple Threat — when all three of Iron's, Ars, and Apotheosis are loaded
     * AND the perk is enabled, grant +N% max_mana, mana_regen, and spell_power.
     * The tick handler reconciles the three modifiers; they zero out when any
     * mod is absent or the perk is disabled.
     */
    @SubscribeEvent
    public void onPlayerTickPhase3(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if ((player.tickCount % 10) != 0) return;

        boolean allLoaded = ModList.get().isLoaded("irons_spellbooks")
                && ModList.get().isLoaded("ars_nouveau")
                && ModList.get().isLoaded("apotheosis");
        boolean tripleThreat = allLoaded
                && RegistryPerks.TRIPLE_THREAT != null
                && RegistryPerks.TRIPLE_THREAT.get().isEnabled(player);
        double ttValue = tripleThreat
                ? HandlerCommonConfig.HANDLER.instance().xTripleThreatPercent / 100.0 : 0;

        reconcileModifier(player, AttributeRegistry.MAX_MANA, TT_MAX_MANA_UUID,
                "runicskills:triple_threat_mana", tripleThreat, ttValue * 100.0,
                AttributeModifier.Operation.ADDITION);
        reconcileModifier(player, AttributeRegistry.MANA_REGEN, TT_MANA_REGEN_UUID,
                "runicskills:triple_threat_regen", tripleThreat, ttValue,
                AttributeModifier.Operation.ADDITION);
        reconcileModifier(player, AttributeRegistry.SPELL_POWER, TT_SPELL_POWER_UUID,
                "runicskills:triple_threat_sp", tripleThreat, ttValue,
                AttributeModifier.Operation.ADDITION);
    }

    /**
     * Affix Focus — count equipped Rare+ Apotheosis-affix items; if the count
     * meets the threshold, grant +N effective spell levels to any ISS cast.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onModifySpellLevelAffixFocus(ModifySpellLevelEvent event) {
        if (!isActive()) return;
        if (RegistryPerks.AFFIX_FOCUS == null) return;
        if (!ModList.get().isLoaded("apotheosis")) return;
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;
        if (!RegistryPerks.AFFIX_FOCUS.get().isEnabled(player)) return;

        int required = HandlerCommonConfig.HANDLER.instance().xAffixFocusRequiredItems;
        int count = 0;
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            if (!dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper.hasAffixes(stack)) continue;
            var rarity = dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper.getRarity(stack);
            if (rarity.isBound() && rarity.get().ordinal() >= 2) count++;
        }
        if (count >= required) {
            int bonus = HandlerCommonConfig.HANDLER.instance().xAffixFocusBonusLevels;
            if (bonus > 0) event.addLevels(bonus);
        }
    }

    /**
     * Spellsocket (1.2.0) — +N effective spell levels per N equipped Apotheosis sockets.
     * Caps at spellsocketMaxBonus regardless of socket count.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onModifySpellLevelSpellsocket(ModifySpellLevelEvent event) {
        if (!isActive()) return;
        if (RegistryPerks.SPELLSOCKET == null) return;
        if (!ModList.get().isLoaded("apotheosis")) return;
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;
        if (!RegistryPerks.SPELLSOCKET.get().isEnabled(player)) return;

        int totalSockets = 0;
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper.hasAffixes(stack)) continue;
            totalSockets += dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper.getSockets(stack);
        }

        int socketsPerLevel = Math.max(1, HandlerCommonConfig.HANDLER.instance().spellsocketSocketsPerLevel);
        int maxBonus = HandlerCommonConfig.HANDLER.instance().spellsocketMaxBonus;
        int bonus = Math.min(maxBonus, totalSockets / socketsPerLevel);
        if (bonus > 0) event.addLevels(bonus);
    }

    /**
     * Resonant Affixes (1.2.0) — ISS spell-damage bonus per rare-or-better equipped
     * Apotheosis-affix item. Multiplicative; stacks with other spell-damage perks.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onSpellDamageResonantAffixes(SpellDamageEvent event) {
        if (!isActive()) return;
        if (RegistryPerks.RESONANT_AFFIXES == null) return;
        if (!ModList.get().isLoaded("apotheosis")) return;
        Entity src = event.getSpellDamageSource().getEntity();
        if (!(src instanceof Player player) || player.isCreative()) return;
        if (!RegistryPerks.RESONANT_AFFIXES.get().isEnabled(player)) return;

        int count = 0;
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper.hasAffixes(stack)) continue;
            var rarity = dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper.getRarity(stack);
            if (rarity.isBound() && rarity.get().ordinal() >= 2) count++;
        }
        if (count <= 0) return;

        float pct = count * (HandlerCommonConfig.HANDLER.instance().resonantAffixesPercent / 100.0f);
        event.setAmount(event.getAmount() * (1.0f + pct));
    }

    /** Quickcast — cooldown reduction applied only to INSTANT-type spells. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSpellCooldownQuickcast(SpellCooldownAddedEvent.Pre event) {
        if (!isActive()) return;
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
        if (!isActive()) return;
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
