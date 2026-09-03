package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.events.PerkEffectsHandler;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

/**
 * The XP Bonus passive actually pays out (HIGH-04).
 *
 * <p>Through 2.0.3 the {@code xp_bonus} attribute was registered, ranked, synced and shown in the
 * UI while no code ever read it, so this passive was worth nothing at any rank. The failure mode
 * is invisible to a source-scanning test — the attribute has plenty of references — so it needs a
 * running server and a real XP award.
 *
 * <p>The award shape is the one that matters: a hundred single-point pickups, not one large grant.
 * Any implementation that applies the bonus per award and truncates pays exactly zero here, which
 * is what a player mining or fighting actually experiences.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class XpBonusGameTest {

    /** Our own empty template; see {@link PlayerLifecycleGameTest} for why it is not forge's. */
    private static final String EMPTY = "empty";

    /** A modifier id that belongs to no passive, so this test cannot collide with a real one. */
    private static final UUID TEST_MODIFIER = UUID.fromString("2e4f7a10-9c2b-4b57-8f21-0d5a6c3e1b44");

    private static ServerPlayer newPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("runicskills-gametest:" + name).getBytes()), name);
        return new ServerPlayer(level.getServer(), level, profile);
    }

    @GameTest(template = EMPTY)
    public static void xpBonusPaysOutOnSmallAwards(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "xp_bonus");
        // No carry left over from an earlier run of this test in the same server process.
        PerkEffectsHandler.clearPlayer(player.getUUID());

        AttributeInstance attribute = player.getAttribute(RegistryAttributes.XP_BONUS.get());
        if (attribute == null) {
            throw new GameTestAssertException("player has no xp_bonus attribute instance");
        }
        attribute.addPermanentModifier(new AttributeModifier(
                TEST_MODIFIER, "runicskills:gametest_xp_bonus", 0.25D,
                AttributeModifier.Operation.ADDITION));

        for (int i = 0; i < 100; i++) {
            player.giveExperiencePoints(1);
        }

        if (player.totalExperience != 125) {
            throw new GameTestAssertException(
                    "100 x 1 XP at +25% should total 125, got " + player.totalExperience);
        }
        PerkEffectsHandler.clearPlayer(player.getUUID());
        helper.succeed();
    }

    /** Without the attribute the award must be untouched — no rounding drift for an unranked player. */
    @GameTest(template = EMPTY)
    public static void noBonusLeavesAwardsExact(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "xp_bonus_none");
        PerkEffectsHandler.clearPlayer(player.getUUID());

        for (int i = 0; i < 100; i++) {
            player.giveExperiencePoints(1);
        }

        if (player.totalExperience != 100) {
            throw new GameTestAssertException(
                    "100 x 1 XP with no bonus should total 100, got " + player.totalExperience);
        }
        helper.succeed();
    }
}
