package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/**
 * Efficient Crafting saves the materials its tooltip promises, and never counterfeits them
 * (RS-205-04, spec §8).
 *
 * <p>Through 2.0.4 the perk inserted one bonus copy of the result instead, so the interesting
 * behaviour is not "the player gained something" — the old implementation did that too — but
 * <em>what</em> they gained. That can only be judged against a real recipe consumption, remainders
 * included, so it needs a running server: the refund is a diff of the grid across vanilla's own
 * {@code ResultSlot#onTake}, and the cake case below is exactly the one the bonus-output version
 * turned into duplication.
 *
 * <p>The slot is built directly on a {@link TransientCraftingContainer} rather than opened through
 * {@code InventoryMenu} or {@code CraftingMenu}, because that container tells its menu about every
 * {@code removeItem}, and a menu's crafting-grid notification sends a packet down the player's
 * connection — which a player constructed outside the login flow does not have. The slot class is
 * still vanilla's, which is what the mixin actually gates on.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class EfficientCraftingGameTest {

    /** Our own empty template; see {@link PlayerLifecycleGameTest} for why it is not forge's. */
    private static final String EMPTY = "empty";

    // -- 2x2: the simple saving -------------------------------------------------------------------

    /** One log, one craft, 100% proc: the log is still on the grid afterwards. */
    @GameTest(template = EMPTY)
    public static void aProcLeavesTheIngredientOnTheGrid(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "efficient_crafting_planks");
        enablePerk(player, RegistryPerks.EFFICIENT_CRAFTING);

        CraftingContainer grid = grid(2);
        grid.setItem(0, new ItemStack(Items.OAK_LOG));
        craft(player, grid, new ItemStack(Items.OAK_PLANKS, 4), 100);

        assertSlot(grid, 0, Items.OAK_LOG, 1, "a 100% Efficient Crafting must not consume the log");
        helper.succeed();
    }

    /** The same craft without a proc consumes the log, as vanilla does. */
    @GameTest(template = EMPTY)
    public static void withoutAProcTheIngredientIsConsumed(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "efficient_crafting_no_proc");
        enablePerk(player, RegistryPerks.EFFICIENT_CRAFTING);

        CraftingContainer grid = grid(2);
        grid.setItem(0, new ItemStack(Items.OAK_LOG));
        craft(player, grid, new ItemStack(Items.OAK_PLANKS, 4), 0);

        if (!grid.getItem(0).isEmpty()) {
            throw new GameTestAssertException("a 0% Efficient Crafting left " + grid.getItem(0)
                    + " on the grid; crafting must consume normally when the perk does not proc");
        }
        helper.succeed();
    }

    /** A stack loses one unit per craft, so the refund puts back exactly one — not the stack. */
    @GameTest(template = EMPTY)
    public static void aProcOnAStackPutsBackExactlyOneUnit(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "efficient_crafting_stack");
        enablePerk(player, RegistryPerks.EFFICIENT_CRAFTING);

        CraftingContainer grid = grid(2);
        grid.setItem(0, new ItemStack(Items.OAK_LOG, 7));
        craft(player, grid, new ItemStack(Items.OAK_PLANKS, 4), 100);

        assertSlot(grid, 0, Items.OAK_LOG, 7, "one craft consumes one log, so one log comes back");
        helper.succeed();
    }

    // -- 3x3: the case the old implementation duplicated ------------------------------------------

    /**
     * Cake: three milk buckets that vanilla turns into empty buckets, plus six ingredients it
     * consumes outright. The remainders must be left alone — a refunded milk bucket would leave the
     * player holding both it and the empty bucket, which is the duplication this perk used to be.
     */
    @GameTest(template = EMPTY)
    public static void remaindersAreNeverDuplicated(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "efficient_crafting_cake");
        enablePerk(player, RegistryPerks.EFFICIENT_CRAFTING);

        CraftingContainer grid = grid(3);
        for (int i = 0; i < 3; i++) grid.setItem(i, new ItemStack(Items.MILK_BUCKET));
        grid.setItem(3, new ItemStack(Items.SUGAR));
        grid.setItem(4, new ItemStack(Items.EGG));
        grid.setItem(5, new ItemStack(Items.SUGAR));
        for (int i = 6; i < 9; i++) grid.setItem(i, new ItemStack(Items.WHEAT));

        craft(player, grid, new ItemStack(Items.CAKE), 100);

        if (count(grid, Items.MILK_BUCKET) != 0) {
            throw new GameTestAssertException("Efficient Crafting refunded "
                    + count(grid, Items.MILK_BUCKET) + " milk bucket(s) on top of the empty buckets "
                    + "vanilla returned; container-item remainders must never be duplicated");
        }
        if (count(grid, Items.BUCKET) != 3) {
            throw new GameTestAssertException("expected exactly 3 empty buckets after a cake craft, "
                    + "got " + count(grid, Items.BUCKET));
        }
        // The genuinely consumed ingredients are the ones the perk pays for.
        assertSlot(grid, 3, Items.SUGAR, 1, "consumed sugar must be refunded");
        assertSlot(grid, 4, Items.EGG, 1, "a consumed egg must be refunded");
        assertSlot(grid, 5, Items.SUGAR, 1, "consumed sugar must be refunded");
        for (int i = 6; i < 9; i++) {
            assertSlot(grid, i, Items.WHEAT, 1, "consumed wheat must be refunded");
        }
        helper.succeed();
    }

    // -- helpers -----------------------------------------------------------------------------------

    /**
     * Runs one vanilla crafting operation at the given proc chance. {@code onTake} is exactly one
     * operation, which is what a quick-move loop calls once per craft, so this is the same unit the
     * perk rolls on in play.
     */
    private static void craft(ServerPlayer player, CraftingContainer grid, ItemStack result, int percent) {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.efficientCraftingPercent;
        try {
            config.efficientCraftingPercent = percent;
            resultSlot(player, grid).onTake(player, result);
        } finally {
            config.efficientCraftingPercent = previous;
        }
    }

    /** A vanilla {@link ResultSlot} over {@code grid}; the mixin only accepts this exact class. */
    private static Slot resultSlot(ServerPlayer player, CraftingContainer grid) {
        return new ResultSlot(player, grid, new ResultContainer(), 0, 0, 0);
    }

    /** A square grid whose menu callbacks do nothing; see the class javadoc for why. */
    private static CraftingContainer grid(int side) {
        AbstractContainerMenu silent = new AbstractContainerMenu(MenuType.CRAFTING, 0) {
            @Override
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                return true;
            }

            @Override
            public void slotsChanged(net.minecraft.world.Container container) {
            }
        };
        return new TransientCraftingContainer(silent, side, side);
    }

    private static int count(CraftingContainer grid, Item item) {
        int total = 0;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void assertSlot(CraftingContainer grid, int index, Item item, int count, String why) {
        ItemStack stack = grid.getItem(index);
        if (!stack.is(item) || stack.getCount() != count) {
            throw new GameTestAssertException("slot " + index + " holds " + stack + ", expected "
                    + count + "x " + item + ": " + why);
        }
    }

    /** Gives the player the perk: its skill at the required level, and one rank taken. */
    private static void enablePerk(ServerPlayer player, RegistryObject<Perk> registered) {
        Perk perk = registered.get();
        SkillCapability capability = capabilityOf(player);
        capability.setSkillLevel(perk.getSkill(), Math.max(1, perk.requiredLevel));
        capability.setPerkRank(perk, 1);
        if (!perk.isEnabled(player)) {
            throw new GameTestAssertException("could not enable perk " + perk.getName()
                    + " for the test player; the fixture, not the perk, is broken");
        }
    }

    private static ServerPlayer newPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("runicskills-gametest:" + name).getBytes()), name);
        return new ServerPlayer(level.getServer(), level, profile);
    }

    private static SkillCapability capabilityOf(ServerPlayer player) {
        return player.getCapability(RegistryCapabilities.SKILL).orElseThrow(
                () -> new GameTestAssertException("player has no Runic Skills capability"));
    }
}
