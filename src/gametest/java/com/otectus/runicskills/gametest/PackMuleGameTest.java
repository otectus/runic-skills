package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.inventory.InventoryReconciliation;
import com.otectus.runicskills.common.inventory.PlayerStackPolicy;
import com.otectus.runicskills.common.inventory.StackCapacityMath;
import com.otectus.runicskills.common.inventory.StackRepresentationProvider;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;

@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class PackMuleGameTest {
    static ServerPlayer player(GameTestHelper h, String name, int rank) {
        ServerPlayer p = MockPlayers.connectedServerPlayer(h, name);
        var cap = SkillCapability.get(p);
        cap.setSkillLevel(RegistrySkills.STRENGTH.get(), 32);
        cap.setPerkRank(RegistryPerks.PACK_MULE.get(), rank);
        return p;
    }
    /**
     * Ends a Pack-Mule-capacity test early when the perk is deferred.
     *
     * <p>The tests below assert the 128/192/256 grant. Under a foreign stack representation that
     * grant does not exist — reference document §4.5 — so those numbers describe a rule this
     * installation does not have, and asserting them would be asserting the bug. What must be true
     * instead is checked by {@link #stackRepresentationOwnershipMatchesTheInstalledMods}: no second
     * codec, no multiplication of a foreign maximum, a reported reason, and conservation through a
     * cancelled toss and ordinary chest clicks.
     */
    static boolean capacityDeferred(GameTestHelper h) {
        if (StackRepresentationProvider.selected().grantsPackMuleCapacity()) return false;
        h.assertTrue(!PlayerStackPolicy.deferralReason().isEmpty(), "capacity deferred without a reported reason");
        h.succeed();
        return true;
    }
    @GameTest(template = "empty")
    public static void countsRoundTripWithMetadataAndFollowingFields(GameTestHelper h) {
        for (int count : new int[]{0, 1, 16, 63, 64, 65, 127, 128, 129, 191, 192, 193, 255, 256}) {
            ItemStack item = new ItemStack(Items.STONE, count);
            if (count > 0) item.getOrCreateTag().putString("identity", "named cargo");
            for (int repeat = 0; repeat < 3; repeat++) {
                item = ItemStack.of(item.save(new CompoundTag()));
                h.assertTrue(item.getCount() == count, "save count " + count);
                FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    wire.writeItemStack(item, repeat % 2 == 0); wire.writeUtf("following field");
                    ItemStack received = wire.readItem();
                    h.assertTrue(received.getCount() == count, "wire count " + count);
                    h.assertTrue(ItemStack.isSameItemSameTags(item, received), "wire identity " + count);
                    h.assertTrue(wire.readUtf().equals("following field") && wire.readableBytes() == 0, "packet framing");
                } finally { wire.release(); }
            }
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void ranksPickupAndTwoOwnersRemainIndependent(GameTestHelper h) {
        if (capacityDeferred(h)) return;
        for (int rank = 0; rank <= 3; rank++) {
            ServerPlayer p = player(h, "pack_rank_" + rank, rank);
            for (int i = 0; i <= rank; i++) p.getInventory().add(new ItemStack(Items.STONE, 64));
            h.assertTrue(p.getInventory().getItem(0).getCount() == 64 * (rank + 1), "rank capacity " + rank);
            p.getInventory().add(new ItemStack(Items.STONE, 1));
            h.assertTrue(p.getInventory().getItem(1).getCount() == 1, "overflow remainder");
            h.assertTrue(PlayerStackPolicy.capacity(p, new ItemStack(Items.ENDER_PEARL)) == 16, "natural 16");
            h.assertTrue(PlayerStackPolicy.capacity(p, new ItemStack(Items.DIAMOND_SWORD)) == 1, "natural 1");
        }
        h.assertTrue(new ItemStack(Items.STONE).getMaxStackSize() == 64, "item singleton capacity leaked");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void nativeChestClicksConserveAndRespectStorage(GameTestHelper h) {
        if (capacityDeferred(h)) return;
        ServerPlayer p = player(h, "pack_chest", 3);
        SimpleContainer chest = new SimpleContainer(27);
        ChestMenu menu = ChestMenu.threeRows(77, p.getInventory(), chest);
        p.containerMenu = menu;
        menu.setCarried(new ItemStack(Items.STONE, 256));
        menu.clicked(0, 0, ClickType.PICKUP, p);
        h.assertTrue(chest.getItem(0).getCount() == 64 && menu.getCarried().getCount() == 192, "chest cursor boundary");
        menu.clicked(27, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getSlot(27).getItem().getCount() == 192 && menu.getCarried().isEmpty(), "player cursor boundary");
        menu.clicked(0, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(chest.getItem(0).isEmpty() && total(menu) == 256, "chest to player conservation");
        int occupied = -1;
        for (int i = 27; i < menu.slots.size(); i++) if (!menu.getSlot(i).getItem().isEmpty()) occupied = i;
        h.assertTrue(occupied >= 0 && menu.getSlot(occupied).getItem().getCount() == 256, "quick move aggregates");
        menu.clicked(occupied, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(total(menu) == 256, "player to chest conservation");
        for (int i = 0; i < 27; i++) h.assertTrue(chest.getItem(i).getCount() <= 64, "chest native limit");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void wrappersSimulateWithoutMutationAndFullRespecRetainsCounts(GameTestHelper h) {
        if (capacityDeferred(h)) return;
        ServerPlayer p = player(h, "pack_wrapper", 3);
        PlayerInvWrapper wrapper = new PlayerInvWrapper(p.getInventory());
        ItemStack source = new ItemStack(Items.STONE, 257);
        h.assertTrue(wrapper.insertItem(0, source, true).getCount() == 1, "simulation remainder");
        h.assertTrue(wrapper.getStackInSlot(0).isEmpty() && source.getCount() == 257, "simulation mutated");
        h.assertTrue(wrapper.insertItem(0, source, false).getCount() == 1, "insert remainder");
        h.assertTrue(wrapper.getStackInSlot(0).getCount() == 256, "actual inserted count");
        for (int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        SkillCapability.get(p).setPerkRank(RegistryPerks.PACK_MULE.get(), 0);
        h.assertTrue(InventoryReconciliation.normalize(p, false) == 1 && wrapper.getStackInSlot(0).getCount() == 256, "full respec lost items");
        h.assertTrue(wrapper.insertItem(0, new ItemStack(Items.STONE), false).getCount() == 1, "over-limit stack grew");
        for (int i = 1; i <= 3; i++) p.getInventory().setItem(i, ItemStack.EMPTY);
        h.assertTrue(InventoryReconciliation.normalize(p, false) == 0, "normalization failed");
        for (int i = 0; i <= 3; i++) h.assertTrue(wrapper.getStackInSlot(i).getCount() == 64, "normalization count");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void deathCaptureAndCanceledTossPreserveAllCargo(GameTestHelper h) {
        if (capacityDeferred(h)) return;
        ServerPlayer p = player(h, "pack_death", 3);
        p.getInventory().setItem(0, new ItemStack(Items.STONE, 256));
        var drops = new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
        p.captureDrops(drops);
        try { p.getInventory().dropAll(); } finally { p.captureDrops(null); }
        h.assertTrue(drops.size() == 4 && drops.stream().allMatch(e -> e.getItem().getCount() == 64), "native death split");
        h.assertTrue(p.getInventory().isEmpty(), "death source not consumed");
        Object cancel = new Object() {
            @net.minecraftforge.eventbus.api.SubscribeEvent
            public void toss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
                if (event.getPlayer() == p) event.setCanceled(true);
            }
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(cancel);
        try {
            p.drop(new ItemStack(Items.STONE, 256), true);
            InventoryReconciliation.restore(p);
            h.assertTrue(p.getInventory().getItem(0).getCount() == 256, "canceled whole toss lost cargo");
            p.containerMenu = p.inventoryMenu;
            p.inventoryMenu.clicked(36, 0, ClickType.THROW, p);
            h.assertTrue(p.getInventory().getItem(0).getCount() == 255, "single toss did not remove exactly one before recovery");
        }
        finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancel); }
        InventoryReconciliation.restore(p);
        h.assertTrue(p.getInventory().getItem(0).getCount() == 256, "canceled drop lost cargo");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void refusedDeathSpawnsRecoverAndGravesKeepOwnership(GameTestHelper h) {
        ServerPlayer p = player(h, "pack_refused_death", 3);
        p.getInventory().setItem(0, new ItemStack(Items.STONE, 256));
        var cargo = p.getInventory().getItem(0); cargo.getOrCreateTag().putBoolean("pack_death_fixture", true);
        Object refuse = new Object() {
            @net.minecraftforge.eventbus.api.SubscribeEvent public void refuseDeathEntity(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
                if (event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item
                        && item.getItem().hasTag() && item.getItem().getTag().getBoolean("pack_death_fixture")) event.setCanceled(true);
            }
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(refuse);
        try { p.die(p.damageSources().generic()); }
        finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(refuse); }
        InventoryReconciliation.restore(p);
        h.assertTrue(p.getInventory().items.stream().mapToInt(ItemStack::getCount).sum() == 256, "refused death spawn lost cargo");
        ServerPlayer graveOwner = player(h, "pack_grave", 3);
        graveOwner.getInventory().setItem(0, new ItemStack(Items.STONE, 256));
        java.util.List<ItemStack> grave = new java.util.ArrayList<>();
        Object capture = new Object() {
            @net.minecraftforge.eventbus.api.SubscribeEvent public void graveOwnsDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
                if (event.getEntity() == graveOwner) { event.getDrops().forEach(e -> grave.add(e.getItem().copy())); event.setCanceled(true); }
            }
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(capture);
        try { graveOwner.die(graveOwner.damageSources().generic()); }
        finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(capture); }
        InventoryReconciliation.restore(graveOwner);
        h.assertTrue(grave.stream().mapToInt(ItemStack::getCount).sum() == 256 && graveOwner.getInventory().isEmpty(), "grave ownership duplicated");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void creativeCloneThrowAndMenuCloseRespectOwner(GameTestHelper h) {
        if (capacityDeferred(h)) return;
        ServerPlayer p = player(h, "pack_clone", 3);
        var menu = ChestMenu.threeRows(81, p.getInventory(), new SimpleContainer(27)); p.containerMenu = menu;
        menu.getSlot(0).set(new ItemStack(Items.STONE, 64));
        menu.clicked(0, 2, ClickType.CLONE, p);
        h.assertTrue(menu.getCarried().isEmpty(), "survival clone granted cargo");
        p.getAbilities().instabuild = true;
        menu.clicked(0, 2, ClickType.CLONE, p);
        h.assertTrue(menu.getCarried().getCount() == 256, "creative clone capacity");
        menu.removed(p);
        h.assertTrue(menu.getCarried().isEmpty() && p.getInventory().getItem(0).getCount() == 256, "close lost cursor");
        var drops = new java.util.ArrayList<ItemStack>();
        Object observer = new Object() {
            @net.minecraftforge.eventbus.api.SubscribeEvent public void nativeMenuToss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
                if (event.getPlayer() == p) drops.add(event.getEntity().getItem().copy());
            }
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(observer);
        try { menu.clicked(54, 1, ClickType.THROW, p); }
        finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(observer); }
        h.assertTrue(drops.stream().mapToInt(ItemStack::getCount).sum() == 256
                && drops.stream().allMatch(e -> e.getCount() <= 64) && p.getInventory().isEmpty(), "native throw failed conservation");
        ServerPlayer low = player(h, "pack_low_clone", 0); low.getAbilities().instabuild = true;
        var lowMenu = ChestMenu.threeRows(82, low.getInventory(), menu.getSlot(0).container); low.containerMenu = lowMenu;
        lowMenu.clicked(0, 2, ClickType.CLONE, low);
        h.assertTrue(lowMenu.getCarried().getCount() == 64, "another viewer inherited capacity");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void dragSwapAndDoubleClickConserveNativeMenus(GameTestHelper h) {
        ServerPlayer p = player(h, "pack_clicks", 3);
        SimpleContainer chest = new SimpleContainer(27);
        ChestMenu menu = ChestMenu.threeRows(78, p.getInventory(), chest);
        p.containerMenu = menu;
        menu.setCarried(new ItemStack(Items.STONE, 256));
        menu.clicked(-999, 0, ClickType.QUICK_CRAFT, p);
        menu.clicked(0, 1, ClickType.QUICK_CRAFT, p);
        menu.clicked(27, 1, ClickType.QUICK_CRAFT, p);
        menu.clicked(-999, 2, ClickType.QUICK_CRAFT, p);
        h.assertTrue(total(menu) == 256 && chest.getItem(0).getCount() <= 64, "drag conservation and destination");
        menu.clicked(28, 0, ClickType.PICKUP, p); // put the remainder away
        menu.clicked(0, 0, ClickType.SWAP, p);
        h.assertTrue(total(menu) == 256 && chest.getItem(0).getCount() <= 64, "hotbar swap");
        menu.clicked(54, 0, ClickType.PICKUP, p);
        menu.clicked(1, 0, ClickType.PICKUP_ALL, p);
        h.assertTrue(total(menu) == 256, "double-click conservation");
        menu.clicked(1, 40, ClickType.SWAP, p);
        h.assertTrue(total(menu) + p.getOffhandItem().getCount() == 256, "offhand swap conservation");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void randomizedNativeMenuTransactionsConserveEveryItem(GameTestHelper h) {
        ServerPlayer p = player(h, "pack_random", 3);
        SimpleContainer chest = new SimpleContainer(27);
        ChestMenu menu = ChestMenu.threeRows(79, p.getInventory(), chest); p.containerMenu = menu;
        for (int i = 0; i < 9; i++) { chest.setItem(i, new ItemStack(Items.STONE, 64)); p.getInventory().setItem(i, new ItemStack(Items.STONE, 256)); }
        int initial = total(menu);
        var random = new java.util.Random(220);
        ClickType[] kinds = {ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP, ClickType.PICKUP_ALL};
        for (int i = 0; i < 2000; i++) {
            ClickType kind = kinds[random.nextInt(kinds.length)];
            int button = kind == ClickType.SWAP ? random.nextInt(9) : random.nextInt(2);
            menu.clicked(random.nextInt(menu.slots.size()), button, kind, p);
            h.assertTrue(total(menu) == initial, "native click conservation at operation " + i + "/" + kind);
            for (int slot = 0; slot < 27; slot++) h.assertTrue(chest.getItem(slot).getCount() <= 64, "storage cap at " + i);
            h.assertTrue(menu.getCarried().getCount() <= 256, "cursor capacity at " + i);
        }
        for (int invalid : new int[]{-2000, -2, 99999}) menu.clicked(invalid, 0, ClickType.PICKUP, p);
        menu.clicked(0, 99, ClickType.SWAP, p);
        h.assertTrue(total(menu) == initial, "malformed clicks changed ownership");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void nativeCraftingPaysForAll256Outputs(GameTestHelper h) {
        if (capacityDeferred(h)) return;
        ServerPlayer p = player(h, "pack_craft", 3);
        var pos = h.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        h.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        var menu = new net.minecraft.world.inventory.CraftingMenu(80, p.getInventory(),
                net.minecraft.world.inventory.ContainerLevelAccess.create(h.getLevel(), pos));
        p.containerMenu = menu;
        menu.getSlot(1).set(new ItemStack(Items.OAK_LOG, 64));
        menu.slotsChanged(menu.getSlot(1).container);
        h.assertTrue(menu.getSlot(0).getItem().is(Items.OAK_PLANKS), "real recipe did not resolve");
        menu.clicked(0, 0, ClickType.QUICK_MOVE, p);
        int total = p.getInventory().items.stream().filter(v -> v.is(Items.OAK_PLANKS)).mapToInt(ItemStack::getCount).sum();
        h.assertTrue(total == 256 && menu.getSlot(1).getItem().isEmpty(), "recipe payments/output conservation: " + total);
        h.assertTrue(p.getInventory().items.stream().anyMatch(v -> v.getCount() == 256), "output did not aggregate in player inventory");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void malformedCountsAndCreativeForgeryCannotGrantItems(GameTestHelper h) {
        if (capacityDeferred(h)) return;
        var unsupported = new ItemStack(Items.STONE).save(new CompoundTag());
        unsupported.putInt("Count", Integer.MAX_VALUE); unsupported.getCompound("tag").putString("identity", "foreign count");
        var recovery = com.otectus.runicskills.common.inventory.StackDataRecovery.preserveInvalid(unsupported);
        try { h.assertTrue(net.minecraft.nbt.NbtIo.readCompressed(recovery.toFile()).equals(unsupported), "invalid saved data was not preserved losslessly"); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        for (int count : new int[]{0, -1, 127, Integer.MAX_VALUE}) {
            var buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                buf.writeBoolean(true); buf.writeVarInt(net.minecraft.world.item.Item.getId(Items.STONE)); buf.writeByte(-128); buf.writeVarInt(count); buf.writeNbt(null);
                boolean rejected = false;
                try { buf.readItem(); } catch (io.netty.handler.codec.DecoderException expected) { rejected = true; }
                h.assertTrue(rejected, "malformed extended count accepted: " + count);
            } finally { buf.release(); }
        }
        ServerPlayer p = player(h, "pack_forgery", 3);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        p.connection.handleSetCreativeModeSlot(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(36, new ItemStack(Items.STONE, 256)));
        h.assertTrue(p.getInventory().getItem(0).getCount() == 256, "legitimate creative capacity");
        p.connection.handleSetCreativeModeSlot(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(36, new ItemStack(Items.DIAMOND, 257)));
        h.assertTrue(p.getInventory().getItem(0).is(Items.STONE), "creative forged count was installed");
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.connection.handleSetCreativeModeSlot(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(37, new ItemStack(Items.DIAMOND, 256)));
        h.assertTrue(p.getInventory().getItem(1).isEmpty(), "survival creative packet minted items");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void recipeBookAndSmallGridConserveNativePayments(GameTestHelper h) {
        ServerPlayer p = player(h, "pack_recipe_book", 3);
        p.getInventory().setItem(0, new ItemStack(Items.WHEAT, 256));
        var pos = h.absolutePos(new net.minecraft.core.BlockPos(1,1,1));
        h.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        var menu = new net.minecraft.world.inventory.CraftingMenu(83, p.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(h.getLevel(),pos));
        p.containerMenu = menu;
        var recipe = h.getLevel().getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("minecraft:bread")).orElseThrow();
        p.awardRecipes(java.util.List.of(recipe)); menu.handlePlacement(true, recipe, p);
        int grid = 0;
        for (int i=1;i<=9;i++) { var stack=menu.getSlot(i).getItem(); grid+=stack.getCount(); h.assertTrue(stack.getCount()<=64,"recipe book enlarged ingredient slot"); }
        h.assertTrue(grid>0 && grid+p.getInventory().items.stream().filter(s->s.is(Items.WHEAT)).mapToInt(ItemStack::getCount).sum()==256,"recipe placement lost ingredients");
        menu.clicked(0,0,ClickType.QUICK_MOVE,p);
        int bread=p.getInventory().items.stream().filter(s->s.is(Items.BREAD)).mapToInt(ItemStack::getCount).sum();
        int wheat=p.getInventory().items.stream().filter(s->s.is(Items.WHEAT)).mapToInt(ItemStack::getCount).sum();
        for(int i=1;i<=9;i++) if(menu.getSlot(i).getItem().is(Items.WHEAT)) wheat+=menu.getSlot(i).getItem().getCount();
        h.assertTrue(bread>0 && wheat+3*bread==256,"recipe book craft conservation");
        menu.removed(p); p.getInventory().clearContent(); p.containerMenu=p.inventoryMenu;
        p.inventoryMenu.getSlot(1).set(new ItemStack(Items.OAK_LOG,64)); p.inventoryMenu.slotsChanged(p.inventoryMenu.getSlot(1).container);
        p.inventoryMenu.clicked(0,0,ClickType.QUICK_MOVE,p);
        h.assertTrue(p.getInventory().items.stream().filter(s->s.is(Items.OAK_PLANKS)).mapToInt(ItemStack::getCount).sum()==256
                && p.inventoryMenu.getSlot(1).getItem().isEmpty(),"2x2 native payments lost or duplicated output");
        h.succeed();
    }
    /**
     * Who owns the count representation, and that Pack Mule's on/off behaviour does not depend on
     * the answer changing underneath it.
     *
     * <p>The Runic branch is the shipped default and is asserted outright: the representation is
     * Runic, both count hooks are live, the bound is the Runic one, and rank 0 and rank 3 differ
     * only in granted capacity. {@code countsRoundTripWithMetadataAndFollowingFields} above proves
     * both halves of the hook split actually applied — it saves and reads NBT and writes and reads
     * a packet at 128 and above, which is exactly what {@code MixItemStackCount} and
     * {@code MixFriendlyByteBuf} are for.
     *
     * <p>The delegated branch is guarded on the other mod being present, so this test is meaningful
     * in the default profile and in a {@code -PbiggerStacksProfile=true} run without being two
     * tests that contradict each other.
     */
    @GameTest(template = "empty")
    public static void stackRepresentationOwnershipMatchesTheInstalledMods(GameTestHelper h) {
        var provider = StackRepresentationProvider.selected();
        h.assertTrue(!StackRepresentationProvider.selectionDetail().isBlank(), "no selection diagnostic");
        if (!net.minecraftforge.fml.ModList.get().isLoaded("biggerstacks")) {
            h.assertTrue(provider == StackRepresentationProvider.RUNIC, "unexpected owner " + provider);
            h.assertTrue(provider.ownsNbtCount() && provider.ownsNetworkCount(), "Runic count hooks inactive");
            h.assertTrue(provider.maxRepresentableCount() == StackCapacityMath.MAX_SERIALIZED_COUNT, "Runic bound changed");
            h.assertTrue(PlayerStackPolicy.deferralReason().isEmpty(), "Pack Mule deferred with no foreign provider");
            ServerPlayer off = player(h, "pack_repr_off", 0);
            ServerPlayer on = player(h, "pack_repr_on", 3);
            h.assertTrue(PlayerStackPolicy.capacity(off, new ItemStack(Items.STONE)) == 64, "rank 0 capacity");
            h.assertTrue(PlayerStackPolicy.capacity(on, new ItemStack(Items.STONE)) == 256, "rank 3 capacity");
            h.assertTrue(StackRepresentationProvider.selected() == provider, "the perk changed the wire format");
        } else {
            h.assertTrue(!provider.ownsNbtCount() && !provider.ownsNetworkCount(),
                    "a second count codec was installed alongside Bigger Stacks");
            h.assertTrue(!provider.grantsPackMuleCapacity() && !PlayerStackPolicy.deferralReason().isEmpty(),
                    "Pack Mule was layered on a foreign provider without a reported reason");
            ServerPlayer delegated = player(h, "pack_repr_delegated", 3);
            ItemStack stone = new ItemStack(Items.STONE);
            h.assertTrue(PlayerStackPolicy.capacity(delegated, stone) == stone.getMaxStackSize(),
                    "foreign native maximum was multiplied");
            h.assertTrue(StackCapacityMath.representable(StackCapacityMath.MAX_SERIALIZED_COUNT + 1L),
                    "a supported external count was judged by the Runic limit");
            // Deferring the perk must not cost the invariants that have nothing to do with it.
            // MixPackMuleDrops and MixInventory's death-drop hook stay applied for exactly this:
            // a refused toss still comes back to its owner, whole.
            ServerPlayer tossed = player(h, "pack_repr_toss", 3);
            tossed.getInventory().setItem(0, new ItemStack(Items.STONE, 256));
            Object cancel = new Object() {
                @net.minecraftforge.eventbus.api.SubscribeEvent
                public void toss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
                    if (event.getPlayer() == tossed) event.setCanceled(true);
                }
            };
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(cancel);
            try { tossed.drop(tossed.getInventory().removeItemNoUpdate(0), true); }
            finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancel); }
            InventoryReconciliation.restore(tossed);
            h.assertTrue(tossed.getInventory().items.stream().mapToInt(ItemStack::getCount).sum() == 256,
                    "a cancelled toss lost cargo under a foreign representation");
            // Ordinary container traffic still conserves, with the foreign limits in charge.
            ServerPlayer clicker = player(h, "pack_repr_clicks", 3);
            SimpleContainer chest = new SimpleContainer(27);
            ChestMenu menu = ChestMenu.threeRows(84, clicker.getInventory(), chest);
            clicker.containerMenu = menu;
            menu.setCarried(new ItemStack(Items.STONE, 256));
            int initial = total(menu);
            var random = new java.util.Random(221);
            ClickType[] kinds = {ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP, ClickType.PICKUP_ALL};
            for (int i = 0; i < 500; i++) {
                menu.clicked(random.nextInt(menu.slots.size()), random.nextInt(2),
                        kinds[random.nextInt(kinds.length)], clicker);
                h.assertTrue(total(menu) == initial, "click conservation under a foreign representation at " + i);
            }
        }
        h.succeed();
    }
    static int total(ChestMenu menu) {
        return menu.slots.stream().mapToInt(s -> s.getItem().getCount()).sum() + menu.getCarried().getCount();
    }
}
