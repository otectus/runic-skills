package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * E11: Treasure Hunter pays for a break that happened, not for one that was attempted (RS207-09).
 *
 * <p>It used to listen at {@code HIGHEST} priority to {@code BlockEvent.BreakEvent} — which is
 * fired before the break and is cancellable — and then enqueue its drop for the next tick. So a
 * break refused by a protection mod, a land claim or any lower-priority listener still paid out,
 * and the block was still there afterwards. Standing in a protected region and swinging at dirt was
 * a free item source.
 *
 * <p>The test is that exact scenario: a listener at {@code LOWEST} cancels every break, and nothing
 * may be dropped. The positive case is asserted alongside it, because a perk that had simply been
 * disabled would also pass the first half.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class TreasureHunterCommitGameTest {

    private static final String EMPTY = "empty";

    /** Cancelling the break must cancel the payout with it. */
    @GameTest(template = EMPTY)
    public static void acancelledBreakPaysNothing(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "treasure_hunter_cancelled");
        enablePerk(player, RegistryPerks.TREASURE_HUNTER);

        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());

        Object veto = new Object() {
            @SubscribeEvent(priority = EventPriority.LOWEST)
            public void onBreak(BlockEvent.BreakEvent event) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.register(veto);
        try {
            player.gameMode.destroyBlock(pos);
            List<ItemEntity> dropped = itemsAround(helper.getLevel(), pos);
            if (!dropped.isEmpty()) {
                throw new GameTestAssertException("a cancelled break dropped " + dropped.size()
                        + " item(s); a reward must never be paid for a break that did not happen");
            }
            if (helper.getLevel().getBlockState(pos).isAir()) {
                throw new GameTestAssertException("the cancelled break removed the block anyway;"
                        + " the fixture, not the perk, is broken");
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(veto);
            helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            for (ItemEntity item : itemsAround(helper.getLevel(), pos)) item.discard();
        }
        helper.succeed();
    }

    /**
     * An allowed break pays at most once.
     *
     * <p>"At most" rather than "exactly": the perk rolls, so a single break may legitimately pay
     * nothing. What must not happen is the block's own drop being counted as a payout as well, or
     * one break producing two.
     */
    @GameTest(template = EMPTY)
    public static void anAllowedBreakPaysAtMostOnce(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "treasure_hunter_allowed");
        enablePerk(player, RegistryPerks.TREASURE_HUNTER);

        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        for (ItemEntity item : itemsAround(helper.getLevel(), pos)) item.discard();

        try {
            player.gameMode.destroyBlock(pos);
            // The dirt block itself drops, so one item is the floor and two the ceiling.
            List<ItemEntity> dropped = itemsAround(helper.getLevel(), pos);
            if (dropped.size() > 2) {
                throw new GameTestAssertException("one break produced " + dropped.size()
                        + " item entities; the block's own drop plus at most one perk drop is two");
            }
        } finally {
            helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            for (ItemEntity item : itemsAround(helper.getLevel(), pos)) item.discard();
        }
        helper.succeed();
    }

    private static List<ItemEntity> itemsAround(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0));
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
}
