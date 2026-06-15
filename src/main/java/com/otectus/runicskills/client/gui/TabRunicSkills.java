package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import dev.xkmc.l2tabs.tabs.core.BaseTab;
import dev.xkmc.l2tabs.tabs.core.TabManager;
import dev.xkmc.l2tabs.tabs.core.TabToken;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class TabRunicSkills extends BaseTab<TabRunicSkills> {

    public TabRunicSkills(TabToken<TabRunicSkills> token, TabManager manager, ItemStack stack, Component title){
        super(token, manager, stack, title);
    }

    @Override
    public void onTabClicked() {
        Utils.playSound();
        Minecraft.getInstance().setScreen(new RunicSkillsScreen());
    }
}
