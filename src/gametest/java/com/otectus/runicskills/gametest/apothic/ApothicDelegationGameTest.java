package com.otectus.runicskills.gametest.apothic;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.gametest.MockPlayers;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.RegistryPassives;
import com.otectus.runicskills.registry.passive.Passive;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class ApothicDelegationGameTest {
    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void delegationReloadMovesExistingPlayerBonusesWithoutDoubling(GameTestHelper helper) {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean oldMining = config.apothicDelegateMiningSpeed;
        boolean oldCrit = config.apothicDelegateCritDamage;
        boolean oldArrow = config.apothicDelegateArrowDamage;
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "delegation_reload");
        Passive[] passives = {RegistryPassives.BREAK_SPEED.get(), RegistryPassives.CRITICAL_DAMAGE.get(),
                RegistryPassives.PROJECTILE_DAMAGE.get()};
        Attribute[] owned = {RegistryAttributes.BREAK_SPEED.get(), RegistryAttributes.CRITICAL_DAMAGE.get(),
                RegistryAttributes.PROJECTILE_DAMAGE.get()};
        try {
            for (Passive passive : passives) {
                SkillCapability.get(player).setSkillLevel(passive.getSkill(), config.skillMaxLevel);
                SkillCapability.get(player).addPassiveLevel(passive, passive.getMaxLevel());
            }
            setDelegation(config, true);
            RegistryPassives.refreshFromConfig();
            RegistryAttributes.modifierAttributes(player);
            Attribute[] delegated = {passives[0].attribute, passives[1].attribute, passives[2].attribute};
            for (int index = 0; index < passives.length; index++) {
                helper.assertTrue(delegated[index] != owned[index], "fixture did not resolve Apothic's real attribute");
                assertOnlyProvider(helper, player, passives[index], delegated[index], owned[index]);
            }

            setDelegation(config, false);
            RegistryPassives.refreshFromConfig();
            RegistryAttributes.modifierAttributes(player);
            for (int index = 0; index < passives.length; index++) {
                helper.assertTrue(passives[index].attribute == owned[index], "reload kept the old attribute provider");
                assertOnlyProvider(helper, player, passives[index], owned[index], delegated[index]);
            }

            setDelegation(config, true);
            RegistryPassives.refreshFromConfig();
            RegistryAttributes.modifierAttributes(player);
            for (int index = 0; index < passives.length; index++) {
                assertOnlyProvider(helper, player, passives[index], delegated[index], owned[index]);
            }
        } finally {
            config.apothicDelegateMiningSpeed = oldMining;
            config.apothicDelegateCritDamage = oldCrit;
            config.apothicDelegateArrowDamage = oldArrow;
            RegistryPassives.refreshFromConfig();
            RegistryAttributes.modifierAttributes(player);
        }
        helper.succeed();
    }

    private static void setDelegation(HandlerCommonConfig config, boolean enabled) {
        config.apothicDelegateMiningSpeed = enabled;
        config.apothicDelegateCritDamage = enabled;
        config.apothicDelegateArrowDamage = enabled;
    }

    private static void assertOnlyProvider(GameTestHelper helper, ServerPlayer player, Passive passive,
                                           Attribute active, Attribute inactive) {
        UUID id = UUID.fromString(passive.attributeUuid);
        var current = player.getAttribute(active);
        var previous = player.getAttribute(inactive);
        helper.assertTrue(current != null && previous != null, "both providers must remain available after reload");
        var modifier = current.getModifier(id);
        helper.assertTrue(modifier != null && Math.abs(modifier.getAmount() - passive.getValue()) < 0.0001,
                "active provider lost the earned passive bonus for " + passive.getName());
        helper.assertTrue(previous.getModifier(id) == null,
                "inactive provider retained a duplicate bonus for " + passive.getName());
    }
}
