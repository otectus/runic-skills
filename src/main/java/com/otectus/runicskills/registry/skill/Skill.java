package com.otectus.runicskills.registry.skill;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPassives;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.passive.Passive;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class Skill {
    public final int index;
    public final ResourceLocation key;
    public final ResourceLocation[] lockedTexture;
    public final ResourceLocation background;
    public List<Perk> list = new ArrayList<>();

    // Optional datapack-driven visual override (since 1.3.0). Written by the
    // SkillVisualsReloadListener on /reload (off-thread relative to render);
    // read by RunicSkillsScreen during rendering.
    private volatile SkillVisuals visuals;

    public Skill(int index, ResourceLocation key, ResourceLocation[] lockedTexture, ResourceLocation background) {
        this.index = index;
        this.key = key;
        this.lockedTexture = lockedTexture;
        this.background = background;
    }

    public Skill get() {
        return this;
    }

    public String getName() {
        return this.key.getPath();
    }

    public String getKey() {
        return "skill." + this.key.toLanguageKey();
    }

    public String getDescription() {
        return getKey() + ".description";
    }

    public void setList(List<Perk> list) {
        this.list = list;
    }

    // Both of these iterate the memoised registry snapshot once.
    //
    // They previously called `PERKS_REGISTRY.get().getValues().stream().toList()` twice per
    // iteration — once to evaluate the loop bound and once to index the element — so listing the
    // perks of a single skill copied all 471 registry entries 942 times, roughly 450,000 element
    // copies per GUI rebuild, and the Skills screen rebuilds on every page change (RS-077).

    public List<Perk> getPerks(Skill skill) {
        List<Perk> list = new ArrayList<>();
        for (Perk perk : RegistryPerks.getCachedValues()) {
            if (perk.getSkill() == skill) list.add(perk);
        }
        return list;
    }

    public List<Passive> getPassives(Skill skill) {
        List<Passive> list = new ArrayList<>();
        for (Passive passive : RegistryPassives.getCachedValues()) {
            if (passive.getSkill() == skill) list.add(passive);
        }
        return list;
    }


    public int getLevel() {
        SkillCapability cap = SkillCapability.getLocal();
        return cap == null ? 1 : cap.getSkillLevel(this);
    }

    public int getLevel(Player player) {
        SkillCapability cap = SkillCapability.get(player);
        return cap == null ? 1 : cap.getSkillLevel(this);
    }

    public MutableComponent getRank(int skillLevel) {
        int rank = com.otectus.runicskills.common.util.SkillLevelUpMath.rankIndex(skillLevel,
                HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
        return Component.translatable("skill.runicskills.rank." + rank);
    }

    public ResourceLocation getLockedTexture(int fromLevel) {
        return lockedTextureFor(fromLevel);
    }

    public ResourceLocation getLockedTexture() {
        return lockedTextureFor(getLevel());
    }

    /**
     * Picks the locked-skill icon for a displayed level.
     *
     * <p>Both entry points used to <em>write</em> to the capability from here —
     * {@code SkillCapability.getLocal().setSkillLevel(this, size)} — whenever the player's level
     * exceeded a lowered {@code skillMaxLevel}. That is a render-path mutation of authoritative
     * progression state, performed on the client, against a nullable accessor that NPE'd whenever
     * the capability had not resolved yet. It also could not achieve anything: the server still
     * held the real level, so the "fix" lasted until the next sync and then reappeared (RS-081,
     * RS-090). Clamping the index locally displays the right icon and changes no state; lowering
     * the cap is reconciled server-side where it belongs.
     */
    private ResourceLocation lockedTextureFor(int level) {
        int size = Math.max(1, HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
        int textureListSize = this.lockedTexture.length;
        if (textureListSize == 0) return null;

        int index = Math.floorDiv(Math.min(level, size) * textureListSize, size);
        index = Math.min(index, textureListSize - 1);
        return this.lockedTexture[Math.max(0, index)];
    }

    // ===== Visual override layer (since 1.3.0) =====

    public void setVisuals(SkillVisuals visuals) {
        this.visuals = visuals;
    }

    public void clearVisuals() {
        this.visuals = null;
    }

    public SkillVisuals getVisuals() {
        return this.visuals;
    }

    public ResourceLocation getOverviewIcon() {
        SkillVisuals v = this.visuals;
        if (v != null && v.overviewIcon() != null) return v.overviewIcon();
        return getLockedTexture();
    }

    public ResourceLocation getDetailIcon() {
        SkillVisuals v = this.visuals;
        if (v != null && v.detailIcon() != null) return v.detailIcon();
        return getLockedTexture();
    }

    public ResourceLocation getBackgroundTexture() {
        SkillVisuals v = this.visuals;
        if (v != null && v.background() != null) return v.background();
        return this.background;
    }
}


