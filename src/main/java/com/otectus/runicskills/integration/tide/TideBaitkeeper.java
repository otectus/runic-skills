package com.otectus.runicskills.integration.tide;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Only consulted inside a verified native shrinkAll/shrinkStack call for a delivered fish. */
public final class TideBaitkeeper {
    private static Method isBait;
    private TideBaitkeeper() {}
    public static void probe() throws ReflectiveOperationException {
        isBait=Class.forName("com.li64.tide.util.BaitUtils",false,TideBaitkeeper.class.getClassLoader()).getMethod("isBait",ItemStack.class);
    }
    public static boolean available() {
        return IntegrationRuntime.check(IntegrationModule.TIDE,Feature.PERKS,Capability.CATCH_COMMIT,Capability.BAIT_CONSUMPTION).available();
    }
    static boolean eligible(Player player,ItemStack stack) {
        return available() && RegistryPerks.TIDE_BAITKEEPER.get().isEnabled(player) && nativeBait(stack);
    }
    static boolean nativeBait(ItemStack stack) {
        if (stack.isEmpty() || isBait == null) return false;
        try { return (Boolean)isBait.invoke(null,stack); }
        catch (ReflectiveOperationException | RuntimeException e) {
            IntegrationRuntime.capability(IntegrationModule.TIDE,Capability.BAIT_CONSUMPTION,"Native bait eligibility read failed; conservation disabled until restart.");
            return false;
        }
    }
    static double chance() { return Math.max(0,Math.min(.25,HandlerCommonConfig.HANDLER.instance().tideBaitkeeperPercent/100.0)); }
}
