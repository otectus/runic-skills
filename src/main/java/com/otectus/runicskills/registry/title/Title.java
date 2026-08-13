package com.otectus.runicskills.registry.title;

import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.event.TitleEarnedEvent;
import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.network.packet.client.TitleOverlayCP;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;

public class Title {
    private final ResourceLocation key;
    public final boolean Requirement;
    public final boolean HideRequirements;

    /**
     * Whether losing this title's condition takes the title back.
     *
     * <p>Almost every title is an achievement — "killed 100 zombies" cannot become untrue, and
     * unlocks are deliberately monotonic so a config change or a stat reset never strips a
     * player's earned record. Permission-derived titles are the exception: {@code administrator}
     * is granted from {@code hasPermissions(2)} and there was no branch that ever took it away,
     * so a de-opped player kept it permanently — and because {@code SetPlayerTitleSP} authorises
     * off that persisted flag and {@code displayTitlesAsPrefix} defaults to true, they could keep
     * wearing an "Administrator" prefix in chat on a public server (RS-056).
     */
    public final boolean Revocable;

    public Title(ResourceLocation key, boolean requirement, boolean hideRequirements) {
        this(key, requirement, hideRequirements, false);
    }

    public Title(ResourceLocation key, boolean requirement, boolean hideRequirements, boolean revocable) {
        this.key = key;
        this.Requirement = requirement;
        this.HideRequirements = hideRequirements;
        this.Revocable = revocable;
    }

    public Title get() {
        return this;
    }


    public String getMod() {
        return this.key.getNamespace();
    }

    public String getName() {
        return this.key.getPath();
    }

    public String getKey() {
        return "title." + this.key.toLanguageKey();
    }

    public String getDescription() {
        return getKey() + ".description";
    }

    public boolean getRequirement() {
        return SkillCapability.getLocal().getLockTitle(this);
    }

    public boolean getRequirement(Player player) {
        // The capability is nullable — it is absent for a FakePlayer by design, and briefly during
        // teardown — and this was dereferenced unguarded from the title scan (RS-055).
        SkillCapability capability = SkillCapability.get(player);
        return capability != null && capability.getLockTitle(this);
    }

    public void setRequirement(ServerPlayer serverPlayer, boolean check) {
        SkillCapability capability = SkillCapability.get(serverPlayer);
        if (capability == null) return;
        boolean held = capability.getLockTitle(this);

        if (!held && check) {
            TitleOverlayCP.send(serverPlayer, this);
            capability.setUnlockTitle(this, true);
            // Fire public Forge event (since 1.2.0). Non-cancelable — unlock is already
            // committed to the capability by the time subscribers see this.
            MinecraftForge.EVENT_BUS.post(new TitleEarnedEvent(serverPlayer, this));
            // Quest bridge (since 1.3.0). FTB Quests title_unlocked tasks re-evaluate here.
            com.otectus.runicskills.integration.quests.RunicQuestBridge.onTitleUnlockedChanged(serverPlayer, this, true);
            SyncSkillCapabilityCP.send(serverPlayer);
            return;
        }

        // Re-lock branch, for revocable titles only. Achievement titles stay monotonic (RS-056).
        if (held && !check && this.Revocable) {
            capability.setUnlockTitle(this, false);
            // Stop wearing a title that has just been taken away, or the display would keep
            // showing it until the player picked something else.
            if (getName().equals(capability.playerTitle)) {
                capability.playerTitle = com.otectus.runicskills.registry.RegistryTitles
                        .getTitle("titleless").getName();
            }
            com.otectus.runicskills.integration.quests.RunicQuestBridge.onTitleUnlockedChanged(serverPlayer, this, false);
            SyncSkillCapabilityCP.send(serverPlayer);
        }
    }

    public List<Component> tooltip() {
        List<Component> list = new ArrayList<>();
        list.add(Component.empty().append(Component.translatable("title.runicskills.requirement_description").withStyle(ChatFormatting.GOLD)).append(Component.translatable(getDescription()).withStyle(ChatFormatting.GRAY)));
        if (HandlerConfigClient.showTitleModName.get())
            list.add(Component.literal(Utils.getModName(getMod())).withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.ITALIC));
        return list;
    }
}


