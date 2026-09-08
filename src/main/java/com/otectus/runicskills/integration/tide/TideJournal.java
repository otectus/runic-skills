package com.otectus.runicskills.integration.tide;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.event.PowerProcEvent;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import java.util.*;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Server authority for known-fish information, explicit selection and one native species roll. */
public final class TideJournal {
    public static final String ALMANAC = "tide_anglers_almanac", FAVOR = "tide_favor_from_the_deep";
    public static final ResourceLocation NONE = new ResourceLocation("minecraft:air");
    private static final Map<SkillCapability, Memory> MEMORY = new WeakHashMap<>();
    private static final Map<Entity, WeightedCast> CASTS = new WeakHashMap<>();
    private static final ThreadLocal<Roll> ROLL = new ThreadLocal<>();
    private static long weightedEntries;
    public static long weightedEntries() { return weightedEntries; }
    private static final class Memory {
        final Map<String, Long> almanac = new HashMap<>(), favor = new HashMap<>();
        ResourceLocation selection = NONE;
    }
    private record WeightedCast(SkillCapability owner, ResourceLocation selection, long revision) {}
    private record Roll(Object context, Entity hook, WeightedCast cast) {}
    public record Entry(ResourceLocation fish, boolean eligible, List<TideJournalAccess.Condition> requirements) {}
    public record Page(int page, int pages, int known, int features, boolean live, ResourceLocation selected, List<Entry> entries, String message) {}
    private TideJournal() {}
    public static Result availability(boolean power, boolean weighting) {
        var result = IntegrationRuntime.check(IntegrationModule.TIDE, power ? Feature.POWERS : Feature.PERKS, Capability.JOURNAL);
        if (result.available() && power) result = IntegrationRuntime.check(IntegrationModule.TIDE, Feature.POWERS, Capability.CATCH_COMMIT);
        if (result.available()) result = IntegrationRuntime.check(IntegrationModule.TIDE, Feature.JOURNAL, Capability.JOURNAL);
        if (result.available() && weighting) result = IntegrationRuntime.check(IntegrationModule.TIDE, Feature.WEIGHTING, Capability.CATCH_COMMIT, Capability.SPECIES_WEIGHTING);
        return result;
    }
    private static Power almanac() { return RegistryPowers.TIDE_ANGLERS_ALMANAC.get(); }
    private static Power favor() { return RegistryPowers.TIDE_FAVOR_FROM_THE_DEEP.get(); }
    private static boolean active(Player player, Power power) {
        return player != null && !(player instanceof FakePlayer) && player.isAlive() && power.isEquippedBy(player)
                && PowerEligibility.evaluateActive(player, power).eligible();
    }
    private static Memory memory(SkillCapability cap) {
        if (cap == null) return null;
        Memory m = MEMORY.get(cap);
        if (m == null && MEMORY.size() < 1024) { m = new Memory(); MEMORY.put(cap, m); }
        return m;
    }
    public static boolean hasJournal(Player player) {
        return player.getInventory().items.stream().anyMatch(TideJournal::journalItem)
                || player.getInventory().offhand.stream().anyMatch(TideJournal::journalItem);
    }
    private static boolean journalItem(net.minecraft.world.item.ItemStack stack) {
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && "tide".equals(id.getNamespace()) && Set.of("fishing_journal", "fish_finder").contains(id.getPath());
    }
    public static Page query(ServerPlayer player, ResourceLocation selected, int requestedPage, int filter, boolean select) {
        Page empty = new Page(0, 1, 0, 0, false, NONE, List.of(), "unavailable");
        if (player instanceof FakePlayer || !player.isAlive() || !hasJournal(player)
                || (!availability(false, false).available() && !availability(true, false).available())) return empty;
        var cap = SkillCapability.get(player); Memory memory = memory(cap); if (memory == null) return empty;
        try {
            var known = TideJournalAccess.known(player);
            boolean reading = RegistryPerks.TIDE_READ_THE_WATER.get().isEnabled(player);
            boolean naturalist = RegistryPerks.TIDE_FIELD_NATURALIST.get().isEnabled(player);
            boolean expanded = active(player, almanac()) && cap.isPowerWindowActive(ALMANAC, player.level().getGameTime());
            int features = expanded ? 3 : naturalist ? known.size() >= 50 ? 3 : known.size() >= 25 ? 2 : known.size() >= 10 ? 1 : 0 : 0;
            if (!reading && !naturalist && !expanded && !active(player, favor())) return empty;
            if (!known.containsKey(selected)) selected = NONE;
            if (select && !NONE.equals(selected) && active(player, favor())) memory.selection = selected;
            Entity hook = TideNativeAccess.active(player);
            Object context = hook == null ? null : TideJournalAccess.context(hook);
            int allowedFilter = Math.max(0, Math.min(features, filter));
            List<Entry> rows = new ArrayList<>();
            List<TideJournalAccess.Condition> reference = NONE.equals(selected) ? List.of() : TideJournalAccess.requirements(known.get(selected), context);
            for (var fish : known.entrySet()) {
                boolean eligible = TideJournalAccess.eligible(fish.getValue(), context);
                if (allowedFilter == 1 && !eligible) continue;
                var requirements = TideJournalAccess.requirements(fish.getValue(), context);
                if (allowedFilter == 2 && !reference.isEmpty() && requirements.stream().noneMatch(c -> reference.stream()
                        .anyMatch(r -> r.label().equals(c.label()) && r.requirement().equals(c.requirement())))) continue;
                rows.add(new Entry(fish.getKey(), eligible, reading || features > 0 ? requirements : List.of()));
            }
            if (allowedFilter == 3 && context != null) rows.sort(Comparator.comparingLong(e -> e.requirements.stream().filter(c -> !c.passed()).count()));
            int pages = Math.max(1, (rows.size() + 11) / 12), page = Math.max(0, Math.min(pages - 1, requestedPage));
            return new Page(page, pages, known.size(), features, context != null, memory.selection,
                    List.copyOf(rows.subList(Math.min(rows.size(), page * 12), Math.min(rows.size(), (page + 1) * 12))),
                    context == null ? "no_context" : "live_context");
        } catch (RuntimeException e) { failed(); return empty; }
    }
    static void caught(ServerPlayer player, Set<String> species) {
        var cap = SkillCapability.get(player); var m = memory(cap); if (m == null) return;
        long now = player.level().getGameTime();
        sequence(player, almanac(), m.almanac, species, now, 6000, 3, 1200);
        sequence(player, favor(), m.favor, species, now, 12000, 5, 2400);
    }
    private static void sequence(ServerPlayer player, Power power, Map<String, Long> sequence, Set<String> species, long now, int window, int required, int charge) {
        var cap = SkillCapability.get(player);
        if (!active(player, power) || cap.isPowerOnCooldown(power.getName(), now)) { sequence.clear(); return; }
        sequence.values().removeIf(t -> now < t || now - t > window);
        for (String id : species) if (sequence.size() < required) sequence.put(id, now);
        if (sequence.size() < required) return;
        sequence.clear();
        if (!PowerCooldownDebt.checkAndStart(player, power, now, Math.max(1, Math.min(72000,
                PowerOverridesManager.icdTicksOr(power, power.defaultIcdTicks))))) return;
        cap.setPowerWindow(power.getName(), now + charge); SyncSkillCapabilityCP.send(player);
        MinecraftForge.EVENT_BUS.post(new PowerProcEvent(player, power, null, null, 0, 128, false, true));
    }
    static void cast(ServerPlayer player, Entity hook) {
        var cap = SkillCapability.get(player); var memory = MEMORY.get(cap);
        if (memory == null || !active(player, favor()) || !cap.isPowerWindowActive(FAVOR, player.level().getGameTime())) return;
        try {
            if (TideJournalAccess.known(player).containsKey(memory.selection) && CASTS.size() < 1024) {
                cap.setPowerWindow(FAVOR, 0); SyncSkillCapabilityCP.send(player);
                CASTS.put(hook, new WeightedCast(cap, memory.selection, IntegrationRuntime.configurationRevision()));
            }
        } catch (RuntimeException e) { failed(); }
    }
    public interface Scope extends AutoCloseable { @Override void close(); }
    public static Scope roll(Object context) {
        Roll previous = ROLL.get(); ROLL.remove(); Entity hook;
        try { hook = TideJournalAccess.hook(context); }
        catch (RuntimeException e) { failed(); return () -> { if (previous != null) ROLL.set(previous); }; }
        WeightedCast cast = CASTS.remove(hook);
        if (previous == null && cast != null && hook instanceof Projectile p && p.getOwner() instanceof ServerPlayer player
                && active(player, favor()) && cast.owner == SkillCapability.get(player) && cast.revision == IntegrationRuntime.configurationRevision())
            ROLL.set(new Roll(context, hook, cast));
        return () -> { if (previous == null) ROLL.remove(); else ROLL.set(previous); };
    }
    public static double weight(Object data, Object context, double nativeWeight) {
        Roll roll = ROLL.get();
        if (roll == null || roll.context != context || !Double.isFinite(nativeWeight) || nativeWeight <= 0) return nativeWeight;
        try {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(TideJournalAccess.key(data));
            Object canonical = item == null ? null : TideJournalAccess.data(new net.minecraft.world.item.ItemStack(item));
            if (canonical != null && roll.cast.selection.equals(TideJournalAccess.key(canonical)) && TideJournalAccess.eligible(data, context)) {
                weightedEntries++; return IntegrationLimits.eligibleSpeciesWeight(nativeWeight, true, 1.10);
            }
            return nativeWeight;
        }
        catch (RuntimeException e) { failed(); return nativeWeight; }
    }
    public static void clear(SkillCapability cap, String id) {
        if (cap == null || (!ALMANAC.equals(id) && !FAVOR.equals(id) && id != null)) return;
        var m = MEMORY.get(cap);
        if (m != null) { if (id == null || ALMANAC.equals(id)) m.almanac.clear(); if (id == null || FAVOR.equals(id)) m.favor.clear(); }
        if (id == null || ALMANAC.equals(id)) cap.setPowerWindow(ALMANAC, 0);
        if (id == null || FAVOR.equals(id)) {
            cap.setPowerWindow(FAVOR, 0);
            if (net.minecraftforge.fml.util.thread.EffectiveSide.get().isServer()) CASTS.values().removeIf(c -> c.owner == cap);
        }
        if (id == null) MEMORY.remove(cap);
    }
    static void tick(ServerPlayer player) {
        var cap = SkillCapability.get(player);
        if (!active(player, almanac())) clear(cap, ALMANAC);
        if (!active(player, favor())) clear(cap, FAVOR);
        CASTS.keySet().removeIf(Entity::isRemoved);
    }
    private static void failed() {
        IntegrationRuntime.capability(IntegrationModule.TIDE, Capability.JOURNAL, "Native journal read failed; unavailable until restart.");
    }
}
