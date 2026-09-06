package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.util.ItemBonusTags;
import com.otectus.runicskills.integration.tconstruct.WorkmanshipModifier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.item.Items;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * Spec §18.2 D07 and §5.4 L03: the craftsmanship stamp on a native tool.
 *
 * <p>D07 is a stability test, not a magnitude test. The bonus a stamp is worth is a design decision;
 * that a hundred rebuilds produce the same tool, with one modifier entry and no inflating maximum,
 * is a correctness one — and the failure it catches is the classic modifier bug, where a stat is
 * added on each rebuild instead of computed from stored data and a tool grows every time a player
 * looks at it.
 */
@PrefixGameTestTemplate(false)
public class WorkmanshipGameTest {

    private static final String EMPTY = "empty";

    /** Rebuilds enough times that an accumulating bonus would be unmistakable. */
    private static final int REBUILDS = 100;

    /** A stamp is stored natively, read back through the ordinary accessor, and raises durability. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aStampIsStoredNativelyAndRaisesNativeDurability(GameTestHelper helper) {
        ItemStack pickaxe = TinkerFixtures.pickaxeOfTier(1);
        int baseline = ToolStack.from(pickaxe).getStats().getInt(ToolStats.DURABILITY);

        ItemBonusTags.stamp(pickaxe, ItemBonusTags.BONUS_DURABILITY, 10);

        if (ItemBonusTags.read(pickaxe, ItemBonusTags.BONUS_DURABILITY) != 10) {
            throw new GameTestAssertException("a 10% durability stamp read back as "
                    + ItemBonusTags.read(pickaxe, ItemBonusTags.BONUS_DURABILITY));
        }
        if (pickaxe.getTag() != null && pickaxe.getTag().contains(ItemBonusTags.BONUS_DURABILITY)) {
            throw new GameTestAssertException("the stamp was written to the stack's root tag; §5.4 "
                    + "requires namespaced native persistent data on a native item");
        }
        int stamped = ToolStack.from(pickaxe).getStats().getInt(ToolStats.DURABILITY);
        if (stamped <= baseline) {
            throw new GameTestAssertException("a 10% durability stamp left ToolStats.DURABILITY at "
                    + stamped + ", unchanged from " + baseline + "; §5.4 requires the native stat to "
                    + "rise, not just the reported maximum");
        }
        if (ToolStack.from(pickaxe).getModifierLevel(WorkmanshipModifier.ID) != 1) {
            throw new GameTestAssertException("the workmanship modifier is at level "
                    + ToolStack.from(pickaxe).getModifierLevel(WorkmanshipModifier.ID)
                    + "; §5.4 allows at most one");
        }
        helper.succeed();
    }

    /** D07: a hundred rebuilds change nothing, and a re-stamp keeps the maximum rather than adding. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void rebuildingAndRestampingAreIdempotent(GameTestHelper helper) {
        ItemStack pickaxe = TinkerFixtures.pickaxeOfTier(1);
        ItemBonusTags.stamp(pickaxe, ItemBonusTags.BONUS_DURABILITY, 10);
        int afterFirst = ToolStack.from(pickaxe).getStats().getInt(ToolStats.DURABILITY);

        for (int i = 0; i < REBUILDS; i++) {
            ToolStack tool = ToolStack.from(pickaxe);
            tool.rebuildStats();
        }
        int afterRebuilds = ToolStack.from(pickaxe).getStats().getInt(ToolStats.DURABILITY);
        if (afterRebuilds != afterFirst) {
            throw new GameTestAssertException(REBUILDS + " rebuilds moved durability from "
                    + afterFirst + " to " + afterRebuilds + "; the stamp must be a function of the "
                    + "stored percentage, not something added each time");
        }

        // A smaller stamp is ignored, a larger one replaces: never additive (§5.4).
        ItemBonusTags.stamp(pickaxe, ItemBonusTags.BONUS_DURABILITY, 5);
        if (ItemBonusTags.read(pickaxe, ItemBonusTags.BONUS_DURABILITY) != 10) {
            throw new GameTestAssertException("re-stamping with a smaller percentage changed the "
                    + "stored value to " + ItemBonusTags.read(pickaxe, ItemBonusTags.BONUS_DURABILITY));
        }
        ItemBonusTags.stamp(pickaxe, ItemBonusTags.BONUS_DURABILITY, 25);
        if (ItemBonusTags.read(pickaxe, ItemBonusTags.BONUS_DURABILITY) != 25) {
            throw new GameTestAssertException("re-stamping with a larger percentage left the stored "
                    + "value at " + ItemBonusTags.read(pickaxe, ItemBonusTags.BONUS_DURABILITY));
        }
        if (ToolStack.from(pickaxe).getModifierLevel(WorkmanshipModifier.ID) != 1) {
            throw new GameTestAssertException("re-stamping added a second workmanship modifier level");
        }
        helper.succeed();
    }

    /**
     * L03: an old root-tag stamp migrates once, and unrecognised data on the stack survives it.
     *
     * <p>The retained key is the point of the second half. A migration that rewrote the tag wholesale
     * would take another mod's data with it, and the player would find out when that mod's feature
     * stopped working on tools they happened to have repaired.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void anOldStampMigratesOnceAndKeepsUnknownData(GameTestHelper helper) {
        ItemStack pickaxe = TinkerFixtures.pickaxeOfTier(1);
        pickaxe.getOrCreateTag().putInt(ItemBonusTags.BONUS_DURABILITY, 15);
        pickaxe.getOrCreateTag().putInt("somemod.retainedValue", 7);

        int migrated = ItemBonusTags.read(pickaxe, ItemBonusTags.BONUS_DURABILITY);
        if (migrated != 15) {
            throw new GameTestAssertException("an old 15% root-tag stamp read back as " + migrated
                    + " after migration");
        }
        if (pickaxe.getTag().contains(ItemBonusTags.BONUS_DURABILITY)) {
            throw new GameTestAssertException("the old root-tag stamp is still present after "
                    + "migration; generic durability scaling would then apply it a second time");
        }
        if (pickaxe.getTag().getInt("somemod.retainedValue") != 7) {
            throw new GameTestAssertException("migration discarded another mod's data on the stack");
        }
        if (ToolStack.from(pickaxe).getModifierLevel(WorkmanshipModifier.ID) != 1) {
            throw new GameTestAssertException("migration did not add exactly one workmanship modifier");
        }

        // Reading again must not migrate again: a second pass would double a percentage.
        if (ItemBonusTags.read(pickaxe, ItemBonusTags.BONUS_DURABILITY) != 15) {
            throw new GameTestAssertException("a second read migrated the tool again");
        }
        helper.succeed();
    }

    /** A vanilla item keeps the old scheme exactly, native adapter or no native adapter. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aVanillaItemStillUsesTheRootTag(GameTestHelper helper) {
        ItemStack vanilla = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemBonusTags.stamp(vanilla, ItemBonusTags.BONUS_DURABILITY, 10);
        if (vanilla.getTag() == null || vanilla.getTag().getInt(ItemBonusTags.BONUS_DURABILITY) != 10) {
            throw new GameTestAssertException("a vanilla pickaxe no longer stores its stamp in the "
                    + "root tag; installing the native scheme must not move vanilla items");
        }
        if (ItemBonusTags.isNativeItem(vanilla)) {
            throw new GameTestAssertException("the native storage provider claimed a vanilla pickaxe");
        }
        helper.succeed();
    }
}
