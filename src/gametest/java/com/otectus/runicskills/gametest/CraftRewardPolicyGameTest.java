package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.crafting.CraftOperationContext;
import com.otectus.runicskills.common.crafting.CraftOperationKind;
import com.otectus.runicskills.common.crafting.CraftRewardPolicy;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * A bonus crafted copy is only ever paid for a craft that made something (RS207-01, RS207-10).
 *
 * <p>The perks this governs used to copy whatever {@code ItemCraftedEvent} handed them. That event
 * fires for every recipe type in the game, so the copy was also paid for repairing two damaged
 * tools into one, for compressing ingots into a block and decompressing them back, and for any
 * modded menu that chose to fire it — each a way to finish a cycle with more material than it
 * started with. The tests below are those specific loops, asserted to pay nothing, plus one
 * ordinary craft asserted to still pay, because a policy that refuses everything would also pass
 * the first six.
 *
 * <p>Contexts are built directly rather than driven through a menu. What is under test is the
 * classification and the refusal, and building the context by hand is the only way to state the
 * awkward cases — a result carrying NBT, a recipe that returns a bucket — without needing a recipe
 * in the pack for each one.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class CraftRewardPolicyGameTest {

    private static final String EMPTY = "empty";

    /** Enough cycles that a policy which only refused the first would be visible. */
    private static final int CYCLES = 100;

    private static final UUID CRAFTER = UUID.nameUUIDFromBytes("runicskills-gametest:crafter".getBytes());

    // -- the loops that used to pay ------------------------------------------------------------

    /**
     * E01: repairing two damaged pickaxes into one is a craft, and must earn nothing.
     *
     * <p>Two refusals cover it independently — the operation classifies as a conversion, and the
     * result is damageable — so the test forces the kind to {@code MANUFACTURE} as well, to prove
     * the equipment rule alone is enough. A single-rule fix would pass only half of this.
     */
    @GameTest(template = EMPTY)
    public static void repairByCraftingEarnsNoCopy(GameTestHelper helper) {
        ItemStack damaged = new ItemStack(Items.IRON_PICKAXE);
        damaged.setDamageValue(100);

        assertNoReward("repair-by-crafting", context(CraftOperationKind.CONVERSION,
                List.of(damaged.copy(), damaged.copy()), List.of(), new ItemStack(Items.IRON_PICKAXE)));
        assertNoReward("repair-by-crafting classified as manufacture",
                context(CraftOperationKind.MANUFACTURE,
                        List.of(damaged.copy(), damaged.copy()), List.of(), new ItemStack(Items.IRON_PICKAXE)));
        helper.succeed();
    }

    /**
     * E06: an ingot/block/nugget cycle, a hundred times round, produces no net gain.
     *
     * <p>Compression is a manufacture by every structural test — new item, real ingredients — and
     * is refused on the one property that distinguishes it: everything consumed was a single item
     * type. Both directions are checked, because a policy that only refused the compressing half
     * would leave the decompressing half paying for the round trip.
     */
    @GameTest(template = EMPTY)
    public static void compressionCycleEarnsNoCopy(GameTestHelper helper) {
        for (int cycle = 0; cycle < CYCLES; cycle++) {
            assertNoReward("nuggets to ingot", context(CraftOperationKind.MANUFACTURE,
                    nCopies(Items.IRON_NUGGET, 9), List.of(), new ItemStack(Items.IRON_INGOT)));
            assertNoReward("ingots to block", context(CraftOperationKind.MANUFACTURE,
                    nCopies(Items.IRON_INGOT, 9), List.of(), new ItemStack(Items.IRON_BLOCK)));
            assertNoReward("block to ingots", context(CraftOperationKind.CONVERSION,
                    List.of(new ItemStack(Items.IRON_BLOCK)), List.of(), new ItemStack(Items.IRON_INGOT, 9)));
        }
        helper.succeed();
    }

    /** A craft that returns a bucket did not cost what it looks like it cost, so it pays nothing. */
    @GameTest(template = EMPTY)
    public static void aCraftThatReturnsAContainerEarnsNoCopy(GameTestHelper helper) {
        assertNoReward("milk-bucket recipe", context(CraftOperationKind.MANUFACTURE,
                List.of(new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.WHEAT),
                        new ItemStack(Items.SUGAR)),
                List.of(new ItemStack(Items.BUCKET)), new ItemStack(Items.CAKE)));
        helper.succeed();
    }

    /** A result carrying NBT carries whatever the craft put there, and copying it copies that too. */
    @GameTest(template = EMPTY)
    public static void aResultWithNbtEarnsNoCopy(GameTestHelper helper) {
        ItemStack stamped = new ItemStack(Items.TORCH);
        stamped.setTag(new CompoundTag());
        stamped.getOrCreateTag().putInt("runicskills.test", 1);
        assertNoReward("result with NBT", context(CraftOperationKind.MANUFACTURE,
                List.of(new ItemStack(Items.STICK), new ItemStack(Items.COAL)), List.of(), stamped));
        helper.succeed();
    }

    /** Anything this mod could not classify is refused, because it cannot vouch for it. */
    @GameTest(template = EMPTY)
    public static void anUnclassifiedCraftEarnsNoCopy(GameTestHelper helper) {
        assertNoReward("unknown operation", context(CraftOperationKind.UNKNOWN,
                List.of(new ItemStack(Items.STICK), new ItemStack(Items.COAL)), List.of(),
                new ItemStack(Items.TORCH, 4)));
        helper.succeed();
    }

    // -- the craft that must still pay ---------------------------------------------------------

    /** An ordinary two-ingredient craft of a stackable item earns exactly the configured cap. */
    @GameTest(template = EMPTY)
    public static void anOrdinaryCraftEarnsTheConfiguredCap(GameTestHelper helper) {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.craftRewardMaxExtraOutputs;
        try {
            config.craftRewardMaxExtraOutputs = 2;
            int allowed = CraftRewardPolicy.allowedExtraOutputs(torchCraft(), config);
            if (allowed != 2) {
                throw new GameTestAssertException("an ordinary craft was allowed " + allowed
                        + " bonus copies; the configured cap is 2");
            }

            // And zero means zero: the setting has to be able to turn the whole mechanic off.
            config.craftRewardMaxExtraOutputs = 0;
            allowed = CraftRewardPolicy.allowedExtraOutputs(torchCraft(), config);
            if (allowed != 0) {
                throw new GameTestAssertException("craftRewardMaxExtraOutputs = 0 still allowed "
                        + allowed + " bonus copies");
            }
        } finally {
            config.craftRewardMaxExtraOutputs = previous;
        }
        helper.succeed();
    }

    /** An automation block crafting on a player's behalf has no progression to reward. */
    @GameTest(template = EMPTY)
    public static void aFakePlayerEarnsNoCopy(GameTestHelper helper) {
        CraftOperationContext automated = new CraftOperationContext(CRAFTER, true,
                CraftOperationKind.MANUFACTURE, new ResourceLocation("minecraft", "torch"),
                List.of(new ItemStack(Items.STICK), new ItemStack(Items.COAL)), List.of(),
                new ItemStack(Items.TORCH, 4));
        assertNoReward("fake player", automated);
        helper.succeed();
    }

    // -- helpers -------------------------------------------------------------------------------

    private static CraftOperationContext torchCraft() {
        return context(CraftOperationKind.MANUFACTURE,
                List.of(new ItemStack(Items.STICK), new ItemStack(Items.COAL)), List.of(),
                new ItemStack(Items.TORCH, 4));
    }

    private static CraftOperationContext context(CraftOperationKind kind, List<ItemStack> inputs,
                                                 List<ItemStack> remainders, ItemStack result) {
        return new CraftOperationContext(CRAFTER, false, kind,
                new ResourceLocation(RunicSkills.MOD_ID, "gametest"), inputs, remainders, result);
    }

    private static List<ItemStack> nCopies(net.minecraft.world.item.Item item, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new ItemStack(item))
                .toList();
    }

    /** Asserted against the configured cap raised to its maximum, so a refusal is the only reason. */
    private static void assertNoReward(String what, CraftOperationContext context) {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.craftRewardMaxExtraOutputs;
        try {
            config.craftRewardMaxExtraOutputs = 4;
            int allowed = CraftRewardPolicy.allowedExtraOutputs(context, config);
            if (allowed != 0) {
                throw new GameTestAssertException(what + " was allowed " + allowed
                        + " bonus copies; it creates no material and must be allowed none");
            }
        } finally {
            config.craftRewardMaxExtraOutputs = previous;
        }
    }
}
