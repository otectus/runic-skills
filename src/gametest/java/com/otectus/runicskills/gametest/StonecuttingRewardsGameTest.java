package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.crafting.CraftingConversionIndex;
import com.otectus.runicskills.common.rules.PackRule;
import com.otectus.runicskills.common.rules.PackRuleIndex;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StonecuttingRewardsGameTest {
    @GameTest(template = "empty")
    public static void previewSelectionPaysNothingAndCommittedCutsPayOnce(GameTestHelper helper) {
        ServerPlayer player = player(helper, "stonecutting_commit");
        StonecutterMenu menu = menu(player, 8, Items.STONE_SLAB);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.stoneCutterEfficiencyPercent;
        try {
            config.stoneCutterEfficiencyPercent = 100;
            int selected = menu.getSelectedRecipeIndex();
            for (int attempt = 0; attempt < 100; attempt++) menu.clickMenuButton(player, selected);
            if (menu.getSlot(1).getItem().getCount() != 2 || count(player, Items.STONE_SLAB) != 0) {
                throw new GameTestAssertException("cycling a stonecutting preview granted a bonus");
            }
            menu.clicked(1, 0, ClickType.PICKUP, player);
            if (menu.getCarried().getCount() != 2 || count(player, Items.STONE_SLAB) != 1
                    || menu.getSlot(0).getItem().getCount() != 7) {
                throw new GameTestAssertException("a committed cut did not deliver exactly one bonus item");
            }
            menu.clicked(1, 0, ClickType.QUICK_MOVE, player);
            if (!menu.getSlot(0).getItem().isEmpty() || count(player, Items.STONE_SLAB) != 22) {
                throw new GameTestAssertException("shift-cutting did not pay one bonus per consumed stone");
            }
        } finally {
            config.stoneCutterEfficiencyPercent = previous;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aFullInventoryDoesNotSpendInputsOrAwardBonusItems(GameTestHelper helper) {
        ServerPlayer player = player(helper, "stonecutting_full");
        StonecutterMenu menu = menu(player, 3, Items.STONE_SLAB);
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.stoneCutterEfficiencyPercent;
        try {
            config.stoneCutterEfficiencyPercent = 100;
            menu.clicked(1, 0, ClickType.QUICK_MOVE, player);
            if (menu.getSlot(0).getItem().getCount() != 3 || count(player, Items.STONE_SLAB) != 0) {
                throw new GameTestAssertException("a refused shift-cut consumed inputs or paid a bonus");
            }
        } finally {
            config.stoneCutterEfficiencyPercent = previous;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reversibleDatapackStonecuttingDoesNotMintMaterials(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        List<Recipe<?>> previousRecipes = new ArrayList<>(manager.getRecipes());
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.stoneCutterEfficiencyPercent;
        try {
            List<Recipe<?>> recipes = new ArrayList<>(previousRecipes);
            recipes.add(new StonecutterRecipe(new ResourceLocation(RunicSkills.MOD_ID, "test_stone_to_cobble"),
                    "", Ingredient.of(Items.STONE), new ItemStack(Items.COBBLESTONE)));
            recipes.add(new StonecutterRecipe(new ResourceLocation(RunicSkills.MOD_ID, "test_cobble_to_stone"),
                    "", Ingredient.of(Items.COBBLESTONE), new ItemStack(Items.STONE)));
            manager.replaceRecipes(recipes);
            CraftingConversionIndex.invalidate();
            config.stoneCutterEfficiencyPercent = 100;
            ServerPlayer player = player(helper, "stonecutting_reversible");
            StonecutterMenu menu = menu(player, 1, Items.COBBLESTONE);
            menu.clicked(1, 0, ClickType.PICKUP, player);
            if (menu.getCarried().getCount() != 1 || count(player, Items.COBBLESTONE) != 0) {
                throw new GameTestAssertException("a reversible stonecutting recipe awarded free material");
            }
        } finally {
            manager.replaceRecipes(previousRecipes);
            CraftingConversionIndex.invalidate();
            config.stoneCutterEfficiencyPercent = previous;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void packRewardDenialsAlsoApplyToStonecutting(GameTestHelper helper) {
        ServerPlayer player = player(helper, "stonecutting_pack_denial");
        StonecutterMenu menu = menu(player, 1, Items.STONE_SLAB);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.stoneCutterEfficiencyPercent;
        try {
            PackRuleIndex.install(List.of(new PackRule(new ResourceLocation(RunicSkills.MOD_ID, "test_no_copies"),
                    PackRule.Kind.CRAFT_REWARD_POLICY, 100, PackRule.Match.any(), java.util.Map.of(), false)));
            config.stoneCutterEfficiencyPercent = 100;
            menu.clicked(1, 0, ClickType.PICKUP, player);
            if (menu.getCarried().getCount() != 2 || count(player, Items.STONE_SLAB) != 0) {
                throw new GameTestAssertException("stonecutting bypassed a pack's extra-output denial");
            }
        } finally {
            PackRuleIndex.clear();
            config.stoneCutterEfficiencyPercent = previous;
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, name);
        var perk = RegistryPerks.STONE_CUTTER_EFFICIENCY.get();
        SkillCapability capability = SkillCapability.get(player);
        capability.setSkillLevel(perk.getSkill(), Math.max(1, perk.requiredLevel));
        capability.setPerkRank(perk, 1);
        if (!perk.isEnabled(player)) throw new GameTestAssertException("could not enable Stone Cutter Efficiency");
        return player;
    }

    private static StonecutterMenu menu(ServerPlayer player, int count, Item output) {
        StonecutterMenu menu = new StonecutterMenu(1, player.getInventory());
        player.containerMenu = menu;
        menu.getSlot(0).set(new ItemStack(Items.STONE, count));
        for (int index = 0; index < menu.getRecipes().size(); index++) {
            if (menu.getRecipes().get(index).getResultItem(player.level().registryAccess()).is(output)) {
                menu.clickMenuButton(player, index);
                return menu;
            }
        }
        throw new GameTestAssertException("no stonecutting recipe for " + output);
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }
}
