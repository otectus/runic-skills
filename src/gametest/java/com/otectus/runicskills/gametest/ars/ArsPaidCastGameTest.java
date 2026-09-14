package com.otectus.runicskills.gametest.ars;

import com.hollingsworth.arsnouveau.api.event.SpellResolveEvent;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.gametest.MockPlayers;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real transformed Ars debit and Forge resolve events, with both optional mana systems loaded. */
@PrefixGameTestTemplate(false)
public final class ArsPaidCastGameTest {
    @GameTest(template = "empty", templateNamespace = RunicSkills.MOD_ID)
    public static void backflowTracksPaymentNotHits(GameTestHelper helper) {
        var player = MockPlayers.connectedServerPlayer(helper, "ars_payment");
        var config = HandlerCommonConfig.HANDLER.instance();
        boolean previousArs = config.enableArsNouveauIntegration;
        boolean previousIrons = config.enableIronsSpellbooksIntegration;
        int previousRefund = config.xUnifiedArcanaPercent;
        try {
            config.enableArsNouveauIntegration = true;
            config.enableIronsSpellbooksIntegration = true;
            config.xUnifiedArcanaPercent = 10;
            var perk = RegistryPerks.UNIFIED_ARCANA.get();
            var skills = SkillCapability.get(player);
            skills.setSkillLevel(perk.getSkill(), Math.max(1, perk.requiredLevel));
            skills.setPerkRank(perk, 1);
            if (!perk.isEnabled(player)) throw new GameTestAssertException("Unified Arcana fixture is not enabled");

            var ars = CapabilityRegistry.getMana(player).orElseThrow(
                    () -> new GameTestAssertException("Missing native Ars mana capability"));
            var irons = MagicData.getPlayerMagicData(player);
            var spell = new Spell(MethodSelf.INSTANCE);
            var context = SpellContext.fromEntity(spell, player, ItemStack.EMPTY);
            // Override only the quote; expendMana is the actual transformed upstream method.
            var paid = new SpellResolver(context) {
                @Override public int getResolveCost() { return 20; }
            };
            ars.setMaxMana(100);
            ars.setMana(100);
            near(100, ars.getCurrentMana(), "native Ars mana fixture");
            irons.setMana(0);
            paid.expendMana();
            near(80, ars.getCurrentMana(), "native Ars debit");
            near(2, irons.getMana(), "one discounted payment refund");

            for (int i = 0; i < 8; i++) {
                MinecraftForge.EVENT_BUS.post(new SpellResolveEvent.Post(helper.getLevel(), player,
                        new EntityHitResult(player), spell, context, paid));
            }
            near(2, irons.getMana(), "repeat resolve events must not generate mana");

            new SpellResolver(context) {
                @Override public int getResolveCost() { return 0; }
            }.expendMana();
            near(2, irons.getMana(), "a free payment must not generate mana");

            config.enableIronsSpellbooksIntegration = false;
            paid.expendMana();
            near(60, ars.getCurrentMana(), "native payment remains when integration is disabled");
            near(2, irons.getMana(), "live disable must stop cross-mod backflow");

            config.enableIronsSpellbooksIntegration = true;
            ars.setMana(100);
            irons.setMana(0);
            var nested = new SpellResolver(context) {
                @Override public int getResolveCost() { return 5; }
            };
            new SpellResolver(context) {
                @Override public int getResolveCost() {
                    nested.expendMana();
                    return 20;
                }
            }.expendMana();
            near(75, ars.getCurrentMana(), "native nested-quote payment");
            near(2.5, irons.getMana(), "quote-time nested payment must not be counted twice");
            helper.succeed();
        } finally {
            config.enableArsNouveauIntegration = previousArs;
            config.enableIronsSpellbooksIntegration = previousIrons;
            config.xUnifiedArcanaPercent = previousRefund;
        }
    }

    private static void near(double expected, double actual, String what) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .001)
            throw new GameTestAssertException(what + ": expected " + expected + ", got " + actual);
    }
}
