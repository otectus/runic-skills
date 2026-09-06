package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.workshop.WorkshopFocusService;
import com.otectus.runicskills.common.workshop.WorkshopFocusService.Outcome;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.ICastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.MelterBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.MeltingModuleInventory;

/**
 * Spec W02 and E09: a focused workshop runs faster and conserves exactly as much as it did before.
 *
 * <p>The two halves of §6.5 are tested separately because they fail differently. Melting is about
 * the <em>increment</em>: a one-unit rate with a 15% bonus is worth nothing at all unless the
 * fraction is carried, and a bonus applied in the wrong branch would finish a melt twice. Casting is
 * about the <em>completion</em>: the timer may move faster, but the recipe must still be assembled
 * once, the tank emptied once, and the cast handled exactly as native code handles it — which in
 * this release means Cast Keeper does not exist yet and nothing is returned to anybody.
 *
 * <p>Both are driven through the real block entities rather than through the bridge, because the
 * property under test is a property of the injected native method rather than of the calculation
 * that feeds it.
 */
@PrefixGameTestTemplate(false)
public class CastingGameTest {

    private static final String EMPTY = "empty";

    /** How many one-unit heating ticks each measurement runs for. */
    private static final int HEAT_TICKS = 40;

    private static final BlockPos MELTER = new BlockPos(1, 1, 1);
    private static final BlockPos TABLE = new BlockPos(2, 1, 1);

    /**
     * W02: a focused melt gains the configured share, carried fractionally, and finishes no sooner
     * than one native completion.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void focusedMeltingGainsTheConfiguredShare(GameTestHelper helper) {
        int baseline = meltFor(helper, "w02-baseline", false);
        int boosted = meltFor(helper, "w02-focused", true);
        if (baseline != HEAT_TICKS) {
            throw new GameTestAssertException("an unfocused melt advanced by " + baseline
                    + " over " + HEAT_TICKS + " one-unit ticks; native progress was changed");
        }
        if (boosted <= baseline) {
            throw new GameTestAssertException("a focused melt advanced by " + boosted
                    + ", no more than the unfocused " + baseline
                    + "; the fractional carry is being truncated away");
        }
        // Overclock is 15%, and the cap is 25%: the gain is real but bounded, and never doubles.
        int ceiling = baseline + (int) Math.ceil(baseline * 0.25) + 1;
        if (boosted > ceiling) {
            throw new GameTestAssertException("a focused melt advanced by " + boosted
                    + ", past the capped ceiling of " + ceiling);
        }
        helper.succeed();
    }

    /** W02: the bonus stops the moment the focus does. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aReleasedFocusStopsAcceleratingTheMelt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MELTER);
        MeltingModuleInventory inventory = melter(helper);
        ServerPlayer player = focusHolder(helper, "w02-release", pos);

        inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        requireRecipe(inventory);
        WorkshopFocusService.release(player, WorkshopFocusService.revisionOf(player.getUUID()));

        int before = inventory.getCurrentTime(0);
        for (int tick = 0; tick < HEAT_TICKS; tick++) {
            inventory.getModule(0).heatItem(10_000, 1);
        }
        int advanced = inventory.getCurrentTime(0) - before;
        if (advanced != HEAT_TICKS) {
            throw new GameTestAssertException("a released focus still advanced the melt by "
                    + advanced + " over " + HEAT_TICKS + " one-unit ticks");
        }
        if (WorkshopFocusService.focusAt(level, pos) != null) {
            throw new GameTestAssertException("the released focus is still indexed at the melter");
        }
        helper.succeed();
    }

    /**
     * E09: a cast completes once, produces the native output and consumes the native fluid, whether
     * or not the workshop is focused.
     *
     * <p>Two runs of the same cast, one focused: the focused one finishes in fewer ticks and in
     * every other respect is identical. That is the whole of what this stage is allowed to change,
     * and the count assertions are what would catch a second assembly.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aFocusedCastStillProducesExactlyOneNativeOutput(GameTestHelper helper) {
        Cast unfocused = castOnce(helper, "e09-baseline", false);
        Cast focused = castOnce(helper, "e09-focused", true);

        if (unfocused.output.getCount() != 1 || focused.output.getCount() != 1) {
            throw new GameTestAssertException("a cast produced " + unfocused.output.getCount()
                    + " unfocused and " + focused.output.getCount()
                    + " focused; the output count is not native");
        }
        if (!ItemStack.isSameItem(unfocused.output, focused.output)) {
            throw new GameTestAssertException("a focused cast produced " + focused.output
                    + " where an unfocused one produced " + unfocused.output);
        }
        if (unfocused.fluidLeft != 0 || focused.fluidLeft != 0) {
            throw new GameTestAssertException("a cast left " + unfocused.fluidLeft
                    + " mB unfocused and " + focused.fluidLeft
                    + " mB focused; the fluid is not being consumed natively");
        }
        // The cast is handled exactly as the recipe says, focused or not. Nothing in this stage
        // returns one: Cast Keeper is a later stage, and until it exists a focused cast must leave
        // the cast in precisely the state an unfocused one does.
        boolean castKept = !unfocused.castLeft.isEmpty();
        if (castKept == unfocused.castConsumed) {
            throw new GameTestAssertException("a cast the recipe calls "
                    + (unfocused.castConsumed ? "consumed" : "reusable") + " was left as "
                    + unfocused.castLeft + "; native cast handling changed");
        }
        if (!ItemStack.matches(unfocused.castLeft, focused.castLeft)) {
            throw new GameTestAssertException("a focused cast left " + focused.castLeft
                    + " where an unfocused one left " + unfocused.castLeft
                    + "; nothing in this stage may return or consume a cast differently");
        }
        if (focused.ticks >= unfocused.ticks) {
            throw new GameTestAssertException("a focused cast took " + focused.ticks
                    + " ticks against an unfocused " + unfocused.ticks + " on a "
                    + unfocused.coolingTime + "-tick pour; the bonus did nothing");
        }
        helper.succeed();
    }

    /** What one completed cast produced, and how long it took. */
    private record Cast(ItemStack output, ItemStack castLeft, int fluidLeft, int ticks,
                        int coolingTime, boolean castConsumed) {
    }

    /**
     * A casting-table recipe the loaded pack actually has, with the cast and fluid it needs.
     *
     * <p>Chosen by asking the recipe manager rather than by naming an ingot cast and molten iron:
     * a fixture that names its ingredients is a fixture that fails when the pack changes, and the
     * property under test has nothing to do with which recipe runs. The largest pour is preferred
     * because cooling time scales with it, and a bonus of a few percent is only measurable against
     * a cast that takes long enough to have percentages.
     */
    private record Pour(ItemStack cast, FluidStack fluid, boolean consumed) {
    }

    private static Pour pour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos scratch = helper.absolutePos(TABLE);
        List<IDisplayableCastingRecipe> candidates = new ArrayList<>();
        for (ICastingRecipe recipe : level.getRecipeManager()
                .getAllRecipesFor(TinkerRecipeTypes.CASTING_TABLE.get())) {
            if (!(recipe instanceof IDisplayableCastingRecipe displayable)) continue;
            if (!displayable.hasCast() || displayable.getCastItems().isEmpty()
                    || displayable.getFluids().isEmpty() || displayable.getOutput().isEmpty()) {
                continue;
            }
            if (displayable.getFluids().get(0).isEmpty()) continue;
            candidates.add(displayable);
        }
        // Longest pour first: cooling time scales with the amount, and a few percent is only
        // measurable against a cast that takes long enough to have percentages.
        candidates.sort(Comparator.comparingInt(
                (IDisplayableCastingRecipe recipe) -> recipe.getFluids().get(0).getAmount())
                .reversed());

        StringBuilder tried = new StringBuilder();
        for (IDisplayableCastingRecipe candidate : candidates) {
            // A fresh block entity per candidate: a table that already started a cast refuses the
            // next one outright, so reusing one would reject every recipe after the first.
            level.setBlockAndUpdate(scratch, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(scratch, TinkerSmeltery.searedTable.get().defaultBlockState());
            if (!(level.getBlockEntity(scratch) instanceof CastingBlockEntity table)) continue;
            ItemStack cast = candidate.getCastItems().get(0).copy();
            FluidStack fluid = candidate.getFluids().get(0).copy();
            table.setItem(CastingBlockEntity.INPUT, cast.copy());
            // A recipe the table will not actually start - a cast it does not accept in that slot,
            // or a fluid it filters out - is simply not the recipe this test wants.
            int filled = table.getTank().fill(fluid.copy(), FluidAction.EXECUTE);
            if (filled > 0 && table.getCoolingTime() >= 40) {
                boolean consumed = ((ICastingRecipe) candidate).isConsumed();
                level.setBlockAndUpdate(scratch, Blocks.AIR.defaultBlockState());
                return new Pour(cast, fluid, consumed);
            }
            if (tried.length() < 400) {
                tried.append("\n  ").append(cast).append(" + ").append(fluid.getAmount())
                        .append("mB filled=").append(filled)
                        .append(" cooling=").append(table.getCoolingTime());
            }
        }
        level.setBlockAndUpdate(scratch, Blocks.AIR.defaultBlockState());
        throw new GameTestAssertException("none of the " + candidates.size() + " casting-table "
                + "recipes that start from a cast poured and cooled for at least forty ticks:"
                + tried);
    }

    /** Runs one whole cast at the table, optionally with the workshop focused. */
    private static Cast castOnce(GameTestHelper helper, String name, boolean focus) {
        ServerLevel level = helper.getLevel();
        BlockPos melterPos = helper.absolutePos(MELTER);
        BlockPos tablePos = helper.absolutePos(TABLE);
        melter(helper);
        // Chosen before the table is placed, because choosing one puts a scratch table in this very
        // position and takes it out again - a block entity fetched first would be a removed one.
        Pour pour = pour(helper);
        level.setBlockAndUpdate(tablePos, TinkerSmeltery.searedTable.get().defaultBlockState());
        if (!(level.getBlockEntity(tablePos) instanceof CastingBlockEntity table)) {
            throw new GameTestAssertException("the casting table did not place a block entity");
        }

        WorkshopFocusService.clearAll();
        if (focus) {
            ServerPlayer player = focusHolder(helper, name, melterPos);
            Outcome associated = WorkshopFocusService.associate(player, tablePos,
                    WorkshopFocusService.revisionOf(player.getUUID()));
            if (associated != Outcome.ASSOCIATED) {
                throw new GameTestAssertException(
                        "the casting table could not be associated with the focus: " + associated);
            }
        }

        table.setItem(CastingBlockEntity.INPUT, pour.cast().copy());
        int filled = table.getTank().fill(pour.fluid().copy(), FluidAction.EXECUTE);
        if (filled <= 0) {
            throw new GameTestAssertException("the casting table refused " + pour.fluid()
                    + " for " + pour.cast() + ", which is its own recipe's pour; the fixture, not "
                    + "the code under test, is wrong");
        }

        BlockState state = level.getBlockState(tablePos);
        int cooling = table.getCoolingTime();
        int ticks = 0;
        while (table.getItem(CastingBlockEntity.OUTPUT).isEmpty() && ticks < 400) {
            CastingBlockEntity.SERVER_TICKER.tick(level, tablePos, state, table);
            ticks++;
        }
        ItemStack output = table.getItem(CastingBlockEntity.OUTPUT).copy();
        if (output.isEmpty()) {
            throw new GameTestAssertException("the cast never completed in 400 ticks");
        }
        WorkshopFocusService.clearAll();
        return new Cast(output, table.getItem(CastingBlockEntity.INPUT).copy(),
                table.getTank().getFluidInTank(0).getAmount(), ticks, cooling, pour.consumed());
    }

    /** Advances one melt by {@link #heatCalls} one-unit ticks and reports how far it moved. */
    private static int meltFor(GameTestHelper helper, String name, boolean focus) {
        BlockPos pos = helper.absolutePos(MELTER);
        MeltingModuleInventory inventory = melter(helper);
        WorkshopFocusService.clearAll();
        if (focus) focusHolder(helper, name, pos);

        inventory.setStackInSlot(0, ItemStack.EMPTY);
        inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        requireRecipe(inventory);

        int before = inventory.getCurrentTime(0);
        for (int tick = 0; tick < HEAT_TICKS; tick++) {
            inventory.getModule(0).heatItem(10_000, 1);
        }
        int advanced = inventory.getCurrentTime(0) - before;
        if (inventory.getStackInSlot(0).isEmpty()) {
            throw new GameTestAssertException("the melt completed during the measured interval; "
                    + "the fixture must stop short of a completion for this to mean anything");
        }
        WorkshopFocusService.clearAll();
        return advanced;
    }

    /** How many one-unit heating ticks each measurement runs for. */
    private static int heatCalls(GameTestHelper helper) {
        return 40;
    }

    /** Places the melter and hands back its melting inventory. */
    private static MeltingModuleInventory melter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MELTER);
        level.setBlockAndUpdate(pos, TinkerSmeltery.searedMelter.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof MelterBlockEntity melter)) {
            throw new GameTestAssertException("the melter did not place a block entity");
        }
        return melter.getItemHandler();
    }

    /**
     * Puts a test player somewhere.
     *
     * <p>{@code moveTo} is not usable here: {@code ServerPlayer} overrides it to tell the client
     * where it now is, and a test player has no connection to tell.
     */
    private static void setPosition(ServerPlayer player, Vec3 where) {
        player.setPos(where.x, where.y, where.z);
    }

    /** A player standing at {@code controller} with Overclock, holding a focus on it. */
    private static ServerPlayer focusHolder(GameTestHelper helper, String name, BlockPos controller) {
        ServerPlayer player = TinkerFixtures.player(helper, name);
        setPosition(player, Vec3.atCenterOf(controller));
        TinkerFixtures.enablePerk(player, RegistryPerks.OVERCLOCK);
        Outcome outcome = WorkshopFocusService.focus(player, controller,
                WorkshopFocusService.revisionOf(player.getUUID()));
        if (outcome != Outcome.GRANTED) {
            throw new GameTestAssertException("the test player could not focus the workshop: "
                    + outcome);
        }
        return player;
    }

    /** Fails clearly when the loaded pack has no melting recipe for the fixture's input. */
    private static void requireRecipe(MeltingModuleInventory inventory) {
        if (inventory.getRequiredTime(0) < 80) {
            throw new GameTestAssertException("an iron ingot melts in "
                    + inventory.getRequiredTime(0)
                    + " ticks on this pack, which is too short to measure against; the fixture, not "
                    + "the code under test, needs a slower input");
        }
    }
}
