package com.otectus.runicskills.integration.tom;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.integration.common.*;
import net.minecraft.server.level.ServerPlayer;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Called only by the companion-bound paid-cast subscriber. Reads native functional slots. */
public final class TomTalent {
    private TomTalent() {}
    public static Result availability() {
        var result = TomCastRewards.availability(false);
        return result.available() ? IntegrationRuntime.check(IntegrationModule.TOM, Feature.PERKS, Capability.TALENT_VALIDITY) : result;
    }
    public static boolean equipped(ServerPlayer player) {
        if (!availability().available()) return false;
        var cap = SkillCapability.get(player);
        if (cap == null || !CuriosApi.getEntitySlots(player).containsKey("talent")) return false;
        var inventory = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        var slots = inventory == null ? null : inventory.getCurios().get("talent");
        if (slots == null) return false;
        var stacks = slots.getStacks();
        for (int i = 0; i < Math.min(64, stacks.getSlots()); i++) {
            var stack = stacks.getStackInSlot(i);
            if (!WeaponCombat.owner(stack, "traveloptics") || !WeaponCombat.tagged(stack, "curios:talent")
                    || !cap.canUseItemSilent(player, stack) || !stacks.isItemValid(i, stack)) continue;
            var context = new SlotContext("talent", player, i, false, slots.isVisible());
            if (CuriosApi.isStackValid(context, stack)) return true;
        }
        return false;
    }
}
