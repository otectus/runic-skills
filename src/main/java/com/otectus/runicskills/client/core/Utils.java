package com.otectus.runicskills.client.core;

import com.otectus.runicskills.common.util.ExperienceMath;
import com.otectus.runicskills.registry.RegistrySounds;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;


public class Utils {
    public static final int FONT_COLOR = Color.BLACK.getRGB();
    public static final int SKILL_ABBR_COLOR = new Color(240, 240, 240).getRGB();
    public static final int SKILL_LEVEL_COLOR = Color.WHITE.getRGB();
    public static final int TITLE_SELECTED_COLOR = 0x55FF55;
    public static final int TITLE_UNSELECTED_COLOR = 0xFFAA00;
    public static final Minecraft client = Minecraft.getInstance();

    public static ItemStack playerHead() {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        if (client.player != null) {
            if (client.player.getGameProfile().getProperties().isEmpty()) {
                return head;
            }
            CompoundTag nbt = head.getOrCreateTag();
            GameProfile gameProfile = client.player.getGameProfile();
            SkullBlockEntity.updateGameprofile(gameProfile, profile -> nbt.put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), profile)));
            head.setTag(nbt);
        }
        return head;
    }

    public static void playSound() {
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public static void drawToolTip(GuiGraphics matrixStack, Component tooltip, int mouseX, int mouseY) {
        matrixStack.renderTooltip(client.font, tooltip, mouseX, mouseY);
    }

    public static void drawToolTipList(GuiGraphics matrixStack, List<Component> tooltip, int mouseX, int mouseY) {
        matrixStack.renderTooltip(client.font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    /**
     * Half-open hit test: {@code [x, x + width)} by {@code [y, y + height)}.
     *
     * <p>Both edges used to be inclusive, so a region and the one starting immediately after it
     * both claimed the same boundary pixel. With tabs laid out at a 27 px pitch that gave every
     * adjacent pair a shared column, and whichever came first in the iteration silently won
     * (RS-170).
     */
    public static boolean checkMouse(int x, int y, int mouseX, int mouseY, int width, int height) {
        return (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height);
    }

    /**
     * Restores the global render state this mod's GUI code borrows.
     *
     * <p>Twelve {@code RenderSystem.enableBlend()} calls existed against two
     * {@code disableBlend()}, so whatever Minecraft (or another mod) drew after one of our
     * overlays inherited blending and a possibly non-white shader colour. Every borrow site now
     * calls this from a {@code finally}, which also means new draw code inherits the discipline
     * instead of having to remember two calls (RS-163).
     */
    public static void resetRenderState() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    public static String numberFormat(int number) {
        return String.format("%02d", number);
    }

    public static void drawCenter(GuiGraphics matrixStack, Component string, int x, int y) {
        matrixStack.drawString(client.font, string, x - client.font.width(string) / 2, y, FONT_COLOR, false);
    }

    public static void drawCenterWithShadow(GuiGraphics matrixStack, String string, int x, int y, int color) {
        matrixStack.drawString(client.font, string, x - client.font.width(string) / 2, y, color, true);
    }

    public static void drawCenterWithShadow(GuiGraphics matrixStack, Component string, int x, int y, int color) {
        matrixStack.drawString(client.font, string, x - client.font.width(string) / 2, y, color, true);
    }

    /**
     * Roman numeral for {@code number}, falling back to decimal outside the representable range.
     *
     * <p>The lookup tables only cover 0–3999, and the method indexed them directly, so a value
     * outside that range threw {@link ArrayIndexOutOfBoundsException} — from a tooltip or render
     * path, where an exception is a crash rather than a bad label. Perk ranks and boosts are
     * config-driven, so reaching 4000 or a negative value takes only an unusual config (RS-169).
     */
    public static String intToRoman(int number) {
        if (number < 1 || number > 3999) return String.valueOf(number);
        String[] thousands = {"", "M", "MM", "MMM"};
        String[] hundreds = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] units = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return thousands[number / 1000] + hundreds[number % 1000 / 100] + tens[number % 100 / 10] + units[number % 10];
    }

    public static String periodValue(double value) {
        DecimalFormat df = new DecimalFormat("#.#");
        String number = String.valueOf(value);
        if (number.contains(".")) {
            String decimal = number.split("\\.")[1];
            int zero = 1;
            for (int i = 0; i < decimal.length(); i++) {
                if (decimal.charAt(i) == '0') {
                    zero++;
                }
            }

            String format = "#." + "#".repeat(zero);
            return (new DecimalFormat(format)).format(value);
        }
        return df.format(value);
    }

    public static String getModName(String modId) {
        AtomicReference<String> modName = new AtomicReference<>("Misspelled Mod");
        if (modId != null) {
            ModList.get().getModContainerById(modId).ifPresent(modContainer -> modName.set(modContainer.getModInfo().getDisplayName()));
        }
        return modName.get();
    }

    @OnlyIn(Dist.CLIENT)
    public static void getTitleSound() {
        client.getSoundManager().play(SimpleSoundInstance.forUI(RegistrySounds.GAIN_TITLE.get(), 1.0F));
    }

    /** The player's current spendable XP-point balance. Delegates to the shared {@link ExperienceMath}. */
    public static int getPlayerXP(Player player) {
        return ExperienceMath.spendableXp(player.experienceLevel, player.experienceProgress);
    }


}


