package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.crafting.MasterResearcherRecipeIndex;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.events.ScholarPerkHandler;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Master Researcher discovers real recipes, and a hostile recipe cannot take the craft with it
 * (RS-205-05/06).
 *
 * <p>The perk used to walk the recipe manager and call {@code Ingredient.test} on up to 4096
 * recipes per proc, inside a crafting event, with no isolation — a single modded ingredient that
 * threw turned every craft by a player who had the perk into a crash. That is not reproducible
 * from source inspection: it needs a real recipe manager, a real recipe book, and an ingredient
 * that actually throws.
 *
 * <p>The throwing ingredient is built here rather than registered, because a recipe only enters
 * the server's {@code RecipeManager} through a datapack and this source set ships none.
 * {@link MasterResearcherRecipeIndex#build(Iterable)} takes the recipe collection precisely so the
 * hostile case can be put through the real build path without one.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class MasterResearcherGameTest {

    /** Our own empty template; see {@link PlayerLifecycleGameTest} for why it is not forge's. */
    private static final String EMPTY = "empty";

    private static final ResourceLocation OAK_PLANKS = new ResourceLocation("minecraft", "oak_planks");

    /**
     * A server player with a connection: {@code ServerPlayer.awardRecipes} sends the unlocked
     * recipes down it, so a bare hand-built player would fault on a null one. See
     * {@link MockPlayers} for why forge's own mock server player cannot be used either.
     */
    private static ServerPlayer newPlayer(GameTestHelper helper, String name) {
        return MockPlayers.connectedServerPlayer(helper, name);
    }

    private static SkillCapability capabilityOf(ServerPlayer player) {
        return player.getCapability(RegistryCapabilities.SKILL).orElseThrow(
                () -> new GameTestAssertException("the test player has no skill capability"));
    }

    /**
     * A craft with the perk at 100 % teaches the player something that uses what they made.
     *
     * <p>The proc is repeated because it unlocks at most five recipes at a time and an oak log is
     * an ingredient of many; the assertion is that the intended recipe is reachable at all, not
     * that it happens to come first in the candidate list.
     */
    @GameTest(template = EMPTY)
    public static void craftingTeachesARecipeThatUsesTheResult(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "master_researcher");
        Recipe<?> planks = helper.getLevel().getRecipeManager().byKey(OAK_PLANKS).orElseThrow(
                () -> new GameTestAssertException("this server has no " + OAK_PLANKS + " recipe"));
        if (player.getRecipeBook().contains(planks)) {
            throw new GameTestAssertException("a brand-new player already knew " + OAK_PLANKS);
        }

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int researcher = config.masterResearcherPercent;
        int inventor = config.inventorPercent;
        try {
            config.masterResearcherPercent = 100;
            config.inventorPercent = 0;   // so a random Inventor unlock cannot pass this test for us
            enable(player, RegistryPerks.MASTER_RESEARCHER.get());

            ScholarPerkHandler handler = new ScholarPerkHandler();
            ItemStack crafted = new ItemStack(Items.OAK_LOG);
            for (int proc = 0; proc < 20 && !player.getRecipeBook().contains(planks); proc++) {
                handler.onItemCrafted(new PlayerEvent.ItemCraftedEvent(
                        player, crafted, new SimpleContainer(1)));
            }

            if (!player.getRecipeBook().contains(planks)) {
                throw new GameTestAssertException("20 Master Researcher procs on an oak log never"
                        + " unlocked " + OAK_PLANKS + ", which is one of the log's own recipes");
            }
        } finally {
            config.masterResearcherPercent = researcher;
            config.inventorPercent = inventor;
        }
        helper.succeed();
    }

    /**
     * A recipe whose ingredient throws while being indexed is kept, not dropped, and not fatal.
     *
     * <p>Kept matters as much as survived: an un-indexable recipe goes to the fallback bucket,
     * which every lookup returns, so the perk still finds it — it just verifies it at craft time.
     */
    @GameTest(template = EMPTY)
    public static void aThrowingIngredientIsIsolatedAndItsRecipeKept(GameTestHelper helper) {
        ResourceLocation hostileId = new ResourceLocation(RunicSkills.MOD_ID, "gametest_hostile_recipe");
        MasterResearcherRecipeIndex index;
        try {
            index = MasterResearcherRecipeIndex.build(List.of(new HostileRecipe(hostileId)));
        } catch (RuntimeException e) {
            throw new GameTestAssertException("indexing a recipe with a throwing ingredient escaped"
                    + " its isolation: " + e);
        }

        // Any item at all: the fallback bucket is returned by every lookup, which is the point.
        Set<ResourceLocation> candidates = index.candidates(Items.DIAMOND);
        if (!candidates.contains(hostileId)) {
            throw new GameTestAssertException("the un-indexable recipe was dropped instead of being"
                    + " put in the fallback bucket; the perk would never find it again");
        }
        helper.succeed();
    }

    /** Grants the perk by hand: the skill level it gates on, and rank 1. */
    private static void enable(ServerPlayer player, Perk perk) {
        SkillCapability capability = capabilityOf(player);
        capability.setSkillLevel(perk.getSkill(), Math.max(1, perk.getLvl()));
        capability.setPerkRank(perk, 1);
        if (!perk.isEnabled(player)) {
            throw new GameTestAssertException("could not enable " + perk.getName() + " on the test"
                    + " player; the perk gate has changed shape");
        }
    }

    /** An ingredient that refuses to say what it matches — the shape that used to crash crafts. */
    private static final class HostileIngredient extends Ingredient {

        private HostileIngredient() {
            super(Stream.empty());
        }

        /** Not empty, so the index actually asks it for its items. */
        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public ItemStack[] getItems() {
            throw new IllegalStateException("gametest ingredient refuses to enumerate");
        }

        @Override
        public boolean test(ItemStack stack) {
            throw new IllegalStateException("gametest ingredient refuses to match");
        }
    }

    /** The least a recipe can be and still be something the index will try to read. */
    private record HostileRecipe(ResourceLocation id) implements Recipe<Container> {

        @Override
        public NonNullList<Ingredient> getIngredients() {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            ingredients.add(new HostileIngredient());
            return ingredients;
        }

        @Override
        public boolean matches(Container container, Level level) {
            return false;
        }

        @Override
        public ItemStack assemble(Container container, RegistryAccess registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return false;
        }

        @Override
        public ItemStack getResultItem(RegistryAccess registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return RecipeSerializer.SHAPELESS_RECIPE;
        }

        @Override
        public RecipeType<?> getType() {
            return RecipeType.CRAFTING;
        }
    }
}
