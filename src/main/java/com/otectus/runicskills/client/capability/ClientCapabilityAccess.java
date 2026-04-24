package com.otectus.runicskills.client.capability;

import com.otectus.runicskills.client.gui.OverlaySkillGui;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.model.Skills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.registry.RegistryCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Client-side bridge for obtaining the local player's {@link SkillCapability}.
 * Call {@link #register()} during FMLClientSetupEvent.
 */
public final class ClientCapabilityAccess {

    private ClientCapabilityAccess() {}

    public static void register() {
        SkillCapability.LOCAL_SUPPLIER = ClientCapabilityAccess::localCapability;
    }

    @Nullable
    public static SkillCapability localCapability() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        return player.getCapability(RegistryCapabilities.SKILL).orElse(null);
    }

    /**
     * Client-side item-use check for gun-mod integrations and PointBlank.
     * Shows the skill-lock overlay when the player lacks the required level.
     */
    public static boolean canUseItemClient(ItemStack item) {
        if (!HandlerCommonConfig.HANDLER.instance().enableItemLocks) return true;
        SkillCapability cap = localCapability();
        if (cap == null) return true;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item.getItem());
        if (key == null) return true;
        List<Skills> skills = HandlerSkill.getValue(key.toString());
        if (skills == null) return true;
        for (Skills s : skills) {
            if (cap.getSkillLevel(s.getSkill()) < s.getSkillLvl()) {
                OverlaySkillGui.showWarning(key.toString());
                return false;
            }
        }
        return true;
    }
}
