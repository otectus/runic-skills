package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.KeystoneModifier;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;

/**
 * Spec §10.4: the keystone is a paid, permanent, once-per-tool station service.
 *
 * <p>Every case here is about the same seam and asks it a different way. The station computes one
 * result for everybody with no player attached, so the eligibility question cannot be settled where
 * the result is computed; it is settled at {@code Slot.mayPickup}, which both a click and Mantle's
 * shift-click pass through before anything is consumed. So "an ineligible smith is refused" and
 * "nothing was consumed" are one assertion in two halves — a refusal that still ate the netherite
 * would be worse than no refusal.
 *
 * <p>The permanence half is the other reason this file exists. §10.4 forbids removing a slot the
 * player paid for when the service is later turned off, because the modifiers standing in that slot
 * would be invalidated with it. That is asserted directly: the flag goes off and the tool is asked
 * again.
 *
 * <p>No {@code @GameTestHolder}: registered by {@code TConstructGameTests} only when Tinkers' is
 * loaded, so every method names its template namespace itself.
 */
@PrefixGameTestTemplate(false)
public class TcKeystoneTinkerGameTest {

    private static final String EMPTY = "empty";

    /** Where the station is placed in every test below. */
    private static final BlockPos STATION = new BlockPos(1, 1, 1);

    /** E: an eligible smith pays once and the tool gains exactly one upgrade slot. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void anEligibleSmithFitsOneSlotByClick(GameTestHelper helper) {
        fitAndAssert(helper, "tc_keystone_click", false);
        helper.succeed();
    }

    /** The same take through Mantle's quick-move, which never calls the removal method. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void anEligibleSmithFitsOneSlotByShiftClick(GameTestHelper helper) {
        fitAndAssert(helper, "tc_keystone_shift", true);
        helper.succeed();
    }

    /**
     * A smith without the perk is refused, and the materials are still on the table.
     *
     * <p>Run both ways: the refusal lands at {@code mayPickup}, which vanilla consults before a
     * click and before a shift-click alike, and a guard that only covered one of them would be the
     * exact asymmetry the station tests were written for.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void anIneligibleSmithIsRefusedAndPaysNothing(GameTestHelper helper) {
        refuseAndAssert(helper, setup(helper, "tc_keystone_denied", false), false);
        refuseAndAssert(helper, setup(helper, "tc_keystone_denied_shift", false), true);
        helper.succeed();
    }

    /**
     * A machine gets nothing. A {@link FakePlayer} is a {@code ServerPlayer}, so "is it a player"
     * is not the question; "is there a smith" is.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aFakePlayerFitsNothing(GameTestHelper helper) {
        Setup setup = setup(helper, "tc_keystone_fake", false);
        FakePlayer machine = FakePlayerFactory.getMinecraft(helper.getLevel());
        AbstractContainerMenu menu = setup.station.createMenu(1, machine.getInventory(), machine);
        int slot = TinkerFixtures.resultSlotIndex(menu);

        menu.clicked(slot, 0, ClickType.PICKUP, machine);

        if (!menu.getCarried().isEmpty()) {
            throw new GameTestAssertException("a FakePlayer took a keystone from the station");
        }
        assertMaterialsPresent(setup.station, "a FakePlayer's refused take");
        helper.succeed();
    }

    /**
     * A second keystone cannot be bought, however many smiths work on the tool.
     *
     * <p>The refusal is the recipe's, not the guard's: the station offers no result at all for a
     * tool that already carries one, so there is nothing to take and nothing to explain away.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aSecondKeystoneIsRefused(GameTestHelper helper) {
        Setup setup = setup(helper, "tc_keystone_twice", true);
        setup.menu.clicked(setup.resultSlot, 0, ClickType.PICKUP, setup.player);
        ItemStack fitted = setup.menu.getCarried().copy();
        if (fitted.isEmpty()) {
            throw new GameTestAssertException("the first keystone was never fitted");
        }
        setup.menu.setCarried(ItemStack.EMPTY);

        // Put the finished tool back with a fresh set of materials and ask again.
        setup.station.setItem(TinkerStationBlockEntity.TINKER_SLOT, fitted);
        setup.station.setItem(TinkerStationBlockEntity.INPUT_SLOT, new ItemStack(Items.NETHERITE_INGOT));
        setup.station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, new ItemStack(Items.AMETHYST_SHARD));

        AbstractContainerMenu menu = setup.station.createMenu(2, setup.player.getInventory(), setup.player);
        int slot = TinkerFixtures.resultSlotIndex(menu);
        if (!menu.getSlot(slot).getItem().isEmpty()) {
            throw new GameTestAssertException(
                    "the station offered a second keystone for a tool that already carries one");
        }
        menu.clicked(slot, 0, ClickType.PICKUP, setup.player);
        assertMaterialsPresent(setup.station, "a refused second keystone");
        if (upgradeSlots(setup.station.getItem(TinkerStationBlockEntity.TINKER_SLOT))
                != upgradeSlots(fitted)) {
            throw new GameTestAssertException("a refused second keystone still changed the tool");
        }
        helper.succeed();
    }

    /**
     * §10.4: turning the service off stops new keystones and does not take a paid one back.
     *
     * <p>Asserted on the tool after a rebuild rather than on the stored tag, because a rebuild is
     * where a dynamically-removed slot would actually disappear — and the modifiers occupying that
     * slot with it.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void disablingTheServiceKeepsAPaidSlot(GameTestHelper helper) {
        Setup setup = setup(helper, "tc_keystone_disable", true);
        int before = upgradeSlots(setup.station.getItem(TinkerStationBlockEntity.TINKER_SLOT));
        setup.menu.clicked(setup.resultSlot, 0, ClickType.PICKUP, setup.player);
        ItemStack fitted = setup.menu.getCarried().copy();
        if (upgradeSlots(fitted) != before + 1) {
            throw new GameTestAssertException("the keystone did not grant its slot before the flag moved");
        }

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean previous = config.enableTConstructPerks;
        try {
            config.enableTConstructPerks = false;
            ToolStack tool = ToolStack.copyFrom(fitted);
            tool.rebuildStats();
            if (tool.getFreeSlots(SlotType.UPGRADE) != before + 1) {
                throw new GameTestAssertException("disabling the service erased a slot the player paid "
                        + "for; the modifiers standing in it would go with it");
            }
        } finally {
            config.enableTConstructPerks = previous;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    /** One prepared station: a tool, the two materials, a player and an open menu. */
    private record Setup(ServerPlayer player, TinkerStationBlockEntity station,
                         AbstractContainerMenu menu, int resultSlot) {
    }

    private static Setup setup(GameTestHelper helper, String name, boolean withPerk) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, name);
        if (withPerk) TinkerFixtures.enablePerk(player, RegistryPerks.TC_KEYSTONE_TINKER);

        TinkerStationBlockEntity station = TinkerFixtures.station(helper, STATION, 2);
        station.setItem(TinkerStationBlockEntity.TINKER_SLOT, TinkerFixtures.pickaxeOfTier(1));
        station.setItem(TinkerStationBlockEntity.INPUT_SLOT, new ItemStack(Items.NETHERITE_INGOT));
        station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, new ItemStack(Items.AMETHYST_SHARD));

        AbstractContainerMenu menu = station.createMenu(1, player.getInventory(), player);
        int slot = TinkerFixtures.resultSlotIndex(menu);
        if (menu.getSlot(slot).getItem().isEmpty()) {
            throw new GameTestAssertException("the station offered no keystone for a pickaxe, a "
                    + "netherite ingot and an amethyst shard; the recipe or the allowlist is wrong");
        }
        return new Setup(player, station, menu, slot);
    }

    private static void fitAndAssert(GameTestHelper helper, String name, boolean quickMove) {
        Setup setup = setup(helper, name, true);
        int before = upgradeSlots(setup.station.getItem(TinkerStationBlockEntity.TINKER_SLOT));

        setup.menu.clicked(setup.resultSlot, 0,
                quickMove ? ClickType.QUICK_MOVE : ClickType.PICKUP, setup.player);

        ItemStack fitted = quickMove
                ? firstPickaxe(setup.player) : setup.menu.getCarried();
        String how = quickMove ? "shift-click" : "click";
        if (fitted.isEmpty()) {
            throw new GameTestAssertException("the keystoned tool was never delivered (" + how + ")");
        }
        if (ToolStack.from(fitted).getModifierLevel(KeystoneModifier.ID) != 1) {
            throw new GameTestAssertException(
                    "the delivered tool does not carry exactly one keystone (" + how + ")");
        }
        if (upgradeSlots(fitted) != before + 1) {
            throw new GameTestAssertException("the keystone changed the upgrade slots from " + before
                    + " to " + upgradeSlots(fitted) + "; it grants exactly one (" + how + ")");
        }
        for (int slot = TinkerStationBlockEntity.INPUT_SLOT;
             slot < setup.station.getContainerSize(); slot++) {
            if (!setup.station.getItem(slot).isEmpty()) {
                throw new GameTestAssertException("the station kept a material after fitting a "
                        + "keystone; the cost is paid once (" + how + ")");
            }
        }
    }

    private static void refuseAndAssert(GameTestHelper helper, Setup setup, boolean quickMove) {
        setup.menu.clicked(setup.resultSlot, 0,
                quickMove ? ClickType.QUICK_MOVE : ClickType.PICKUP, setup.player);
        String how = quickMove ? "shift-click" : "click";

        if (!setup.menu.getCarried().isEmpty() || !firstPickaxe(setup.player).isEmpty()) {
            throw new GameTestAssertException(
                    "a smith without Keystone Tinker was handed a keystoned tool (" + how + ")");
        }
        assertMaterialsPresent(setup.station, "a refused take (" + how + ")");
        if (setup.station.getItem(TinkerStationBlockEntity.TINKER_SLOT).isEmpty()) {
            throw new GameTestAssertException("a refused take consumed the tool (" + how + ")");
        }
    }

    /** Both materials still in the station, in full. */
    private static void assertMaterialsPresent(TinkerStationBlockEntity station, String what) {
        for (int slot = TinkerStationBlockEntity.INPUT_SLOT; slot < station.getContainerSize(); slot++) {
            if (station.getItem(slot).isEmpty()) {
                throw new GameTestAssertException(
                        what + " consumed the materials; nothing may be paid for nothing");
            }
        }
    }

    /** Free upgrade slots on {@code stack}, native accounting and all. */
    private static int upgradeSlots(ItemStack stack) {
        return stack.isEmpty() ? 0 : ToolStack.from(stack).getFreeSlots(SlotType.UPGRADE);
    }

    /** The first pickaxe in the player's inventory, for the shift-click paths. */
    private static ItemStack firstPickaxe(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == TinkerFixtures.modifiable("pickaxe").asItem()) return stack;
        }
        return ItemStack.EMPTY;
    }
}
