package com.otectus.runicskills.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.client.event.InventoryTabsScreenHandler;
import com.otectus.runicskills.client.gui.DrawTabs;
import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.integration.L2TabsIntegration;
import com.otectus.runicskills.integration.LegendaryTabsIntegration;
import com.otectus.runicskills.network.packet.common.OpenEnderChestSP;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({InventoryScreen.class})
public abstract class MixInventoryScreen extends EffectRenderingInventoryScreen<InventoryMenu> {
    @Unique
    public boolean this$checkMouse = false;

    public MixInventoryScreen(Player player) {
        super(player.inventoryMenu, player.getInventory(), Component.translatable("container.crafting"));
    }

    @Unique
    public boolean this$isMouseCheck = false;

    // L2Tabs and Legendary Tabs render the Skills tab natively via their own tab APIs
    // (see RunicSkillsClient#clientSetup); skip the mixin-driven draw so we don't double-render.
    // L2Tabs counts as active only after registration succeeds, so an incompatible version falls
    // back to Runic Skills' built-in strip instead of hiding the tab or crashing startup.
    @Unique
    private boolean runicskills$externalTabsActive() {
        return L2TabsIntegration.isNativeTabsActive() || LegendaryTabsIntegration.isModLoaded();
    }

    /**
     * Tab bodies and the Wormhole Storage button.
     *
     * <p>Both stay on {@code renderBg} — under the item layer — on purpose. Forge's
     * {@code ScreenEvent.Render.Post} fires after the screen has drawn its items and their
     * tooltips, so anything painted there covers them. Position, hover, click and tooltip all come
     * from {@link InventoryTabsScreenHandler} now; this method only paints.
     */
    @Inject(method = {"renderBg"}, at = {@At("TAIL")})
    private void render(GuiGraphics matrixStack, float delta, int mouseX, int mouseY, CallbackInfo info) {
        if (runicskills$externalTabsActive()) return;

        if (HandlerConfigClient.inventoryTabsEnabled.get()) {
            DrawTabs.render(matrixStack, mouseX, mouseY,
                    InventoryTabsScreenHandler.layoutFor((InventoryScreen) (Object) this));
        }

        if (RegistryPerks.WORMHOLE_STORAGE != null && RegistryPerks.WORMHOLE_STORAGE.get().isEnabled()) {
            this.this$isMouseCheck = false;
            matrixStack.pose().pushPose();
            try {
                // The button rides the panel, so it uses the panel's real position. The old
                // "+77 when the recipe book is open" was an assumed shift; vanilla only moves the
                // panel when the window is at least 379 px wide, so on a narrow window the
                // constant pushed the button 77 px off the inventory it belongs to.
                int width = (getMinecraft().getWindow().getGuiScaledWidth() - 176) / 2;
                int height = (getMinecraft().getWindow().getGuiScaledHeight() - 166) / 2;
                int recipeShift = getGuiLeft() - width;
                int buttonX = width + 127 + recipeShift;
                int buttonY = height + 61;
                int checkButton = 0;
                if (Utils.checkMouse(buttonX, buttonY, mouseX, mouseY, 20, 18)) {
                    checkButton = 18;
                    this.this$isMouseCheck = true;
                    if (this.this$checkMouse) {
                        OpenEnderChestSP.send();
                        Utils.playSound();
                        this.this$checkMouse = false;
                    }
                }
                RenderSystem.enableBlend();
                matrixStack.blit(new ResourceLocation(RunicSkills.MOD_ID, "textures/skill/ender_chest_button.png"), buttonX, buttonY, 0.0F, checkButton, 20, 18, 20, 36);
            } finally {
                Utils.resetRenderState();
                matrixStack.pose().popPose();
            }
        }
    }

    /**
     * Arms the Wormhole Storage button's latch only. Tab clicks are handled by
     * {@link InventoryTabsScreenHandler}, which hit-tests against the same rects the tabs were
     * drawn at; this injection stays because the button's own latch pair
     * ({@code this$isMouseCheck}/{@code this$checkMouse}) is set during {@code renderBg} and has
     * nothing to hit-test against outside the mixin.
     */
    @Inject(method = {"mouseClicked"}, at = {@At("HEAD")})
    private void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> info) {
        if (runicskills$externalTabsActive()) return;
        if (button == 0 && this.this$isMouseCheck) this.this$checkMouse = true;
    }

    // The DrawTabs click latch is cleared on close by
    // com.otectus.runicskills.client.event.InventoryTabsScreenHandler, not from here. A
    // `public void onClose()` on this mixin is an implicit overwrite of an inherited method, which
    // Mixin drops outright when another mod's InventoryScreen mixin declares one too — Highlighter
    // does, and ours was the one being skipped. See that class for the full account.
}
