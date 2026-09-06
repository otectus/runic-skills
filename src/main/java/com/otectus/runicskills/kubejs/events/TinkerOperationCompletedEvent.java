package com.otectus.runicskills.kubejs.events;

import com.otectus.runicskills.common.scripting.TinkerScriptHooks;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * What a {@code RunicSkillsEvents.tinkerOperationCompleted} listener sees.
 *
 * <p>Read-only, one emission per root operation, posted after the take has committed (§14.5). There
 * is deliberately no cancellation on this class: nothing a listener does can un-commit an
 * operation, and a surface that let a script try would invite the retry-or-duplicate that section
 * forbids. A script that throws here is logged and the operation stands.
 *
 * <pre>{@code
 * RunicSkillsEvents.tinkerOperationCompleted(event => {
 *   console.info(`${event.player.username} ${event.kind} ${event.item}, `
 *     + `${event.nativeRestored} restored, ${event.bonusCopies} bonus`)
 * })
 * }</pre>
 */
public class TinkerOperationCompletedEvent extends TinkerEventJS {

    private final TinkerScriptHooks.Operation operation;

    public TinkerOperationCompletedEvent(ServerPlayer player, TinkerScriptHooks.Operation operation) {
        super(player);
        this.operation = operation;
    }

    /** The operation, in the same vocabulary the check event uses. */
    public String getKind() {
        return operation.kind();
    }

    /** The native recipe id, or an empty string when the operation has none. */
    public String getRecipeId() {
        return operation.recipeId() == null ? "" : operation.recipeId().toString();
    }

    /** The item the operation produced, as an id. */
    public String getItem() {
        return operation.resultItem() == null ? "" : operation.resultItem().toString();
    }

    /** How many of {@link #getItem()} it produced. */
    public int getCount() {
        return operation.resultCount();
    }

    /** Item id to total count consumed, as the station held them at commit time. */
    public Map<String, Integer> getInputs() {
        return operation.inputs();
    }

    /** The root action this belongs to; the same number the check event carried. */
    public long getOperationId() {
        return operation.operationId();
    }

    /** Durability the <em>native</em> operation restored, before anything this mod added. */
    public int getNativeRestored() {
        return operation.nativeRestored();
    }

    /** Extra outputs this mod paid for the operation, across every perk together. */
    public int getBonusCopies() {
        return operation.runicBonusCopies();
    }
}
