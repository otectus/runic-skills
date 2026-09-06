package com.otectus.runicskills.client.integration.tconstruct;

import com.otectus.runicskills.network.packet.client.WorkshopStatusCP;
import com.otectus.runicskills.network.packet.common.WorkshopFocusSP;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Set;

/**
 * A small Runic strip beside a native Tinkers' screen: what the server would give you, and a way to
 * claim the workshop you are standing at.
 *
 * <p><b>It knows nothing about Tinker's Construct.</b> The screen is recognised by class name, the
 * quote arrives as text and one preview stack, and the position the Focus button names came from
 * the server in the last status packet. That is deliberate rather than lazy: this class is loaded
 * on every client, whatever mods are installed, so a hard reference to a {@code slimeknights} screen
 * would be a class-loading failure on the majority of installs that do not have it. It also means
 * an upstream screen refactor costs the panel, not the game.
 *
 * <p><b>Placement.</b> §14.2 forbids overlapping the native modifier, repair and information tabs,
 * JEI's transfer controls and add-on screen extensions — so nothing is drawn inside the native
 * area. The panel sits immediately to the left of the container's own rectangle, which is the one
 * region the native layout and its add-ons do not use, and the button is a real
 * {@link Button} widget: keyboard-focusable, narratable, and reachable without a mouse. No
 * information here is hover-only.
 */
public final class TinkerStationPanel {

    /**
     * The native screens this panel attaches to, by simple name.
     *
     * <p>Simple names rather than fully-qualified ones because two of these live in different
     * packages in different Tinkers' lines, and the panel's failure mode for a wrong guess is that
     * it does not appear — never that something breaks.
     */
    private static final Set<String> STATION_SCREENS = Set.of(
            "TinkerStationScreen", "CraftingStationScreen", "PartBuilderScreen",
            "ModifierWorktableScreen", "MelterScreen", "HeatingStructureScreen", "AlloyerScreen");

    /** Where the panel sits relative to the container rectangle, and how wide it is. */
    private static final int PANEL_WIDTH = 104;
    private static final int PANEL_GAP = 6;

    private static boolean focused;
    private static BlockPos controller = BlockPos.ZERO;
    private static int associations;
    private static int remainingTicks;
    private static long token;
    private static int bonusPercent;
    private static boolean automationRewards;
    private static String owner = "";
    private static BlockPos menuBlock = BlockPos.ZERO;
    private static byte menuKind = WorkshopStatusCP.MENU_NONE;

    private static int quoteMenuId = -1;
    private static String quoteKind = "";
    private static String quoteRecipe = "";
    private static ItemStack quotePreview = ItemStack.EMPTY;

    /** The tick the last status arrived on, so the countdown is derived locally (§15.3). */
    private static long statusReceivedAt;

    /**
     * The button currently on screen, so its label can follow the focus rather than the screen.
     *
     * <p>A status arriving after the screen was built would otherwise leave a button reading
     * "Focus" on a workshop the player has just claimed — the one thing the control must never do,
     * since §14.2 requires it to say who holds the workshop and offer release.
     */
    private static Button focusButton;

    private TinkerStationPanel() {
    }

    /**
     * Starts listening for native screens.
     *
     * <p>Reached from {@code RunicSkillsClient.ClientProxy} as a method reference, guarded by the
     * mod being loaded, exactly as the inventory-tab integrations are.
     */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(TinkerStationPanel.class);
    }

    /** Takes the server's latest word on this player's focus. */
    public static void acceptStatus(boolean isFocused, BlockPos focusController, int associationCount,
                                    int remaining, long currentToken, int bonus,
                                    boolean automation, String ownerName, BlockPos block,
                                    byte kind) {
        focused = isFocused;
        controller = focusController;
        associations = associationCount;
        remainingTicks = remaining;
        token = currentToken;
        bonusPercent = bonus;
        automationRewards = automation;
        owner = ownerName;
        menuBlock = block;
        menuKind = kind;
        Minecraft client = Minecraft.getInstance();
        statusReceivedAt = client.level == null ? 0L : client.level.getGameTime();
        if (focusButton != null) focusButton.setMessage(buttonLabel());
    }

    /** Takes the server's latest quote for the station this player has open. */
    public static void acceptQuote(int menuId, long revision, String kind, String recipeId,
                                   ItemStack preview) {
        quoteMenuId = menuId;
        quoteKind = kind;
        quoteRecipe = recipeId;
        quotePreview = preview;
        // The revision is not displayed; it is kept out of the panel deliberately, because a number
        // on screen invites a player to treat it as something they can act on. It is a server fact.
    }

    /** Forgets everything. Called when the connection ends, like every other per-session cache. */
    public static void reset() {
        focused = false;
        controller = BlockPos.ZERO;
        associations = 0;
        remainingTicks = 0;
        token = 0L;
        bonusPercent = 0;
        automationRewards = false;
        owner = "";
        menuBlock = BlockPos.ZERO;
        menuKind = WorkshopStatusCP.MENU_NONE;
        quoteMenuId = -1;
        quoteKind = "";
        quoteRecipe = "";
        quotePreview = ItemStack.EMPTY;
        focusButton = null;
    }

    /** Adds the Focus button once the native screen has finished building its own widgets. */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!isNativeScreen(screen)) return;
        int left = screen.getGuiLeft() - PANEL_WIDTH - PANEL_GAP;
        int top = screen.getGuiTop() + 4;
        focusButton = Button.builder(buttonLabel(), button -> onFocusPressed(screen))
                .bounds(left, top, PANEL_WIDTH, 20)
                .build();
        event.addListener(focusButton);
    }

    /** Draws the quote and the focus lines beside the native screen. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!isNativeScreen(screen)) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int left = screen.getGuiLeft() - PANEL_WIDTH - PANEL_GAP;
        int top = screen.getGuiTop() + 28;
        for (Component line : lines(screen)) {
            graphics.drawString(Minecraft.getInstance().font, line, left, top, 0xFFFFFF);
            top += 10;
        }
    }

    /** The panel's text: the quote first, then where the focus stands. */
    private static List<Component> lines(AbstractContainerScreen<?> screen) {
        Component quote = quoteMenuId == screen.getMenu().containerId && !quotePreview.isEmpty()
                ? Component.translatable("gui.runicskills.tconstruct.quote",
                        quotePreview.getHoverName(),
                        Component.translatable("gui.runicskills.tconstruct.kind."
                                + quoteKind.toLowerCase(java.util.Locale.ROOT)))
                : Component.translatable("gui.runicskills.tconstruct.quote.none");
        if (!focused) {
            return List.of(quote.copy().withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.runicskills.tconstruct.focus.none")
                            .withStyle(ChatFormatting.DARK_GRAY));
        }
        return List.of(quote.copy().withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.runicskills.tconstruct.focus.held",
                        controller.getX(), controller.getY(), controller.getZ())
                        .withStyle(ChatFormatting.GOLD),
                Component.translatable("gui.runicskills.tconstruct.focus.remaining",
                        secondsLeft(), bonusPercent).withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.runicskills.tconstruct.focus.associations",
                        associations, owner).withStyle(ChatFormatting.DARK_GRAY),
                Component.translatable(automationRewards
                        ? "gui.runicskills.tconstruct.focus.automation_on"
                        : "gui.runicskills.tconstruct.focus.automation_off")
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * The countdown, worked out here rather than streamed.
     *
     * <p>One packet every ten ticks plus local arithmetic, which is what §15.3 asks for instead of
     * a packet per remaining tick.
     */
    private static int secondsLeft() {
        Minecraft client = Minecraft.getInstance();
        long now = client.level == null ? statusReceivedAt : client.level.getGameTime();
        long elapsed = Math.max(0L, now - statusReceivedAt);
        return (int) Math.max(0L, (remainingTicks - elapsed) / 20L);
    }

    private static Component buttonLabel() {
        return Component.translatable(focused
                ? "gui.runicskills.tconstruct.release"
                : "gui.runicskills.tconstruct.focus");
    }

    /**
     * Asks the server to claim, associate or release.
     *
     * <p>The client decides nothing here: it names the block the server told it about and quotes
     * the token the server issued. Every one of those is checked again server-side, and a refusal
     * comes back as an ordinary notice.
     */
    private static void onFocusPressed(AbstractContainerScreen<?> screen) {
        int containerId = screen.getMenu().containerId;
        if (focused && menuKind == WorkshopStatusCP.MENU_CASTING) {
            WorkshopFocusSP.send(containerId, WorkshopFocusSP.Action.ASSOCIATE, menuBlock, token);
            return;
        }
        if (focused) {
            WorkshopFocusSP.send(containerId, WorkshopFocusSP.Action.RELEASE, BlockPos.ZERO, token);
            return;
        }
        WorkshopFocusSP.send(containerId, WorkshopFocusSP.Action.FOCUS, menuBlock, token);
    }

    private static boolean isNativeScreen(Screen screen) {
        return STATION_SCREENS.contains(screen.getClass().getSimpleName());
    }
}
