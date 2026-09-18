package com.otectus.runicskills.client.tooltip;

import com.otectus.runicskills.integration.lock.LockAction;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.common.InspectStackSP;
import com.otectus.runicskills.network.packet.client.StackRequirementsCP;
import com.otectus.runicskills.registry.RegistrySkills;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;
import java.util.*;

/** Hovering a visible slot asks the server; bounded, expiring copies cannot outlive material swaps. */
public final class StackRequirementTooltip {
    private static ItemStack requested = ItemStack.EMPTY;
    private static int menuId = -1, slotId = -1;
    private static long nonce, requestedTick = -100;
    private static StackRequirementsCP response;
    private static int configVersion = -1;
    private StackRequirementTooltip() {}
    public static void clear() { requested = ItemStack.EMPTY; response = null; menuId = -1; slotId = -1; requestedTick = -100; }
    public static void accept(StackRequirementsCP reply) {
        var player = Minecraft.getInstance().player;
        if (player != null && reply.request() == nonce && reply.menu() == menuId && reply.slot() == slotId
                && player.containerMenu.containerId == menuId && slotId >= 0 && slotId < player.containerMenu.slots.size()
                && player.containerMenu.getStateId() == reply.state()
                && reply.stackHash() == StackRequirementsCP.fingerprint(requested)
                && ItemStack.matches(player.containerMenu.getSlot(slotId).getItem(), requested)) response = reply;
    }
    public static boolean append(ItemStack stack, List<Component> lines) {
        int revision = com.otectus.runicskills.config.snapshot.GameplayConfigSnapshot.version();
        if (revision != configVersion) { clear(); configVersion = revision; }
        var player = Minecraft.getInstance().player;
        if (player == null || stack.isEmpty() || !stack.is(net.minecraft.tags.ItemTags.create(
                new net.minecraft.resources.ResourceLocation("tconstruct:modifiable")))) return false;
        var menu = player.containerMenu;
        int slot = -1;
        for (int i = 0; i < menu.slots.size(); i++) if (ItemStack.matches(menu.getSlot(i).getItem(), stack)) { slot = i; break; }
        if (slot < 0) return false;
        boolean same = menuId == menu.containerId && slotId == slot && ItemStack.matches(stack, requested);
        if (!same) response = null;
        if ((!same || player.tickCount - requestedTick >= 20) && player.tickCount - requestedTick >= 5) {
            requested = stack.copy(); menuId = menu.containerId; slotId = slot; requestedTick = player.tickCount;
            ServerNetworking.sendToServer(new InspectStackSP(menuId, slotId, ++nonce));
        }
        if (!same || response == null || response.views().isEmpty()) return false;
        Set<String> sources = new LinkedHashSet<>();
        for (LockAction action : LockAction.values()) {
            if (!action.appliesToStack()) continue;
            var view = response.views().get(action);
            if (view == null || (view.requirements().isEmpty() && view.uncertainty().isEmpty())) continue;
            var text = Component.translatable("tooltip.skill.stack_action." + action.name().toLowerCase(Locale.ROOT)).append(": ");
            boolean first = true;
            for (var entry : new TreeMap<>(view.requirements()).entrySet()) {
                if (!first) text.append(", "); first = false;
                var skill = RegistrySkills.getSkill(entry.getKey());
                text.append(skill == null ? Component.literal(entry.getKey()) : Component.translatable(skill.getKey()));
                text.append(" " + entry.getValue());
            }
            lines.add(text.withStyle(view.allowed() ? ChatFormatting.GREEN : ChatFormatting.RED));
            sources.addAll(view.rules());
            if (!view.uncertainty().isEmpty()) lines.add(Component.translatable("tooltip.skill.stack_undetermined").withStyle(ChatFormatting.GRAY));
        }
        sources.stream().limit(3).forEach(source -> lines.add(Component.translatable("tooltip.skill.stack_source",
                source.length() > 160 ? source.substring(0,157)+"..." : source).withStyle(ChatFormatting.DARK_GRAY)));
        return true;
    }
}
