package com.otectus.runicskills.integration.apprenticecodex;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.lock.ApprenticeCodexLockProvider;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import jp.aquafactory.apprenticecodex.item.ManaBypassSpellItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Apprentice's Codex entry point, and the only class {@code RunicSkills} names for it.
 *
 * <p>Loaded through {@code RunicSkills.tryLoadIntegration("apprenticecodex", …)}, so every
 * {@code jp.aquafactory} type this package references is resolved only on an installation that has
 * the mod. The gates themselves do not live here: they are published by
 * {@link ApprenticeCodexLockProvider}, which is registered from the always-loaded lock registry and
 * names no Codex type at all. This class exists for the three things that genuinely need the mod
 * present — reporting what was detected, answering the compatibility question, and being the
 * landing site for the automation policy the dispenser mixin calls.
 *
 * <h2>What is <em>not</em> here</h2>
 *
 * <p>No synthetic cast events. Spec §6.4 forbids posting a manufactured {@code SpellOnCastEvent} to
 * gain generic perk support, because listeners would apply damage, resources or rewards twice.
 *
 * <p>No spellgun cast hook either, and that is a finding rather than an omission. A Codex spellgun
 * lends the player the mana it is short of, calls Iron's {@code attemptInitiateCast}, and — when
 * that returns false — subtracts exactly the amount it lent before returning. Runic Skills refuses a
 * gated cast by cancelling {@code SpellPreCastEvent}, which is what makes
 * {@code attemptInitiateCast} return false, so the native rollback is the one that runs and the
 * borrowed mana is restored by the same code that lent it. {@code reserveBorrowedMana} is only
 * reached on a cast that started, so a refusal leaves no reservation behind either. Adding a refund
 * of our own would double the restoration; §12.3 says to prevent the commitment rather than to
 * refund after it, and this path already does.
 */
public final class CodexBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger("runicskills/apprentice-codex");

    /** The Iron's version Codex itself requires, and the one this release read. */
    public static final String REQUIRED_IRONS = "1.20.1-3.16.3";

    private static volatile String status = "not loaded";

    public CodexBootstrap() {
        boolean irons = ModList.get().isLoaded("irons_spellbooks");
        boolean curios = ModList.get().isLoaded("curios");
        if (!irons || !curios) {
            // Codex declares both mandatory, so this cannot normally happen; if it somehow does,
            // the integration says so and does nothing rather than half-applying.
            status = "inactive: Codex is installed without "
                    + (!irons ? "irons_spellbooks" : "curios");
            LOGGER.warn("[Runic Skills] {}", status);
            return;
        }
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        status = cfg.enableApprenticeCodexIntegration
                ? "active, " + CodexAutomation.describe()
                : "present, but enableApprenticeCodexIntegration is off";
        LOGGER.debug("[Runic Skills] Apprentice's Codex integration {}", status);
    }

    /** One line for the compatibility diagnostic. */
    public static String status() {
        return status;
    }

    /**
     * Whether {@code item} is one of Codex's mana-lending items.
     *
     * <p>Used by the tests that assert a refused cast leaves the lender's books balanced. It is a
     * plain {@code instanceof} against the interface Codex publishes for exactly this purpose,
     * which is a stabler question than "is this a spellgun" and covers the items that are not.
     */
    public static boolean lendsMana(Item item) {
        return item instanceof ManaBypassSpellItem;
    }

    /**
     * The spell capacity of a Codex stack, read through Iron's container API.
     *
     * <p>Here rather than in the lock provider because the provider must not name an
     * {@code io.redspace} type; this is the same read, exposed for diagnostics and tests.
     */
    public static int capacityOf(ItemStack stack) {
        try {
            return ISpellContainer.isSpellContainer(stack) ? ISpellContainer.get(stack).getMaxSpellCount() : 0;
        } catch (RuntimeException | LinkageError e) {
            RunicSkills.getLOGGER().debug("Could not read the spell capacity of {}", stack, e);
            return 0;
        }
    }
}
