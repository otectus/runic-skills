package com.otectus.runicskills.mixin;

import com.otectus.runicskills.registry.RegistryAttributes;
import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantmentMenu;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enchanting Power under Apotheosis's enchanting module.
 *
 * <p>{@code MixEnchantmentMenu} adds the passive to the {@code power} argument vanilla derives from
 * bookshelves, and that is the whole story right up until Apotheosis replaces the table. Its
 * {@link ApothEnchantmentMenu} overrides {@code slotsChanged} without calling {@code super} and
 * never calls {@code EnchantmentHelper.getEnchantmentCost} at all: bookshelves become <em>eterna</em>,
 * gathered into an immutable {@code TableStats} record and fed to Apotheosis's own cost helper. The
 * vanilla hook is not weaker there, it simply never runs — so the passive needs a second reader.
 *
 * <p>Eterna is what the readout on the screen shows and what the roll uses, so the record is
 * replaced rather than the cost call being intercepted; boosting one and not the other would print
 * a number the enchant does not honour. Apotheosis sends the stats to the client from inside the
 * same lambda that assigns them, immediately after the assignment, which is why the injection sits
 * on the field write rather than at the return of {@code gatherStats()} — a return injection would
 * land after the packet had already gone out with the unboosted figure.
 *
 * <p>Only applied when Apotheosis is installed: {@code RunicSkillsMixinPlugin} refuses this mixin
 * outright otherwise, so its target class is never looked up in a pack without the mod.
 */
@Pseudo
@Mixin(value = ApothEnchantmentMenu.class, remap = false)
public abstract class MixApothEnchantmentMenu {

    @Shadow
    protected ApothEnchantmentMenu.TableStats stats;

    @Shadow
    @Final
    protected Player player;

    /**
     * The synthetic lambda {@code gatherStats()} evaluates against the table's level access. Its
     * name and descriptor, the {@code stats} field write and the ordering of the sync packet after
     * it were all read off {@code javap -p -c} of the mapped Apotheosis jar (file id 6461960).
     *
     * <p>Clamped to the attribute's own 0..1024 range and floored, so the eterna a player is told
     * about is a whole number and a hostile config cannot overflow the addition.
     */
    @Inject(method = "lambda$gatherStats$3(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Ldev/shadowsoffire/apotheosis/ench/table/ApothEnchantmentMenu;",
            at = @At(value = "FIELD",
                    target = "Ldev/shadowsoffire/apotheosis/ench/table/ApothEnchantmentMenu;stats:Ldev/shadowsoffire/apotheosis/ench/table/ApothEnchantmentMenu$TableStats;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER),
            require = 0)
    private void runicskills$addEnchantingPower(
            CallbackInfoReturnable<ApothEnchantmentMenu> cir) {
        if (this.player == null || this.stats == null) return;
        double bonus = this.player.getAttributeValue(RegistryAttributes.ENCHANTING_POWER.get());
        if (!(bonus > 0)) return;
        float eterna = this.stats.eterna() + (float) Math.min(1024.0, Math.floor(bonus));
        this.stats = new ApothEnchantmentMenu.TableStats(eterna, this.stats.quanta(),
                this.stats.arcana(), this.stats.rectification(), this.stats.clues(),
                this.stats.blacklist(), this.stats.treasure());
    }
}
