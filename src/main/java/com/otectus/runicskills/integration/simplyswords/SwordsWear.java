package com.otectus.runicskills.integration.simplyswords;

import com.otectus.runicskills.common.actions.ActionOrigin;
import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.combat.DamageContext;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import java.lang.reflect.Method;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Identifies the standard SwordItem durability call inside a committed manual action. */
public final class SwordsWear {
    private record Direct(ItemStack stack, LivingEntity target, Player actor, long action) {}
    private record Spend(ItemStack stack, ServerPlayer actor, long action, boolean mining) {}
    private static final ThreadLocal<Direct> DIRECT = new ThreadLocal<>();
    private static final ThreadLocal<Spend> SPEND = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> GEM = new ThreadLocal<>();
    private static Method delegated;
    private SwordsWear() {}
    public interface Scope extends AutoCloseable { @Override void close(); }
    public static boolean available() {
        return IntegrationRuntime.check(IntegrationModule.SIMPLY_SWORDS, Feature.PERKS, Capability.ORDINARY_WEAR).available();
    }
    public static void probe() throws ReflectiveOperationException {
        var api = Class.forName("net.sweenus.simplyswords.api.SimplySwordsAPI", false, SwordsWear.class.getClassLoader());
        delegated = api.getMethod("getDelegatedWeaponHitContext");
        if (!delegated.getReturnType().getName().equals("net.sweenus.simplyswords.api.DelegatedWeaponHitContext"))
            throw new NoSuchMethodException("Delegated weapon hit context return type");
    }
    private static <T> void restore(ThreadLocal<T> local, T previous) { if (previous == null) local.remove(); else local.set(previous); }
    public static Scope direct(ItemStack stack, LivingEntity target, Player actor) {
        Direct previous = DIRECT.get();
        var frame = RunicActionContext.current();
        DIRECT.set(new Direct(stack, target, actor, frame.actionId()));
        return () -> restore(DIRECT, previous);
    }
    public static Scope gem() {
        Boolean previous = GEM.get(); GEM.set(true);
        return () -> restore(GEM, previous);
    }
    public static boolean manualOrigin() {
        if (Boolean.TRUE.equals(GEM.get()) || SwordsActivations.running() || DamageContext.depth() != 0) return false;
        if (delegated == null) return !net.minecraftforge.fml.ModList.get().isLoaded("simplyswords");
        try { return delegated.invoke(null) == null; }
        catch (ReflectiveOperationException | LinkageError | RuntimeException failure) { failed(); return false; }
    }
    private static void failed() {
        delegated = null;
        for (var module : new IntegrationModule[]{IntegrationModule.SIMPLY_SWORDS, IntegrationModule.SIMPLY_MORE})
            for (var capability : new Capability[]{Capability.ORDINARY_WEAR, Capability.PRIMARY_HIT})
                IntegrationRuntime.capability(module, capability, "Native delegated-hit provenance failed; unavailable until restart.");
    }
    public static Scope spend(ItemStack stack, LivingEntity actor, LivingEntity target, boolean mining) {
        Spend previous = SPEND.get();
        SPEND.remove();
        var frame = RunicActionContext.current();
        boolean ordinary = frame.actionId() != 0 && frame.actionId() == frame.rootId()
                && frame.actor() != null && frame.actor().equals(actor.getUUID());
        if (mining) ordinary &= frame.origin() == ActionOrigin.BLOCK_BREAK;
        else {
            Direct direct = DIRECT.get();
            ordinary &= frame.origin() == ActionOrigin.MELEE && direct != null && direct.stack == stack
                    && direct.actor == actor && direct.target == target && direct.action == frame.actionId();
            if (actor instanceof Player player) {
                ordinary &= target != null && target != actor
                        && !com.otectus.runicskills.common.powers.PowerRuntime.AllyDetector.isAlly(player, target)
                        && (!(target instanceof Player other) || (player.getServer() != null
                        && player.getServer().isPvpAllowed() && player.canHarmPlayer(other)));
            }
        }
        if (ordinary && actor instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && !Boolean.TRUE.equals(GEM.get()) && DamageContext.depth() == 0 && (available()
                || IntegrationRuntime.check(IntegrationModule.SIMPLY_MORE, Feature.PERKS, Capability.ORDINARY_WEAR).available())) {
            try {
                if (delegated != null && delegated.invoke(null) == null) SPEND.set(new Spend(stack, player, frame.actionId(), mining));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                failed();
            }
        }
        return () -> restore(SPEND, previous);
    }
    /** Joins the existing Runic roll, affecting one point only and claiming this root once. */
    public static double contribution(ServerPlayer player, ItemStack stack) {
        Spend spend = SPEND.get();
        if (spend == null || spend.actor != player || spend.stack != stack || spend.action != RunicActionContext.currentActionId()
                || !player.isAlive() || Boolean.TRUE.equals(GEM.get())) return 0;
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null
                || (player.getMainHandItem() != stack && player.getOffhandItem() != stack)) return 0;
        var cap = SkillCapability.get(player);
        if (cap != null && "simplymore".equals(id.getNamespace()) && !spend.mining
                && RunicActionContext.attackStrength(player.getUUID(), 0) >= .9f
                && (WeaponCombat.tagged(stack, "simplymore:weapon_types/pernachs") || WeaponCombat.tagged(stack, "simplymore:weapon_types/quarterstaffs"))
                && cap.canUseItemSilent(player, stack) && RegistryPerks.SM_MEASURED_BLOWS.get().isEnabled(player)) {
            int percent = HandlerCommonConfig.HANDLER.instance().smMeasuredBlowsPercent;
            return percent > 0 && RunicActionContext.claim("sm_measured_blows") ? Math.min(.30, percent / 100.0) : 0;
        }
        if (!"simplyswords".equals(id.getNamespace()) || !available()) return 0;
        if (cap == null || !cap.canUseItemSilent(player, stack) || !RegistryPerks.SS_PATIENT_TEMPER.get().isEnabled(player)) return 0;
        int percent = HandlerCommonConfig.HANDLER.instance().ssPatientTemperPercent;
        return percent > 0 && RunicActionContext.claim("ss_patient_temper") ? Math.min(.30, percent / 100.0) : 0;
    }
}
