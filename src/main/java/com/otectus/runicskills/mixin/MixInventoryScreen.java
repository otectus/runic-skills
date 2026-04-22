package com.otectus.runicskills.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.client.gui.DrawTabs;
import com.otectus.runicskills.integration.L2TabsIntegration;
import com.otectus.runicskills.integration.LegendaryTabsIntegration;
import com.otectus.runicskills.network.packet.common.OpenEnderChestSP;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Shadow
    public abstract RecipeBookComponent getRecipeBookComponent();

    // L2Tabs and Legendary Tabs render the Skills tab natively via their own tab APIs
    // (see RunicSkillsClient#clientSetup); skip the mixin-driven draw so we don't double-render.
    @Unique
    private boolean runicskills$externalTabsActive() {
        return L2TabsIntegration.isModLoaded() || LegendaryTabsIntegration.isModLoaded();
    }

    @Inject(method = {"renderBg"}, at = {@At("TAIL")})
    private void render(GuiGraphics matrixStack, float delta, int mouseX, int mouseY, CallbackInfo info) {
        if (runicskills$externalTabsActive()) return;

        RecipeBookComponent recipeBook = getRecipeBookComponent();
        int recipeOffset = (recipeBook != null && recipeBook.isVisible()) ? 77 : 0;

        DrawTabs.render(matrixStack, mouseX, mouseY, 176, 166, recipeOffset);

        if (RegistryPerks.WORMHOLE_STORAGE != null && RegistryPerks.WORMHOLE_STORAGE.get().isEnabled()) {
            this.this$isMouseCheck = false;
            matrixStack.pose().pushPose();
            try {
                int width = (getMinecraft().getWindow().getGuiScaledWidth() - 176) / 2;
                int height = (getMinecraft().getWindow().getGuiScaledHeight() - 166) / 2;
                int buttonX = width + 127 + recipeOffset;
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
                matrixStack.pose().popPose();
            }
        }
    }

    @Inject(method = {"mouseClicked"}, at = {@At("HEAD")})
    private void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> info) {
        if (runicskills$externalTabsActive()) return;
        if (button == 0 && this.this$isMouseCheck) this.this$checkMouse = true;
        DrawTabs.mouseClicked(button);
    }

    public void onClose() {
        this.this$checkMouse = false;
        DrawTabs.onClose();
        super.onClose();
    }
}
