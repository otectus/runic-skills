package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.network.packet.client.GuardStateCP;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Map;

/** Remaining owned Guard and expiry for self, ridden mount and the looked-at recipient. */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, value = Dist.CLIENT)
public final class OverlayGuardGui implements IGuiOverlay {
    public static final OverlayGuardGui INSTANCE = new OverlayGuardGui();
    private static final Map<Integer, GuardStateCP> STATES = new HashMap<>();
    private static Object level;
    public static void accept(GuardStateCP state) {
        checkLevel();
        if (state.points() == 0) STATES.remove(state.entityId());
        else if (STATES.size() < 4096 || STATES.containsKey(state.entityId())) STATES.put(state.entityId(), state);
    }
    private static void checkLevel() {
        Object current = Minecraft.getInstance().level;
        if (current != level) { STATES.clear(); level = current; }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        checkLevel();
        STATES.entrySet().removeIf(entry -> entry.getValue().ticks() <= 1);
        STATES.replaceAll((id, state) -> new GuardStateCP(id, state.uuid(), state.points(), state.ticks() - 1));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { STATES.clear(); level = null; }
    @Override public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        int y = height / 2 + 18;
        y = draw(graphics, minecraft.player, width, y);
        Entity mount = minecraft.player.getVehicle();
        if (mount != null) y = draw(graphics, mount, width, y);
        if (minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() != mount
                && hit.getEntity() != minecraft.player) draw(graphics, hit.getEntity(), width, y);
    }
    private static int draw(GuiGraphics graphics, Entity entity, int width, int y) {
        GuardStateCP state = STATES.get(entity.getId());
        if (state == null || !state.uuid().equals(entity.getUUID())) return y;
        String points = java.math.BigDecimal.valueOf(state.points()).setScale(1, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
        var text = Component.translatable("overlay.runicskills.guard", entity.getDisplayName(), points, (state.ticks() + 19) / 20);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, text, (width - font.width(text)) / 2, y, 0x7FE0DA, true);
        return y + 11;
    }
}
