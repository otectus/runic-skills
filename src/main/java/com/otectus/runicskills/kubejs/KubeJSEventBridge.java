package com.otectus.runicskills.kubejs;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.progression.ProgressionHooks;
import com.otectus.runicskills.common.progression.ProgressionService;
import com.otectus.runicskills.common.scripting.TinkerScriptHooks;
import com.otectus.runicskills.kubejs.events.CustomEvents;
import com.otectus.runicskills.kubejs.events.LevelUpEvent;
import com.otectus.runicskills.kubejs.events.TinkerOperationCheckEvent;
import com.otectus.runicskills.kubejs.events.TinkerOperationCompletedEvent;
import com.otectus.runicskills.kubejs.events.TinkerToolLevelChangedEvent;
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
        // The three tinkering events (§14.5). Installed here rather than from the Tinkers'
        // bootstrap: the surface has to exist on a server that has no Tinkers' at all, so that a
        // pack shared between servers loads its scripts either way and simply sees nothing happen.
        TinkerScriptHooks.operationGate = KubeJSEventBridge::postOperationCheck;
        TinkerScriptHooks.operationObserver = KubeJSEventBridge::postOperationCompleted;
        TinkerScriptHooks.toolLevelObserver = KubeJSEventBridge::postToolLevelChanged;
        TinkerScriptHooks.kubejsTinkerBridgeInstalled = true;
        RunicSkills.getLOGGER().debug("Runic Skills KubeJS progression and tinkering events enabled.");
    }

    /**
     * The pre-commit gate.
     *
     * <p>Exceptions are deliberately <em>not</em> caught here: {@code TinkerScriptHooks} turns a
     * throw into a denial, which is what §14.5 asks for, and catching it in the bridge would turn
     * it into an allow before the hook could see it.
     */
    private static TinkerScriptHooks.Veto postOperationCheck(ServerPlayer player,
                                                             TinkerScriptHooks.Operation operation) {
        TinkerOperationCheckEvent event = new TinkerOperationCheckEvent(player, operation);
        EventResult result = CustomEvents.TINKER_OPERATION_CHECK.post(ScriptType.SERVER, event);
        boolean denied = event.isCancelled() || (result != null && result.interruptFalse());
        if (!denied) return TinkerScriptHooks.Veto.ALLOW;
        String message = event.getDenialMessage();
        if (message == null && result != null && result.value() instanceof String s) message = s;
        return TinkerScriptHooks.Veto.deny(message);
    }

    private static void postOperationCompleted(ServerPlayer player,
                                               TinkerScriptHooks.Operation operation) {
        CustomEvents.TINKER_OPERATION_COMPLETED.post(ScriptType.SERVER,
                new TinkerOperationCompletedEvent(player, operation));
    }

    private static void postToolLevelChanged(ServerPlayer player,
                                             TinkerScriptHooks.ToolLevelChange change) {
        CustomEvents.TINKER_TOOL_LEVEL_CHANGED.post(ScriptType.SERVER,
                new TinkerToolLevelChangedEvent(player, change));
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
