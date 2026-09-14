package com.otectus.runicskills.validation;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opt-in disposable server fixture. Never included in the distributable. */
@Mod.EventBusSubscriber(modid="runicskills_validation")
public final class Multiplayer220Checks {
    private static final Set<UUID> initialized = new HashSet<>(), ready = new HashSet<>();
    private static boolean reported;
    private static int ticks;
    private Multiplayer220Checks() {}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("runicskills.multiplayerValidation")) return;
        event.getDispatcher().register(Commands.literal("runic220ready").executes(context -> {
            var player = context.getSource().getPlayerOrException();
            int count = player.getGameProfile().getName().endsWith("1") ? 128 : 256;
            if (player.getInventory().getItem(0).getCount() != count || !player.containerMenu.getCarried().isEmpty())
                throw new IllegalStateException("Real client/server inventory diverged: " + player.getGameProfile().getName());
            ready.add(player.getUUID()); return 1;
        }));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("runicskills.multiplayerValidation") || event.phase != TickEvent.Phase.END || ++ticks%20 != 0) return;
        var server=event.getServer();
        for (var player : server.getPlayerList().getPlayers()) {
            if (!player.getGameProfile().getName().startsWith("R220Rank") || !initialized.add(player.getUUID())) continue;
            int rank=player.getGameProfile().getName().endsWith("1") ? 1 : 3;
            var cap=SkillCapability.get(player); cap.setSkillLevel(RegistrySkills.STRENGTH.get(), com.otectus.runicskills.handler.HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
            cap.setPerkRank(RegistryPerks.PACK_MULE.get(), rank);
            player.getInventory().clearContent(); player.containerMenu.setCarried(ItemStack.EMPTY);
            player.getInventory().setItem(0,new ItemStack(Items.STONE,64+64*rank));
            SyncSkillCapabilityCP.send(player); player.containerMenu.broadcastFullState();
        }
        if (!reported && ready.size()==2 && server.getPlayerList().getPlayerCount()==2) {
            reported=true;
            for (var player : server.getPlayerList().getPlayers()) {
                int count=player.getGameProfile().getName().endsWith("1") ? 128 : 256;
                if (player.getInventory().getItem(0).getCount()!=count) throw new IllegalStateException("Cross-player capacity leak");
            }
            server.getPlayerList().broadcastSystemMessage(Component.literal("RUNIC_220_MULTIPLAYER_PASS"),false);
            com.otectus.runicskills.RunicSkills.getLOGGER().info("RUNIC_220_MULTIPLAYER PASS two simultaneous real clients, ranks 1/3, native split/merge conserved 128/256");
        }
    }
}
