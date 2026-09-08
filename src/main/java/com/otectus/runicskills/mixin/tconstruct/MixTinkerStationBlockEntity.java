package com.otectus.runicskills.mixin.tconstruct;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.tconstruct.TConstructStationBridge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;

/**
 * The moment a native station take is committed.
 *
 * <p>{@code onCraft(Player, ItemStack, int)} carries the player as a parameter and, in 3.11.2.166,
 * fires the crafting event and then shrinks its inputs — in that order. Injecting at the head
 * therefore lands after the take has been accepted and while the inputs are still there to be
 * described, which is what {@link TConstructStationBridge} needs to classify the operation from the
 * station's own recipe rather than from a guess about a container that is not a crafting grid.
 *
 * <p>The method returns early when there is no recipe or no result, so the guards in the bridge are
 * about a station that <em>has</em> committed rather than about one that might.
 *
 * <p>String target and {@code remap = false}, gated in {@code RunicSkillsMixinPlugin}: the same
 * arrangement as every other mixin in this package, and for the same reason — Tinkers' classes are
 * not obfuscated, so there is no refmap entry to produce and nothing to resolve on an install that
 * does not have them.
 */
@Mixin(targets = "slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity",
        remap = false)
public class MixTinkerStationBlockEntity {

    /** One local scope per invocation; nested crafts and exceptions cannot strand the frame. */
    @WrapMethod(method = "onCraft", remap = false,
            require = 0, expect = 1)
    private void runicskills$stationCraft(Player player, ItemStack result, int amount,
                                          Operation<Void> original) {
        TinkerStationBlockEntity station = (TinkerStationBlockEntity) (Object) this;
        boolean opened = TConstructStationBridge.beginCraft(player);
        try {
            TConstructStationBridge.onStationCraft(player, result, station, station);
            original.call(player, result, amount);
        } finally {
            TConstructStationBridge.endCraft(opened);
        }
    }
}
