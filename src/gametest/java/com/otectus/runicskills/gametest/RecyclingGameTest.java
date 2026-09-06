package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.crafting.RecyclingIndex;
import com.otectus.runicskills.common.crafting.RecyclingRule;
import com.otectus.runicskills.common.util.ContainerInteraction;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.events.WorkshopPerkHandler;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Salvage costs the thing being salvaged, and is read from rules rather than derived (RS207-02/03).
 *
 * <p>Two separate defects are covered. The first is that Resource Efficiency and Salvage Expert
 * paid out on {@code BlockEvent.BreakEvent}, which consumes nothing: the block dropped as an item
 * <em>and</em> returned its materials, so placing and breaking one crafting table in a loop
 * produced planks forever. The second is that what came back was derived by walking the recipe
 * manager per event, which scaled with the pack and treated "there is a recipe" as "this is worth
 * its ingredients".
 *
 * <p>The first is asserted structurally — no handler in this mod subscribes to a block break for
 * salvage any more — because the loop it enabled is a thousand identical iterations of an event
 * whose absence is the actual fix. The second is asserted against the index.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class RecyclingGameTest {

    private static final String EMPTY = "empty";

    /**
     * E07: no amount of placing and breaking a crafting table produces ingredients.
     *
     * <p>Stated as "the workshop handler has no block-break subscriber at all", which is the
     * property that makes the loop impossible rather than merely unprofitable. A test that broke a
     * thousand blocks and counted drops would pass just as well against a handler that had been
     * given a cooldown, and a cooldown is not the fix.
     */
    @GameTest(template = EMPTY)
    public static void breakingABlockNeverSalvagesIt(GameTestHelper helper) {
        List<String> subscribers = new ArrayList<>();
        for (Method method : WorkshopPerkHandler.class.getDeclaredMethods()) {
            if (method.getAnnotation(SubscribeEvent.class) == null) continue;
            if (Arrays.stream(method.getParameterTypes())
                    .anyMatch(BlockEvent.BreakEvent.class::isAssignableFrom)) {
                subscribers.add(method.getName());
            }
        }
        if (!subscribers.isEmpty()) {
            throw new GameTestAssertException("WorkshopPerkHandler still subscribes to"
                    + " BlockEvent.BreakEvent from " + subscribers + "; a break consumes nothing, so"
                    + " paying materials for one makes place-and-break a material source (RS207-02)");
        }
        helper.succeed();
    }

    /** Nothing is salvageable unless a rule says so. An unknown item yields no rule at all. */
    @GameTest(template = EMPTY)
    public static void anItemWithNoRuleYieldsNothing(GameTestHelper helper) {
        RecyclingIndex index = RecyclingIndex.build(List.of(craftingTableRule()));
        if (index.rule(Items.NETHERITE_HOE) != null) {
            throw new GameTestAssertException("an item with no rule was found salvageable;"
                    + " the allowlist is the whole of the mechanic (RS207-03)");
        }
        helper.succeed();
    }

    /**
     * An enchanted or renamed item is worth more than its materials, so it is not salvage material.
     * Nor is a stack too small to pay the rule's cost.
     */
    @GameTest(template = EMPTY)
    public static void nbtAndCountAreEnforcedByTheIndex(GameTestHelper helper) {
        RecyclingIndex index = RecyclingIndex.build(List.of(twoTableRule()));

        ItemStack tagged = new ItemStack(Items.CRAFTING_TABLE, 2);
        tagged.getOrCreateTag().putInt("runicskills.test", 1);
        if (index.ruleFor(tagged) != null) {
            throw new GameTestAssertException("an item carrying NBT was accepted for salvage");
        }

        if (index.ruleFor(new ItemStack(Items.CRAFTING_TABLE, 1)) != null) {
            throw new GameTestAssertException("a stack of 1 was accepted for a rule that consumes 2");
        }
        if (index.ruleFor(new ItemStack(Items.CRAFTING_TABLE, 2)) == null) {
            throw new GameTestAssertException("a stack of 2 was refused by a rule that consumes 2;"
                    + " the fixture, not the rule, is broken");
        }
        helper.succeed();
    }

    /**
     * A rule the pack got wrong costs that rule and nothing else.
     *
     * <p>The index is what a datapack reload installs, and a pack with one broken entry must not
     * lose the rest of its salvage. A second rule claiming an input another rule already owns is
     * the case that would otherwise resolve by iteration order.
     */
    @GameTest(template = EMPTY)
    public static void oneBadRuleDoesNotCostTheOthers(GameTestHelper helper) {
        List<RecyclingRule> rules = new ArrayList<>();
        rules.add(craftingTableRule());
        rules.add(null);
        rules.add(new RecyclingRule(new ResourceLocation(RunicSkills.MOD_ID, "no_outputs"),
                Items.FURNACE, 1, List.of(), 1, true));
        rules.add(new RecyclingRule(new ResourceLocation(RunicSkills.MOD_ID, "duplicate"),
                Items.CRAFTING_TABLE, 1,
                List.of(new RecyclingRule.Output(Items.DIAMOND, 64)), 64, true));

        RecyclingIndex index = RecyclingIndex.build(rules);
        if (index.size() != 1) {
            throw new GameTestAssertException("a rule list with one null, one empty and one"
                    + " duplicate entry built " + index.size() + " rules; expected exactly the 1"
                    + " good one");
        }
        RecyclingRule kept = index.rule(Items.CRAFTING_TABLE);
        if (kept == null || kept.outputs().get(0).item() != Items.OAK_PLANKS) {
            throw new GameTestAssertException("the duplicate rule overwrote the first one;"
                    + " which of two rules wins would then depend on datapack iteration order");
        }
        helper.succeed();
    }

    /**
     * The grindstone is the surface that makes salvage cost something.
     *
     * <p>Forge's {@code OnPlaceItem} sets the output and vanilla's take path then clears the inputs,
     * so the payout <em>is</em> the output and the input is consumed exactly once. This asserts the
     * half this mod owns: with the perk taken and a rule present, an output appears, and the
     * grindstone's experience payout is zeroed — there are no enchantments being stripped here, and
     * a salvage must not also be an XP source.
     */
    @GameTest(template = EMPTY)
    public static void aGrindstoneSalvageProducesAnOutputAndNoExperience(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "grindstone_salvage");
        enablePerk(player, RegistryPerks.RESOURCE_EFFICIENCY);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean previousEnabled = config.recyclingEnabled;
        int previousPercent = config.resourceEfficiencyPercent;
        RecyclingIndex previousIndex = RecyclingIndex.get();
        Player restore = ContainerInteraction.begin(player);
        try {
            config.recyclingEnabled = true;
            config.resourceEfficiencyPercent = 100;
            RecyclingIndex.install(RecyclingIndex.build(List.of(craftingTableRule())));

            GrindstoneEvent.OnPlaceItem event = new GrindstoneEvent.OnPlaceItem(
                    new ItemStack(Items.CRAFTING_TABLE), ItemStack.EMPTY, 7);
            new WorkshopPerkHandler().onGrindstoneChange(event);

            if (event.getOutput().isEmpty() || event.getOutput().getItem() != Items.OAK_PLANKS) {
                throw new GameTestAssertException("a 100% Resource Efficiency salvage of a crafting"
                        + " table produced " + event.getOutput() + "; expected oak planks");
            }
            if (event.getXp() != 0) {
                throw new GameTestAssertException("a salvage paid " + event.getXp()
                        + " experience; a grindstone's XP is the enchantments it strips, and there"
                        + " are none here");
            }
        } finally {
            ContainerInteraction.end(restore);
            RecyclingIndex.install(previousIndex);
            config.recyclingEnabled = previousEnabled;
            config.resourceEfficiencyPercent = previousPercent;
        }
        helper.succeed();
    }

    /** An item with no rule is left entirely to vanilla, output and experience alike. */
    @GameTest(template = EMPTY)
    public static void aGrindstoneLeavesAnUnknownItemAlone(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "grindstone_unknown");
        enablePerk(player, RegistryPerks.RESOURCE_EFFICIENCY);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previousPercent = config.resourceEfficiencyPercent;
        RecyclingIndex previousIndex = RecyclingIndex.get();
        Player restore = ContainerInteraction.begin(player);
        try {
            config.resourceEfficiencyPercent = 100;
            RecyclingIndex.install(RecyclingIndex.build(List.of(craftingTableRule())));

            GrindstoneEvent.OnPlaceItem event = new GrindstoneEvent.OnPlaceItem(
                    new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY, 7);
            new WorkshopPerkHandler().onGrindstoneChange(event);

            if (!event.getOutput().isEmpty() || event.getXp() != 7) {
                throw new GameTestAssertException("an item with no salvage rule was changed at the"
                        + " grindstone: output " + event.getOutput() + ", xp " + event.getXp());
            }
        } finally {
            ContainerInteraction.end(restore);
            RecyclingIndex.install(previousIndex);
            config.resourceEfficiencyPercent = previousPercent;
        }
        helper.succeed();
    }

    private static void enablePerk(ServerPlayer player, RegistryObject<Perk> registered) {
        Perk perk = registered.get();
        SkillCapability capability = player.getCapability(RegistryCapabilities.SKILL).orElseThrow(
                () -> new GameTestAssertException("player has no Runic Skills capability"));
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

    private static RecyclingRule craftingTableRule() {
        return new RecyclingRule(new ResourceLocation(RunicSkills.MOD_ID, "crafting_table"),
                Items.CRAFTING_TABLE, 1,
                List.of(new RecyclingRule.Output(Items.OAK_PLANKS, 2)), 2, true);
    }

    private static RecyclingRule twoTableRule() {
        return new RecyclingRule(new ResourceLocation(RunicSkills.MOD_ID, "two_tables"),
                Items.CRAFTING_TABLE, 2,
                List.of(new RecyclingRule.Output(Items.OAK_PLANKS, 4)), 4, true);
    }
}
