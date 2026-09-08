package com.otectus.runicskills.integration.tom;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import com.otectus.runicskills.integration.common.*;
/** Public ISS casting state before and after the actual native cancel operation. */
public final class TomCounterspell {
    public record Receipt(ServerPlayer player,IMagicEntity target,long revision) {}
    private TomCounterspell() {}
    public static Receipt before(Object target) {
        var player=TomPaidCasts.manualCaster("irons_spellbooks:counterspell");
        if(player==null || !(target instanceof LivingEntity living) || !(target instanceof IMagicEntity magic)
                || !magic.isCasting() || !magic.getMagicData().isCasting() || !WeaponCombat.eligible(player,living))return null;
        var id=ForgeRegistries.ENTITY_TYPES.getKey(living.getType());
        return id!=null && id.getNamespace().equals("traveloptics")?new Receipt(player,magic,IntegrationRuntime.configurationRevision()):null;
    }
    public static void after(Receipt receipt) {
        if(receipt!=null && receipt.revision==IntegrationRuntime.configurationRevision() && !receipt.target.isCasting() && !receipt.target.getMagicData().isCasting())
            TomNativeRewards.counterspell(receipt.player);
    }
}
