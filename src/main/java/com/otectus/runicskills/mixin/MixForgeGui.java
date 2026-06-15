package com.otectus.runicskills.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.ForgeMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ForgeGui.class})
public abstract class MixForgeGui extends Gui {
    @Unique
    private final ForgeGui this$class = (ForgeGui) (Object) this;

    public MixForgeGui(Minecraft mc) {
        super(mc, mc.getItemRenderer());
    }

    @Inject(method = {"renderAir"}, at = {@At("HEAD")}, remap = false, cancellable = true)
    private void renderAirs(int width, int height, GuiGraphics guiGraphics, CallbackInfo info) {
        info.cancel();
        // The camera entity is not always a Player (spectator target, detached/3rd-party camera).
        // A blind (Player) cast + requireNonNull threw CCE/NPE; skip the custom air bar instead.
        if (!(this.minecraft.getCameraEntity() instanceof Player player)) {
            return;
        }
        this.minecraft.getProfiler().push("air");
        RenderSystem.enableBlend();
        int left = width / 2 + 91;
        int top = height - this.this$class.rightHeight;

        int air = player.getAirSupply();
        if (player.isEyeInFluidType(ForgeMod.WATER_TYPE.get()) || air < player.getMaxAirSupply()) {
            int full = Mth.ceil((air - 2) * 10.0D / player.getMaxAirSupply());
            int partial = Mth.ceil(air * 10.0D / player.getMaxAirSupply()) - full;

            for (int i = 0; i < full + partial; i++) {
                guiGraphics.blit(GUI_ICONS_LOCATION, left - i * 8 - 9, top, (i < full) ? 16 : 25, 18, 9, 9);
            }
            this.this$class.rightHeight = 10;
        }

        RenderSystem.disableBlend();
        this.minecraft.getProfiler().pop();
    }
}


