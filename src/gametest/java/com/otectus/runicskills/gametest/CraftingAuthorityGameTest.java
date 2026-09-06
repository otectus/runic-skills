package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.events.CraftRewardDispatcher;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

/**
 * The crafting bonus perks are server-authoritative and roll independently (RS-205-03, spec 7/9).
 *
 * <p>Three defects are covered. The handler used to accept any {@link Player}, and
 * {@code ItemCraftedEvent} fires on both logical sides — so a client that had the perk inserted a
 * bonus stack the server never granted, which the player sees as an item that vanishes on the next
 * sync. The perk percentages were then summed into one chance that rolled once, so several crafting
 * perks capped at a single bonus item and, past 100% together, produced one on every craft. And once
 * they rolled independently nothing bounded the total, so a player holding several of them was paid
 * several copies of a result whose materials had been spent once (RS207-10).
 *
 * <p>Percentages are pinned to 100 and 0 rather than sampled, because what is asserted here is the
 * <em>count</em> of independent successes, not a rate: three perks that all certainly proc must
 * yield three items, which is exactly what a single summed roll cannot do.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class CraftingAuthorityGameTest {

    /** Our own empty template; see {@link PlayerLifecycleGameTest} for why it is not forge's. */
    private static final String EMPTY = "empty";

    // -- independent rolls -----------------------------------------------------------------------

    /** One certain perk, one bonus item of count 1 — the baseline the other cases measure against. */
    @GameTest(template = EMPTY)
    public static void oneCertainPerkYieldsExactlyOneBonus(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "crafting_authority_one_perk");
        enablePerk(player, RegistryPerks.ASSEMBLY_LINE);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int assembly = config.assemblyLinePercent;
        try {
            config.assemblyLinePercent = 100;
            fireCraft(player, new ItemStack(Items.OAK_PLANKS, 4));
        } finally {
            config.assemblyLinePercent = assembly;
        }

        assertInventory(player, Items.OAK_PLANKS, 1, 1,
                "Assembly Line at 100% must grant exactly one bonus plank");
        helper.succeed();
    }

    /**
     * Three certain perks roll independently up to the budget, and never past it.
     *
     * <p>Both halves matter, and they used to contradict each other. Summing the percentages into
     * one roll meant three perks could only ever grant one item; rolling them independently with
     * nothing bounding the total meant a player holding several crafting perks was paid several
     * copies of a result whose materials had been spent once (RS207-10). So the perks still roll one
     * by one — raise the budget and all three pay — and the budget is what decides the total.
     */
    @GameTest(template = EMPTY)
    public static void certainPerksRollIndependentlyUpToTheCap(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "crafting_authority_three_perks");
        enablePerk(player, RegistryPerks.ASSEMBLY_LINE);
        enablePerk(player, RegistryPerks.MASS_PRODUCTION);
        enablePerk(player, RegistryPerks.MEDIEVAL_ARCHITECTURE);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int assembly = config.assemblyLinePercent;
        int mass = config.massProductionPercent;
        int medieval = config.medievalArchitecturePercent;
        int cap = config.craftRewardMaxExtraOutputs;
        try {
            config.assemblyLinePercent = 100;
            config.massProductionPercent = 100;
            config.medievalArchitecturePercent = 100;

            // Stone is a BlockItem and is neither an "ingot" nor a "planks", so exactly the three
            // perks under test are eligible for it.
            config.craftRewardMaxExtraOutputs = 3;
            fireCraft(player, new ItemStack(Items.STONE));
            assertInventory(player, Items.STONE, 1, 3,
                    "three perks at 100% with a budget of 3 must roll independently and grant three");

            player.getInventory().clearContent();
            config.craftRewardMaxExtraOutputs = 1;
            fireCraft(player, new ItemStack(Items.STONE));
            assertInventory(player, Items.STONE, 1, 1,
                    "the same three perks with a budget of 1 must grant exactly one (RS207-10)");
        } finally {
            config.assemblyLinePercent = assembly;
            config.massProductionPercent = mass;
            config.medievalArchitecturePercent = medieval;
            config.craftRewardMaxExtraOutputs = cap;
        }
        helper.succeed();
    }

    /** At 0% nothing procs, with every eligible perk taken. */
    @GameTest(template = EMPTY)
    public static void zeroPercentYieldsNothing(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "crafting_authority_zero_percent");
        enablePerk(player, RegistryPerks.ASSEMBLY_LINE);
        enablePerk(player, RegistryPerks.MASS_PRODUCTION);
        enablePerk(player, RegistryPerks.MEDIEVAL_ARCHITECTURE);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int assembly = config.assemblyLinePercent;
        int mass = config.massProductionPercent;
        int medieval = config.medievalArchitecturePercent;
        try {
            config.assemblyLinePercent = 0;
            config.massProductionPercent = 0;
            config.medievalArchitecturePercent = 0;
            fireCraft(player, new ItemStack(Items.STONE));
        } finally {
            config.assemblyLinePercent = assembly;
            config.massProductionPercent = mass;
            config.medievalArchitecturePercent = medieval;
        }

        assertInventory(player, Items.STONE, 0, 0, "0% must never proc");
        helper.succeed();
    }

    // -- side authority --------------------------------------------------------------------------

    /**
     * A player that is not a {@link ServerPlayer} gets nothing.
     *
     * <p>The client-side firing itself cannot be reproduced from a GameTest — there is no
     * {@code ClientLevel} on a test server to build a client player in. What the handler tests is
     * the type, so the stand-in is a plain {@link Player} in the server level: it takes the same
     * rejection branch a client player would, before any perk or config value is read.
     */
    @GameTest(template = EMPTY)
    public static void nonServerPlayerGetsNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getInventory().clearContent();

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int assembly = config.assemblyLinePercent;
        try {
            config.assemblyLinePercent = 100;
            fireCraft(player, new ItemStack(Items.OAK_PLANKS, 4));
        } finally {
            config.assemblyLinePercent = assembly;
        }

        assertInventory(player, Items.OAK_PLANKS, 0, 0,
                "a non-server player must never have items inserted (RS-205-03)");
        helper.succeed();
    }

    /**
     * A {@link FakePlayer} gets nothing either — and since it is a {@code ServerPlayer} subclass,
     * the type check alone does not cover it. The rejection precedes every perk and config lookup,
     * so this holds whether or not an automated crafter carries perk data at all.
     */
    @GameTest(template = EMPTY)
    public static void fakePlayerGetsNothing(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int assembly = config.assemblyLinePercent;
        try {
            config.assemblyLinePercent = 100;
            fireCraft(player, new ItemStack(Items.OAK_PLANKS, 4));
        } finally {
            config.assemblyLinePercent = assembly;
        }

        assertInventory(player, Items.OAK_PLANKS, 0, 0,
                "an automated crafter must not be paid crafting perks");
        helper.succeed();
    }

    // -- fixtures --------------------------------------------------------------------------------

    private static void fireCraft(Player player, ItemStack result) {
        new CraftRewardDispatcher().onItemCrafted(
                new PlayerEvent.ItemCraftedEvent(player, result, grid(player)));
    }

    /**
     * A 3x3 crafting grid holding the torch recipe, the shape the event carries during a real craft.
     *
     * <p>Two distinct ingredients, deliberately. A grid holding one item type is the shape of every
     * compression and decompression recipe in the game, and {@code CraftRewardPolicy} refuses those
     * outright — so a single-ingredient fixture would make every positive case below assert nothing
     * (RS207-01). The result stack is supplied by each test; what the grid decides is how the
     * operation classifies.
     *
     * <p>Filled through the {@code NonNullList} constructor rather than {@code setItem}, so the
     * container never calls back into the menu it is attached to — the menu is only a required
     * constructor argument here, not part of what is being tested.
     */
    private static Container grid(Player player) {
        NonNullList<ItemStack> slots = NonNullList.withSize(9, ItemStack.EMPTY);
        slots.set(0, new ItemStack(Items.COAL));
        slots.set(3, new ItemStack(Items.STICK));
        return new TransientCraftingContainer(player.inventoryMenu, 3, 3, slots);
    }

    /**
     * A server player with a connection: {@code Inventory.placeItemBackInInventory} sends a slot
     * packet, so a player built by hand (as in {@link XpBonusGameTest}) would fault on a null one.
     * See {@link MockPlayers} for why forge's own mock server player cannot be used either.
     */
    private static ServerPlayer newPlayer(GameTestHelper helper, String name) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, name);
        player.getInventory().clearContent();
        return player;
    }

    /**
     * Gives the player one rank of the perk, at the skill level it requires.
     *
     * <p>The level is raised to the requirement, never lowered to it: Mass Production (Building 32)
     * and Medieval Architecture (Building 21) are perks of the same skill, so writing the second
     * one's requirement over the first one's level silently switched Mass Production back off, and
     * the {@code isEnabled} check below only ever looks at the perk just granted.
     */
    private static void enablePerk(ServerPlayer player, RegistryObject<Perk> registered) {
        Perk perk = registered.get();
        SkillCapability capability = player.getCapability(RegistryCapabilities.SKILL).orElseThrow(
                () -> new GameTestAssertException("player has no Runic Skills capability"));
        capability.setSkillLevel(perk.getSkill(), Math.max(
                capability.getSkillLevel(perk.getSkill()), Math.max(1, perk.requiredLevel)));
        capability.setPerkRank(perk, 1);
        if (!perk.isEnabled(player)) {
            throw new GameTestAssertException("could not enable perk " + perk.getName()
                    + " for the test player; the fixture, not the perk, is broken");
        }
    }

    /**
     * Asserts both the number of stacks of {@code item} and the total item count, because "one
     * bonus" means one item — an implementation that inserted a stack of three would satisfy a
     * slot count alone.
     */
    private static void assertInventory(Player player, Item item, int expectedStacks,
                                        int expectedTotal, String what) {
        int stacks = 0;
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                stacks++;
                total += stack.getCount();
            }
        }
        if (stacks != expectedStacks || total != expectedTotal) {
            throw new GameTestAssertException(what + ": expected " + expectedStacks + " stack(s) "
                    + "totalling " + expectedTotal + ", found " + stacks + " stack(s) totalling " + total);
        }
    }
}
