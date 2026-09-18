package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.handler.HandlerCommonConfig;

import java.util.List;

/**
 * An integration that publishes reviewed, action-scoped rules rather than untyped id locks.
 *
 * <p>{@link LockItemProvider} can only say "this id requires these levels", with no notion of the
 * action being attempted. That is enough for a sword and wrong for a workstation: one untyped entry
 * for {@code apprenticecodex:spellcaster_workbench} would refuse to let an underlevelled player
 * <em>break</em> the block they cannot operate, which §12.1 forbids in as many words. A typed rule
 * names the actions it is about and nothing else.
 *
 * <p>The layer sits between the authored rules and the untyped generators: a reviewed built-in
 * profile ({@link GateSource#CURATED_PROFILE}) or an adapter reading native metadata
 * ({@link GateSource#NATIVE_ADAPTER}) outranks a keyword generator and loses to anything a person
 * wrote. Each rule carries its own {@link GateRule#source()}, so the position is a property of the
 * rule rather than of the loop that collected it.
 *
 * <p>Implementations are registered from {@link LockProviderRegistry}'s static initialiser, which
 * runs on installations that have none of the mods involved. So an implementation must be loadable
 * without its target mod: name no optional type in its own constant pool, and answer
 * {@link #isActive} from {@code ModList} and the configuration.
 */
public interface TypedGateProvider {

    /** Stable identifier, used in the audit export and in ownership claims. */
    String id();

    /** Whether this provider's rules apply at all: its mod is present and its setting is on. */
    boolean isActive(HandlerCommonConfig cfg);

    /**
     * The rules this provider publishes for the current registries, or an empty list.
     *
     * <p>Called once per rule build, on the server thread, after registries and tags are available.
     * A provider that throws costs its own rules and nothing else.
     */
    List<GateRule> generateGateRules();
}
