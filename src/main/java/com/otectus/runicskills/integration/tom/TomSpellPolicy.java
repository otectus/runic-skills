package com.otectus.runicskills.integration.tom;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Pinned public acquisition API plus explicit exclusions from the native spell guide metadata. */
public final class TomSpellPolicy {
    private static Class<?> weapon,unique;
    private static final Set<String> EXCLUDED=Set.of(
            "traveloptics:call_forth_the_dead_king", "traveloptics:flood_slash", "traveloptics:tidal_slash",
            "traveloptics:skypiercer", "traveloptics:reversal", "traveloptics:floodgate",
            "irons_spellbooks:gluttony", "irons_spellbooks:haste");
    // These normal Aqua spells are described by native guide metadata as directly damaging.
    // Utility pools, reaction damage, recasts and equipment-bound spells cannot consume a charge.
    private static final Set<String> AQUA_DAMAGE=Set.of("traveloptics:hydroshot","traveloptics:aqua_missiles","traveloptics:tsunami");
    // The native guide identifies these as acquired player summon spells. Boss-drop acquisition
    // is distinct from Call Forth the Dead King's scroll-only hostile encounter summon.
    private static final Set<String> ACQUIRED_SUMMONS=Set.of("traveloptics:summon_desert_dwellers","traveloptics:sticky_steed_summon",
            "traveloptics:echo_of_the_abyss","traveloptics:herald_of_acropolis");
    private TomSpellPolicy() {}
    public static void probe() throws ReflectiveOperationException {
        weapon=Class.forName("com.gametechbc.traveloptics.api.spells.AbstractWeaponSpell");
        unique=Class.forName("com.gametechbc.traveloptics.api.spells.AbstractUniqueSpell");
    }
    public static boolean normal(AbstractSpell spell) {
        if(weapon==null || unique==null || weapon.isInstance(spell) || EXCLUDED.contains(spell.getSpellId())
                || spell.getCastType()==CastType.CONTINUOUS)return false;
        if(ACQUIRED_SUMMONS.contains(spell.getSpellId()))return true;
        if(unique.isInstance(spell))return false;
        var key=new ResourceLocation(spell.getSpellId());
        return Set.of("traveloptics","irons_spellbooks").contains(key.getNamespace()) && (spell.allowLooting() || spell.allowCrafting());
    }
    public static boolean offensiveAqua(AbstractSpell spell) {return normal(spell) && AQUA_DAMAGE.contains(spell.getSpellId());}
}
