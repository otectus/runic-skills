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
        serverPlayer.getCapability(RegistryCapabilities.SKILL).ifPresent(skillCapability -> {
            for (Passive passive : RegistryPassives.getCachedValues()) {
                (new RegisterAttribute(serverPlayer, passive.attribute, passive.getValue() / passive.levelsRequired.length * passive.getLevel(serverPlayer), UUID.fromString(passive.attributeUuid))).amplifyAttribute(true);
            }
        });
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

        public void amplifyAttribute(boolean isEnabled) {
            AttributeInstance instance = this.player.getAttribute(this.attribute);
            if (instance == null)
                return;
            AttributeModifier oldModifier = instance.getModifier(this.uuid);
            if (oldModifier != null) instance.removeModifier(oldModifier);

            AttributeModifier newModifier = new AttributeModifier(this.uuid, RunicSkills.MOD_ID, this.modifier, AttributeModifier.Operation.ADDITION);
            if (isEnabled) {
                instance.addPermanentModifier(newModifier);
            } else {
                instance.removeModifier(newModifier);
            }
        }
    }
}


