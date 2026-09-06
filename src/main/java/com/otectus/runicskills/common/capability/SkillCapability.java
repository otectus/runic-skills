package com.otectus.runicskills.common.capability;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.util.CapabilityBounds;
import com.otectus.runicskills.common.model.Skills;
import com.otectus.runicskills.common.equipment.RequirementDecision;
import com.otectus.runicskills.common.util.LockCheck;
import com.otectus.runicskills.integration.lock.LockAction;
import com.otectus.runicskills.integration.lock.LockProviderRegistry;
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
     * Schema version of the player data this class writes.
     *
     * <p>Version 0 is any save written before this field existed. Migration used to be tag-type
     * sniffing plus three hardcoded legacy key names, with no way to tell an old shape from a new
     * one — so any future change to what a stored number *means* (rank index becoming points
     * spent, say) would have been silently reinterpreted on every existing save, with no hook to
     * correct it (RS-005). Bump this and add a branch in {@link #migrate} whenever the meaning or
     * layout of stored data changes.
     *
     * <p>1 — first versioned schema. Identical in layout to the unversioned form; the bump exists
     * so later migrations have a floor to work from.
     *
     * <p>2 — same layout, but every stored value is now range- and count-checked on the way in by
     * {@link CapabilitySanitizer}. The bump is what makes the repair observable: a save at version
     * 2 has been through the sanitizer at least once, so support can tell "this data was never
     * validated" apart from "this data was validated and is still odd" (RS10-017).
     */
    public static final int DATA_VERSION = 2;

    private static final String KEY_DATA_VERSION = "dataVersion";
    private static final String KEY_ORPHANS = "runicskills:retained";

    /**
     * The Runic-owned compound for integration state (§15.1), schema 1.
     *
     * <p>Additive: absent means empty, so a save written by any earlier version loads unchanged and
     * one written here loads on an earlier version as a retained unknown key rather than as a
     * corrupt field. It holds one thing so far — the Artifice Power cooldown debt — and it holds it
     * as <em>remaining</em> ticks, because the runtime clock restarts at zero with the server and a
     * saved absolute deadline would read as already elapsed after every restart (RS18).
     */
    private static final String KEY_TC_STATE = "runicskills:tc_state";

    /** Schema version of {@link #KEY_TC_STATE}. A future shape change bumps this, not DATA_VERSION. */
    private static final int TC_STATE_VERSION = 1;

    private static final String KEY_TC_VERSION = "version";
    private static final String KEY_TC_COOLDOWNS = "cooldowns";

    /** §11.5: twelve cooldown records per player, and no more, whatever the file says. */
    private static final int MAX_TC_COOLDOWNS = 12;

    /** §15.1: one day of game ticks is the longest debt any of them may carry. */
    private static final int MAX_TC_COOLDOWN_TICKS = 24000;

    /** Upper bound on retained unknown keys, so a pathological save cannot grow player NBT without limit. */
    private static final int MAX_ORPHAN_KEYS = 4096;

    /**
     * Keys that were present on load but that no current registry entry or field claims.
     *
     * <p>Both serialization directions iterate the registry rather than the NBT, so a key whose
     * registry entry is absent was never read and never re-written — the first autosave erased it.
     * Titles are registered from an editable config file, so an operator removing one title
     * destroyed every player's record of having earned it, permanently and silently; the same
     * applied to any addon that was temporarily uninstalled (RS-006). These are replayed verbatim
     * on save so the data survives until whatever owns it comes back.
     */
    private CompoundTag orphanTags = new CompoundTag();

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
    /**
     * Artifice Power cooldowns, as absolute server game-time, mirroring {@code PowerRuntime}.
     *
     * <p>Kept here as well as in the runtime map because the runtime map is cleared on logout and
     * §15.1 requires the debt to outlive that: "serialize the debt, clear runtime references, and
     * restore cleanly". Written at the moment a cooldown starts, converted to remaining ticks on
     * save and back to absolute on load, and restored into the runtime map at login by
     * {@link com.otectus.runicskills.common.powers.PowerCooldownDebt}.
     */
    public Map<String, Long> tcPowerCooldowns = new HashMap<>();

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

    /**
     * Whether the Counter Attack retaliation window is currently open.
     *
     * <p>The window is the cooldown itself: {@link #tickCooldowns()} decrements it once per tick,
     * so it expires on its own and no separate timer is needed. Previously the state was split
     * across two entries and neither worked — {@code setCounterAttack(true)} was a no-op, so this
     * method could never return true, so the two call sites that clear the retaliation bonus never
     * ran and the ATTACK_DAMAGE modifier it granted was permanent and NBT-persisted. The
     * accompanying timer was stored in the same map {@code tickCooldowns()} decrements, so even
     * had the flag worked, the timer was pinned and could never reach its expiry threshold
     * (RS-011).
     */
    public boolean getCounterAttack() {
        return getCooldown(COOLDOWN_COUNTER_ATTACK) > 0;
    }

    /** Opens the retaliation window for {@code windowTicks}; {@code 0} closes it immediately. */
    public void setCounterAttack(int windowTicks) {
        setCooldown(COOLDOWN_COUNTER_ATTACK, Math.max(0, windowTicks));
    }

    /** Closes the retaliation window. */
    public void clearCounterAttack() {
        setCooldown(COOLDOWN_COUNTER_ATTACK, 0);
        // Drop the pre-1.7.0 companion entry so it stops round-tripping through NBT.
        setCooldown(COOLDOWN_COUNTER_ATTACK_TIMER, 0);
    }

    /**
     * Per-thread single-entry memo for {@link #get(Player)}. A single melee hit runs hundreds of
     * perk checks across eight LivingHurtEvent handlers, each re-resolving the capability through
     * the Forge dispatcher; within one (player, tick) the result cannot change, so consecutive
     * lookups hit this memo instead. Null results are deliberately NOT memoized — the capability
     * attaches during entity construction at tickCount 0, so caching an early null would poison
     * every read until the first tick (including login-time capability sync).
     * ThreadLocal keeps client and server threads of an integrated server independent.
     */
    private static final ThreadLocal<Object[]> GET_MEMO = ThreadLocal.withInitial(() -> new Object[3]);

    @Nullable
    public static SkillCapability get(Player player) {
        if (player == null) return null;
        Object[] memo = GET_MEMO.get();
        if (memo[0] == player && memo[1] instanceof Integer tick && tick == player.tickCount) {
            return (SkillCapability) memo[2];
        }
        LazyOptional<SkillCapability> capability = player.getCapability(RegistryCapabilities.SKILL);
        if (capability.isPresent() && capability.resolve().isPresent()) {
            SkillCapability resolved = capability.resolve().get();
            memo[0] = player;
            memo[1] = player.tickCount;
            memo[2] = resolved;
            return resolved;
        }

        return null;
    }

    public int getSkillLevel(Skill skill) {
        return safeLevel(skill.getName());
    }

    public int getSkillLevel(String skillName) {
        return safeLevel(skillName);
    }

    // Null-safe level read: a skill name not present in the map (e.g. an item locked against a
    // skill from a no-longer-loaded addon, or a skill registered after this capability was
    // built) resolves to the default level 1 instead of unboxing null into an NPE mid-attack/use.
    // Mirrors the skill-1 default used throughout (de)serializeNBT. Both getSkillLevel overloads
    // delegate here — mapSkills() seeds every registered skill, so present keys behave
    // identically; only the previously-NPE absent-key case changes.
    private int safeLevel(String skillName) {
        Integer level = this.skillLevel.get(skillName);
        return level == null ? 1 : level;
    }

    public void setSkillLevel(Skill skill, int lvl) {
        this.skillLevel.put(skill.getName(), lvl);
    }

    /**
     * Sum of every skill level. Saturating rather than wrapping: this used to be a plain
     * {@code int} sum over values loaded straight from NBT, so a corrupt save could overflow it
     * into a negative number — which then reads as "below every requirement" at every site that
     * compares against it, silently locking a player out of their own content (RS10-013).
     * {@link CapabilitySanitizer} makes the inputs sane; this makes the sum safe regardless.
     */
    public int getGlobalLevel(){
        int total = 0;
        for (Integer level : this.skillLevel.values()) {
            if (level == null) continue;
            total = com.otectus.runicskills.common.util.CapabilityBounds.addSaturating(total, level);
        }
        return total;
    }

    /**
     * The starting global level of a fresh player: the sum of every skill's starting level.
     * Each skill seeds at level 1 (see {@link #mapSkills()}), so this equals the number of
     * registered skills today. Derived (not hardcoded) so it tracks the skill registry, and used
     * only to normalise the perk-budget scaling — {@link #getGlobalLevel()} itself is unchanged.
     */
    public static int baselineGlobalLevel() {
        return RegistrySkills.getCachedValues().size();
    }

    /**
     * Global level a player has <em>earned</em> above the starting baseline, i.e.
     * {@code max(0, getGlobalLevel() - baselineGlobalLevel())}. A brand-new player is 0. This is the
     * value the {@code perksPerGlobalLevel} budget scales from, so {@code 0.5} grants the first perk
     * slot after 2 earned levels and 3 slots after 6, instead of granting slots for the baseline a
     * fresh player already has.
     */
    public int getEarnedGlobalLevelForPerkBudget() {
        return Math.max(0, getGlobalLevel() - baselineGlobalLevel());
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

    /**
     * Removes a Power id from whichever slot holds it, without needing the id to resolve.
     *
     * <p>Unknown ids are deliberately retained on load — uninstalling an addon for one session must
     * not silently clear a player's loadout. But retention left them unclearable: the equip packet
     * looked the id up in the registry to decide which tier's list to touch, so an id that no
     * longer resolved occupied a slot with no way to reclaim it short of a full respec, which
     * resets every skill to 1 (RS10-006). Searching all three lists costs nothing and closes that
     * trap.
     *
     * @return true if a slot was freed
     */
    public boolean unequipUnknownPower(String powerName) {
        if (powerName == null || powerName.isEmpty()) return false;
        if (this.equippedMarks.remove(powerName)) return true;
        if (this.equippedSeals.remove(powerName)) return true;
        if (this.equippedCrown.equals(powerName)) {
            this.equippedCrown = "";
            return true;
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
        Boolean stackVerdict = stackLockVerdict(player, item, LockAction.USE, true);
        if (stackVerdict != null) return stackVerdict;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.getItem());
        return id == null || canUse(player, id);
    }

    /**
     * The stack-aware lock verdict for {@code item}, or {@code null} when no provider claims it and
     * the unchanged item-id path should decide.
     *
     * <p>Stack providers are asked first, not last. A provider that reads the stack knows something
     * the id cannot express — which material an item is made of, whether it is a hybrid tool being
     * swung rather than dug with — so an id rule that also matched would be the coarser of two
     * answers about the same item, and letting the coarse one win would make the fine one
     * unreachable.
     */
    private Boolean stackLockVerdict(Player player, ItemStack item, LockAction action, boolean notify) {
        if (item == null || item.isEmpty()) return null;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return null;
        if (!HandlerCommonConfig.HANDLER.instance().enableItemLocks) return null;

        java.util.Optional<RequirementDecision> resolved =
                LockProviderRegistry.resolveStack(serverPlayer, item, action);
        if (resolved.isEmpty()) return null;
        RequirementDecision decision = resolved.get();
        if (decision.allowed()) return Boolean.TRUE;

        if (notify) {
            if (decision.reason() != null) {
                serverPlayer.sendSystemMessage(decision.reason());
            } else {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.getItem());
                if (id != null) SkillOverlayCP.send(player, id.toString());
            }
        }
        return Boolean.FALSE;
    }

    /**
     * Same lock decision as {@link #canUseItem(Player, ItemStack)} but never sends the
     * SkillOverlayCP "requirement not met" packet. Used by per-tick / per-hit / per-break
     * backstops (melee damage, block breaking) so enforcement that fires many times a second
     * does not spam the client overlay — the one-shot warning still comes from the discrete
     * action events (attack swing, left/right-click).
     */
    public boolean canUseItemSilent(Player player, ItemStack item) {
        Boolean stackVerdict = stackLockVerdict(player, item, LockAction.USE, false);
        if (stackVerdict != null) return stackVerdict;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.getItem());
        return id == null || canUse(player, id.toString(), false);
    }

    /**
     * The lock decision for an item known only by its registry id.
     *
     * <p><b>Caveat.</b> This overload cannot enforce a requirement that depends on what an
     * individual stack is made of: every stack sharing a registry id is one item to it. Prefer
     * {@link #canUseItem(Player, ItemStack)} wherever a stack is in hand — it consults the
     * stack-aware providers first and falls through to exactly this rule when none claims the item.
     */
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
        return canUse(player, resource.toString(), true);
    }

    private boolean canUse(Player player, String restrictionID) {
        return canUse(player, restrictionID, true);
    }

    /**
     * Single source of truth for the lock decision. Short-circuits when item locks are disabled,
     * looks up the item's requirements, and delegates the level comparison to the pure, unit-tested
     * {@link LockCheck#meetsRequirements}. When {@code notify} is true and the requirement is not met,
     * the server tells the client to show the lock overlay.
     */
    private boolean canUse(Player player, String restrictionID, boolean notify) {
        if (!HandlerCommonConfig.HANDLER.instance().enableItemLocks) return true;
        List<Skills> skill = HandlerSkill.getValue(restrictionID);
        if (skill == null || skill.isEmpty()) return true;

        Map<String, Integer> required = new HashMap<>();
        for (Skills skills : skill) {
            if (skills.getSkill() != null) {
                required.put(skills.getSkill().getName(), skills.getSkillLvl());
            }
        }

        if (LockCheck.meetsRequirements(required, this::safeLevel)) return true;

        if (notify && player instanceof net.minecraft.server.level.ServerPlayer) {
            SkillOverlayCP.send(player, restrictionID);
        }
        return false;
    }

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        // Replay retained keys FIRST, so anything the registry currently owns is written over the
        // top of a stale copy rather than the other way round (RS-006).
        for (String key : this.orphanTags.getAllKeys()) {
            Tag value = this.orphanTags.get(key);
            if (value != null) nbt.put(key, value.copy());
        }
        nbt.putInt(KEY_DATA_VERSION, DATA_VERSION);
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

        nbt.put(KEY_TC_STATE, writeIntegrationState());

        nbt.putString("playerTitle", this.playerTitle);
        nbt.putDouble("betterCombatEntityRange", this.betterCombatEntityRange);
        return nbt;
    }

    /**
     * The integration compound: schema version, and the cooldown debt as remaining ticks.
     *
     * <p>An elapsed cooldown is not written at all, so a player who has not used a Power for a
     * month carries nothing for it. Time that passed while the server was down or the player was
     * offline does not count against the debt, which is exactly what storing what is <em>left</em>
     * rather than when it ends means.
     */
    private CompoundTag writeIntegrationState() {
        CompoundTag state = new CompoundTag();
        state.putInt(KEY_TC_VERSION, TC_STATE_VERSION);
        CompoundTag cooldowns = new CompoundTag();
        long now = serverTick();
        for (Map.Entry<String, Long> entry : this.tcPowerCooldowns.entrySet()) {
            if (cooldowns.size() >= MAX_TC_COOLDOWNS) break;
            long remaining = entry.getValue() - now;
            if (remaining <= 0L) continue;
            cooldowns.putInt(entry.getKey(), (int) Math.min(remaining, MAX_TC_COOLDOWN_TICKS));
        }
        state.put(KEY_TC_COOLDOWNS, cooldowns);
        return state;
    }

    /**
     * The integration compound, read back as absolute ticks against the clock running now.
     *
     * <p>Every value is bounded on the way in for the same reason the timer maps are: the file is
     * not a trusted input, and a debt of two billion ticks is a Power the player never gets back.
     */
    private void readIntegrationState(CompoundTag nbt) {
        this.tcPowerCooldowns.clear();
        if (!nbt.contains(KEY_TC_STATE, Tag.TAG_COMPOUND)) return;
        CompoundTag state = nbt.getCompound(KEY_TC_STATE);
        if (state.getInt(KEY_TC_VERSION) != TC_STATE_VERSION) return;
        CompoundTag cooldowns = state.getCompound(KEY_TC_COOLDOWNS);
        long now = serverTick();
        for (String key : cooldowns.getAllKeys()) {
            if (this.tcPowerCooldowns.size() >= MAX_TC_COOLDOWNS) break;
            if (!CapabilityBounds.isStorableKey(key)) continue;
            int remaining = cooldowns.getInt(key);
            if (remaining <= 0) continue;
            this.tcPowerCooldowns.put(key, now + Math.min(remaining, MAX_TC_COOLDOWN_TICKS));
        }
    }

    /** The running server's tick count, or {@code 0} when there is no server (unit tests). */
    private static long serverTick() {
        return RunicSkills.server == null ? 0L : RunicSkills.server.getTickCount();
    }

    /**
     * Applies ordered fixups to raw NBT before it is read, based on the schema version it was
     * written with. Version 0 is any pre-1.7.0 save.
     *
     * <p>Nothing structural changed in version 1, so this is currently only a hook — but it is the
     * hook every later data change needs, and it has to exist in a shipped version before the
     * first change that depends on it, or that change has no way to recognise old data (RS-005).
     */
    private void migrate(CompoundTag nbt, int fromVersion) {
        if (fromVersion >= DATA_VERSION) return;
        // 0 -> 1: the legacy perk-rank byte/int sniffing and the three hardcoded cooldown key
        // names are still handled inline below, because saves at version 0 are the common case
        // and the inline handling is already correct for them.
        //
        // 1 -> 2: no NBT rewriting is needed here. The change is a validation pass over the loaded
        // values, which has to run after they are read rather than before — see the
        // CapabilitySanitizer call at the end of deserializeNBT. It runs on every load, not only
        // on the version transition, so a save corrupted after migrating is still repaired; the
        // version bump exists to record that validation happened at all.
    }

    /**
     * Copies every key the load path did not claim into {@link #orphanTags} for write-back.
     *
     * <p>This is what stops a temporarily absent registry entry — a title deleted from
     * {@code titles.json5}, an uninstalled addon's perk — from being erased on the next autosave
     * (RS-006).
     */
    private void retainOrphans(CompoundTag nbt, java.util.Set<String> consumed) {
        this.orphanTags = new CompoundTag();
        int retained = 0;
        for (String key : nbt.getAllKeys()) {
            if (consumed.contains(key)) continue;
            if (retained >= MAX_ORPHAN_KEYS) {
                RunicSkills.getLOGGER().warn(
                        "Player data carries more than {} unrecognised keys; the remainder will not be "
                        + "preserved. This usually means a large mod was removed.", MAX_ORPHAN_KEYS);
                break;
            }
            Tag value = nbt.get(key);
            if (value == null) continue;
            this.orphanTags.put(key, value.copy());
            retained++;
        }
    }

    /**
     * Reads an equipped-Power slot list, dropping blanks and duplicates and stopping at the
     * tier's slot count. Powers that no longer exist in the registry are kept here rather than
     * discarded — removing an addon should not silently unequip a player's loadout — and are
     * filtered at use time instead.
     */
    /**
     * Reads a timer compound without letting the save decide how much memory and per-tick work to
     * commit to. Entry count and key length are bounded here rather than only afterwards, because
     * the cost this guards against is incurred while building the map: a hostile or corrupt
     * compound with a million keys used to become a million live map entries walked every tick for
     * as long as that player stayed online (RS10-017).
     *
     * @param toValue reads one entry's value; values are range-clamped later by the sanitizer
     */
    private static <V> int readBoundedTimers(CompoundTag source, Map<String, V> target,
                                             java.util.function.Function<String, V> toValue) {
        int skipped = 0;
        for (String key : source.getAllKeys()) {
            if (target.size() >= CapabilityBounds.MAX_TIMER_ENTRIES
                    || !CapabilityBounds.isStorableKey(key)) {
                skipped++;
                continue;
            }
            target.put(key, toValue.apply(key));
        }
        return skipped;
    }

    private static void readPowerSlots(ListTag source, List<String> target, int maxSlots) {
        for (int i = 0; i < source.size() && target.size() < maxSlots; i++) {
            String name = source.getString(i);
            if (name.isEmpty() || target.contains(name)) continue;
            target.add(name);
        }
    }

    public void deserializeNBT(CompoundTag nbt) {
        int fromVersion = nbt.contains(KEY_DATA_VERSION, Tag.TAG_INT) ? nbt.getInt(KEY_DATA_VERSION) : 0;
        migrate(nbt, fromVersion);
        // Every key this method reads is recorded, so whatever is left over can be retained.
        java.util.Set<String> consumed = new java.util.HashSet<>();
        consumed.add(KEY_DATA_VERSION);
        consumed.add(KEY_ORPHANS);

        for (Skill skill : RegistrySkills.getCachedValues()) {
            String key = "skill." + skill.getName();
            consumed.add(key);
            this.skillLevel.put(skill.getName(), nbt.contains(key, Tag.TAG_INT) ? nbt.getInt(key) : 1);
        }
        for (Passive passive : RegistryPassives.getCachedValues()) {
            String key = "passive." + passive.getName();
            consumed.add(key);
            this.passiveLevel.put(passive.getName(), nbt.contains(key, Tag.TAG_INT) ? nbt.getInt(key) : 0);
        }
        for (Perk perk : RegistryPerks.getCachedValues()) {
            String key = "perk." + perk.getName();
            consumed.add(key);
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
            consumed.add(key);
            this.unlockTitle.put(title.getName(), nbt.contains(key) ? nbt.getBoolean(key) : title.Requirement);
        }

        java.util.Collections.addAll(consumed,
                "perkCooldowns", "power.equippedMarks", "power.equippedSeals", "power.equippedCrown",
                "powerCooldowns", "powerWindows", "playerTitle", "betterCombatEntityRange",
                // Known, not orphaned: the retention pass writes back anything it does not
                // recognise, and a compound this class both reads and rewrites must not also be
                // replayed from a stale copy.
                KEY_TC_STATE,
                // Consumed by migrate(): read once and deliberately not carried forward.
                "counterAttackTimer", "counterAttack", "limitBreakerCooldown");
        retainOrphans(nbt, consumed);

        // Load generic perk cooldowns
        int skippedTimers = 0;
        this.perkCooldowns.clear();
        if (nbt.contains("perkCooldowns", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag cooldownsTag = nbt.getCompound("perkCooldowns");
            skippedTimers += readBoundedTimers(cooldownsTag, this.perkCooldowns, cooldownsTag::getInt);
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
        // Equipped Power lists are bounded and de-duplicated on load. They were previously read
        // straight out of NBT with no cap, no dedup and no existence check, so hand-edited or
        // stale player data could carry more Powers than there are slots, or the same one many
        // times over (RS-051).
        this.equippedMarks.clear();
        if (nbt.contains("power.equippedMarks", Tag.TAG_LIST)) {
            readPowerSlots(nbt.getList("power.equippedMarks", Tag.TAG_STRING), this.equippedMarks,
                    PowerTier.MARK.maxEquipped);
        }
        this.equippedSeals.clear();
        if (nbt.contains("power.equippedSeals", Tag.TAG_LIST)) {
            readPowerSlots(nbt.getList("power.equippedSeals", Tag.TAG_STRING), this.equippedSeals,
                    PowerTier.SEAL.maxEquipped);
        }
        this.equippedCrown = nbt.contains("power.equippedCrown") ? nbt.getString("power.equippedCrown") : "";
        this.powerCooldowns.clear();
        if (nbt.contains("powerCooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag cdTag = nbt.getCompound("powerCooldowns");
            skippedTimers += readBoundedTimers(cdTag, this.powerCooldowns, cdTag::getLong);
        }
        this.powerWindows.clear();
        if (nbt.contains("powerWindows", Tag.TAG_COMPOUND)) {
            CompoundTag winTag = nbt.getCompound("powerWindows");
            skippedTimers += readBoundedTimers(winTag, this.powerWindows, winTag::getLong);
        }

        readIntegrationState(nbt);

        this.playerTitle = nbt.contains("playerTitle") ? nbt.getString("playerTitle") : RegistryTitles.getTitle("titleless").getName();
        this.betterCombatEntityRange = nbt.getDouble("betterCombatEntityRange");

        // Defensive on every load, not only on the version transition: data can be corrupted or
        // hand-edited after it has already been migrated, and the rest of the mod assumes these
        // ranges everywhere (RS10-017). One summarised line per player, never one per bad key.
        CapabilitySanitizer.Report report = CapabilitySanitizer.sanitize(this);
        if (report.changedAnything() || skippedTimers > 0) {
            RunicSkills.getLOGGER().warn(
                    "Repaired out-of-range Runic Skills player data (save version {}): {}{}.",
                    fromVersion, report.summary(),
                    skippedTimers > 0 ? "; " + skippedTimers + " timer entr(y/ies) refused at read" : "");
        }
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
        // §15.1: death, respec and a dimension change remove a Power's benefit and keep its debt.
        this.tcPowerCooldowns = new HashMap<>(source.tcPowerCooldowns);

        this.playerTitle = source.playerTitle;
        this.betterCombatEntityRange = source.betterCombatEntityRange;
        // Retained keys have to survive the clone too, or death and dimension change would erase
        // exactly the data retention was added to protect (RS-006).
        this.orphanTags = source.orphanTags.copy();
    }
}


