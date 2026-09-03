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
    // Identity and presentation: fixed by the mod, never by configuration.
    public final ResourceLocation key;
    private final Supplier<Skill> skillSupplier;
    public final ResourceLocation texture;

    // Tunables: everything below comes from the config file and therefore has to be able to change
    // while the game is running. They were final, captured once when the registry was filled — so
    // /skillsreload updated the values the event handlers read live while leaving the registered
    // requirement levels, tooltip numbers and eligibility checks frozen at their startup values,
    // and a client's own file decided them regardless of the server's (RS10-005). Forge registries
    // are frozen after startup, so the entry cannot be replaced; instead the registration lambda is
    // re-run and its results adopted in place by
    // {@link com.otectus.runicskills.registry.RegistryPerks#refreshFromConfig()}.
    //
    // volatile: publication happens on the server thread (/skillsreload) or the client's network
    // thread task, and readers are on the tick and render threads.
    public volatile int requiredLevel;
    public volatile int maxRank;
    public volatile int[] rankLevelRequirements;
    private volatile Value[] configValues;
    private volatile Value[][] rankedConfigValues;
    private volatile double[] cachedValues;
    private volatile double[][] cachedRankedValues;

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
        return new Perk(key, () -> skill, levelRequirement, HandlerResources.parseTexture(texture), perkValues);
    }

    // KubeJS support - multi rank
    public static Perk add(String perkName, String skillName, int[] rankLevelReqs, String texture, Value[]... rankedValues) {
        Skill skill = RegistrySkills.getSkill(skillName);
        if (skill == null) {
            throw new IllegalArgumentException("Skill name doesn't exist: " + skillName);
        }
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, perkName);
        return new Perk(key, () -> skill, rankLevelReqs, HandlerResources.parseTexture(texture), rankedValues);
    }

    /**
     * Takes on {@code rebuilt}'s configuration-derived values, keeping this registered instance's
     * identity.
     *
     * <p>Called with the result of re-running this perk's own registration lambda against the
     * current configuration, so the mapping from config field to perk stays in exactly one place —
     * the registration site — rather than being restated in a refresh routine that could drift
     * from it.
     *
     * <p>The derived-value caches are dropped here rather than recomputed: nothing may read a
     * tooltip number derived from the previous configuration, and recomputing eagerly would do the
     * work for all 462 perks on every reload whether or not anything reads them.
     */
    public void adoptTunables(Perk rebuilt) {
        if (rebuilt == null || rebuilt == this) return;
        this.requiredLevel = rebuilt.requiredLevel;
        this.maxRank = rebuilt.maxRank;
        this.rankLevelRequirements = rebuilt.rankLevelRequirements;
        this.configValues = rebuilt.configValues;
        this.rankedConfigValues = rebuilt.rankedConfigValues;
        this.cachedValues = null;
        this.cachedRankedValues = null;
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
        double[] cached = this.cachedValues;
        if (cached == null) {
            // Read the source through a local too: adoptTunables may swap configValues between
            // these two statements, and recomputing from a stale array is better than a null
            // dereference — the next call sees the new one.
            cached = extractValues(this.configValues);
            this.cachedValues = cached;
        }
        return cached;
    }

    // Returns values for a specific rank (1-indexed)
    public double[] getValue(int rank) {
        Value[][] ranked = this.rankedConfigValues;
        if (ranked == null || rank < 1 || rank > ranked.length) {
            return getValue();
        }
        double[][] cache = this.cachedRankedValues;
        if (cache == null || cache.length != ranked.length) {
            cache = new double[ranked.length][];
            this.cachedRankedValues = cache;
        }
        int idx = rank - 1;
        double[] cached = cache[idx];
        if (cached == null) {
            cached = extractValues(ranked[idx]);
            cache[idx] = cached;
        }
        return cached;
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
        Value[] values = this.configValues;
        double[] resolved = getValue();
        Object[] newValue = new Object[values.length];
        for (int i = 0; i < newValue.length; i++) {
            // resolved is derived from a possibly newer configValues after a concurrent reload;
            // guard the index rather than risking an out-of-bounds on a tooltip.
            if (values[i] != null && i < resolved.length) {
                newValue[i] = getParameter(values[i].type, resolved[i]);
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
        // TICKS prints bare: the only lang strings that use it already write "ticks" after the
        // placeholder, so appending a unit here would read "60 ticks ticks" in 17 locales.
        if (type.equals(ValueType.TICKS)) parameter = "§9" + parameter;
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
        if (this.requiredLevel <= 0) return false;
        SkillCapability cap = SkillCapability.getLocal();
        return cap != null && cap.getSkillLevel(this.getSkill()) >= this.requiredLevel;
    }

    public boolean getToggle(Player player) {
        if (this.requiredLevel <= 0) return false;
        SkillCapability cap = SkillCapability.get(player);
        return cap != null && cap.getSkillLevel(this.getSkill()) >= this.requiredLevel;
    }

    public boolean isEnabled() {
        if (this.requiredLevel < 1) return true;
        if (com.otectus.runicskills.registry.RegistryPerks.isDisabled(this)) return false;
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
        if (com.otectus.runicskills.registry.RegistryPerks.isDisabled(this)) return false;
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
