package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.util.ContainerInteraction;
import com.otectus.runicskills.common.util.GameTimeWindow;
import com.otectus.runicskills.common.util.LogOnce;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enchanting and lore perks from the Wisdom and Tinkering trees, all previously registered with
 * config, tooltips and textures but no runtime effect whatsoever (RS10-004).
 */
public class EnchantingLorePerkHandler {

    /**
     * Game time of each player's most recent damage, for Temporal Wisdom's "in combat" window.
     *
     * <p>{@code level.getGameTime()}, not {@code Player.tickCount}: tickCount restarts at zero on
     * respawn and on every dimension change, so the window read as wide open for the rest of the
     * session after a portal, and as never-opened after a death.
     */
    private static final Map<UUID, Long> LAST_COMBAT_TICK = new ConcurrentHashMap<>();

    /**
     * Master Artificer's enchantable-candidate list per item type; see {@code candidatesFor}.
     * Bounded, and cleared on server stop, because the enchantment registry itself is per-server.
     */
    private static final Map<Item, List<Enchantment>> ARTIFICER_CANDIDATES = new ConcurrentHashMap<>();

    /** Distinct item types the Artificer cache holds before it is thrown away and rebuilt. */
    private static final int MAX_CACHED_ARTIFICER_ITEMS = 512;

    /**
     * What each dying player was holding, captured before the inventory is emptied.
     *
     * <p>Soul Binding acts on "the item in your hand", and by the time the drop list exists the
     * hand — and the whole inventory — is empty. Death and its drops happen in the same tick on the
     * same thread, so the entry is written and consumed immediately; the logout sweep below only
     * covers the case where nothing consumed it.
     */
    private static final Map<UUID, ItemStack> HELD_AT_DEATH = new ConcurrentHashMap<>();

    /**
     * Clears what a death should end.
     *
     * <p>{@link #HELD_AT_DEATH} is consumed by {@code LivingDropsEvent} on the death tick itself,
     * long before the respawn clone, so clearing it here takes nothing away from Soul Binding — it
     * only sweeps the entry left behind when nothing dropped at all (keepInventory).
     */
    public static void clearCombatWindows(UUID id) {
        if (id == null) return;
        LAST_COMBAT_TICK.remove(id);
        HELD_AT_DEATH.remove(id);
    }

    /** Frees one player's state. Called on logout. */
    public static void clearPlayer(UUID id) {
        clearCombatWindows(id);
    }

    /** Drops every player's state, so a single-player world does not leak into the next one. */
    public static void clearAll() {
        LAST_COMBAT_TICK.clear();
        HELD_AT_DEATH.clear();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayer(event.getEntity().getUUID());
    }

    // ── Keeping things through death ────────────────────────────────────────────────────────

    /** Remembers the dying player's main-hand item, which Soul Binding is about to rescue. */
    @SubscribeEvent
    public void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        if (RegistryPerks.SOUL_BINDING == null || !RegistryPerks.SOUL_BINDING.get().isEnabled(player)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) HELD_AT_DEATH.put(player.getUUID(), held.copy());
    }

    /**
     * Soul Binding and Enchantment Preservation — what death does not get to take.
     *
     * <p>Both perks promised that something survives dying, and neither said where it goes. The
     * honest answer in vanilla is the ender chest: it is the one container that belongs to the
     * player rather than to a place, it already survives death, and it is saved with the player's
     * own data — so a rescued item is on disk the moment it is rescued rather than held in the
     * server's memory across a respawn that may never come.
     *
     * <p>Soul Binding rescues the item that was in your hand, every time. Enchantment Preservation
     * rolls separately for each enchanted item, which is what makes it a chance rather than a
     * guarantee, and only for enchanted ones — the perk is about enchantments, not about gear.
     *
     * <p>An item the ender chest has no room for simply drops as it would have. That is the honest
     * failure: the perk cannot conjure storage, and silently deleting the item would be far worse
     * than dropping it where the player died.
     *
     * <p>{@code LivingDropsEvent} rather than a player-specific one because 1.20.1 Forge has none:
     * a player's inventory is emptied inside {@code dropAllDeathLoot} while drops are captured, so
     * every item a player loses on death arrives here.
     */
    @SubscribeEvent
    public void onPlayerDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        ItemStack bound = HELD_AT_DEATH.remove(player.getUUID());

        boolean preserving = RegistryPerks.ENCHANTMENT_PRESERVATION != null
                && RegistryPerks.ENCHANTMENT_PRESERVATION.get().isEnabled(player);
        if (bound == null && !preserving) return;

        double chance = HandlerCommonConfig.HANDLER.instance().enchantmentPreservationPercent / 100.0;
        net.minecraft.world.SimpleContainer ender = player.getEnderChestInventory();

        java.util.Iterator<net.minecraft.world.entity.item.ItemEntity> drops = event.getDrops().iterator();
        boolean boundRescued = false;
        while (drops.hasNext()) {
            net.minecraft.world.entity.item.ItemEntity drop = drops.next();
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) continue;

            boolean rescue = false;
            if (!boundRescued && bound != null && ItemStack.matches(bound, stack)) {
                rescue = true;
                boundRescued = true;   // "the item in your hand" is one item, not every copy of it
            } else if (preserving && stack.isEnchanted() && chance > 0
                    && player.getRandom().nextDouble() < chance) {
                rescue = true;
            }
            if (!rescue) continue;

            ItemStack leftover = ender.addItem(stack);
            if (leftover.isEmpty()) drops.remove();
            else drop.setItem(leftover);
        }
    }

    // ── Power Tools ─────────────────────────────────────────────────────────────────────────

    /**
     * Power Tools — "Tool efficiency enchantment level effectively increased".
     *
     * <p>Reproduces vanilla's own Efficiency curve rather than adding a flat multiplier: the speed
     * an Efficiency level grants is {@code level² + 1}, so the perk's bonus is the difference
     * between the curve at the tool's real level and at its boosted one. That way "+2 levels" means
     * the same thing here as it does on the enchantment, instead of being a percentage in disguise.
     */
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (RegistryPerks.POWER_TOOLS == null || !RegistryPerks.POWER_TOOLS.get().isEnabled(player)) {
            return;
        }
        int extra = (int) HandlerCommonConfig.HANDLER.instance().powerToolsAmplifier;
        if (extra <= 0) return;

        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) return;
        int current = EnchantmentHelper.getBlockEfficiency(player);
        // Efficiency only applies when the tool is actually effective on the block; matching that
        // keeps the perk from making a bare hand dig stone.
        if (!tool.isCorrectToolForDrops(event.getState())) return;

        float atCurrent = current > 0 ? current * current + 1 : 0;
        int boosted = current + extra;
        float atBoosted = boosted * boosted + 1;
        event.setNewSpeed(event.getNewSpeed() + (atBoosted - atCurrent));
    }

    // ── Master Artificer ────────────────────────────────────────────────────────────────────

    /**
     * Master Artificer — "Crafted items have a chance for a bonus enchantment".
     *
     * <p>Only items that can hold an enchantment and are not already enchanted, so the perk cannot
     * overwrite the work of an enchanting table or stack invisibly onto a result the player already
     * curated.
     */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        // ServerPlayer, not "not client", so the logical client never rolls this. FakePlayer is a
        // ServerPlayer, so it needs saying separately: an autocrafter or machine block crafting
        // through one must not roll a real player's perk. (It would fail the capability check
        // below anyway, but that is an accident of how capabilities attach, not a decision.)
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (RegistryPerks.MASTER_ARTIFICER == null
                || !RegistryPerks.MASTER_ARTIFICER.get().isEnabled(player)) {
            return;
        }
        double chance = HandlerCommonConfig.HANDLER.instance().masterArtificerPercent / 100.0;
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) return;

        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty() || crafted.isEnchanted() || !crafted.isEnchantable()) return;

        List<Enchantment> candidates = candidatesFor(crafted);
        if (candidates.isEmpty()) return;
        crafted.enchant(candidates.get(player.getRandom().nextInt(candidates.size())), 1);
    }

    /**
     * Which enchantments could go on {@code crafted}, cached per item type.
     *
     * <p>The walk is the whole enchantment registry — several hundred entries in a modded pack,
     * each one a {@code canEnchant} call into third-party code — and it was being repeated on
     * every proc. The answer depends only on the item type in every implementation vanilla and
     * Forge ship (they test {@code stack.getItem()} against an {@code EnchantmentCategory}), so
     * caching by {@link Item} is sound and turns a per-craft registry walk into a map lookup.
     *
     * <p>A {@code canEnchant} that throws costs its own enchantment and nothing else: before this,
     * one broken enchantment destroyed the craft of any player who had the perk.
     */
    private static List<Enchantment> candidatesFor(ItemStack crafted) {
        List<Enchantment> cached = ARTIFICER_CANDIDATES.get(crafted.getItem());
        if (cached != null) return cached;

        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment candidate : net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS) {
            if (candidate.isCurse() || candidate.isTreasureOnly()) continue;
            try {
                if (candidate.canEnchant(crafted)) candidates.add(candidate);
            } catch (RuntimeException e) {
                var id = net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getKey(candidate);
                LogOnce.warnOnce("artificer:" + id,
                        "[Runic Skills] Master Artificer skipped enchantment {} because canEnchant threw"
                        + " {}. The craft was allowed to continue.",
                        id, e.getClass().getSimpleName());
            }
        }

        // Cleared rather than evicted: this is a pure function of the item, so throwing the whole
        // map away costs one rebuild per item still in use, and there is no sensible victim to
        // pick when a pack somehow presents more than 512 enchantable item types.
        if (ARTIFICER_CANDIDATES.size() >= MAX_CACHED_ARTIFICER_ITEMS) ARTIFICER_CANDIDATES.clear();
        List<Enchantment> immutable = List.copyOf(candidates);
        ARTIFICER_CANDIDATES.put(crafted.getItem(), immutable);
        return immutable;
    }

    /** Drops the Master Artificer cache, so one world's registry cannot answer for the next. */
    public static void clearCache() {
        ARTIFICER_CANDIDATES.clear();
    }

    // ── Scroll perks ────────────────────────────────────────────────────────────────────────

    /**
     * Scroll Mastery — the enchanted book you apply at an anvil may survive the process.
     *
     * <p>Vanilla has no scrolls; the enchanted book is what a scroll is in this game — a
     * single-use, consumed carrier for an enchantment. Setting the material cost to zero is
     * vanilla's own mechanism for "the right-hand item is not consumed", so the perk uses it rather
     * than handing back a copy.
     */
    @SubscribeEvent
    public void onAnvilUse(AnvilUpdateEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (RegistryPerks.SCROLL_MASTERY == null
                || !RegistryPerks.SCROLL_MASTERY.get().isEnabled(player)) {
            return;
        }
        if (event.getRight().getItem() != Items.ENCHANTED_BOOK) return;
        if (event.getOutput().isEmpty() || event.getMaterialCost() <= 0) return;

        double chance = HandlerCommonConfig.HANDLER.instance().scrollMasteryPercent / 100.0;
        // Deterministic for these inputs, for the same reason AnvilPerkHandler's rolls are: the
        // event fires repeatedly while the anvil is open, and a fresh roll each time would flicker.
        if (!AnvilPerkHandler.stableRoll(event, "scroll-mastery", chance)) return;
        event.setMaterialCost(0);
    }

    /**
     * Scroll Scribe — disenchanting may hand back a book carrying one of the enchantments removed.
     *
     * <p>"Create scrolls that replicate enchantments", expressed with the pieces vanilla has: the
     * grindstone destroys enchantments outright, and this recovers one of them onto the item that
     * carries enchantments around. The player is supplied by {@link ContainerInteraction}, because
     * the grindstone event itself carries none.
     */
    @SubscribeEvent
    public void onGrindstoneTake(GrindstoneEvent.OnTakeItem event) {
        Player player = ContainerInteraction.currentPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (RegistryPerks.SCROLL_SCRIBE == null
                || !RegistryPerks.SCROLL_SCRIBE.get().isEnabled(player)) {
            return;
        }
        double chance = HandlerCommonConfig.HANDLER.instance().scrollScribePercent / 100.0;
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) return;

        // Whichever input carried enchantments is the one that was just stripped.
        ItemStack source = event.getTopItem().isEnchanted() ? event.getTopItem() : event.getBottomItem();
        Map<Enchantment, Integer> removed = EnchantmentHelper.getEnchantments(source);
        removed.keySet().removeIf(Enchantment::isCurse);
        if (removed.isEmpty()) return;

        List<Map.Entry<Enchantment, Integer>> options = new ArrayList<>(removed.entrySet());
        Map.Entry<Enchantment, Integer> recovered =
                options.get(player.getRandom().nextInt(options.size()));

        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        net.minecraft.world.item.EnchantedBookItem.addEnchantment(book,
                new net.minecraft.world.item.enchantment.EnchantmentInstance(
                        recovered.getKey(), recovered.getValue()));
        if (!player.getInventory().add(book)) player.drop(book, false);
    }

    // ── Dimensional Wisdom ──────────────────────────────────────────────────────────────────

    /**
     * Dimensional Wisdom — "Enchantments work better in other dimensions".
     *
     * <p>Scales with how enchanted the weapon actually is, so the perk rewards bringing good gear
     * somewhere dangerous rather than being a flat damage bonus that happens to check the
     * dimension. Anywhere that is not the Overworld counts as "other", including modded dimensions.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onOutgoingDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) return;
        if (RegistryPerks.DIMENSIONAL_WISDOM == null
                || !RegistryPerks.DIMENSIONAL_WISDOM.get().isEnabled(player)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || !weapon.isEnchanted()) return;

        int levels = 0;
        for (int level : EnchantmentHelper.getEnchantments(weapon).values()) levels += level;
        if (levels <= 0) return;

        double perLevel = HandlerCommonConfig.HANDLER.instance().dimensionalWisdomPercent / 100.0;
        // Capped so a heavily enchanted weapon cannot compound into an unbounded multiplier.
        double bonus = Math.min(1.0, levels * perLevel / 10.0);
        event.setAmount((float) (event.getAmount() * (1.0 + bonus)));
    }

    // ── Temporal Wisdom ─────────────────────────────────────────────────────────────────────

    /** Records when a player was last in combat, which is what Temporal Wisdom keys off. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCombat(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player hurt) {
            LAST_COMBAT_TICK.put(hurt.getUUID(), hurt.level().getGameTime());
        }
        if (event.getSource().getEntity() instanceof Player attacker) {
            LAST_COMBAT_TICK.put(attacker.getUUID(), attacker.level().getGameTime());
        }
    }

    /**
     * Temporal Wisdom — "Enchantment effects last longer in combat".
     *
     * <p>Vanilla enchantments do not grant timed effects, so the durable reading is the beneficial
     * effects a player is running on while fighting. Extended as they arrive, and only while combat
     * is recent, so it rewards drinking during a fight rather than stockpiling beforehand.
     */
    @SubscribeEvent
    public void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        if (RegistryPerks.TEMPORAL_WISDOM == null
                || !RegistryPerks.TEMPORAL_WISDOM.get().isEnabled(player)) {
            return;
        }
        MobEffectInstance added = event.getEffectInstance();
        if (added == null || added.isInfiniteDuration()) return;
        if (added.getEffect().getCategory() != MobEffectCategory.BENEFICIAL) return;

        Long lastCombat = LAST_COMBAT_TICK.get(player.getUUID());
        int window = HandlerCommonConfig.HANDLER.instance().temporalWisdomCombatTicks;
        if (!GameTimeWindow.within(player.level().getGameTime(), lastCombat, window)) return;

        double extra = HandlerCommonConfig.HANDLER.instance().temporalWisdomPercent / 100.0;
        if (extra <= 0) return;
        player.addEffect(new MobEffectInstance(added.getEffect(),
                (int) (added.getDuration() * (1.0 + extra)),
                added.getAmplifier(), added.isAmbient(), added.isVisible()));
    }

    // ── Mystic Sight ────────────────────────────────────────────────────────────────────────

    /**
     * Mystic Sight — "You can see enchantments on items within a short radius".
     *
     * <p>Delivered as an outline on enchanted item entities nearby, which is vanilla's own way of
     * making something visible through the world and needs no client code. Throttled and short-lived
     * so the effect refreshes rather than accumulating.
     */
    @SubscribeEvent
    public void onSightTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % 20 != 0) return;
        if (RegistryPerks.MYSTIC_SIGHT == null
                || !RegistryPerks.MYSTIC_SIGHT.get().isEnabled(player)) {
            return;
        }
        double radius = HandlerCommonConfig.HANDLER.instance().mysticSightRadiusBlocks;
        if (radius <= 0) return;

        // An ItemEntity is not living, so there is no timed effect to apply; vanilla's outline for
        // a dropped item is the glowing tag, which persists until it is cleared. Setting it alone
        // would leave every item the player ever walked past glowing for the rest of the world's
        // life, so each pass sweeps a wider band and clears anything no longer being watched.
        AABB lit = player.getBoundingBox().inflate(radius);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, lit)) {
            if (item.getItem().isEnchanted() && !item.hasGlowingTag()) item.setGlowingTag(true);
        }

        AABB sweep = player.getBoundingBox().inflate(radius + CLEANUP_MARGIN_BLOCKS);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, sweep)) {
            if (!item.hasGlowingTag()) continue;
            // Only clear what nobody is still watching — otherwise one player walking away would
            // strip the outline from items another player with the perk is standing next to.
            if (!isWatchedByAnyone(item, radius)) item.setGlowingTag(false);
        }
    }

    /** How far past the lit radius the cleanup sweep reaches, so items are released as you leave. */
    private static final double CLEANUP_MARGIN_BLOCKS = 8.0;

    private static boolean isWatchedByAnyone(ItemEntity item, double radius) {
        for (Player nearby : item.level().getEntitiesOfClass(Player.class,
                item.getBoundingBox().inflate(radius))) {
            if (RegistryPerks.MYSTIC_SIGHT.get().isEnabled(nearby)) return true;
        }
        return false;
    }
}
