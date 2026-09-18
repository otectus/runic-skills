package com.otectus.runicskills.gametest.codex;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.integration.lock.ApprenticeCodexLockProvider;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spec §6.6, first bullet: every supported registered Codex item and block has a ledger outcome.
 *
 * <p>Coverage of the whole namespace rather than of a sample, because the claim being tested is
 * exactly that nothing was missed. A Codex release that adds an item must either be classified by
 * one of the reviewed rules or land on the explicit "no reviewed role matches this" exemption — and
 * both are outcomes. What must not happen is an entry with no row at all, which is the state that
 * would let content be silently ungated <em>and</em> silently unreviewed.
 */
@PrefixGameTestTemplate(false)
public final class CodexLedgerGameTest {

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void everyCodexEntryHasALedgerOutcome(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean enabled = cfg.enableApprenticeCodexIntegration;
        try {
            cfg.enableApprenticeCodexIntegration = true;
            HandlerSkill.getSkill();
            Map<String, ApprenticeCodexLockProvider.LedgerRow> ledger = ApprenticeCodexLockProvider.ledger();

            List<String> missing = new ArrayList<>();
            int items = 0;
            int blocks = 0;
            for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
                if (!"apprenticecodex".equals(id.getNamespace())) continue;
                items++;
                if (!ledger.containsKey("item:" + id)) missing.add("item:" + id);
            }
            for (ResourceLocation id : ForgeRegistries.BLOCKS.getKeys()) {
                if (!"apprenticecodex".equals(id.getNamespace())) continue;
                blocks++;
                if (!ledger.containsKey("block:" + id)) missing.add("block:" + id);
            }

            helper.assertTrue(items > 0 && blocks > 0,
                    "the Codex registries were empty; this test would have passed vacuously");
            helper.assertTrue(missing.isEmpty(), missing.size() + " Codex entries have no ledger "
                    + "outcome, starting with " + (missing.isEmpty() ? "" : missing.get(0)));
            // Nothing may carry an outcome and no reason: a row without one is not reviewable.
            for (var row : ledger.values()) {
                helper.assertTrue(row.reason() != null && !row.reason().isBlank(),
                        row.target() + " has an outcome with no recorded reason");
            }
            helper.assertTrue(ApprenticeCodexLockProvider.ledgerCounts().values().stream()
                            .mapToInt(Integer::intValue).sum() == ledger.size(),
                    "the outcome counts do not add up to the number of ledger rows");
        } finally {
            cfg.enableApprenticeCodexIntegration = enabled;
            HandlerSkill.getSkill();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void reviewedRolesLandWhereTheyWereReviewed(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean enabled = cfg.enableApprenticeCodexIntegration;
        try {
            cfg.enableApprenticeCodexIntegration = true;
            HandlerSkill.getSkill();
            Map<String, ApprenticeCodexLockProvider.LedgerRow> ledger = ApprenticeCodexLockProvider.ledger();

            var workbench = ledger.get("block:apprenticecodex:spellcaster_workbench");
            helper.assertTrue(workbench != null
                            && workbench.outcome() == ApprenticeCodexLockProvider.Outcome.CURATED
                            && workbench.actions().contains(
                                    com.otectus.runicskills.integration.lock.LockAction.INTERACT_BLOCK),
                    "the spellcaster workbench is not a reviewed operation gate: " + workbench);
            helper.assertTrue(workbench.actions().size() == 1,
                    "the workstation gate names more than INTERACT_BLOCK, so it would also forbid "
                            + "breaking the block: " + workbench.actions());

            var creative = ledger.get("block:apprenticecodex:creative_spell_dispenser");
            helper.assertTrue(creative != null
                            && creative.outcome() == ApprenticeCodexLockProvider.Outcome.EXEMPT,
                    "creative content was gated: " + creative);

            // The ender grimoire's capacity lives in a player capability, not on the stack, so the
            // chassis read cannot describe it and the reviewed storage anchor decides instead. Both
            // are real outcomes; what must not happen is an unreviewed guess.
            var grimoire = ledger.get("item:apprenticecodex:ender_grimoire");
            helper.assertTrue(grimoire != null
                            && grimoire.outcome() == ApprenticeCodexLockProvider.Outcome.CURATED
                            && grimoire.requirements().getOrDefault("magic", 0) > 0,
                    "the ender grimoire has no reviewed storage gate: " + grimoire);
            helper.assertTrue(!grimoire.actions().contains(
                            com.otectus.runicskills.integration.lock.LockAction.TAKE),
                    "a storage book's gate covers TAKE, which would trap a player's contents");

            var round = ledger.get("item:apprenticecodex:basic_spellcaster_round");
            helper.assertTrue(round != null
                            && round.outcome() == ApprenticeCodexLockProvider.Outcome.EXEMPT,
                    "ammunition was gated: " + round);
        } finally {
            cfg.enableApprenticeCodexIntegration = enabled;
            HandlerSkill.getSkill();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void switchingTheIntegrationOffLeavesCodexContentUngated(GameTestHelper helper) {
        // Spec §6.3: an opt-out must also stop the universal engine recreating the same
        // restrictions under another name. Off means ungated, not "gated by the generic estimator".
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean enabled = cfg.enableApprenticeCodexIntegration;
        try {
            cfg.enableApprenticeCodexIntegration = false;
            HandlerSkill.getSkill();
            List<String> gated = new ArrayList<>();
            for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
                if (!"apprenticecodex".equals(id.getNamespace())) continue;
                var required = HandlerSkill.getValue(id.toString());
                if (required != null && !required.isEmpty()) gated.add(id.toString());
            }
            helper.assertTrue(gated.isEmpty(), gated.size() + " Codex items are still gated with the "
                    + "integration off, starting with " + (gated.isEmpty() ? "" : gated.get(0)));
        } finally {
            cfg.enableApprenticeCodexIntegration = enabled;
            HandlerSkill.getSkill();
        }
        helper.succeed();
    }
}
