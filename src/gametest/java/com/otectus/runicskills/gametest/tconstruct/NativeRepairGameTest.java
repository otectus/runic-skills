package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.durability.RepairService;
import com.otectus.runicskills.common.durability.RepairSource;
import com.otectus.runicskills.common.equipment.EquipmentProfile;
import com.otectus.runicskills.common.equipment.EquipmentProfileService;
import com.otectus.runicskills.common.equipment.EquipmentRole;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.item.Items;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.Optional;

/**
 * Spec §18.2 D05: a repair goes through the item's own mod, and the source is not lost on the way.
 *
 * <p>The failure this guards against is quiet rather than loud. {@code stack.setDamageValue(...)} on
 * a Tinkers' tool does not throw — it writes a number the item does not read, so the repair simply
 * does not happen and every perk that paid for it thinks it did. That is why the repair route is a
 * service with an adapter behind it, and why the assertion here is on the durability the tool
 * actually regained rather than on the call having returned.
 */
@PrefixGameTestTemplate(false)
public class NativeRepairGameTest {

    private static final String EMPTY = "empty";

    /** The adapter claims a native tool, and claims it before the vanilla one does. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aNativeToolIsClassifiedByItsOwnAdapter(GameTestHelper helper) {
        ItemStack pickaxe = TinkerFixtures.pickaxeOfTier(1);
        Optional<EquipmentProfile> profile = EquipmentProfileService.profile(pickaxe);
        if (profile.isEmpty()) {
            throw new GameTestAssertException("no adapter claimed a Tinkers' pickaxe");
        }
        if (!"tconstruct".equals(profile.get().providerId())) {
            throw new GameTestAssertException("a Tinkers' pickaxe was classified by adapter '"
                    + profile.get().providerId() + "'; the native adapter must be asked first");
        }
        if (!profile.get().has(EquipmentRole.TOOL) || !profile.get().has(EquipmentRole.DIGGER)) {
            throw new GameTestAssertException("a Tinkers' pickaxe holds roles "
                    + profile.get().roles() + "; TOOL and DIGGER are what every durability and "
                    + "mining perk means by a pickaxe");
        }
        // A vanilla item must still be answered by the vanilla adapter, unchanged.
        Optional<EquipmentProfile> vanilla = EquipmentProfileService.profile(new ItemStack(Items.DIAMOND_PICKAXE));
        if (vanilla.isEmpty() || !"vanilla".equals(vanilla.get().providerId())) {
            throw new GameTestAssertException("registering the native adapter changed how a vanilla "
                    + "pickaxe is classified");
        }
        helper.succeed();
    }

    /**
     * A Runic-origin repair restores real durability, and reports exactly what it restored.
     *
     * <p>The reported number is the committed difference, not the request: §5.3 admits only positive
     * committed repair, and a budget credited for a repair the tool did not take would drain itself
     * mending nothing.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void autoRepairMendsANativeTool(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_repair_auto");
        ItemStack pickaxe = TinkerFixtures.pickaxeOfTier(1);
        ToolStack tool = ToolStack.from(pickaxe);
        tool.setDamage(20);

        int spent = RepairService.repair(player, pickaxe, 5, RepairSource.AUTO_REPAIR);
        int remaining = ToolStack.from(pickaxe).getDamage();

        if (spent != 5 || remaining != 15) {
            throw new GameTestAssertException("a 5-point Auto Repair on a tool at 20 damage reported "
                    + spent + " points and left it at " + remaining + "; expected 5 and 15");
        }
        helper.succeed();
    }

    /** An undamaged tool absorbs nothing, and says so rather than reporting a repair. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void anUndamagedToolAbsorbsNothing(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_repair_whole");
        ItemStack pickaxe = TinkerFixtures.pickaxeOfTier(1);
        int spent = RepairService.repair(player, pickaxe, 10, RepairSource.AUTO_REPAIR);
        if (spent != 0) {
            throw new GameTestAssertException("repairing an undamaged native tool reported "
                    + spent + " points spent");
        }
        helper.succeed();
    }

    /**
     * An unattributable repair proceeds unchanged and earns nothing extra.
     *
     * <p>§5.3: "unknown external repairs proceed unchanged and trigger no Runic repair reward". The
     * observable half of that here is that {@link RepairSource#UNKNOWN} restores exactly the same
     * durability as {@link RepairSource#AUTO_REPAIR} — no bonus, no penalty, no refusal.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void anUnknownSourceIsNeitherAmplifiedNorRefused(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_repair_unknown");

        ItemStack known = TinkerFixtures.pickaxeOfTier(1);
        ToolStack.from(known).setDamage(30);
        int knownSpent = RepairService.repair(player, known, 7, RepairSource.AUTO_REPAIR);

        ItemStack unknown = TinkerFixtures.pickaxeOfTier(1);
        ToolStack.from(unknown).setDamage(30);
        int unknownSpent = RepairService.repair(player, unknown, 7, RepairSource.UNKNOWN);

        if (knownSpent != unknownSpent) {
            throw new GameTestAssertException("the same 7-point repair restored " + knownSpent
                    + " from AUTO_REPAIR and " + unknownSpent + " from UNKNOWN; an unattributable "
                    + "repair proceeds unchanged, which means identically");
        }
        helper.succeed();
    }

    /** A repair larger than the damage is clamped to the damage, never past whole. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aRepairIsClampedToTheRemainingDamage(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_repair_clamp");
        ItemStack pickaxe = TinkerFixtures.pickaxeOfTier(1);
        ToolStack.from(pickaxe).setDamage(4);

        int spent = RepairService.repair(player, pickaxe, 1_000, RepairSource.AUTO_REPAIR);
        int remaining = ToolStack.from(pickaxe).getDamage();
        if (spent != 4 || remaining != 0) {
            throw new GameTestAssertException("a 1000-point repair on a tool at 4 damage reported "
                    + spent + " and left it at " + remaining + "; expected 4 and 0");
        }
        helper.succeed();
    }
}
