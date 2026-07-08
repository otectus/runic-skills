package com.otectus.runicskills.integration;

import com.otectus.runicskills.common.util.ForgingQualityMath;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Overgeared (realistic forging) integration — Tinkering-tree perks. Forge-only by design: Overgeared
 * fires vanilla {@code PlayerEvent.ItemCraftedEvent} when a forged result is taken from its smithing
 * anvils and its three furnaces use vanilla {@code FurnaceResultSlot} (→ {@code ItemSmeltedEvent});
 * forging quality is the plain item-NBT string {@code "ForgingQuality"} whose stats Overgeared derives
 * dynamically per query, so a post-craft tag upgrade is a legitimate stat change. No
 * {@code net.stirdrem.overgeared} types are referenced, so this class is safe to load with the mod
 * absent (verified against Overgeared 1.6.x for 1.20.1).
 *
 * <p>Deliberately NOT implemented (no stable upstream hook): changing minigame tolerance/zones
 * (client-side static state), failure-roll reduction and fuel/input preservation (both internal to the
 * anvil/furnace block entities). Quality upgrades are hard-capped at {@code perfect} — Masterwork
 * remains exclusively Overgeared's own reward path.</p>
 */
public class OvergearedIntegration {

    private static final String MOD_ID = "overgeared";
    private static final String QUALITY_TAG = "ForgingQuality";
    /** Blueprint progress NBT (see Overgeared BlueprintItem / AbstractSmithingAnvilBlockEntity). */
    private static final String BLUEPRINT_QUALITY_TAG = "Quality";
    private static final String BLUEPRINT_USES_TAG = "Uses";
    /** Defensive ceiling for blueprint bonus progress; upstream thresholds are two digits. */
    private static final int MAX_BLUEPRINT_USES = 10_000;

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player == null || player instanceof FakePlayer || player.level().isClientSide()) return;
        if (!isModLoaded()) return;

        ItemStack result = event.getCrafting();
        CompoundTag tag = result.getTag();
        if (tag == null || !tag.contains(QUALITY_TAG, CompoundTag.TAG_STRING)) return;
        // Only Overgeared stamps ForgingQuality, and its anvil menus pass their own crafting
        // container to the event — double-check the origin so a coincidental tag from another
        // mod's crafting grid is never touched.
        if (!fromOvergeared(event)) return;
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();

        // STEADY_HAMMER — chance to salvage a poor outcome up to well-forged.
        if (RegistryPerks.STEADY_HAMMER != null && RegistryPerks.STEADY_HAMMER.get().isEnabled(player)
                && player.getRandom().nextDouble() < cfg.steadyHammerPercent / 100.0) {
            String mitigated = ForgingQualityMath.mitigatePoor(tag.getString(QUALITY_TAG));
            if (mitigated != null) tag.putString(QUALITY_TAG, mitigated);
        }

        // MASTER_SMITH — late-game chance for one bonus tier, never past perfect.
        if (RegistryPerks.MASTER_SMITH != null && RegistryPerks.MASTER_SMITH.get().isEnabled(player)
                && player.getRandom().nextDouble() < cfg.masterSmithPercent / 100.0) {
            String upgraded = ForgingQualityMath.nextQuality(tag.getString(QUALITY_TAG));
            if (upgraded != null) tag.putString(QUALITY_TAG, upgraded);
        }

        // BLUEPRINT_SAVANT — chance for the blueprint used in this forging to gain one bonus point of
        // Uses progress. Only the counter is bumped: level-up itself stays with Overgeared's own
        // increment, whose ">= usesToLevel" check absorbs any overshoot safely.
        if (RegistryPerks.BLUEPRINT_SAVANT != null && RegistryPerks.BLUEPRINT_SAVANT.get().isEnabled(player)
                && player.getRandom().nextDouble() < cfg.blueprintSavantPercent / 100.0) {
            ItemStack blueprint = findOpenBlueprint(player);
            if (!blueprint.isEmpty()) {
                CompoundTag bp = blueprint.getTag();
                int uses = bp.getInt(BLUEPRINT_USES_TAG);
                if (uses >= 0 && uses < MAX_BLUEPRINT_USES) bp.putInt(BLUEPRINT_USES_TAG, uses + 1);
            }
        }
    }

    // METALLURGIST — chance for one bonus item when taking Overgeared smelting output (alloy, nether
    // alloy and cast furnaces all use vanilla FurnaceResultSlot, which fires this event). Uses the
    // same bonus-copy idiom as PerkEffectsHandler.onCraft, so quick-move transfers stay dupe-safe.
    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        Player player = event.getEntity();
        if (player == null || player instanceof FakePlayer || player.level().isClientSide()) return;
        if (!isModLoaded()) return;

        ItemStack result = event.getSmelting();
        if (result.isEmpty() || !isNamespace(result, MOD_ID)) return;
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        if (RegistryPerks.METALLURGIST != null && RegistryPerks.METALLURGIST.get().isEnabled(player)
                && player.getRandom().nextDouble() < cfg.metallurgistPercent / 100.0) {
            ItemStack bonus = result.copy();
            bonus.setCount(1);
            player.getInventory().placeItemBackInInventory(bonus);
        }
    }

    /** True when the craft event originated from an Overgeared menu/container (class-name check only). */
    private static boolean fromOvergeared(PlayerEvent.ItemCraftedEvent event) {
        if (event.getInventory() != null
                && event.getInventory().getClass().getName().startsWith("net.stirdrem.overgeared")) return true;
        // Fallback: the anvil menu is still open on the crafting player while the result is taken.
        Player player = event.getEntity();
        return player.containerMenu != null
                && player.containerMenu.getClass().getName().startsWith("net.stirdrem.overgeared");
    }

    /**
     * The blueprint sitting in the player's open Overgeared anvil menu, or EMPTY. The craft event's
     * container only exposes the 3x3 grid, so the blueprint slot is reached through the open
     * {@code containerMenu} using vanilla Slot API only.
     */
    private static ItemStack findOpenBlueprint(Player player) {
        if (player.containerMenu == null
                || !player.containerMenu.getClass().getName().startsWith("net.stirdrem.overgeared")) {
            return ItemStack.EMPTY;
        }
        for (Slot slot : player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !isNamespace(stack, MOD_ID)) continue;
            CompoundTag tag = stack.getTag();
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null && id.getPath().contains("blueprint")
                    && tag != null
                    && tag.contains(BLUEPRINT_QUALITY_TAG, CompoundTag.TAG_STRING)
                    && tag.contains(BLUEPRINT_USES_TAG, CompoundTag.TAG_INT)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isNamespace(ItemStack stack, String namespace) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && namespace.equals(id.getNamespace());
    }
}
