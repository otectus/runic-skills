package com.otectus.runicskills.mixin;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.crafting.CraftingRefund;
import com.otectus.runicskills.common.util.ProcRoll;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Efficient Crafting — "Crafting has a X% chance to not consume materials" — as an actual refund of
 * the materials (RS-205-04, spec §8).
 *
 * <p>It used to be a chance to insert one extra copy of the result into the player's inventory,
 * which is not the same promise and, for any recipe whose ingredients outvalue a single output, is
 * a duplication bug wearing a saving's name. Materials are saved here instead, at the one place
 * that knows what a recipe really cost: around vanilla's consumption itself.
 *
 * <p><b>Why the decision lives in {@code onTake} and not in {@code ItemCraftedEvent}.</b> A
 * shift-click crafts many times from one click, and {@code CraftingMenu#quickMoveStack} fires the
 * craft event from {@code onQuickCraft} <em>before</em> {@code onTake} runs. So the event is neither
 * one-per-operation nor correctly ordered against the consumption, while {@code onTake} is exactly
 * one crafting operation, every time, for both a normal pickup and each iteration of a quick move.
 * Rolling the proc at its HEAD gives one independent roll per craft with no counting anywhere.
 *
 * <p><b>Why {@code RETURN} and not {@code TAIL}.</b> The refund must see the grid exactly as the
 * method leaves it — remainders placed, stacks shrunk — and {@code RETURN} is the point that is
 * defined to be after all of that. {@code TAIL} would target the last instruction before the return
 * inside the surrounding loop structure, which is not the same guarantee.
 *
 * <p><b>Why a stale snapshot cannot leak.</b> The field is cleared unconditionally at every HEAD
 * and again as soon as RETURN reads it, so a throw out of vanilla (or out of a mod's remainder
 * logic) between the two leaves nothing behind for the next craft to refund.
 *
 * <p><b>Why modded {@code ResultSlot} subclasses are skipped.</b> A subclass that overrides
 * {@code onTake} may consume from somewhere other than {@code craftSlots}, or consume several
 * recipe sets at once; the diff would then be attributing changes it cannot explain. Spec §8.4.7
 * is explicit that an unsupported transaction must skip the refund rather than guess, and say so
 * once at DEBUG naming the class.
 */
@Mixin(ResultSlot.class)
public abstract class MixResultSlot {

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    /**
     * The grid as it stood before vanilla consumed it, non-null only between HEAD and RETURN of a
     * craft that actually procced. Per-slot state, like the vanilla fields beside it, so two menus
     * crafting in the same tick cannot see each other's snapshot.
     */
    @Unique
    private ItemStack[] runicskills$snapshot;

    /** Subclass names already reported, bounded so a pathological pack cannot grow this forever. */
    @Unique
    private static final Set<String> runicskills$reportedSubclasses = ConcurrentHashMap.newKeySet();

    @Unique
    private static final int RUNICSKILLS$MAX_REPORTED = 64;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void runicskills$snapshotGrid(Player player, ItemStack stack, CallbackInfo ci) {
        this.runicskills$snapshot = null;

        // Server-authoritative: the client runs this method too, and a refund applied there would
        // only survive until the next slot broadcast corrected it.
        if (!(player instanceof ServerPlayer)) return;

        Class<?> self = ((Object) this).getClass();
        if (self != ResultSlot.class) {
            runicskills$reportUnsupported(self);
            return;
        }

        if (RegistryPerks.EFFICIENT_CRAFTING == null
                || !RegistryPerks.EFFICIENT_CRAFTING.get().isEnabled(player)) {
            return;
        }
        if (!ProcRoll.rollsPercent(HandlerCommonConfig.HANDLER.instance().efficientCraftingPercent)) {
            return;
        }

        int size = this.craftSlots.getContainerSize();
        ItemStack[] snapshot = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            snapshot[i] = this.craftSlots.getItem(i).copy();
        }
        this.runicskills$snapshot = snapshot;
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void runicskills$refundConsumedMaterials(Player player, ItemStack stack, CallbackInfo ci) {
        ItemStack[] snapshot = this.runicskills$snapshot;
        this.runicskills$snapshot = null;
        if (snapshot == null) return;

        int size = Math.min(snapshot.length, this.craftSlots.getContainerSize());
        List<CraftingRefund.SlotView> before = new ArrayList<>(size);
        List<CraftingRefund.SlotView> after = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            before.add(runicskills$view(snapshot[i]));
            after.add(runicskills$view(this.craftSlots.getItem(i)));
        }

        List<CraftingRefund.SlotRefund> refunds = CraftingRefund.plan(before, after);
        if (refunds.isEmpty()) return;

        for (CraftingRefund.SlotRefund refund : refunds) {
            int slot = refund.slot();
            switch (refund.action()) {
                case RESTORE_FULL -> this.craftSlots.setItem(slot, snapshot[slot].copy());
                case GROW_ONE -> this.craftSlots.getItem(slot).grow(1);
            }
        }
        this.craftSlots.setChanged();
    }

    /**
     * Reduces a stack to the identity the diff compares on: its item and its NBT, which is where
     * damage lives too. So a tool that came back one use worse is a different key and is never
     * refunded, exactly as an empty bucket is. A two-element {@link List} is the key rather than a
     * dedicated type because {@code List} equality is already element-wise, {@code Item} equality is
     * identity and {@code CompoundTag} equality is by value — the three parts of
     * {@code ItemStack.isSameItemSameTags}, without needing a stack to ask it of. An absent tag is
     * normalised to an empty one; the two describe the same stack.
     */
    @Unique
    private static CraftingRefund.SlotView runicskills$view(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CraftingRefund.SlotView(null, 0);
        CompoundTag tag = stack.getTag();
        return new CraftingRefund.SlotView(
                List.of(stack.getItem(), tag == null ? new CompoundTag() : tag.copy()),
                stack.getCount());
    }

    /** One DEBUG line per unsupported slot class, so a compatibility question stays answerable. */
    @Unique
    private static void runicskills$reportUnsupported(Class<?> slotClass) {
        String name = slotClass.getName();
        if (runicskills$reportedSubclasses.size() >= RUNICSKILLS$MAX_REPORTED) return;
        if (!runicskills$reportedSubclasses.add(name)) return;
        RunicSkills.getLOGGER().debug(
                "Efficient Crafting skips {}: a ResultSlot subclass may consume outside the "
                        + "crafting grid, so the refund cannot be inferred safely", name);
    }
}
