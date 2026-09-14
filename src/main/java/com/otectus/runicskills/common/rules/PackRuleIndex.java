package com.otectus.runicskills.common.rules;

import com.otectus.runicskills.common.equipment.EquipmentRole;
import com.otectus.runicskills.common.util.LogOnce;
import com.otectus.runicskills.integration.lock.LockAction;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The pack rules currently in force, and the only thing that answers a question with them.
 *
 * <p>Immutable and swapped whole, for the reason {@code RecyclingIndex} is: a lookup that could
 * observe a half-loaded pack would be a protection index with a hole in it, and §13.3 step 7 says
 * never to leave one. The previous index stays in force until a new one is completely built, so a
 * datapack that fails to parse disables <em>new</em> rules rather than every rule.
 *
 * <p><b>The revision is part of the contract.</b> §13.3 step 6 asks for one so a profile, a preview
 * or a client snapshot computed against an old ruleset can be told it is stale. It increments once
 * per successful install and never rolls back, so a failed reload leaves both the rules and the
 * revision as they were — a consumer that saw no change is right to believe nothing changed.
 *
 * <p><b>Equal-priority disagreement is refused, not resolved.</b> §13.3 step 3 forbids falling back
 * to filesystem order, so two rules of the same priority that match the same thing and say
 * different things produce no answer at all: the automatic profile applies and the conflict is
 * logged once, naming both. Silently picking one would make the pack's behaviour depend on which
 * file the resource manager happened to read first.
 */
public final class PackRuleIndex {

    /** In force before the first reload, and on a server whose packs ship no rules. */
    private static final PackRuleIndex EMPTY = new PackRuleIndex(List.of(), 0);

    private static volatile PackRuleIndex current = EMPTY;

    private final List<PackRule> useRequirements;
    private final List<PackRule> craftRewardPolicies;
    private final int revision;

    private PackRuleIndex(List<PackRule> rules, int revision) {
        List<PackRule> use = new ArrayList<>();
        List<PackRule> reward = new ArrayList<>();
        for (PackRule rule : rules) {
            switch (rule.kind()) {
                case USE_REQUIREMENT -> use.add(rule);
                case CRAFT_REWARD_POLICY -> reward.add(rule);
            }
        }
        // Sorted once, at build time, highest priority first. A lookup compares all matches in
        // the winning priority group; two agreeing entries must not hide a third disagreement.
        Comparator<PackRule> byPriority = Comparator.comparingInt(PackRule::priority).reversed();
        use.sort(byPriority);
        reward.sort(byPriority);
        this.useRequirements = List.copyOf(use);
        this.craftRewardPolicies = List.copyOf(reward);
        this.revision = revision;
    }

    /** The index currently in force. Never {@code null}. */
    public static PackRuleIndex get() {
        return current;
    }

    /**
     * Installs {@code rules} as the ruleset, one revision on from the current one.
     *
     * <p>Called from the reload listener's {@code apply}, which the resource manager runs on the
     * server thread between ticks — the tick boundary §13.3 step 6 asks the swap to happen at.
     */
    public static void install(List<PackRule> rules) {
        if (rules == null) return;
        PackRuleIndex previous = current;
        current = new PackRuleIndex(rules, previous.revision + 1);
    }

    /** Drops every rule and bumps the revision. Server stop and tests only; a reload replaces. */
    public static void clear() {
        current = new PackRuleIndex(List.of(), current.revision + 1);
    }

    /** How many times a ruleset has been installed on this server. */
    public int revision() {
        return revision;
    }

    /** How many rules are loaded, of both kinds. For diagnostics and tests. */
    public int size() {
        return useRequirements.size() + craftRewardPolicies.size();
    }

    /**
     * Whether any loaded use rule narrows by role.
     *
     * <p>Asked by the Tinkers' side before it builds the definition-to-roles table a role selector
     * needs: that table costs a pass over the item registry, and the common case is a pack whose
     * rules name definitions and materials only, where the pass would buy nothing.
     */
    public boolean usesRoleSelectors() {
        for (PackRule rule : useRequirements) {
            if (!rule.match().roles().isEmpty()) return true;
        }
        return false;
    }

    /**
     * The explicit rule about using this tool, or empty when no pack has written one.
     *
     * @param materialTier the highest functional material tier, or {@code -1} when undetermined
     */
    public Optional<PackRule> useRequirement(ResourceLocation definitionId,
                                             Set<ResourceLocation> materialIds,
                                             Set<EquipmentRole> roles, int materialTier,
                                             LockAction action) {
        List<PackRule> matched = new ArrayList<>();
        for (PackRule rule : useRequirements) {
            if (!matched.isEmpty() && rule.priority() < matched.get(0).priority()) break;
            if (rule.match().matchesTool(definitionId, materialIds, roles, materialTier, action)) {
                matched.add(rule);
            }
        }
        return winner(matched, (first, next) -> first.requirements().equals(next.requirements()));
    }

    /** Whether any reward rule is loaded at all, so the ordinary craft path can skip the lookup. */
    public boolean hasCraftRewardPolicies() {
        return !craftRewardPolicies.isEmpty();
    }

    /** The explicit rule about rewarding a craft of this result, or empty when there is none. */
    public Optional<PackRule> craftRewardPolicy(String providerId, ResourceLocation itemId) {
        List<PackRule> matched = new ArrayList<>();
        for (PackRule rule : craftRewardPolicies) {
            if (!matched.isEmpty() && rule.priority() < matched.get(0).priority()) break;
            if (rule.match().matchesResult(providerId, itemId)) {
                matched.add(rule);
            }
        }
        return winner(matched,
                (first, next) -> first.allowExtraOutput() == next.allowExtraOutput());
    }

    /**
     * The highest-priority match, unless any pair of winning-priority matches disagrees.
     *
     * @param agrees whether two rules say the same thing; repeated identical requirements are fine
     */
    private static Optional<PackRule> winner(List<PackRule> matched,
                                             java.util.function.BiPredicate<PackRule, PackRule> agrees) {
        if (matched.isEmpty()) return Optional.empty();
        PackRule first = matched.get(0);
        for (int index = 1; index < matched.size(); index++) {
            PackRule next = matched.get(index);
            if (agrees.test(first, next)) continue;
            LogOnce.warnOnce("pack-rule-conflict:" + first.id() + "|" + next.id(),
                    "[Runic Skills] pack rules {} and {} both match at priority {} and disagree; neither"
                    + " is applied. Give one of them a higher priority.", first.id(), next.id(), first.priority());
            return Optional.empty();
        }
        return Optional.of(first);
    }
}
