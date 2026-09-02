package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.client.vfx.PowerProcDescriptor;
import com.otectus.runicskills.registry.powers.PowerTier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The proc HUD: up to three small cards above the hotbar naming what just fired.
 *
 * <h2>Why the card is the primary channel</h2>
 * It is the only feedback that survives every setting and every situation. Particles are off at
 * {@code OFF} quality, sound is off for a muted or deaf player, and both are useless when the effect
 * happened behind the player. The card still answers "did a Power trigger, and which one" — which is
 * the first two of the five questions the whole system exists to answer inside half a second.
 *
 * <h2>Bounded and coalescing</h2>
 * At most {@value #MAX_CARDS}. An identical Power arriving while its card is still up increments a
 * counter on that card rather than adding another, so an every-hit amplifier reads as
 * "Kindle x7" instead of scrolling seven cards past. A fourth distinct Power evicts the oldest
 * rather than growing the stack.
 */
@OnlyIn(Dist.CLIENT)
public class OverlayPowerProcGui implements IGuiOverlay {

    public static final OverlayPowerProcGui INSTANCE = new OverlayPowerProcGui();

    /** Simultaneous cards. Three is enough to read at a glance and few enough to read at all. */
    public static final int MAX_CARDS = 3;

    /** Per-tier card lifetimes, matching the tier's own escalation: 250 / 400 / 600 ms. */
    private static final int MARK_TICKS = 5;
    private static final int SEAL_TICKS = 8;
    private static final int CROWN_TICKS = 12;

    private static final int CARD_HEIGHT = 12;
    private static final int CARD_PADDING = 4;

    private static final class Card {
        final String powerId;
        final Component name;
        final int accent;
        final int lifetime;
        int ticksLeft;
        int count = 1;
        boolean critical;

        Card(String powerId, Component name, int accent, int lifetime, boolean critical) {
            this.powerId = powerId;
            this.name = name;
            this.accent = accent;
            this.lifetime = lifetime;
            this.ticksLeft = lifetime;
            this.critical = critical;
        }
    }

    private static final List<Card> CARDS = new ArrayList<>(MAX_CARDS);

    private final Minecraft client = Minecraft.getInstance();

    /** Queues a card, or folds the proc into the one already showing for that Power. */
    public static synchronized void push(PowerProcDescriptor descriptor, boolean critical) {
        if (descriptor == null || descriptor.power() == null) return;
        String id = descriptor.power().getName();
        for (Card card : CARDS) {
            if (card.powerId.equals(id)) {
                card.count++;
                card.critical |= critical;
                card.ticksLeft = card.lifetime;
                return;
            }
        }
        if (CARDS.size() >= MAX_CARDS) CARDS.remove(0);
        CARDS.add(new Card(id, descriptor.displayName(), descriptor.primaryColor(),
                lifetimeFor(descriptor.power().getTier()), critical));
    }

    private static int lifetimeFor(PowerTier tier) {
        if (tier == null) return MARK_TICKS;
        return switch (tier) {
            case MARK -> MARK_TICKS;
            case SEAL -> SEAL_TICKS;
            case CROWN -> CROWN_TICKS;
        };
    }

    public static synchronized void clear() {
        CARDS.clear();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // END only. ClientTickEvent fires twice per tick, and two of this mod's other overlays
        // spent their whole existence counting down at double speed for want of this line.
        if (event.phase != TickEvent.Phase.END) return;
        synchronized (OverlayPowerProcGui.class) {
            CARDS.removeIf(card -> --card.ticksLeft <= 0);
        }
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics matrixStack, float partialTick,
                       int screenWidth, int screenHeight) {
        if (this.client.level == null || this.client.player == null) return;
        List<Card> snapshot;
        synchronized (OverlayPowerProcGui.class) {
            if (CARDS.isEmpty()) return;
            snapshot = List.copyOf(CARDS);
        }

        // Sits above the hotbar and its status bars rather than at a fixed pixel offset, so it
        // stays clear of them at every GUI scale and in both the armoured and unarmoured layouts.
        int bottom = screenHeight - 60;
        int centerX = screenWidth / 2;

        matrixStack.pose().pushPose();
        try {
            for (int i = 0; i < snapshot.size(); i++) {
                Card card = snapshot.get(i);
                int y = bottom - (snapshot.size() - 1 - i) * (CARD_HEIGHT + 2);
                renderCard(matrixStack, card, centerX, y, screenWidth);
            }
        } finally {
            Utils.resetRenderState();
            matrixStack.pose().popPose();
        }
    }

    private void renderCard(GuiGraphics matrixStack, Card card, int centerX, int y, int screenWidth) {
        MutableComponent label = card.name.copy();
        if (card.count > 1) {
            label = label.append(Component.literal(" ")).append(
                    Component.translatable("screen.runicskills.powers.proc_count", card.count)
                            .withStyle(ChatFormatting.GRAY));
        }

        int textWidth = this.client.font.width(label);
        // Clamp to the safe area: a long translated Power name at GUI scale 1 on a narrow window
        // would otherwise run off both edges.
        int maxWidth = Math.max(40, screenWidth - 40);
        int width = Math.min(textWidth, maxWidth) + CARD_PADDING * 2;
        int left = centerX - width / 2;

        // Fade the last quarter of the life so cards leave rather than vanish.
        float alpha = card.ticksLeft > card.lifetime / 4.0F
                ? 1.0F
                : Math.max(0.0F, card.ticksLeft / (card.lifetime / 4.0F));
        int backdropAlpha = (int) (0xB0 * alpha) << 24;
        matrixStack.fill(left, y, left + width, y + CARD_HEIGHT, backdropAlpha);

        // A two-pixel school-coloured rule on the leading edge. Colour is reinforcement here, never
        // the only carrier: the name is spelled out beside it.
        int accentAlpha = (int) (0xFF * alpha) << 24;
        matrixStack.fill(left, y, left + 2, y + CARD_HEIGHT, accentAlpha | (card.accent & 0xFFFFFF));
        if (card.critical) {
            matrixStack.fill(left + width - 2, y, left + width, y + CARD_HEIGHT,
                    accentAlpha | 0xFFFFFF);
        }

        int textColor = (int) (0xFF * alpha) << 24 | 0xE8E8E8;
        matrixStack.drawString(this.client.font, label,
                left + CARD_PADDING, y + 2, textColor, true);
    }
}
