package com.otectus.runicskills.kubejs.events;

import com.otectus.runicskills.common.scripting.TinkerScriptHooks;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * What a {@code RunicSkillsEvents.tinkerOperationCheck} listener sees.
 *
 * <p>Posted after a valid native operation is known and <b>before</b> this mod consumes, pays or
 * services anything (§14.5). {@code event.deny('…')} refuses the Runic half — a bonus copy, a
 * repair top-up, the keystone service — and refuses it before anything is spent. It does not and
 * cannot cancel the native craft: the station has already accepted the take, and vetoing native
 * crafting from a Runic observer is the failure §14.5 names outright.
 *
 * <pre>{@code
 * RunicSkillsEvents.tinkerOperationCheck(event => {
 *   if (event.kind !== 'keystone_service') return
 *   if (!event.hasAdvancement('minecraft:story/enter_the_nether')) {
 *     event.deny('Visit the Nether before performing Keystone work.')
 *   }
 * })
 * }</pre>
 *
 * <p>Three cancellation spellings, for the reason {@code LevelUpEvent} lists: KubeJS's own
 * {@code event.cancel()}, the {@code setCancelled}/{@code setCanceled} pair scripts already
 * contain, and {@code deny(reason)} which is the same veto with something to tell the player.
 *
 * <p>A script that throws here denies as well. That is decided in {@code TinkerScriptHooks}, not
 * in this class, so the same rule holds for any future gate on the same hook.
 */
public class TinkerOperationCheckEvent extends TinkerEventJS {

    private final TinkerScriptHooks.Operation operation;

    private boolean cancelled = false;
    @Nullable
    private String denialMessage = null;

    public TinkerOperationCheckEvent(ServerPlayer player, TinkerScriptHooks.Operation operation) {
        super(player);
        this.operation = operation;
    }

    /** The operation, lowercase: {@code assembly}, {@code repair}, {@code part_swap}, {@code modify}, {@code keystone_service}, {@code unknown}. */
    public String getKind() {
        return operation.kind();
    }

    /** The native recipe id, or an empty string when the operation has none. */
    public String getRecipeId() {
        return operation.recipeId() == null ? "" : operation.recipeId().toString();
    }

    /** The item the operation produces, as an id, or an empty string when there is none. */
    public String getItem() {
        return operation.resultItem() == null ? "" : operation.resultItem().toString();
    }

    /** How many of {@link #getItem()} the operation produces. */
    public int getCount() {
        return operation.resultCount();
    }

    /** Item id to total count consumed, e.g. {@code event.inputs['tconstruct:pickaxe']}. */
    public Map<String, Integer> getInputs() {
        return operation.inputs();
    }

    /** The root action this belongs to. The completion event carries the same number. */
    public long getOperationId() {
        return operation.operationId();
    }

    public boolean getCancelled() {
        return cancelled;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean getCanceled() {
        return cancelled;
    }

    public boolean isCanceled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void setCanceled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /** Refuses the Runic service and tells the player why. The native operation is untouched. */
    public void deny(@Nullable String message) {
        this.cancelled = true;
        this.denialMessage = message;
    }

    @Nullable
    public String getDenialMessage() {
        return denialMessage;
    }
}
