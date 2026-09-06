package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.gametest.MockPlayers;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructPowerDispatcher;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerDispatch;
import com.otectus.runicskills.registry.powers.PowerEligibility;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.ToolBuildingRecipe;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tables.menu.slot.LazyResultSlot;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;

import java.util.ArrayList;
import java.util.List;

/**
 * Real Tinkers' tools, built from the materials the running server actually loaded.
 *
 * <p>Spec §18 asks for fixtures built from real loaded material definitions rather than from
 * hand-written NBT, and the reason is that hand-written NBT is a guess about a schema this mod does
 * not own: a tool assembled by hand can carry a material that no longer exists, stats that a
 * rebuild would never produce, or a part count the definition does not have, and then pass a test
 * that the real item would fail.
 *
 * <p>Materials are chosen by tier at run time rather than named. Naming {@code tconstruct:cobalt}
 * would pin the test to one version's material list; asking the registry for "something the loaded
 * pack calls tier 3" asks the same question the resolver under test asks.
 */
// Public since the add-on gametests (S5) live in a sub-package: they need the same
// fixtures, and package-private does not reach a sub-package.
public final class TinkerFixtures {

    /** A container synchronizer that sends nothing, for a player with nowhere to send it. */
    private static final ContainerSynchronizer SILENT = new ContainerSynchronizer() {
        @Override
        public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items,
                                    ItemStack carried, int[] data) {
        }

        @Override
        public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu menu, ItemStack carried) {
        }

        @Override
        public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
        }
    };

    private TinkerFixtures() {
    }

    /** The native item with this id, as the modifiable tool it is. */
    static IModifiable modifiable(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct", path));
        if (!(item instanceof IModifiable tool)) {
            throw new GameTestAssertException("tconstruct:" + path
                    + " is not a modifiable tool on this server; the fixture cannot be built");
        }
        return tool;
    }

    /**
     * The lowest-sorted loaded material of exactly {@code tier} that can be a pickaxe head.
     *
     * <p>{@code HeadMaterialStats.ID} — {@code tconstruct:head} — rather than a stat-type group: a
     * material without head stats has no durability or mining tier, and a pickaxe built from it
     * would be a tool whose numbers came from a default rather than from the material the test
     * chose. Tiers here run from 0 (wood) upward, which is Tinkers' own numbering.
     */
    static MaterialVariant materialOfTier(int tier) {
        IMaterial best = null;
        for (IMaterial material : MaterialRegistry.getMaterials()) {
            if (material.getTier() != tier || material.isHidden()) continue;
            if (MaterialRegistry.getInstance()
                    .getMaterialStats(material.getIdentifier(), HeadMaterialStats.ID)
                    .isEmpty()) {
                continue;
            }
            if (best == null || material.getSortOrder() < best.getSortOrder()) best = material;
        }
        if (best == null) {
            throw new GameTestAssertException("no loaded material of tier " + tier
                    + " has head stats; the fixture cannot be built");
        }
        return MaterialVariant.of(best);
    }

    /** A pickaxe built entirely from one material, exactly as the station would build it. */
    public static ItemStack pickaxeOfTier(int tier) {
        return ToolBuildHandler.createSingleMaterial(modifiable("pickaxe"), materialOfTier(tier));
    }

    /**
     * A tool of this type, built from materials the loaded pack actually accepts for each part.
     *
     * <p>{@code createSingleMaterial} is not usable for every tool — a bow's limb and grip take
     * different stat types from a pickaxe head — so the fixture asks Tinkers' own builder for a
     * valid random combination rather than guessing one.
     */
    static ItemStack randomTool(String path) {
        return ToolBuildHandler.buildItemRandomMaterials(modifiable(path), RandomSource.create(1234L));
    }

    /**
     * A test player whose packets go somewhere, for the flows that send some.
     *
     * <p>Native code talks to the player more than vanilla does: the station syncs its recipe to
     * whoever is looking, and an area harvest sends a block update per extra block through Tinkers'
     * own network. Both read {@code player.connection} without checking it, so a player built by
     * {@link #player} — which has none — dies inside Tinkers' rather than in the code under test.
     * {@code MockPlayers} already solved this for the vanilla suite with an in-memory channel; this
     * is the same player with the station's synchronizer silenced.
     */
    public static ServerPlayer connectedPlayer(GameTestHelper helper, String name) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, name);
        player.containerMenu.setSynchronizer(SILENT);
        return player;
    }

    /**
     * A genuinely logged-in player: in the player list, in the level, and ticked. Needed by the
     * tests whose product code resolves a player from the list or waits on a tick. The caller logs
     * them out again, or the rest of the batch inherits them.
     */
    public static ServerPlayer onlinePlayer(GameTestHelper helper, String name) {
        ServerPlayer player = MockPlayers.onlineServerPlayer(helper, name);
        player.containerMenu.setSynchronizer(SILENT);
        // A logged-in player loads their save file, and the gametest world keeps one from run to
        // run. Without this, the second run of a test that spends a Crown cooldown starts with that
        // cooldown already restored from login and the Power silently declines to fire -- a
        // test that passes exactly once, on a clean world, and then never again.
        PowerRuntime.clearPlayer(player.getUUID());
        SkillCapability capability = capabilityOf(player);
        capability.tcPowerCooldowns.clear();
        capability.equippedMarks.clear();
        capability.equippedSeals.clear();
        capability.equippedCrown = "";
        TConstructPowerDispatcher.forget(player);
        return player;
    }

    /** Undoes {@link #onlinePlayer}. */
    public static void logOut(ServerPlayer player) {
        MockPlayers.logOut(player);
    }

    /**
     * A test player with a silenced container and an in-memory packet sink.
     *
     * <p>It used to be built with no connection at all, which was enough while the classpath held
     * only Tinkers'. It is not enough once a pack's other mods are on it: Ars Nouveau's
     * {@code ManaCapEvents.playerOnTick} sends a mana sync to every ticked player unconditionally,
     * so a connectionless test player dies inside another mod on the first tick. That is ordinary
     * behaviour on a real server and not something a fixture is entitled to object to.
     *
     * <p>Fixed once, here, rather than per test: {@link #connectedPlayer} already exists for the
     * same reason and every caller of this method wants the same thing. The player is otherwise
     * identical, and no assertion anywhere depends on its packets going nowhere.
     */
    public static ServerPlayer player(GameTestHelper helper, String name) {
        return connectedPlayer(helper, name);
    }

    /**
     * Takes the perk back off a player.
     *
     * <p>Needed by the add-on tests, whose "without the perk" half runs against a genuinely
     * logged-in player: the gametest world persists between runs, so a player who took the perk in
     * an earlier run logs back in still holding it and the negative half of the case quietly stops
     * testing anything.
     */
    public static void disablePerk(ServerPlayer player, RegistryObject<Perk> registered) {
        capabilityOf(player).setPerkRank(registered.get(), 0);
    }

    /** Gives the player the perk: its skill at the required level, and one rank taken. */
    public static void enablePerk(ServerPlayer player, RegistryObject<Perk> registered) {
        Perk perk = registered.get();
        SkillCapability capability = capabilityOf(player);
        capability.setSkillLevel(perk.getSkill(), Math.max(1, perk.requiredLevel));
        capability.setPerkRank(perk, 1);
        if (!perk.isEnabled(player)) {
            throw new GameTestAssertException("could not enable perk " + perk.getName()
                    + " for the test player; the fixture, not the perk, is broken");
        }
    }

    /**
     * Equips an Artifice Power, prerequisites and all.
     *
     * <p>Every skill goes to the configured cap first, which satisfies the governing, secondary and
     * total-skill gates at once, and then the same-school chain is walked from the bottom: a Seal
     * needs an Artifice Mark equipped and a Crown needs an Artifice Seal. Going through
     * {@code evaluateEquip} rather than writing the slot directly is deliberate -- a Power the
     * server would refuse must fail the fixture rather than be tested in a state no player can
     * reach.
     */
    static void equipPower(ServerPlayer player, RegistryObject<Power> registered) {
        Power power = registered.get();
        SkillCapability capability = capabilityOf(player);
        if (capability.isPowerEquipped(power)) return;

        int cap = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;
        for (Skill skill : RegistrySkills.getCachedValues()) {
            capability.setSkillLevel(skill, cap);
        }
        switch (power.getTier()) {
            case SEAL -> equipPower(player, RegistryPowers.TC_FIRST_HEAT);
            case CROWN -> equipPower(player, RegistryPowers.TC_TEMPER_RESERVE);
            default -> { }
        }

        PowerEligibility.Result verdict = PowerEligibility.evaluateEquip(player, power);
        if (!verdict.eligible()) {
            throw new GameTestAssertException("could not equip " + power.getName() + ": "
                    + verdict.reason() + " (required " + verdict.required() + ", actual "
                    + verdict.actual() + "); the fixture, not the Power, is broken");
        }
        capability.equipPower(power);
        if (!PowerDispatch.isEquipped(player, registered)) {
            throw new GameTestAssertException(power.getName()
                    + " was equipped but is not active for the test player");
        }
    }

    /** Lucky Break, the one wear perk every test here uses, at a known percentage. */
    static void enableLuckyBreak(ServerPlayer player) {
        enablePerk(player, RegistryPerks.LUCKY_BREAK);
    }

    /**
     * A real Tinker Station, placed in the test structure with {@code inputs} input slots.
     *
     * <p>Resized rather than left at its default because {@code ToolBuildingRecipe.matches} refuses
     * a station whose input count is not exactly the recipe's part count — the same thing that
     * happens in play when the layout is selected, expressed without a client to select it. The
     * station counts its tool slot in its container size and its inputs as everything else, so the
     * resize asks for one more slot than the caller wants inputs.
     */
    static TinkerStationBlockEntity station(GameTestHelper helper, BlockPos relative, int inputs) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relative);
        level.setBlockAndUpdate(pos, TinkerTables.tinkerStation.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof TinkerStationBlockEntity station)) {
            throw new GameTestAssertException("the Tinker Station did not place a block entity");
        }
        station.resize(inputs + 1);
        return station;
    }

    /** The loaded recipe that builds {@code tool} from parts at the station. */
    static ToolBuildingRecipe buildingRecipeFor(ServerLevel level, IModifiable tool) {
        for (ITinkerStationRecipe recipe
                : level.getRecipeManager().getAllRecipesFor(TinkerRecipeTypes.TINKER_STATION.get())) {
            if (recipe instanceof ToolBuildingRecipe building && building.getOutput() == tool) {
                return building;
            }
        }
        throw new GameTestAssertException(
                "no loaded tool-building recipe produces " + tool + "; the fixture cannot be built");
    }

    /**
     * One part stack per slot of {@code recipe}, each of a material that part actually accepts.
     *
     * <p>Asked of the part rather than fixed at one material: a handle and a head do not accept the
     * same list, and a hand-picked pair that happens to work today is a test that fails when the
     * pack changes.
     */
    static List<ItemStack> partsFor(ToolBuildingRecipe recipe) {
        List<ItemStack> parts = new ArrayList<>();
        for (IToolPart part : recipe.getToolParts()) {
            parts.add(part.withMaterial(materialFor(part).getVariant()));
        }
        return parts;
    }

    /** The lowest-sorted loaded material this part accepts. */
    static MaterialVariant materialFor(IToolPart part) {
        IMaterial best = null;
        for (IMaterial material : MaterialRegistry.getMaterials()) {
            if (material.isHidden() || !part.canUseMaterial(material.getIdentifier())) continue;
            if (best == null || material.getSortOrder() < best.getSortOrder()) best = material;
        }
        if (best == null) {
            throw new GameTestAssertException(
                    "no loaded material fits " + part.asItem() + "; the fixture cannot be built");
        }
        return MaterialVariant.of(best);
    }

    /** The index of the station's result slot in {@code menu}. */
    static int resultSlotIndex(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot instanceof LazyResultSlot) return slot.index;
        }
        throw new GameTestAssertException("the station menu has no lazy result slot");
    }

    /** Leaves the player with no free space at all, for the full-inventory regression cases. */
    static void fillInventory(ServerPlayer player, Item filler) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = new ItemStack(filler);
            stack.setCount(stack.getMaxStackSize());
            player.getInventory().setItem(slot, stack);
        }
    }

    static SkillCapability capabilityOf(ServerPlayer player) {
        return player.getCapability(RegistryCapabilities.SKILL).orElseThrow(
                () -> new GameTestAssertException("player has no Runic Skills capability"));
    }
}
