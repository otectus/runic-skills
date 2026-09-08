package com.otectus.runicskills.client.screen;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.client.gui.PowerSelectionSnapshot;
import com.otectus.runicskills.client.gui.PowerTuningText;
import com.otectus.runicskills.client.tooltip.TooltipWrap;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.common.PowerEquipSP;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerEligibility;
import com.otectus.runicskills.registry.powers.PowerOverrides;
import com.otectus.runicskills.registry.powers.PowerOverridesManager;
import com.otectus.runicskills.client.vfx.ProcPulse;
import com.otectus.runicskills.integration.tconstruct.TConstructPowers;
import com.otectus.runicskills.registry.powers.PowerSchool;
import com.otectus.runicskills.registry.powers.PowerTier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
import java.util.Map;

/**
 * Powers panel. Three tier columns (Marks / Seals / Crown), each listing every
 * registered Power with an Equip/Unequip button and a tooltip description on hover. Triggered
 * by the {@code key.runicskills.open_powers} keybind, which ships <b>unbound</b>: no default key
 * can be chosen safely without testing it against a real pack's control scheme, and a silent
 * clash is worse than an unassigned key. Players bind it under Options → Controls. This javadoc
 * previously claimed a default of {@code U}, which was never what
 * {@link com.otectus.runicskills.RunicSkillsClient} registered.
 *
 * <p>The main Skills overview also opens this panel through its icon-only Powers control.
 * Entries use native-resolution mechanic emblems, school colours and a local proc pulse.
 * A panel opened from Skills returns there on Escape; the no-argument keybind entry closes
 * directly to gameplay. Missing-addon slots can be reclaimed through the footer control.
 *
 * <p>State changes round-trip through {@link PowerEquipSP}; the server is authoritative on
 * skill-level / slot-cap / disabled gates. After a successful equip the server fires
 * {@code SyncSkillCapabilityCP} which refreshes the local {@link SkillCapability}, and we
 * rebuild the screen on the next render.
 */
@OnlyIn(Dist.CLIENT)
public class PowersScreen extends Screen {

    private static final int TITLE_BAND_HEIGHT = 32;
    private static final int FOOTER_BAND_HEIGHT = 24;
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
    private final Screen parent;
    private PowerSelectionSnapshot displayedSelection;
    private List<String> missingEquipped = List.of();

    public PowersScreen() {
        this(null);
    }

    public PowersScreen(Screen parent) {
        super(Component.translatable("screen.runicskills.powers.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        rebuildPools();
        rebuildButtons();
    }

    private void rebuildPools() {
        markPool  = sortByName(filterHidden(RegistryPowers.getByTier(PowerTier.MARK)));
        sealPool  = sortByName(filterHidden(RegistryPowers.getByTier(PowerTier.SEAL)));
        crownPool = sortByName(filterHidden(RegistryPowers.getByTier(PowerTier.CROWN)));
    }

    // Hide disabled unequipped powers. Equipped entries remain removable regardless of the hide
    // setting. Buttons and rendering share these pools, keeping scroll/index math consistent.
    private static List<Power> filterHidden(List<Power> in) {
        List<Power> out = new ArrayList<>(in);
        SkillCapability cap = SkillCapability.getLocal();
        out.removeIf(power -> RegistryPowers.isHiddenFromUi(power)
                && (cap == null || !cap.isPowerEquipped(power)));
        return out;
    }

    private static List<Power> sortByName(List<Power> in) {
        List<Power> out = new ArrayList<>(in);
        out.sort(Comparator.comparing(power -> Component.translatable(power.getKey()).getString(),
                String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private void rebuildButtons() {
        clearWidgets();
        SkillCapability cap = SkillCapability.getLocal();
        if (cap == null) {
            this.displayedSelection = null;
            return;
        }
        this.displayedSelection = new PowerSelectionSnapshot(cap.equippedMarks, cap.equippedSeals, cap.equippedCrown,
                HandlerCommonConfig.HANDLER.instance().disabledPowers, HandlerCommonConfig.HANDLER.instance().hideDisabledPowers);
        this.missingEquipped = this.displayedSelection.missingIds(id -> RegistryPowers.getPower(id) != null);
        int colWidth = (this.width - 4 * COL_PAD) / 3;
        addColumnButtons(cap, markPool,  PowerTier.MARK,  COL_PAD, colWidth, scroll[0]);
        addColumnButtons(cap, sealPool,  PowerTier.SEAL,  COL_PAD * 2 + colWidth, colWidth, scroll[1]);
        addColumnButtons(cap, crownPool, PowerTier.CROWN, COL_PAD * 3 + 2 * colWidth, colWidth, scroll[2]);
        if (!this.missingEquipped.isEmpty()) {
            String missingId = this.missingEquipped.get(0);
            this.addRenderableWidget(Button.builder(Component.translatable(
                            "screen.runicskills.powers.remove_missing", this.missingEquipped.size()),
                            button -> ServerNetworking.sendToServer(PowerEquipSP.unequipUnknown(missingId)))
                    .bounds(COL_PAD, this.height - FOOTER_BAND_HEIGHT + 4, 112, BTN_H)
                    .tooltip(Tooltip.create(Component.translatable("screen.runicskills.powers.remove_missing.tooltip", missingId)))
                    .build());
        }
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
            Button btn = Button.builder(label, b -> {
                        SkillCapability current = SkillCapability.getLocal();
                        if (current != null) sendEquip(p, !current.isPowerEquipped(p));
                    })
                    .bounds(x + colWidth - BTN_W - 2, rowY + 2, BTN_W, BTN_H)
                    .build();
            btn.active = !disabled || equipped; // can always unequip even a disabled Power
            this.addRenderableWidget(btn);
        }
    }

    private void sendEquip(Power power, boolean equip) {
        ServerNetworking.sendToServer(new PowerEquipSP(power, equip));
        // Refresh only after authoritative state changes, not on a queued task that can execute
        // before the network round-trip and leave a permanently stale Equip button.
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        renderBackground(g);

        SkillCapability cap = SkillCapability.getLocal();
        if (cap == null) {
            if (this.displayedSelection != null) {
                clearWidgets();
                this.displayedSelection = null;
            }
            g.drawCenteredString(this.font,
                    Component.translatable("screen.runicskills.powers.no_capability"),
                    this.width / 2, this.height / 2, 0xFF5555);
            super.render(g, mouseX, mouseY, partialTicks);
            return;
        }

        if (this.displayedSelection == null || !this.displayedSelection.matches(
                cap.equippedMarks, cap.equippedSeals, cap.equippedCrown,
                HandlerCommonConfig.HANDLER.instance().disabledPowers, HandlerCommonConfig.HANDLER.instance().hideDisabledPowers)) {
            // Keep keyboard focus in the same visible row after an equip/unequip acknowledgement.
            int focusIndex = children().indexOf(getFocused());
            rebuildPools();
            rebuildButtons();
            if (focusIndex >= 0 && focusIndex < children().size()) setFocused(children().get(focusIndex));
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
        Component counter = Component.empty()
                .append(tierSegment(PowerTier.MARK, markCount))
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(tierSegment(PowerTier.SEAL, sealCount))
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(tierSegment(PowerTier.CROWN, crownCount));
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

        // Footer hint band
        g.fill(0, this.height - FOOTER_BAND_HEIGHT, this.width, this.height, 0xC8000000);
        g.fill(0, this.height - FOOTER_BAND_HEIGHT, this.width, this.height - FOOTER_BAND_HEIGHT + 1, 0xFFD9A03A);
        Component hint = Component.translatable("screen.runicskills.powers.hint")
                .withStyle(ChatFormatting.GRAY);
        int hintStart = this.missingEquipped.isEmpty() ? COL_PAD : COL_PAD + 120;
        g.drawString(this.font, this.font.plainSubstrByWidth(hint.getString(), Math.max(0, this.width - hintStart - COL_PAD)),
                hintStart, this.height - 15, 0x999999);

        super.render(g, mouseX, mouseY, partialTicks);

        if (hoveredPower != null) {
            renderPowerTooltip(g, hoveredPower, mouseX, mouseY);
        }
    }

    /**
     * Counter segment "Marks 3/5", coloured red (full), green (free slot) or dim (empty).
     *
     * <p>The tier name was a hardcoded English string concatenated into a literal. It is the
     * tier's own translation key now, so this reads correctly in every locale and picks up any
     * rename of the tier for free.
     */
    private static Component tierSegment(PowerTier tier, int used) {
        int cap = tier.maxEquipped;
        ChatFormatting colour = (used >= cap) ? ChatFormatting.RED
                : (used > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
        return Component.translatable("screen.runicskills.powers.slot_counter",
                Component.translatable(tier.getKey()), used, cap).withStyle(colour);
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

            // Its own icon, at last. Every Power used to share HandlerResources.NULL_PERK, so the
            // panel was seventy-five identical squares and the name was the only thing telling
            // them apart.
            int iconX = x + 4;
            int iconY = rowY + 3;
            g.blit(p.texture, iconX, iconY, 0.0F, 0.0F, 16, 16, 16, 16);
            // A ring pulse on the icon when this Power has just fired, from the same descriptor
            // the world VFX used, so the row and the effect are visibly the same event.
            float pulse = ProcPulse.strength(p.getName());
            if (pulse > 0.0F) {
                int alpha = (int) (0xC0 * pulse) << 24;
                g.fill(iconX - 1, iconY - 1, iconX + 17, iconY, alpha | 0xD9A03A);
                g.fill(iconX - 1, iconY + 16, iconX + 17, iconY + 17, alpha | 0xD9A03A);
                g.fill(iconX - 1, iconY, iconX, iconY + 16, alpha | 0xD9A03A);
                g.fill(iconX + 16, iconY, iconX + 17, iconY + 16, alpha | 0xD9A03A);
            }

            // Composed rather than concatenated: drawString(prefix + name.getString() + suffix)
            // flattened the translated name to plain text, discarding any style or nested
            // translation another mod or a resource pack had put in it (RS-085).
            MutableComponent label = Component.empty();
            if (equipped) {
                label.append(Component.literal("✓ ").withStyle(ChatFormatting.GOLD));
            }
            label.append(Component.translatable(p.getKey()));
            // A Power that does not fully deliver its description is marked in the list itself,
            // not only in the tooltip a player might never open (RS10-004). The tooltip says which
            // kind of shortfall it is; the row only has to say that there is one.
            if (com.otectus.runicskills.registry.content.ContentStatusIndex.effective(p)
                    .needsUiLabel()) {
                label.append(Component.literal(" *").withStyle(ChatFormatting.YELLOW));
            }
            // Reserve the equip button, including at the minimum GUI width. Scissoring preserves
            // component styles and native glyph pixels; the tooltip always carries the full name.
            g.enableScissor(iconX + 20, rowY, Math.max(iconX + 20, x + colWidth - BTN_W - 4), rowY + LIST_ROW_HEIGHT);
            g.drawString(this.font, label, iconX + 20, rowY + 6, nameColor);
            g.disableScissor();
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
        PowerOverrides overrides = PowerOverridesManager.forPower(p);
        boolean tuned = overrides != null && (!overrides.values().isEmpty() || overrides.hasIcdTicks());
        if (tuned && !PowerSchool.TINKERING.equals(school) && !PowerSchool.ANGLING.equals(school)) {
            lines.add(Component.translatable("screen.runicskills.powers.tuning.defaults")
                    .withStyle(ChatFormatting.YELLOW));
        }
        // Through TConstructPowers rather than translatable(key) directly: an Artifice description
        // is formatted from the very numbers its dispatcher executes, override included, so a pack
        // that halves a magnitude in JSON cannot leave the tooltip advertising the old one (13.1).
        // Every other Power takes the same plain translation it always did.
        lines.add((com.otectus.runicskills.integration.tide.TidePowers.owns(p)
                ? com.otectus.runicskills.integration.tide.TidePowers.description(p)
                : TConstructPowers.description(p)).copy().withStyle(ChatFormatting.GRAY));
        if (tuned) {
            lines.add(Component.translatable("screen.runicskills.powers.tuning.title")
                    .withStyle(ChatFormatting.GOLD));
            if (overrides.hasIcdTicks()) {
                addTuningLine(lines, "cooldown_ticks", overrides.icdTicks());
            }
            int shown = 0;
            int limit = Math.min(6, Math.max(1, (this.height - 140) / 12));
            for (Map.Entry<String, Double> entry : overrides.values().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).limit(limit).toList()) {
                addTuningLine(lines, entry.getKey(), entry.getValue());
                shown++;
            }
            if (overrides.values().size() > shown) {
                lines.add(Component.translatable("screen.runicskills.powers.tuning.more", overrides.values().size() - shown)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        int requiredLevel = PowerOverridesManager.requiredSkillLevelOr(p, p.requiredSkillLevel);
        if (requiredLevel > 0 && p.getGoverningSkill() != null) {
            lines.add(Component.literal(""));
            // Was `"Requires " + skill.getName() + " " + level` -- English, and built from the
            // registry path rather than the skill's own translated name.
            lines.add(Component.translatable("screen.runicskills.powers.requires",
                            Component.translatable(p.getGoverningSkill().getKey()),
                            requiredLevel)
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (RegistryPowers.isDisabled(p)) {
            lines.add(Component.translatable("screen.runicskills.powers.disabled_in_config")
                    .withStyle(ChatFormatting.RED));
        }

        // Why this Power cannot be equipped right now. The server has produced a structured denial
        // reason since 2.0.0; until now the screen simply declined to light the row up, which told
        // the player that something was wrong but never which of six things it was.
        SkillCapability local = SkillCapability.getLocal();
        if (Minecraft.getInstance().player != null && local != null && !local.isPowerEquipped(p)) {
            PowerEligibility.Result verdict =
                    PowerEligibility.evaluateEquip(Minecraft.getInstance().player, p);
            if (!verdict.eligible()) {
                lines.add(Component.literal(""));
                lines.add(verdict.describe(p).copy().withStyle(ChatFormatting.RED));
            }
        }
        // Say plainly when a Power is not the finished article. A player choosing between five
        // Marks is spending a scarce slot, and "this one is an approximation of its description"
        // is exactly the information that choice needs.
        com.otectus.runicskills.registry.content.ContentStatus status =
                com.otectus.runicskills.registry.content.ContentStatusIndex.effective(p);
        if (status.needsUiLabel()) {
            lines.add(Component.literal(""));
            lines.add(Component.translatable(status.labelKey())
                    .copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            String explanation = status.labelKey() + ".tooltip";
            // Only three of the four states carry an explanation; a missing dependency explains
            // itself, and inventing a line for it would render the raw key.
            if (status != com.otectus.runicskills.registry.content.ContentStatus.UNAVAILABLE_DEPENDENCY) {
                lines.add(Component.translatable(explanation).copy().withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        g.renderComponentTooltip(this.font, TooltipWrap.wrap(lines, Math.max(80, Math.min(280, this.width - 20))), mouseX, mouseY);
    }

    private static void addTuningLine(List<Component> lines, String key, double value) {
        PowerTuningText.Line text = PowerTuningText.format(key, value);
        lines.add(Component.translatable("screen.runicskills.powers.tuning.entry", text.label(),
                        Component.translatable(text.unitKey(), text.number()))
                .withStyle(ChatFormatting.AQUA));
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
        if (PowerSchool.TINKERING.equals(school)) return 0xD98A3F;
        if (PowerSchool.ANGLING.equals(school)) return 0x48C9C5;
        return 0xCCCCCC;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        int colWidth = (this.width - 4 * COL_PAD) / 3;
        int colIdx;
        if (mouseX < COL_PAD * 2 + colWidth) colIdx = 0;
        else if (mouseX < COL_PAD * 3 + 2 * colWidth) colIdx = 1;
        else colIdx = 2;
        // Clamped at BOTH ends. The upper bound was missing, so scrolling past the last row kept
        // incrementing an offset the render pass then clamped for display -- the list stopped
        // moving while the wheel kept counting, and it took exactly as many clicks back to
        // start moving again (RS-166).
        int dir = scrollDelta > 0 ? -1 : 1;
        List<Power> pool = switch (colIdx) {
            case 0 -> markPool;
            case 1 -> sealPool;
            default -> crownPool;
        };
        int rowsVisible = Math.max(1, (this.height - LIST_TOP_Y - LIST_BOTTOM_PAD) / LIST_ROW_HEIGHT);
        int maxScroll = Math.max(0, pool.size() - rowsVisible);
        int next = Math.max(0, Math.min(maxScroll, scroll[colIdx] + dir));
        if (next == scroll[colIdx]) return false;
        scroll[colIdx] = next;
        rebuildButtons();
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
