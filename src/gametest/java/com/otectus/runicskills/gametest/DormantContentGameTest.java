package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.PowerEligibility;
import com.otectus.runicskills.registry.powers.PowerTier;
import com.otectus.runicskills.registry.powers.Power;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Removing an optional mod loses nothing and frees nothing (L02, §15.1).
 *
 * <p>This run <em>is</em> the scenario: Tinker's Construct is not installed, so every {@code tc_}
 * perk id in a save is a name the registry cannot resolve and every {@code tc_} Power is registered
 * but has no native seam behind it. What §15.1 requires of that state is three separate things, and
 * each is a different mechanism:
 *
 * <ul>
 *   <li>the saved ids survive a load and save, so reinstalling the mod restores the player's
 *       selection rather than a blank slate;</li>
 *   <li>an unresolvable perk id charges nothing against the active-perk budget, because a perk that
 *       cannot be shown or switched off must not be able to lock a player out of the rest;</li>
 *   <li>nothing becomes active on its own. A Power whose capability is missing is refused at the
 *       eligibility gate, and re-equipping it has to pass every requirement again — "never activate
 *       over-capacity content automatically".</li>
 * </ul>
 *
 * <p>{@code PerkBudgetDormancyGameTest} covers the budget arithmetic on its own; this is the round
 * trip and the activation rule around it.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class DormantContentGameTest {

    private static final String EMPTY = "empty";

    /** A perk id from the Tinkers' set, which is not registered on a server without that mod. */
    private static final String DORMANT_PERK = "tc_material_harmony";

    /** A Power from the Artifice set, which is registered everywhere but inert without the mod. */
    private static final String DORMANT_MARK = "tc_quench";

    @GameTest(template = EMPTY)
    public static void savedTinkersSelectionsSurviveALoadAndSave(GameTestHelper helper) {
        CompoundTag saved = new CompoundTag();
        saved.putInt("perk." + DORMANT_PERK, 1);
        ListTag marks = new ListTag();
        marks.add(StringTag.valueOf(DORMANT_MARK));
        saved.put("power.equippedMarks", marks);

        SkillCapability capability = new SkillCapability();
        capability.deserializeNBT(saved);
        CompoundTag written = capability.serializeNBT();

        if (written.getInt("perk." + DORMANT_PERK) != 1) {
            throw new GameTestAssertException("the saved rank of " + DORMANT_PERK + " was dropped on "
                    + "the first save; uninstalling a mod must not erase what the player bought");
        }
        if (!capability.getEquippedPowers(PowerTier.MARK).contains(DORMANT_MARK)) {
            throw new GameTestAssertException("an equipped Power was unequipped by a load");
        }
        ListTag writtenMarks = written.getList("power.equippedMarks", net.minecraft.nbt.Tag.TAG_STRING);
        if (writtenMarks.isEmpty() || !writtenMarks.getString(0).equals(DORMANT_MARK)) {
            throw new GameTestAssertException("the equipped Power was not written back");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aDormantPerkIdCostsNoBudget(GameTestHelper helper) {
        // The same class runs in both profiles, so the id being asked about has to be one that is
        // dormant in each. With Tinkers' absent that is the real perk id; with Tinkers' present the
        // real id is live content and must count, and the dormant case is any tc_ id this build does
        // not have. Both halves are asserted wherever they apply.
        boolean present = RegistryPerks.getPerk(DORMANT_PERK) != null;

        SkillCapability capability = new SkillCapability();
        capability.perkRank.put(DORMANT_PERK, 1);
        int counted = RegistryPerks.countEnabledPerks(capability);
        if (counted != (present ? 1 : 0)) {
            throw new GameTestAssertException(present
                    ? "a registered Tinkers' perk at rank 1 counted " + counted
                    : "a dormant perk id was charged against the budget (" + counted + "); the "
                      + "player cannot see it, use it or switch it off");
        }

        SkillCapability unresolvable = new SkillCapability();
        unresolvable.perkRank.put("tc_a_perk_no_build_of_this_mod_registers", 1);
        int dormant = RegistryPerks.countEnabledPerks(unresolvable);
        if (dormant != 0) {
            throw new GameTestAssertException("an unresolvable tc_ perk id was charged against the "
                    + "budget (" + dormant + "); removing an add-on would freeze the player's other "
                    + "perks until they respec");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void anUnavailablePowerIsNeitherActiveNorReEquippable(GameTestHelper helper) {
        Power power = RegistryPowers.getPower(DORMANT_MARK);
        if (power == null) {
            throw new GameTestAssertException(DORMANT_MARK + " is not registered; the Artifice "
                    + "Powers are registered on every install so a save holding one still resolves");
        }
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "dormant_content");
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) throw new GameTestAssertException("player has no skill capability");
        capability.equippedMarks.add(DORMANT_MARK);

        PowerEligibility.Result active = PowerEligibility.evaluateActive(player, power);
        if (active.eligible()) {
            throw new GameTestAssertException("an Artifice Power was active with no native seam "
                    + "behind it; an unavailable Power must contribute no effect");
        }
        // The specific reason is only meaningful where the integration is genuinely absent. With
        // Tinkers' installed a fresh mock player is refused for an ordinary skill requirement, which
        // is the same "not active, not automatic" outcome by a different route.
        if (!net.minecraftforge.fml.ModList.get().isLoaded("tconstruct")
                && active.reason() != PowerEligibility.Reason.MISSING_CAPABILITY
                && active.reason() != PowerEligibility.Reason.MISSING_DEPENDENCY
                && active.reason() != PowerEligibility.Reason.INERT_CONTENT
                && active.reason() != PowerEligibility.Reason.DISABLED_BY_CONFIG) {
            throw new GameTestAssertException("the Power was refused for " + active.reason()
                    + " rather than for the missing integration; the dormant reason a player is "
                    + "shown would be the wrong one");
        }

        // And it cannot quietly become active again: equipping goes through the same gate, which
        // still refuses. Nothing in the load path bypasses it.
        PowerEligibility.Result equip = PowerEligibility.evaluateEquip(player, power);
        if (equip.eligible()) {
            throw new GameTestAssertException("an unavailable Power could be equipped; saved intent "
                    + "is preserved but activation has to be revalidated");
        }
        helper.succeed();
    }
}
