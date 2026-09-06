package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.common.workshop.WorkshopFocusService;
import com.otectus.runicskills.common.workshop.WorkshopBonus;
import com.otectus.runicskills.common.workshop.WorkshopFocusService.Focus;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.network.PacketRateLimiter;
import com.otectus.runicskills.network.packet.client.StationQuoteCP;
import com.otectus.runicskills.network.packet.client.WorkshopStatusCP;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import slimeknights.mantle.inventory.BaseContainerMenu;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.AlloyerBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.MelterBlockEntity;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The workshop half of the native integration: what a focus is worth, and what a player is told.
 *
 * <p><b>What the process bonus may change.</b> §6.5 draws the line at conservation: speed, yes;
 * material, fuel, byproducts and ore rates, never. Both seams behind this class honour that by
 * construction rather than by promise. Melting adds to the <em>increment</em> {@code heatItem} was
 * about to apply, which native code only reaches once its own fuel and temperature checks have
 * passed and which cannot complete a recipe in the same call; casting adds to the cooling timer,
 * which native code compares against its own target once per tick. Neither ever calls a native tick
 * twice, and neither touches a tank, a recipe or an output.
 *
 * <p><b>Where the bonus comes from.</b> The same two perks that already accelerate a vanilla
 * furnace — Smelter for the melt, Overclock for any station — read off the player who focused the
 * workshop rather than off whoever happens to be standing near it. That is the whole point of §6.4:
 * a furnace can be attributed by proximity because it is a block a player stands at, and a smeltery
 * cannot, because it is a machine a hopper feeds. The total is capped by
 * {@code tconstructWorkshopBonusCap}.
 *
 * <p><b>What it sends.</b> A focused player, and a player looking at any native station, get one
 * status at most twice a second (§15.3) through the ordinary rate limiter, and a station quote only
 * when the station's inputs or native result have actually moved. A player doing neither costs one
 * {@code instanceof} and one map read every ten ticks, which is why this can run for everybody
 * rather than needing a registry of who has a panel open.
 */
public final class TConstructWorkshopBridge {

    /** Once every ten ticks: the two updates a second §15.3 allows while a panel is open. */
    private static final int PUSH_INTERVAL_TICKS = 10;

    /** What each player was last quoted, so an unchanged station sends nothing. */
    private static final Map<UUID, Integer> LAST_QUOTE = new ConcurrentHashMap<>();

    /**
     * Installs the three answers the common focus service cannot have on its own.
     *
     * <p>Two predicates and one sender, all of them things that need a {@code slimeknights} type to
     * compute and that the packet handler, the command and the service itself must be able to do
     * without one. Called once, from the bootstrap.
     */
    static void install() {
        WorkshopFocusService.setTargets(
                TConstructWorkshopBridge::isController, TConstructWorkshopBridge::isCastingBlock);
        WorkshopFocusService.setEligibility(TConstructWorkshopBridge::measure);
        WorkshopFocusService.setStatusPublisher(TConstructWorkshopBridge::pushStatus);
    }

    /**
     * Whether this position holds a controller a focus may be claimed on.
     *
     * <p>Asked through {@code getBlockEntity} on a position the caller has already confirmed is
     * loaded, so it cannot load a chunk: an unloaded position simply has no block entity and
     * answers no. The three types are the ones that run a melting process — smeltery and foundry
     * through their shared heating structure, the melter, and the alloyer.
     */
    public static boolean isController(Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof HeatingStructureBlockEntity
                || entity instanceof MelterBlockEntity
                || entity instanceof AlloyerBlockEntity;
    }

    /** Whether this position holds a casting table or basin. */
    public static boolean isCastingBlock(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CastingBlockEntity;
    }

    /**
     * The share by which a focused workshop's melting increment is raised, or zero.
     *
     * @param controller the position of the controller that owns the melting module
     */
    public static double meltingBonus(Level level, BlockPos controller) {
        Focus focus = WorkshopFocusService.focusAt(level, controller);
        return focus == null ? 0.0 : focus.bonus().melting();
    }

    /**
     * The share by which a focused casting block's cooling progress is raised, or zero.
     *
     * @param casting the position of the casting table or basin itself, which must have been
     *                explicitly associated with a focused controller
     */
    public static double castingBonus(Level level, BlockPos casting) {
        Focus focus = WorkshopFocusService.focusAt(level, casting);
        return focus == null ? 0.0 : focus.bonus().casting();
    }

    /**
     * The online player whose focus covers {@code pos}, or {@code null}.
     *
     * <p>The one attribution question the casting and melting seams both ask, answered once here
     * rather than in each of them: a focus outlives a disconnect by up to one revalidation pass, and
     * crediting a player who is not there is not attribution.
     */
    public static ServerPlayer focusHolder(Level level, BlockPos pos) {
        if (level == null || level.getServer() == null) return null;
        UUID holder = WorkshopFocusService.holderOf(level, pos);
        return holder == null ? null : level.getServer().getPlayerList().getPlayer(holder);
    }

    /** Whether output produced without a player operating the workshop counts as their work. */
    public static boolean allowsAutomationRewards() {
        return HandlerCommonConfig.HANDLER.instance().tconstructAllowAutomationRewards;
    }

    /** Forgets one player's last quote. Called with their focus, on logout and on death. */
    public static void clearPlayer(UUID player) {
        if (player != null) LAST_QUOTE.remove(player);
    }

    /** Forgets every last quote, on server stop. */
    public static void clearAll() {
        LAST_QUOTE.clear();
    }

    /** Drops the quote cache for a player who has left, since nothing will tick it away. */
    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayer(event.getEntity().getUUID());
    }

    /** Drops every quote on server stop, so a second world in this JVM starts clean. */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        clearAll();
    }

    /** Pushes focus status and station quotes to whoever has a reason to see one. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % PUSH_INTERVAL_TICKS != 0) return;
        if (!TConstructCompatibilityStatus.current().supports(Capability.WORKSHOP)) return;

        WorkshopFocusService.maybeRevalidate(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // The player-side half of revalidation: distance, dimension, a controller that is no
            // longer there, and a fresh measurement of what their perks are currently worth.
            boolean focused = WorkshopFocusService.heartbeat(player) != null;
            boolean nativeMenu = player.containerMenu instanceof BaseContainerMenu<?>;
            if (!focused && !nativeMenu) {
                LAST_QUOTE.remove(player.getUUID());
                continue;
            }
            if (PacketRateLimiter.allow(player, "tc_workshop_status", PUSH_INTERVAL_TICKS)) {
                pushStatus(player);
            }
            if (player.containerMenu instanceof TinkerStationContainerMenu station) {
                sendQuote(player, station);
            }
        }
    }

    /**
     * Tells one player where their focus stands and what the block in front of them is.
     *
     * <p>Also the answer to a focus request, accepted or refused: a client that quoted a stale
     * token gets the current one here rather than being left unable to try again.
     */
    public static void pushStatus(ServerPlayer player) {
        if (player == null) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Focus focus = WorkshopFocusService.activeFocus(player);

        BlockPos menuBlock = BlockPos.ZERO;
        byte menuKind = WorkshopStatusCP.MENU_NONE;
        // The block behind the open menu, read server-side so the client never has to name one it
        // worked out for itself. Mantle's base menu is what every Tinkers' station GUI extends, so
        // one cast covers the station, the melter, the smeltery and the alloyer alike.
        if (player.containerMenu instanceof BaseContainerMenu<?> menu && menu.getTile() != null) {
            BlockPos pos = menu.getTile().getBlockPos();
            if (isController(player.level(), pos)) {
                menuBlock = pos;
                menuKind = WorkshopStatusCP.MENU_CONTROLLER;
            } else if (isCastingBlock(player.level(), pos)) {
                menuBlock = pos;
                menuKind = WorkshopStatusCP.MENU_CASTING;
            }
        }

        int bonusPercent = focus == null ? 0 : (int) Math.round(focus.bonus().melting() * 100.0);
        String owner = focus == null ? "" : player.getGameProfile().getName();
        WorkshopStatusCP.send(player, focus,
                WorkshopFocusService.remainingTicks(focus, server), bonusPercent,
                allowsAutomationRewards(), owner, menuBlock, menuKind);
    }

    /**
     * Sends this player their own quote for the station they are looking at, when it has changed.
     *
     * <p>A quote is per-player and derived from a copy — see {@code TConstructStationBridge.quote}
     * — so two players at one station get two answers and neither is the block entity's cached
     * result. It is re-sent only when the inputs or the native result move, which is §14.2's
     * staleness rule expressed as the thing that actually decides it.
     */
    private void sendQuote(ServerPlayer player, TinkerStationContainerMenu menu) {
        TinkerStationBlockEntity station = menu.getTile();
        if (station == null) {
            LAST_QUOTE.remove(player.getUUID());
            return;
        }
        ItemStack base = station.getCraftingResult().getResult();
        StationQuote quote = TConstructStationBridge.quote(
                player, menu.containerId, base, station, station);
        int key = quote.menuId() * 961 + quote.inputFingerprint() * 31 + quote.baseFingerprint();
        Integer previous = LAST_QUOTE.get(player.getUUID());
        if (previous != null && previous == key) return;
        if (!PacketRateLimiter.allow(player, "tc_station_quote", PUSH_INTERVAL_TICKS)) return;
        LAST_QUOTE.put(player.getUUID(), key);
        StationQuoteCP.send(player, quote.menuId(), quote.revision(), quote.kind().name(),
                quote.recipeId() == null ? "" : quote.recipeId().toString(), quote.preview());
    }

    /**
     * What one player's workshop perks are worth, capped, at the moment they are measured.
     *
     * <p>Installed into the focus service as its eligibility source, so this runs when a claim is
     * made or refreshed and never while a block is ticking. Smelter is a melting perk and stays out
     * of the casting figure; Overclock covers any station and is in both. The cap is applied here
     * so nothing downstream has to remember it.
     */
    private static WorkshopBonus measure(ServerPlayer player) {
        if (player == null) return WorkshopBonus.NONE;
        if (!TConstructCompatibilityStatus.current().supports(Capability.WORKSHOP)) {
            return WorkshopBonus.NONE;
        }
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double shared = 0.0;
        if (RegistryPerks.OVERCLOCK != null && RegistryPerks.OVERCLOCK.get().isEnabled(player)) {
            shared = config.overclockPercent / 100.0;
        }
        double melting = shared;
        if (RegistryPerks.SMELTER != null && RegistryPerks.SMELTER.get().isEnabled(player)) {
            melting += config.smelterPercent / 100.0;
        }
        // Thermal Rhythm melts; Workshop Cadence cools. They join the same two figures the two
        // older perks feed, under the same single cap — §13.2's worked example is exactly this:
        // Thermal Rhythm at 10% plus a 15% melting perk is 25% extra melting progress, and adding
        // Workshop Cadence to cooling cannot push cooling past that same ceiling.
        melting += TConstructPerkHandler.thermalRhythmBonus(player);
        double casting = shared + TConstructPerkHandler.workshopCadenceBonus(player);
        // Heart of the Foundry raises melting and cooling alike, and joins the same two figures
        // under the same ceiling: §11.4 says it shares the 25% workshop cap rather than adding one.
        double artifice = TConstructPowerDispatcher.workshopProgressBonus(player);
        melting += artifice;
        casting += artifice;
        double cap = config.tconstructWorkshopBonusCap;
        return new WorkshopBonus(Math.min(melting, cap), Math.min(casting, cap));
    }
}
