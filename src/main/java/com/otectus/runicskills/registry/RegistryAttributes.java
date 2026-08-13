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
    public static final UUID COUNTER_ATTACK_UUID = UUID.fromString("55550aa2-eff2-4a81-b92b-a1cb95f15590");
    public static final UUID ONE_HANDED_UUID = UUID.fromString("55550aa2-eff2-4a81-b92b-a1cb95f15555");
    public static final UUID DIAMOND_SKIN_UUID = UUID.fromString("55550aa2-eff2-4a81-b92b-a1cb95f15556");

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
        purgeLegacyPermanentModifiers(serverPlayer);
        serverPlayer.getCapability(RegistryCapabilities.SKILL).ifPresent(skillCapability -> {
            for (Passive passive : RegistryPassives.getCachedValues()) {
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

    /**
     * One-shot removal of the permanent attribute modifiers written by versions up to 1.6.1.
     *
     * <p>Every modifier this mod applies used {@code addPermanentModifier}, so it was serialised
     * into the player's attribute NBT and outlived whatever granted it. A perk disabled in config,
     * a lost skill level, a Counter Attack window that never closed (RS-011) or a toggled Apothic
     * delegation (RS-073) each left a bonus in the save that no code path could reach any more.
     * Modifiers are transient now, but existing saves still carry the old ones, and they are
     * indistinguishable from live ones except by the fact that nothing re-applies them.
     *
     * <p>Clearing everything this mod owns is safe precisely because it is all re-derived: passives
     * immediately below, perk modifiers on the next player tick. Runs once per player, tracked in
     * persistent data so a re-login does not repeat the sweep.
     */
    private static void purgeLegacyPermanentModifiers(ServerPlayer serverPlayer) {
        final String flag = "rs_attr_transient_migrated";
        if (serverPlayer.getPersistentData().getBoolean(flag)) return;
        serverPlayer.getPersistentData().putBoolean(flag, true);

        int removed = 0;
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES) {
            AttributeInstance instance = serverPlayer.getAttribute(attribute);
            if (instance == null) continue;
            for (AttributeModifier modifier : new java.util.ArrayList<>(instance.getModifiers())) {
                if (RunicSkills.MOD_ID.equals(modifier.getName())) {
                    instance.removeModifier(modifier);
                    removed++;
                }
            }
        }
        if (removed > 0) {
            RunicSkills.getLOGGER().info(
                    "Removed {} persisted attribute modifier(s) from {} left by a pre-1.7.0 version; "
                    + "active bonuses are re-applied automatically.", removed, serverPlayer.getGameProfile().getName());
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


