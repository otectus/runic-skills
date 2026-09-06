package com.otectus.runicskills.gametest.tconstruct.addons;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.ActionOrigin;
import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.gametest.tconstruct.TinkerFixtures;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.addons.TcAddonHooks;
import com.otectus.runicskills.integration.tconstruct.addons.TinkersLevellingAdapter;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import vectorwing.farmersdelight.common.registry.ModEffects;

import java.util.function.Supplier;

/**
 * §18.3 C08 for the add-on perks whose add-ons this release can actually boot.
 *
 * <p>Two of the seven have a live path in this profile: Seasoned Hands (Tinkers' Levelling) and
 * Banquet of Cinders (Tinkers' Delight with Farmer's Delight). Charged Craft has its own class,
 * because its add-on cannot boot a dedicated server at all — see {@code TcChargedCraftGameTest}. The
 * remaining four need Botania, Ars Nouveau, Create or Malum on top of TCIntegrations, none of which
 * this build resolves, so their live behaviour is not asserted here and their dormancy is asserted
 * in {@code TcAddonAbsenceGameTest} instead — an unproven claim is worse than an absent one (§12.1).
 *
 * <p>C08 asks the same four things of each: it works when active, does nothing when inactive, is
 * dormant without its dependency, and its stated boundary holds. The third is the absence test's;
 * the other three are below, driven through the exact static seam each mixin calls, because that
 * method <em>is</em> the perk — the mixin around it only supplies the arguments.
 *
 * <p>No {@code @GameTestHolder}: registered from {@code TConstructGameTests} only when every add-on
 * these cases need is loaded.
 */
@PrefixGameTestTemplate(false)
public class TcAddonPerksGameTest {

    private static final String EMPTY = "empty";

    // -- tc_seasoned_hands ------------------------------------------------------------------------

    /** The award is scaled once, only for a holder, and only inside a real player action. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void seasonedHandsScalesOneAward(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_seasoned_hands");
        ToolStack tool = ToolStack.from(TinkerFixtures.pickaxeOfTier(1));

        int without = inAction(player, () -> TinkersLevellingAdapter.scaleExperience(tool, 100, player));
        if (without != 100) {
            throw new GameTestAssertException("a player without the perk had an award of 100 turned "
                    + "into " + without);
        }

        TinkerFixtures.enablePerk(player, RegistryPerks.TC_SEASONED_HANDS);
        int percent = HandlerCommonConfig.HANDLER.instance().tcSeasonedHandsPercent;
        int expected = 100 + 100 * percent / 100;
        int with = inAction(player, () -> TinkersLevellingAdapter.scaleExperience(tool, 100, player));
        if (with != expected) {
            throw new GameTestAssertException("an award of 100 became " + with + ", expected "
                    + expected + " at " + percent + '%');
        }
        helper.succeed();
    }

    /** A non-positive award, and one with no player action behind it, are left exactly alone. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void seasonedHandsIgnoresCommandAndNegativeAwards(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_seasoned_hands_bounds");
        ToolStack tool = ToolStack.from(TinkerFixtures.pickaxeOfTier(1));
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_SEASONED_HANDS);

        // No open action: a command grant or a script, which §12.3 says gets no multiplier.
        int command = TinkersLevellingAdapter.scaleExperience(tool, 100, player);
        if (command != 100) {
            throw new GameTestAssertException("an award with no player action behind it was scaled to "
                    + command);
        }
        int negative = inAction(player, () -> TinkersLevellingAdapter.scaleExperience(tool, -50, player));
        if (negative != -50) {
            throw new GameTestAssertException("a negative award was changed to " + negative);
        }
        int zero = inAction(player, () -> TinkersLevellingAdapter.scaleExperience(tool, 0, player));
        if (zero != 0) {
            throw new GameTestAssertException("a zero award was changed to " + zero);
        }
        helper.succeed();
    }

    // -- tc_banquet_of_cinders --------------------------------------------------------------------

    /** The melee share is paid only while the add-on's actual food effect is on the player. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void banquetPaysOnlyWhileNourished(GameTestHelper helper) {
        // Connected: applying a mob effect syncs it to the client, and a player with no connection
        // dies inside vanilla rather than in the code under test.
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, "tc_banquet");
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_BANQUET_OF_CINDERS);

        double unfed = TcAddonHooks.meleeDamageBonus(player);
        if (unfed != 0.0) {
            throw new GameTestAssertException("an unfed holder was paid " + unfed);
        }

        player.addEffect(new MobEffectInstance(ModEffects.NOURISHMENT.get(), 200));
        double fed = TcAddonHooks.meleeDamageBonus(player);
        double expected = HandlerCommonConfig.HANDLER.instance().tcBanquetOfCindersPercent / 100.0;
        if (Math.abs(fed - expected) > 1.0E-6) {
            throw new GameTestAssertException("a nourished holder was paid " + fed + ", expected "
                    + expected);
        }

        // And the contribution is the perk's, not the effect's: without the perk it is not paid.
        ServerPlayer other = TinkerFixtures.connectedPlayer(helper, "tc_banquet_nonholder");
        other.addEffect(new MobEffectInstance(ModEffects.NOURISHMENT.get(), 200));
        double nonHolder = TcAddonHooks.meleeDamageBonus(other);
        if (nonHolder != 0.0) {
            throw new GameTestAssertException("a nourished non-holder was paid " + nonHolder);
        }
        helper.succeed();
    }

    /** Runs {@code query} inside an ordinary-use action frame owned by {@code player}. */
    private static int inAction(ServerPlayer player, Supplier<Integer> query) {
        boolean opened = RunicActionContext.enter(ActionOrigin.BLOCK_BREAK, player.getUUID());
        try {
            return query.get();
        } finally {
            if (opened) RunicActionContext.exit();
        }
    }
}
