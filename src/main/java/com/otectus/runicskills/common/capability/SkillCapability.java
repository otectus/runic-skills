package com.otectus.runicskills.common.capability;

import com.otectus.runicskills.common.model.Skills;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.packet.client.SkillOverlayCP;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.skill.Skill;
import com.otectus.runicskills.registry.passive.Passive;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.registry.title.Title;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class SkillCapability implements INBTSerializable<CompoundTag> {
    public static final String COOLDOWN_COUNTER_ATTACK = "counter_attack";
    public static final String COOLDOWN_COUNTER_ATTACK_TIMER = "counter_attack_timer";
    public static final String COOLDOWN_LIMIT_BREAKER = "limit_breaker";

    /**
     * Set by the client during FMLClientSetupEvent to supply the local player's capability.
     * On dedicated servers this stays null and {@link #getLocal()} returns null.
     */
    @Nullable
    public static Supplier<SkillCapability> LOCAL_SUPPLIER = null;

    /**
     * Returns the local (client-side) player's capability, or null on a dedicated server
     * or before the supplier has been registered.
     */
    @Nullable
    public static SkillCapability getLocal() {
        return LOCAL_SUPPLIER != null ? LOCAL_SUPPLIER.get() : null;
    }

    public Map<String, Integer> skillLevel = mapSkills();
    public Map<String, Integer> passiveLevel = mapPassive();
    public Map<String, Integer> perkRank = mapPerks();
    public Map<String, Boolean> unlockTitle = mapTitles();
    public String playerTitle = RegistryTitles.getTitle("titleless").getName();
    public double betterCombatEntityRange = 0.0D;

    public Map<String, Integer> perkCooldowns = new HashMap<>();

    private Map<String, Integer> mapSkills() {
        Map<String, Integer> map = new HashMap<>();
        List<Skill> skillList = RegistrySkills.getCachedValues();
        for (Skill skill : skillList) {
            map.put(skill.getName(), 1);
        }
        return map;
    }

    private Map<String, Integer> mapPassive() {
        Map<String, Integer> map = new HashMap<>();
        List<Passive> passiveList = RegistryPassives.getCachedValues();
        for (Passive passive : passiveList) {
            map.put(passive.getName(), 0);
        }
        return map;
    }

    private Map<String, Integer> mapPerks() {
        Map<String, Integer> map = new HashMap<>();
        List<Perk> perkList = RegistryPerks.getCachedValues();
        for (Perk perk : perkList) {
            map.put(perk.getName(), 0);
        }
        return map;
    }

    private Map<String, Boolean> mapTitles() {
        Map<String, Boolean> map = new HashMap<>();
        List<Title> titleList = RegistryTitles.getCachedValues();
        for (Title title : titleList) {
            map.put(title.getName(), title.Requirement);
        }
        return map;
    }

    public int getCooldown(String perkName) {
        return this.perkCooldowns.getOrDefault(perkName, 0);
    }

    public void setCooldown(String perkName, int ticks) {
        if (ticks <= 0) {
            this.perkCooldowns.remove(perkName);
        } else {
            this.perkCooldowns.put(perkName, ticks);
        }
    }

    public void tickCooldowns() {
        perkCooldowns.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - 1);
            return entry.getValue() <= 0;
        });
    }

    // Legacy accessors for counter attack (delegates to generic cooldown)
    public boolean getCounterAttack() {
        return getCooldown(COOLDOWN_COUNTER_ATTACK) > 0;
    }

    public void setCounterAttack(boolean set) {
        if (!set) setCooldown(COOLDOWN_COUNTER_ATTACK, 0);
    }

    public int getCounterAttackTimer() {
        return getCooldown(COOLDOWN_COUNTER_ATTACK_TIMER);
    }

    public void setCounterAttackTimer(int timer) {
        setCooldown(COOLDOWN_COUNTER_ATTACK_TIMER, timer);
    }

    @Nullable
    public static SkillCapability get(Player player) {
        LazyOptional<SkillCapability> capability = player.getCapability(RegistryCapabilities.SKILL);
        if(capability.isPresent() && capability.resolve().isPresent()){
            return capability.resolve().get();
        }

        return null;
    }

    public int getSkillLevel(Skill skill) {
        return this.skillLevel.get(skill.getName());
    }

    public int getSkillLevel(String skillName) {
        return this.skillLevel.get(skillName);
    }

    public void setSkillLevel(Skill skill, int lvl) {
        this.skillLevel.put(skill.getName(), lvl);
    }

    public int getGlobalLevel(){
        return this.skillLevel.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void addSkillLevel(Skill skill, int addLvl) {
        this.skillLevel.put(skill.getName(), Math.min(this.skillLevel.get(skill.getName()) + addLvl, HandlerCommonConfig.HANDLER.instance().skillMaxLevel));
    }

    public int getPassiveLevel(Passive passive) {
        return this.passiveLevel.get(passive.getName());
    }

    public void addPassiveLevel(Passive passive, int addLvl) {
        this.passiveLevel.put(passive.getName(), Math.min(this.passiveLevel.get(passive.getName()) + addLvl, passive.levelsRequired.length));
    }

    public void subPassiveLevel(Passive passive, int subLvl) {
        this.passiveLevel.put(passive.getName(), Math.max(this.passiveLevel.get(passive.getName()) - subLvl, 0));
    }

    public int getPerkRank(Perk perk) {
        return this.perkRank.getOrDefault(perk.getName(), 0);
    }

    public void setPerkRank(Perk perk, int rank) {
        this.perkRank.put(perk.getName(), rank);
    }

    public boolean isPerkActive(Perk perk) {
        return getPerkRank(perk) >= 1;
    }

    // Backward-compatible accessors used by existing code
    public boolean getTogglePerk(Perk perk) {
        return isPerkActive(perk);
    }

    public void setTogglePerk(Perk perk, boolean toggle) {
        setPerkRank(perk, toggle ? Math.max(1, getPerkRank(perk)) : 0);
    }

    public boolean getLockTitle(Title title) {
        return this.unlockTitle.get(title.getName());
    }

    public void setUnlockTitle(Title title, boolean requirement) {
        this.unlockTitle.put(title.getName(), requirement);
    }

    public String getPlayerTitle() {
        return this.playerTitle;
    }

    public void setPlayerTitle(Title title) {
        this.playerTitle = title.getName();
    }

    public boolean canUseItem(Player player, ItemStack item) {
        return canUse(player, Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item.getItem())));
    }

    public boolean canUseItem(Player player, ResourceLocation resourceLocation) {
        return canUse(player, resourceLocation);
    }

    public boolean canUseSpecificID(Player player, String specificID){
        return canUse(player, specificID);
    }

    public boolean canUseBlock(Player player, Block block) {
        return canUse(player, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(block)));
    }

    public boolean canUseEntity(Player player, Entity entity) {
        return canUse(player, Objects.requireNonNull(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())));
    }

    private boolean canUse(Player player, ResourceLocation resource) {
        List<Skills> skill = HandlerSkill.getValue(resource.toString());
        if (skill != null) {
            for (Skills skills : skill) {
                if (getSkillLevel(skills.getSkill()) < skills.getSkillLvl()) {
                    if (player instanceof net.minecraft.server.level.ServerPlayer)
                        SkillOverlayCP.send(player, resource.toString());
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canUse(Player player, String restrictionID) {
        List<Skills> skill = HandlerSkill.getValue(restrictionID);
        if (skill != null) {
            for (Skills skills : skill) {
                if (getSkillLevel(skills.getSkill()) < skills.getSkillLvl()) {
                    if (player instanceof net.minecraft.server.level.ServerPlayer)
                        SkillOverlayCP.send(player, restrictionID);
                    return false;
                }
            }
        }
        return true;
    }

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        for (Skill skill : RegistrySkills.getCachedValues()){
            nbt.putInt("skill." + skill.getName(), this.skillLevel.get(skill.getName()));
        }
        for (Passive passive : RegistryPassives.getCachedValues()){
            nbt.putInt("passive." + passive.getName(), this.passiveLevel.get(passive.getName()));
        }
        for (Perk perk : RegistryPerks.getCachedValues()){
            nbt.putInt("perk." + perk.getName(), this.perkRank.getOrDefault(perk.getName(), 0));
        }
        for (Title title : RegistryTitles.getCachedValues()){
            nbt.putBoolean("title." + title.getName(), this.unlockTitle.get(title.getName()));
        }
        CompoundTag cooldownsTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : this.perkCooldowns.entrySet()) {
            cooldownsTag.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("perkCooldowns", cooldownsTag);
        nbt.putString("playerTitle", this.playerTitle);
        nbt.putDouble("betterCombatEntityRange", this.betterCombatEntityRange);
        return nbt;
    }

    public void deserializeNBT(CompoundTag nbt) {
        for (Skill skill : RegistrySkills.getCachedValues()) {
            String key = "skill." + skill.getName();
            this.skillLevel.put(skill.getName(), nbt.contains(key) ? nbt.getInt(key) : 1);
        }
        for (Passive passive : RegistryPassives.getCachedValues()) {
            String key = "passive." + passive.getName();
            this.passiveLevel.put(passive.getName(), nbt.contains(key) ? nbt.getInt(key) : 0);
        }
        for (Perk perk : RegistryPerks.getCachedValues()) {
            String key = "perk." + perk.getName();
            if (nbt.contains(key)) {
                byte tagType = nbt.getTagType(key);
                if (tagType == net.minecraft.nbt.Tag.TAG_BYTE) {
                    // Legacy boolean format: true -> rank 1, false -> rank 0
                    this.perkRank.put(perk.getName(), nbt.getBoolean(key) ? 1 : 0);
                } else if (tagType == net.minecraft.nbt.Tag.TAG_INT) {
                    this.perkRank.put(perk.getName(), nbt.getInt(key));
                } else {
                    this.perkRank.put(perk.getName(), 0);
                }
            } else {
                this.perkRank.put(perk.getName(), 0);
            }
        }
        for (Title title : RegistryTitles.getCachedValues()) {
            String key = "title." + title.getName();
            this.unlockTitle.put(title.getName(), nbt.contains(key) ? nbt.getBoolean(key) : title.Requirement);
        }

        // Load generic perk cooldowns
        this.perkCooldowns.clear();
        if (nbt.contains("perkCooldowns", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag cooldownsTag = nbt.getCompound("perkCooldowns");
            for (String key : cooldownsTag.getAllKeys()) {
                this.perkCooldowns.put(key, cooldownsTag.getInt(key));
            }
        }
        // Migrate legacy hardcoded cooldowns
        if (nbt.contains("counterAttackTimer")) {
            int timer = nbt.getInt("counterAttackTimer");
            if (timer > 0) this.perkCooldowns.put(COOLDOWN_COUNTER_ATTACK_TIMER, timer);
        }
        if (nbt.contains("counterAttack") && nbt.getBoolean("counterAttack")) {
            this.perkCooldowns.put(COOLDOWN_COUNTER_ATTACK, 1);
        }
        if (nbt.contains("limitBreakerCooldown")) {
            int cd = nbt.getInt("limitBreakerCooldown");
            if (cd > 0) this.perkCooldowns.put(COOLDOWN_LIMIT_BREAKER, cd);
        }

        this.playerTitle = nbt.contains("playerTitle") ? nbt.getString("playerTitle") : RegistryTitles.getTitle("titleless").getName();
        this.betterCombatEntityRange = nbt.getDouble("betterCombatEntityRange");
    }

    public void copyFrom(SkillCapability source) {
        for (Skill skill : RegistrySkills.getCachedValues()){
            this.skillLevel.put(skill.getName(), source.skillLevel.get(skill.getName()));
        }
        for (Passive passive : RegistryPassives.getCachedValues()){
            this.passiveLevel.put(passive.getName(), source.passiveLevel.get(passive.getName()));
        }
        for (Perk perk : RegistryPerks.getCachedValues()){
            this.perkRank.put(perk.getName(), source.perkRank.getOrDefault(perk.getName(), 0));
        }
        for (Title title : RegistryTitles.getCachedValues()){
            this.unlockTitle.put(title.getName(), source.unlockTitle.get(title.getName()));
        }

        this.perkCooldowns = new HashMap<>(source.perkCooldowns);
        this.playerTitle = source.playerTitle;
        this.betterCombatEntityRange = source.betterCombatEntityRange;
    }
}


