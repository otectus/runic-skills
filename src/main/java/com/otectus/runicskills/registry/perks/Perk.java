package com.otectus.runicskills.registry.perks;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.client.core.Value;
import com.otectus.runicskills.client.core.ValueType;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.text.DecimalFormat;
import java.util.Objects;
import java.util.function.Supplier;

public class Perk {
    public final ResourceLocation key;
    private final Supplier<Skill> skillSupplier;
    public final int requiredLevel;
    public final int maxRank;
    public final int[] rankLevelRequirements;
    public final ResourceLocation texture;
    private final Value[] configValues;
    private final Value[][] rankedConfigValues;
    private double[] cachedValues;
    private double[][] cachedRankedValues;

    // Single-rank constructor (backward compatible)
    public Perk(ResourceLocation perkKey, Supplier<Skill> skillSupplier, int levelRequirement, ResourceLocation perkTexture, Value... perkValues) {
        this.key = perkKey;
        this.skillSupplier = skillSupplier;
        this.requiredLevel = levelRequirement;
        this.maxRank = 1;
        this.rankLevelRequirements = new int[]{levelRequirement};
        this.texture = perkTexture;
        this.configValues = perkValues;
        this.rankedConfigValues = null;
    }

    // Multi-rank constructor
    public Perk(ResourceLocation perkKey, Supplier<Skill> skillSupplier, int[] rankLevelReqs, ResourceLocation perkTexture, Value[]... rankedValues) {
        this.key = perkKey;
        this.skillSupplier = skillSupplier;
        this.maxRank = rankLevelReqs.length;
        this.rankLevelRequirements = rankLevelReqs;
        this.requiredLevel = rankLevelReqs.length > 0 ? rankLevelReqs[0] : 0;
        this.texture = perkTexture;
        this.configValues = rankedValues.length > 0 ? rankedValues[0] : new Value[0];
        this.rankedConfigValues = rankedValues;
    }

    // KubeJS support - single rank
    public static Perk add(String perkName, String skillName, int levelRequirement, String texture, Value... perkValues) {
        Skill skill = RegistrySkills.getSkill(skillName);
        if (skill == null) {
            throw new IllegalArgumentException("Skill name doesn't exist: " + skillName);
        }
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, perkName);
        return new Perk(key, () -> skill, levelRequirement, HandlerResources.create(texture), perkValues);
    }

    // KubeJS support - multi rank
    public static Perk add(String perkName, String skillName, int[] rankLevelReqs, String texture, Value[]... rankedValues) {
        Skill skill = RegistrySkills.getSkill(skillName);
        if (skill == null) {
            throw new IllegalArgumentException("Skill name doesn't exist: " + skillName);
        }
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, perkName);
        return new Perk(key, () -> skill, rankLevelReqs, HandlerResources.create(texture), rankedValues);
    }

    public Skill getSkill() {
        return skillSupplier.get();
    }

    public Perk get() {
        return this;
    }

    public String getMod() {
        return this.key.getNamespace();
    }

    public String getName() {
        return this.key.getPath();
    }

    public String getKey() {
        return "perk." + this.key.toLanguageKey();
    }

    public String getDescription() {
        return getKey() + ".description";
    }

    public int getLvl() {
        return this.requiredLevel;
    }

    public int getMaxRank() {
        return this.maxRank;
    }

    public int getLevelForRank(int rank) {
        if (rank < 1 || rank > this.rankLevelRequirements.length) return Integer.MAX_VALUE;
        return this.rankLevelRequirements[rank - 1];
    }

    public int getPlayerRank(Player player) {
        if (player == null) return 0;
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return 0;
        return cap.getPerkRank(this);
    }

    public int getPlayerRank() {
        SkillCapability cap = SkillCapability.getLocal();
        if (cap == null) return 0;
        return cap.getPerkRank(this);
    }

    // Returns values for Rank I (backward compatible)
    public double[] getValue() {
        if (cachedValues == null) {
            cachedValues = extractValues(this.configValues);
        }
        return cachedValues;
    }

    // Returns values for a specific rank (1-indexed)
    public double[] getValue(int rank) {
        if (rankedConfigValues == null || rank < 1 || rank > rankedConfigValues.length) {
            return getValue();
        }
        if (cachedRankedValues == null) {
            cachedRankedValues = new double[rankedConfigValues.length][];
        }
        int idx = rank - 1;
        if (cachedRankedValues[idx] == null) {
            cachedRankedValues[idx] = extractValues(rankedConfigValues[idx]);
        }
        return cachedRankedValues[idx];
    }

    // Returns values for the player's current rank
    public double[] getActiveValue(Player player) {
        int rank = getPlayerRank(player);
        return rank >= 1 ? getValue(rank) : getValue();
    }

    private static double[] extractValues(Value[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = 0.0D;
            if (values[i] != null) {
                Object object = values[i].value;
                if (object instanceof Number value) {
                    result[i] = value.doubleValue();
                }
            }
        }
        return result;
    }

    public MutableComponent getMutableDescription(String description) {
        Object[] newValue = new Object[this.configValues.length];
        for (int i = 0; i < newValue.length; i++) {
            if (this.configValues[i] != null) {
                newValue[i] = getParameter((this.configValues[i]).type, getValue()[i]);
            }
        }
        return Component.translatable(description, newValue);
    }

    public String getParameter(ValueType type, double parameterValue) {
        DecimalFormat df = new DecimalFormat("0.##");
        String probabilityValue = Utils.periodValue(1.0D / parameterValue * 100.0D);
        String parameter = df.format(parameterValue);
        if (type.equals(ValueType.MODIFIER)) parameter = "§cx" + parameter;
        if (type.equals(ValueType.DURATION)) parameter = "§9" + parameter + "s";
        if (type.equals(ValueType.AMPLIFIER)) parameter = "§6+" + parameter;
        if (type.equals(ValueType.PERCENT)) parameter = "§2" + parameter + "%";
        if (type.equals(ValueType.BOOST)) parameter = "§d" + Utils.intToRoman(Integer.parseInt(parameter));
        if (type.equals(ValueType.PROBABILITY))
            parameter = "§e1/" + parameter + "§r§7 (§2" + probabilityValue + "%§7§r)";
        return parameter + "§r§7";
    }

    public boolean canPerk() {
        if (requiredLevel <= 0) return false;
        SkillCapability cap = SkillCapability.getLocal();
        return cap != null && cap.isPerkActive(this);
    }

    public boolean canPerk(Player player) {
        if (requiredLevel <= 0) return false;
        SkillCapability cap = SkillCapability.get(player);
        return cap != null && cap.isPerkActive(this);
    }

    public boolean getToggle() {
        return this.requiredLevel > 0 && SkillCapability.getLocal().getSkillLevel(this.getSkill()) >= this.requiredLevel;
    }

    public boolean getToggle(Player player) {
        return this.requiredLevel > 0 && SkillCapability.get(player).getSkillLevel(this.getSkill()) >= this.requiredLevel;
    }

    public boolean isEnabled() {
        if (this.requiredLevel < 1) return true;
        SkillCapability cap = SkillCapability.getLocal();
        if (cap == null) return false;
        return cap.getSkillLevel(this.getSkill()) >= this.requiredLevel && cap.isPerkActive(this);
    }

    public boolean isEnabled(Player player) {
        // Use isRemoved() (simple field read) instead of isDeadOrDying() — the latter calls
        // getHealth() → SynchedEntityData.get(DATA_HEALTH_ID), which NPEs when invoked from
        // Entity.<init> → getMaxAirSupply → MixPlayer before LivingEntity.defineSynchedData
        // has registered DATA_HEALTH_ID. isRemoved() only checks the nullable removalReason
        // field and is always safe.
        if (player == null || this.requiredLevel < 1 || player.isRemoved()) return false;
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return false;
        return cap.getSkillLevel(this.getSkill()) >= this.requiredLevel && cap.isPerkActive(this);
    }

    // Check if the player can upgrade to the next rank
    public boolean canRankUp(Player player) {
        if (player == null) return false;
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return false;
        int currentRank = cap.getPerkRank(this);
        if (currentRank >= maxRank) return false;
        int nextRank = currentRank + 1;
        return cap.getSkillLevel(this.getSkill()) >= getLevelForRank(nextRank);
    }

    public ResourceLocation getTexture() {
        return Objects.requireNonNullElse(this.texture, HandlerResources.NULL_PERK);
    }
}
