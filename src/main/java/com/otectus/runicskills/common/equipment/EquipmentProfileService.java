package com.otectus.runicskills.common.equipment;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.durability.RepairSource;
import com.otectus.runicskills.registry.RegistryTags;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "What is this item?", asked in one place by every perk that used to guess.
 *
 * <p>The list is ordered and always ends in {@link VanillaEquipmentAdapter}, which claims
 * everything. {@link #register} inserts ahead of it, so an optional-mod adapter gets first refusal
 * on its own items and vanilla remains the answer for everything else. Registration order among
 * registered adapters is preserved, so the earliest registered wins a contested stack.
 *
 * <p><b>The deny tag is checked before any adapter.</b> {@code runicskills:equipment_deny} is a
 * pack statement that an item is not equipment for this mod's purposes at all — a decorative
 * sword, a quest item that happens to extend {@code SwordItem}. Checking it first means no adapter
 * can override it, which is the point: an opt-out an integration could quietly beat is not an
 * opt-out.
 */
public final class EquipmentProfileService {

    private EquipmentProfileService() {
    }

    private static final List<EquipmentAdapter> ADAPTERS = new ArrayList<>(List.of(
            VanillaEquipmentAdapter.INSTANCE));

    /**
     * Registers {@code adapter} ahead of the vanilla adapter, which stays last.
     *
     * <p>Called from an optional integration's bootstrap during mod loading.
     */
    public static synchronized void register(EquipmentAdapter adapter) {
        if (adapter == null) return;
        ADAPTERS.add(Math.max(0, ADAPTERS.size() - 1), adapter);
    }

    /** Immutable snapshot of the adapters, in order. */
    public static synchronized List<EquipmentAdapter> adapters() {
        return List.copyOf(ADAPTERS);
    }

    /** What {@code stack} is, or empty when a pack has denied it or nothing claimed it. */
    public static Optional<EquipmentProfile> profile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        if (stack.is(RegistryTags.Items.EQUIPMENT_DENY)) return Optional.empty();
        for (EquipmentAdapter adapter : adapters()) {
            Optional<EquipmentProfile> profile = claim(adapter, stack);
            if (profile.isPresent()) return profile;
        }
        return Optional.empty();
    }

    /** Whether {@code stack} holds {@code role}. The question almost every caller actually has. */
    public static boolean hasRole(ItemStack stack, EquipmentRole role) {
        return profile(stack).map(profile -> profile.has(role)).orElse(false);
    }

    /**
     * Mends {@code stack} through whichever adapter claims it, and reports the points spent.
     * A stack no adapter claims is not repaired, and says so by returning zero.
     */
    public static int repair(ItemStack stack, int points, RepairSource source) {
        if (stack == null || stack.isEmpty() || points <= 0) return 0;
        if (stack.is(RegistryTags.Items.EQUIPMENT_DENY)) return 0;
        for (EquipmentAdapter adapter : adapters()) {
            if (claim(adapter, stack).isEmpty()) continue;
            try {
                return adapter.repair(stack, points, source);
            } catch (RuntimeException e) {
                // A third-party adapter must not be able to break a repair for everyone else.
                RunicSkills.getLOGGER().warn(
                        "[Runic Skills] equipment adapter {} threw while repairing; no points spent",
                        adapter.id(), e);
                return 0;
            }
        }
        return 0;
    }

    /** One adapter's answer, with a throwing adapter treated as "does not recognise it". */
    private static Optional<EquipmentProfile> claim(EquipmentAdapter adapter, ItemStack stack) {
        try {
            Optional<EquipmentProfile> profile = adapter.profile(stack);
            return profile == null ? Optional.empty() : profile;
        } catch (RuntimeException e) {
            RunicSkills.getLOGGER().warn(
                    "[Runic Skills] equipment adapter {} threw while classifying an item; skipping it",
                    adapter.id(), e);
            return Optional.empty();
        }
    }
}
