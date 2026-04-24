package com.otectus.runicskills.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaItemHandler;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;

/**
 * Class-load-isolated Botania API wrapper. Every {@code vazkii.botania.*} import in this
 * mod lives here; other classes must only touch this file through
 * {@link BotaniaIntegration#isModLoaded()}-guarded code paths so that
 * {@code NoClassDefFoundError} cannot fire when Botania is absent.
 */
public final class BotaniaCompat {

    private BotaniaCompat() {}

    /** Default scan radius for nearby-pool operations. Matches the plan's 12-block cap. */
    public static final int POOL_SCAN_RADIUS = 6;
    public static final int POOL_SCAN_HEIGHT = 3;

    /**
     * Drain up to {@code want} mana from the nearest Mana Pool in a 12x6x12 box around
     * {@code center}. Returns the amount actually drained, or 0 if no pool was found
     * or every nearby pool was empty.
     */
    public static int drainNearbyPool(Level level, BlockPos center, int want) {
        if (level == null || center == null || want <= 0) return 0;
        BlockPos min = center.offset(-POOL_SCAN_RADIUS, -POOL_SCAN_HEIGHT, -POOL_SCAN_RADIUS);
        BlockPos max = center.offset(POOL_SCAN_RADIUS, POOL_SCAN_HEIGHT, POOL_SCAN_RADIUS);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) continue;
            ManaReceiver cap = be.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER).orElse(null);
            if (cap instanceof ManaPool pool && pool.getCurrentMana() > 0) {
                int take = Math.min(want, pool.getCurrentMana());
                pool.receiveMana(-take);
                return take;
            }
        }
        return 0;
    }

    /** True iff any Mana Pool inside the scan box has at least one mana unit. */
    public static boolean hasNearbyPoolMana(Level level, BlockPos center) {
        if (level == null || center == null) return false;
        BlockPos min = center.offset(-POOL_SCAN_RADIUS, -POOL_SCAN_HEIGHT, -POOL_SCAN_RADIUS);
        BlockPos max = center.offset(POOL_SCAN_RADIUS, POOL_SCAN_HEIGHT, POOL_SCAN_RADIUS);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) continue;
            ManaReceiver cap = be.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER).orElse(null);
            if (cap instanceof ManaPool pool && pool.getCurrentMana() > 0) return true;
        }
        return false;
    }

    /**
     * Drain {@code amount} mana from the player's inventory ManaItems (Tablets, Bands,
     * Mana Mirrors, etc.). Returns true only if the full amount was available.
     */
    public static boolean drainPlayerMana(Player player, int amount) {
        if (player == null || amount <= 0) return false;
        ItemStack held = player.getMainHandItem();
        return ManaItemHandler.instance().requestManaExact(held, player, amount, true);
    }

    /**
     * Trickle charge: deposit mana into the first ManaItem in the player's inventory that
     * can accept it. Returns the amount actually deposited.
     */
    public static int chargePlayerMana(Player player, int amount) {
        if (player == null || amount <= 0) return 0;
        ItemStack held = player.getMainHandItem();
        return ManaItemHandler.instance().dispatchMana(held, player, amount, true);
    }

    /** Total mana currently stored across every ManaItem-capable stack the player carries. */
    public static int getPlayerManaTotal(Player player) {
        if (player == null) return 0;
        // ManaItemHandler.requestMana with remove=false returns how much is available.
        return ManaItemHandler.instance().requestMana(player.getMainHandItem(), player,
                Integer.MAX_VALUE, false);
    }
}
