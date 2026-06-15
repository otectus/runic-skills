package com.otectus.runicskills.common.capability;

import com.otectus.runicskills.common.model.Skills;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.packet.client.SkillOverlayCP;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerTier;
import com.otectus.runicskills.registry.skill.Skill;
import com.otectus.runicskills.registry.passive.Passive;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.registry.title.Title;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class SkillCapability implements INBTSerializable<CompoundTag> {
    public static final String COOLDOWN_COUNTER_ATTACK = "counter_attack";
    public static final String COOLDOWN_COUNTER_ATTACK_TIMER = "counter_attack_timer";
    public static final String COOLDOWN_LIMIT_BREAKER = "limit_breaker";
    public static final String COOLDOWN_PERK_SWAP = "perk_swap";

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

    // Powers system (RUNIC_SKILLS_POWERS.md). Marks/Seals/Crown are equipped slots; cooldowns
    // and windows are runtime state keyed by Power id (path only, mod-id implicit). Caps mirror
    // PowerTier.maxEquipped — the SP packet enforces server-side, the lists here are trusted.
    public List<String> equippedMarks = new ArrayList<>();
    public List<String> equippedSeals = new ArrayList<>();
    public String equippedCrown = "";
    /** powerId → absolute server game-time at which the Power becomes available again (ICDs). */
    public Map<String, Long> powerCooldowns = new HashMap<>();
    /** powerId → absolute server game-time at which a buffered window/proc expires. */
    public Map<String, Long> powerWindows = new HashMap<>();

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

    // S6 fix: perk-keyed cooldown accessors that derive the key from the Perk itself,
    // avoiding new hardcoded constants whenever a perk wants a cooldown.
    public int getCooldown(Perk perk) {
        return perk == null ? 0 : getCooldown("perk." + perk.getName());
    }

    public void setCooldown(Perk perk, int ticks) {
        if (perk != null) setCooldown("perk." + perk.getName(), ticks);
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

    // ── Powers ─────────────────────────────────────────────────────────────────

    /** Equipped power-ids in the given tier (path only, mod-id implicit; never null, may be empty). */
    public List<String> getEquippedPowers(PowerTier tier) {
        return switch (tier) {
            case MARK -> this.equippedMarks;
            case SEAL -> this.equippedSeals;
            case CROWN -> this.equippedCrown.isEmpty() ? Collections.emptyList() : List.of(this.equippedCrown);
        };
    }

    public boolean isPowerEquipped(Power power) {
        if (power == null) return false;
        return isPowerEquipped(power.getName(), power.getTier());
    }

    public boolean isPowerEquipped(String powerName, PowerTier tier) {
        return switch (tier) {
            case MARK -> this.equippedMarks.contains(powerName);
            case SEAL -> this.equippedSeals.contains(powerName);
            case CROWN -> this.equippedCrown.equals(powerName);
        };
    }

    /** Returns true if the slot was free and the power was added; false on duplicate or full. */
    public boolean equipPower(Power power) {
        if (power == null) return false;
        String name = power.getName();
        switch (power.getTier()) {
            case MARK -> {
                if (this.equippedMarks.contains(name) || this.equippedMarks.size() >= PowerTier.MARK.maxEquipped) return false;
                this.equippedMarks.add(name);
                return true;
            }
            case SEAL -> {
                if (this.equippedSeals.contains(name) || this.equippedSeals.size() >= PowerTier.SEAL.maxEquipped) return false;
                this.equippedSeals.add(name);
                return true;
            }
            case CROWN -> {
                if (!this.equippedCrown.isEmpty()) return false;
                this.equippedCrown = name;
                return true;
            }
        }
        return false;
    }

    public boolean unequipPower(Power power) {
        if (power == null) return false;
        String name = power.getName();
        return switch (power.getTier()) {
            case MARK -> this.equippedMarks.remove(name);
            case SEAL -> this.equippedSeals.remove(name);
            case CROWN -> {
                if (this.equippedCrown.equals(name)) {
                    this.equippedCrown = "";
                    yield true;
                }
                yield false;
            }
        };
    }

    /** Power cooldowns are absolute game-times. Returns true while the cooldown is still active. */
    public boolean isPowerOnCooldown(String powerName, long now) {
        Long until = this.powerCooldowns.get(powerName);
        return until != null && until > now;
    }

    public void setPowerCooldown(String powerName, long availableAt) {
        if (availableAt <= 0) {
            this.powerCooldowns.remove(powerName);
        } else {
            this.powerCooldowns.put(powerName, availableAt);
        }
    }

    public boolean isPowerWindowActive(String powerName, long now) {
        Long until = this.powerWindows.get(powerName);
        return until != null && until > now;
    }

    public void setPowerWindow(String powerName, long expiresAt) {
        if (expiresAt <= 0) {
            this.powerWindows.remove(powerName);
        } else {
            this.powerWindows.put(powerName, expiresAt);
        }
    }

    public long getPowerWindowExpiry(String powerName) {
        Long until = this.powerWindows.get(powerName);
        return until == null ? 0L : until;
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

    // Null-safe registry lookups: ForgeRegistries.*.getKey(...) returns null for an
    // unregistered/modded entry (e.g. an item from a mod being removed). A null id cannot match
    // any lock entry, so use is allowed. The previous Objects.requireNonNull(...) threw an NPE here
    // — on the server during a use/attack/equip, mid-gameplay — instead of gracefully allowing it.
    // This mirrors the null check already present in ClientCapabilityAccess.canUseItemClient.
    public boolean canUseItem(Player player, ItemStack item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.getItem());
        return id == null || canUse(player, id);
    }

    public boolean canUseItem(Player player, ResourceLocation resourceLocation) {
        return canUse(player, resourceLocation);
    }

    public boolean canUseSpecificID(Player player, String specificID){
        return canUse(player, specificID);
    }

    public boolean canUseBlock(Player player, Block block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        return id == null || canUse(player, id);
    }

    public boolean canUseEntity(Player player, Entity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id == null || canUse(player, id);
    }

    private boolean canUse(Player player, ResourceLocation resource) {
        if (!HandlerCommonConfig.HANDLER.instance().enableItemLocks) return true;
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
        if (!HandlerCommonConfig.HANDLER.instance().enableItemLocks) return true;
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
        // getOrDefault (not get): a registry entry added after this cap was constructed — or a key
        // dropped during copyFrom — would otherwise unbox null and NPE during save. Defaults mirror
        // deserializeNBT (skill 1, passive 0, title.Requirement), matching the perk path below.
        for (Skill skill : RegistrySkills.getCachedValues()){
            nbt.putInt("skill." + skill.getName(), this.skillLevel.getOrDefault(skill.getName(), 1));
        }
        for (Passive passive : RegistryPassives.getCachedValues()){
            nbt.putInt("passive." + passive.getName(), this.passiveLevel.getOrDefault(passive.getName(), 0));
        }
        for (Perk perk : RegistryPerks.getCachedValues()){
            nbt.putInt("perk." + perk.getName(), this.perkRank.getOrDefault(perk.getName(), 0));
        }
        for (Title title : RegistryTitles.getCachedValues()){
            nbt.putBoolean("title." + title.getName(), this.unlockTitle.getOrDefault(title.getName(), title.Requirement));
        }
        CompoundTag cooldownsTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : this.perkCooldowns.entrySet()) {
            cooldownsTag.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("perkCooldowns", cooldownsTag);

        // Powers
        ListTag marksTag = new ListTag();
        for (String n : this.equippedMarks) marksTag.add(StringTag.valueOf(n));
        nbt.put("power.equippedMarks", marksTag);
        ListTag sealsTag = new ListTag();
        for (String n : this.equippedSeals) sealsTag.add(StringTag.valueOf(n));
        nbt.put("power.equippedSeals", sealsTag);
        nbt.putString("power.equippedCrown", this.equippedCrown);
        CompoundTag powerCdTag = new CompoundTag();
        for (Map.Entry<String, Long> e : this.powerCooldowns.entrySet()) {
            powerCdTag.putLong(e.getKey(), e.getValue());
        }
        nbt.put("powerCooldowns", powerCdTag);
        CompoundTag powerWinTag = new CompoundTag();
        for (Map.Entry<String, Long> e : this.powerWindows.entrySet()) {
            powerWinTag.putLong(e.getKey(), e.getValue());
        }
        nbt.put("powerWindows", powerWinTag);

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

        // Powers
        this.equippedMarks.clear();
        if (nbt.contains("power.equippedMarks", Tag.TAG_LIST)) {
            ListTag marksTag = nbt.getList("power.equippedMarks", Tag.TAG_STRING);
            for (int i = 0; i < marksTag.size(); i++) this.equippedMarks.add(marksTag.getString(i));
        }
        this.equippedSeals.clear();
        if (nbt.contains("power.equippedSeals", Tag.TAG_LIST)) {
            ListTag sealsTag = nbt.getList("power.equippedSeals", Tag.TAG_STRING);
            for (int i = 0; i < sealsTag.size(); i++) this.equippedSeals.add(sealsTag.getString(i));
        }
        this.equippedCrown = nbt.contains("power.equippedCrown") ? nbt.getString("power.equippedCrown") : "";
        this.powerCooldowns.clear();
        if (nbt.contains("powerCooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag cdTag = nbt.getCompound("powerCooldowns");
            for (String key : cdTag.getAllKeys()) this.powerCooldowns.put(key, cdTag.getLong(key));
        }
        this.powerWindows.clear();
        if (nbt.contains("powerWindows", Tag.TAG_COMPOUND)) {
            CompoundTag winTag = nbt.getCompound("powerWindows");
            for (String key : winTag.getAllKeys()) this.powerWindows.put(key, winTag.getLong(key));
        }

        this.playerTitle = nbt.contains("playerTitle") ? nbt.getString("playerTitle") : RegistryTitles.getTitle("titleless").getName();
        this.betterCombatEntityRange = nbt.getDouble("betterCombatEntityRange");
    }

    public void copyFrom(SkillCapability source) {
        // getOrDefault: tolerate a source map missing a key (e.g. registry grew across the clone)
        // instead of storing null, which would then NPE on the next serializeNBT.
        for (Skill skill : RegistrySkills.getCachedValues()){
            this.skillLevel.put(skill.getName(), source.skillLevel.getOrDefault(skill.getName(), 1));
        }
        for (Passive passive : RegistryPassives.getCachedValues()){
            this.passiveLevel.put(passive.getName(), source.passiveLevel.getOrDefault(passive.getName(), 0));
        }
        for (Perk perk : RegistryPerks.getCachedValues()){
            this.perkRank.put(perk.getName(), source.perkRank.getOrDefault(perk.getName(), 0));
        }
        for (Title title : RegistryTitles.getCachedValues()){
            this.unlockTitle.put(title.getName(), source.unlockTitle.getOrDefault(title.getName(), title.Requirement));
        }

        this.perkCooldowns = new HashMap<>(source.perkCooldowns);

        this.equippedMarks = new ArrayList<>(source.equippedMarks);
        this.equippedSeals = new ArrayList<>(source.equippedSeals);
        this.equippedCrown = source.equippedCrown;
        this.powerCooldowns = new HashMap<>(source.powerCooldowns);
        this.powerWindows = new HashMap<>(source.powerWindows);

        this.playerTitle = source.playerTitle;
        this.betterCombatEntityRange = source.betterCombatEntityRange;
    }
}


