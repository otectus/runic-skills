package com.otectus.runicskills.client.screen;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.common.PowerEquipSP;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerSchool;
import com.otectus.runicskills.registry.powers.PowerTier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Minimum-viable Powers panel. Three tier columns (Marks / Seals / Crown), each listing every
 * registered Power with an Equip/Unequip button and a tooltip description on hover. Triggered
 * by the {@code key.runicskills.open_powers} keybind (default {@code U}) and by a button on
 * the existing {@link RunicSkillsScreen} (Phase 3 — not yet wired so as not to perturb that
 * screen's complex layout).
 *
 * <p>Deliberately plain: no texture-blit chrome (the existing skill_panel_*.png assets target
 * a 176×194 layout that doesn't fit a three-column Powers panel), no drag-and-drop, no school
 * color-coded glow. The doc's §3.2 polish is a follow-up; this is the gameplay loop unlock.
 *
 * <p>State changes round-trip through {@link PowerEquipSP}; the server is authoritative on
 * skill-level / slot-cap / disabled gates. After a successful equip the server fires
 * {@code SyncSkillCapabilityCP} which refreshes the local {@link SkillCapability}, and we
 * rebuild the screen on the next render.
 */
@OnlyIn(Dist.CLIENT)
public class PowersScreen extends Screen {

    private static final int TITLE_BAND_HEIGHT = 32;
    private static final int FOOTER_BAND_HEIGHT = 14;
    private static final int COL_HEADER_HEIGHT = 14;
    private static final int LIST_TOP_Y = TITLE_BAND_HEIGHT + 4 + COL_HEADER_HEIGHT;
    private static final int LIST_ROW_HEIGHT = 22;
    private static final int LIST_BOTTOM_PAD = FOOTER_BAND_HEIGHT + 6;
    private static final int COL_PAD = 6;
    private static final int BTN_W = 56;
    private static final int BTN_H = 16;

    private final int[] scroll = new int[]{0, 0, 0}; // one scroll offset per column
    private List<Power> markPool;
    private List<Power> sealPool;
    private List<Power> crownPool;

    public PowersScreen() {
        super(Component.translatable("screen.runicskills.powers.title"));
    }

    @Override
    protected void init() {
        super.init();
        rebuildPools();
        rebuildButtons();
    }

    private void rebuildPools() {
        markPool  = sortByName(RegistryPowers.getByTier(PowerTier.MARK));
        sealPool  = sortByName(RegistryPowers.getByTier(PowerTier.SEAL));
        crownPool = sortByName(RegistryPowers.getByTier(PowerTier.CROWN));
    }

    private static List<Power> sortByName(List<Power> in) {
        List<Power> out = new ArrayList<>(in);
        out.sort(Comparator.comparing(Power::getName));
        return out;
    }

    private void rebuildButtons() {
        clearWidgets();
        SkillCapability cap = SkillCapability.getLocal();
        if (cap == null) return;
        int colWidth = (this.width - 4 * COL_PAD) / 3;
        addColumnButtons(cap, markPool,  PowerTier.MARK,  COL_PAD, colWidth, scroll[0]);
        addColumnButtons(cap, sealPool,  PowerTier.SEAL,  COL_PAD * 2 + colWidth, colWidth, scroll[1]);
        addColumnButtons(cap, crownPool, PowerTier.CROWN, COL_PAD * 3 + 2 * colWidth, colWidth, scroll[2]);
    }

    private void addColumnButtons(SkillCapability cap, List<Power> pool, PowerTier tier,
                                  int x, int colWidth, int scrollOffset) {
        int rowsVisible = Math.max(1, (this.height - LIST_TOP_Y - LIST_BOTTOM_PAD) / LIST_ROW_HEIGHT);
        int start = Math.max(0, Math.min(scrollOffset, Math.max(0, pool.size() - rowsVisible)));
        for (int i = 0; i < rowsVisible && start + i < pool.size(); i++) {
            Power p = pool.get(start + i);
            int rowY = LIST_TOP_Y + i * LIST_ROW_HEIGHT;
            boolean equipped = cap.isPowerEquipped(p);
            boolean disabled = RegistryPowers.isDisabled(p);
            Component label = equipped
                    ? Component.translatable("screen.runicskills.powers.unequip")
                    : Component.translatable("screen.runicskills.powers.equip");
            Button btn = Button.builder(label, b -> sendEquip(p, !equipped))
                    .bounds(x + colWidth - BTN_W - 2, rowY + 2, BTN_W, BTN_H)
                    .build();
            btn.active = !disabled || equipped; // can always unequip even a disabled Power
            this.addRenderableWidget(btn);
        }
    }

    private void sendEquip(Power power, boolean equip) {
        ServerNetworking.sendToServer(new PowerEquipSP(power, equip));
        // The server will respond with SyncSkillCapabilityCP, refreshing our capability cache;
        // re-init on the next frame so the buttons reflect the new state. Doing it inline here
        // would race against the packet round-trip.
        Minecraft.getInstance().tell(this::rebuildButtons);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        renderBackground(g);

        SkillCapability cap = SkillCapability.getLocal();
        if (cap == null) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.runicskills.powers.no_capability"),
                    this.width / 2, this.height / 2, 0xFF5555);
            super.render(g, mouseX, mouseY, partialTicks);
            return;
        }

        // Title strip — runestone-slab approximation: dark band with subtle gradient,
        // a thin amber rule above and below to suggest carved metal trim.
        g.fill(0, 0, this.width, 32, 0xC8000000);
        g.fill(0, 0, this.width, 1, 0xFFD9A03A);
        g.fill(0, 31, this.width, 32, 0xFFD9A03A);
        g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD),
                this.width / 2, 7, 0xFFD9A03A);

        // Counter — colour each tier-segment by its fullness so a glance shows free slots.
        int markCount  = cap.equippedMarks.size();
        int sealCount  = cap.equippedSeals.size();
        int crownCount = cap.equippedCrown.isEmpty() ? 0 : 1;
        Component counter = Component.literal("")
                .copy()
                .append(tierSegment("Marks", markCount, PowerTier.MARK.maxEquipped))
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(tierSegment("Seals", sealCount, PowerTier.SEAL.maxEquipped))
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(tierSegment("Crown", crownCount, PowerTier.CROWN.maxEquipped));
        g.drawCenteredString(this.font, counter, this.width / 2, 20, 0xCCCCCC);

        // Three columns with translucent dark backings. The column header text and amber
        // separator are drawn inside renderColumn so each column owns its own header chrome.
        int colWidth = (this.width - 4 * COL_PAD) / 3;
        int panelTop = TITLE_BAND_HEIGHT + 4;
        int panelBottom = this.height - LIST_BOTTOM_PAD;
        for (int i = 0; i < 3; i++) {
            int x = COL_PAD * (i + 1) + colWidth * i;
            g.fill(x, panelTop, x + colWidth, panelBottom, 0x66000000);
        }

        Power hoveredPower = renderColumn(g, cap, markPool, PowerTier.MARK,
                COL_PAD, colWidth, scroll[0], mouseX, mouseY);
        Power sealHover = renderColumn(g, cap, sealPool, PowerTier.SEAL,
                COL_PAD * 2 + colWidth, colWidth, scroll[1], mouseX, mouseY);
        if (sealHover != null) hoveredPower = sealHover;
        Power crownHover = renderColumn(g, cap, crownPool, PowerTier.CROWN,
                COL_PAD * 3 + 2 * colWidth, colWidth, scroll[2], mouseX, mouseY);
        if (crownHover != null) hoveredPower = crownHover;

        super.render(g, mouseX, mouseY, partialTicks);

        // Footer hint band
        g.fill(0, this.height - 14, this.width, this.height, 0xC8000000);
        g.fill(0, this.height - 14, this.width, this.height - 13, 0xFFD9A03A);
        Component hint = Component.literal("Esc to close · Scroll to navigate · Hover for details")
                .copy().withStyle(ChatFormatting.GRAY);
        g.drawCenteredString(this.font, hint, this.width / 2, this.height - 11, 0x999999);

        if (hoveredPower != null) {
            renderPowerTooltip(g, hoveredPower, mouseX, mouseY);
        }
    }

    /** Counter segment "Marks 3/5" coloured red (full), green (free slot), or dim (empty). */
    private static Component tierSegment(String label, int used, int cap) {
        ChatFormatting colour = (used >= cap) ? ChatFormatting.RED
                : (used > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
        return Component.literal(label + " " + used + "/" + cap).withStyle(colour);
    }

    /** Returns the Power being hovered in this column, or null. */
    private Power renderColumn(GuiGraphics g, SkillCapability cap, List<Power> pool,
                               PowerTier tier, int x, int colWidth, int scrollOffset,
                               int mouseX, int mouseY) {
        // Column header — inside the dark panel, with amber underline rule.
        int headerY = TITLE_BAND_HEIGHT + 6;
        g.drawCenteredString(this.font, Component.translatable(tier.getKey())
                .copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                x + colWidth / 2, headerY, 0xFFD9A03A);
        g.fill(x + 8, headerY + 11, x + colWidth - 8, headerY + 12, 0x80D9A03A);

        Power hovered = null;
        int rowsVisible = Math.max(1, (this.height - LIST_TOP_Y - LIST_BOTTOM_PAD) / LIST_ROW_HEIGHT);
        int start = Math.max(0, Math.min(scrollOffset, Math.max(0, pool.size() - rowsVisible)));
        for (int i = 0; i < rowsVisible && start + i < pool.size(); i++) {
            Power p = pool.get(start + i);
            int rowY = LIST_TOP_Y + i * LIST_ROW_HEIGHT;
            boolean equipped = cap.isPowerEquipped(p);
            boolean disabled = RegistryPowers.isDisabled(p);
            int nameColor = disabled ? 0x666666
                    : equipped ? 0x88FF88
                    : schoolColor(p.getSchoolId());

            // Equipped marker — a thin amber bar on the left edge of the row + checkmark.
            if (equipped) {
                g.fill(x, rowY + 1, x + 2, rowY + LIST_ROW_HEIGHT - 1, 0xFFD9A03A);
            }

            // Hover band — amber tint instead of plain white for chrome consistency.
            boolean rowHovered = mouseX >= x && mouseX < x + colWidth
                    && mouseY >= rowY && mouseY < rowY + LIST_ROW_HEIGHT;
            if (rowHovered) {
                g.fill(x + 2, rowY, x + colWidth, rowY + LIST_ROW_HEIGHT, 0x40D9A03A);
                hovered = p;
            }

            String prefix = equipped ? "✓ " : "  ";
            MutableComponent name = Component.translatable(p.getKey());
            g.drawString(this.font, prefix + name.getString(), x + 4, rowY + 6, nameColor);
        }

        // Scroll indicator at the bottom of the column.
        if (pool.size() > rowsVisible) {
            String marker = String.format("%d/%d", start + 1, pool.size());
            g.drawString(this.font, marker, x + 4, this.height - LIST_BOTTOM_PAD + 2, 0x888888);
        }
        return hovered;
    }

    private void renderPowerTooltip(GuiGraphics g, Power p, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(p.getKey())
                .copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        lines.add(Component.translatable(p.getTier().getKey())
                .copy().withStyle(ChatFormatting.GOLD));
        ResourceLocation school = p.getSchoolId();
        if (school != null) {
            String langKey = "school.runicskills." + school.getPath();
            // For ISS schools fall back to "fire", "ice", etc. by path; cross-cutting
            // categories already use that form natively.
            lines.add(Component.translatable(langKey).copy().withStyle(
                    ChatFormatting.AQUA));
        }
        lines.add(Component.literal(""));
        lines.add(Component.translatable(p.getDescriptionKey())
                .copy().withStyle(ChatFormatting.GRAY));
        if (p.requiredSkillLevel > 0 && p.getGoverningSkill() != null) {
            lines.add(Component.literal(""));
            lines.add(Component.literal("Requires " + p.getGoverningSkill().getName()
                    + " " + p.requiredSkillLevel).withStyle(ChatFormatting.YELLOW));
        }
        if (RegistryPowers.isDisabled(p)) {
            lines.add(Component.literal("[disabled in config]").withStyle(ChatFormatting.RED));
        }
        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    /** Cheap mapping of school ResourceLocation → tooltip color. */
    private static int schoolColor(ResourceLocation school) {
        if (school == null) return 0xCCCCCC;
        if (PowerSchool.FIRE.equals(school))      return 0xFF6633;
        if (PowerSchool.ICE.equals(school))       return 0x66CCFF;
        if (PowerSchool.LIGHTNING.equals(school)) return 0xFFFF66;
        if (PowerSchool.HOLY.equals(school))      return 0xFFEEAA;
        if (PowerSchool.ENDER.equals(school))     return 0xAA66FF;
        if (PowerSchool.BLOOD.equals(school))     return 0xCC2244;
        if (PowerSchool.EVOCATION.equals(school)) return 0x66FF66;
        if (PowerSchool.NATURE.equals(school))    return 0x99DD66;
        if (PowerSchool.ELDRITCH.equals(school))  return 0x99FFCC;
        return 0xCCCCCC;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        int colWidth = (this.width - 4 * COL_PAD) / 3;
        int colIdx;
        if (mouseX < COL_PAD * 2 + colWidth) colIdx = 0;
        else if (mouseX < COL_PAD * 3 + 2 * colWidth) colIdx = 1;
        else colIdx = 2;
        int dir = scrollDelta > 0 ? -1 : 1;
        scroll[colIdx] = Math.max(0, scroll[colIdx] + dir);
        rebuildButtons();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
