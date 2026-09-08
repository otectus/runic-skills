package com.otectus.runicskills.integration.tom;
import com.otectus.runicskills.common.util.MixinHookVerification;
import com.otectus.runicskills.integration.common.*;
import org.objectweb.asm.tree.ClassNode;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.Capability;
public final class TomHookVerification {
    private static ClassNode mixin, target;
    private static final java.util.Map<String,ClassNode[]> COMBAT=new java.util.HashMap<>();
    private TomHookVerification() {}
    public static void record(ClassNode m, ClassNode t) { mixin = m; target = t; }
    public static void combat(String name,ClassNode m,ClassNode t) {COMBAT.put(name.substring(name.lastIndexOf('.')+1),new ClassNode[]{m,t});}
    public static boolean probe() {
        String reason = "Paid cast initiation, native debit and completion hooks did not all apply.";
        String combatReason="Primary and paid projectile tick hooks did not all apply.";
        String counterReason="Native counterspell outcome hooks did not apply.",armorReason="Authenticated armor input and paid output hooks did not apply.";
        try {
            Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell", false, TomHookVerification.class.getClassLoader());
            if (mixin != null && target != null && MixinHookVerification.missingHandlers(mixin, target).isEmpty()) reason = "";
            Class.forName("net.minecraft.server.level.ServerLevel",false,TomHookVerification.class.getClassLoader());
            Class.forName("net.minecraft.world.entity.player.Player",false,TomHookVerification.class.getClassLoader());
            boolean verified=true;
            for(String name:java.util.List.of("MixPrimaryWeaponHit","MixPaidProjectileTick")) {
                var entry=COMBAT.get(name);verified &= entry!=null && MixinHookVerification.missingHandlers(entry[0],entry[1]).isEmpty();
            }
            if(verified)combatReason="";
            Class.forName("io.redspace.ironsspellbooks.spells.ender.CounterspellSpell",false,TomHookVerification.class.getClassLoader());
            if(applied("MixCounterspellCommit"))counterReason="";
            TomEquipment.probe();Class.forName("com.gametechbc.traveloptics.network.ArmorKeyPacket",false,TomHookVerification.class.getClassLoader());
            if(applied("MixArmorKeyInput") && applied("MixPaidArmorCommit"))armorReason="";
        } catch (ReflectiveOperationException | LinkageError ignored) { }
        finally { mixin = null; target = null; COMBAT.clear(); }
        IntegrationRuntime.capability(IntegrationModule.TOM, Capability.PAID_CAST, reason);
        IntegrationRuntime.capability(IntegrationModule.TOM, Capability.PRIMARY_HIT, combatReason);
        IntegrationRuntime.capability(IntegrationModule.TOM, Capability.CAST_DAMAGE, reason.isEmpty()?combatReason:reason);
        IntegrationRuntime.capability(IntegrationModule.TOM, Capability.RELIC_TIER, TomRelics.verified()?"":"Pinned advanced relic identities are missing from the item registry.");
        IntegrationRuntime.capability(IntegrationModule.TOM, Capability.COUNTERSPELL, reason.isEmpty()?counterReason:reason);
        IntegrationRuntime.capability(IntegrationModule.TOM, Capability.ARMOR_COMMIT, armorReason);
        return reason.isEmpty();
    }
    private static boolean applied(String name) {var entry=COMBAT.get(name);return entry!=null && MixinHookVerification.missingHandlers(entry[0],entry[1]).isEmpty();}
}
