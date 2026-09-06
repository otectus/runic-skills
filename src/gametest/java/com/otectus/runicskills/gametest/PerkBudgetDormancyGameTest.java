package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A saved perk id the registry cannot resolve does not charge the player's budget.
 *
 * <p>Content that is gated on an optional mod is <em>dormant</em> when that mod is absent: the id
 * stays in the player's saved data so removing and reinstalling the mod keeps their selection, but
 * nothing in the game can show it, toggle it, or run its effect. Charging budget for one is
 * therefore charging for something the player cannot use and cannot switch off — and because going
 * over the effective cap freezes perk activation until a respec, a player could be locked out of
 * their remaining perks by a mod they uninstalled.
 *
 * <p>This is the invariant the sixteen {@code tc_} perks rely on, and it is deliberately tested in
 * the base suite rather than the Tinkers' one: the case that matters is the run where Tinkers' is
 * <b>not</b> installed, which is exactly this run.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class PerkBudgetDormancyGameTest {

    private static final String EMPTY = "empty";

    /** An unresolvable id at rank 1 is not counted, and a real one still is. */
    @GameTest(template = EMPTY)
    public static void anUnregisteredPerkIdDoesNotChargeTheBudget(GameTestHelper helper) {
        // A fresh capability: every registered perk present at rank 0, which is what a new
        // character has and the only starting point that makes the counts below meaningful.
        SkillCapability capability = new SkillCapability();

        int baseline = RegistryPerks.countEnabledPerks(capability);
        if (baseline != 0) {
            throw new GameTestAssertException("a cleared capability counted " + baseline + " perks");
        }

        // An id from a release or an optional mod this server does not have. It resolves to
        // nothing in the perk registry, which is the whole definition of dormant content.
        capability.perkRank.put("tc_a_perk_this_server_does_not_have", 1);
        int withDormant = RegistryPerks.countEnabledPerks(capability);
        if (withDormant != 0) {
            throw new GameTestAssertException("a dormant, unresolvable perk id was charged against "
                    + "the budget (" + withDormant + "); it cannot be shown, used or switched off, "
                    + "so it must not cost the player a slot");
        }

        // And a real one still counts, so this is not simply a count that always returns zero.
        capability.setPerkRank(RegistryPerks.ONE_HANDED.get(), 1);
        int withReal = RegistryPerks.countEnabledPerks(capability);
        if (withReal != 1) {
            throw new GameTestAssertException("a registered perk at rank 1 counted " + withReal);
        }
        helper.succeed();
    }
}
