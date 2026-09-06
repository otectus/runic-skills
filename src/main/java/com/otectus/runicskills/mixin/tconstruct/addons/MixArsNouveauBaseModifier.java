package com.otectus.runicskills.mixin.tconstruct.addons;

import com.otectus.runicskills.integration.tconstruct.addons.TcIntegrationsAdapter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * Source Tempering's trigger: an Ars armour repair that actually happened.
 *
 * <p><b>Why a before-and-after and not a listener.</b> TCIntegrations' Ars base modifier does its
 * work inside an inventory tick: if the wearer has at least twenty source it removes twenty and
 * reduces the tool's damage by one, all inside a lambda that receives the mana capability and the
 * tool and nothing else. There is no event, no return value and no player at the point of the
 * repair. §10.3 requires both halves — source actually spent <em>and</em> durability actually
 * restored — and forbids inferring success from the tick itself, so the tick is bracketed and the
 * damage difference is read: positive means the add-on took both steps, zero means it took neither.
 *
 * <p>The wearer arrives as the tick's own argument, which is what makes the charge belong to a
 * player rather than to an armour piece — §10.3's "one charge per player, not per armor piece".
 *
 * <p><b>Optional by design.</b> {@code require = 0}, for the reason {@code MixManaModifier} gives:
 * this class only exists on an install that also has Ars Nouveau, no profile here can boot it, and
 * an upstream change must make the perk inert rather than crash a server. The adapter verifies the
 * method shape reflectively at boot and reports the mismatch.
 */
@Mixin(targets = "tcintegrations.items.modifiers.ArsNouveauBaseModifier", remap = false)
public class MixArsNouveauBaseModifier {

    private static final String TICK = "onInventoryTick(Lslimeknights/tconstruct/library/tools/nbt/"
            + "IToolStackView;Lslimeknights/tconstruct/library/modifiers/ModifierEntry;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;IZZ"
            + "Lnet/minecraft/world/item/ItemStack;)V";

    /** Records the damage the tick started from. */
    @Inject(method = TICK, at = @At("HEAD"), remap = false, require = 0, expect = 1)
    private void runicskills$beforeArsRepair(IToolStackView tool, ModifierEntry entry, Level level,
                                             LivingEntity wearer, int slot, boolean selected,
                                             boolean correctSlot, ItemStack stack, CallbackInfo ci) {
        if (level == null || level.isClientSide()) return;
        TcIntegrationsAdapter.beginArsRepair(tool);
    }

    /** Arms the charge when the tick actually restored durability, and clears the record either way. */
    @Inject(method = TICK, at = @At("RETURN"), remap = false, require = 0, expect = 1)
    private void runicskills$afterArsRepair(IToolStackView tool, ModifierEntry entry, Level level,
                                            LivingEntity wearer, int slot, boolean selected,
                                            boolean correctSlot, ItemStack stack, CallbackInfo ci) {
        if (level == null || level.isClientSide()) return;
        TcIntegrationsAdapter.endArsRepair(wearer, tool);
    }
}
