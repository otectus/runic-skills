package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.durability.WearAvoidance;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.integration.tconstruct.TConstructPowers;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * Spec 18.3 D08: Last Temper, the one bounded exception to probabilistic wear.
 *
 * <p>Everything else in the Runic wear stage is a chance to spare a point. Section 13.2 allows one
 * deterministic clamp, and this is it: a loss that would break a usable tool is reduced to leave
 * exactly one durability. The properties that keep that from being an infinite-durability switch
 * are what this class checks -- it never repairs, it never touches a tool that is already broken or
 * already on its last point, it never fires for a loss the tool survives anyway, and the cooldown
 * is spent only on the loss it actually clamped.
 *
 * <p>Measured through {@code WearAvoidance.reduce}, which is the method the native redirect calls,
 * so what is under test is the number Tinkers' would have taken. The player holds no wear perk, so
 * the probabilistic stage contributes nothing and the clamp is observed on its own.
 *
 * <p>No {@code @GameTestHolder}: registered from {@code TConstructGameTests} only when Tinkers' is
 * loaded, so every method names its own template namespace.
 */
@PrefixGameTestTemplate(false)
public class LastTemperGameTest {

    private static final String EMPTY = "empty";

    /** More than any tool here has left, so the loss is unambiguously lethal. */
    private static final int LETHAL_LOSS = 40;

    /**
     * D08 proper: the tool comes out of a lethal hit on exactly one durability, the cooldown is
     * spent, and the very next lethal hit is not saved.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aLethalLossLeavesExactlyOneDurabilityAndSpendsTheCooldown(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_last_temper");
        Power power = power();
        TinkerFixtures.equipPower(player, RegistryPowers.TC_LAST_TEMPER);

        ItemStack tool = toolWithRemaining(player, 5);
        int spent = WearAvoidance.reduce(player, tool, LETHAL_LOSS, RandomSource.create());
        if (spent != 4) {
            throw new GameTestAssertException("a lethal " + LETHAL_LOSS + "-point loss on a tool with"
                    + " 5 durability left spent " + spent + " points; Last Temper must leave exactly"
                    + " one, so it should have spent 4");
        }
        if (PowerCooldownDebt.remaining(player, power, tick(player)) <= 0L) {
            throw new GameTestAssertException("Last Temper fired without starting its cooldown");
        }

        // Still on cooldown: the second tool is not saved, and nothing is repaired to hide it.
        ItemStack second = toolWithRemaining(player, 5);
        int again = WearAvoidance.reduce(player, second, LETHAL_LOSS, RandomSource.create());
        if (again != LETHAL_LOSS) {
            throw new GameTestAssertException("a second lethal loss inside the cooldown was reduced"
                    + " to " + again + "; the cooldown is per player, shared across tools");
        }
        helper.succeed();
    }

    /** A player who has not equipped it loses the whole amount, and owes nothing. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void itDoesNothingForAPlayerWhoHasNotEquippedIt(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_last_temper_off");
        ItemStack tool = toolWithRemaining(player, 5);

        int spent = WearAvoidance.reduce(player, tool, LETHAL_LOSS, RandomSource.create());
        if (spent != LETHAL_LOSS) {
            throw new GameTestAssertException("an unequipped player was still saved: " + spent
                    + " of " + LETHAL_LOSS + " points spent");
        }
        if (PowerCooldownDebt.remaining(player, power(), tick(player)) != 0L) {
            throw new GameTestAssertException(
                    "an unequipped Power started a cooldown for a player who does not hold it");
        }
        helper.succeed();
    }

    /**
     * A loss the tool survives is not clamped, and does not spend the cooldown -- otherwise every
     * ordinary swing would burn the Crown and it would never be there when it mattered.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aSurvivableLossIsUntouchedAndFree(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_last_temper_survivable");
        TinkerFixtures.equipPower(player, RegistryPowers.TC_LAST_TEMPER);

        ItemStack tool = toolWithRemaining(player, 20);
        int spent = WearAvoidance.reduce(player, tool, 3, RandomSource.create());
        if (spent != 3) {
            throw new GameTestAssertException("a survivable 3-point loss was clamped to " + spent);
        }
        if (PowerCooldownDebt.remaining(player, power(), tick(player)) != 0L) {
            throw new GameTestAssertException("a survivable loss spent the Last Temper cooldown;"
                    + " it is spent only when the clamp actually bites");
        }
        helper.succeed();
    }

    /**
     * A tool already on its last point is left alone. Section 11.4 is explicit: this Power does not
     * repair, so there is nothing for it to do here, and it must not pay for the privilege.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aToolAlreadyOnItsLastPointIsNotSaved(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_last_temper_last_point");
        TinkerFixtures.equipPower(player, RegistryPowers.TC_LAST_TEMPER);

        ItemStack tool = toolWithRemaining(player, 1);
        int spent = WearAvoidance.reduce(player, tool, 1, RandomSource.create());
        if (spent != 1) {
            throw new GameTestAssertException("a tool on its last durability was saved anyway ("
                    + spent + " of 1 point spent); that would make it unbreakable");
        }
        if (PowerCooldownDebt.remaining(player, power(), tick(player)) != 0L) {
            throw new GameTestAssertException(
                    "a tool on its last point spent the cooldown without being saved");
        }
        helper.succeed();
    }

    // -- helpers -------------------------------------------------------------------------------

    private static Power power() {
        Power power = RegistryPowers.getPower(TConstructPowers.LAST_TEMPER);
        if (power == null) {
            throw new GameTestAssertException("tc_last_temper is not registered");
        }
        return power;
    }

    /**
     * A real native pickaxe damaged to leave exactly {@code remaining} durability, held in the main
     * hand so the tool under test is the tool the player is using.
     */
    private static ItemStack toolWithRemaining(ServerPlayer player, int remaining) {
        ItemStack tool = TinkerFixtures.pickaxeOfTier(1);
        ToolStack stack = ToolStack.from(tool);
        int max = stack.getStats().getInt(ToolStats.DURABILITY);
        if (max <= remaining) {
            throw new GameTestAssertException("the fixture pickaxe has only " + max
                    + " durability, too few to leave " + remaining + " and still be damaged");
        }
        stack.setDamage(max - remaining);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
        return tool;
    }

    private static long tick(ServerPlayer player) {
        return player.getServer() == null ? 0L : player.getServer().getTickCount();
    }
}
