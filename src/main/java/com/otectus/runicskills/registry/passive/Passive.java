package com.otectus.runicskills.registry.passive;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Objects;
import java.util.function.Supplier;

public class Passive {
    public final ResourceLocation key;
    private final Supplier<Skill> skillSupplier;
    public final ResourceLocation texture;
    public final Attribute attribute;
    public final String attributeUuid;
    public final Object attributeValue;
    public final int[] levelsRequired;

    public Passive(ResourceLocation passiveKey, Supplier<Skill> skillSupplier, ResourceLocation passiveTexture, Attribute attribute, String attributeUuid, Object attributeValue, int... levelsRequired) {
        this.key = passiveKey;
        this.skillSupplier = skillSupplier;
        this.texture = passiveTexture;
        this.attribute = attribute;
        this.attributeUuid = attributeUuid;
        this.attributeValue = attributeValue;
        this.levelsRequired = levelsRequired;
    }

    public Skill getSkill() {
        return skillSupplier.get();
    }

    // KubeJS support
    public static Passive add(String passiveName, String skillName, String texture, Attribute attribute, String attributeUUID, Object attributeValue, int... levelsRequired){
        Skill skill = RegistrySkills.getSkill(skillName);
        if (skill == null){
            throw new IllegalArgumentException("Skill name doesn't exist: " + skillName);
        }

        final Skill resolvedSkill = skill;
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, passiveName);
        return new Passive(key, () -> resolvedSkill, HandlerResources.create(texture), attribute, attributeUUID, attributeValue, levelsRequired);
    }

    public Passive get() {
        return this;
    }

    public String getMod() {
        return this.key.getNamespace();
    }

    public String getName() {
        return this.key.getPath();
    }

    public String getKey() {
        return "passive." + this.key.toLanguageKey();
    }

    public String getDescription() {
        return getKey() + ".description";
    }

    public double getValue() {
        double newValue = 0.0D;
        if (this.attributeValue != null) {
            Object object = this.attributeValue;
            if (object instanceof ForgeConfigSpec.DoubleValue) {
                ForgeConfigSpec.DoubleValue value = (ForgeConfigSpec.DoubleValue) object;
                newValue = value.get();
            }
            if (object instanceof ForgeConfigSpec.IntValue) {
                ForgeConfigSpec.IntValue value = (ForgeConfigSpec.IntValue) object;
                newValue = value.get();
            }
            if (object instanceof Number) {
                Number value = (Number) object;
                newValue = value.doubleValue();
            }

        }
        return newValue;
    }

    public int getNextLevelUp() {
        int[] requirement = new int[this.levelsRequired.length + 2];
        requirement[0] = 0;
        System.arraycopy(this.levelsRequired, 0, requirement, 1, this.levelsRequired.length);
        int index = Math.min(getLevel() + 1, requirement.length - 1); // C7: Bounds check
        return requirement[index];
    }

    public int getLevel() {
        SkillCapability cap = SkillCapability.getLocal();
        return cap != null ? cap.getPassiveLevel(this) : 0;
    }

    public int getLevel(Player player) {
        SkillCapability cap = SkillCapability.get(player);
        return cap != null ? cap.getPassiveLevel(this) : 0;
    }

    public int getMaxLevel() {
        return this.levelsRequired.length;
    }

    public ResourceLocation getTexture() {
        return Objects.requireNonNullElse(this.texture, HandlerResources.NULL_PERK);
    }
}


