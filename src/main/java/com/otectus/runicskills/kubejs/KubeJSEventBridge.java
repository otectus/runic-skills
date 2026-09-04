package com.otectus.runicskills.kubejs;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.progression.ProgressionHooks;
import com.otectus.runicskills.common.progression.ProgressionService;
import com.otectus.runicskills.kubejs.events.CustomEvents;
import com.otectus.runicskills.kubejs.events.LevelUpEvent;
import com.otectus.runicskills.registry.skill.Skill;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The only class in the mod that turns a progression hook into a KubeJS post.
 *
 * <p>Loaded reflectively by {@code RunicSkills.tryLoadIntegration("kubejs", …)}, so on an
 * installation without KubeJS it is never named and its KubeJS types are never
 * resolved. Constructing it installs both {@link ProgressionHooks} vetoes; nothing outside this
 * package may reference this class by type, or that isolation is lost.
 *
 * <p><b>Two posts, two script types, one authority.</b> The server post runs inside
 * {@code ProgressionService} before the capability is written and its cancellation is final — that
 * is what makes a {@code server_scripts} gate hold against commands and hand-sent packets alike.
 * The client post runs in the Skills screen before the purchase packet is sent and only suppresses
 * that packet; it exists so a denied click gives immediate feedback, and a pack that only writes a
 * server script loses nothing by ignoring it.
 *
 * <p>A cancellation is either spelling: the event's own flag ({@code setCancelled}, {@code deny})
 * or KubeJS's {@code event.cancel()}, which unwinds through {@code EventExit} and comes back as an
 * {@code interruptFalse} result. The denial message prefers {@code deny}'s, falling back to the
 * value passed to {@code event.cancel('…')}.
 */
public class KubeJSEventBridge {

    public KubeJSEventBridge() {
        ProgressionHooks.serverLevelUpVeto = KubeJSEventBridge::postServer;
        ProgressionHooks.clientLevelUpVeto = KubeJSEventBridge::postClient;
        ProgressionHooks.kubejsBridgeInstalled = true;
        RunicSkills.getLOGGER().debug("Runic Skills KubeJS progression events enabled.");
    }

    private static ProgressionHooks.VetoResult postServer(ServerPlayer player, Skill skill,
                                                          int oldLevel, int newLevel,
                                                          ProgressionService.Cause cause) {
        LevelUpEvent event = new LevelUpEvent(player, skill, oldLevel, newLevel, cause, true);
        EventResult result = CustomEvents.SKILL_LEVELUP.post(ScriptType.SERVER, event);
        return toVeto(event, result);
    }

    private static ProgressionHooks.VetoResult postClient(Player player, Skill skill,
                                                          int oldLevel, int newLevel,
                                                          ProgressionService.Cause cause) {
        LevelUpEvent event = new LevelUpEvent(player, skill, oldLevel, newLevel, cause, false);
        EventResult result = CustomEvents.SKILL_LEVELUP.post(ScriptType.CLIENT, event);
        return toVeto(event, result);
    }

    private static ProgressionHooks.VetoResult toVeto(LevelUpEvent event, EventResult result) {
        boolean cancelled = event.isCancelled() || (result != null && result.interruptFalse());
        if (!cancelled) return ProgressionHooks.VetoResult.ALLOW;

        String message = event.getDenialMessage();
        if (message == null && result != null && result.value() instanceof String s) {
            message = s;
        }
        return ProgressionHooks.VetoResult.deny(message);
    }
}
