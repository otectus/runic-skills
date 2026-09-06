package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.ActionOrigin;
import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * Spec §18.2 D01, D03 and D09: the Runic wear stage on a native tool.
 *
 * <p>The three things worth proving here are all negatives. Runic must not touch wear it cannot
 * attribute to an ordinary use (D01, and §5.2's "unknown origin gets native behaviour"); it must
 * not weaken a native protection (D03); and it must not misbehave when another mod hands it an
 * absurd number (D09). The positive — that a 10% perk avoids about 10% — is measured too, but it is
 * the least interesting of the four: a broken implementation usually avoids everything or nothing.
 *
 * <p>No {@code @GameTestHolder}: this class is registered by {@code TConstructGameTests} only when
 * Tinkers' is loaded, so every method names its template namespace itself.
 */
@PrefixGameTestTemplate(false)
public class NativeWearGameTest {

    private static final String EMPTY = "empty";

    /** Enough single-point hits that a 10% rate is distinguishable from 0% or 20%. */
    private static final int HITS = 4_000;

    /**
     * D01, the important half: wear with no identified action is native wear.
     *
     * <p>Tinkers' spends durability for things that are not a use at all — a modifier paying for an
     * ability, a tank converting durability into a resource — and 3.11 passes no cause to say which
     * is which. So the redirect asks {@code RunicActionContext}, and outside any action it must
     * forward the native amount untouched however many wear perks the player has taken.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void wearOutsideAnyActionIsNative(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_wear_unattributed");
        TinkerFixtures.enableLuckyBreak(player);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.luckyBreakPercent;
        int spent;
        try {
            config.luckyBreakPercent = 100;
            spent = damageOverHits(TinkerFixtures.pickaxeOfTier(1), player, HITS, false);
        } finally {
            config.luckyBreakPercent = previous;
        }

        if (spent != HITS) {
            throw new GameTestAssertException("a 100% Lucky Break avoided " + (HITS - spent)
                    + " points of native wear with no action open; §5.2 requires a positively "
                    + "identified ordinary use, and unknown origin must get native behaviour");
        }
        helper.succeed();
    }

    /** D01, the other half: inside a block break, the same perk avoids roughly what it promises. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void wearInsideABlockBreakIsReducedOnce(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_wear_break");
        TinkerFixtures.enableLuckyBreak(player);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.luckyBreakPercent;
        int spent;
        try {
            config.luckyBreakPercent = 10;
            spent = damageOverHits(TinkerFixtures.pickaxeOfTier(1), player, HITS, true);
        } finally {
            config.luckyBreakPercent = previous;
        }

        int avoided = HITS - spent;
        if (avoided < HITS * 7 / 100 || avoided > HITS * 13 / 100) {
            throw new GameTestAssertException("a 10% Lucky Break avoided " + avoided + " of " + HITS
                    + " native durability points; expected between 7% and 13%. Outside that band the "
                    + "stage is either not applied or applied more than once");
        }
        helper.succeed();
    }

    /** D01 again, from the other side: no perk means the native number arrives unchanged. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void withoutAPerkTheNativeAmountIsUnchanged(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_wear_no_perk");
        int spent = damageOverHits(TinkerFixtures.pickaxeOfTier(1), player, 200, true);
        if (spent != 200) {
            throw new GameTestAssertException("200 native wear points became " + spent
                    + " for a player with no wear perk at all");
        }
        helper.succeed();
    }

    /**
     * D03: native protections stay authoritative.
     *
     * <p>An unbreakable tool and a broken one are both refused by {@code damage} before the redirect
     * is reached, and the redirect must not be able to change that — §5.2's "preserve native
     * unbreakability; Runic's cap is not an instruction to make native unbreakable tools break", and
     * its mirror, no infinite use of broken equipment.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void nativeProtectionsRemainAuthoritative(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_wear_protections");
        TinkerFixtures.enableLuckyBreak(player);

        ItemStack stack = TinkerFixtures.pickaxeOfTier(1);
        // "Unbreakable" in the stack's root tag is what ToolStack.isUnbreakable() reads, so this is
        // the tool declaring itself unbreakable rather than the test asserting that it is.
        stack.getOrCreateTag().putBoolean("Unbreakable", true);
        ToolStack tool = ToolStack.from(stack);
        if (!tool.isUnbreakable()) {
            throw new GameTestAssertException("the fixture tool did not read as unbreakable");
        }
        int before = tool.getDamage();
        try (RunicActionContext.Scope scope =
                     RunicActionContext.push(ActionOrigin.BLOCK_BREAK, player.getUUID())) {
            ToolDamageUtil.damage(tool, 5, player, stack);
        }
        if (tool.getDamage() != before) {
            throw new GameTestAssertException("an unbreakable native tool took "
                    + (tool.getDamage() - before) + " damage through the Runic wear stage");
        }

        // A broken tool: damage() refuses, and must keep refusing with the redirect installed.
        ItemStack second = TinkerFixtures.pickaxeOfTier(1);
        ToolStack broken = ToolStack.from(second);
        broken.setDamage(broken.getStats().getInt(ToolStats.DURABILITY));
        if (!broken.isBroken()) {
            throw new GameTestAssertException("the fixture tool did not break at full damage");
        }
        int brokenDamage = broken.getDamage();
        try (RunicActionContext.Scope scope =
                     RunicActionContext.push(ActionOrigin.BLOCK_BREAK, player.getUUID())) {
            if (ToolDamageUtil.damage(broken, 5, player, second)) {
                throw new GameTestAssertException("damage() reported a break on an already broken tool");
            }
        }
        if (broken.getDamage() != brokenDamage) {
            throw new GameTestAssertException("a broken native tool took further damage");
        }
        helper.succeed();
    }

    /**
     * D09: extreme values stay bounded.
     *
     * <p>{@code Integer.MAX_VALUE} in one call is not a realistic amount, which is exactly why it is
     * the one to try: the per-point loop that is correct for a single point would run for hours on
     * it, and any arithmetic that multiplies before clamping would overflow into a negative and
     * repair the tool instead of damaging it.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void anAbsurdAmountIsBoundedAndNeverNegative(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_wear_extreme");
        TinkerFixtures.enableLuckyBreak(player);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.luckyBreakPercent;
        ItemStack stack = TinkerFixtures.pickaxeOfTier(1);
        ToolStack tool = ToolStack.from(stack);
        int max = tool.getStats().getInt(ToolStats.DURABILITY);
        long started = System.nanoTime();
        try {
            config.luckyBreakPercent = 50;
            try (RunicActionContext.Scope scope =
                         RunicActionContext.push(ActionOrigin.BLOCK_BREAK, player.getUUID())) {
                ToolDamageUtil.damage(tool, Integer.MAX_VALUE, player, stack);
            }
        } finally {
            config.luckyBreakPercent = previous;
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        if (tool.getDamage() < 0 || tool.getDamage() > max) {
            throw new GameTestAssertException("Integer.MAX_VALUE of wear left the tool at damage "
                    + tool.getDamage() + " against a maximum of " + max);
        }
        if (elapsedMs > 2_000L) {
            throw new GameTestAssertException("one wear call of Integer.MAX_VALUE took " + elapsedMs
                    + " ms; §5.2 requires a bounded-time sampler, not a loop over the amount");
        }
        helper.succeed();
    }

    /**
     * Spends {@code hits} single points on {@code stack} and reports how many were actually taken.
     * With {@code inAction} the whole run sits inside one block-break scope, which is what the
     * game-mode mixin opens around a real break.
     */
    private static int damageOverHits(ItemStack stack, ServerPlayer player, int hits, boolean inAction) {
        ToolStack tool = ToolStack.from(stack);
        if (!inAction) {
            return spend(tool, stack, player, hits);
        }
        try (RunicActionContext.Scope scope =
                     RunicActionContext.push(ActionOrigin.BLOCK_BREAK, player.getUUID())) {
            return spend(tool, stack, player, hits);
        }
    }

    /**
     * Points actually taken over {@code hits} single-point calls.
     *
     * <p>The tool is reset to undamaged before each call, because a wooden pickaxe would otherwise
     * break a couple of hundred hits in and every subsequent call would be refused by the native
     * broken check — which would make the measured rate a measurement of the tool's durability
     * rather than of the perk.
     */
    private static int spend(ToolStack tool, ItemStack stack, ServerPlayer player, int hits) {
        int spent = 0;
        for (int i = 0; i < hits; i++) {
            tool.setDamage(0);
            ToolDamageUtil.damage(tool, 1, player, stack);
            spent += tool.getDamage();
        }
        return spent;
    }
}
