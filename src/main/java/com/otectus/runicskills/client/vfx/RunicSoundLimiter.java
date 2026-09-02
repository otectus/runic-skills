package com.otectus.runicskills.client.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Caps how much noise the Powers system can make.
 *
 * <p>Without a cap the audio is the first thing that fails under load: a chain reaction or a
 * multi-target proc plays one clip per proc per client, they stack into clipping, and the result is
 * both unpleasant and less informative than a single clean cue. The rule here is simple — at most
 * {@value #MAX_CONCURRENT} Power one-shots inside a rolling window, and repeats of the same sound
 * inside a short window are dropped rather than layered.
 *
 * <p>Positional for world procs so the sound comes from where the effect happened, which is half of
 * how a player answers "was that mine?". Owner-only confirmations play non-positionally at UI
 * volume, because they are about the player's own state rather than a place in the world.
 */
public final class RunicSoundLimiter {

    /** Simultaneous Power one-shots. Above this, a proc is silent but still seen. */
    public static final int MAX_CONCURRENT = 8;

    /** Ticks a given sound event is suppressed for after playing. */
    private static final long REPEAT_WINDOW_TICKS = 3;

    private static final Deque<Long> RECENT = new ArrayDeque<>();
    private static SoundEvent lastEvent;
    private static long lastEventTick;

    private RunicSoundLimiter() {
    }

    /**
     * Plays {@code sound} if the budget allows.
     *
     * @param at    where it happened, or {@code null} for a non-positional owner confirmation
     * @param pitch base pitch from the school
     * @param seed  proc seed; varies the pitch deterministically inside a narrow band so repeats do
     *              not sound mechanical and every client still hears the same thing
     * @return whether it played
     */
    public static boolean play(SoundEvent sound, Vec3 at, float pitch, int seed) {
        if (sound == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;

        long now = mc.level.getGameTime();
        prune(now);
        if (RECENT.size() >= MAX_CONCURRENT) return false;
        if (sound == lastEvent && now - lastEventTick < REPEAT_WINDOW_TICKS) return false;

        // A narrow band: enough that two procs in a row are audibly different takes of the same
        // cue, not enough that the school stops being identifiable by ear.
        float variance = 0.94F + ((seed >>> 8) & 0x7F) / 127.0F * 0.12F;
        float finalPitch = Math.max(0.5F, Math.min(2.0F, pitch * variance));

        if (at == null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, finalPitch, 0.35F));
        } else {
            mc.level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS,
                    0.45F, finalPitch, false);
        }

        RECENT.addLast(now);
        lastEvent = sound;
        lastEventTick = now;
        return true;
    }

    private static void prune(long now) {
        while (!RECENT.isEmpty() && now - RECENT.peekFirst() > REPEAT_WINDOW_TICKS * 2) {
            RECENT.removeFirst();
        }
    }

    /** Drops the window. Called on disconnect so a new session starts with a clean budget. */
    public static void reset() {
        RECENT.clear();
        lastEvent = null;
        lastEventTick = 0L;
    }
}
