package com.otectus.runicskills.registry.powers;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * A Power is a passive-install / reactive-trigger ability from RUNIC_SKILLS_POWERS.md.
 * <p>
 * Powers are pure metadata: tier, school, governing skill, level gate, optional ICD, texture.
 * Behavior lives in {@link com.otectus.runicskills.registry.events.PowerEventDispatcher},
 * keyed by {@link #getName()}. This mirrors how
 * {@link com.otectus.runicskills.registry.perks.Perk} carries config values without owning
 * its own event handler — a single big subscriber dispatches all of them, matching the
 * existing IronsSpellbooksIntegration pattern.
 * <p>
 * Cross-cutting Powers (Projectile, Channel, etc.) carry a {@code runicskills}-namespaced
 * school id from {@link PowerSchool}.
 */
public class Power {

    public final ResourceLocation key;
    public final PowerTier tier;
    public final ResourceLocation schoolId;
    private final Supplier<Skill> governingSkillSupplier;
    public final int requiredSkillLevel;
    public final ResourceLocation texture;
    /** Default internal cooldown in ticks (0 = no ICD). Datapack overrides win at runtime. */
    public final int defaultIcdTicks;
    /** Optional required-mod-id; if non-null and the mod isn't loaded, the Power is registered no-op. */
    @Nullable public final String requiredModId;

    public Power(ResourceLocation key,
                 PowerTier tier,
                 ResourceLocation schoolId,
                 Supplier<Skill> governingSkillSupplier,
                 int requiredSkillLevel,
                 ResourceLocation texture,
                 int defaultIcdTicks,
                 @Nullable String requiredModId) {
        this.key = key;
        this.tier = tier;
        this.schoolId = schoolId;
        this.governingSkillSupplier = governingSkillSupplier;
        this.requiredSkillLevel = requiredSkillLevel;
        this.texture = texture;
        this.defaultIcdTicks = Math.max(0, defaultIcdTicks);
        this.requiredModId = requiredModId;
    }

    public Skill getGoverningSkill() {
        return governingSkillSupplier.get();
    }

    public PowerTier getTier() {
        return tier;
    }

    public ResourceLocation getSchoolId() {
        return schoolId;
    }

    public String getMod() {
        return key.getNamespace();
    }

    /** Path-only id, used as the SkillCapability map key for both equip slots and runtime state. */
    public String getName() {
        return key.getPath();
    }

    public String getFullId() {
        return key.toString();
    }

    public String getKey() {
        return "power." + key.toLanguageKey();
    }

    public String getDescriptionKey() {
        return getKey() + ".description";
    }

    public ResourceLocation getTexture() {
        return Objects.requireNonNullElse(this.texture, HandlerResources.NULL_PERK);
    }

    /** Honoured by the dispatcher to short-circuit handler dispatch when the player hasn't equipped this Power. */
    public boolean isEquippedBy(Player player) {
        if (player == null) return false;
        SkillCapability cap = SkillCapability.get(player);
        return cap != null && cap.isPowerEquipped(this);
    }

    /** Equipping requires both the skill threshold and the slot. */
    public boolean meetsSkillRequirement(Player player) {
        if (player == null || requiredSkillLevel < 1) return false;
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return false;
        Skill skill = getGoverningSkill();
        return skill != null && cap.getSkillLevel(skill) >= requiredSkillLevel;
    }

    /** Convenience for builder lambdas in RegistryPowers — same shape as Perk.add for KubeJS parity. */
    public static Power of(String path,
                           PowerTier tier,
                           ResourceLocation schoolId,
                           Supplier<Skill> governingSkill,
                           int requiredSkillLevel,
                           ResourceLocation texture,
                           int defaultIcdTicks) {
        return new Power(new ResourceLocation(RunicSkills.MOD_ID, path), tier, schoolId,
                governingSkill, requiredSkillLevel, texture, defaultIcdTicks, null);
    }

    public static Power ofIss(String path,
                              PowerTier tier,
                              ResourceLocation schoolId,
                              Supplier<Skill> governingSkill,
                              int requiredSkillLevel,
                              ResourceLocation texture,
                              int defaultIcdTicks) {
        return new Power(new ResourceLocation(RunicSkills.MOD_ID, path), tier, schoolId,
                governingSkill, requiredSkillLevel, texture, defaultIcdTicks, "irons_spellbooks");
    }
}
