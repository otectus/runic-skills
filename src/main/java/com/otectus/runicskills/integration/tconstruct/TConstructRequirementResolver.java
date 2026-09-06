package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.common.equipment.RequirementDecision;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.integration.lock.LockAction;
import com.otectus.runicskills.integration.lock.StackLockProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a player must have learned to use one particular Tinkers' tool.
 *
 * <p>A registry-id lock cannot express this at all. Every Tinkers' pickaxe in the game is
 * {@code tconstruct:pickaxe}, so an id rule either locks a wooden one and a manyullyn one alike or
 * locks neither — which is why spec §7 asks for a stack-aware resolver and why this is a
 * {@link StackLockProvider} rather than another entry in the lock table.
 *
 * <p><b>Off by default.</b> {@code enableTConstructLockItems} defaults to false. §7.1 is explicit
 * about why: turning material-tier locks on in an existing world can disable equipment players
 * already own and rely on, so it is an opt-in progression preset rather than something a version
 * bump does to a server. With it off, this provider declines every automatic verdict and the
 * install behaves exactly as it did before Tinkers' was recognised.
 *
 * <h2>Precedence (§7.2)</h2>
 * <ol>
 *   <li>An explicit pack rule for this exact definition and materials — {@link TConstructRuleSource},
 *       loaded in stage S6, {@link TConstructRuleSource#EMPTY} until then.</li>
 *   <li>An existing configured lock on the item id. This provider <em>declines</em> when one
 *       exists, which hands the decision back to the unchanged id path: §7.2 says a manual override
 *       replaces the generated rule rather than being maximised with it, and declining is how a
 *       provider says "not mine to answer".</li>
 *   <li>The optional automatic material profile below, when it is enabled.</li>
 *   <li>Nothing matched, so the item is allowed.</li>
 * </ol>
 *
 * <p><b>An unknown material is not a punishment.</b> §7.3: unrecognised material data must not
 * crash, must not mutate the item, and must not produce a maximum-level lock. It produces an allow
 * carrying an "undetermined" fact, so an inspection can say the requirement could not be worked out
 * instead of a player finding out by being refused.
 */
public final class TConstructRequirementResolver implements StackLockProvider {

    /**
     * The §7.3 table, at the stock cap of 32. Index is the native material tier.
     *
     * <p>Index 0 is the starting materials — Tinkers' calls wood tier 0 — and asks for nothing.
     * Tier 5 and above is deliberately absent: the table's last row says an explicit rule is
     * required and no extra lock is inferred, which is what {@link #requirementFor} returning empty
     * above the table means.
     */
    private static final int[] TINKERING_AT_CAP_32 = {0, 1, 8, 16, 24};

    private static final int[] FUNCTIONAL_AT_CAP_32 = {0, 1, 4, 8, 16};

    /** The cap the table above is written against, so a server that changes it scales rather than breaks. */
    private static final int TABLE_CAP = 32;

    private final TConstructRuleSource rules;

    TConstructRequirementResolver(TConstructRuleSource rules) {
        this.rules = rules == null ? TConstructRuleSource.EMPTY : rules;
    }

    @Override
    public String id() {
        return "tconstruct";
    }

    @Override
    public Optional<RequirementDecision> resolve(ServerPlayer player, ItemStack stack, LockAction action) {
        if (!TConstructEquipmentAdapter.isNativeTool(stack)) return Optional.empty();
        // §7.4: never deny inventory removal, storage or crafting. Those actions are not a use, and
        // a lock that stopped a player picking their own tool out of a chest would be a trap.
        if (action == LockAction.TAKE || action == LockAction.CRAFT) return Optional.empty();

        ToolStack tool = ToolStack.from(stack);
        ResourceLocation definitionId = tool.getDefinition().getId();
        List<ResourceLocation> materialIds = new ArrayList<>();
        for (MaterialVariant variant : tool.getMaterials()) {
            materialIds.add(variant.getId());
        }

        Optional<RequirementDecision> explicit = rules.rule(definitionId, materialIds, action);
        if (explicit.isPresent()) return explicit.map(decision -> evaluated(player, stack, decision));

        // An existing id lock outranks the automatic profile, and the id path already enforces it.
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null) {
            var configured = HandlerSkill.getValue(itemId.toString());
            if (configured != null && !configured.isEmpty()) return Optional.empty();
        }

        if (!HandlerCommonConfig.HANDLER.instance().enableTConstructLockItems) return Optional.empty();

        return Optional.of(automaticProfile(player, stack, tool, action));
    }

    /**
     * A pack rule's requirements, decided against the player in front of the tool.
     *
     * <p>{@link TConstructRuleSource#rule} is not given the player — a rule is a statement about a
     * tool, not about a person — so a rule source can only ever report what a pack requires. The
     * comparison is the same one the automatic profile makes, made here because this is the first
     * point at which both the rule and the player are in hand. A rule that requires nothing is an
     * explicit allow, which is how a pack exempts a tool the automatic profile would have locked.
     */
    private RequirementDecision evaluated(ServerPlayer player, ItemStack stack,
                                          RequirementDecision decision) {
        if (decision == null) return RequirementDecision.allow();
        Map<String, Integer> required = decision.requirements();
        if (required.isEmpty()) return decision;
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            if (levelOf(player, entry.getKey()) < entry.getValue()) {
                return new RequirementDecision(false, required, decision.matchedRuleIds(),
                        decision.unsupportedFacts(),
                        decision.reason() != null ? decision.reason() : reason(stack, required));
            }
        }
        return new RequirementDecision(true, required, decision.matchedRuleIds(),
                decision.unsupportedFacts(), null);
    }

    /**
     * The §7.3 material-tier profile for this tool, as a verdict.
     *
     * <p>The tier used is the highest across the tool's materials. §7.3 asks for the highest
     * <em>relevant functional</em> tier and excludes cosmetic embellishments — in 3.11 an
     * embellishment is a modifier rather than a material part, so every entry in
     * {@code getMaterials()} is already a functional part and the maximum over them is that number.
     */
    private RequirementDecision automaticProfile(ServerPlayer player, ItemStack stack,
                                                 ToolStack tool, LockAction action) {
        int tier = -1;
        boolean undetermined = !MaterialRegistry.isFullyLoaded();
        for (MaterialVariant variant : tool.getMaterials()) {
            if (variant.isUnknown() || variant.isEmpty()) {
                undetermined = true;
                continue;
            }
            tier = Math.max(tier, variant.get().getTier());
        }

        // -1, not 0: Tinkers' numbers its tiers from zero, so wood is a determined tier that asks
        // for nothing. Treating it as "could not be determined" would report the most common tool
        // in the game as unrecognised.
        if (undetermined || tier < 0) {
            return new RequirementDecision(true, Map.of(), List.of(id() + ":auto_material"),
                    List.of("material tier could not be determined"), null);
        }

        Map<String, Integer> required = new LinkedHashMap<>();
        requirementFor(TINKERING_AT_CAP_32, tier)
                .ifPresent(level -> required.put("tinkering", level));
        String functional = functionalSkill(stack, action);
        if (functional != null) {
            requirementFor(FUNCTIONAL_AT_CAP_32, tier)
                    // §7.2: within an automatic profile, combine same-skill requirements by
                    // maximum. merge does that for the case where a tool's functional skill is
                    // Tinkering itself, which no current mapping produces but a later one might.
                    .ifPresent(level -> required.merge(functional, level, Math::max));
        }
        if (required.isEmpty()) {
            return new RequirementDecision(true, Map.of(), List.of(id() + ":auto_material"),
                    List.of(), null);
        }

        boolean allowed = true;
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            if (levelOf(player, entry.getKey()) < entry.getValue()) {
                allowed = false;
                break;
            }
        }
        return new RequirementDecision(allowed, required, List.of(id() + ":auto_material"),
                List.of(), allowed ? null : reason(stack, required));
    }

    /**
     * The level this tier demands, scaled from the stock cap of 32 to the server's own cap.
     *
     * <p>Integer ceiling and a cap, per §7.3: a server that halves {@code skillMaxLevel} halves the
     * thresholds rather than making every late-game tool permanently unreachable, and one that
     * raises it does not leave the whole table sitting at the bottom of the range.
     */
    static Optional<Integer> requirementFor(int[] table, int tier) {
        // Tier 0 (wood and its peers) sits at index 0, whose entry is zero: the starting materials
        // ask for nothing. Above the table, §7.3's last row says an explicit rule is required and no
        // extra lock is inferred, which is what an empty answer means here.
        if (tier < 0 || tier >= table.length) return Optional.empty();
        int base = table[tier];
        if (base <= 0) return Optional.empty();
        int cap = Math.max(2, HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
        int scaled = (int) Math.ceil(base * (double) cap / TABLE_CAP);
        return Optional.of(Math.max(1, Math.min(cap, scaled)));
    }

    /**
     * Which functional skill this action needs on this tool, or null for none.
     *
     * <p>§7.3: "hybrid equipment resolves against the action being performed; it does not require
     * all possible skills just to sit in an inventory". So a tool that is both a digger and a melee
     * weapon asks for Strength when it is swung and for nothing extra when it is merely held —
     * requiring both would be the punishment that sentence exists to forbid.
     */
    private static String functionalSkill(ItemStack stack, LockAction action) {
        boolean armor = stack.is(TinkerTags.Items.ARMOR);
        boolean ranged = stack.is(TinkerTags.Items.RANGED);
        // Tinkers' already distinguishes "can be swung" from "is meant to be swung": a pickaxe is in
        // the melee tag because hitting something with it does damage, and in the harvest PRIMARY
        // tag because mining is what it is for. Reading the primary tags is how "resolve against the
        // action" gets an answer for a generic use rather than falling back to nothing.
        boolean harvestPrimary = stack.is(TinkerTags.Items.HARVEST_PRIMARY);
        boolean meleePrimary = stack.is(TinkerTags.Items.MELEE_PRIMARY);

        return switch (action) {
            case ATTACK -> stack.is(TinkerTags.Items.MELEE_WEAPON) ? "strength" : null;
            case EQUIP -> armor ? "constitution" : null;
            case USE -> {
                if (harvestPrimary && !meleePrimary) yield "endurance";
                if (meleePrimary && !harvestPrimary) yield "strength";
                if (!harvestPrimary && !meleePrimary) {
                    if (ranged) yield "dexterity";
                    if (armor) yield "constitution";
                }
                // Primary at both: a genuinely hybrid tool asks for nothing extra merely to be held.
                yield null;
            }
            default -> null;
        };
    }

    private static int levelOf(ServerPlayer player, String skillName) {
        var capability = com.otectus.runicskills.common.capability.SkillCapability.get(player);
        return capability == null ? 1 : capability.getSkillLevel(skillName);
    }

    /** The sentence the player is shown, naming the tool and everything it asks for. */
    private static Component reason(ItemStack stack, Map<String, Integer> required) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            if (text.length() > 0) text.append(", ");
            text.append(entry.getKey()).append(' ').append(entry.getValue());
        }
        return Component.translatable("message.runicskills.tconstruct.material_locked",
                stack.getHoverName(), text.toString());
    }
}
