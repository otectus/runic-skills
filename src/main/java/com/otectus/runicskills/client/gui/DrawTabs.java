package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.client.core.Tabs;
import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.client.gui.InventoryTabLayout.Rect;
import com.otectus.runicskills.client.gui.InventoryTabLayout.TabLayout;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import com.otectus.runicskills.registry.RegistryItems;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class DrawTabs {
    public static final ResourceLocation TEXTURE = new ResourceLocation(RunicSkills.MOD_ID, "textures/gui/container/tabs.png");
    public static final Minecraft client = Minecraft.getInstance();
    public static ArrayList<Tabs> tabList = new ArrayList<>();
    public static boolean isMouseCheck = false;
    public static boolean checkMouse = false;

    /**
     * Player-head icon, resolved once per player rather than per frame.
     *
     * <p>{@code Utils.playerHead()} builds an ItemStack and writes a GameProfile into its NBT.
     * Doing that on every frame rebuilt the profile tag 60+ times a second for an icon that only
     * changes when the player does (RS-025).
     */
    private static ItemStack cachedPlayerHead;
    private static java.util.UUID cachedPlayerHeadOwner;

    /**
     * The layout the strip was last drawn with, and the screen it was drawn on.
     *
     * <p>Body rendering, the tooltip and the click handler now live in three different places —
     * a mixin on {@code renderBg}, a {@code ScreenEvent.Render.Post} listener and a mouse-event
     * listener — and the whole point of the layout engine is that they agree on where the tabs
     * are. Whoever draws the bodies publishes the rects here; everyone else reads them, so a
     * config edit between two callbacks in the same frame cannot desynchronise hover from paint.
     */
    private static Screen layoutScreen;
    private static TabLayout lastLayout;

    private static ItemStack playerHeadIcon() {
        java.util.UUID owner = client.player == null ? null : client.player.getUUID();
        if (cachedPlayerHead == null || !java.util.Objects.equals(owner, cachedPlayerHeadOwner)) {
            cachedPlayerHead = Utils.playerHead();
            cachedPlayerHeadOwner = owner;
        }
        return cachedPlayerHead;
    }

    /**
     * Rebuilds the strip contents for the current screen.
     *
     * <p>Still per frame, because the list encodes which tab is currently active, but it now costs
     * two small records: the icons are cached and the screens are suppliers that only run when a
     * tab is clicked (RS-025).
     */
    private static void rebuildTabs() {
        if (client.player == null) return;
        Screen screen = client.screen;
        tabList = new ArrayList<>();
        tabList.add(new Tabs("inventory", playerHeadIcon(),
                () -> new InventoryScreen(client.player),
                screen instanceof InventoryScreen, Component.translatable("container.inventory")));
        tabList.add(new Tabs("leveling", RegistryItems.LEVELING_BOOK.get().getDefaultInstance(),
                RunicSkillsScreen::new,
                screen instanceof RunicSkillsScreen, Component.translatable("screen.skill.title")));
    }

    /** How many tabs the strip holds right now — the {@code tabCount} the layout needs. */
    public static int tabCount() {
        rebuildTabs();
        return tabList.size();
    }

    /** The layout {@code screen} was last drawn with, or {@code null} if that was another screen. */
    public static TabLayout currentLayout(Screen screen) {
        return screen != null && screen == layoutScreen ? lastLayout : null;
    }

    /**
     * Draws the tab bodies at the positions the layout chose and runs the click latch.
     *
     * <p>Bodies only: the tooltip is drawn separately by {@link #renderTooltip}, because this is
     * called from {@code renderBg}, underneath the item layer, and a tooltip drawn there is
     * painted over by every item in the inventory.
     */
    public static void render(GuiGraphics matrixStack, int mouseX, int mouseY, TabLayout layout) {
        rebuildTabs();
        isMouseCheck = false;
        layoutScreen = client.screen;
        lastLayout = layout;

        int count = Math.min(tabList.size(), layout.tabs().size());
        for (int i = 0; i < count; i++) {
            Rect rect = layout.tabs().get(i);
            renderTabVisual(matrixStack, tabList.get(i), rect.x(), rect.y());
        }
        for (int i = 0; i < count; i++) {
            Tabs type = tabList.get(i);
            if (layout.tabs().get(i).contains(mouseX, mouseY) && !type.isScreen()) {
                isMouseCheck = true;
                if (checkMouse) {
                    setScreen(i);
                    checkMouse = false;
                }
            }
        }
    }

    /**
     * Draws the hovered tab's name. Called after the screen has finished rendering (including its
     * own tooltips) so the label is not buried under the item layer.
     */
    public static void renderTooltip(GuiGraphics matrixStack, int mouseX, int mouseY, TabLayout layout) {
        int count = Math.min(tabList.size(), layout.tabs().size());
        for (int i = 0; i < count; i++) {
            if (layout.tabs().get(i).contains(mouseX, mouseY)) {
                Utils.drawToolTip(matrixStack, tabList.get(i).getComponentName(), mouseX, mouseY);
                return;
            }
        }
    }

    /** Hit test against the same rects that were drawn, for the click handler. */
    public static int tabIndexAt(TabLayout layout, int mouseX, int mouseY) {
        int count = Math.min(tabList.size(), layout.tabs().size());
        for (int i = 0; i < count; i++) {
            if (layout.tabs().get(i).contains(mouseX, mouseY)) return i;
        }
        return -1;
    }

    private static void renderTabVisual(GuiGraphics matrixStack, Tabs type, int x, int y) {
        matrixStack.pose().pushPose();
        try {
            RenderSystem.enableBlend();
            matrixStack.blit(TEXTURE, x, y, type.getName().equals("inventory") ? 0 : 26, type.isScreen() ? 32 : 0, 26, 32);
            float scale = (type.getItemStack().getItem() instanceof net.minecraft.world.item.StandingAndWallBlockItem) ? 1.125F : 1.0F;
            float newX = (x + 13.0F - 8.0F) / scale;
            float newY = (y + 15.0F - 8.0F + (type.isScreen() ? 0.0F : 2.0F)) / scale;
            matrixStack.pose().pushPose();
            try {
                matrixStack.pose().scale(scale, scale, 1.0F);
                matrixStack.renderItem(type.getItemStack(), (int) newX, (int) newY);
            } finally {
                matrixStack.pose().popPose();
            }
        } finally {
            Utils.resetRenderState();
            matrixStack.pose().popPose();
        }
    }

    public static void setScreen(int i) {
        Utils.playSound();
        client.setScreen(tabList.get(i).getScreen());
    }

    public static void mouseClicked(int button) {
        if (button == 0 && isMouseCheck) checkMouse = true;
    }

    public static void onClose() {
        checkMouse = false;
        layoutScreen = null;
        lastLayout = null;
    }
}
