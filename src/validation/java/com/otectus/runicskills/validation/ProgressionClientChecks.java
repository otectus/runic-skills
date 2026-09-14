package com.otectus.runicskills.validation;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.client.config.*;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.*;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.utils.OptionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Runs only in the disposable 2.2.0 client profile, using real screens, server saves and packets. */
@Mod.EventBusSubscriber(modid="runicskills_validation", value=Dist.CLIENT)
public final class ProgressionClientChecks {
    private static int ticks, step;
    private static int connectionTicks;
    private static boolean finished;
    private static TransactionalConfigScreen editor;
    private static byte[] saved;
    private static String capture;
    private static Path output;
    private static final List<String> passes = new ArrayList<>();
    private ProgressionClientChecks() {}
    private static void check(boolean value, String why) { if (!value) throw new IllegalStateException(why); }
    @SuppressWarnings("unchecked") private static <T> Option<T> option(String key) {
        List<Option<?>> options = new ArrayList<>(); OptionUtils.forEachOptions(editor.config, options::add);
        return (Option<T>) options.stream().filter(o -> o.name().getContents() instanceof TranslatableContents t && t.getKey().equals(key)).findFirst().orElseThrow();
    }
    private static void open(Minecraft mc, boolean forgeEntry) {
        var screen = forgeEntry ? YaclConfigUiBuilder.buildScreen(mc, null)
                : ((YetAnotherConfigLib) HandlerCommonConfig.HANDLER.generateGui()).generateScreen(null);
        check(screen instanceof TransactionalConfigScreen, "config entry did not produce the actual YACL editor: " + screen.getClass());
        editor = (TransactionalConfigScreen) screen; mc.setScreen(editor);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("runicskills.progressionClientValidation") || finished || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (Boolean.getBoolean("runicskills.multiplayerValidation")
                && (System.getProperty("runicskills.progressionClientPhase", "").equals("lan")
                    || mc.gameDirectory.getName().equals("update-220-lan-guest"))) {
            // Keep automation from occupying the user's desktop while both clients rendezvous.
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            mc.options.pauseOnLostFocus=false;
        }
        if (mc.player == null || mc.level == null) {
            acceptExperimental(mc);
            if (Boolean.getBoolean("runicskills.multiplayerValidation")
                    && mc.gameDirectory.getName().equals("update-220-lan-guest")
                    && mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen && ++connectionTicks%100==0) {
                var data=new net.minecraft.client.multiplayer.ServerData("Disposable LAN validation","127.0.0.1:25586",true);
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,
                        net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(data.ip),data,false);
            }
            return;
        }
        if (++ticks < 80 || ticks % 20 != 0) return;
        try {
            if (output == null) { output = mc.gameDirectory.toPath().resolve("progression-client-evidence"); Files.createDirectories(output); mc.options.pauseOnLostFocus = false; }
            var path = HandlerCommonConfig.HANDLER.path();
            var server = mc.getSingleplayerServer();
            String phase = System.getProperty("runicskills.progressionClientPhase", "edit");
            if (Boolean.getBoolean("runicskills.multiplayerValidation")) { multiplayer(mc, path, phase); return; }
            if (phase.equals("io")) { ioFailure(mc, path); return; }
            if (phase.equals("scales")) {
                if (step < 4) {
                    mc.options.guiScale().set(++step); mc.resizeDisplay();
                    check(mc.player.getInventory().getItem(0).getCount()==256,"scale changed real count");
                    mc.setScreen(new InventoryScreen(mc.player)); capture="inventory-scale-"+step;
                    passes.add("inventory_gui_scale_"+step);
                } else { mc.options.guiScale().set(0); mc.resizeDisplay(); finish(mc,null); }
                return;
            }
            if (phase.equals("restart")) {
                check(HandlerCommonConfig.HANDLER.instance().skillMaxLevel == 64, "saved cap did not survive restart");
                check(mc.player.getInventory().getItem(0).getCount() == 256, "256 stack did not survive player save/restart and network sync");
                mc.setScreen(new InventoryScreen(mc.player)); capture = "restart-256";
                passes.add("restarted_player_count_and_configuration"); finish(mc, null); return;
            }
            if (server == null) { remote(mc, path); return; }
            RunicSkills.getLOGGER().info("RUNIC_220_CLIENT STEP {}", step);
            switch (step++) {
                case 0 -> {
                    saved = Files.readAllBytes(path); var time = Files.getLastModifiedTime(path);
                    open(mc, true);
                    check(Arrays.equals(saved, Files.readAllBytes(path)) && time.equals(Files.getLastModifiedTime(path)), "opening wrote config");
                    option("runicskills.config.per_skill").requestSet(64);
                    option("runicskills.config.global_mode").requestSet("sum_of_skill_caps");
                    editor.resize(mc, editor.width, editor.height);
                    check(option("runicskills.config.per_skill").pendingValue().equals(64), "resize lost draft");
                    check(Arrays.equals(saved, Files.readAllBytes(path)), "resize wrote config");
                    capture = "progression-preview-64";
                }
                case 1 -> { editor.finishOrSave(); editor.finishOrSave(); passes.add("open_resize_save_done"); }
                case 2 -> {
                    check(HandlerCommonConfig.HANDLER.local().skillMaxLevel == 64, "save did not publish holder");
                    check(server.submit(() -> com.otectus.runicskills.common.progression.LevelCaps.global()).join() == RegistrySkills.getCachedValues().size() * 64, "integrated server used stale cap");
                    open(mc, false); check(option("runicskills.config.per_skill").pendingValue().equals(64), "reopen reset saved cap");
                    saved = Files.readAllBytes(path); option("runicskills.config.per_skill").requestSet(100); editor.cancelOrReset();
                    check(Arrays.equals(saved, Files.readAllBytes(path)), "Cancel wrote the draft"); passes.add("reopen_cancel_server_authority");
                }
                case 3 -> {
                    open(mc, true); option("runicskills.config.per_skill").requestSet(100);
                    Files.writeString(path, Files.readString(path) + "\n// independent edit\n"); byte[] external = Files.readAllBytes(path);
                    editor.finishOrSave(); check(mc.screen == editor && Arrays.equals(external, Files.readAllBytes(path)), "conflicting save overwrote external edit");
                    check(HandlerCommonConfig.HANDLER.local().skillMaxLevel == 64, "failed save published draft"); capture = "save-conflict";
                }
                case 4 -> {
                    Files.write(path, saved); editor.finishOrSave(); check(HandlerCommonConfig.HANDLER.local().skillMaxLevel == 100, "retry lost retained draft");
                    editor.finishOrSave(); passes.add("external_conflict_retained_draft_retry");
                }
                case 5 -> {
                    open(mc, false); option("runicskills.config.per_skill").requestSet(64);
                    editor.finishOrSave(); editor.finishOrSave();
                    server.submit(() -> {
                        var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                        var cap = com.otectus.runicskills.common.capability.SkillCapability.get(player);
                        cap.setSkillLevel(RegistrySkills.STRENGTH.get(), 64); cap.setPerkRank(RegistryPerks.PACK_MULE.get(), 3);
                        player.getInventory().clearContent(); player.getInventory().setItem(0, new ItemStack(Items.STONE, 256));
                        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 128)); player.getInventory().setItem(40, new ItemStack(Items.COBBLESTONE, 192));
                        com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP.send(player); player.containerMenu.broadcastFullState();
                    }).join();
                }
                case 6 -> {
                    check(mc.player.getInventory().getItem(0).getCount() == 256 && mc.player.getOffhandItem().getCount() == 192, "server item-count sync failed");
                    mc.setScreen(new InventoryScreen(mc.player)); capture = "inventory-128-192-256";
                    passes.add("real_server_client_extended_counts");
                }
                case 7 -> mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 36, 1, ClickType.PICKUP, mc.player);
                case 8 -> {
                    check(mc.player.containerMenu.getCarried().getCount() == 128 && mc.player.getInventory().getItem(0).getCount() == 128, "client native right-click split");
                    check(server.submit(() -> {
                        var player = server.getPlayerList().getPlayer(mc.player.getUUID()); return player.containerMenu.getCarried().getCount() == 128 && player.getInventory().getItem(0).getCount() == 128;
                    }).join(), "server click transaction diverged");
                    mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 36, 0, ClickType.PICKUP, mc.player);
                }
                case 9 -> {
                    check(mc.player.getInventory().getItem(0).getCount() == 256 && mc.player.containerMenu.getCarried().isEmpty(), "client/server merge failed");
                    passes.add("native_client_server_split_and_merge"); finish(mc, null);
                }
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }
    private static void remote(Minecraft mc, Path path) throws Exception {
        byte[] before = Files.readAllBytes(path); open(mc, true);
        check(HandlerCommonConfig.HANDLER.instance().skillMaxLevel != HandlerCommonConfig.HANDLER.local().skillMaxLevel, "remote fixture did not have distinct server and local values");
        check(!option("runicskills.config.per_skill").available(), "remote common options were editable");
        check(option("runicskills.config.per_skill").pendingValue().equals(HandlerCommonConfig.HANDLER.instance().skillMaxLevel), "remote screen did not show server values");
        editor.finishOrSave(); check(Arrays.equals(before, Files.readAllBytes(path)), "remote Done wrote local config");
        passes.add("remote_server_values_and_read_only_editor"); finish(mc, null);
    }
    private static void multiplayer(Minecraft mc, Path path, String phase) throws Exception {
        if (ticks > 3600) throw new IllegalStateException("Timed out waiting for both real clients");
        int count=mc.player.getGameProfile().getName().endsWith("1") ? 128 : 256;
        switch (step) {
            case 0 -> {
                if (mc.player.getInventory().getItem(0).getCount()!=count) return;
                if (phase.equals("lan")) {
                    // Synthetic test identities have no launcher account or session token.
                    mc.getSingleplayerServer().setUsesAuthentication(false);
                    check(mc.getSingleplayerServer().publishServer(net.minecraft.world.level.GameType.SURVIVAL,false,25586), "LAN publication failed");
                    passes.add("integrated_server_published_to_lan");
                } else {
                    byte[] before=Files.readAllBytes(path); open(mc,true);
                    check(HandlerCommonConfig.HANDLER.instance().skillMaxLevel != HandlerCommonConfig.HANDLER.local().skillMaxLevel,"local/server config fixture must differ");
                    check(!option("runicskills.config.per_skill").available(),"remote editor was writable");
                    check(option("runicskills.config.per_skill").pendingValue().equals(HandlerCommonConfig.HANDLER.instance().skillMaxLevel),"remote editor showed local cap");
                    editor.finishOrSave();check(Arrays.equals(before,Files.readAllBytes(path)),"remote Done wrote local config");
                    passes.add("distinct_local_config_read_only_server_values");
                }
                mc.setScreen(new InventoryScreen(mc.player)); capture="multiplayer-"+count; step++;
            }
            case 1 -> { mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,36,1,ClickType.PICKUP,mc.player);step++; }
            case 2 -> {
                check(mc.player.containerMenu.getCarried().getCount()==count/2 && mc.player.getInventory().getItem(0).getCount()==count/2,"remote split diverged");
                mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,36,0,ClickType.PICKUP,mc.player);step++;
            }
            case 3 -> {
                check(mc.player.containerMenu.getCarried().isEmpty() && mc.player.getInventory().getItem(0).getCount()==count,"remote merge diverged");
                passes.add("rank_capacity_and_native_split_merge_"+count);
                mc.player.connection.sendCommand("runic220ready"); step++;
            }
        }
    }
    @SubscribeEvent public static void chat(net.minecraftforge.client.event.ClientChatReceivedEvent event) {
        if (Boolean.getBoolean("runicskills.multiplayerValidation") && event.getMessage().getString().equals("RUNIC_220_MULTIPLAYER_PASS")) {
            passes.add("two_simultaneous_real_clients_server_verified"); finish(Minecraft.getInstance(),null);
        }
    }
    private static void ioFailure(Minecraft mc, Path path) throws Exception {
        Path tmp = path.resolveSibling(path.getFileName()+".runicskills.tmp");
        if (step++ == 0) {
            saved = Files.readAllBytes(path); open(mc, true); option("runicskills.config.per_skill").requestSet(100);
            Files.createDirectory(tmp); Files.writeString(tmp.resolve("fixture"), "deliberate temporary I/O failure");
            try {
                editor.finishOrSave();
                check(mc.screen == editor && Arrays.equals(saved, Files.readAllBytes(path)), "I/O failure lost original config or closed screen");
                check(HandlerCommonConfig.HANDLER.local().skillMaxLevel == 64, "I/O failure published draft");
                capture = "save-io-failure";
            } finally { Files.delete(tmp.resolve("fixture")); Files.delete(tmp); }
        } else {
            editor.finishOrSave(); check(HandlerCommonConfig.HANDLER.local().skillMaxLevel == 100, "I/O retry lost draft"); editor.finishOrSave();
            open(mc, true); option("runicskills.config.per_skill").requestSet(64); editor.finishOrSave(); editor.finishOrSave();
            passes.add("native_save_io_failure_retained_draft_and_retry"); finish(mc, null);
        }
    }
    private static void acceptExperimental(Minecraft mc) {
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen screen)
                || !(screen.getTitle().getContents() instanceof TranslatableContents title)
                || !title.getKey().equals("selectWorld.backupQuestion.experimental")) return;
        for (var child : screen.children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                && widget.active && widget.getMessage().equals(net.minecraft.network.chat.CommonComponents.GUI_PROCEED)) {
            screen.mouseClicked(widget.getX() + widget.getWidth()/2., widget.getY()+widget.getHeight()/2., 0); break;
        }
    }
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event) {
        if (capture == null || event.phase != TickEvent.Phase.END || output == null) return;
        String name = capture; capture = null;
        try (var shot = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { shot.writeToFile(output.resolve(name+".png")); }
        catch (Exception failure) { RunicSkills.getLOGGER().error("RUNIC_220_CLIENT capture failed", failure); }
    }
    private static void finish(Minecraft mc, Throwable failure) {
        finished = true;
        try {
            if (capture != null) rendered(new TickEvent.RenderTickEvent(TickEvent.Phase.END, 0));
            Files.writeString(output.resolve(System.getProperty("runicskills.progressionClientPhase", "edit")+".json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed", failure == null, "checks", passes, "failure", failure == null ? "" : failure.toString())) + "\n");
        } catch (Exception writeFailure) { RunicSkills.getLOGGER().error("RUNIC_220_CLIENT evidence failed", writeFailure); }
        if (failure == null) RunicSkills.getLOGGER().info("RUNIC_220_CLIENT PASS {}", passes);
        else RunicSkills.getLOGGER().error("RUNIC_220_CLIENT FAIL step " + step, failure);
        mc.stop();
    }
}
