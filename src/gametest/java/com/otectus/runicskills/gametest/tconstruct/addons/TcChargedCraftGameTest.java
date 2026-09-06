package com.otectus.runicskills.gametest.tconstruct.addons;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.ActionOrigin;
import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.gametest.tconstruct.TinkerFixtures;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.addons.TinkersAdvancedAdapter;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Supplier;

/**
 * §18.3 C08 for Charged Craft, in a class of its own because its add-on cannot boot with the others.
 *
 * <p>Tinkers' Advanced {@code 3.0.0-beta.13} does not start on a dedicated server: it declares
 * {@code com.c2h6s.tinkers_advanced.client.event.TiAcForgeEventHandler} — a client class that
 * touches {@code net.minecraft.client.player.Input} — as an unqualified
 * {@code @Mod.EventBusSubscriber}, so Forge auto-subscribes it on the server and the load fails with
 * "Attempted to load class net/minecraft/client/player/Input for invalid dist DEDICATED_SERVER".
 * That is an upstream defect with no Runic code on the stack, recorded per §16.3. Keeping this case
 * separate means the levelling and culinary perks still get a live profile, instead of one add-on's
 * server-side bug costing the other two their coverage.
 *
 * <p>The perk itself is unaffected on an install where the add-on does load: it is registered on
 * that mod id, so on a server where the add-on cannot load the id is absent and the perk is dormant.
 *
 * <p>No {@code @GameTestHolder}: registered from {@code TConstructGameTests} only when Tinkers'
 * Advanced and EtSTLib are both loaded.
 */
@PrefixGameTestTemplate(false)
public class TcChargedCraftGameTest {

    private static final String EMPTY = "empty";

    /** A positive FE cost is discounted for an identified operation, and never falls below one. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void chargedCraftDiscountsWithAPositiveMinimum(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.onlinePlayer(helper, "tc_charged_craft");
        try {
            int percent = HandlerCommonConfig.HANDLER.instance().tcChargedCraftPercent;
            // The gametest world persists, and this player is genuinely logged in, so a rank taken
            // by an earlier run would come back with them and make the negative half vacuous.
            TinkerFixtures.disablePerk(player, RegistryPerks.TC_CHARGED_CRAFT);

            int without = inAction(player, () -> TinkersAdvancedAdapter.discountEnergyCost(1000));
            if (without != 1000) {
                throw new GameTestAssertException("a non-holder was charged " + without + " of 1000");
            }

            TinkerFixtures.enablePerk(player, RegistryPerks.TC_CHARGED_CRAFT);
            int expected = 1000 - 1000 * percent / 100;
            int with = inAction(player, () -> TinkersAdvancedAdapter.discountEnergyCost(1000));
            if (with != expected) {
                throw new GameTestAssertException("1000 FE became " + with + ", expected " + expected);
            }
            // §10.3's minimum: a cost of one is still a cost.
            int one = inAction(player, () -> TinkersAdvancedAdapter.discountEnergyCost(1));
            if (one != 1) {
                throw new GameTestAssertException("a one-FE cost became " + one);
            }
            // And an extraction with no identified player action stays at full price: §12.7 forbids
            // discounting a machine's, a tick handler's or an automated exchanger's energy.
            int unattributed = TinkersAdvancedAdapter.discountEnergyCost(1000);
            if (unattributed != 1000) {
                throw new GameTestAssertException("an unattributed extraction was discounted to "
                        + unattributed);
            }
        } finally {
            TinkerFixtures.logOut(player);
        }
        helper.succeed();
    }

    /** Runs {@code query} inside an ordinary-use action frame owned by {@code player}. */
    private static int inAction(ServerPlayer player, Supplier<Integer> query) {
        boolean opened = RunicActionContext.enter(ActionOrigin.BLOCK_BREAK, player.getUUID());
        try {
            return query.get();
        } finally {
            if (opened) RunicActionContext.exit();
        }
    }
}
