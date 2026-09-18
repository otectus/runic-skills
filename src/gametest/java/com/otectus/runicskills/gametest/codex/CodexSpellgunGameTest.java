package com.otectus.runicskills.gametest.codex;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.gametest.MockPlayers;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.integration.apprenticecodex.CodexBootstrap;
import com.otectus.runicskills.registry.RegistrySkills;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import jp.aquafactory.apprenticecodex.item.spellgun.AbstractSpellGunItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * A refused spellgun cast restores the borrowed mana and consumes no shot (spec §6.6, §16.3).
 *
 * <p>The mechanism is worth stating because the test exists to prove it rather than to implement
 * it. A Codex spellgun lends the player whatever mana they are short of, calls Iron's
 * {@code attemptInitiateCast}, and — if that returns false — subtracts exactly what it lent before
 * returning. Runic Skills refuses a gated cast by cancelling {@code SpellPreCastEvent}, which is
 * what makes {@code attemptInitiateCast} return false, so the native rollback is the one that runs.
 * {@code reserveBorrowedMana} is only reached after a cast has started, so a refusal leaves no
 * reservation behind either. Adding a refund of our own would double the restoration.
 *
 * <p>The player starts with no mana at all, so the amount lent is the whole cost: a test where the
 * player could already afford the spell would pass without the rollback ever running.
 */
@PrefixGameTestTemplate(false)
public final class CodexSpellgunGameTest {

    /**
     * The first registered spell this gun will hold whose own gate refuses a level 1 Magic player.
     *
     * <p>Both halves matter. A spell the gun cannot hold never reaches the cast, and a spell the
     * gate would allow would make the refusal assertion pass for the wrong reason.
     */
    private static AbstractSpell firstGatedImbuableSpell(AbstractSpellGunItem gun) {
        for (AbstractSpell candidate : SpellRegistry.REGISTRY.get()) {
            if (candidate == null || candidate == SpellRegistry.none()) continue;
            if (!gun.canImbueSpell(candidate, 1)) continue;
            if (com.otectus.runicskills.integration.irons.IronsSpellGate
                    .requirement(candidate.getSpellId(), 1).orElse(0) <= 1) continue;
            return candidate;
        }
        return null;
    }

    /** How many of {@code item} the player is carrying, across every inventory slot. */
    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void aRefusedSpellgunCastRestoresBorrowedManaAndConsumesNoAmmo(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean spellLocks = cfg.enableSpellLocks;
        boolean schoolGating = cfg.ironsEnableSchoolGating;
        boolean codex = cfg.enableApprenticeCodexIntegration;
        try {
            cfg.enableSpellLocks = true;
            cfg.ironsEnableSchoolGating = true;
            cfg.enableApprenticeCodexIntegration = true;
            HandlerSkill.getSkill();

            Item gunItem = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("apprenticecodex", "copper_spellcaster_gun"));
            helper.assertTrue(gunItem instanceof AbstractSpellGunItem,
                    "copper_spellcaster_gun is not a spellgun");
            helper.assertTrue(CodexBootstrap.lendsMana(gunItem),
                    "the spellgun does not declare itself a mana-lending item, so this test would "
                            + "not be exercising the borrowed-mana path at all");
            AbstractSpellGunItem gun = (AbstractSpellGunItem) gunItem;

            // Which spells a gun accepts depends on its cast-type set and the server's spellgun
            // cooldown limits, so the spell is discovered rather than named: any spell this gun
            // will hold and that the gate refuses at Magic 1 proves the same thing, and a hard-coded
            // one would make the test a statement about Codex's tuning instead of about the gate.
            AbstractSpell spell = firstGatedImbuableSpell(gun);
            helper.assertTrue(spell != null, "the copper spellgun accepts no spell that the Magic "
                    + "gate refuses at level 1, so this test could not exercise a refusal");

            ItemStack weapon = new ItemStack(gun);
            gun.initializeSpellContainer(weapon);
            ISpellContainer.get(weapon).addSpell(spell, 1, false, weapon);
            gun.normalizeImbuedSpellContainer(weapon);
            helper.assertTrue(ISpellContainer.get(weapon).getActiveSpellCount() > 0,
                    "the spellgun refused to hold the spell");

            ServerPlayer player = MockPlayers.connectedServerPlayer(helper,
                    "gun_" + UUID.randomUUID().toString().substring(0, 8));
            SkillCapability.get(player).setSkillLevel(RegistrySkills.MAGIC.get(), 1);
            MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(
                    helper.getLevel().getServer().getPlayerList(), player));
            player.setItemInHand(InteractionHand.MAIN_HAND, weapon);

            Item ammoItem = gun.getAmmoItem(weapon, new SpellData(spell, 1));
            helper.assertTrue(ammoItem != null, "the spellgun named no ammunition item");
            // Counted out of the inventory rather than off the stack handed to add(): Inventory.add
            // shrinks the stack it is given as it inserts, so reading the original afterwards would
            // compare zero with zero and pass whatever happened.
            player.getInventory().add(new ItemStack(ammoItem, 8));
            int ammoBefore = count(player, ammoItem);
            helper.assertTrue(ammoBefore == 8, "the ammunition did not reach the inventory: " + ammoBefore);

            MagicData magic = MagicData.getPlayerMagicData(player);
            magic.getPlayerCooldowns().clearCooldowns();
            magic.setMana(0);
            float manaBefore = magic.getMana();

            boolean fired = gun.tryTriggerImbuedSpell(player, InteractionHand.MAIN_HAND, null);

            helper.assertTrue(!fired, "a level 1 Magic player fired a gated spellgun");
            helper.assertTrue(magic.getMana() == manaBefore,
                    "the refused spellgun cast left borrowed mana behind: " + manaBefore + " -> "
                            + magic.getMana());
            helper.assertTrue(!magic.isCasting(), "the refused spellgun cast left the player casting");
            helper.assertTrue(count(player, ammoItem) == ammoBefore,
                    "the refused spellgun cast consumed ammunition: " + ammoBefore + " -> "
                            + count(player, ammoItem));
            helper.assertTrue(weapon.getCount() == 1, "the refused cast consumed the weapon");

            // The same call, from a player who qualifies, must succeed: otherwise the refusal above
            // proved only that something failed, not that the gate is what failed it.
            SkillCapability.get(player).setSkillLevel(RegistrySkills.MAGIC.get(), cfg.skillMaxLevel);
            magic.setMana(0);
            boolean allowed = gun.tryTriggerImbuedSpell(player, InteractionHand.MAIN_HAND, null);
            helper.assertTrue(allowed, "a fully levelled player could not fire the same spellgun, so "
                    + "the refusal above was not the skill gate");
            io.redspace.ironsspellbooks.api.util.Utils.serverSideCancelCast(player);
            magic.getPlayerCooldowns().clearCooldowns();
        } finally {
            cfg.enableSpellLocks = spellLocks;
            cfg.ironsEnableSchoolGating = schoolGating;
            cfg.enableApprenticeCodexIntegration = codex;
            HandlerSkill.getSkill();
        }
        helper.succeed();
    }
}
