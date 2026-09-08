package com.otectus.runicskills.integration.simplyswords;

import com.otectus.runicskills.common.util.MixinHookVerification;
import com.otectus.runicskills.integration.common.*;
import org.objectweb.asm.tree.ClassNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

public final class SwordsHookVerification {
    private record Pending(ClassNode mixin, ClassNode target) {}
    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();
    private SwordsHookVerification() {}
    public static void record(String name, ClassNode mixin, ClassNode target) {
        PENDING.put(name.substring(name.lastIndexOf('.') + 1), new Pending(mixin, target));
        if (name.endsWith("MixNativeGemEffect")) PENDING.put("MixNativeGemEffect:"+target.name,new Pending(mixin,target));
        if (name.endsWith("MixReturnedWeaponPickup")) PENDING.put("MixReturnedWeaponPickup:"+target.name,new Pending(mixin,target));
        for(String extra:List.of("MixNativeGemSummon","MixNativeGemHealing","MixPassiveGemEffect","MixManualGemEffect","MixGemMomentum"))
            if(name.endsWith(extra))PENDING.put(extra+":"+target.name,new Pending(mixin,target));
    }
    public static void probe() {
        var module = IntegrationModule.SIMPLY_SWORDS;
        var evidence = IntegrationRuntime.localEvidence().get(module);
        if (evidence == null) { PENDING.clear(); return; }
        String reason = "Manual action, sword wear and gem exclusion hooks did not all apply.";
        String repairReason = "Native anvil repair hook did not apply.";
        String activationReason = "Manual native activation hooks did not all apply.";
        String gemReason = "Native gem success sites did not all apply.";
        String returnReason="Manual throw and native owner pickup hooks did not all apply.";
        try {
            SwordsWear.probe();
            SwordsActivations.probe();
            ClassLoader loader = SwordsHookVerification.class.getClassLoader();
            for (String name : List.of("net.minecraft.world.entity.player.Player", "net.minecraft.world.item.SwordItem",
                    "net.sweenus.simplyswords.power.GemPowerComponent", "net.minecraft.world.inventory.AnvilMenu",
                    "net.minecraft.server.level.ServerPlayerGameMode", "net.sweenus.simplyswords.world.PlayerWeaponAbilityManager",
                    "net.sweenus.simplyswords.api.SimplySwordsAPI", "net.sweenus.simplyswords.util.WeaponManaCost")) Class.forName(name, false, loader);
            Pending anvil = PENDING.get("MixAnvilMenu");
            if (anvil != null && MixinHookVerification.missingHandlers(anvil.mixin, anvil.target).isEmpty()) repairReason = "";
            boolean applied = true;
            for (String name : List.of("MixPlayerWeaponWear", "MixSwordOrdinaryWear", "MixGemWearContext", "MixPrimaryWeaponHit")) {
                Pending pending = PENDING.get(name);
                applied &= pending != null && MixinHookVerification.missingHandlers(pending.mixin, pending.target).isEmpty();
            }
            if (applied) reason = "";
            boolean actions = true;
            for (String name : List.of("MixManualWeaponInput", "MixWeaponKeyInput", "MixWeaponActivation", "MixWeaponManaPayment")) {
                Pending pending = PENDING.get(name);
                actions &= pending != null && MixinHookVerification.missingHandlers(pending.mixin, pending.target).isEmpty();
            }
            if (actions) activationReason = "";
            SwordsGems.probe();
            boolean gems = true;
            for (String name : List.of("EchoPower","FloatPower","FreezePower","NullificationPower","OnslaughtPower","RadiancePower",
                    "ShieldingPower","SlowPower","StoneskinPower","SwiftnessPower","TrailblazePower","WeakenPower","WildfirePower","ZephyrPower")) {
                String target="net.sweenus.simplyswords.power.powers."+name;
                Class.forName(target,false,loader);
                Pending pending=PENDING.get("MixNativeGemEffect:"+target.replace('.','/'));
                gems &= pending!=null && MixinHookVerification.missingHandlers(pending.mixin,pending.target).isEmpty();
            }
            var gemHooks=Map.of("MixNativeGemSummon",List.of("world.DancingBladeManager","world.GoatStampedeManager","world.NecromanticArsenalManager","world.SnifferSlamManager","world.WingBuffetManager","world.WolfPackManager"),
                    "MixNativeGemHealing",List.of("power.powers.BerserkPower"),"MixPassiveGemEffect",List.of("power.powers.FrostWardPower","power.powers.UnstablePower"),
                    "MixManualGemEffect",List.of("power.powers.ImmolationPower","power.powers.WardPower"),"MixGemMomentum",List.of("power.powers.MomentumPower"));
            for(var entry:gemHooks.entrySet())for(String suffix:entry.getValue()) {
                String target="net.sweenus.simplyswords."+suffix;Class.forName(target,false,loader);
                var pending=PENDING.get(entry.getKey()+":"+target.replace('.','/'));
                gems &= pending!=null && MixinHookVerification.missingHandlers(pending.mixin,pending.target).isEmpty();
            }
            if (gems) gemReason="";
            SwordsReturns.probe();Class.forName("net.minecraft.world.item.ItemStack",false,loader);
            Pending release=PENDING.get("MixReleasedWeapon");
            boolean returns=actions && release!=null && MixinHookVerification.missingHandlers(release.mixin,release.target).isEmpty();
            for(String name:List.of("ThrownSwordEntity","ThrownSpearEntity")) {
                Pending pending=PENDING.get("MixReturnedWeaponPickup:net/sweenus/simplyswords/entity/"+name);
                returns &= pending!=null && MixinHookVerification.missingHandlers(pending.mixin,pending.target).isEmpty();
            }
            if(returns)returnReason="";
            if(net.minecraftforge.fml.ModList.get().isLoaded("simplymore")) {
                String moreReason="Native Mimicry input, timeline and replacement hooks did not all apply.";
                try {
                    MoreMimicry.probe();Class.forName("net.rosemarythyme.simplymore.effect.MimicryEffect",false,loader);
                    boolean verified=actions;
                    for(String name:List.of("MixMimicryTransition","MixMimicryTimeline","MixNativeShieldDisable")) {
                        var pending=PENDING.get(name);verified &= pending!=null && MixinHookVerification.missingHandlers(pending.mixin,pending.target).isEmpty();
                    }
                    if(verified)moreReason="";
                } catch(ReflectiveOperationException | LinkageError | RuntimeException ignored) { }
                IntegrationRuntime.capability(IntegrationModule.SIMPLY_MORE,Capability.MIMICRY_TRANSITION,moreReason);
                IntegrationRuntime.capability(IntegrationModule.SIMPLY_MORE,Capability.SHIELD_DISABLE,moreReason);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            reason = "Native delegated-hit provenance could not be verified.";
        } finally { PENDING.clear(); }
        IntegrationRuntime.capability(module, Capability.ORDINARY_WEAR, reason);
        IntegrationRuntime.capability(module, Capability.PRIMARY_HIT, reason);
        IntegrationRuntime.capability(module, Capability.MANUAL_REPAIR, repairReason);
        IntegrationRuntime.capability(module, Capability.NATIVE_ACTIVATION, activationReason);
        IntegrationRuntime.capability(module, Capability.GEM_SUCCESS, gemReason);
        IntegrationRuntime.capability(module, Capability.RETURN_PROVENANCE, returnReason);
        IntegrationRuntime.capability(module, Capability.PAID_ABILITY, activationReason.isEmpty() && SwordsActivations.paymentReader()
                ? "" : "Actual native weapon mana payment cannot be verified in this profile.");
        var more = IntegrationRuntime.localEvidence().get(IntegrationModule.SIMPLY_MORE);
        if (more != null && IntegrationAvailability.evaluate(IntegrationModule.SIMPLY_MORE, more,
                new Request("auto", true), java.util.Set.of(Capability.CLASSIFICATION)).available()) {
            IntegrationRuntime.capability(IntegrationModule.SIMPLY_MORE, Capability.PRIMARY_HIT, reason);
            IntegrationRuntime.capability(IntegrationModule.SIMPLY_MORE, Capability.ORDINARY_WEAR, reason);
            IntegrationRuntime.capability(IntegrationModule.SIMPLY_MORE, Capability.MANUAL_REPAIR, repairReason);
            IntegrationRuntime.capability(IntegrationModule.SIMPLY_MORE, Capability.MOUNTED_CHARGE, reason);
            IntegrationRuntime.capability(IntegrationModule.SIMPLY_MORE, Capability.LEGAL_REACH,
                    net.minecraftforge.fml.ModList.get().isLoaded("bettercombat")?"The installed combat system does not have a verified server reach adapter.":reason);
        }
    }
}
