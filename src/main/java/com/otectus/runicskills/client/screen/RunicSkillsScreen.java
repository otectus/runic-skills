package com.otectus.runicskills.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.otectus.runicskills.client.core.SortPassives;
import com.otectus.runicskills.client.core.SortPerks;
import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.client.gui.DrawTabs;
import com.otectus.runicskills.client.tooltip.PassiveTooltip;
import com.otectus.runicskills.client.tooltip.PerkTooltip;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.integration.KubeJSIntegration;
import com.otectus.runicskills.integration.L2TabsIntegration;
import com.otectus.runicskills.integration.LegendaryTabsIntegration;
import com.otectus.runicskills.network.packet.common.PassiveLevelDownSP;
import com.otectus.runicskills.network.packet.common.PassiveLevelUpSP;
import com.otectus.runicskills.network.packet.common.SetPlayerTitleSP;
import com.otectus.runicskills.network.packet.common.SkillLevelUpSP;
import com.otectus.runicskills.network.packet.common.TogglePerkSP;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.RegistryTitles;
import com.otectus.runicskills.registry.passive.Passive;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.registry.skill.Skill;
import com.otectus.runicskills.registry.title.Title;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class RunicSkillsScreen extends Screen {
    private static final Minecraft client = Minecraft.getInstance();

    private static final int PAGE_OVERVIEW = 0;
    private static final int PAGE_DETAIL = 1;
    private static final int PAGE_TITLES = 2;

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 194;
    private static final int PANEL_CENTER_X = PANEL_WIDTH / 2;

    private static final int HEADER_NAME_Y = 7;
    private static final int HEADER_LEVEL_Y = 17;
    private static final int TITLE_BUTTON_Y = 27;
    private static final int XP_BAR_X = 12;
    private static final int XP_BAR_Y = 43;

    private static final int OVERVIEW_SLOT_COLUMNS = 2;
    private static final int OVERVIEW_SLOT_WIDTH = 66;
    private static final int OVERVIEW_SLOT_HEIGHT = 26;
    // Hover texture is a 74x26 halo — 4px of green glow on each horizontal side of the
    // underlying 66x26 button. Must be passed to blit at its real size so UV normalization
    // is correct; shifting x by -4 centers the halo over the button.
    private static final int OVERVIEW_HOVER_TEX_WIDTH = 74;
    private static final int OVERVIEW_HOVER_TEX_HEIGHT = 26;
    private static final int OVERVIEW_HOVER_X_OFFSET = (OVERVIEW_HOVER_TEX_WIDTH - OVERVIEW_SLOT_WIDTH) / 2;
    private static final int OVERVIEW_HOVER_Y_OFFSET = (OVERVIEW_HOVER_TEX_HEIGHT - OVERVIEW_SLOT_HEIGHT) / 2;
    private static final int OVERVIEW_LEFT_SLOT_X = 16;
    private static final int OVERVIEW_RIGHT_SLOT_X = 93;
    private static final int OVERVIEW_FIRST_SLOT_Y = 50;
    private static final int OVERVIEW_SLOT_ROW_SPACING = 28;
    private static final int OVERVIEW_CONTENT_PAD_X = 4;
    private static final int OVERVIEW_CONTENT_PAD_Y = 2;
    private static final int OVERVIEW_ICON_SIZE = 16;
    private static final int OVERVIEW_TEXT_GAP = 4;
    private static final int OVERVIEW_TEXT_TOP_OFFSET = 3;

    private static final int DETAIL_CONTENT_X = 7;
    private static final int DETAIL_CONTENT_Y = 32;
    private static final int DETAIL_CONTENT_WIDTH = 160;
    private static final int DETAIL_CONTENT_HEIGHT = 140;
    private static final int DETAIL_ITEMS_PER_ROW = 5;
    private static final int DETAIL_ROWS_PER_PAGE = 4;
    private static final int DETAIL_ROW_HEIGHT = 26;
    private static final int DETAIL_ICON_SIZE = 24;
    private static final int DETAIL_ICON_SPACING = 26;

    private static final int FOOTER_Y = 172;
    private static final int FOOTER_BUTTON_SIZE = 11;
    private static final int FOOTER_MOD_TOGGLE_X = 16;
    private static final int FOOTER_PASSIVE_SORT_X = 28;
    private static final int FOOTER_PERK_SORT_X = 40;
    private static final int BACK_BUTTON_X = 141;
    private static final int BACK_BUTTON_WIDTH = 18;
    private static final int BACK_BUTTON_HEIGHT = 10;

    private static final int LEVEL_UP_BUTTON_X = 149;
    private static final int LEVEL_UP_BUTTON_Y = 10;
    private static final int LEVEL_UP_BUTTON_SIZE = 14;

    private static final int TITLE_SEARCH_X = 41;
    private static final int TITLE_SEARCH_Y = 17;
    private static final int TITLE_SEARCH_WIDTH = 93;
    private static final int TITLE_SEARCH_HEIGHT = 12;
    private static final int TITLE_LIST_X = 8;
    private static final int TITLE_LIST_Y = 33;
    private static final int TITLE_LIST_WIDTH = 142;
    private static final int TITLE_LIST_VIEWPORT_HEIGHT = 132;
    private static final int TITLE_ROW_HEIGHT = 12;
    private static final int TITLE_VISIBLE_ROWS = 11;
    private static final int TITLE_SCROLL_X = 152;
    private static final int TITLE_SCROLL_WIDTH = 12;
    private static final int TITLE_SCROLL_HANDLE_HEIGHT = 15;

    private int selectedPage = PAGE_OVERVIEW;
    private String selectedSkill = "";

    private int tick = 0;
    private final int maxTick = 40;
    private boolean pulseOn = true;

    private int perkActualPage = 0;
    private int perkSizePage = 0;

    private int scrollDropDown = 0;
    private int scrollHandleY = 0;
    private boolean scrollingDropDown = false;

    private String searchValue = "";
    private EditBox searchTitle;

    public RunicSkillsScreen() {
        super(Component.translatable("screen.skill.title"));
    }

    @Override
    protected void init() {
        super.init();
        int x = panelLeft();
        int y = panelTop();

        this.searchTitle = new EditBox(this.font, x + TITLE_SEARCH_X, y + TITLE_SEARCH_Y, TITLE_SEARCH_WIDTH, TITLE_SEARCH_HEIGHT,
                Component.translatable("screen.title.search"));
        this.searchTitle.setMaxLength(50);
        this.searchTitle.setBordered(true);
        this.searchTitle.setTextColor(0xFFFFFF);
        this.searchTitle.setValue(this.searchValue);
        this.searchTitle.setFocused(this.selectedPage == PAGE_TITLES);
        this.searchTitle.setVisible(this.selectedPage == PAGE_TITLES);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        int x = panelLeft();
        int y = panelTop();

        updateSearchBox(x, y);
        drawScreen(guiGraphics, x, y, mouseX, mouseY, delta);

        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    private void drawScreen(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics);

        guiGraphics.pose().pushPose();
        switch (this.selectedPage) {
            case PAGE_OVERVIEW -> {
                RenderSystem.enableBlend();
                guiGraphics.blit(HandlerResources.SKILL_PANEL[0], x, y, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
                drawOverview(guiGraphics, x, y, mouseX, mouseY);
            }
            case PAGE_DETAIL -> {
                RenderSystem.enableBlend();
                drawDetailBackground(guiGraphics, x, y);
                guiGraphics.blit(HandlerResources.SKILL_PANEL[1], x, y, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
                drawDetail(guiGraphics, x, y, mouseX, mouseY);
            }
            case PAGE_TITLES -> {
                RenderSystem.enableBlend();
                guiGraphics.blit(HandlerResources.SKILL_PANEL[2], x, y, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
                drawTitles(guiGraphics, x, y, mouseX, mouseY, delta);
            }
            default -> {
            }
        }

        // Suppress our legacy tab strip whenever an external tab system is active —
        // those mods register our Skills tab themselves and render it in their own strip,
        // so drawing DrawTabs on top produces the duplicate-tabs-above-panel artefact.
        if (!L2TabsIntegration.isModLoaded() && !LegendaryTabsIntegration.isModLoaded()) {
            DrawTabs.render(guiGraphics, mouseX, mouseY, PANEL_WIDTH, PANEL_HEIGHT, 0);
        }
        guiGraphics.pose().popPose();
    }

    private void drawOverview(GuiGraphics guiGraphics, int panelX, int panelY, int mouseX, int mouseY) {
        if (client.player == null) {
            return;
        }

        Utils.drawCenter(guiGraphics, client.player.getName(), panelX + PANEL_CENTER_X, panelY + HEADER_NAME_Y);
        Utils.drawCenter(guiGraphics,
                Component.translatable("screen.skill.level", client.player.experienceLevel, Utils.getPlayerXP(client.player)),
                panelX + PANEL_CENTER_X,
                panelY + HEADER_LEVEL_Y);

        drawTitleButton(guiGraphics, panelX, panelY, mouseX, mouseY);

        int progress = (int) (client.player.experienceProgress * 151.0F);
        guiGraphics.blit(HandlerResources.PERK_PAGE[0], panelX + XP_BAR_X, panelY + XP_BAR_Y, 0, 166, progress, 5);

        List<Skill> skills = getSortedSkills();
        // Defer the hovered-skill tooltip until after all cells are drawn. Rendering it
        // inline inside the loop causes subsequent cells (e.g. the neighbour to the right
        // of STR) to overpaint the tooltip.
        Skill hoveredSkill = null;
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            Area slotBounds = overviewSlotBounds(panelX, panelY, i);
            Area contentBounds = overviewContentBounds(slotBounds);
            Area iconBounds = overviewIconBounds(contentBounds);
            int textX = iconBounds.x() + OVERVIEW_ICON_SIZE + OVERVIEW_TEXT_GAP;
            int abbreviationY = contentBounds.y() + OVERVIEW_TEXT_TOP_OFFSET;
            int levelY = abbreviationY + 9;
            int skillLevel = skill.getLevel();
            boolean hovered = slotBounds.contains(mouseX, mouseY);

            if (hovered) {
                guiGraphics.blit(HandlerResources.SKILL_CARD_HOVER,
                        slotBounds.x() - OVERVIEW_HOVER_X_OFFSET,
                        slotBounds.y() - OVERVIEW_HOVER_Y_OFFSET,
                        0, 0,
                        OVERVIEW_HOVER_TEX_WIDTH, OVERVIEW_HOVER_TEX_HEIGHT,
                        OVERVIEW_HOVER_TEX_WIDTH, OVERVIEW_HOVER_TEX_HEIGHT);
                hoveredSkill = skill;
            }

            guiGraphics.blit(skill.getLockedTexture(), iconBounds.x(), iconBounds.y(), 0.0F, 0.0F,
                    OVERVIEW_ICON_SIZE, OVERVIEW_ICON_SIZE, OVERVIEW_ICON_SIZE, OVERVIEW_ICON_SIZE);
            guiGraphics.drawString(client.font,
                    Component.translatable(skill.getKey() + ".abbreviation").withStyle(ChatFormatting.BOLD),
                    textX,
                    abbreviationY,
                    Utils.SKILL_ABBR_COLOR,
                    false);
            guiGraphics.drawString(client.font,
                    Component.translatable("screen.skill.experience", Utils.numberFormat(skillLevel),
                            HandlerCommonConfig.HANDLER.instance().skillMaxLevel),
                    textX,
                    levelY,
                    Color.WHITE.getRGB(),
                    false);
        }

        if (hoveredSkill != null) {
            Utils.drawToolTip(guiGraphics, Component.translatable(hoveredSkill.getKey()), mouseX, mouseY);
        }
    }

    private void drawDetailBackground(GuiGraphics guiGraphics, int panelX, int panelY) {
        DetailPageState detailState = buildDetailPageState();
        if (detailState != null && detailState.skill().background != null) {
            guiGraphics.blit(detailState.skill().background, panelX + DETAIL_CONTENT_X, panelY + 30, 0.0F, 0.0F,
                    DETAIL_CONTENT_WIDTH, 156, 16, 16);
        }
    }

    private void drawDetail(GuiGraphics guiGraphics, int panelX, int panelY, int mouseX, int mouseY) {
        if (client.player == null) {
            return;
        }

        DetailPageState detailState = buildDetailPageState();
        if (detailState == null) {
            return;
        }

        Skill skill = detailState.skill();
        String key = skill.getKey();
        int skillLevel = detailState.skillLevel();
        String rank = skill.getRank(skillLevel).getString();

        guiGraphics.blit(skill.getLockedTexture(), panelX + 12, panelY + 9, 0.0F, 0.0F, 16, 16, 16, 16);
        guiGraphics.drawString(client.font, Component.translatable(key).withStyle(ChatFormatting.BOLD), panelX + 34, panelY + 8, Utils.FONT_COLOR, false);
        guiGraphics.drawString(client.font,
                Component.translatable("screen.perk.level_and_rank", Utils.numberFormat(skillLevel),
                        HandlerCommonConfig.HANDLER.instance().skillMaxLevel, rank),
                panelX + 34,
                panelY + 18,
                Utils.FONT_COLOR,
                false);

        drawDetailFooter(guiGraphics, panelX, panelY, mouseX, mouseY);
        drawLevelUpButton(guiGraphics, detailState, panelX, panelY, mouseX, mouseY);

        int rowStartY = centeredContentTop(panelY, detailState.visibleRows().size(), DETAIL_ROW_HEIGHT);
        for (int rowIndex = 0; rowIndex < detailState.visibleRows().size(); rowIndex++) {
            List<Object> row = detailState.visibleRows().get(rowIndex);
            int rowY = rowStartY + rowIndex * DETAIL_ROW_HEIGHT;
            drawDetailRow(guiGraphics, detailState.capability(), row, panelX, rowY, mouseX, mouseY);
        }

        drawBackButton(guiGraphics, panelX, panelY, mouseX, mouseY,
                Component.translatable("tooltip.perk.back"), PAGE_OVERVIEW);
        drawDetailPageControls(guiGraphics, panelX, panelY, mouseX, mouseY);
    }

    private void drawTitles(GuiGraphics guiGraphics, int panelX, int panelY, int mouseX, int mouseY, float delta) {
        updateSearchBox(panelX, panelY);
        Utils.drawCenter(guiGraphics, Component.translatable("screen.title.choose_your_title"), panelX + PANEL_CENTER_X, panelY + 7);

        this.searchTitle.render(guiGraphics, mouseX, mouseY, delta);

        List<Title> filteredTitles = getFilteredTitles();
        int visibleCount = Math.min(filteredTitles.size(), TITLE_VISIBLE_ROWS);
        int maxOffset = Math.max(0, filteredTitles.size() - TITLE_VISIBLE_ROWS);
        this.scrollDropDown = Mth.clamp(this.scrollDropDown, 0, maxOffset);

        for (int i = 0; i < TITLE_VISIBLE_ROWS; i++) {
            Area rowBounds = titleRowBounds(panelX, panelY, i);

            if (i < visibleCount) {
                int titleIndex = i + this.scrollDropDown;
                Title title = filteredTitles.get(titleIndex);
                boolean hovered = rowBounds.contains(mouseX, mouseY) && !this.scrollingDropDown;
                SkillCapability localCap = SkillCapability.getLocal();
                boolean selectedTitle = localCap != null && title == RegistryTitles.getTitle(localCap.getPlayerTitle());
                int textColor = selectedTitle ? Utils.TITLE_SELECTED_COLOR : Utils.TITLE_UNSELECTED_COLOR;

                if (hovered) {
                    guiGraphics.fill(rowBounds.x(), rowBounds.y(), rowBounds.x() + TITLE_LIST_WIDTH, rowBounds.y() + TITLE_ROW_HEIGHT, 0xFF505050);
                }
                guiGraphics.drawString(client.font, Component.translatable(title.getKey()), rowBounds.x() + 2, rowBounds.y() + 2, textColor, false);

                if (hovered) {
                    Utils.drawToolTipList(guiGraphics, title.tooltip(), mouseX, mouseY);
                }
            }
        }

        updateTitleScrollbar(panelX, panelY, mouseY, filteredTitles.size());
        drawTitleScrollbar(guiGraphics, panelX, panelY, filteredTitles.size(), mouseX, mouseY);

        drawTitleModToggle(guiGraphics, panelX, panelY, mouseX, mouseY);
        drawBackButton(guiGraphics, panelX, panelY, mouseX, mouseY,
                Component.translatable("tooltip.title.back"), PAGE_OVERVIEW);
    }

    private void drawTitleButton(GuiGraphics guiGraphics, int panelX, int panelY, int mouseX, int mouseY) {
        SkillCapability localCap = SkillCapability.getLocal();
        Title currentTitle = localCap == null ? null : RegistryTitles.getTitle(localCap.getPlayerTitle());
        String titleText = currentTitle == null ? "" : Component.translatable(currentTitle.getKey()).getString();
        String displayTitle = ellipsize(titleText, 98);
        int innerWidth = client.font.width(displayTitle) + 15;
        int leftCapX = panelX + PANEL_CENTER_X - innerWidth / 2 - 2;
        int buttonY = panelY + TITLE_BUTTON_Y;
        boolean hovered = titleButtonBounds(panelX, panelY, innerWidth).contains(mouseX, mouseY);

        guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_OVERVIEW], leftCapX, buttonY, hovered ? 4 : 0, 214, 2, 14);
        guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_OVERVIEW], leftCapX + 2, buttonY, 0, hovered ? 228 : 242, innerWidth, 14);
        guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_OVERVIEW], leftCapX + innerWidth + 2, buttonY, hovered ? 6 : 2, 214, 2, 14);
        guiGraphics.drawString(client.font, displayTitle, leftCapX + 4, buttonY + 3, Color.WHITE.getRGB());
        guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_OVERVIEW], leftCapX + innerWidth - 8, buttonY + 3, 8, 218, 8, 8);

        if (hovered) {
            Utils.drawToolTip(guiGraphics, Component.translatable("tooltip.edit_title"), mouseX, mouseY);
        }
    }

    private void drawDetailFooter(GuiGraphics guiGraphics, int panelX, int panelY, int mouseX, int mouseY) {
        drawModToggleButton(guiGraphics, panelX, panelY, mouseX, mouseY);
        drawPassiveSortButton(guiGraphics, panelX, panelY, mouseX, mouseY);
        drawPerkSortButton(guiGraphics, panelX, panelY, mouseX, mouseY);
    }

    private void drawModToggleButton(GuiGraphics guiGraphics, int panelX, int panelY, int mouseX, int mouseY) {
        Area bounds = footerButtonBounds(panelX, panelY, FOOTER_MOD_TOGGLE_X);
        boolean hovered = bounds.contains(mouseX, mouseY);
        guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_DETAIL], bounds.x(), bounds.y(), 30, hovered ? 179 : 167, FOOTER_BUTTON_SIZE, FOOTER_BUTTON_SIZE);

        if (hovered) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("tooltip.sort.button.mod_names").withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("tooltip.sort.button.true").withStyle(HandlerConfigClient.showPerkModName.get() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.sort.button.false").withStyle(!HandlerConfigClient.showPerkModName.get() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            Utils.drawToolTipList(guiGraphics, tooltip, mouseX, mouseY);
        }
    }

    private void drawPassiveSortButton(GuiGraphics guiGraphics, int panelX, int panelY, int mouseX, int mouseY) {
        Area bounds = footerButtonBounds(panelX, panelY, FOOTER_PASSIVE_SORT_X);
        boolean hovered = bounds.contains(mouseX, mouseY);
        guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_DETAIL], bounds.x(), bounds.y(), 42, hovered ? 179 : 167, FOOTER_BUTTON_SIZE, FOOTER_BUTTON_SIZE);

        if (hovered) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("tooltip.sort.button.passives").withStyle(ChatFormatting.DARK_AQUA));
            for (SortPassives sort : SortPassives.values()) {
                ChatFormatting color = sort == HandlerConfigClient.sortPassive.get() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
                tooltip.add(Component.translatable(sort.order).withStyle(color));
            }
            Utils.drawToolTipList(guiGraphics, tooltip, mouseX, mouseY);
        }
    }

    private void drawPerkSortButton(GuiGraphics guiGraphics, int panelX, int panelY, int mouseX, int mouseY) {
        Area bounds = footerButtonBounds(panelX, panelY, FOOTER_PERK_SORT_X);
        boolean hovered = bounds.contains(mouseX, mouseY);
        guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_DETAIL], bounds.x(), bounds.y(), 54, hovered ? 179 : 167, FOOTER_BUTTON_SIZE, FOOTER_BUTTON_SIZE);

        if (hovered) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("tooltip.sort.button.perks").withStyle(ChatFormatting.DARK_AQUA));
            for (SortPerks sort : SortPerks.values()) {
                ChatFormatting color = sort == HandlerConfigClient.sortPerk.get() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
                tooltip.add(Component.translatable(sort.order).withStyle(color));
            }
            Utils.drawToolTipList(guiGraphics, tooltip, mouseX, mouseY);
        }
    }

    private void drawLevelUpButton(GuiGraphics guiGraphics, DetailPageState detailState, int panelX, int panelY, int mouseX, int mouseY) {
        Area bounds = levelUpButtonBounds(panelX, panelY);
        int skillLevel = detailState.skillLevel();
        int frame = skillLevel < HandlerCommonConfig.HANDLER.instance().skillMaxLevel ? (this.pulseOn ? 6 : 0) : 12;
        boolean canLevelUpSkill = canLevelUp(detailState.skill(), skillLevel);

        guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_DETAIL], panelX + 153, panelY + 14, 177 + frame, 1, 6, 6);

        if (bounds.contains(mouseX, mouseY)) {
            if (detailState.capability().getGlobalLevel() >= HandlerCommonConfig.HANDLER.instance().playersMaxGlobalLevel) {
                Utils.drawToolTip(guiGraphics,
                        Component.translatable("tooltip.skill.global_max_level", HandlerCommonConfig.HANDLER.instance().playersMaxGlobalLevel)
                                .withStyle(ChatFormatting.RED),
                        mouseX,
                        mouseY);
            } else if (skillLevel < HandlerCommonConfig.HANDLER.instance().skillMaxLevel) {
                ChatFormatting color = canLevelUpSkill ? ChatFormatting.GREEN : ChatFormatting.RED;
                Utils.drawToolTip(guiGraphics,
                        Component.translatable("tooltip.skill.level_up",
                                Component.literal(String.valueOf(SkillLevelUpSP.requiredExperienceLevels(skillLevel))).withStyle(color),
                                Component.literal(String.valueOf(SkillLevelUpSP.requiredPoints(skillLevel))).withStyle(color),
                                Component.translatable(detailState.skill().getKey()).withStyle(color)).withStyle(ChatFormatting.GRAY),
                        mouseX,
                        mouseY);
                this.tick = this.maxTick - 5;
                this.pulseOn = canLevelUpSkill;
            } else {
                Utils.drawToolTip(guiGraphics,
                        Component.translatable("tooltip.skill.max_level", Component.translatable(detailState.skill().getKey()).withStyle(ChatFormatting.GREEN))
                                .withStyle(ChatFormatting.GRAY),
                        mouseX,
                        mouseY);
            }
        } else if (canLevelUpSkill) {
            this.tick++;
        } else {
            this.pulseOn = false;
        }

        if (this.tick >= this.maxTick) {
            this.pulseOn = !this.pulseOn;
            this.tick = 0;
        }
    }

    private void drawDetailRow(GuiGraphics guiGraphics, SkillCapability capability, List<Object> row, int panelX, int rowY, int mouseX, int mouseY) {
        for (int itemIndex = 0; itemIndex < row.size(); itemIndex++) {
            IconLayout iconLayout = iconLayout(panelX, rowY, row.size(), itemIndex);
            Object item = row.get(itemIndex);

            if (item instanceof Passive passive) {
                drawPassiveIcon(guiGraphics, capability, passive, iconLayout, mouseX, mouseY);
            } else if (item instanceof Perk perk) {
                drawPerkIcon(guiGraphics, capability, perk, iconLayout, mouseX, mouseY);
            }
        }
    }

    private void drawPassiveIcon(GuiGraphics guiGraphics, SkillCapability capability, Passive passive, IconLayout iconLayout, int mouseX, int mouseY) {
        int maxState = passive.getLevel() == passive.getMaxLevel() ? 24 : 0;
        guiGraphics.blit(passive.getTexture(), iconLayout.textureX(), iconLayout.textureY(), 0.0F, 0.0F, 20, 20, 20, 20);
        guiGraphics.blit(HandlerResources.PERK_ICONS, iconLayout.frameX(), iconLayout.frameY(), 0.0F, maxState, 24, 24, 72, 72);

        int iconAddState = passive.getLevel() < passive.getMaxLevel()
                && capability.getSkillLevel(passive.getSkill()) >= passive.getNextLevelUp() ? 10 : 0;
        int iconLessState = passive.getLevel() > 0 ? 10 : 0;

        if (iconLayout.frameBounds().contains(mouseX, mouseY)) {
            if (iconLayout.decrementBounds().contains(mouseX, mouseY) && passive.getLevel() > 0) {
                iconLessState = 20;
            }
            if (iconLayout.incrementBounds().contains(mouseX, mouseY)
                    && passive.getLevel() < passive.getMaxLevel()
                    && capability.getSkillLevel(passive.getSkill()) >= passive.getNextLevelUp()) {
                iconAddState = 20;
            }

            guiGraphics.pose().pushPose();
            Utils.drawToolTipList(guiGraphics, PassiveTooltip.tooltip(passive), mouseX, mouseY);
            RenderSystem.enableBlend();
            guiGraphics.blit(HandlerResources.PERK_ICONS, iconLayout.frameX(), iconLayout.frameY(), 0.0F, 48.0F, 24, 24, 72, 72);
            guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_DETAIL], iconLayout.frameX() + 2, iconLayout.frameY() + 2, 1, 167 + iconLessState, 9, 9);
            guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_DETAIL], iconLayout.frameX() + 13, iconLayout.frameY() + 2, 11, 167 + iconAddState, 9, 9);
            guiGraphics.pose().popPose();
        }

        String level = String.valueOf(passive.getLevel());
        int labelX = iconLayout.frameX() + 12 - client.font.width(level) / 2;
        guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_DETAIL], labelX - 1, iconLayout.frameY() + 17, 21, 167, 7, 8);
        guiGraphics.drawString(client.font, level, labelX, iconLayout.frameY() + 18, Color.BLACK.getRGB(), false);
        guiGraphics.drawString(client.font, level, labelX - 1, iconLayout.frameY() + 17, Color.WHITE.getRGB(), false);
    }

    private void drawPerkIcon(GuiGraphics guiGraphics, SkillCapability capability, Perk perk, IconLayout iconLayout, int mouseX, int mouseY) {
        int toggleState = perk.canPerk() ? 24 : 0;
        guiGraphics.blit(perk.getTexture(), iconLayout.textureX(), iconLayout.textureY(), 0.0F, 0.0F, 20, 20, 20, 20);
        guiGraphics.blit(HandlerResources.PERK_ICONS, iconLayout.frameX(), iconLayout.frameY(), 24.0F, toggleState, 24, 24, 72, 72);

        if (!perk.getToggle()) {
            guiGraphics.pose().pushPose();
            RenderSystem.enableBlend();
            guiGraphics.blit(HandlerResources.PERK_ICONS, iconLayout.frameX(), iconLayout.frameY(), 24.0F, 48.0F, 24, 24, 72, 72);
            guiGraphics.pose().popPose();
        }

        if (perk.getMaxRank() > 1) {
            int currentRank = capability.getPerkRank(perk);
            String rankText = currentRank > 0 ? Utils.intToRoman(currentRank) : "-";
            int textX = iconLayout.frameX() + 12 - client.font.width(rankText) / 2;
            guiGraphics.drawString(client.font, rankText, textX, iconLayout.frameY() + 17, currentRank > 0 ? 0xAA00FF : 0x888888, false);
        }

        if (iconLayout.frameBounds().contains(mouseX, mouseY)) {
            Utils.drawToolTipList(guiGraphics, PerkTooltip.tooltip(perk), mouseX, mouseY);
        }
    }

    private void drawTitleScrollbar(GuiGraphics guiGraphics, int panelX, int panelY, int titleCount, int mouseX, int mouseY) {
        int maxOffset = Math.max(0, titleCount - TITLE_VISIBLE_ROWS);
        int trackX = panelX + TITLE_SCROLL_X;
        int trackY = panelY + TITLE_LIST_Y;

        int scrollY = maxOffset == 0 ? trackY : this.scrollHandleY;
        guiGraphics.fill(trackX + 1, scrollY, trackX + TITLE_SCROLL_WIDTH - 1, scrollY + TITLE_SCROLL_HANDLE_HEIGHT, 0xFF666666);
    }

    private void drawTitleModToggle(GuiGraphics guiGraphics, int panelX, int panelY, int mouseX, int mouseY) {
        Area bounds = footerButtonBounds(panelX, panelY, FOOTER_MOD_TOGGLE_X);
        boolean hovered = bounds.contains(mouseX, mouseY);

        guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_DETAIL], bounds.x(), bounds.y(), 30, hovered ? 179 : 167, FOOTER_BUTTON_SIZE, FOOTER_BUTTON_SIZE);

        if (hovered) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("tooltip.sort.button.mod_names").withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("tooltip.sort.button.true").withStyle(HandlerConfigClient.showTitleModName.get() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.sort.button.false").withStyle(!HandlerConfigClient.showTitleModName.get() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            Utils.drawToolTipList(guiGraphics, tooltip, mouseX, mouseY);
        }
    }

    private void drawBackButton(GuiGraphics guiGraphics, int panelX, int panelY, int mouseX, int mouseY, Component tooltip, int destinationPage) {
        Area bounds = backButtonBounds(panelX, panelY);
        boolean hovered = bounds.contains(mouseX, mouseY);

        int textureIndex = this.selectedPage == PAGE_TITLES ? PAGE_TITLES : PAGE_DETAIL;
        guiGraphics.blit(HandlerResources.PERK_PAGE[textureIndex], bounds.x(), bounds.y(), 204, 0, BACK_BUTTON_WIDTH, BACK_BUTTON_HEIGHT);
        if (hovered) {
            guiGraphics.blit(HandlerResources.PERK_PAGE[textureIndex], bounds.x(), bounds.y(), 222, 0, BACK_BUTTON_WIDTH, BACK_BUTTON_HEIGHT);
            Utils.drawToolTip(guiGraphics, tooltip, mouseX, mouseY);
        }
    }

    private void drawDetailPageControls(GuiGraphics guiGraphics, int panelX, int panelY, int mouseX, int mouseY) {
        if (this.perkSizePage <= 0) {
            return;
        }

        String pageNumber = (this.perkActualPage + 1) + "/" + (this.perkSizePage + 1);
        int pageTextX = panelX + PANEL_CENTER_X - client.font.width(pageNumber) / 2;
        int pageTextY = panelY + FOOTER_Y + 2;
        guiGraphics.drawString(client.font, pageNumber, pageTextX, pageTextY, Color.WHITE.getRGB(), false);

        if (this.perkActualPage > 0) {
            Area previousBounds = detailPreviousPageBounds(panelX, panelY, pageNumber);
            boolean hovered = previousBounds.contains(mouseX, mouseY);
            guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_DETAIL], previousBounds.x(), previousBounds.y(), 241, hovered ? 12 : 0, 7, 11);
            if (hovered) {
                Utils.drawToolTip(guiGraphics, Component.translatable("tooltip.perk.previous"), mouseX, mouseY);
            }
        }

        if (this.perkActualPage < this.perkSizePage) {
            Area nextBounds = detailNextPageBounds(panelX, panelY, pageNumber);
            boolean hovered = nextBounds.contains(mouseX, mouseY);
            guiGraphics.blit(HandlerResources.PERK_PAGE[PAGE_DETAIL], nextBounds.x(), nextBounds.y(), 249, hovered ? 12 : 0, 7, 11);
            if (hovered) {
                Utils.drawToolTip(guiGraphics, Component.translatable("tooltip.perk.next"), mouseX, mouseY);
            }
        }
    }

    private void updateTitleScrollbar(int panelX, int panelY, int mouseY, int titleCount) {
        int maxOffset = Math.max(0, titleCount - TITLE_VISIBLE_ROWS);
        if (maxOffset == 0) {
            this.scrollDropDown = 0;
            this.scrollHandleY = panelY + TITLE_LIST_Y;
            return;
        }

        int listY = panelY + TITLE_LIST_Y;
        int scrollTravel = titleScrollTravel();
        if (this.scrollingDropDown) {
            this.scrollHandleY = Mth.clamp(mouseY - (TITLE_SCROLL_HANDLE_HEIGHT / 2), listY, listY + scrollTravel);
            this.scrollDropDown = Math.round((float) maxOffset * (this.scrollHandleY - listY) / scrollTravel);
        } else {
            this.scrollHandleY = listY + Math.round((float) scrollTravel * this.scrollDropDown / maxOffset);
        }
    }

    private DetailPageState buildDetailPageState() {
        Skill skill = RegistrySkills.getSkill(this.selectedSkill);
        if (skill == null) {
            return null;
        }

        SkillCapability capability = SkillCapability.getLocal();
        if (capability == null) {
            return null;
        }
        List<Passive> passives = new ArrayList<>(skill.getPassives(skill));
        List<Perk> perks = new ArrayList<>(skill.getPerks(skill));

        switch (HandlerConfigClient.sortPassive.get()) {
            case ByName -> passives.sort(new SortPassiveByName());
            case ByReverseName -> passives.sort(new SortPassiveByName().reversed());
        }

        switch (HandlerConfigClient.sortPerk.get()) {
            case ByName -> perks.sort(new SortPerkByName());
            case ByReverseName -> perks.sort(new SortPerkByName().reversed());
            case ByLevel -> perks.sort(new SortPerkList());
        }

        List<Object> combined = new ArrayList<>();
        combined.addAll(passives);
        combined.addAll(perks);

        List<List<Object>> rows = chunkList(combined, DETAIL_ITEMS_PER_ROW);
        int pageCount = Math.max(1, Mth.ceil((float) rows.size() / DETAIL_ROWS_PER_PAGE));
        this.perkSizePage = Math.max(0, pageCount - 1);
        this.perkActualPage = Mth.clamp(this.perkActualPage, 0, this.perkSizePage);

        int fromIndex = this.perkActualPage * DETAIL_ROWS_PER_PAGE;
        int toIndex = Math.min(rows.size(), fromIndex + DETAIL_ROWS_PER_PAGE);
        List<List<Object>> visibleRows = rows.subList(fromIndex, toIndex);

        return new DetailPageState(skill, capability, skill.getLevel(), visibleRows);
    }

    private List<Skill> getSortedSkills() {
        List<Skill> skills = new ArrayList<>(RegistrySkills.getCachedValues());
        skills.sort(new SortSkillByDateCreated());
        return skills;
    }

    private List<Title> getFilteredTitles() {
        List<Title> titles = new ArrayList<>(RegistryTitles.TITLES_REGISTRY.get().getValues().stream().toList());
        List<Title> unlocked = new ArrayList<>();
        List<Title> locked = new ArrayList<>();

        for (Title title : titles) {
            if (title.getRequirement()) {
                unlocked.add(title);
            } else if (!title.HideRequirements) {
                locked.add(title);
            }
        }

        unlocked.sort(new SortTitleByName());
        locked.sort(new SortTitleByName());

        List<Title> combined = new ArrayList<>(unlocked.size() + locked.size());
        combined.addAll(unlocked);
        combined.addAll(locked);

        String searchText = this.searchTitle == null ? this.searchValue : this.searchTitle.getValue();
        if (searchText == null || searchText.isBlank()) {
            return combined;
        }

        List<Title> filtered = new ArrayList<>();
        String loweredSearch = searchText.toLowerCase();
        for (Title title : combined) {
            if (Component.translatable(title.getKey()).getString().toLowerCase().contains(loweredSearch)) {
                filtered.add(title);
            }
        }
        return filtered;
    }

    private boolean canLevelUp(Skill skill, int skillLevel) {
        if (client.player == null) {
            return false;
        }

        return client.player.isCreative()
                || Utils.getExperienceForLevel(SkillLevelUpSP.requiredExperienceLevels(skillLevel)) <= Utils.getPlayerXP(client.player)
                || SkillLevelUpSP.requiredExperienceLevels(skillLevel) <= client.player.experienceLevel;
    }

    private void updateSearchBox(int panelX, int panelY) {
        if (this.searchTitle == null) {
            return;
        }

        this.searchTitle.setX(panelX + TITLE_SEARCH_X);
        this.searchTitle.setY(panelY + TITLE_SEARCH_Y);
        this.searchTitle.setVisible(this.selectedPage == PAGE_TITLES);
        if (this.selectedPage != PAGE_TITLES) {
            this.searchTitle.setFocused(false);
        }
    }

    private void openSkillDetail(Skill skill) {
        this.tick = this.maxTick / 2;
        this.pulseOn = true;
        this.selectedSkill = skill.getName();
        this.selectedPage = PAGE_DETAIL;
        this.perkActualPage = 0;
        this.scrollingDropDown = false;
        this.searchTitle.setFocused(false);
        Utils.playSound();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !L2TabsIntegration.isModLoaded() && !LegendaryTabsIntegration.isModLoaded()) {
            DrawTabs.mouseClicked(button);
        }

        if (this.selectedPage == PAGE_TITLES && this.searchTitle != null && this.searchTitle.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button != 0) {
            return false;
        }

        boolean handled = switch (this.selectedPage) {
            case PAGE_OVERVIEW -> handleOverviewClick(mouseX, mouseY);
            case PAGE_DETAIL -> handleDetailClick(mouseX, mouseY);
            case PAGE_TITLES -> handleTitleClick(mouseX, mouseY);
            default -> false;
        };

        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.scrollingDropDown = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (this.selectedPage == PAGE_DETAIL && this.perkSizePage > 0) {
            if (amount > 0 && this.perkActualPage > 0) {
                this.perkActualPage--;
                Utils.playSound();
                return true;
            }
            if (amount < 0 && this.perkActualPage < this.perkSizePage) {
                this.perkActualPage++;
                Utils.playSound();
                return true;
            }
        }

        if (this.selectedPage == PAGE_TITLES) {
            int maxOffset = Math.max(0, getFilteredTitles().size() - TITLE_VISIBLE_ROWS);
            this.scrollDropDown = Mth.clamp((int) (this.scrollDropDown - amount), 0, maxOffset);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.selectedPage == PAGE_TITLES && this.searchTitle != null && this.searchTitle.charTyped(chr, modifiers)) {
            this.searchValue = this.searchTitle.getValue();
            this.scrollDropDown = 0;
            return true;
        }

        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.selectedPage == PAGE_TITLES && this.searchTitle != null && this.searchTitle.keyPressed(keyCode, scanCode, modifiers)) {
            this.searchValue = this.searchTitle.getValue();
            this.scrollDropDown = 0;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.perkActualPage = 0;
        this.selectedPage = PAGE_OVERVIEW;
        this.scrollDropDown = 0;
        this.scrollingDropDown = false;
        this.searchValue = "";
        if (this.searchTitle != null) {
            this.searchTitle.setValue("");
        }
        if (!L2TabsIntegration.isModLoaded() && !LegendaryTabsIntegration.isModLoaded()) {
            DrawTabs.onClose();
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean handleOverviewClick(double mouseX, double mouseY) {
        int panelX = panelLeft();
        int panelY = panelTop();

        SkillCapability localCap = SkillCapability.getLocal();
        Title currentTitle = localCap == null ? null : RegistryTitles.getTitle(localCap.getPlayerTitle());
        String titleText = currentTitle == null ? "" : Component.translatable(currentTitle.getKey()).getString();
        String displayTitle = ellipsize(titleText, 98);
        int innerWidth = client.font.width(displayTitle) + 15;
        if (titleButtonBounds(panelX, panelY, innerWidth).contains(mouseX, mouseY)) {
            this.selectedPage = PAGE_TITLES;
            this.searchTitle.setFocused(true);
            Utils.playSound();
            return true;
        }

        List<Skill> skills = getSortedSkills();
        for (int i = 0; i < skills.size(); i++) {
            if (overviewSlotBounds(panelX, panelY, i).contains(mouseX, mouseY)) {
                openSkillDetail(skills.get(i));
                return true;
            }
        }

        return false;
    }

    private boolean handleDetailClick(double mouseX, double mouseY) {
        int panelX = panelLeft();
        int panelY = panelTop();

        if (footerButtonBounds(panelX, panelY, FOOTER_MOD_TOGGLE_X).contains(mouseX, mouseY)) {
            HandlerConfigClient.showPerkModName.set(!HandlerConfigClient.showPerkModName.get());
            Utils.playSound();
            return true;
        }

        if (footerButtonBounds(panelX, panelY, FOOTER_PASSIVE_SORT_X).contains(mouseX, mouseY)) {
            HandlerConfigClient.sortPassive.set(SortPassives.fromIndex(HandlerConfigClient.sortPassive.get().index + 1));
            Utils.playSound();
            return true;
        }

        if (footerButtonBounds(panelX, panelY, FOOTER_PERK_SORT_X).contains(mouseX, mouseY)) {
            HandlerConfigClient.sortPerk.set(SortPerks.fromIndex(HandlerConfigClient.sortPerk.get().index + 1));
            Utils.playSound();
            return true;
        }

        if (backButtonBounds(panelX, panelY).contains(mouseX, mouseY)) {
            this.perkActualPage = 0;
            this.selectedPage = PAGE_OVERVIEW;
            Utils.playSound();
            return true;
        }

        if (this.perkSizePage > 0) {
            String pageNumber = (this.perkActualPage + 1) + "/" + (this.perkSizePage + 1);
            if (this.perkActualPage > 0 && detailPreviousPageBounds(panelX, panelY, pageNumber).contains(mouseX, mouseY)) {
                this.perkActualPage--;
                Utils.playSound();
                return true;
            }
            if (this.perkActualPage < this.perkSizePage && detailNextPageBounds(panelX, panelY, pageNumber).contains(mouseX, mouseY)) {
                this.perkActualPage++;
                Utils.playSound();
                return true;
            }
        }

        DetailPageState detailState = buildDetailPageState();
        if (detailState == null) {
            return false;
        }

        if (levelUpButtonBounds(panelX, panelY).contains(mouseX, mouseY)
                && detailState.capability().getGlobalLevel() < HandlerCommonConfig.HANDLER.instance().playersMaxGlobalLevel
                && detailState.skillLevel() < HandlerCommonConfig.HANDLER.instance().skillMaxLevel
                && canLevelUp(detailState.skill(), detailState.skillLevel())) {
            Utils.playSound();
            if (KubeJSIntegration.isModLoaded()) {
                boolean cancelled = new KubeJSIntegration().postLevelUpEvent(client.player, detailState.skill());
                if (!cancelled) {
                    SkillLevelUpSP.send(detailState.skill());
                }
            } else {
                SkillLevelUpSP.send(detailState.skill());
            }
            return true;
        }

        int rowStartY = centeredContentTop(panelY, detailState.visibleRows().size(), DETAIL_ROW_HEIGHT);
        for (int rowIndex = 0; rowIndex < detailState.visibleRows().size(); rowIndex++) {
            List<Object> row = detailState.visibleRows().get(rowIndex);
            int rowY = rowStartY + rowIndex * DETAIL_ROW_HEIGHT;
            for (int itemIndex = 0; itemIndex < row.size(); itemIndex++) {
                IconLayout iconLayout = iconLayout(panelX, rowY, row.size(), itemIndex);
                Object item = row.get(itemIndex);
                if (item instanceof Passive passive) {
                    if (iconLayout.decrementBounds().contains(mouseX, mouseY) && passive.getLevel() > 0) {
                        Utils.playSound();
                        PassiveLevelDownSP.send(passive);
                        return true;
                    }
                    if (iconLayout.incrementBounds().contains(mouseX, mouseY)
                            && passive.getLevel() < passive.getMaxLevel()
                            && detailState.capability().getSkillLevel(passive.getSkill()) >= passive.getNextLevelUp()) {
                        Utils.playSound();
                        PassiveLevelUpSP.send(passive);
                        return true;
                    }
                } else if (item instanceof Perk perk && iconLayout.frameBounds().contains(mouseX, mouseY) && perk.getToggle()) {
                    Utils.playSound();
                    int currentRank = detailState.capability().getPerkRank(perk);
                    if (perk.getMaxRank() <= 1) {
                        TogglePerkSP.send(perk, currentRank == 0);
                    } else {
                        int nextRank = currentRank + 1;
                        if (nextRank > perk.getMaxRank()) {
                            nextRank = 0;
                        }
                        TogglePerkSP.send(perk, nextRank);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private boolean handleTitleClick(double mouseX, double mouseY) {
        int panelX = panelLeft();
        int panelY = panelTop();
        List<Title> filteredTitles = getFilteredTitles();
        int maxOffset = Math.max(0, filteredTitles.size() - TITLE_VISIBLE_ROWS);

        if (footerButtonBounds(panelX, panelY, FOOTER_MOD_TOGGLE_X).contains(mouseX, mouseY)) {
            HandlerConfigClient.showTitleModName.set(!HandlerConfigClient.showTitleModName.get());
            Utils.playSound();
            return true;
        }

        if (backButtonBounds(panelX, panelY).contains(mouseX, mouseY)) {
            this.perkActualPage = 0;
            this.selectedPage = PAGE_OVERVIEW;
            this.searchTitle.setFocused(false);
            Utils.playSound();
            return true;
        }

        if (maxOffset > 0 && scrollbarBounds(panelX, panelY).contains(mouseX, mouseY)) {
            this.scrollingDropDown = true;
            updateTitleScrollbar(panelX, panelY, (int) mouseY, filteredTitles.size());
            Utils.playSound();
            return true;
        }

        int visibleCount = Math.min(filteredTitles.size(), TITLE_VISIBLE_ROWS);
        for (int i = 0; i < visibleCount; i++) {
            Title title = filteredTitles.get(i + this.scrollDropDown);
            if (titleRowBounds(panelX, panelY, i).contains(mouseX, mouseY) && title.getRequirement()) {
                SetPlayerTitleSP.send(title);
                Utils.playSound();
                return true;
            }
        }

        return false;
    }

    private Area overviewSlotBounds(int panelX, int panelY, int index) {
        int column = index % OVERVIEW_SLOT_COLUMNS;
        int row = index / OVERVIEW_SLOT_COLUMNS;
        int slotX = column == 0 ? panelX + OVERVIEW_LEFT_SLOT_X : panelX + OVERVIEW_RIGHT_SLOT_X;
        return new Area(slotX,
                panelY + OVERVIEW_FIRST_SLOT_Y + row * OVERVIEW_SLOT_ROW_SPACING,
                OVERVIEW_SLOT_WIDTH,
                OVERVIEW_SLOT_HEIGHT);
    }

    private Area overviewContentBounds(Area slotBounds) {
        return slotBounds.inset(OVERVIEW_CONTENT_PAD_X, OVERVIEW_CONTENT_PAD_Y);
    }

    private Area overviewIconBounds(Area contentBounds) {
        return new Area(contentBounds.x(),
                contentBounds.y() + Math.max(0, (contentBounds.height() - OVERVIEW_ICON_SIZE) / 2),
                OVERVIEW_ICON_SIZE,
                OVERVIEW_ICON_SIZE);
    }

    private Area titleButtonBounds(int panelX, int panelY, int innerWidth) {
        return new Area(panelX + PANEL_CENTER_X - innerWidth / 2 - 2, panelY + TITLE_BUTTON_Y, innerWidth + 4, 14);
    }

    private Area footerButtonBounds(int panelX, int panelY, int xOffset) {
        return new Area(panelX + xOffset, panelY + FOOTER_Y, FOOTER_BUTTON_SIZE, FOOTER_BUTTON_SIZE);
    }

    private Area backButtonBounds(int panelX, int panelY) {
        return new Area(panelX + BACK_BUTTON_X, panelY + FOOTER_Y, BACK_BUTTON_WIDTH, BACK_BUTTON_HEIGHT);
    }

    private Area levelUpButtonBounds(int panelX, int panelY) {
        return new Area(panelX + LEVEL_UP_BUTTON_X, panelY + LEVEL_UP_BUTTON_Y, LEVEL_UP_BUTTON_SIZE, LEVEL_UP_BUTTON_SIZE);
    }

    private Area titleRowBounds(int panelX, int panelY, int visibleIndex) {
        return new Area(panelX + TITLE_LIST_X, panelY + TITLE_LIST_Y + visibleIndex * TITLE_ROW_HEIGHT, TITLE_LIST_WIDTH, TITLE_ROW_HEIGHT);
    }

    private Area scrollbarBounds(int panelX, int panelY) {
        return new Area(panelX + TITLE_SCROLL_X, panelY + TITLE_LIST_Y, TITLE_SCROLL_WIDTH, TITLE_LIST_VIEWPORT_HEIGHT);
    }

    private Area detailPreviousPageBounds(int panelX, int panelY, String pageNumber) {
        int pageTextX = panelX + PANEL_CENTER_X - client.font.width(pageNumber) / 2;
        return new Area(pageTextX - 12, panelY + FOOTER_Y, 7, 11);
    }

    private Area detailNextPageBounds(int panelX, int panelY, String pageNumber) {
        int pageTextX = panelX + PANEL_CENTER_X - client.font.width(pageNumber) / 2;
        return new Area(pageTextX + client.font.width(pageNumber) + 5, panelY + FOOTER_Y, 7, 11);
    }

    private int panelLeft() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int panelTop() {
        return (this.height - PANEL_HEIGHT) / 2;
    }

    private int centeredContentTop(int panelY, int rowCount, int rowHeight) {
        int contentTop = panelY + DETAIL_CONTENT_Y + 2;
        int availableHeight = DETAIL_CONTENT_HEIGHT - 4;
        int totalHeight = rowCount * rowHeight;
        return contentTop + Math.max(0, (availableHeight - totalHeight) / 2);
    }

    private int titleScrollTravel() {
        return TITLE_LIST_VIEWPORT_HEIGHT - TITLE_SCROLL_HANDLE_HEIGHT;
    }

    private IconLayout iconLayout(int panelX, int rowY, int rowSize, int itemIndex) {
        int rowWidth = DETAIL_ICON_SIZE + Math.max(0, rowSize - 1) * DETAIL_ICON_SPACING;
        int startX = panelX + PANEL_CENTER_X - rowWidth / 2;
        return new IconLayout(startX + itemIndex * DETAIL_ICON_SPACING, rowY);
    }

    private <T> List<List<T>> chunkList(List<T> source, int size) {
        List<List<T>> rows = new ArrayList<>();
        for (int i = 0; i < source.size(); i += size) {
            rows.add(new ArrayList<>(source.subList(i, Math.min(source.size(), i + size))));
        }
        return rows;
    }

    private String ellipsize(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int availableWidth = Math.max(0, maxWidth - this.font.width(ellipsis));
        return this.font.plainSubstrByWidth(text, availableWidth) + ellipsis;
    }

    private record Area(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }

        private Area inset(int xInset, int yInset) {
            return new Area(this.x + xInset, this.y + yInset,
                    Math.max(0, this.width - (xInset * 2)),
                    Math.max(0, this.height - (yInset * 2)));
        }
    }

    private record IconLayout(int frameX, int frameY) {
        private int textureX() {
            return this.frameX + 2;
        }

        private int textureY() {
            return this.frameY + 2;
        }

        private Area frameBounds() {
            return new Area(this.frameX, this.frameY, DETAIL_ICON_SIZE, DETAIL_ICON_SIZE);
        }

        private Area decrementBounds() {
            return new Area(this.frameX + 2, this.frameY + 2, 9, 9);
        }

        private Area incrementBounds() {
            return new Area(this.frameX + 13, this.frameY + 2, 9, 9);
        }
    }

    private record DetailPageState(Skill skill, SkillCapability capability, int skillLevel, List<List<Object>> visibleRows) {
    }

    public static class SortSkillByDateCreated implements Comparator<Skill> {
        @Override
        public int compare(Skill date1, Skill date2) {
            return date1.index - date2.index;
        }
    }

    public static class SortPassiveByName implements Comparator<Passive> {
        @Override
        public int compare(Passive name1, Passive name2) {
            return name1.getName().compareTo(name2.getName());
        }
    }

    public static class SortPerkByName implements Comparator<Perk> {
        @Override
        public int compare(Perk name1, Perk name2) {
            return name1.getName().compareTo(name2.getName());
        }
    }

    public static class SortPerkList implements Comparator<Perk> {
        @Override
        public int compare(Perk lvl1, Perk lvl2) {
            return lvl1.requiredLevel - lvl2.requiredLevel;
        }
    }

    public static class SortTitleByName implements Comparator<Title> {
        @Override
        public int compare(Title name1, Title name2) {
            return name1.getName().compareTo(name2.getName());
        }
    }
}
