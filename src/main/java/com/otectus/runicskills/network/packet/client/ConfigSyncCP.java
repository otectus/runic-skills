package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.config.models.ESkill;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerLockItemsConfig;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.network.ServerNetworking;
import io.netty.handler.codec.DecoderException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sent server → client on player join to sync the locked-items list.
 * Wire format: list of (item id, list of (skill enum ordinal, level)).
 */
public class ConfigSyncCP {
    private static final int MAX_LOCK_ITEMS = 8192;
    private static final int MAX_SKILLS_PER_ITEM = 32;

    private final List<LockItem> lockItems;

    public ConfigSyncCP(List<LockItem> lockItems) {
        this.lockItems = lockItems;
    }

    public ConfigSyncCP(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_LOCK_ITEMS) {
            throw new DecoderException("ConfigSyncCP: lock-item count out of range: " + count);
        }
        this.lockItems = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String item = buffer.readUtf(Short.MAX_VALUE);
            int skillCount = buffer.readVarInt();
            if (skillCount < 0 || skillCount > MAX_SKILLS_PER_ITEM) {
                throw new DecoderException("ConfigSyncCP: skill count out of range: " + skillCount);
            }
            List<LockItem.Skill> skills = new ArrayList<>(skillCount);
            for (int j = 0; j < skillCount; j++) {
                int ordinal = buffer.readVarInt();
                int level = buffer.readVarInt();
                if (ordinal < 0 || ordinal >= ESkill.values().length) {
                    throw new DecoderException("ConfigSyncCP: skill ordinal out of range: " + ordinal);
                }
                LockItem.Skill skill = new LockItem.Skill();
                skill.Skill = ESkill.values()[ordinal];
                skill.Level = level;
                skills.add(skill);
            }
            LockItem li = new LockItem(item);
            li.Skills = skills;
            this.lockItems.add(li);
        }
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.lockItems.size());
        for (LockItem li : this.lockItems) {
            buffer.writeUtf(li.Item, Short.MAX_VALUE);
            buffer.writeVarInt(li.Skills.size());
            for (LockItem.Skill s : li.Skills) {
                buffer.writeVarInt(s.Skill != null ? s.Skill.ordinal() : 0);
                buffer.writeVarInt(s.Level);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            LocalPlayer localPlayer = Minecraft.getInstance().player;
            if (localPlayer != null) {
                HandlerSkill.UpdateLockItems(this.lockItems);
            }
        });
        context.setPacketHandled(true);
    }

    public static void sendToPlayer(Player player) {
        List<LockItem> items = new ArrayList<>(HandlerLockItemsConfig.HANDLER.instance().lockItemList);
        ServerNetworking.sendToPlayer(new ConfigSyncCP(items), (ServerPlayer) player);
    }
}
