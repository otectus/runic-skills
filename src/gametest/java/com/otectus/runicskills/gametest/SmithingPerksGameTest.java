package com.otectus.runicskills.gametest;

import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.util.ItemBonusTags;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.events.AnvilPerkHandler;
import com.otectus.runicskills.registry.events.PerkEffectsHandler;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/**
 * The five tinkering perks that used to be terms in the passive-repair sum now do what their
 * tooltips say (RS-205 follow-up).
 *
 * <p>Precision Tools, Tinker's Touch, Tool Smith, Weapon Smith and Runic Engineering all promised
 * something specific — a bigger durability pool, a crafted item's bonus, a repaired tool's speed, a
 * repaired weapon's damage, a runic item's effects — and all five were implemented as "mend one
 * equipped item per second", which is none of those things. Each is now exercised through the
 * mechanic it names, which is why this needs a server: two of the five live in mixins, two are NBT
 * written by a real container menu, and the fifth is an enchantment edit at an anvil.
 *
 * <p>Menus are built for real rather than simulated. A hand-built {@link ServerPlayer} has no
 * connection, and both {@link CraftingMenu} and {@link AnvilMenu} send packets when their slots
 * change, so the menu tests use {@link MockPlayers#connectedServerPlayer} and the pure-arithmetic
 * ones keep the cheaper silent player.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class SmithingPerksGameTest {

    /** Our own empty template; see {@link PlayerLifecycleGameTest} for why it is not forge's. */
    private static final String EMPTY = "empty";

    /** Enough single-point hits that a 50% avoidance is distinguishable from 45% or 55%. */
    private static final int HITS = 10_000;

    /** Seeded so a failure is reproducible; the rate, not the seed, is what is asserted. */
    private static final long SEED = 4321L;

    /** A diamond pickaxe's vanilla maximum damage, and the same raised by the default 15%. */
    private static final int PICKAXE_MAX = 1561;
    private static final int PICKAXE_MAX_AT_15_PERCENT = 1795;

    /**
     * The Weapon Smith attack-damage modifier's identity, duplicated here on purpose.
     *
     * <p>Vanilla matches modifiers by UUID across equip and unequip, so changing the constant in
     * {@code SmithingPerkHandler} would strand the old modifier on every weapon a player is
     * holding. Restating it in a test is how that "never change this" becomes enforceable.
     */
    private static final UUID WEAPON_SMITH_DAMAGE_UUID =
            UUID.fromString("6b1a2f34-9c7d-4e58-8a03-5d2e7f1b4c96");

    /** A container synchronizer that sends nothing, for a player with nowhere to send it. */
    private static final ContainerSynchronizer SILENT = new ContainerSynchronizer() {
        @Override
        public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items,
                                    ItemStack carried, int[] data) {
        }

        @Override
        public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu menu, ItemStack carried) {
        }

        @Override
        public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
        }
    };

    // -- Precision Tools -------------------------------------------------------------------------

    /**
     * "Tool durability increased by X%", measured the only way it can be: as points not spent.
     *
     * <p>Configured at 100%, which is the value where the conversion is easiest to check by hand —
     * doubling an item's life means declining exactly half of the points, so about 5,000 of 10,000
     * single-point hits must land. A naive implementation that avoided X% of points would land
     * about 10,000 here and would be a different perk.
     */
    @GameTest(template = EMPTY)
    public static void precisionToolsSpendsHalfThePointsAtOneHundredPercent(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "precision_tools_tool");
        enablePerk(player, RegistryPerks.PRECISION_TOOLS);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.precisionToolsPercent;
        int taken;
        try {
            config.precisionToolsPercent = 100;
            taken = pointsTaken(new ItemStack(Items.DIAMOND_PICKAXE), player);
        } finally {
            config.precisionToolsPercent = previous;
        }

        if (taken < 4_500 || taken > 5_500) {
            throw new GameTestAssertException("Precision Tools at +100% spent " + taken + " of "
                    + HITS + " durability points; doubling a tool's life means spending about half");
        }
        helper.succeed();
    }

    /** Armour is not a tool, so a chestplate spends every point it is dealt. */
    @GameTest(template = EMPTY)
    public static void precisionToolsDoesNotProtectArmour(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "precision_tools_armour");
        enablePerk(player, RegistryPerks.PRECISION_TOOLS);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.precisionToolsPercent;
        int taken;
        try {
            config.precisionToolsPercent = 100;
            taken = pointsTaken(new ItemStack(Items.DIAMOND_CHESTPLATE), player);
        } finally {
            config.precisionToolsPercent = previous;
        }

        if (taken != HITS) {
            throw new GameTestAssertException("Precision Tools spared " + (HITS - taken)
                    + " points of armour durability; it is a TOOL perk");
        }
        helper.succeed();
    }

    // -- Tinker's Touch --------------------------------------------------------------------------

    /** The stamp is read back as a larger maximum: floor(1561 * 1.15) = 1795. */
    @GameTest(template = EMPTY)
    public static void aStampedToolHasABiggerDurabilityPool(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        if (pickaxe.getMaxDamage() != PICKAXE_MAX) {
            throw new GameTestAssertException("a diamond pickaxe's maximum is "
                    + pickaxe.getMaxDamage() + ", not the " + PICKAXE_MAX + " this test assumes");
        }

        ItemBonusTags.stamp(pickaxe, ItemBonusTags.BONUS_DURABILITY, 15);
        if (pickaxe.getMaxDamage() != PICKAXE_MAX_AT_15_PERCENT) {
            throw new GameTestAssertException("a pickaxe stamped at +15% reports a maximum of "
                    + pickaxe.getMaxDamage() + "; expected " + PICKAXE_MAX_AT_15_PERCENT);
        }
        helper.succeed();
    }

    /** A tool crafted at a real crafting table carries the stamp when it is picked up. */
    @GameTest(template = EMPTY)
    public static void aCraftedToolIsStampedOnPickup(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "tinkers_touch_pickup");
        enablePerk(player, RegistryPerks.TINKERS_TOUCH);

        CraftingMenu menu = craftingTable(helper, player);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.tinkersTouchPercent;
        ItemStack taken;
        try {
            config.tinkersTouchPercent = 15;
            layOutWoodenPickaxe(menu);
            menu.clicked(0, 0, ClickType.PICKUP, player);
            taken = menu.getCarried();
        } finally {
            config.tinkersTouchPercent = previous;
        }

        assertStamped(taken, ItemBonusTags.BONUS_DURABILITY, 15,
                "a tool crafted by a Tinker's Touch player must carry the bonus");
        helper.succeed();
    }

    /**
     * And when it is shift-clicked out, which is the case that made the event-based implementation
     * impossible: {@code quickMoveStack} moves {@code split()} copies into the inventory before the
     * craft event fires, so anything stamped at take time is thrown away.
     */
    @GameTest(template = EMPTY)
    public static void aShiftClickedCraftIsStampedToo(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "tinkers_touch_quick_move");
        enablePerk(player, RegistryPerks.TINKERS_TOUCH);

        CraftingMenu menu = craftingTable(helper, player);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.tinkersTouchPercent;
        try {
            config.tinkersTouchPercent = 15;
            layOutWoodenPickaxe(menu);
            menu.quickMoveStack(player, 0);
        } finally {
            config.tinkersTouchPercent = previous;
        }

        ItemStack moved = firstInInventory(player, Items.WOODEN_PICKAXE);
        assertStamped(moved, ItemBonusTags.BONUS_DURABILITY, 15,
                "a shift-clicked craft must be stamped as well as a picked-up one");
        helper.succeed();
    }

    /** Without the perk nothing is written, so an ordinary craft carries no mod NBT at all. */
    @GameTest(template = EMPTY)
    public static void withoutThePerkACraftCarriesNoTag(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "tinkers_touch_off");

        CraftingMenu menu = craftingTable(helper, player);
        layOutWoodenPickaxe(menu);
        ItemStack result = menu.getSlot(0).getItem();

        if (ItemBonusTags.read(result, ItemBonusTags.BONUS_DURABILITY) != 0) {
            throw new GameTestAssertException("a craft by a player without Tinker's Touch was "
                    + "stamped; a disabled perk must leave no NBT on the item");
        }
        helper.succeed();
    }

    // -- Tool Smith / Weapon Smith ---------------------------------------------------------------

    /** Repairing a pickaxe at an anvil stamps the Tool Smith bonus onto the result. */
    @GameTest(template = EMPTY)
    public static void repairingAToolStampsToolSmith(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "tool_smith_repair");
        enablePerk(player, RegistryPerks.TOOL_SMITH);

        ItemStack result = repairAtAnAnvil(helper, player, halfDamaged(Items.DIAMOND_PICKAXE));
        assertStamped(result, ItemBonusTags.TOOL_SMITH, 10,
                "a Tool Smith's repair must stamp the efficiency bonus on the result");
        helper.succeed();
    }

    /**
     * And the stamp is what the mining-speed handler reads — off the tool, with no perk check, so
     * the bonus survives the item changing hands.
     */
    @GameTest(template = EMPTY)
    public static void aToolSmithStampSpeedsUpMining(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "tool_smith_break_speed");

        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemBonusTags.stamp(pickaxe, ItemBonusTags.TOOL_SMITH, 10);
        player.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);

        // The handler is called directly rather than posted, so the number asserted below is this
        // perk's contribution and not the sum of whatever else is subscribed to BreakSpeed.
        PlayerEvent.BreakSpeed event = new PlayerEvent.BreakSpeed(
                player, Blocks.STONE.defaultBlockState(), 1.0f, player.blockPosition());
        new PerkEffectsHandler().onBreakSpeed(event);

        if (Math.abs(event.getNewSpeed() - 1.10f) > 1.0e-4f) {
            throw new GameTestAssertException("a +10% Tool Smith pickaxe mined at "
                    + event.getNewSpeed() + "x the original speed; expected 1.10x");
        }
        helper.succeed();
    }

    /** Repairing a sword stamps Weapon Smith, and the stamp becomes a visible damage modifier. */
    @GameTest(template = EMPTY)
    public static void repairingAWeaponStampsWeaponSmith(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "weapon_smith_repair");
        enablePerk(player, RegistryPerks.WEAPON_SMITH);

        ItemStack result = repairAtAnAnvil(helper, player, halfDamaged(Items.DIAMOND_SWORD));
        assertStamped(result, ItemBonusTags.WEAPON_SMITH, 10,
                "a Weapon Smith's repair must stamp the damage bonus on the result");
        helper.succeed();
    }

    /** The modifier vanilla renders as the green "+10% Attack Damage" line. */
    @GameTest(template = EMPTY)
    public static void aWeaponSmithStampAddsAnAttackDamageModifier(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ItemBonusTags.stamp(sword, ItemBonusTags.WEAPON_SMITH, 10);

        Multimap<Attribute, AttributeModifier> modifiers =
                sword.getAttributeModifiers(EquipmentSlot.MAINHAND);
        AttributeModifier found = null;
        for (AttributeModifier modifier : modifiers.get(Attributes.ATTACK_DAMAGE)) {
            if (WEAPON_SMITH_DAMAGE_UUID.equals(modifier.getId())) found = modifier;
        }

        if (found == null) {
            throw new GameTestAssertException("a stamped sword carries no Weapon Smith modifier "
                    + "under " + WEAPON_SMITH_DAMAGE_UUID + "; the UUID is fixed and must not change");
        }
        if (Math.abs(found.getAmount() - 0.10D) > 1.0e-9D
                || found.getOperation() != AttributeModifier.Operation.MULTIPLY_TOTAL) {
            throw new GameTestAssertException("Weapon Smith added " + found.getAmount() + " as "
                    + found.getOperation() + "; expected 0.10 as MULTIPLY_TOTAL");
        }
        helper.succeed();
    }

    /** An empty hand carries no modifier, so the hot path really does early-out on a miss. */
    @GameTest(template = EMPTY)
    public static void anUnstampedWeaponGainsNothing(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        for (AttributeModifier modifier
                : sword.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            if (WEAPON_SMITH_DAMAGE_UUID.equals(modifier.getId())) {
                throw new GameTestAssertException("an unrepaired sword carries a Weapon Smith modifier");
            }
        }
        helper.succeed();
    }

    /**
     * Renaming is not repairing. The anvil accepts a name with no second item and produces an
     * output at the same damage value, which must stamp nothing — otherwise the anvil would be a
     * bonus dispenser at one level a go.
     */
    @GameTest(template = EMPTY)
    public static void renamingStampsNothing(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "tool_smith_rename");
        enablePerk(player, RegistryPerks.TOOL_SMITH);

        AnvilMenu menu = anvil(helper, player);
        Slot left = menu.getSlot(0);
        left.set(halfDamaged(Items.DIAMOND_PICKAXE));
        menu.slotsChanged(left.container);
        menu.setItemName("Sharp Bob");

        ItemStack result = menu.getSlot(2).getItem();
        if (ItemBonusTags.read(result, ItemBonusTags.TOOL_SMITH) != 0) {
            throw new GameTestAssertException("renaming a pickaxe stamped the Tool Smith bonus; "
                    + "the tooltip says REPAIRED tools");
        }
        helper.succeed();
    }

    // -- Runic Engineering -----------------------------------------------------------------------

    /**
     * The effect itself, called directly.
     *
     * <p>No item in the dev runtime has "runic" or "rune" in its registry path — the rule exists
     * for packs that ship runic gear — so the perk's own gate can only be tested negatively here.
     * What can be tested is the edit it performs, which it shares with Enchantment Amplifier.
     */
    @GameTest(template = EMPTY)
    public static void theRepairBonusRaisesTheLowestEnchantment(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.enchant(Enchantments.BLOCK_EFFICIENCY, 1);

        if (!AnvilPerkHandler.raiseLowestEnchantment(pickaxe)) {
            throw new GameTestAssertException("Efficiency I was not raised at all");
        }
        int level = net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, pickaxe);
        if (level != 2) {
            throw new GameTestAssertException("Efficiency I became level " + level + ", not II");
        }
        helper.succeed();
    }

    /** An enchantment already at its maximum is left alone, and the caller is told so. */
    @GameTest(template = EMPTY)
    public static void anEnchantmentAtItsMaximumIsNotRaised(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.enchant(Enchantments.BLOCK_EFFICIENCY, Enchantments.BLOCK_EFFICIENCY.getMaxLevel());

        if (AnvilPerkHandler.raiseLowestEnchantment(pickaxe)) {
            throw new GameTestAssertException("an enchantment at its maximum level was raised past it");
        }
        helper.succeed();
    }

    /** And a non-runic item repaired by a Runic Engineer keeps exactly the enchantments it had. */
    @GameTest(template = EMPTY)
    public static void aNonRunicRepairGainsNoEnchantmentLevel(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "runic_engineering_off");
        enablePerk(player, RegistryPerks.RUNIC_ENGINEERING);

        ItemStack pickaxe = halfDamaged(Items.DIAMOND_PICKAXE);
        pickaxe.enchant(Enchantments.BLOCK_EFFICIENCY, 1);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.runicEngineeringPercent;
        ItemStack result;
        try {
            config.runicEngineeringPercent = 100;
            result = repairAtAnAnvil(helper, player, pickaxe);
        } finally {
            config.runicEngineeringPercent = previous;
        }

        int level = net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, result);
        if (level != 1) {
            throw new GameTestAssertException("a diamond pickaxe is not a runic item, but repairing "
                    + "it at 100% Runic Engineering took Efficiency to level " + level);
        }
        helper.succeed();
    }

    // -- helpers ---------------------------------------------------------------------------------

    /**
     * Durability actually spent by {@link #HITS} single-point hits.
     *
     * <p>The damage value is reset before each hit: 10,000 points would break the item several
     * times over, and a broken stack stops being hurt at all — which would silently turn a rate
     * measurement into a count of how long the item lasted.
     */
    private static int pointsTaken(ItemStack stack, ServerPlayer player) {
        RandomSource random = RandomSource.create(SEED);
        int taken = 0;
        for (int i = 0; i < HITS; i++) {
            stack.setDamageValue(0);
            stack.hurt(1, random, player);
            taken += stack.getDamageValue();
        }
        return taken;
    }

    /** An item at half its durability, which is more than the quarter one repair material mends. */
    private static ItemStack halfDamaged(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(stack.getMaxDamage() / 2);
        return stack;
    }

    /**
     * Repairs {@code damaged} with one diamond at a real anvil and returns the result slot's stack.
     * The result is read rather than taken, because taking it charges experience levels a
     * hand-built player does not have.
     */
    private static ItemStack repairAtAnAnvil(GameTestHelper helper, ServerPlayer player, ItemStack damaged) {
        AnvilMenu menu = anvil(helper, player);
        Slot left = menu.getSlot(0);
        left.set(damaged);
        menu.getSlot(1).set(new ItemStack(Items.DIAMOND));
        menu.slotsChanged(left.container);
        return menu.getSlot(2).getItem();
    }

    /** An anvil menu over a real anvil block, because {@code createResult} runs against the level. */
    private static AnvilMenu anvil(GameTestHelper helper, ServerPlayer player) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.ANVIL);
        return new AnvilMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(relative)));
    }

    /** A crafting menu over a real crafting table; the stamp is written from its slot callback. */
    private static CraftingMenu craftingTable(GameTestHelper helper, ServerPlayer player) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.CRAFTING_TABLE);
        return new CraftingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(relative)));
    }

    /**
     * Fills the grid with a wooden pickaxe recipe. Slot 0 is the result and 1-9 are the grid, so
     * planks go along the top row and sticks down the middle.
     */
    private static void layOutWoodenPickaxe(CraftingMenu menu) {
        for (int slot = 1; slot <= 3; slot++) {
            menu.getSlot(slot).set(new ItemStack(Items.OAK_PLANKS));
        }
        menu.getSlot(5).set(new ItemStack(Items.STICK));
        menu.getSlot(8).set(new ItemStack(Items.STICK));
        menu.slotsChanged(menu.getSlot(1).container);
    }

    private static ItemStack firstInInventory(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void assertStamped(ItemStack stack, String key, int expected, String why) {
        if (stack.isEmpty()) {
            throw new GameTestAssertException("expected a stamped item, got nothing: " + why);
        }
        int actual = ItemBonusTags.read(stack, key);
        if (actual != expected) {
            throw new GameTestAssertException(stack + " carries " + key + " = " + actual
                    + ", expected " + expected + ": " + why);
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
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile);
        // No connection, so any container broadcast would fault; see DurabilityPerksGameTest.
        player.containerMenu.setSynchronizer(SILENT);
        return player;
    }

    private static SkillCapability capabilityOf(ServerPlayer player) {
        return player.getCapability(RegistryCapabilities.SKILL).orElseThrow(
                () -> new GameTestAssertException("player has no Runic Skills capability"));
    }
}
