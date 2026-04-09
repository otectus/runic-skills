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
            if (this.toggle) {
                MobEffectInstance instance = new MobEffectInstance(this.effect, duration, 0, false, false, HandlerCommonConfig.HANDLER.instance().showPotionsHud);
                this.player.addEffect(instance);
            }
        }

        public void add(int duration, int amplifier) {
            if (this.toggle) {
                MobEffectInstance instance = new MobEffectInstance(this.effect, duration, amplifier, false, false, HandlerCommonConfig.HANDLER.instance().showPotionsHud);
                this.player.addEffect(instance);
            }
        }
    }
}


