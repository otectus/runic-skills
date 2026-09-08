package com.otectus.runicskills.integration.tom;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.*;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.integration.common.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.lang.reflect.Method;
import java.util.*;

/** Authenticated key input + native resource debit + committed native output. No native state writes. */
@Mod.EventBusSubscriber(modid=RunicSkills.MOD_ID)
public final class TomEquipment {
    private static Method fuel;private static Class<?> armor;
    private static final ThreadLocal<ServerPlayer> INPUT=new ThreadLocal<>();
    private static final ThreadLocal<Activation> CURRENT=new ThreadLocal<>();
    public interface Scope extends AutoCloseable {default void completed() {} @Override void close();}
    private static final class Activation {
        final ServerPlayer player;final ItemStack stack;final int before;final Vec3 movement;final long revision;
        final List<Projectile> projectiles=new ArrayList<>();
        Activation(ServerPlayer p,ItemStack s,int before) {player=p;stack=s;this.before=before;movement=p.getDeltaMovement();revision=IntegrationRuntime.configurationRevision();}
    }
    private TomEquipment() {}
    public static void probe() throws ReflectiveOperationException {
        fuel=Class.forName("com.gametechbc.traveloptics.data_manager.PlasmaFuelManager").getMethod("getPlasmaFuel",ItemStack.class);
        armor=Class.forName("com.gametechbc.traveloptics.item.armor.MechanizedExoskeletonArmorItem");
    }
    public static Runnable packet(ServerPlayer sender,Runnable original) {
        return ()->{
            if(sender==null || sender instanceof FakePlayer || !sender.isAlive() || sender.isCreative() || INPUT.get()!=null || RunicActionContext.currentActionId()!=0) {
                // A nested or unauthenticated packet cannot borrow the outer activation's identity.
                var previousInput=INPUT.get();var previousActivation=CURRENT.get();INPUT.remove();CURRENT.remove();
                try {original.run();} finally {
                    if(previousInput!=null)INPUT.set(previousInput);
                    if(previousActivation!=null)CURRENT.set(previousActivation);
                }
                return;
            }
            INPUT.set(sender);
            try(var action=RunicActionContext.push(ActionOrigin.NATIVE_ACTIVATION,sender.getUUID())) {original.run();}
            finally {INPUT.remove();CURRENT.remove();}
        };
    }
    private static boolean equipped(ServerPlayer p,ItemStack stack) {
        var cap=SkillCapability.get(p);return armor!=null && armor.isInstance(stack.getItem()) && stack.getItem() instanceof ArmorItem item
                && p.getItemBySlot(item.getEquipmentSlot())==stack && cap!=null && cap.canUseItemSilent(p,stack);
    }
    public static Scope activation(Player actor,ItemStack stack) {
        if(!(actor instanceof ServerPlayer p) || INPUT.get()!=p || CURRENT.get()!=null || !equipped(p,stack))return ()->{};
        int before=read(stack);if(before<=0)return ()->{};
        Activation a=new Activation(p,stack,before);CURRENT.set(a);
        return new Scope() {
            private boolean completed;
            @Override public void completed() {completed=true;}
            @Override public void close() {
            CURRENT.remove();int after=read(stack);
            if(!completed || after<0 || after>=a.before || a.revision!=IntegrationRuntime.configurationRevision() || !p.isAlive() || !equipped(p,stack))return;
            Vec3 movement=p.getDeltaMovement();
            boolean thrust=Double.isFinite(movement.lengthSqr()) && movement.distanceToSqr(a.movement)>1e-8;
            boolean launched=a.projectiles.stream().anyMatch(projectile->!projectile.isRemoved() && projectile.getOwner()==p && p.serverLevel().getEntity(projectile.getId())==projectile);
            if(thrust || launched)TomNativeRewards.armorCompleted(p);
            }
        };
    }
    private static int read(ItemStack stack) {
        try {return fuel==null?-1:((Number)fuel.invoke(null,stack)).intValue();}
        catch(ReflectiveOperationException | RuntimeException e) {
            IntegrationRuntime.capability(IntegrationModule.TOM,IntegrationAvailability.Capability.ARMOR_COMMIT,"Native Plasma Fuel reader failed; unavailable until restart.");return -1;
        }
    }
    @SubscribeEvent public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        var a=CURRENT.get();
        if(a==null || e.loadedFromDisk() || e.isCanceled() || e.getLevel().isClientSide || a.projectiles.size()>=32 || !(e.getEntity() instanceof Projectile projectile)
                || projectile.getOwner()!=a.player || !projectile.getClass().getName().startsWith("com.gametechbc.traveloptics.entity."))return;
        a.projectiles.add(projectile);
    }
}
