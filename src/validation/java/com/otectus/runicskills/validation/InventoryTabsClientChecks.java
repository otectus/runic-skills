package com.otectus.runicskills.validation;

import com.google.gson.GsonBuilder;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.client.gui.DrawTabs;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Opt-in visual/interaction checks against the reobfuscated mod in a copied production pack.
 * Never included in release jars. Run only in the disposable directory made for validation:
 * GUI options and synthetic player's UI state are changed here, never a user's original save.
 * Screenshots come from Minecraft's actual framebuffer after rendering, not mocked GUI calls.
 */
@Mod.EventBusSubscriber(modid = "runicskills_validation", value = Dist.CLIENT)
public final class InventoryTabsClientChecks {
    private record Tab(AbstractWidget widget, String provider, String id, String className,
                       int x, int y, int width, int height, boolean active, Boolean selected) {
        Map<String, Object> evidence() {
            var data = new LinkedHashMap<String, Object>();
            data.put("provider", provider);
            data.put("id", id);
            data.put("class", className);
            data.put("x", x);
            data.put("y", y);
            data.put("width", width);
            data.put("height", height);
            data.put("active", active);
            data.put("selected", selected);
            return data;
        }
    }

    private record Step(String name, Runnable action, String expectedScreen) {}
    private static final List<Map<String, Object>> captures = new ArrayList<>();
    private static final List<String> failures = new ArrayList<>();
    private static final List<Step> steps = new ArrayList<>();
    private static final String profile = System.getProperty("runicskills.tabValidationProfile", "tabs");
    private static Path output;
    private static int ticks;
    private static int stepIndex = -1;
    private static int settleTicks;
    private static int savedGuiScale;
    private static boolean capturePending;
    private static boolean finished;
    private static Screen experimentalConfirmation;
    private static int confirmationTicks;

    private InventoryTabsClientChecks() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("runicskills.tabValidation") || finished
                || event.phase != TickEvent.Phase.END) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            acknowledgeCopiedWorldWarning(minecraft);
            return;
        }
        if (++ticks < 120) return;
        try {
            if (output == null) initialize(minecraft);
            if (capturePending) return;
            if (settleTicks > 0) {
                if (--settleTicks == 0) capturePending = true;
                return;
            }
            if (++stepIndex >= steps.size()) {
                finish(minecraft);
                return;
            }
            Step step = steps.get(stepIndex);
            RunicSkills.getLOGGER().info("RUNIC_TAB_VALIDATION STEP {} {}", stepIndex, step.name());
            step.action().run();
            settleTicks = 30;
        } catch (Throwable failure) {
            fail("step " + stepIndex + ": " + failure, failure);
            finish(minecraft);
        }
    }

    /**
     * The copied test world may require Forge's experimental-settings confirmation. Let the
     * normal screen initialize and the previous world-open attempt release its storage lock
     * before clicking Proceed. Citadel's synchronous Opening-event bypass can re-enter world
     * loading while the lock is still held, so that bypass is disabled in the test copy only.
     */
    private static void acknowledgeCopiedWorldWarning(Minecraft minecraft) {
        Screen screen = minecraft.screen;
        if (!(screen instanceof ConfirmScreen)
                || !(screen.getTitle().getContents() instanceof TranslatableContents title)
                || !title.getKey().equals("selectWorld.backupQuestion.experimental")) {
            experimentalConfirmation = null;
            confirmationTicks = 0;
            return;
        }
        if (experimentalConfirmation != screen) {
            experimentalConfirmation = screen;
            confirmationTicks = 0;
        }
        if (++confirmationTicks != 20) return;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.active
                    && widget.getMessage().equals(CommonComponents.GUI_PROCEED)) {
                RunicSkills.getLOGGER().info("RUNIC_TAB_VALIDATION acknowledging copied-world experimental settings");
                screen.mouseClicked(widget.getX() + widget.getWidth() / 2.0,
                        widget.getY() + widget.getHeight() / 2.0, 0);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void rendered(TickEvent.RenderTickEvent event) {
        if (!Boolean.getBoolean("runicskills.tabValidation") || finished || !capturePending
                || event.phase != TickEvent.Phase.END) return;
        var minecraft = Minecraft.getInstance();
        try {
            capture(minecraft, steps.get(stepIndex));
        } catch (Throwable failure) {
            fail("capture " + stepIndex + ": " + failure, failure);
        }
        capturePending = false;
    }

    private static void initialize(Minecraft minecraft) throws Exception {
        output = minecraft.gameDirectory.toPath().resolve("tab-validation").resolve(profile);
        Files.createDirectories(output);
        savedGuiScale = minecraft.options.guiScale().get();
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.hideGui = false;
        minecraft.options.guiScale().set(4);
        minecraft.resizeDisplay();
        steps.add(new Step("inventory", InventoryTabsClientChecks::openInventory, "InventoryScreen"));
        if (ModList.get().isLoaded("l2tabs")) {
            steps.add(new Step("attributes-click", () -> clickTab("attributes"), "AttributeScreen"));
            steps.add(new Step("inventory-after-attributes", InventoryTabsClientChecks::openInventory, "InventoryScreen"));
            if (ModList.get().isLoaded("curios")) {
                steps.add(new Step("curios-list-click", () -> clickTab("curios"), "CuriosListScreen"));
                steps.add(new Step("inventory-after-curios-list", InventoryTabsClientChecks::openInventory, "InventoryScreen"));
            }
        }
        steps.add(new Step("skills-click", () -> clickTab("skills"), "RunicSkillsScreen"));
        steps.add(new Step("inventory-return-click", () -> clickTab("inventory"), "InventoryScreen|CuriosScreen"));
        if (ModList.get().isLoaded("customnpcs")) {
            steps.add(new Step("factions-click", () -> clickTab("faction"), "GuiFaction"));
            steps.add(new Step("quests-click", () -> clickTab("quest"), "GuiQuestLog"));
            steps.add(new Step("skills-from-quests-click", () -> clickTab("skills"), "RunicSkillsScreen"));
            steps.add(new Step("inventory-from-skills-click", () -> clickTab("inventory"), "InventoryScreen|CuriosScreen"));
        }
        steps.add(new Step("vanilla-inventory-for-recipe", InventoryTabsClientChecks::openInventory, "InventoryScreen"));
        steps.add(new Step("recipe-open", () -> setRecipeBook(true), "InventoryScreen"));
        steps.add(new Step("recipe-closed", () -> setRecipeBook(false), "InventoryScreen"));
        steps.add(new Step("inventory-scale-3", () -> setScale(3), "InventoryScreen"));
        steps.add(new Step("skills-scale-3-click", () -> clickTab("skills"), "RunicSkillsScreen"));
        steps.add(new Step("skills-scale-4", () -> setScale(4), "RunicSkillsScreen"));
        steps.add(new Step("inventory-scale-4-click", () -> clickTab("inventory"), "InventoryScreen|CuriosScreen"));
        steps.add(new Step("vanilla-inventory-for-reinitialize", InventoryTabsClientChecks::openInventory, "InventoryScreen"));
        steps.add(new Step("inventory-reinitialize", () -> {
            Screen screen = minecraft.screen;
            screen.resize(minecraft, screen.width, screen.height);
        }, "InventoryScreen"));
    }

    private static void openInventory() {
        var minecraft = Minecraft.getInstance();
        minecraft.setScreen(new InventoryScreen(minecraft.player));
        if (((InventoryScreen) minecraft.screen).getRecipeBookComponent().isVisible()) setRecipeBook(false);
    }

    private static void setScale(int scale) {
        var minecraft = Minecraft.getInstance();
        minecraft.options.guiScale().set(scale);
        minecraft.resizeDisplay();
    }

    /** Exercise the vanilla recipe button through the same screen hit test as a mouse click. */
    private static void setRecipeBook(boolean open) {
        var minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof InventoryScreen inventory)) {
            throw new IllegalStateException("Recipe-book check requires InventoryScreen");
        }
        if (inventory.getRecipeBookComponent().isVisible() == open) return;
        inventory.mouseClicked(inventory.getGuiLeft() + 114, inventory.height / 2.0 - 13, 0);
        if (inventory.getRecipeBookComponent().isVisible() != open) {
            throw new IllegalStateException("Recipe-book button did not toggle visibility to " + open);
        }
    }

    private static void clickTab(String target) {
        var minecraft = Minecraft.getInstance();
        Screen original = minecraft.screen;
        for (int page = 0; page < 12; page++) {
            List<Tab> tabs = visibleTabs(original);
            for (Tab tab : tabs) {
                String key = (tab.id + " " + tab.className.substring(tab.className.lastIndexOf('.') + 1))
                        .toLowerCase(Locale.ROOT);
                boolean match;
                if (tab.provider.equals("legendarytabs")) {
                    // FTB Quests and CustomNPCs Quests coexist. Match the exported action
                    // identity, including after paging, rather than the generic word "quest".
                    String id = switch (target) {
                        case "skills" -> "runicskills_skills";
                        case "faction" -> "customnpcs:factions";
                        case "quest" -> "customnpcs:quests";
                        case "attributes" -> "l2tabs:menu.tabs.attribute:";
                        case "curios" -> "l2tabs:menu.tabs.curios:";
                        default -> target;
                    };
                    match = id.endsWith(":") ? tab.id.matches(java.util.regex.Pattern.quote(id) + "[0-9]+")
                            : id.equals(tab.id);
                } else {
                    match = switch (target) {
                        case "skills" -> key.contains("runicskills") || key.contains("skills");
                        case "inventory" -> key.contains("inventory") || key.contains("vanilla");
                        case "attributes" -> tab.className.equals("dev.xkmc.l2tabs.tabs.contents.TabAttributes");
                        case "curios" -> tab.className.equals("dev.xkmc.l2tabs.compat.TabCurios");
                        default -> key.contains(target);
                    };
                }
                if (match && !key.contains("nexttabsbutton")) {
                    original.mouseClicked(tab.x + tab.width / 2.0, tab.y + tab.height / 2.0, 0);
                    // L2's configured Curios inventory opens through a server packet. Check
                    // the resulting screen after settleTicks, not during the click callback.
                    // CustomNPCs GuiQuestLog also discards super's handled return value even
                    // when a tab has successfully opened its destination.
                    return;
                }
            }
            var next = tabs.stream().filter(tab -> tab.className.contains("NextTabsButton")).findFirst();
            if (next.isEmpty()) break;
            Tab tab = next.get();
            original.mouseClicked(tab.x + tab.width / 2.0, tab.y + tab.height / 2.0, 0);
        }
        throw new IllegalStateException("No clickable " + target + " tab on " + original.getClass().getName()
                + ": " + visibleTabs(original).stream().map(Tab::evidence).toList());
    }

    private static List<Tab> visibleTabs(Screen screen) {
        List<Tab> found = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) continue;
            String provider = provider(widget.getClass());
            if (provider == null) continue;
            String id = widget.getMessage().getString();
            try {
                Object tabBase = widget.getClass().getField("tabBase").get(widget);
                var getId = tabBase.getClass().getMethod("getId");
                getId.setAccessible(true);
                id = String.valueOf(getId.invoke(tabBase));
            } catch (ReflectiveOperationException ignored) {
                // L2 and CustomNPCs expose their action through the widget type/message instead.
            }
            found.add(new Tab(widget, provider, id, widget.getClass().getName(), widget.getX(), widget.getY(),
                    widget.getWidth(), widget.getHeight(), widget.active, selected(widget, screen, provider)));
        }
        return found;
    }

    private static Boolean selected(AbstractWidget widget, Screen screen, String provider) {
        try {
            if (provider.equals("legendarytabs")) {
                Object tabBase = widget.getClass().getField("tabBase").get(widget);
                var method = tabBase.getClass().getMethod("isCurrentlyUsed", Screen.class);
                method.setAccessible(true);
                return (Boolean) method.invoke(tabBase, screen);
            }
            if (provider.equals("l2tabs")) {
                Object token = widget.getClass().getField("token").get(widget);
                Object manager = widget.getClass().getField("manager").get(widget);
                return token == manager.getClass().getField("selected").get(manager);
            }
            for (Class<?> type = widget.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    var field = type.getDeclaredField("screenClass");
                    field.setAccessible(true);
                    return screen.getClass() == field.get(widget);
                } catch (NoSuchFieldException ignored) {
                    // CustomNPCs' selected-screen class is inherited by each concrete tab.
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Paging controls have no selected action; null distinguishes this from false.
        }
        return null;
    }

    private static String provider(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            String name = current.getName();
            if (name.startsWith("sfiomn.legendarytabs.")) return "legendarytabs";
            if (name.startsWith("dev.xkmc.l2tabs.")) return "l2tabs";
            if (name.startsWith("noppes.npcs.client.gui.player.tabs.")) return "customnpcs";
        }
        return null;
    }

    private static void capture(Minecraft minecraft, Step step) throws Exception {
        Screen screen = minecraft.screen;
        if (screen == null || java.util.Arrays.stream(step.expectedScreen.split("\\|")).noneMatch(expected ->
                expected.equals(screen.getClass().getSimpleName())
                        || (expected.equals("CuriosScreen") && screen.getClass().getSimpleName().startsWith("CuriosScreen")))) {
            throw new IllegalStateException("Expected " + step.expectedScreen + ", got "
                    + (screen == null ? "null" : screen.getClass().getName()));
        }
        List<Tab> tabs = visibleTabs(screen);
        var capture = new LinkedHashMap<String, Object>();
        capture.put("step", step.name);
        capture.put("screen", screen.getClass().getName());
        capture.put("guiWidth", screen.width);
        capture.put("guiHeight", screen.height);
        capture.put("guiScale", minecraft.getWindow().getGuiScale());
        capture.put("tabs", tabs.stream().map(Tab::evidence).toList());
        capture.put("builtinLayout", DrawTabs.currentLayout(screen));
        if (screen instanceof InventoryScreen inventory) {
            capture.put("panelLeft", inventory.getGuiLeft());
            capture.put("panelTop", inventory.getGuiTop());
            capture.put("recipeBookOpen", inventory.getRecipeBookComponent().isVisible());
        } else if (screen instanceof RunicSkillsScreen skills) {
            capture.put("panelLeft", skills.panelLeft());
            capture.put("panelTop", skills.panelTop());
        }
        List<String> problems = new ArrayList<>();
        if (tabs.isEmpty() && DrawTabs.currentLayout(screen) == null) problems.add("No visible tab navigation");
        var providers = tabs.stream().map(Tab::provider).distinct().toList();
        if (providers.size() > 1) problems.add("Multiple independent tab providers: " + providers);
        if (!tabs.isEmpty() && DrawTabs.currentLayout(screen) != null) problems.add("Built-in and native strips coexist");
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            if (tab.x < 0 || tab.y < 0 || tab.x + tab.width > screen.width || tab.y + tab.height > screen.height) {
                problems.add("Offscreen tab: " + tab.evidence());
            }
            for (int j = i + 1; j < tabs.size(); j++) {
                Tab other = tabs.get(j);
                // Allow native chrome to share a border; the icon centers must remain separate.
                int minWidth = Math.min(16, Math.min(tab.width, other.width));
                int minHeight = Math.min(16, Math.min(tab.height, other.height));
                if (Math.abs(tab.x + tab.width / 2.0 - other.x - other.width / 2.0) < minWidth
                        && Math.abs(tab.y + tab.height / 2.0 - other.y - other.height / 2.0) < minHeight) {
                    problems.add("Overlapping tab icons: " + tab.evidence() + " and " + other.evidence());
                }
            }
        }
        String fileName = String.format(Locale.ROOT, "%02d-%s.png", stepIndex, step.name);
        try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            image.writeToFile(output.resolve(fileName));
        }
        capture.put("screenshot", fileName);
        capture.put("problems", problems);
        captures.add(capture);
        for (String problem : problems) fail(step.name + ": " + problem, null);
        RunicSkills.getLOGGER().info("RUNIC_TAB_VALIDATION CAPTURE {} {}", step.name, capture);
        writeResult(false);
    }

    private static void fail(String message, Throwable failure) {
        failures.add(message);
        if (failure == null) RunicSkills.getLOGGER().error("RUNIC_TAB_VALIDATION FAIL {}", message);
        else RunicSkills.getLOGGER().error("RUNIC_TAB_VALIDATION FAIL " + message, failure);
    }

    private static void writeResult(boolean complete) throws Exception {
        var result = new LinkedHashMap<String, Object>();
        result.put("profile", profile);
        result.put("complete", complete);
        result.put("passed", complete && failures.isEmpty() && captures.size() == steps.size());
        result.put("expectedCaptures", steps.size());
        result.put("captures", captures);
        result.put("failures", failures);
        result.put("mods", ModList.get().getMods().stream()
                .filter(mod -> List.of("runicskills", "legendarytabs", "l2tabs", "customnpcs").contains(mod.getModId()))
                .map(mod -> Map.of("id", mod.getModId(), "version", mod.getVersion().toString())).toList());
        Files.writeString(output.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
    }

    private static void finish(Minecraft minecraft) {
        if (finished) return;
        finished = true;
        try {
            if (output != null) writeResult(true);
            if (failures.isEmpty() && captures.size() == steps.size() && !steps.isEmpty()) {
                RunicSkills.getLOGGER().info("RUNIC_TAB_VALIDATION PASS {} {} captures", profile, captures.size());
                RunicSkills.getLOGGER().info("RUNIC_CLIENT_VALIDATION PASS inventory_tabs {}", profile);
            } else RunicSkills.getLOGGER().error("RUNIC_CLIENT_VALIDATION FAIL inventory_tabs {}", profile);
            minecraft.options.guiScale().set(savedGuiScale);
            minecraft.setScreen(null);
        } catch (Throwable failure) {
            fail("writing result: " + failure, failure);
        } finally {
            minecraft.stop();
        }
    }
}
