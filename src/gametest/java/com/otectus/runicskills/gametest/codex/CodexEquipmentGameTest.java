package com.otectus.runicskills.gametest.codex;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.gametest.MockPlayers;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.integration.apprenticecodex.CodexBootstrap;
import com.otectus.runicskills.integration.lock.ApprenticeCodexLockProvider;
import com.otectus.runicskills.integration.lock.GateSource;
import com.otectus.runicskills.integration.lock.GateTarget;
import com.otectus.runicskills.integration.lock.LockAction;
import com.otectus.runicskills.registry.RegistrySkills;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * Codex spell containers are gated by what they can hold, and by nothing else.
 *
 * <p>Three claims, all of which the 2.2.0 behaviour got wrong or could not express:
 *
 * <ul>
 *   <li>A grimoire, a runic tablet and a spellgun are ranked by their real capacity through the
 *       same function Iron's own books use, so an addon book and a base-game book of the same size
 *       ask the same thing. Three of Codex's six container class shapes do not implement
 *       {@code ISpellbook} at all, so this cannot be done by class.</li>
 *   <li>The gate is action-scoped. Equipping is refused; taking the item out of a container is not,
 *       which is what keeps a storage book's contents retrievable below the gate (§6.2).</li>
 *   <li>A player who meets the requirement is allowed, so the refusal is a requirement and not a
 *       blanket ban on the namespace.</li>
 * </ul>
 */
@PrefixGameTestTemplate(false)
public final class CodexEquipmentGameTest {

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void aCodexContainerIsGatedByItsCapacity(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean enabled = cfg.enableApprenticeCodexIntegration;
        boolean itemLocks = cfg.enableItemLocks;
        try {
            cfg.enableApprenticeCodexIntegration = true;
            cfg.enableItemLocks = true;
            HandlerSkill.getSkill();

            ItemStack tablet = stack("apprenticecodex:spellstained_runic_tablet");
            helper.assertTrue(!tablet.isEmpty(), "spellstained_runic_tablet is not registered");
            helper.assertTrue(CodexBootstrap.capacityOf(tablet) > 0,
                    "the runic tablet reported no spell capacity, so the gate would be about nothing");

            var row = ApprenticeCodexLockProvider.ledger()
                    .get("item:apprenticecodex:spellstained_runic_tablet");
            helper.assertTrue(row != null && row.outcome() == ApprenticeCodexLockProvider.Outcome.NATIVE,
                    "the tablet's gate did not come from its chassis: " + row);

            ServerPlayer novice = player(helper, 1);
            helper.assertTrue(!SkillCapability.get(novice).canUseItem(novice, tablet, LockAction.EQUIP),
                    "a level 1 Magic player equipped a Codex spell container");
            helper.assertTrue(SkillCapability.get(novice).canUseItem(novice, tablet, LockAction.TAKE),
                    "taking the container out of a slot was refused, which traps its contents");

            ServerPlayer adept = player(helper, cfg.skillMaxLevel);
            helper.assertTrue(SkillCapability.get(adept).canUseItem(adept, tablet, LockAction.EQUIP),
                    "a fully levelled player was still refused the container");

            helper.assertTrue(HandlerSkill.snapshot().sourceOf(GateTarget.item(
                            new ResourceLocation("apprenticecodex", "spellstained_runic_tablet")))
                            == GateSource.NATIVE_ADAPTER,
                    "the tablet's rule was not recorded as coming from the native adapter layer");
        } finally {
            cfg.enableApprenticeCodexIntegration = enabled;
            cfg.enableItemLocks = itemLocks;
            HandlerSkill.getSkill();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void aSpellgunCarriesBothItsPhysicalAndItsMagicRole(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean enabled = cfg.enableApprenticeCodexIntegration;
        try {
            cfg.enableApprenticeCodexIntegration = true;
            HandlerSkill.getSkill();
            var row = ApprenticeCodexLockProvider.ledger()
                    .get("item:apprenticecodex:copper_spellcaster_gun");
            helper.assertTrue(row != null, "the copper spellcaster gun has no ledger row");
            helper.assertTrue(row.requirements().containsKey("dexterity"),
                    "a spellgun has no physical requirement: " + row.requirements());
            helper.assertTrue(row.requirements().containsKey("magic"),
                    "a spellgun has no magic requirement: " + row.requirements());
            helper.assertTrue(row.actions().contains(LockAction.ATTACK)
                            && row.actions().contains(LockAction.EQUIP),
                    "a spellgun's gate does not cover using it: " + row.actions());
        } finally {
            cfg.enableApprenticeCodexIntegration = enabled;
            HandlerSkill.getSkill();
        }
        helper.succeed();
    }

    private static ItemStack stack(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ServerPlayer player(GameTestHelper helper, int magicLevel) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper,
                "codex_" + UUID.randomUUID().toString().substring(0, 8));
        SkillCapability capability = SkillCapability.get(player);
        capability.setSkillLevel(RegistrySkills.MAGIC.get(), magicLevel);
        capability.setSkillLevel(RegistrySkills.INTELLIGENCE.get(), magicLevel);
        capability.setSkillLevel(RegistrySkills.DEXTERITY.get(), magicLevel);
        return player;
    }
}
