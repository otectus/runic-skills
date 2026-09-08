package com.otectus.runicskills.gametest.tconstruct.addons;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.ActionOrigin;
import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.gametest.tconstruct.TinkerFixtures;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.addons.TinkersLevellingAdapter;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pyre.tinkerslevellingaddon.ImprovableModifier;
import pyre.tinkerslevellingaddon.util.ToolLevellingUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * §12.3: player progression and tool progression stay separate, and stay each other's business.
 *
 * <p>The list the spec gives is a list of ways the two could bleed into one another, and each case
 * below is one of them: a tool award must not move the player's experience, a carry must not travel
 * between players or tools, an award past the add-on's cap must not be inflated, and switching the
 * perk off must change the tool experience and nothing else.
 *
 * <p><b>Nothing here calls {@code addExperience}.</b> Doing so would drive the add-on's own level-up,
 * history and packet code from a test and prove nothing about the perk; the perk is the scaling
 * function, and it is the scaling function that is measured. The cap case does read the add-on's
 * own {@code canLevelUp} to decide what "past the cap" means, so the test and the code agree with
 * the add-on rather than with each other.
 *
 * <p>No {@code @GameTestHolder}: registered from {@code TConstructGameTests}.
 */
@PrefixGameTestTemplate(false)
public class LevellingCoexistenceGameTest {

    private static final String EMPTY = "empty";

    /** A tool experience award moves no player experience, in either direction. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void toolExperienceIsNotPlayerExperience(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "levelling_separate");
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_SEASONED_HANDS);
        ToolStack tool = ToolStack.from(TinkerFixtures.pickaxeOfTier(1));

        int before = player.totalExperience;
        boolean opened = RunicActionContext.enter(ActionOrigin.BLOCK_BREAK, player.getUUID());
        try {
            TinkersLevellingAdapter.scaleExperience(tool, 100, player);
        } finally {
            if (opened) RunicActionContext.exit();
        }
        if (player.totalExperience != before) {
            throw new GameTestAssertException("scaling a tool award changed the player's experience "
                    + "from " + before + " to " + player.totalExperience);
        }
        helper.succeed();
    }

    /**
     * The fractional carry belongs to one player and one tool item, and is dropped rather than moved.
     *
     * <p>At 10%, an award of one is worth 0.1: each is worth nothing on its own, and enough of them
     * are worth exactly one whole point and no more. A second player starting from scratch must not
     * inherit any of that, which is §12.3's rule that a carry cannot be transferred.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void theCarryDoesNotTravel(GameTestHelper helper) {
        int percent = HandlerCommonConfig.HANDLER.instance().tcSeasonedHandsPercent;
        if (percent <= 0) {
            throw new GameTestAssertException("tcSeasonedHandsPercent is " + percent
                    + "; the carry cannot be measured with the perk turned down to nothing");
        }
        ServerPlayer first = TinkerFixtures.player(helper, "levelling_carry_a");
        ServerPlayer second = TinkerFixtures.player(helper, "levelling_carry_b");
        TinkerFixtures.enablePerk(first, RegistryPerks.TC_SEASONED_HANDS);
        TinkerFixtures.enablePerk(second, RegistryPerks.TC_SEASONED_HANDS);
        ToolStack pickaxe = ToolStack.from(TinkerFixtures.pickaxeOfTier(1));

        // One award past the point where the remainders must have added up to a whole point, so
        // the assertion cannot turn on whether 0.1 ten times is exactly 1.0 in binary.
        int awards = 100 / percent + 1;
        int paid = 0;
        for (int i = 0; i < awards; i++) {
            paid += award(first, pickaxe, 1) - 1;
        }
        if (paid != 1) {
            throw new GameTestAssertException("a carry that should have paid exactly one point over "
                    + awards + " awards of one paid " + paid);
        }

        // The other player has accumulated nothing from the first player's awards.
        int otherFirstAward = award(second, pickaxe, 1);
        if (otherFirstAward != 1) {
            throw new GameTestAssertException("a second player's first award was already carrying "
                    + "someone else's remainder: " + otherFirstAward);
        }
        helper.succeed();
    }

    /** No multiplier is granted once the add-on says the tool cannot level any further. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void nothingIsGrantedPastTheAddonsCap(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "levelling_cap");
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_SEASONED_HANDS);
        ToolStack tool = ToolStack.from(TinkerFixtures.pickaxeOfTier(1));

        // Find a level the add-on itself refuses to go past. With an unlimited cap configured there
        // is none, and the rule is vacuously true — which the test says rather than pretending to
        // have proved something.
        int capped = -1;
        for (int level = 0; level < 4096; level++) {
            if (!ToolLevellingUtil.canLevelUp(level)) {
                capped = level;
                break;
            }
        }
        if (capped < 0) {
            helper.succeed();
            return;
        }
        tool.getPersistentData().putInt(ImprovableModifier.LEVEL_KEY, capped);
        int atCap = award(player, tool, 100);
        if (atCap != 100) {
            throw new GameTestAssertException("an award to a tool at the addon's cap (level " + capped
                    + ") was scaled to " + atCap);
        }
        helper.succeed();
    }

    /** With the perk off, the same input sequence produces exactly the native award. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void theOnlyDifferenceIsTheIntendedOne(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "levelling_off_on");
        ToolStack tool = ToolStack.from(TinkerFixtures.pickaxeOfTier(1));

        int off = award(player, tool, 40);
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_SEASONED_HANDS);
        int on = award(player, tool, 40);
        int percent = HandlerCommonConfig.HANDLER.instance().tcSeasonedHandsPercent;
        if (off != 40) {
            throw new GameTestAssertException("with the perk off, 40 became " + off);
        }
        if (on != 40 + 40 * percent / 100) {
            throw new GameTestAssertException("with the perk on, 40 became " + on);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void largeAwardNeverOverflowsOrLeavesUnboundedCarry(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "levelling_large");
        ToolStack tool = ToolStack.from(TinkerFixtures.pickaxeOfTier(1));
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_SEASONED_HANDS);
        if (award(player, tool, Integer.MAX_VALUE) != Integer.MAX_VALUE) {
            throw new GameTestAssertException("a large positive tool award overflowed");
        }
        int percent = HandlerCommonConfig.HANDLER.instance().tcSeasonedHandsPercent;
        if (award(player, tool, 1) > 2 + percent / 100) {
            throw new GameTestAssertException("saturation left an unbounded carry");
        }
        helper.succeed();
    }

    /** One award, inside an ordinary-use action frame owned by {@code player}. */
    private static int award(ServerPlayer player, ToolStack tool, int amount) {
        boolean opened = RunicActionContext.enter(ActionOrigin.BLOCK_BREAK, player.getUUID());
        try {
            return TinkersLevellingAdapter.scaleExperience(tool, amount, player);
        } finally {
            if (opened) RunicActionContext.exit();
        }
    }
}
