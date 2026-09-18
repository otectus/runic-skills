package com.otectus.runicskills.integration.apprenticecodex;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.IronsSpellbooksIntegration;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import jp.aquafactory.apprenticecodex.block.spelldispenser.SpellDispenserSpellValidator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The spell-dispenser automation policy (spec §6.5), and the only thing the dispenser mixin calls.
 *
 * <p>Loaded exclusively from behind the Codex presence check — {@link CodexBootstrap} instantiates
 * it, and {@code RunicSkillsMixinPlugin} refuses to apply the mixin that calls it unless the mod is
 * installed and the integration is switched on. Nothing outside this package names a
 * {@code jp.aquafactory} type.
 *
 * <h2>Why a mixin at all</h2>
 *
 * <p>{@code SpellDispenserCastHelper} does not call {@code AbstractSpell.attemptInitiateCast}. It
 * reimplements the cast loop — {@code checkPreCastConditions}, {@code onServerPreCast},
 * {@code onCast}, {@code MagicData.setMana} — so neither {@code SpellPreCastEvent} nor
 * {@code SpellOnCastEvent} is ever posted for an autonomous cast and no Forge event is posted in
 * their place. The ordinary player pre-cast subscription is therefore not coverage of this path; it
 * is coverage of every path except this one.
 *
 * <h2>The two policies</h2>
 *
 * <ul>
 *   <li><b>DEVICE</b> (default). The player actions on the device — placing it, opening it,
 *       configuring it — carry the gate, and they are ordinary block interactions gated by the
 *       reviewed {@code INTERACT_BLOCK} rule. The machine's own casting rules then run untouched
 *       and the execution is marked {@code AUTOMATION_EXEMPT}. An empty FakePlayer capability is
 *       never read as the owner's skill record, and no skill reward or combat credit is granted for
 *       a machine's work.</li>
 *   <li><b>ONLINE_OWNER</b>. The cast is attributed to a verified owner who must be online, and is
 *       checked against exactly the same resolver a hand cast goes through. An owner that cannot be
 *       resolved, or who is offline, <em>pauses</em> the device with the native failure diagnostic
 *       rather than silently allowing it or silently denying it, and nothing loads offline player
 *       data to answer the question.</li>
 * </ul>
 *
 * <p>The refusal is taken at the head of the widest {@code tryCast} /
 * {@code tryStartContinuousCast} overload, which every other overload funnels into, and therefore
 * before the helper has resolved a caster proxy, touched {@code MagicData} or consumed anything.
 * There is nothing to refund because nothing has been spent — which is the only kind of gate §6.5
 * accepts, as opposed to cancelling a {@code void} callback after the fact and calling it one.
 */
public final class CodexAutomation {

    private static final Logger LOGGER = LoggerFactory.getLogger("runicskills/apprentice-codex");

    /**
     * The placeholder profile Codex uses for a dispenser with no real owner.
     *
     * <p>Recomputed from the same seed string rather than read off the helper's private constant:
     * the value is a pure function of that string, so this cannot drift without the seed changing,
     * and reflecting into a private field to learn a constant would be the more fragile of the two.
     */
    private static final UUID OWNER_OPTIONAL_FALLBACK = UUID.nameUUIDFromBytes(
            "apprenticecodex:spell_dispenser_owner_optional".getBytes(StandardCharsets.UTF_8));

    /** Executions this policy let through as machine work, for the diagnostic line. */
    private static final AtomicLong AUTOMATION_EXEMPT = new AtomicLong();

    /** Executions the strict policy refused. */
    private static final AtomicLong REFUSED = new AtomicLong();

    /** Game time of the last logged pause, so a stuck dispenser cannot flood the log. */
    private static volatile long lastPauseLog = Long.MIN_VALUE;

    private static final long PAUSE_LOG_INTERVAL_TICKS = 200;

    private CodexAutomation() {
    }

    /** Machine executions allowed as automation, since server start. */
    public static long automationExemptCount() {
        return AUTOMATION_EXEMPT.get();
    }

    /** Machine executions refused by the strict policy, since server start. */
    public static long refusedCount() {
        return REFUSED.get();
    }

    /** Test seam: forget the counters between cases. */
    public static void resetCounters() {
        AUTOMATION_EXEMPT.set(0);
        REFUSED.set(0);
    }

    /** The policy in force, normalised. Unknown values fall back to {@code DEVICE}. */
    public static String policy() {
        String configured = HandlerCommonConfig.HANDLER.instance().codexAutomationGatePolicy;
        if (configured == null) return "DEVICE";
        String value = configured.trim().toUpperCase(Locale.ROOT);
        return "ONLINE_OWNER".equals(value) ? "ONLINE_OWNER" : "DEVICE";
    }

    /**
     * Whether one autonomous dispenser cast may proceed.
     *
     * <p>Returns {@code true} for everything it cannot decide. An integration that is switched off,
     * a validation result it cannot read, a spell it cannot identify: none of those is evidence that
     * the cast should be refused, and refusing on a read failure would break a working machine to
     * report a bug in this class.
     *
     * @param level      the server level the device is in
     * @param owner      the device's owner profile, or Codex's placeholder when it has none
     * @param validation the resolved spell for this execution
     */
    public static boolean allows(ServerLevel level, GameProfile owner,
                                 SpellDispenserSpellValidator.ValidationResult validation) {
        try {
            if (!HandlerCommonConfig.HANDLER.instance().enableApprenticeCodexIntegration) return true;
            if (!"ONLINE_OWNER".equals(policy())) {
                // DEVICE: the device's own rules decide. Recorded, never charged to a person.
                AUTOMATION_EXEMPT.incrementAndGet();
                return true;
            }
            if (level == null) return true;
            SpellData spell = validation == null ? null : validation.spellData();
            AbstractSpell definition = spell == null ? null : spell.getSpell();
            String spellId = definition == null ? null : definition.getSpellId();
            if (spellId == null || spellId.isBlank()) return true;

            if (!verifiedOwner(owner)) {
                pause(level, "the device has no verified owner", spellId);
                REFUSED.incrementAndGet();
                return false;
            }
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(owner.getId());
            if (online == null) {
                // Spec §6.5: pause with a machine diagnostic. Do NOT synchronously load offline
                // player data to answer this, and do not treat an absent record as permission.
                pause(level, "its owner " + owner.getName() + " is offline", spellId);
                REFUSED.incrementAndGet();
                return false;
            }
            boolean allowed = IronsSpellbooksIntegration.allowsCast(online, spellId,
                    spell.getLevel(), false);
            if (!allowed) {
                pause(level, "its owner " + owner.getName()
                        + " does not meet the requirement for this spell", spellId);
                REFUSED.incrementAndGet();
                return false;
            }
            AUTOMATION_EXEMPT.incrementAndGet();
            return true;
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("The Apprentice's Codex automation policy could not be evaluated; the "
                    + "device's own rules decide this execution", e);
            return true;
        }
    }

    /**
     * Whether a profile identifies a real person.
     *
     * <p>Codex hands the helper a synthetic profile when the dispenser has no owner. Under the
     * strict policy that is precisely the case §6.5 says to pause on, so the placeholder is
     * rejected by value rather than being mistaken for a player who happens to be offline.
     */
    private static boolean verifiedOwner(GameProfile owner) {
        return owner != null && owner.getId() != null && !OWNER_OPTIONAL_FALLBACK.equals(owner.getId())
                && owner.getName() != null && !owner.getName().isBlank();
    }

    private static void pause(ServerLevel level, String reason, String spellId) {
        long now = level.getGameTime();
        if (now - lastPauseLog < PAUSE_LOG_INTERVAL_TICKS && now >= lastPauseLog) return;
        lastPauseLog = now;
        LOGGER.info("[Runic Skills] A spell dispenser is paused under codexAutomationGatePolicy="
                + "ONLINE_OWNER: {} ({}). The device keeps its contents and spends nothing.",
                reason, spellId);
    }

    /** One line for the compatibility diagnostic and the audit export. */
    public static String describe() {
        return "policy=" + policy() + ", automation_exempt=" + AUTOMATION_EXEMPT.get()
                + ", refused=" + REFUSED.get();
    }
}
