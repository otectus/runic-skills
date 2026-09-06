package com.otectus.runicskills.kubejs.events;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public interface CustomEvents {
    EventGroup GROUP = EventGroup.of("RunicSkillsEvents");

    /**
     * Posted on the server before a skill level is written, and again on the client before the
     * purchase packet is sent.
     *
     * <p>{@code common} rather than {@code client}: registered client-only, a
     * {@code server_scripts} listener was never called at all, which is the whole of issue #1.
     * {@code hasResult()} is what makes {@code event.cancel()} legal in a script — without it
     * KubeJS refuses the call, so the surface's documented cancellation spelling would throw.
     */
    EventHandler SKILL_LEVELUP = GROUP.common("skillLevelUp", () -> LevelUpEvent.class).hasResult();

    /**
     * Posted on the server after a native operation is known and before this mod consumes, pays or
     * services anything for it (§14.5).
     *
     * <p>{@code server} rather than {@code common}: a pre-commit gate has one authority, and a
     * client-side copy of it could only ever disagree with the server that already decided. Same
     * {@code hasResult()} as the level-up gate, because a script's {@code event.cancel()} is one of
     * the three legal ways to deny.
     */
    EventHandler TINKER_OPERATION_CHECK =
            GROUP.server("tinkerOperationCheck", () -> TinkerOperationCheckEvent.class).hasResult();

    /**
     * Posted once per committed root operation, after the fact (§14.5).
     *
     * <p>No {@code hasResult()}: there is nothing left to cancel, and a surface that accepted a
     * cancellation here would be promising a rollback this mod does not do.
     */
    EventHandler TINKER_OPERATION_COMPLETED =
            GROUP.server("tinkerOperationCompleted", () -> TinkerOperationCompletedEvent.class);

    /** Posted after a real levelling add-on transition, as an observation (§14.5). */
    EventHandler TINKER_TOOL_LEVEL_CHANGED =
            GROUP.server("tinkerToolLevelChanged", () -> TinkerToolLevelChangedEvent.class);
}
