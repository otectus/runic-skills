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
}
