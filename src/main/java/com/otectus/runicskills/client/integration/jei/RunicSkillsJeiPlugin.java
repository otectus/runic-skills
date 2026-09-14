package com.otectus.runicskills.client.integration.jei;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.client.integration.tide.TideJournalScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Loaded by JEI's own plugin discovery only; no always-loaded class references JEI types. */
@JeiPlugin
public final class RunicSkillsJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(RunicSkills.MOD_ID, "inventory_controls");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(InventoryScreen.class, new IGuiContainerHandler<InventoryScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(InventoryScreen screen) {
                List<Rect2i> areas = new ArrayList<>();
                for (var child : screen.children()) {
                    if (child instanceof TideJournalScreen.FieldNotesButton button && button.visible) {
                        // Read the real widget, so resize and future positioning changes cannot
                        // separate its painted/clickable bounds from JEI's reserved rectangle.
                        areas.add(new Rect2i(button.getX(), button.getY(), button.getWidth(), button.getHeight()));
                    }
                }
                return areas;
            }
        });
    }
}
