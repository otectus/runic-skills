package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.common.equipment.RequirementDecision;
import com.otectus.runicskills.integration.lock.LockAction;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Where an explicit pack rule about one Tinkers' tool comes from.
 *
 * <p>Spec §7.2 puts an explicit rule for a specific tool definition and material above everything
 * else, including the existing item-id locks, so the resolver has to consult a rule source before
 * it consults anything. Stage S6 loads those rules from {@code runicskills/tconstruct_rules/*.json};
 * until then the only implementation is {@link #EMPTY}, which declines everything.
 *
 * <p>An interface with an empty implementation rather than a {@code TODO}: the precedence order is
 * the part that is easy to get wrong and hard to change later, so it is written and exercised now,
 * with the loader dropped in behind it. A resolver that grew rule loading and precedence together
 * would have neither tested until both existed.
 */
public interface TConstructRuleSource {

    /** Declines every question. The rule source on every install until stage S6 ships the loader. */
    TConstructRuleSource EMPTY = (definitionId, materialIds, action) -> Optional.empty();

    /**
     * The explicit rule for this exact tool, or empty when a pack has written none.
     *
     * @param definitionId the native tool definition, e.g. {@code tconstruct:pickaxe}
     * @param materialIds  the tool's material ids in part order, never null and possibly empty
     * @param action       what the player is attempting, so a rule may speak about one action only
     */
    Optional<RequirementDecision> rule(ResourceLocation definitionId,
                                       java.util.List<ResourceLocation> materialIds,
                                       LockAction action);
}
