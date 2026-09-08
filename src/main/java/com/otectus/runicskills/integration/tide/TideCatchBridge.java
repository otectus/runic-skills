package com.otectus.runicskills.integration.tide;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Observes native casts and delivery; never grants fish, XP, journal entries or refunds. */
public final class TideCatchBridge {
    private record Live(TideCatchLedger.Cast cast, ItemStack actualRod, ItemStack snapshot) {}
    private static final TideCatchLedger LEDGER = new TideCatchLedger();
    private static final Map<UUID, Live> LIVE = new HashMap<>();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final ThreadLocal<Retrieval> CURRENT = new ThreadLocal<>();
    private static long fishCommits;
    public static boolean available() {
        return IntegrationRuntime.check(IntegrationModule.TIDE, Feature.PERKS, Capability.CATCH_COMMIT, Capability.ORDINARY_WEAR).available();
    }
    private static boolean trackingAvailable() { return available() || TidePowers.availability().available() || TideUnbrokenThread.availability().available() || TideKeeperOfTheBanks.availability().available() || TideBaitkeeper.available() || TideManyWaters.available(); }
    public static Entity active(Player player) {
        if (player.level().isClientSide || !trackingAvailable()) return null;
        try { return TideNativeAccess.active(player); }
        catch (RuntimeException e) { unavailable(e); return null; }
    }
    /** Called only after native castHook returns, with the hook present before the call. */
    public static void cast(Player player, ItemStack rod, Entity before) {
        if (!(player instanceof ServerPlayer server) || player instanceof FakePlayer || !trackingAvailable() || before != null) return;
        try {
            Entity hook = TideNativeAccess.active(player);
            if (!(hook instanceof Projectile projectile) || projectile.getOwner() != player
                    || server.serverLevel().getEntity(hook.getUUID()) != hook || TideNativeAccess.rod(hook) != rod) return;
            boolean offHand = player.getOffhandItem() == rod;
            if (!offHand && player.getMainHandItem() != rod) return;
            var capability = com.otectus.runicskills.common.capability.SkillCapability.get(player);
            if (capability == null || !capability.canUseItemSilent(player, rod)) return;
            TidePowers.cast(server);
            TideKeeperOfTheBanks.cast(server);
            TideEmberAndStar.cast(server, hook);
            TideJournal.cast(server, hook);
            TideManyWaters.clearCharge(server);
            if (!boundedTag(rod)) return;
            long now = server.serverLevel().getGameTime();
            var cast = new TideCatchLedger.Cast(player.getUUID(), hook.getUUID(), GENERATION.incrementAndGet(),
                    server.level().dimension().location().toString(), ForgeRegistries.ITEMS.getKey(rod.getItem()).toString(),
                    offHand, "pending", "native_retrieval", IntegrationRuntime.configurationRevision(), now, now + 24000);
            if (LEDGER.begin(cast)) LIVE.put(player.getUUID(), new Live(cast, rod, rod.copy()));
        } catch (RuntimeException e) { unavailable(e); }
    }
    private static boolean boundedTag(ItemStack rod) {
        if (rod.getTag() == null) return true;
        try {
            NbtIo.write(rod.getTag(), new DataOutputStream(new OutputStream() {
                int bytes;
                @Override public void write(int value) throws IOException { if (++bytes > 4096) throw new IOException("Rod snapshot too large"); }
            }));
            return true;
        } catch (IOException e) { return false; }
    }
    public static Retrieval retrieve(Entity hook, ItemStack rod, Player player) {
        Retrieval scope = new Retrieval(hook, rod, player, CURRENT.get());
        CURRENT.set(scope);
        return scope;
    }
    public static void delivered(Entity hook, Entity output, boolean accepted) {
        Retrieval current = CURRENT.get();
        if (current != null && current.hook == hook && output instanceof ItemEntity item) {
            current.lastDeliveredFish=false;
            if (accepted && current.eligible) try { current.lastDeliveredFish=TideNativeAccess.species(item.getItem())!=null; }
            catch (RuntimeException e) { current.eligible=false; unavailable(e); }
        }
        if (current != null && current.hook == hook && accepted && output instanceof ItemEntity item) {
            if (current.delivered.size() >= 32) current.eligible = false;
            else current.delivered.add(item.getItem());
        }
    }
    public static final class BaitScope implements AutoCloseable {
        private final Retrieval scope;
        private final Object previous;
        private BaitScope(Entity hook,Object contents) {
            scope=CURRENT.get(); previous=scope==null?null:scope.baitContents;
            if (scope!=null) scope.baitContents=scope.hook==hook && previous==null?contents:null;
        }
        @Override public void close() { if (scope!=null) scope.baitContents=previous; }
    }
    public static BaitScope baitScope(Entity hook,Object contents) { return new BaitScope(hook,contents); }
    /** A single admitted attempt; the ItemStack is the native shrink call's actual mutable subject. */
    public static boolean preserveBait(Object contents,ItemStack bait,int amount) {
        Retrieval scope=CURRENT.get();
        if (scope==null || scope.previous!=null || scope.completed || !scope.eligible || scope.baitAttempted
                || scope.baitContents!=contents || !scope.lastDeliveredFish || amount!=1 || !scope.player.isAlive()
                || !scope.currentUseAllowed() || !TideBaitkeeper.nativeBait(bait)) return false;
        double chance = TideBaitkeeper.eligible(scope.player, bait) ? TideBaitkeeper.chance() : 0;
        double keeper = TideKeeperOfTheBanks.baitChance(scope.player);
        if (chance <= 0 && keeper <= 0) return false;
        scope.baitAttempted=true;
        chance += TideKeeperOfTheBanks.spendBait((ServerPlayer) scope.player);
        return scope.player.getRandom().nextDouble() < Math.min(.25, chance);
    }
    /** Runs after native logging returns, even when optional size/journal recording declines. */
    public static void fishProcessed(Entity hook, ItemStack stack) {
        Retrieval current = CURRENT.get();
        if (current == null || current.hook != hook || !current.eligible || !current.delivered.contains(stack)) return;
        try {
            String species = TideNativeAccess.species(stack);
            if (species != null) current.species.add(species);
        } catch (RuntimeException e) { current.eligible = false; unavailable(e); }
    }
    public static final class Retrieval implements AutoCloseable {
        private final Entity hook;
        private final Player player;
        private final Live live;
        private final Retrieval previous;
        private final Set<ItemStack> delivered = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<String> species = new TreeSet<>();
        private boolean eligible, completed;
        private boolean lastDeliveredFish,baitAttempted;
        private Object baitContents;
        private String medium = "unknown";
        private TideHabitat.Family habitat = TideHabitat.Family.OTHER;
        private boolean currentUseAllowed() {
            if (live==null || player==null) return false;
            var cap=com.otectus.runicskills.common.capability.SkillCapability.get(player);
            return cap!=null && cap.canUseItemSilent(player,live.actualRod)
                    && (live.cast.offHand()?player.getOffhandItem()==live.actualRod:player.getMainHandItem()==live.actualRod);
        }
        private Retrieval(Entity hook, ItemStack rod, Player player, Retrieval previous) {
            this.hook = hook; this.player = player; this.previous = previous;
            live = player == null ? null : LIVE.get(player.getUUID());
            try {
                eligible = previous == null && trackingAvailable() && live != null && player instanceof ServerPlayer && !(player instanceof FakePlayer)
                        && !hook.isRemoved() && hook instanceof Projectile projectile && projectile.getOwner() == player
                        && TideNativeAccess.active(player) == hook && live.cast.hook().equals(hook.getUUID())
                        && live.actualRod == rod && ItemStack.matches(rod, live.snapshot)
                        && com.otectus.runicskills.common.capability.SkillCapability.get(player) != null
                        && com.otectus.runicskills.common.capability.SkillCapability.get(player).canUseItemSilent(player, rod)
                        && (live.cast.offHand() ? player.getOffhandItem() == rod : player.getMainHandItem() == rod)
                        && !TideNativeAccess.pulledEntity(hook) && "FISH".equals(TideNativeAccess.catchType(hook));
                if (eligible) { habitat = TideHabitat.family(hook.level().getBiome(hook.blockPosition())); medium = TideNativeAccess.medium(hook); eligible = TideNativeAccess.legalMedium(hook, medium); }
            } catch (RuntimeException e) { eligible = false; unavailable(e); }
        }
        public int complete(int nativeWear) {
            completed = true;
            if (live == null || previous != null || !live.cast.hook().equals(hook.getUUID())) return nativeWear;
            var result = LEDGER.commit(player.getUUID(), hook.getUUID(), live.cast.generation(),
                    player.level().dimension().location().toString(), player.level().getGameTime(),
                    eligible && trackingAvailable() && player.isAlive() && currentUseAllowed() && !species.isEmpty(),
                    species.isEmpty() ? TideCatchLedger.Outcome.EMPTY : TideCatchLedger.Outcome.FISH,
                    species.isEmpty() ? null : species.iterator().next(), false);
            LIVE.remove(player.getUUID(), live);
            if (result.isEmpty()) return nativeWear;
            fishCommits++;
            TidePowers.caught((ServerPlayer) player);
            TideManyWaters.caught((ServerPlayer) player, habitat);
            TideKeeperOfTheBanks.caught((ServerPlayer) player, Set.copyOf(species), habitat);
            TideEmberAndStar.caught((ServerPlayer) player, medium);
            TideJournal.caught((ServerPlayer) player, Set.copyOf(species));
            double chance = TideUnbrokenThread.caught((ServerPlayer)player,live.cast.hook());
            var cfg = HandlerCommonConfig.HANDLER.instance();
            if (RegistryPerks.TIDE_PATIENT_HANDS.get().isEnabled(player)) chance += cfg.tidePatientHandsPercent / 100.0;
            if (("tide:lava".equals(medium) || "tide:void".equals(medium))
                    && RegistryPerks.TIDE_CAREFUL_LANDING.get().isEnabled(player)) chance += cfg.tideCarefulLandingPercent / 100.0;
            if (chance > 0) chance = TideWear.additionalChance(chance,
                    com.otectus.runicskills.common.durability.WearAvoidance.avoidance((ServerPlayer)player,live.actualRod));
            return chance <= 0 || nativeWear <= 0 ? nativeWear : TideWear.reduce(nativeWear, chance, player.getRandom().nextDouble());
        }
        @Override public void close() {
            if (!completed && previous == null && live != null && live.cast.hook().equals(hook.getUUID())) clear(player.getUUID());
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
    private static void unavailable(RuntimeException e) {
        IntegrationRuntime.capability(IntegrationModule.TIDE, Capability.CATCH_COMMIT, "Native retrieval observation failed; benefits disabled until restart.");
        RunicSkills.getLOGGER().warn("Tide catch observation disabled", e);
    }
    public static long fishCommits() { return fishCommits; }
    public static int activeCount() { return LIVE.size(); }
    private static void clear(UUID actor) { LIVE.remove(actor); LEDGER.clear(actor); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity().getUUID()); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity().getUUID()); }
    @SubscribeEvent public void death(LivingDeathEvent event) { if (event.getEntity() instanceof Player) clear(event.getEntity().getUUID()); }
    @SubscribeEvent public void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Projectile projectile) || !(projectile.getOwner() instanceof Player player)) return;
        Retrieval current = CURRENT.get();
        if (current != null && current.hook == projectile) return; // Native discard precedes the return/commit.
        Live live = LIVE.get(player.getUUID());
        if (live != null && live.cast.hook().equals(projectile.getUUID())) clear(player.getUUID());
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0 || LIVE.isEmpty()) return;
        long now = event.getServer().overworld().getGameTime();
        for (Live live : List.copyOf(LIVE.values())) if (now < live.cast.started() || now >= live.cast.expires()) clear(live.cast.actor());
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { LIVE.clear(); LEDGER.clear(); CURRENT.remove(); fishCommits = 0; }
}
