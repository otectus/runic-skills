package com.otectus.runicskills.common.rules;

import com.otectus.runicskills.common.equipment.EquipmentRole;
import com.otectus.runicskills.integration.lock.LockAction;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;

/**
 * One validated statement from a {@code runicskills/tconstruct_rules} file.
 *
 * <p>Immutable, and built only by {@link TConstructRulesLoader} once every field it names has been
 * resolved. §13.3 is explicit that a rule may only ever name resource ids, tags, numbers and the
 * handful of Runic vocabulary words below: there is no expression, no class name and no regular
 * expression here, so a rule can be evaluated by comparing values and nothing else.
 *
 * @param id               the pack-chosen id, which is also the merge key across resource packs
 * @param kind             which question this rule answers
 * @param priority         higher wins within a kind; equal-priority disagreement is an error
 * @param match            what the rule is about
 * @param requirements     skill name to level, for {@link Kind#USE_REQUIREMENT}; empty otherwise
 * @param allowExtraOutput for {@link Kind#CRAFT_REWARD_POLICY}: whether a bonus copy may be paid
 */
public record PackRule(ResourceLocation id, Kind kind, int priority, Match match,
                       Map<String, Integer> requirements, boolean allowExtraOutput) {

    public PackRule {
        requirements = Map.copyOf(requirements);
    }

    /** The two questions a pack may answer. Anything else in a file is rejected by name. */
    public enum Kind {
        /** What a player must have learned before they may use the matched equipment. */
        USE_REQUIREMENT("use_requirement"),
        /** Whether a craft producing the matched item may be paid a bonus copy. */
        CRAFT_REWARD_POLICY("craft_reward_policy");

        public final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    /**
     * How a rule composes with the automatic material profile.
     *
     * <p>Only one value is accepted in schema 1, and it is the one §13.3's example uses. The field
     * exists rather than being implied because "this rule replaces the generated one" is a promise
     * a pack should have to make out loud — and because a later schema adding a second composition
     * must be able to tell an old file that meant the first one from a file that never said.
     */
    public static final String COMPOSITION_REPLACE_AUTOMATIC = "replace_automatic";

    /**
     * The selectors a rule matched on, each empty when the pack did not narrow by it.
     *
     * <p>Every populated selector must match — they are ANDed — and a selector matches when any one
     * of its values does. That is the only combination that lets a pack write "a mining tool made of
     * a tier 3 material" without also writing the cross product of every such tool by hand.
     *
     * @param equipmentProvider which adapter must have claimed the item, or {@code null} for any
     */
    public record Match(Set<ResourceLocation> definitions, Set<ResourceLocation> materials,
                        Set<EquipmentRole> roles, Set<Integer> materialTiers,
                        Set<LockAction> actions, String equipmentProvider,
                        Set<ResourceLocation> items) {

        public Match {
            definitions = Set.copyOf(definitions);
            materials = Set.copyOf(materials);
            roles = Set.copyOf(roles);
            materialTiers = Set.copyOf(materialTiers);
            actions = Set.copyOf(actions);
            items = Set.copyOf(items);
        }

        /** A rule that names nothing, and therefore matches every candidate of its kind. */
        public static Match any() {
            return new Match(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), null, Set.of());
        }

        /** Whether this selector describes the tool in front of the player. */
        public boolean matchesTool(ResourceLocation definitionId, Set<ResourceLocation> materialIds,
                                   Set<EquipmentRole> toolRoles, int materialTier, LockAction action) {
            if (!definitions.isEmpty() && !definitions.contains(definitionId)) return false;
            if (!materials.isEmpty() && materialIds.stream().noneMatch(materials::contains)) return false;
            if (!roles.isEmpty() && toolRoles.stream().noneMatch(roles::contains)) return false;
            // A tier of -1 is "could not be determined", which a tier selector must not match:
            // §7.3 forbids an undetermined material producing a lock (see the resolver's own note).
            if (!materialTiers.isEmpty() && (materialTier < 0 || !materialTiers.contains(materialTier))) {
                return false;
            }
            return actions.isEmpty() || actions.contains(action);
        }

        /** Whether this selector describes the result of a craft. */
        public boolean matchesResult(String providerId, ResourceLocation itemId) {
            if (equipmentProvider != null && !equipmentProvider.equals(providerId)) return false;
            return items.isEmpty() || items.contains(itemId);
        }
    }
}
