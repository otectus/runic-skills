package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.crafting.CraftOperationKind;
import com.otectus.runicskills.common.equipment.RequirementDecision;
import com.otectus.runicskills.common.util.ItemBonusTags;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.lock.LockAction;
import com.otectus.runicskills.integration.lock.LockProviderRegistry;
import com.otectus.runicskills.integration.lock.StackLockProvider;
import com.otectus.runicskills.integration.tconstruct.StationQuote;
import com.otectus.runicskills.integration.tconstruct.TConstructStationBridge;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.ToolBuildingRecipe;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spec §18.1 E02–E05, E08 and E12: one native station take, delivered once and paid once.
 *
 * <p>The station is the hardest transaction in the integration because there are two delivery paths
 * that share nothing. A click removes a defensive copy from the result container; a shift-click
 * goes through Mantle, which copies the slot's item itself and never calls the removal method at
 * all. A perk that works on one and silently does nothing on the other is exactly the defect
 * RS207-05 was, one menu over, so every case here is run both ways where it can be.
 *
 * <p>No {@code @GameTestHolder}: registered by {@code TConstructGameTests} only when Tinkers' is
 * loaded, so every method names its template namespace itself.
 */
@PrefixGameTestTemplate(false)
public class StationTransactionGameTest {

    private static final String EMPTY = "empty";

    /** Where the station is placed in every test below. */
    private static final BlockPos STATION = new BlockPos(1, 1, 1);

    /**
     * E02, by click: an assembled tool arrives stamped, and its parts are consumed once.
     *
     * <p>Tinker's Touch stamps bonus durability on a newly made item, and on a native tool that
     * stamp is written into the tool's own persistent data by the equipment adapter rather than
     * into a vanilla tag. Asserting it on the <em>delivered</em> stack rather than on the preview
     * is the whole point: the preview is shared, and a stamp visible there and absent here would be
     * a perk that only worked in the tooltip.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void assemblyByClickIsStampedOnce(GameTestHelper helper) {
        assembleAndAssert(helper, "tc_station_click", false);
        helper.succeed();
    }

    /** E02, by shift-click: the same assembly through Mantle's quick-move path. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void assemblyByShiftClickIsStampedOnce(GameTestHelper helper) {
        assembleAndAssert(helper, "tc_station_shift", true);
        helper.succeed();
    }

    /**
     * E03: a shift-click with nowhere to put the result changes nothing at all.
     *
     * <p>Mantle's quick-move asks every destination in turn and returns before notifying the slot
     * when none of them took anything, so the native flow never reaches the craft. This asserts the
     * consequence rather than the mechanism: no output, no consumption, and the result still on
     * offer.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void fullInventoryShiftClickConsumesNothing(GameTestHelper helper) {
        Setup setup = setup(helper, "tc_station_full");
        TinkerFixtures.fillInventory(setup.player, Items.COBBLESTONE);

        List<ItemStack> before = inputs(setup.station);
        int crafts = countCrafts(() ->
                setup.menu.clicked(setup.resultSlot, 0, ClickType.QUICK_MOVE, setup.player));

        if (crafts != 0) {
            throw new GameTestAssertException("a shift-click with a full inventory fired "
                    + crafts + " crafting events; nothing may be committed when nothing can be delivered");
        }
        assertInputsUnchanged(before, inputs(setup.station), "a refused shift-click");
        if (setup.menu.getSlot(setup.resultSlot).getItem().isEmpty()) {
            throw new GameTestAssertException(
                    "the station result vanished on a refused shift-click; it must stay on offer");
        }
        helper.succeed();
    }

    /**
     * E12: one take commits exactly once, and a take that cannot be delivered commits nothing.
     *
     * <p>The spec asks for a bounded pending-result escrow for a delivery that fails <em>after</em>
     * consumption. Reading Mantle's quick-move shows that case does not arise at this station: the
     * insertion is attempted first and the craft callback runs only once something has actually
     * moved, so there is never a committed output with nowhere to go — which is what
     * {@link #fullInventoryShiftClickConsumesNothing} measures from the other side. Building an
     * escrow anyway would add a second delivery path racing a native one that already refuses
     * correctly, so this asserts the property the escrow existed to guarantee: the callback runs
     * once, one tool arrives, and the inputs are consumed once rather than consumed and refunded.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void oneTakeCommitsExactlyOnce(GameTestHelper helper) {
        Setup setup = setup(helper, "tc_station_once");

        int delivered = countCrafts(() ->
                setup.menu.clicked(setup.resultSlot, 0, ClickType.QUICK_MOVE, setup.player));

        if (delivered != 1) {
            throw new GameTestAssertException("one station take fired " + delivered
                    + " craft callbacks; it must fire exactly one [delivered="
                    + countOf(setup.player, setup.expected.getItem())
                    + " station=" + inputs(setup.station) + " carried=" + setup.menu.getCarried() + "]");
        }
        if (countOf(setup.player, setup.expected.getItem()) != 1) {
            throw new GameTestAssertException("a single committed take delivered "
                    + countOf(setup.player, setup.expected.getItem()) + " tools");
        }
        for (ItemStack input : inputs(setup.station)) {
            if (!input.isEmpty()) {
                throw new GameTestAssertException(
                        "a committed take left an input behind; the parts are consumed, not refunded");
            }
        }
        helper.succeed();
    }

    /**
     * E04: a denied pre-commit check costs the player nothing.
     *
     * <p>The denial used here is the one this release actually has — a stack lock provider refusing
     * the result — and it lands at {@code Slot.mayPickup}, which is where vanilla asks whether a
     * take is permitted at all. The assertion is the spec's: no input loss, no mutation, no proc,
     * and one refusal rather than a half-completed take.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void deniedTakeCostsNothing(GameTestHelper helper) {
        Setup setup = setup(helper, "tc_station_denied");
        List<ItemStack> before = inputs(setup.station);

        DenyEverything.armed = true;
        int crafts;
        try {
            crafts = countCrafts(() ->
                    setup.menu.clicked(setup.resultSlot, 0, ClickType.PICKUP, setup.player));
        } finally {
            DenyEverything.armed = false;
        }

        if (crafts != 0) {
            throw new GameTestAssertException(
                    "a denied take still fired " + crafts + " craft callbacks");
        }
        assertInputsUnchanged(before, inputs(setup.station), "a denied take");
        if (!setup.menu.getCarried().isEmpty()) {
            throw new GameTestAssertException("a denied take put an item on the cursor");
        }
        helper.succeed();
    }

    /**
     * E05: two players at one station get their own answer, and each commit uses the actual actor.
     *
     * <p>The station computes one result and caches it for everybody. This asserts the two halves
     * of §6.2 that follow from that: a quote is private, so a player with Tinker's Touch is quoted a
     * stamped tool while a player without it is quoted the native one; and the cache is untouched,
     * so the second player's actual take is the native result rather than the first player's bonus.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void twoPlayersDoNotShareEachOthersBonus(GameTestHelper helper) {
        Setup setup = setup(helper, "tc_station_two_a");
        ServerPlayer plain = TinkerFixtures.connectedPlayer(helper, "tc_station_two_b");

        ItemStack base = setup.menu.getSlot(setup.resultSlot).getItem().copy();
        StationQuote stamped = TConstructStationBridge.quote(
                setup.player, setup.menu.containerId, base, setup.station, setup.station);
        StationQuote native_ = TConstructStationBridge.quote(
                plain, setup.menu.containerId, base, setup.station, setup.station);

        if (stamped.revision() == native_.revision()) {
            throw new GameTestAssertException("two quotes shared a revision; revisions are unique");
        }
        if (!stamped.player().equals(setup.player.getUUID())
                || !native_.player().equals(plain.getUUID())) {
            throw new GameTestAssertException("a quote was issued to the wrong player");
        }
        if (stamped.kind() != CraftOperationKind.ASSEMBLY) {
            throw new GameTestAssertException(
                    "a tool-building recipe was classified as " + stamped.kind() + ", not ASSEMBLY");
        }
        if (bonusOf(stamped.preview()) <= 0) {
            throw new GameTestAssertException(
                    "the quote for the player with Tinker's Touch carried no bonus durability");
        }
        if (bonusOf(native_.preview()) != 0) {
            throw new GameTestAssertException(
                    "the quote for a player without the perk carried another player's bonus");
        }

        // And the shared cache itself was never touched: the second player's real take is native.
        AbstractContainerMenu plainMenu = openMenu(setup.station, plain);
        int slot = TinkerFixtures.resultSlotIndex(plainMenu);
        plainMenu.clicked(slot, 0, ClickType.PICKUP, plain);
        if (bonusOf(plainMenu.getCarried()) != 0) {
            throw new GameTestAssertException(
                    "a player without Tinker's Touch took a stamped tool from a shared preview");
        }
        helper.succeed();
    }

    /**
     * E08: repairing a nearly full tool restores at most what is missing.
     *
     * <p>The native recipe decides the cost and the amount; this asserts that nothing this mod does
     * turns that into a free second output, a negative damage value, or a reward. A repair is not a
     * manufacture, and the reward policy must deny it whatever crafting perks the player holds.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void repairNeverOverfillsOrRewards(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, "tc_station_repair");
        TinkerFixtures.enablePerk(player, RegistryPerks.ASSEMBLY_LINE);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.assemblyLinePercent;

        TinkerStationBlockEntity station = TinkerFixtures.station(helper, STATION, 1);
        ItemStack tool = TinkerFixtures.pickaxeOfTier(1);
        ToolStack.from(tool).setDamage(1);
        station.setItem(TinkerStationBlockEntity.TINKER_SLOT, tool);
        station.setItem(TinkerStationBlockEntity.INPUT_SLOT, repairKit(player));

        AbstractContainerMenu menu = openMenu(station, player);
        int slot = TinkerFixtures.resultSlotIndex(menu);
        ItemStack result = menu.getSlot(slot).getItem();
        if (result.isEmpty()) {
            throw new GameTestAssertException(
                    "the station offered no repair for a damaged tool and a repair kit");
        }
        if (TConstructStationBridge.kindOf(station.getLastRecipe()) != CraftOperationKind.REPAIR) {
            throw new GameTestAssertException("a station repair was classified as "
                    + TConstructStationBridge.kindOf(station.getLastRecipe()));
        }

        try {
            config.assemblyLinePercent = 100;
            menu.clicked(slot, 0, ClickType.PICKUP, player);
        } finally {
            config.assemblyLinePercent = previous;
        }

        ItemStack repaired = menu.getCarried();
        if (repaired.isEmpty()) {
            throw new GameTestAssertException("the repair delivered nothing");
        }
        int damage = ToolStack.from(repaired).getDamage();
        if (damage < 0) {
            throw new GameTestAssertException("a repair drove damage negative: " + damage);
        }
        if (ToolDamageUtil.isBroken(repaired)) {
            throw new GameTestAssertException("a repair broke the tool it repaired");
        }
        if (countOf(player, repaired.getItem()) != 0) {
            throw new GameTestAssertException(
                    "a repair at a forced 100% Assembly Line paid a bonus copy; repairs may never be rewarded");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    /** One prepared station: parts in, a player with the crafting perks, and an open menu. */
    private record Setup(ServerPlayer player, TinkerStationBlockEntity station,
                         AbstractContainerMenu menu, int resultSlot, ItemStack expected) {
    }

    private static Setup setup(GameTestHelper helper, String name) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, name);
        TinkerFixtures.enablePerk(player, RegistryPerks.TINKERS_TOUCH);

        IModifiable pickaxe = TinkerFixtures.modifiable("pickaxe");
        ToolBuildingRecipe recipe = TinkerFixtures.buildingRecipeFor(helper.getLevel(), pickaxe);
        List<ItemStack> parts = TinkerFixtures.partsFor(recipe);

        TinkerStationBlockEntity station = TinkerFixtures.station(helper, STATION, parts.size());
        for (int index = 0; index < parts.size(); index++) {
            station.setItem(TinkerStationBlockEntity.INPUT_SLOT + index, parts.get(index));
        }

        AbstractContainerMenu menu = openMenu(station, player);
        int slot = TinkerFixtures.resultSlotIndex(menu);
        ItemStack expected = menu.getSlot(slot).getItem().copy();
        if (expected.isEmpty()) {
            throw new GameTestAssertException(
                    "the station produced no result from the parts of its own building recipe");
        }
        return new Setup(player, station, menu, slot, expected);
    }

    private static void assembleAndAssert(GameTestHelper helper, String name, boolean quickMove) {
        Setup setup = setup(helper, name);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();

        setup.menu.clicked(setup.resultSlot, 0,
                quickMove ? ClickType.QUICK_MOVE : ClickType.PICKUP, setup.player);

        ItemStack delivered = quickMove
                ? firstOf(setup.player, setup.expected.getItem()) : setup.menu.getCarried();
        if (delivered.isEmpty()) {
            throw new GameTestAssertException("the assembled tool was never delivered ("
                    + (quickMove ? "shift-click" : "click") + ")");
        }
        int bonus = bonusOf(delivered);
        if (bonus <= 0) {
            throw new GameTestAssertException("Tinker's Touch left no stamp on the delivered tool ("
                    + (quickMove ? "shift-click" : "click")
                    + "); the delivered stack is the only one that matters");
        }
        if (bonus != config.tinkersTouchPercent) {
            throw new GameTestAssertException("the delivered tool carries a stamp of " + bonus
                    + " where the perk grants " + config.tinkersTouchPercent
                    + "; the transform ran more than once");
        }
        for (ItemStack input : inputs(setup.station)) {
            if (!input.isEmpty()) {
                throw new GameTestAssertException(
                        "the station kept an input after assembling; parts are consumed once");
            }
        }
        if (countOf(setup.player, setup.expected.getItem()) > 1) {
            throw new GameTestAssertException("one assembly delivered more than one tool");
        }
    }

    private static AbstractContainerMenu openMenu(TinkerStationBlockEntity station, ServerPlayer player) {
        AbstractContainerMenu menu = station.createMenu(1, player.getInventory(), player);
        if (menu == null) throw new GameTestAssertException("the station opened no menu");
        return menu;
    }

    /** Every slot of the station, copied, tool slot included. */
    private static List<ItemStack> inputs(TinkerStationBlockEntity station) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < station.getContainerSize(); slot++) {
            items.add(station.getItem(slot).copy());
        }
        return items;
    }

    private static void assertInputsUnchanged(List<ItemStack> before, List<ItemStack> after,
                                              String what) {
        if (before.size() != after.size()) {
            throw new GameTestAssertException(what + " resized the station");
        }
        for (int slot = 0; slot < before.size(); slot++) {
            if (!ItemStack.isSameItemSameTags(before.get(slot), after.get(slot))
                    || before.get(slot).getCount() != after.get(slot).getCount()) {
                throw new GameTestAssertException(
                        what + " changed station slot " + slot + "; nothing may be consumed");
            }
        }
    }

    /** How many craft callbacks {@code action} caused. */
    private static int countCrafts(Runnable action) {
        CraftCounter counter = new CraftCounter();
        MinecraftForge.EVENT_BUS.register(counter);
        try {
            action.run();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
        }
        return counter.count;
    }

    private static int bonusOf(ItemStack stack) {
        return stack.isEmpty() ? 0 : ItemBonusTags.read(stack, ItemBonusTags.BONUS_DURABILITY);
    }

    private static int countOf(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) total++;
        }
        return total;
    }

    private static ItemStack firstOf(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** A repair kit of a material the loaded pack actually has. */
    private static ItemStack repairKit(ServerPlayer player) {
        ItemStack kit = new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("tconstruct", "repair_kit")));
        if (kit.isEmpty()) {
            throw new GameTestAssertException("tconstruct:repair_kit is not a loaded item");
        }
        if (!(kit.getItem() instanceof slimeknights.tconstruct.library.tools.part.IMaterialItem material)) {
            throw new GameTestAssertException("tconstruct:repair_kit is not a material item");
        }
        return material.withMaterial(TinkerFixtures.materialOfTier(1).getVariant());
    }

    /** Counts {@code ItemCraftedEvent} while one interaction runs. */
    private static final class CraftCounter {

        private int count;

        @SubscribeEvent
        public void onCrafted(PlayerEvent.ItemCraftedEvent event) {
            // Every firing counts, empty stack included. A station shift-click delivers the result
            // through Mantle and then reports the craft with the emptied leftover, so filtering on
            // a non-empty stack would count the click path and miss the shift-click path entirely.
            count++;
        }
    }

    /**
     * A stack lock provider that refuses everything while armed.
     *
     * <p>Registered once and switched on for the length of one test: the registry has no removal —
     * deliberately, since a provider disappearing mid-session would change what a player may hold —
     * so a flag is the honest way to borrow it. Disarmed it declines, which is how a provider says
     * "not my item" and leaves every other test unaffected.
     */
    private static final class DenyEverything implements StackLockProvider {

        private static volatile boolean armed;
        private static boolean registered;

        static synchronized void install() {
            if (registered) return;
            LockProviderRegistry.registerStackProvider(new DenyEverything());
            registered = true;
        }

        @Override
        public String id() {
            return "runicskills:gametest_deny_everything";
        }

        @Override
        public Optional<RequirementDecision> resolve(ServerPlayer player, ItemStack stack,
                                                     LockAction action) {
            if (!armed) return Optional.empty();
            return Optional.of(new RequirementDecision(false, Map.of("tinkering", 99),
                    List.of(id()), List.of(),
                    Component.literal("refused by the E04 fixture")));
        }
    }

    static {
        DenyEverything.install();
    }
}
