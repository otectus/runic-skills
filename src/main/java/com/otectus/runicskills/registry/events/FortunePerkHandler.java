package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.util.GameTimeWindow;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fortune-tree perks that pay out on an action rather than through the Luck attribute, all
 * previously registered with config, tooltips and textures and no runtime effect at all
 * (RS10-004).
 *
 * <p>The Luck-driven half of the tree — Treasure Sense, Scavenger, Rare Find and the rest — already
 * rides vanilla's {@code LUCK} attribute in {@code PerkEffectsHandler}. These two do not fit there:
 * one is about what a crafting grid gives back, and the other is about doing anything at all.
 */
public class FortunePerkHandler {

    /**
     * Game time at which each player's Chaos Roll last fired.
     *
     * <p>{@code level.getGameTime()}, not {@code Player.tickCount}: tickCount restarts at zero on
     * respawn and on every dimension change, so a cooldown stamped in it came back ready the
     * moment the player died or stepped through a portal.
     */
    private static final Map<UUID, Long> CHAOS_READY_AT = new ConcurrentHashMap<>();

    /**
     * How long Chaos Roll waits between blessings.
     *
     * <p>"Any action" includes breaking a block, which a player does hundreds of times a minute.
     * Without a floor between procs even a small chance would mean a permanent, flickering stack of
     * effects rather than an occasional stroke of luck — the perk would stop reading as chance at
     * all.
     */
    private static final int CHAOS_COOLDOWN_TICKS = 400;

    /** How long a Chaos Roll blessing lasts. Short: it is a moment of luck, not a buff to plan around. */
    private static final int CHAOS_DURATION_TICKS = 300;

    /** The blessings Chaos Roll can land on, all beneficial and all harmless to receive unasked. */
    private static final MobEffect[] BLESSINGS = {
            MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED, MobEffects.DAMAGE_BOOST,
            MobEffects.REGENERATION, MobEffects.DAMAGE_RESISTANCE, MobEffects.LUCK,
            MobEffects.NIGHT_VISION, MobEffects.JUMP,
    };

    /**
     * Frees one player's Chaos Roll cooldown.
     *
     * <p>Deliberately not called on death: a cooldown that death reset would make dying the
     * cheapest way to re-roll a blessing.
     */
    public static void clearPlayer(UUID id) {
        if (id != null) CHAOS_READY_AT.remove(id);
    }

    /** Drops every player's cooldown, so a single-player world does not leak into the next one. */
    public static void clearAll() {
        CHAOS_READY_AT.clear();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayer(event.getEntity().getUUID());
    }

    // ── Jeweler's Eye ───────────────────────────────────────────────────────────────────────

    /**
     * Jeweler's Eye — "Jewelry crafting has a chance for a bonus gem".
     *
     * <p>Vanilla has no jewellery and no way to recognise a mod's, so the perk keys off the part of
     * the description the game can answer: the gem. A craft that put a gem into the grid may hand
     * one back, which is what an eye for stones is worth at a bench.
     *
     * <p>The grid is read from the event rather than by looking the recipe up, because the crafting
     * matrix at this point still holds the exact stacks that were spent — no recipe scan, and no
     * guessing which of several recipes produced the result.
     */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player instanceof FakePlayer) return;
        if (!enabled(RegistryPerks.JEWELERS_EYE, player)) return;

        double chance = HandlerCommonConfig.HANDLER.instance().jewelersEyePercent / 100.0;
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) return;

        ItemStack gem = firstGemIn(event.getInventory());
        if (gem.isEmpty()) return;

        ItemStack bonus = gem.copy();
        bonus.setCount(1);
        if (!player.getInventory().add(bonus)) player.drop(bonus, false);
    }

    /**
     * The first gem in a crafting grid, or empty.
     *
     * <p>Matched on the {@code forge:gems} tag rather than an item list, so a pack's own gems count
     * without a code change — the same reasoning the ore and food perks use for their tags.
     */
    private static ItemStack firstGemIn(Container grid) {
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack stack = grid.getItem(slot);
            if (!stack.isEmpty() && stack.is(Tags.Items.GEMS)) return stack;
        }
        return ItemStack.EMPTY;
    }

    // ── Chaos Roll ──────────────────────────────────────────────────────────────────────────

    /**
     * Chaos Roll — "Any action has a chance for a bonus effect".
     *
     * <p>Taken at its word: the three things a player spends a session doing — breaking blocks,
     * crafting, and killing — each roll for a brief blessing. Which blessing is itself the roll, so
     * the perk never grants the same advantage twice running and cannot be aimed at anything.
     */
    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        rollForBlessing(event.getPlayer());
    }

    @SubscribeEvent
    public void onCraftedSomething(PlayerEvent.ItemCraftedEvent event) {
        rollForBlessing(event.getEntity());
    }

    @SubscribeEvent
    public void onKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof Player killer) rollForBlessing(killer);
    }

    private static void rollForBlessing(Player player) {
        if (player == null || player.level().isClientSide() || player instanceof FakePlayer) return;
        if (!enabled(RegistryPerks.CHAOS_ROLL, player)) return;

        long now = player.level().getGameTime();
        if (!GameTimeWindow.ready(now, CHAOS_READY_AT.get(player.getUUID()), CHAOS_COOLDOWN_TICKS)) return;

        double chance = HandlerCommonConfig.HANDLER.instance().chaosRollPercent / 100.0;
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) return;

        CHAOS_READY_AT.put(player.getUUID(), now);
        MobEffect blessing = BLESSINGS[player.getRandom().nextInt(BLESSINGS.length)];
        player.addEffect(new MobEffectInstance(blessing, CHAOS_DURATION_TICKS, 0, true, true));
    }

    private static boolean enabled(RegistryObject<Perk> perk, Player player) {
        return perk != null && perk.get() != null && perk.get().isEnabled(player);
    }
}
