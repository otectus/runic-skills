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

    /**
     * Replaces the vanilla air bar so it can be drawn with the mod's own HUD layout.
     *
     * <p>Two things here are deliberately narrower than they used to be.
     *
     * <p>The cancellation is now conditional. Cancelling at HEAD unconditionally discarded every
     * other mod's {@code renderAir} modification — including mods that only wanted to reposition
     * or recolour it — and it cancelled even when this mixin then bailed out because the camera
     * entity was not a player, which simply deleted the air bar in spectator mode (RS-078).
     *
     * <p>{@code rightHeight} is Forge's shared right-side HUD cursor: every overlay reads it to
     * find its slot and then <em>increments</em> it so the next overlay stacks below. Assigning
     * {@code rightHeight = 10} stomped whatever other overlays had already accumulated, so any
     * mod drawing on the right-hand side after this one rendered on top of the air bar
     * (RS-078).
     */
    @Inject(method = {"renderAir"}, at = {@At("HEAD")}, remap = false, cancellable = true)
    private void renderAirs(int width, int height, GuiGraphics guiGraphics, CallbackInfo info) {
        // The camera entity is not always a Player (spectator target, detached/3rd-party camera).
        // A blind (Player) cast + requireNonNull threw CCE/NPE; leave vanilla to draw instead.
        if (!(this.minecraft.getCameraEntity() instanceof Player player)) {
            return;
        }
        info.cancel();
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
            // Increment, not assign — this cursor is shared with every other right-side overlay.
            this.this$class.rightHeight += 10;
        }

        RenderSystem.disableBlend();
        this.minecraft.getProfiler().pop();
    }
}


