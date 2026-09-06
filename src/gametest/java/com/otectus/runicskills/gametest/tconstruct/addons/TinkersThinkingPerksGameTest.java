package com.otectus.runicskills.gametest.tconstruct.addons;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.gametest.tconstruct.TinkerFixtures;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.addons.TcAddonHooks;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * §18.3 C08 for the three Tinkers' Thinking perks, against the jar the reference pack runs.
 *
 * <p>Every assertion goes through the public {@code TcAddonHooks} sums rather than through the
 * adapter's own methods, and that is the point rather than a convenience: the sums are what the
 * perk handler composes and caps, so a perk that contributed correctly but never reached the sum
 * would still fail here. The adapter's per-perk methods stay private, as they are on every other
 * adapter in this package.
 *
 * <p><b>Effects, not TinkerData.</b> The add-on's triggers are keyed on
 * {@code TinkerDataCapability.TinkerDataKey}s, which have identity semantics and no public
 * enumeration — a key rebuilt from its id cannot match the one the add-on stored, so no test could
 * set one up either. What the add-on leaves is {@code tinkers_thinking:last_effort} and
 * {@code tinkers_thinking:sculk_power}, and applying those is exactly the state a real save and a
 * real conversion produce.
 *
 * <p>No {@code @GameTestHolder}: registered from {@code TConstructGameTests} only when
 * {@code tinkers_thinking} is loaded.
 */
@PrefixGameTestTemplate(false)
public class TinkersThinkingPerksGameTest {

    private static final String EMPTY = "empty";

    private static final ResourceLocation LAST_EFFORT =
            new ResourceLocation("tinkers_thinking", "last_effort");
    private static final ResourceLocation SCULK_POWER =
            new ResourceLocation("tinkers_thinking", "sculk_power");

    /** Last Thought arms on a refused death and pays into the wear channel, not before. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void lastThoughtReducesDeathWear(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.onlinePlayer(helper, "tc_last_thought");
        try {
            ItemStack tool = TinkerFixtures.pickaxeOfTier(1);
            TinkerFixtures.enablePerk(player, RegistryPerks.TC_THINKING_LAST_THOUGHT);

            // Nothing has happened yet, so nothing is owed.
            double idle = TcAddonHooks.wearAvoidanceBonus(player, tool);
            if (idle != 0.0) {
                throw new GameTestAssertException("wear avoidance was " + idle + " with no save");
            }

            // A death that was NOT refused must not arm it, even with the effect present: the two
            // together are the add-on's save, and either alone is something else.
            applyEffect(player, LAST_EFFORT);
            TcAddonHooks.endDeathResolution(player, false);
            double afterRealDeath = TcAddonHooks.wearAvoidanceBonus(player, tool);
            if (afterRealDeath != 0.0) {
                throw new GameTestAssertException("an unrefused death armed Last Thought: "
                        + afterRealDeath);
            }

            TcAddonHooks.endDeathResolution(player, true);
            double expected = HandlerCommonConfig.HANDLER.instance().tcThinkingLastThoughtPercent / 100.0;
            double armed = TcAddonHooks.wearAvoidanceBonus(player, tool);
            if (Math.abs(armed - expected) > 1.0E-6) {
                throw new GameTestAssertException("wear avoidance was " + armed + ", expected " + expected);
            }

            TinkerFixtures.disablePerk(player, RegistryPerks.TC_THINKING_LAST_THOUGHT);
            double withoutPerk = TcAddonHooks.wearAvoidanceBonus(player, tool);
            if (withoutPerk != 0.0) {
                throw new GameTestAssertException("a non-holder was paid " + withoutPerk);
            }
        } finally {
            TinkerFixtures.logOut(player);
        }
        helper.succeed();
    }

    /** Studied Recall returns a share only while the add-on's own precondition holds. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void studiedRecallAddsXpShare(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.onlinePlayer(helper, "tc_studied_recall");
        try {
            TinkerFixtures.enablePerk(player, RegistryPerks.TC_THINKING_STUDIED_RECALL);

            // sculk_power is what OnExpPickUp itself checks before it cancels, so without it there
            // is no conversion to recover from and the perk must be silent.
            MobEffect sculkPower = effect(SCULK_POWER);
            player.removeEffect(sculkPower);
            double quiet = TcAddonHooks.experienceBonus(player);
            if (quiet != 0.0) {
                throw new GameTestAssertException("a share of " + quiet + " was offered with no "
                        + "sculk power on the player");
            }

            applyEffect(player, SCULK_POWER);
            double expected = HandlerCommonConfig.HANDLER.instance().tcThinkingStudiedRecallPercent / 100.0;
            double share = TcAddonHooks.experienceBonus(player);
            if (Math.abs(share - expected) > 1.0E-6) {
                throw new GameTestAssertException("share was " + share + ", expected " + expected);
            }
            // The share is a share: it can never return more than the orb another mod destroyed.
            if (share > 1.0) {
                throw new GameTestAssertException("a share above 1.0 would mint experience: " + share);
            }

            TinkerFixtures.disablePerk(player, RegistryPerks.TC_THINKING_STUDIED_RECALL);
            double withoutPerk = TcAddonHooks.experienceBonus(player);
            if (withoutPerk != 0.0) {
                throw new GameTestAssertException("a non-holder was offered " + withoutPerk);
            }
        } finally {
            TinkerFixtures.logOut(player);
        }
        helper.succeed();
    }

    /** Embellished Focus reads the modifier off the weapon, and a plain tool earns nothing. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void embellishedFocusAddsMeleeShare(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.onlinePlayer(helper, "tc_embellished_focus");
        try {
            TinkerFixtures.enablePerk(player, RegistryPerks.TC_THINKING_EMBELLISHED_FOCUS);

            ItemStack plain = TinkerFixtures.pickaxeOfTier(1);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, plain);
            double bare = TcAddonHooks.meleeDamageBonus(player);
            if (bare != 0.0) {
                throw new GameTestAssertException("a tool with no Thinking modifier paid " + bare);
            }

            // tinkers_thinking:attack_advanced, read out of the shipped jar's modifier folder: a
            // tconstruct:conditional_melee_damage module, and one of the ids the feature key lists.
            ItemStack embellished = TinkerFixtures.pickaxeOfTier(1);
            ToolStack tool = ToolStack.from(embellished);
            tool.addModifier(new ModifierId("tinkers_thinking", "attack_advanced"), 1);
            tool.rebuildStats();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, embellished);

            double expected =
                    HandlerCommonConfig.HANDLER.instance().tcThinkingEmbellishedFocusPercent / 100.0;
            double paid = TcAddonHooks.meleeDamageBonus(player);
            if (Math.abs(paid - expected) > 1.0E-6) {
                throw new GameTestAssertException("melee share was " + paid + ", expected " + expected);
            }

            TinkerFixtures.disablePerk(player, RegistryPerks.TC_THINKING_EMBELLISHED_FOCUS);
            double withoutPerk = TcAddonHooks.meleeDamageBonus(player);
            if (withoutPerk != 0.0) {
                throw new GameTestAssertException("a non-holder was paid " + withoutPerk);
            }
        } finally {
            TinkerFixtures.logOut(player);
        }
        helper.succeed();
    }

    private static MobEffect effect(ResourceLocation id) {
        MobEffect found = ForgeRegistries.MOB_EFFECTS.getValue(id);
        if (found == null) {
            throw new GameTestAssertException("tinkers_thinking is loaded but " + id
                    + " is not registered; the perks read that effect by id");
        }
        return found;
    }

    private static void applyEffect(ServerPlayer player, ResourceLocation id) {
        player.addEffect(new MobEffectInstance(effect(id), 600, 0));
    }
}
