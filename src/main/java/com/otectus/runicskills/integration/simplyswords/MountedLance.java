package com.otectus.runicskills.integration.simplyswords;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.event.PowerProcEvent;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public final class MountedLance {
    private static final Map<ServerPlayer, Ride> RIDES = new WeakHashMap<>();
    private static final class Ride {
        final LivingEntity mount;
        final MountedTravel travel = new MountedTravel();
        Vec3 position;
        long tick;
        UUID lastTarget;
        Ride(LivingEntity mount, long tick) { this.mount = mount; this.position = mount.position(); this.tick = tick; }
    }
    private MountedLance() {}
    public static Result availability(boolean power) {
        return IntegrationRuntime.check(IntegrationModule.SIMPLY_MORE, power ? Feature.POWERS : Feature.PERKS,
                Capability.PRIMARY_HIT, Capability.MOUNTED_CHARGE);
    }
    @SuppressWarnings("deprecation")
    public static LivingEntity mount(ServerPlayer player) {
        if (player instanceof FakePlayer || !player.isAlive() || !(player.getVehicle() instanceof LivingEntity mount)
                || !mount.isAlive() || mount.getControllingPassenger() != player
                || !WeaponCombat.owner(player.getMainHandItem(), "simplymore")
                || !WeaponCombat.tagged(player.getMainHandItem(), "simplymore:weapon_types/lances")
                || !player.getOffhandItem().getItem().getDefaultAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).isEmpty()) return null;
        UUID owner = mount instanceof TamableAnimal animal ? animal.getOwnerUUID()
                : mount instanceof AbstractHorse horse ? horse.getOwnerUUID() : null;
        if (owner != null && !owner.equals(player.getUUID())) return null;
        var cap = SkillCapability.get(player);
        return cap != null && cap.canUseItemSilent(player, player.getMainHandItem()) ? mount : null;
    }
    public static void sample(ServerPlayer player) {
        LivingEntity mount = mount(player);
        if (mount == null || (!availability(false).available() && !availability(true).available())) { RIDES.remove(player); return; }
        long now = player.level().getGameTime();
        Ride ride = RIDES.get(player);
        if (ride == null || ride.mount != mount) {
            if (RIDES.size() < 1024) RIDES.put(player, new Ride(mount, now));
            return;
        }
        if (ride.tick == now) return;
        boolean separated = true;
        if (ride.lastTarget != null) {
            Entity target = player.serverLevel().getEntity(ride.lastTarget);
            separated = target == null || target.distanceToSqr(mount) > 36;
            if (separated) ride.lastTarget = null;
        }
        Vec3 delta = mount.position().subtract(ride.position), forward = mount.getLookAngle();
        ride.travel.sample(delta.x, delta.z, forward.x, forward.z, now - ride.tick, separated);
        ride.position = mount.position(); ride.tick = now;
    }
    public static void landed(ServerPlayer player, LivingEntity target) {
        LivingEntity mount = mount(player);
        if (mount == null) return;
        var cap = SkillCapability.get(player); var saddleward = RegistryPerks.SM_SADDLEWARD.get();
        if (saddleward.isEnabled(player) && cap.getCooldown(saddleward) <= 0) {
            cap.setCooldown(saddleward, 240); RunicGuard.grant(mount, 2, 80);
        }
        Ride ride = RIDES.get(player);
        if (ride == null || ride.mount != mount || !ride.travel.consume()) return;
        ride.lastTarget = target.getUUID();
        var couched = RegistryPerks.SM_COUCHED_DISCIPLINE.get();
        if (couched.isEnabled(player) && cap.getCooldown(couched) <= 0) {
            cap.setCooldown(couched, 160);
            IntegrationBuffs.grant(player, couched.getName(), IntegrationBuffs.Kind.RESISTANCE,
                    HandlerCommonConfig.HANDLER.instance().smCouchedDisciplinePercent / 100.0, 60, () -> couched.isEnabled(player));
        }
        var power = RegistryPowers.SM_FIRST_PASS.get();
        if (active(player, power) && PowerCooldownDebt.checkAndStart(player, power, player.level().getGameTime(),
                Math.max(1, Math.min(72000, PowerOverridesManager.icdTicksOr(power, 300))))) {
            double speed = IntegrationLimits.bounded(PowerOverridesManager.valueOr(power, "speed_percent", 15) / 100.0, 0, .20);
            IntegrationBuffs.grant(mount, power.getName(), IntegrationBuffs.Kind.SPEED, speed, 60,
                    () -> MountedLance.mount(player) == mount && active(player, power), player);
            MinecraftForge.EVENT_BUS.post(new PowerProcEvent(player, power, mount, null, 0, 128, false, true));
        }
    }
    private static boolean active(ServerPlayer player, Power power) { return power.isEquippedBy(player) && PowerEligibility.evaluateActive(player, power).eligible(); }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) sample(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { RIDES.remove(e.getEntity()); }
    @SubscribeEvent public static void mountEvent(net.minecraftforge.event.entity.EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityMounting() instanceof ServerPlayer player) {
            RIDES.remove(player); IntegrationBuffs.clear(SkillCapability.get(player), "sm_first_pass");
        }
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { RIDES.remove(e.getEntity()); }
}
