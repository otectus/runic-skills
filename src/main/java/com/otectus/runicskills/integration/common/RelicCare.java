package com.otectus.runicskills.integration.common;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Refines a fresh native anvil output; inputs, resource count, XP cost and extraction remain vanilla. */
public final class RelicCare {
    private RelicCare() {}
    public static boolean available(String id) {
        var module = id.startsWith("sm_") ? IntegrationModule.SIMPLY_MORE : IntegrationModule.SIMPLY_SWORDS;
        return IntegrationRuntime.check(module, Feature.PERKS, Capability.MANUAL_REPAIR).available()
                && IntegrationRuntime.check(module, Feature.WORKSHOP, Capability.MANUAL_REPAIR).available();
    }
    public static boolean refine(Player player, ItemStack left, ItemStack output, boolean nativeRepair) {
        if (!nativeRepair || player == null || player instanceof FakePlayer || !player.isAlive()
                || left.isEmpty() || output.isEmpty() || left.getItem() != output.getItem()
                || left.getCount() != 1 || output.getCount() != 1 || !left.isDamageableItem()
                || output.getDamageValue() >= left.getDamageValue()) return false;
        var cap = SkillCapability.get(player);
        if (cap == null || !cap.canUseItemSilent(player, left)) return false;
        boolean swords = WeaponCombat.owner(left, "simplyswords") && WeaponCombat.tagged(left, "simplyswords:uniques");
        boolean more = WeaponCombat.owner(left, "simplymore") && WeaponCombat.tagged(left, "simplymore:uniques");
        if (!swords && !more) return false;
        var perk = swords ? RegistryPerks.SS_RELIC_CARE.get() : RegistryPerks.SM_RELIC_CARE.get();
        if (!perk.isEnabled(player)) return false;
        var cfg = HandlerCommonConfig.HANDLER.instance();
        int extra = IntegrationLimits.additionalRepair(left.getDamageValue() - output.getDamageValue(),
                output.getDamageValue(), (swords ? cfg.ssRelicCarePercent : cfg.smRelicCarePercent) / 100.0);
        if (extra <= 0) return false;
        output.setDamageValue(output.getDamageValue() - extra);
        return true;
    }
}
