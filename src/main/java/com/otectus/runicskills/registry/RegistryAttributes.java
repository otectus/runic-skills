package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.ApothicAttributesIntegration;
import com.otectus.runicskills.integration.ApothicPassiveHelper;
import com.otectus.runicskills.registry.passive.Passive;

import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RegistryAttributes {
    // Aliases onto the central owner table. These three are declared there, not here, so the
    // migration purge and the code that applies them cannot drift apart (RS10-002).
    public static final UUID COUNTER_ATTACK_UUID = RunicAttributeModifiers.COUNTER_ATTACK;
    public static final UUID ONE_HANDED_UUID = RunicAttributeModifiers.ONE_HANDED;
    public static final UUID DIAMOND_SKIN_UUID = RunicAttributeModifiers.DIAMOND_SKIN;

    private static final DeferredRegister<Attribute> REGISTER = DeferredRegister.create(ForgeRegistries.Keys.ATTRIBUTES, RunicSkills.MOD_ID);

    public static final RegistryObject<Attribute> BREAK_SPEED = REGISTER.register("break_speed", () -> (new RangedAttribute("break_speed", 0.0D, 0.0D, 1024.0D)).setSyncable(true));
    public static final RegistryObject<Attribute> CRITICAL_DAMAGE = REGISTER.register("critical_damage", () -> (new RangedAttribute("critical_damage", 0.0D, 0.0D, 1024.0D)).setSyncable(true));
    public static final RegistryObject<Attribute> PROJECTILE_DAMAGE = REGISTER.register("projectile_damage", () -> (new RangedAttribute("projectile_damage", 0.0D, 0.0D, 1024.0D)).setSyncable(true));
    public static final RegistryObject<Attribute> BENEFICIAL_EFFECT = REGISTER.register("beneficial_effect", () -> (new RangedAttribute("beneficial_effect", 0.0D, 0.0D, 1024.0D)).setSyncable(true));
    public static final RegistryObject<Attribute> MAGIC_RESIST = REGISTER.register("magic_resist", () -> (new RangedAttribute("magic_resist", 0.0D, 0.0D, 1024.0D)).setSyncable(true));
    public static final RegistryObject<Attribute> ENCHANTING_POWER = REGISTER.register("enchanting_power", () -> (new RangedAttribute("enchanting_power", 0.0D, 0.0D, 1024.0D)).setSyncable(true));
    public static final RegistryObject<Attribute> XP_BONUS = REGISTER.register("xp_bonus", () -> (new RangedAttribute("xp_bonus", 0.0D, 0.0D, 1024.0D)).setSyncable(true));
    public static final RegistryObject<Attribute> REPAIR_EFFICIENCY = REGISTER.register("repair_efficiency", () -> (new RangedAttribute("repair_efficiency", 0.0D, 0.0D, 1024.0D)).setSyncable(true));
    public static final RegistryObject<Attribute> CRAFTING_LUCK = REGISTER.register("crafting_luck", () -> (new RangedAttribute("crafting_luck", 0.0D, 0.0D, 1024.0D)).setSyncable(true));

    /**
     * Returns the effective Critical Damage attribute.
     * When Apothic Attributes is loaded and delegation is enabled, returns attributeslib:crit_damage.
     * Otherwise returns the Runic Skills custom attribute.
     */
    public static Attribute getEffectiveCritDamage() {
        if (ApothicAttributesIntegration.isModLoaded() && HandlerCommonConfig.HANDLER.instance().apothicDelegateCritDamage) {
            return ApothicPassiveHelper.getCritDamage();
        }
        return CRITICAL_DAMAGE.get();
    }

    /**
     * Returns the effective Break Speed / Mining Speed attribute.
     */
    public static Attribute getEffectiveBreakSpeed() {
        if (ApothicAttributesIntegration.isModLoaded() && HandlerCommonConfig.HANDLER.instance().apothicDelegateMiningSpeed) {
            return ApothicPassiveHelper.getMiningSpeed();
        }
        return BREAK_SPEED.get();
    }

    /**
     * Returns the effective Projectile Damage / Arrow Damage attribute.
     */
    public static Attribute getEffectiveProjectileDamage() {
        if (ApothicAttributesIntegration.isModLoaded() && HandlerCommonConfig.HANDLER.instance().apothicDelegateArrowDamage) {
            return ApothicPassiveHelper.getArrowDamage();
        }
        return PROJECTILE_DAMAGE.get();
    }

    public static void load(IEventBus eventBus) {
        REGISTER.register(eventBus);
    }

    public static void modifierAttributes(ServerPlayer serverPlayer) {
        migrateLegacyModifiers(serverPlayer);
        com.otectus.runicskills.integration.tom.TomAquaAttunement.refresh(serverPlayer);
        serverPlayer.getCapability(RegistryCapabilities.SKILL).ifPresent(skillCapability -> {
            for (Passive passive : RegistryPassives.getCachedValues()) {
                removeInactiveDelegatedModifier(serverPlayer, passive);
                boolean enabled = !RegistryPassives.isDisabled(passive);
                // An empty levelsRequired array divided by zero and installed a NaN modifier,
                // which poisons every downstream calculation on that attribute (RS-042).
                int maxLevel = passive.levelsRequired.length;
                if (maxLevel <= 0) {
                    RunicSkills.getLOGGER().warn(
                            "Passive {} has an empty level array and cannot be applied; check its "
                            + "levels config entry.", passive.getName());
                    new RegisterAttribute(serverPlayer, passive.attribute, 0.0D,
                            UUID.fromString(passive.attributeUuid)).amplifyAttribute(false);
                    continue;
                }
                // Clamp to the configured maximum. Shrinking a passive's level array left players
                // who were already above the new cap scaling past it indefinitely (RS-043).
                int level = Math.min(passive.getLevel(serverPlayer), maxLevel);
                double value = passive.getValue() / maxLevel * level;
                new RegisterAttribute(serverPlayer, passive.attribute, value,
                        UUID.fromString(passive.attributeUuid)).amplifyAttribute(enabled);
            }
        });
    }

    /** A reload must move the same passive UUID, never leave a bonus on both providers. */
    private static void removeInactiveDelegatedModifier(ServerPlayer player, Passive passive) {
        if (!ApothicAttributesIntegration.isModLoaded()) return;
        Attribute owned;
        Attribute delegated;
        if (passive == RegistryPassives.BREAK_SPEED.get()) {
            owned = BREAK_SPEED.get();
            delegated = ApothicPassiveHelper.getMiningSpeed();
        } else if (passive == RegistryPassives.CRITICAL_DAMAGE.get()) {
            owned = CRITICAL_DAMAGE.get();
            delegated = ApothicPassiveHelper.getCritDamage();
        } else if (passive == RegistryPassives.PROJECTILE_DAMAGE.get()) {
            owned = PROJECTILE_DAMAGE.get();
            delegated = ApothicPassiveHelper.getArrowDamage();
        } else return;
        Attribute inactive = passive.attribute == owned ? delegated : owned;
        new RegisterAttribute(player, inactive, 0, UUID.fromString(passive.attributeUuid))
                .amplifyAttribute(false);
    }

    /**
     * Attribute-migration marker. Versioned rather than boolean because the first sweep was
     * incomplete and its {@code true} flag would otherwise permanently mask the repair.
     *
     * <ul>
     *   <li>{@code 0} — never migrated.</li>
     *   <li>{@code 1} — the pre-2.0.0 sweep ran. It removed only modifiers whose display name was
     *       exactly {@code "runicskills"}, so it missed every integration modifier
     *       ({@code "runicskills:wellspring"} and friends) and every perk-attribute modifier
     *       ({@code "runicskills.perk"}).</li>
     *   <li>{@code 2} — the UUID-driven sweep in this class has run.</li>
     * </ul>
     */
    static final String ATTR_MIGRATION_VERSION_KEY = "rs_attr_migration_version";

    /** The pre-2.0.0 boolean flag. Read only to recognise a partially-migrated save. */
    private static final String LEGACY_MIGRATION_FLAG = "rs_attr_transient_migrated";

    static final int ATTR_MIGRATION_VERSION = 2;

    /**
     * One-shot removal of every attribute modifier this mod ever persisted into a player's save.
     *
     * <p>Modifiers up to 1.6.1 were applied with {@code addPermanentModifier} and therefore
     * serialised into the player's own attribute NBT, where they outlived whatever granted them:
     * a perk disabled in config, a lost skill level, a Counter Attack window that never closed
     * (RS-011) or a toggled Apothic delegation (RS-073) each left a bonus nothing could reach.
     * 1.7.0 made the core modifiers transient and added a sweep — but that sweep matched on the
     * modifier's <em>display name</em> being exactly {@code "runicskills"}, and the integration
     * modifiers are named {@code "runicskills:<feature>"} while the perk-attribute pass names its
     * own {@code "runicskills.perk"}. None of them matched, so mana, spell power, cooldown, crit,
     * dodge, lifesteal, armour-pierce and healing bonuses stayed in saves permanently — including
     * for players who had removed Iron's Spells or AttributesLib entirely (RS10-002).
     *
     * <p>This sweep matches on UUID from {@link RunicAttributeModifiers}, which is the actual
     * identity of a modifier, and runs for anyone below migration version 2 — including the saves
     * the 1.7.0 flag already marked as done. Clearing everything the mod owns is safe precisely
     * because it is all re-derived: passives immediately below, perk and integration modifiers on
     * their next reconciliation tick.
     */
    private static void migrateLegacyModifiers(ServerPlayer serverPlayer) {
        var persisted = serverPlayer.getPersistentData();
        int version = persisted.getInt(ATTR_MIGRATION_VERSION_KEY);
        if (version == 0 && persisted.getBoolean(LEGACY_MIGRATION_FLAG)) {
            version = 1;
        }
        if (version >= ATTR_MIGRATION_VERSION) return;

        int removed = RunicAttributeModifiers.removeAllPlayerOwned(serverPlayer);
        persisted.putInt(ATTR_MIGRATION_VERSION_KEY, ATTR_MIGRATION_VERSION);
        persisted.remove(LEGACY_MIGRATION_FLAG);

        if (removed > 0) {
            RunicSkills.getLOGGER().info(
                    "Removed {} persisted attribute modifier(s) from {} left by a pre-2.0.0 version "
                    + "(migration v{} -> v{}); active bonuses are re-applied automatically.",
                    removed, serverPlayer.getGameProfile().getName(), version, ATTR_MIGRATION_VERSION);
        }
    }

    public static class RegisterAttribute {
        private final Player player;
        private final Attribute attribute;
        private final double modifier;
        private final UUID uuid;

        public RegisterAttribute(Player player, Attribute attribute, double modifier, UUID uuid) {
            this.player = player;
            this.attribute = attribute;
            this.modifier = modifier;
            this.uuid = uuid;
        }

        /**
         * Applies or removes this modifier, doing nothing when the attribute already holds the
         * requested value.
         *
         * <p>Two properties matter here and neither used to hold:
         *
         * <p><b>Idempotence.</b> The old implementation removed and re-added unconditionally, and
         * {@code AttributeInstance#removeModifier}/{@code addModifier} both mark the instance
         * dirty. Callers on the player-tick path therefore queued an attribute-sync packet for
         * {@code ARMOR} and {@code ATTACK_DAMAGE} every tick, per player, for perks whose value
         * had not changed — roughly 40 packets/second/player of pure churn (RS-010). Comparing
         * the existing amount first makes the steady state free.
         *
         * <p><b>Transience.</b> Modifiers are now added with {@code addTransientModifier} rather
         * than {@code addPermanentModifier}. Permanent modifiers are serialised into the player's
         * own attribute NBT, so a bonus survived independently of the perk that granted it: a
         * perk disabled in config, a lost skill level, or a toggled Apothic delegation left an
         * unremovable stat bonus in the save with nothing left to clean it up (RS-018, RS-073).
         * Every modifier this mod applies is re-derived on login by
         * {@link RegistryAttributes#modifierAttributes} and on the tick paths that own it, so
         * nothing needs to persist.
         */
        /** @return true if the attribute was actually changed, so callers can sync only on a transition. */
        public boolean amplifyAttribute(boolean isEnabled) {
            AttributeInstance instance = this.player.getAttribute(this.attribute);
            if (instance == null)
                return false;
            AttributeModifier existing = instance.getModifier(this.uuid);
            if (!isEnabled) {
                if (existing == null) return false;
                instance.removeModifier(existing);
                return true;
            }
            if (existing != null) {
                if (existing.getAmount() == this.modifier) return false;
                instance.removeModifier(existing);
            }
            instance.addTransientModifier(new AttributeModifier(
                    this.uuid, RunicSkills.MOD_ID, this.modifier, AttributeModifier.Operation.ADDITION));
            return true;
        }
    }
}


