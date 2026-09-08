package com.otectus.runicskills.client.integration.tide;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.integration.tide.TideJournal;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.common.TideJournalSP;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** A read-only companion page reached from the native journal; all knowledge comes from the server. */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, value = Dist.CLIENT)
public final class TideJournalScreen extends Screen {
    private final Screen parent;
    private ResourceLocation focused;
    private TideJournal.Page data;
    private TideJournal.Entry comparison;
    private int filter, request, scroll;
    private static int nextRequest;
    private static Component text(String key, Object... args) { return Component.translatable("gui.runicskills.tide_journal." + key, args); }
    private TideJournalScreen(Screen parent, ResourceLocation fish) { super(text("title")); this.parent = parent; focused = fish; }
    @SubscribeEvent public static void journal(ScreenEvent.Init.Post event) {
        Screen parent = event.getScreen();
        boolean nativeJournal = parent.getClass().getName().equals("com.li64.tide.client.gui.screens.journal.FishingJournal");
        var player = Minecraft.getInstance().player;
        boolean inventory = parent instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                && player != null && TideJournal.hasJournal(player)
                && (TideJournal.availability(false, false).available() || TideJournal.availability(true, false).available());
        if (!nativeJournal && !inventory) return;
        event.addListener(Button.builder(text("title"), button -> {
            ResourceLocation fish = TideJournal.NONE;
            try {
                var field = parent.getClass().getDeclaredField("activeFish"); field.setAccessible(true);
                Object value = field.get(parent);
                if (value instanceof ItemStack stack && !stack.isEmpty()) fish = ForgeRegistries.ITEMS.getKey(stack.getItem());
            } catch (ReflectiveOperationException ignored) { }
            var screen = new TideJournalScreen(parent, fish); Minecraft.getInstance().setScreen(screen); screen.query(0, false);
        }).bounds(nativeJournal ? 5 : parent.width - 125, 5, 120, 20).build());
    }
    private void query(int page, boolean select) {
        request = ++nextRequest; ServerNetworking.sendToServer(new TideJournalSP(request, focused, Math.max(0,page), filter, select));
    }
    public static void accept(int request, TideJournal.Page data) {
        if (Minecraft.getInstance().screen instanceof TideJournalScreen screen && screen.request == request) {
            screen.data = data; screen.filter = Math.min(screen.filter, data.features()); screen.scroll = 0; screen.comparison = null; screen.rebuildWidgets();
        }
    }
    @Override protected void init() {
        addRenderableWidget(Button.builder(text("back"), b -> onClose()).bounds(10, height-24, Math.max(80,width/3-20), 20).build());
        if (data == null) return;
        int left = 10, top = 52, rowHeight = Math.max(8, Math.min(20,(height-116)/12));
        for (var row : data.entries()) {
            var item = ForgeRegistries.ITEMS.getValue(row.fish());
            Component name = item == null ? text("known_fish") : item.getDescription();
            int y = top; top += rowHeight;
            addRenderableWidget(Button.builder(name, b -> { focused = row.fish(); scroll = 0; rebuildWidgets(); })
                    .bounds(left, y, Math.max(100,width/3-20), rowHeight).build());
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> query(data.page()-1,false)).bounds(10,height-48,22,20).build()).active = data.page()>0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> query(data.page()+1,false)).bounds(36,height-48,22,20).build()).active = data.page()+1<data.pages();
        String[] labels = {"all", "eligible", "shared", "unmet"};
        addRenderableWidget(Button.builder(text(labels[filter]), b -> { filter=(filter+1)%(data.features()+1); query(0,false); })
                .bounds(width/3,28,Math.min(145,width/3),20).build()).active = data.features()>0;
        addRenderableWidget(Button.builder(text("refresh"), b -> query(data.page(),false)).bounds(width-74,28,64,20).build());
        var row = focused();
        int detailWidth = width-width/3-12;
        var player = Minecraft.getInstance().player;
        var favor = com.otectus.runicskills.registry.RegistryPowers.TIDE_FAVOR_FROM_THE_DEEP.get();
        addRenderableWidget(Button.builder(text("select"), b -> query(data.page(),true)).bounds(width/3,height-48,detailWidth,20).build()).active = row != null
                && player != null && favor.isEquippedBy(player) && com.otectus.runicskills.registry.powers.PowerEligibility.evaluateActive(player,favor).eligible();
        if (data.features()>=2) addRenderableWidget(Button.builder(text(comparison==null?"compare":"clear_comparison"), b -> { comparison=comparison==null?focused():null; scroll=0; rebuildWidgets(); })
                .bounds(width/3,height-24,detailWidth,20).build()).active = row != null;
    }
    private TideJournal.Entry focused() { return data == null ? null : data.entries().stream().filter(e -> e.fish().equals(focused)).findFirst().orElse(null); }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g); super.render(g,mx,my,partial);
        g.drawCenteredString(font,title,width/2,10,0xE0D3AA);
        if(data==null) { g.drawCenteredString(font,text("loading"),width/2,55,0xCCCCCC); return; }
        g.drawString(font,text("page",data.known(),data.page()+1,data.pages()),10,32,0xAAAAAA,false);
        var row=focused(); int x=width/3, y=55;
        List<String> lines=new ArrayList<>();
        if(row!=null) {
            lines.add(text(data.live()?(row.eligible()?"conditions_met":"conditions_unmet"):"cast_to_evaluate").getString());
            if(data.selected().equals(row.fish())) lines.add(text("selected").getString());
            append(lines,row);
        } else lines.add(text(data.message()).getString());
        if(comparison!=null && data.features()>=2) {
            var item = ForgeRegistries.ITEMS.getValue(comparison.fish());
            if (item != null) { lines.add(text("comparison",item.getDescription()).getString()); append(lines,comparison); }
        }
        var wrapped=new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for(String line:lines) wrapped.addAll(font.split(Component.literal(line),Math.max(80,width-x-12)));
        int visible=Math.max(1,(height-110)/11); scroll=Math.min(scroll,Math.max(0,wrapped.size()-visible));
        for(int i=scroll;i<Math.min(wrapped.size(),scroll+visible);i++,y+=11) g.drawString(font,wrapped.get(i),x,y,0xDDDDDD,false);
    }
    private void append(List<String> lines,TideJournal.Entry row) {
        for(var c:row.requirements()) lines.add((data.live()?(c.passed()?"✓ ":"× "):"• ")+c.label()+ (c.requirement().isEmpty()?"":": "+c.requirement()));
    }
    @Override public boolean mouseScrolled(double x,double y,double amount) { scroll=Math.max(0,scroll-(int)Math.signum(amount)*3); return true; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
