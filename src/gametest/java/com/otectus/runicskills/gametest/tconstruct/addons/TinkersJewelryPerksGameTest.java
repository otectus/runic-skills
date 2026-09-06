package com.otectus.runicskills.gametest.tconstruct.addons;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.gametest.tconstruct.TinkerFixtures;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.addons.TcAddonHooks;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * §18.3 C08 for the four registered Tinkers' Jewelry perks.
 *
 * <p><b>What a green run here does and does not prove.</b> No {@code -PtinkersAddons} slug boots
 * this class: Modrinth's newest 1.20.1 Forge build of the add-on is 1.1.0 and the reference pack
 * runs 1.2.0, so a profile would test a jar nobody uses. {@code TConstructGameTests} still registers
 * the class whenever {@code tinkersjewelry} is loaded, which is how a pack developer who supplies
 * their own 1.2.0 gets the coverage. In the shipped profiles the jewelry guarantees rest on
 * {@code TcAddonAbsenceGameTest}'s dormancy invariants instead, which run unconditionally.
 *
 * <p>Like its Thinking sibling, every assertion goes through the public {@code TcAddonHooks} sums —
 * the values the perk handler actually composes and caps.
 *
 * <p>No {@code @GameTestHolder}: registered from {@code TConstructGameTests}.
 */
@PrefixGameTestTemplate(false)
public class TinkersJewelryPerksGameTest {

    private static final String EMPTY = "empty";

    /** Jeweler's Setting arms on the take and pays only on the piece that was taken. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void jewelerSettingAffectsStationTake(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.onlinePlayer(helper, "tc_jeweler_setting");
        try {
            TinkerFixtures.enablePerk(player, RegistryPerks.TC_JEWELER_SETTING);
            ItemStack ring = ring();

            double before = TcAddonHooks.wearAvoidanceBonus(player, ring);
            if (before != 0.0) {
                throw new GameTestAssertException("avoidance was " + before + " before any take");
            }

            TcAddonHooks.stationTakeDelivered(player, ring);
            double expected = HandlerCommonConfig.HANDLER.instance().tcJewelerSettingPercent / 100.0;
            double armed = TcAddonHooks.wearAvoidanceBonus(player, ring);
            if (Math.abs(armed - expected) > 1.0E-6) {
                throw new GameTestAssertException("avoidance was " + armed + ", expected " + expected);
            }

            // Identity, not equality: the perk belongs to the piece the player was handed, and a
            // second ring off the same bench is a different piece.
            double other = TcAddonHooks.wearAvoidanceBonus(player, ring());
            if (other != 0.0) {
                throw new GameTestAssertException("a different ring was paid " + other);
            }
            // And an ordinary Tinkers' tool is not jewelry at all.
            double pickaxe = TcAddonHooks.wearAvoidanceBonus(player, TinkerFixtures.pickaxeOfTier(1));
            if (pickaxe != 0.0) {
                throw new GameTestAssertException("a pickaxe was paid " + pickaxe);
            }
        } finally {
            TinkerFixtures.logOut(player);
        }
        helper.succeed();
    }

    /** Gem Attunement pays for a worn piece and for nothing else. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void gemAttunementClassifiesJewelryMaterial(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.onlinePlayer(helper, "tc_gem_attunement");
        try {
            TinkerFixtures.enablePerk(player, RegistryPerks.TC_GEM_ATTUNEMENT);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, TinkerFixtures.pickaxeOfTier(1));

            double unworn = TcAddonHooks.meleeDamageBonus(player);
            if (unworn != 0.0) {
                throw new GameTestAssertException("no jewelry worn, yet " + unworn + " was paid");
            }

            player.setItemSlot(EquipmentSlot.HEAD, ring());
            double expected = HandlerCommonConfig.HANDLER.instance().tcGemAttunementPercent / 100.0;
            double worn = TcAddonHooks.meleeDamageBonus(player);
            if (Math.abs(worn - expected) > 1.0E-6) {
                throw new GameTestAssertException("melee share was " + worn + ", expected " + expected);
            }

            TinkerFixtures.disablePerk(player, RegistryPerks.TC_GEM_ATTUNEMENT);
            double withoutPerk = TcAddonHooks.meleeDamageBonus(player);
            if (withoutPerk != 0.0) {
                throw new GameTestAssertException("a non-holder was paid " + withoutPerk);
            }
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        } finally {
            TinkerFixtures.logOut(player);
        }
        helper.succeed();
    }

    /** Undying Lustre pays only inside a death resolution, and only on an undying piece. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void undyingLustreReducesSaveWear(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.onlinePlayer(helper, "tc_undying_lustre");
        try {
            TinkerFixtures.enablePerk(player, RegistryPerks.TC_UNDYING_LUSTRE);
            ItemStack ring = ring();
            ToolStack tool = ToolStack.from(ring);
            tool.addModifier(new ModifierId("tinkersjewelry", "undying"), 1);
            tool.rebuildStats();

            // Outside the bracket this is ordinary wear on a ring, which the perk has nothing to
            // say about: DamageItemEvents also damages jewelry on a block break and on an attack.
            double ordinary = TcAddonHooks.wearAvoidanceBonus(player, ring);
            if (ordinary != 0.0) {
                throw new GameTestAssertException("ordinary ring wear was discounted by " + ordinary);
            }

            TcAddonHooks.beginDeathResolution(player);
            try {
                double expected = HandlerCommonConfig.HANDLER.instance().tcUndyingLustrePercent / 100.0;
                double saved = TcAddonHooks.wearAvoidanceBonus(player, ring);
                if (Math.abs(saved - expected) > 1.0E-6) {
                    throw new GameTestAssertException("save wear avoidance was " + saved
                            + ", expected " + expected);
                }
                // A ring without the modifier is not what refused the death.
                double plain = TcAddonHooks.wearAvoidanceBonus(player, ring());
                if (plain != 0.0) {
                    throw new GameTestAssertException("a ring with no undying modifier was paid " + plain);
                }
            } finally {
                TcAddonHooks.endDeathResolution(player, true);
            }
        } finally {
            TinkerFixtures.logOut(player);
        }
        helper.succeed();
    }

    /** Polished Facet joins the paid-repair share for a jewelry piece and no other tool. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void polishedFacetAffectsRepair(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.onlinePlayer(helper, "tc_polished_facet");
        try {
            TinkerFixtures.enablePerk(player, RegistryPerks.TC_POLISHED_FACET);

            double pickaxe = TcAddonHooks.repairBonusShare(player, TinkerFixtures.pickaxeOfTier(1));
            if (pickaxe != 0.0) {
                throw new GameTestAssertException("a pickaxe repair was given " + pickaxe);
            }

            double expected = HandlerCommonConfig.HANDLER.instance().tcPolishedFacetPercent / 100.0;
            double jewelry = TcAddonHooks.repairBonusShare(player, ring());
            if (Math.abs(jewelry - expected) > 1.0E-6) {
                throw new GameTestAssertException("repair share was " + jewelry + ", expected " + expected);
            }

            TinkerFixtures.disablePerk(player, RegistryPerks.TC_POLISHED_FACET);
            double withoutPerk = TcAddonHooks.repairBonusShare(player, ring());
            if (withoutPerk != 0.0) {
                throw new GameTestAssertException("a non-holder was given " + withoutPerk);
            }
        } finally {
            TinkerFixtures.logOut(player);
        }
        helper.succeed();
    }

    /**
     * A ring built the way the station builds one.
     *
     * <p>{@code tinkersjewelry:ring} is the add-on's only item, read from its
     * {@code data/tinkersjewelry/tinkering/tool_definitions/ring.json}, and it registers itself into
     * {@code tconstruct:modifiable/durability}. Tinkers' own builder picks materials the ring's
     * parts actually accept, which for this tool means the add-on's gem materials — the very thing
     * the perks recognise.
     */
    private static ItemStack ring() {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tinkersjewelry", "ring"));
        if (!(item instanceof IModifiable tool)) {
            throw new GameTestAssertException("tinkersjewelry is loaded but tinkersjewelry:ring is "
                    + "not a modifiable tool on this server; the fixture cannot be built");
        }
        return ToolBuildHandler.buildItemRandomMaterials(tool, RandomSource.create(4321L));
    }
}
