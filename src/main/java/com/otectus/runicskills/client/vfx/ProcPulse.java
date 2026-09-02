package com.otectus.runicskills.client.vfx;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * The most recent proc time per Power, so the Powers screen can flash the row that just fired.
 *
 * <p>Small on purpose. The screen wants one number — "how recently did this Power fire, as a 0..1
 * decay" — and building that out of the HUD's card list would tie a panel that may not be open to
 * a queue that exists to be looked at. Both read from here instead.
 *
 * <p>The pulse is capped at {@value #PULSE_TICKS} ticks, comfortably under the 0.6s the design
 * allows for an equipped-card animation, and it decays linearly so it cannot flicker.
 */
@OnlyIn(Dist.CLIENT)
public final class ProcPulse {

    /** Ticks a row stays highlighted after its Power fires. 12 ticks is 0.6s at 20 TPS. */
    public static final int PULSE_TICKS = 12;

    private static final Map<String, Long> LAST_PROC = new HashMap<>();

    private ProcPulse() {
    }

    public static synchronized void record(String powerName) {
        Minecraft mc = Minecraft.getInstance();
        if (powerName == null || mc.level == null) return;
        LAST_PROC.put(powerName, mc.level.getGameTime());
        // Bounded by construction: an entry is only kept while it could still be drawn.
        long now = mc.level.getGameTime();
        LAST_PROC.values().removeIf(at -> now - at > PULSE_TICKS);
    }

    /** 1.0 immediately after a proc, decaying to 0.0 over {@link #PULSE_TICKS}. */
    public static synchronized float strength(String powerName) {
        Minecraft mc = Minecraft.getInstance();
        if (powerName == null || mc.level == null) return 0.0F;
        Long at = LAST_PROC.get(powerName);
        if (at == null) return 0.0F;
        long elapsed = mc.level.getGameTime() - at;
        if (elapsed < 0 || elapsed >= PULSE_TICKS) return 0.0F;
        return 1.0F - (float) elapsed / PULSE_TICKS;
    }

    public static synchronized void clear() {
        LAST_PROC.clear();
    }
}
