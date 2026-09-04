package com.otectus.runicskills.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Public access to {@code Screen.addRenderableWidget}, for the CustomNPCs tab strip.
 *
 * <p><b>Why.</b> {@code ScreenEvent.Init.Post#addListener} is the only supported way to add a
 * widget to somebody else's screen, and it exists solely for the duration of that event. The
 * CustomNPCs integration cannot rely on {@code Init.Post} ordering (CustomNPCs adds its own tabs
 * from a handler that is also at {@code EventPriority.LOWEST}, so which handler runs first is
 * registration order and, in practice, ours), so it inserts the tab at first render instead — by
 * which time the event object is gone and the screen's own {@code protected} adder is the only
 * route left. The alternative, mutating {@code children()} directly, would add the tab as a
 * listener without adding it as a renderable, and it would never draw.
 *
 * <p>Client-only: the target is a {@code net.minecraft.client} class.
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T runicskills$addRenderableWidget(T widget);
}
