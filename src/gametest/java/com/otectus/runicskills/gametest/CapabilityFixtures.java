package com.otectus.runicskills.gametest;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.registry.RegistryPassives;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.RegistryTitles;
import com.otectus.runicskills.registry.passive.Passive;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.registry.powers.PowerTier;
import com.otectus.runicskills.registry.skill.Skill;
import com.otectus.runicskills.registry.title.Title;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * Builds a capability whose every structure holds non-default data, and compares two capabilities
 * for deep equality.
 *
 * <p>The progression-wipe bug this exists to catch (RS-007 / RS10-003) did not corrupt one field —
 * it silently replaced the player's entire capability with the blank one created at attach time.
 * A test that checks one skill integer would have passed against several plausible half-fixes, so
 * the fixture deliberately touches every map, list and scalar the capability owns, including the
 * retained-orphan store, and equality is judged on the full serialized form rather than on a
 * hand-written field list that could fall behind the class.
 */
public final class CapabilityFixtures {

    private CapabilityFixtures() {}

    /** A key no registry claims, so it lands in the retained-orphan store on the next load. */
    public static final String ORPHAN_KEY = "somemod:retained_fixture_value";

    /**
     * Fills {@code capability} with values that differ from every default.
     *
     * <p>Values vary per entry rather than being a single constant, so a copy that shifts, zips or
     * truncates the maps fails as loudly as one that drops them.
     */
    public static void populate(SkillCapability capability) {
        int i = 0;
        for (Skill skill : RegistrySkills.getCachedValues()) {
            capability.skillLevel.put(skill.getName(), 2 + (i++ % 7));
        }
        i = 0;
        for (Passive passive : RegistryPassives.getCachedValues()) {
            capability.passiveLevel.put(passive.getName(), 1 + (i++ % 3));
        }
        i = 0;
        for (Perk perk : RegistryPerks.getCachedValues()) {
            // Rank 0 for most perks (that is the realistic shape), but a spread of non-zero ranks
            // often enough that a truncated copy cannot coincidentally match.
            capability.perkRank.put(perk.getName(), (i++ % 5 == 0) ? 1 + (i % 3) : 0);
        }
        boolean unlocked = true;
        Title firstTitle = null;
        for (Title title : RegistryTitles.getCachedValues()) {
            if (firstTitle == null) firstTitle = title;
            capability.unlockTitle.put(title.getName(), unlocked);
            unlocked = !unlocked;
        }
        if (firstTitle != null) capability.playerTitle = firstTitle.getName();

        capability.betterCombatEntityRange = 3.75D;

        capability.perkCooldowns.clear();
        capability.perkCooldowns.put(SkillCapability.COOLDOWN_COUNTER_ATTACK, 1);
        capability.perkCooldowns.put(SkillCapability.COOLDOWN_COUNTER_ATTACK_TIMER, 137);
        capability.perkCooldowns.put(SkillCapability.COOLDOWN_LIMIT_BREAKER, 2400);
        capability.perkCooldowns.put(SkillCapability.COOLDOWN_PERK_SWAP, 55);

        // Slot lists stay inside their tier's capacity and hold no duplicates, because the load
        // path enforces both — a fixture that violated them would "fail" a correct round trip.
        capability.equippedMarks.clear();
        capability.equippedMarks.addAll(markFixture());
        capability.equippedSeals.clear();
        capability.equippedSeals.addAll(sealFixture());
        capability.equippedCrown = "fixture_crown";

        capability.powerCooldowns.clear();
        capability.powerCooldowns.put("fixture_mark_a", 12_345L);
        capability.powerCooldowns.put("fixture_crown", 987_654L);
        capability.powerWindows.clear();
        capability.powerWindows.put("fixture_seal_a", 24_680L);
    }

    private static List<String> markFixture() {
        List<String> all = List.of("fixture_mark_a", "fixture_mark_b", "fixture_mark_c",
                "fixture_mark_d", "fixture_mark_e");
        return all.subList(0, Math.min(all.size(), PowerTier.MARK.maxEquipped));
    }

    private static List<String> sealFixture() {
        List<String> all = List.of("fixture_seal_a", "fixture_seal_b", "fixture_seal_c");
        return all.subList(0, Math.min(all.size(), PowerTier.SEAL.maxEquipped));
    }

    /**
     * A serialized fixture that also carries an unrecognised key, so a round trip exercises the
     * retained-orphan path (RS-006) alongside everything else.
     */
    public static CompoundTag populatedNbtWithOrphan() {
        SkillCapability source = new SkillCapability();
        populate(source);
        CompoundTag nbt = source.serializeNBT();
        nbt.putString(ORPHAN_KEY, "must survive an uninstall/reinstall cycle");
        return nbt;
    }

    /**
     * Deep equality via the serialized form. {@code CompoundTag} equality is by value, so this
     * covers every field the capability persists — including any added after this test was
     * written, and including the retained-orphan store, which has no accessor.
     *
     * @return null when equal, otherwise a message naming the first difference
     */
    public static String difference(SkillCapability expected, SkillCapability actual) {
        CompoundTag a = expected.serializeNBT();
        CompoundTag b = actual.serializeNBT();
        if (a.equals(b)) return null;

        StringBuilder message = new StringBuilder("capability contents differ:");
        for (String key : a.getAllKeys()) {
            if (!b.contains(key)) {
                message.append("\n  missing key '").append(key).append("' (expected ").append(a.get(key)).append(')');
            } else if (!a.get(key).equals(b.get(key))) {
                message.append("\n  key '").append(key).append("': expected ")
                        .append(a.get(key)).append(", got ").append(b.get(key));
            }
        }
        for (String key : b.getAllKeys()) {
            if (!a.contains(key)) {
                message.append("\n  unexpected key '").append(key).append("' = ").append(b.get(key));
            }
        }
        return message.toString();
    }
}
