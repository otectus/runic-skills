package com.otectus.runicskills.registry;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public class RegistryEffects {
    public static class AddEffect {
        public final ServerPlayer player;
        public final boolean toggle;
        public final MobEffect effect;

        public AddEffect(ServerPlayer player, boolean toggle, MobEffect mobEffect) {
            this.player = player;
            this.toggle = toggle;
            this.effect = mobEffect;
        }

        public void add(int duration) {
            add(duration, 0);
        }

        public void add(int duration, int amplifier) {
            if (!this.toggle) return;
            // Only re-apply when the effect is missing or close to lapsing.
            //
            // Callers on the player-tick path passed a 210-tick duration every tick, and
            // ServerPlayer#addEffect sends a clientbound update packet on each successful
            // application. For two always-on perks that is ~40 packets/second/player of traffic
            // for state that never changes — invisible in singleplayer, expensive on a full
            // server (RS-009). Refreshing at a quarter of the duration keeps the effect
            // continuous with a wide margin while making the steady state nearly free.
            MobEffectInstance active = this.player.getEffect(this.effect);
            if (active != null && active.getAmplifier() >= amplifier && active.getDuration() > duration / 4) {
                return;
            }
            MobEffectInstance instance = new MobEffectInstance(this.effect, duration, amplifier, false, false,
                    HandlerCommonConfig.HANDLER.instance().showPotionsHud);
            this.player.addEffect(instance);
        }
    }
}


