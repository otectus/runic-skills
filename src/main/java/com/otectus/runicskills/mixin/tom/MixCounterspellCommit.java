package com.otectus.runicskills.mixin.tom;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.otectus.runicskills.integration.tom.TomCounterspell;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo
@Mixin(targets="io.redspace.ironsspellbooks.spells.ender.CounterspellSpell",remap=false)
public abstract class MixCounterspellCommit {
    @WrapOperation(method="onCast",at=@At(value="INVOKE",target="Lio/redspace/ironsspellbooks/api/entity/IMagicEntity;cancelCast()V"),remap=false)
    private void runicskills$actualInterrupt(IMagicEntity target,Operation<Void> original) {
        var receipt=TomCounterspell.before(target);original.call(target);TomCounterspell.after(receipt);
    }
    @WrapOperation(method="onCast",at=@At(value="INVOKE",target="Lio/redspace/ironsspellbooks/entity/mobs/AntiMagicSusceptible;onAntiMagic(Lio/redspace/ironsspellbooks/api/magic/MagicData;)V"),remap=false)
    private void runicskills$actualAntiMagic(AntiMagicSusceptible target,MagicData caster,Operation<Void> original) {
        var receipt=TomCounterspell.before(target);original.call(target,caster);TomCounterspell.after(receipt);
    }
}
