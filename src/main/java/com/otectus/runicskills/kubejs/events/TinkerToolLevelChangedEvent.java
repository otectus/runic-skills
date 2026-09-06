package com.otectus.runicskills.kubejs.events;

import com.otectus.runicskills.common.scripting.TinkerScriptHooks;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a {@code RunicSkillsEvents.tinkerToolLevelChanged} listener sees.
 *
 * <p>Read-only, posted after a real levelling add-on transition (§14.5). The award reported is the
 * one the add-on applied, including anything a Runic perk added to it, and observing it grants
 * nothing: §12.3 forbids a second award, and this surface is an observation of the add-on's own
 * transition rather than a second source of experience.
 *
 * <pre>{@code
 * RunicSkillsEvents.tinkerToolLevelChanged(event => {
 *   if (event.newLevel >= 10) event.player.tell(`${event.item} reached level ${event.newLevel}`)
 * })
 * }</pre>
 */
public class TinkerToolLevelChangedEvent extends TinkerEventJS {

    private final TinkerScriptHooks.ToolLevelChange change;

    public TinkerToolLevelChangedEvent(ServerPlayer player, TinkerScriptHooks.ToolLevelChange change) {
        super(player);
        this.change = change;
    }

    /** The tool that levelled, as an item id. */
    public String getItem() {
        return change.toolItem() == null ? "" : change.toolItem().toString();
    }

    public int getOldLevel() {
        return change.oldLevel();
    }

    public int getNewLevel() {
        return change.newLevel();
    }

    /** What earned it, lowercase; {@code experience} for an ordinary add-on award. */
    public String getSource() {
        return change.source();
    }

    /** The experience the add-on applied for this award. */
    public int getAward() {
        return change.award();
    }
}
