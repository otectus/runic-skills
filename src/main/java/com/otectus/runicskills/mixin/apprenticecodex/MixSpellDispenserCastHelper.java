package com.otectus.runicskills.mixin.apprenticecodex;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.integration.apprenticecodex.CodexAutomation;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import jp.aquafactory.apprenticecodex.block.spelldispenser.SpellDispenserCastHelper;
import jp.aquafactory.apprenticecodex.block.spelldispenser.SpellDispenserManaHelper;
import jp.aquafactory.apprenticecodex.block.spelldispenser.SpellDispenserSpellValidator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The spell dispenser's autonomous cast, which no event reaches.
 *
 * <p>{@code SpellDispenserCastHelper} does not go through {@code AbstractSpell.attemptInitiateCast}
 * at all: it reimplements the cast loop, so {@code SpellPreCastEvent} and {@code SpellOnCastEvent}
 * are never posted and Codex posts no Forge event of its own in their place. Every other casting
 * route this mod gates reaches a cancelable upstream event; this one has none, which is why §6.5
 * allows a narrowly scoped, version-verified mixin here and nowhere else in the integration.
 *
 * <h2>Where it injects, and why exactly there</h2>
 *
 * <p>Two methods, both at {@code HEAD}: the nine-argument {@code tryCast} and the ten-argument
 * {@code tryStartContinuousCast}. Confirmed by {@code javap -c} against 0.9.7.1 — all six
 * {@code tryCast} overloads and all five {@code tryStartContinuousCast} overloads funnel into these
 * two, so one injector each covers every entry without a second decision being taken on the way in.
 * At {@code HEAD} nothing has happened yet: no caster proxy exists, no {@code MagicData} has been
 * touched, no mana has moved and no cooldown has started. A refusal therefore restores nothing
 * because nothing was spent, which is the only honest form of this gate — §6.5 is explicit that
 * cancelling a {@code void} callback after resources are gone is not one.
 *
 * <p>The refusal returns a real failure value rather than {@code null}:
 * {@code CastResult.serverAllowlistBlocked(validation)} is Codex's own public factory for "the
 * server would not permit this spell here", so the device reports a native diagnostic to nearby
 * players and keeps its contents. The continuous form wraps that same result with a {@code null}
 * session, which the caller only dereferences when {@code result.succeeded()} is true.
 *
 * <h2>Gating</h2>
 *
 * <p>{@code @Pseudo} plus a string target plus {@code RunicSkillsMixinPlugin}, exactly like
 * {@code MixPathingStuckHandler}: the plugin stops Mixin asking the classloader for absent
 * bytecode, and the plugin's condition is Codex being installed <em>and</em>
 * {@code enableApprenticeCodexIntegration} being on — a documented restart-required flag, because
 * no config toggle can hot-unload a mixin.
 *
 * <p>{@code remap = false} at class level is correct and load-bearing: every member named here is
 * declared by Codex or by Iron's, neither of which is obfuscated, and the vanilla types in the
 * signatures are parameters rather than members, so nothing needs an SRG name.
 * {@code require = 0, expect = 1} because a Codex release that reshapes this helper must cost the
 * automation policy its hook and warn, not kill the mixin config of a 300-mod pack.
 */
@Pseudo
@Mixin(targets = "jp.aquafactory.apprenticecodex.block.spelldispenser.SpellDispenserCastHelper",
        remap = false)
public abstract class MixSpellDispenserCastHelper {

    @Inject(method = "tryCast(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;"
            + "Lnet/minecraft/world/phys/Vec3;"
            + "Ljp/aquafactory/apprenticecodex/block/spelldispenser/SpellDispenserSpellValidator$ValidationResult;"
            + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/authlib/GameProfile;"
            + "Ljp/aquafactory/apprenticecodex/block/spelldispenser/SpellDispenserManaHelper$ManaAccess;"
            + "Lio/redspace/ironsspellbooks/api/spells/CastSource;Ljava/lang/String;)"
            + "Ljp/aquafactory/apprenticecodex/block/spelldispenser/SpellDispenserCastHelper$CastResult;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 1)
    private static void runicskills$gateAutonomousCast(
            ServerLevel level, Vec3 origin, Vec3 forward,
            SpellDispenserSpellValidator.ValidationResult validation, ItemStack source,
            GameProfile owner, SpellDispenserManaHelper.ManaAccess mana, CastSource castSource,
            String castingSlot, CallbackInfoReturnable<SpellDispenserCastHelper.CastResult> cir) {
        if (!CodexAutomation.allows(level, owner, validation)) {
            cir.setReturnValue(SpellDispenserCastHelper.CastResult.serverAllowlistBlocked(validation));
        }
    }

    @Inject(method = "tryStartContinuousCast(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
            + "Ljp/aquafactory/apprenticecodex/block/spelldispenser/SpellDispenserSpellValidator$ValidationResult;"
            + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/authlib/GameProfile;"
            + "Ljp/aquafactory/apprenticecodex/block/spelldispenser/SpellDispenserManaHelper$ManaAccess;"
            + "Lio/redspace/ironsspellbooks/api/spells/CastSource;Ljava/lang/String;Ljava/lang/Integer;)"
            + "Ljp/aquafactory/apprenticecodex/block/spelldispenser/SpellDispenserCastHelper$ContinuousCastStartResult;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 1)
    private static void runicskills$gateAutonomousContinuousCast(
            ServerLevel level, Vec3 origin, Vec3 forward,
            SpellDispenserSpellValidator.ValidationResult validation, ItemStack source,
            GameProfile owner, SpellDispenserManaHelper.ManaAccess mana, CastSource castSource,
            String castingSlot, Integer overriddenTicks,
            CallbackInfoReturnable<SpellDispenserCastHelper.ContinuousCastStartResult> cir) {
        // One decision per continuous cast, taken at the authorization boundary §6.5 names: the
        // start. Re-deciding inside the tick loop would either replay the refusal every interval or
        // interrupt a cast the owner was authorised for when they walked out of range.
        if (!CodexAutomation.allows(level, owner, validation)) {
            cir.setReturnValue(new SpellDispenserCastHelper.ContinuousCastStartResult(
                    SpellDispenserCastHelper.CastResult.serverAllowlistBlocked(validation), null));
        }
    }
}
