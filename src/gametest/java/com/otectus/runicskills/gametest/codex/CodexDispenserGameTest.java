package com.otectus.runicskills.gametest.codex;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.gametest.MockPlayers;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.integration.apprenticecodex.CodexAutomation;
import com.otectus.runicskills.registry.RegistrySkills;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import jp.aquafactory.apprenticecodex.block.spelldispenser.SpellDispenserCastHelper;
import jp.aquafactory.apprenticecodex.block.spelldispenser.SpellDispenserSpellValidator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The spell dispenser's autonomous cast, which reaches no Iron's event and no Forge event.
 *
 * <p>Every case here calls the real {@code SpellDispenserCastHelper} entry the narrower overloads
 * funnel into, so a passing test is also proof that the {@code @Pseudo} mixin applied: the value the
 * helper returns is the one Runic Skills substituted, and nothing else in the process can produce
 * it.
 *
 * <p>Spec §6.5's four cases, plus the default: a verified online owner who does not qualify, an
 * owner who is offline, a device with no owner at all, and the default {@code DEVICE} policy under
 * which none of that applies because the machine's own rules decide. Each refusal is asserted to
 * leave the owner's mana and the device's contents exactly as they were — which is trivially true
 * here <em>because</em> the refusal happens at the head of the method, before a caster proxy exists
 * or any {@code MagicData} is touched, and that is the property being tested.
 */
@PrefixGameTestTemplate(false)
public final class CodexDispenserGameTest {

    /** Codex's placeholder owner for a dispenser nobody owns. */
    private static final UUID OWNER_OPTIONAL_FALLBACK = UUID.nameUUIDFromBytes(
            "apprenticecodex:spell_dispenser_owner_optional".getBytes(StandardCharsets.UTF_8));

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void strictPolicyRefusesAnUnqualifiedOwnerAndSpendsNothing(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        String policy = cfg.codexAutomationGatePolicy;
        boolean enabled = cfg.enableApprenticeCodexIntegration;
        boolean spellLocks = cfg.enableSpellLocks;
        boolean schoolGating = cfg.ironsEnableSchoolGating;
        ServerPlayer owner = null;
        try {
            cfg.enableApprenticeCodexIntegration = true;
            cfg.enableSpellLocks = true;
            cfg.ironsEnableSchoolGating = true;
            cfg.codexAutomationGatePolicy = "ONLINE_OWNER";
            CodexAutomation.resetCounters();

            owner = MockPlayers.onlineServerPlayer(helper, "codexowner");
            SkillCapability.get(owner).setSkillLevel(RegistrySkills.MAGIC.get(), 1);
            MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(
                    helper.getLevel().getServer().getPlayerList(), owner));

            MagicData magic = MagicData.getPlayerMagicData(owner);
            magic.setMana(500);
            float manaBefore = magic.getMana();

            ItemStack source = scroll();
            int countBefore = source.getCount();
            SpellDispenserCastHelper.CastResult result = cast(helper, source, owner.getGameProfile());

            helper.assertTrue(result != null && !result.succeeded(),
                    "an unqualified owner's dispenser cast succeeded under ONLINE_OWNER");
            helper.assertTrue(result.failureType()
                            == SpellDispenserCastHelper.FailureType.SERVER_ALLOWLIST,
                    "the refusal did not come from the Runic Skills gate: " + result.failureType());
            helper.assertTrue(magic.getMana() == manaBefore,
                    "the refused autonomous cast moved the owner's mana: " + manaBefore
                            + " -> " + magic.getMana());
            helper.assertTrue(source.getCount() == countBefore,
                    "the refused autonomous cast consumed the dispenser's spell source");
            helper.assertTrue(CodexAutomation.refusedCount() >= 1,
                    "the refusal was not recorded in the automation ledger");
        } finally {
            MockPlayers.logOut(owner);
            cfg.codexAutomationGatePolicy = policy;
            cfg.enableApprenticeCodexIntegration = enabled;
            cfg.enableSpellLocks = spellLocks;
            cfg.ironsEnableSchoolGating = schoolGating;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void strictPolicyPausesForAnOfflineOrAbsentOwner(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        String policy = cfg.codexAutomationGatePolicy;
        boolean enabled = cfg.enableApprenticeCodexIntegration;
        try {
            cfg.enableApprenticeCodexIntegration = true;
            cfg.codexAutomationGatePolicy = "ONLINE_OWNER";

            ItemStack source = scroll();
            // Nobody by this name is logged in: §6.5 says pause, not allow and not assume.
            GameProfile offline = new GameProfile(
                    UUID.nameUUIDFromBytes("codex-offline-owner".getBytes(StandardCharsets.UTF_8)),
                    "OfflineOwner");
            SpellDispenserCastHelper.CastResult absent = cast(helper, source, offline);
            helper.assertTrue(absent != null && !absent.succeeded()
                            && absent.failureType() == SpellDispenserCastHelper.FailureType.SERVER_ALLOWLIST,
                    "an offline owner's dispenser was allowed to cast: " + absent);

            // A device with no owner at all carries Codex's placeholder profile, which is not a
            // person and must not be treated as one.
            GameProfile placeholder = new GameProfile(OWNER_OPTIONAL_FALLBACK, "[SpellDispenser]");
            SpellDispenserCastHelper.CastResult unowned = cast(helper, source, placeholder);
            helper.assertTrue(unowned != null && !unowned.succeeded()
                            && unowned.failureType() == SpellDispenserCastHelper.FailureType.SERVER_ALLOWLIST,
                    "an unowned dispenser was allowed to cast under the strict policy: " + unowned);
        } finally {
            cfg.codexAutomationGatePolicy = policy;
            cfg.enableApprenticeCodexIntegration = enabled;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void theDefaultPolicyLetsTheMachineDecide(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        String policy = cfg.codexAutomationGatePolicy;
        boolean enabled = cfg.enableApprenticeCodexIntegration;
        try {
            cfg.enableApprenticeCodexIntegration = true;
            cfg.codexAutomationGatePolicy = "DEVICE";
            CodexAutomation.resetCounters();

            // An unowned device and an unsupported spell source: under DEVICE neither is Runic
            // Skills' business, so the native validation decides and the result is Codex's own.
            ItemStack empty = new ItemStack(ItemRegistry.SCROLL.get());
            GameProfile placeholder = new GameProfile(OWNER_OPTIONAL_FALLBACK, "[SpellDispenser]");
            SpellDispenserCastHelper.CastResult result = SpellDispenserCastHelper.tryCast(
                    helper.getLevel(), origin(helper), new Vec3(0, 0, 1),
                    SpellDispenserSpellValidator.validate(empty), empty, placeholder, null,
                    CastSource.SCROLL, "mainhand");

            helper.assertTrue(result != null && !result.succeeded(),
                    "an empty scroll cast succeeded");
            helper.assertTrue(result.failureType()
                            != SpellDispenserCastHelper.FailureType.SERVER_ALLOWLIST,
                    "the default DEVICE policy refused an autonomous cast: " + result.failureType());
            helper.assertTrue(CodexAutomation.automationExemptCount() >= 1,
                    "the execution was not recorded as automation-exempt");
            helper.assertTrue(CodexAutomation.refusedCount() == 0,
                    "the default policy refused something");
        } finally {
            cfg.codexAutomationGatePolicy = policy;
            cfg.enableApprenticeCodexIntegration = enabled;
        }
        helper.succeed();
    }

    /** The widest {@code tryCast}, called exactly as the narrower overloads call it. */
    private static SpellDispenserCastHelper.CastResult cast(GameTestHelper helper, ItemStack source,
                                                            GameProfile owner) {
        AbstractSpell spell = SpellRegistry.FIREBALL_SPELL.get();
        SpellDispenserSpellValidator.ValidationResult validation =
                new SpellDispenserSpellValidator.ValidationResult(source, new SpellData(spell, 1),
                        SpellDispenserSpellValidator.FailureReason.NONE);
        // ManaAccess is null on purpose: a correct refusal happens before the helper looks at it,
        // so a null here is what proves nothing downstream ran.
        return SpellDispenserCastHelper.tryCast(helper.getLevel(), origin(helper),
                new Vec3(0, 0, 1), validation, source, owner, null, CastSource.SCROLL, "mainhand");
    }

    private static Vec3 origin(GameTestHelper helper) {
        return Vec3.atCenterOf(helper.absolutePos(net.minecraft.core.BlockPos.ZERO).above(2));
    }

    /** An Iron's scroll carrying one spell: a valid dispenser spell source. */
    private static ItemStack scroll() {
        ItemStack stack = new ItemStack(ItemRegistry.SCROLL.get(), 3);
        ISpellContainer.createScrollContainer(SpellRegistry.FIREBALL_SPELL.get(), 1, stack);
        return stack;
    }
}
