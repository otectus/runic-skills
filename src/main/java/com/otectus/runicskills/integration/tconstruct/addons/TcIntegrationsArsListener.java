package com.otectus.runicskills.integration.tconstruct.addons;

import com.hollingsworth.arsnouveau.api.event.SpellDamageEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Source Tempering's payout: the one Ars spell that follows a source-paid armour repair.
 *
 * <p>Its own class because a Forge event subscriber's parameter types are resolved when the bus
 * scans it. A method taking {@link SpellDamageEvent} on a class that is registered whenever
 * TCIntegrations is present would fail to register on the many installs that have TCIntegrations
 * and no Ars Nouveau — so this class is registered only after {@code ars_nouveau} is confirmed, and
 * nothing else in the add-on package names an Ars type.
 *
 * <p><b>Same stage as the existing scaling.</b> {@code HIGHEST}, alongside
 * {@code ArsNouveauIntegration.onSpellDamage}, so §12.2's "apply the new one-shot 5% contribution
 * once in the same damage policy" is where it happens rather than in a second pass at another
 * priority. The charge is spent on the first accepted damage of the cast and is gone; a chained or
 * secondary cast inside the same window gets nothing.
 */
public final class TcIntegrationsArsListener {

    /** Adds the armed charge's share to one accepted spell, then clears it. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellDamage(SpellDamageEvent.Pre event) {
        if (!(event.caster instanceof ServerPlayer caster)) return;
        if (caster instanceof FakePlayer || caster.isCreative()) return;
        float share = TcIntegrationsAdapter.spendSourceTempering(caster);
        if (share <= 0.0f) return;
        event.damage = event.damage * (1.0f + share);
    }
}
