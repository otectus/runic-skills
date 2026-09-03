package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.events.EnchantingLorePerkHandler;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Master Artificer enchants what a player crafts, survives a repeat craft, and never fires for a
 * machine (RS-205-07).
 *
 * <p>The perk walked the whole enchantment registry on every proc, calling {@code canEnchant} —
 * third-party code — with no isolation, and gated only on "not the client", so any block that
 * crafts through a {@code FakePlayer} rolled it too. The isolation itself cannot be exercised from
 * here: an enchantment that throws would have to be in {@code ForgeRegistries.ENCHANTMENTS}, and
 * this source set cannot register one — registry events have long since fired by the time the
 * GameTest scanner loads these classes. What is testable on a real server is that the perk pays
 * out, that the per-item cache does not corrupt the second craft, and that a FakePlayer is turned
 * away; see {@code MasterResearcherGameTest} for the isolation itself, which is testable because
 * the recipe index accepts a recipe collection.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class MasterArtificerGameTest {

    /** Our own empty template; see {@link PlayerLifecycleGameTest} for why it is not forge's. */
    private static final String EMPTY = "empty";

    /** A profile no real player owns, for the FakePlayer half of the test. */
    private static final GameProfile MACHINE =
            new GameProfile(UUID.nameUUIDFromBytes("runicskills-gametest:machine".getBytes()), "machine");

    private static ServerPlayer newPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("runicskills-gametest:" + name).getBytes()), name);
        return new ServerPlayer(level.getServer(), level, profile);
    }

    /**
     * Two crafts at 100 %: the first builds the candidate cache, the second reads it. Both must
     * come out enchanted — a cache that keyed on the wrong thing would show up as a second craft
     * that is enchanted with something the item cannot hold, or not enchanted at all.
     */
    @GameTest(template = EMPTY)
    public static void craftingEnchantsAndRepeatsCorrectlyFromTheCache(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "master_artificer");
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int artificer = config.masterArtificerPercent;
        try {
            config.masterArtificerPercent = 100;
            enable(player, RegistryPerks.MASTER_ARTIFICER.get());
            EnchantingLorePerkHandler.clearCache();

            EnchantingLorePerkHandler handler = new EnchantingLorePerkHandler();
            for (int craft = 1; craft <= 2; craft++) {
                ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
                handler.onItemCrafted(new PlayerEvent.ItemCraftedEvent(
                        player, pickaxe, new SimpleContainer(1)));
                if (!pickaxe.isEnchanted()) {
                    throw new GameTestAssertException("Master Artificer at 100% left craft " + craft
                            + " unenchanted");
                }
            }
        } finally {
            config.masterArtificerPercent = artificer;
            EnchantingLorePerkHandler.clearCache();
        }
        helper.succeed();
    }

    /**
     * A machine crafting through a FakePlayer earns nothing. A FakePlayer <em>is</em> a
     * ServerPlayer, so this is a decision the handler has to make explicitly rather than one the
     * side check makes for it.
     */
    @GameTest(template = EMPTY)
    public static void aFakePlayerDoesNotProc(GameTestHelper helper) {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int artificer = config.masterArtificerPercent;
        try {
            config.masterArtificerPercent = 100;
            ServerPlayer machine = FakePlayerFactory.get(helper.getLevel(), MACHINE);
            ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
            new EnchantingLorePerkHandler().onItemCrafted(new PlayerEvent.ItemCraftedEvent(
                    machine, pickaxe, new SimpleContainer(1)));
            if (pickaxe.isEnchanted()) {
                throw new GameTestAssertException("a FakePlayer proc'd Master Artificer; automation"
                        + " must not earn a player's perk");
            }
        } finally {
            config.masterArtificerPercent = artificer;
        }
        helper.succeed();
    }

    /** Grants the perk by hand: the skill level it gates on, and rank 1. */
    private static void enable(ServerPlayer player, Perk perk) {
        SkillCapability capability = player.getCapability(RegistryCapabilities.SKILL).orElseThrow(
                () -> new GameTestAssertException("the test player has no skill capability"));
        capability.setSkillLevel(perk.getSkill(), Math.max(1, perk.getLvl()));
        capability.setPerkRank(perk, 1);
        if (!perk.isEnabled(player)) {
            throw new GameTestAssertException("could not enable " + perk.getName() + " on the test"
                    + " player; the perk gate has changed shape");
        }
    }
}
