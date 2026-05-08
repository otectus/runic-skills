package com.otectus.runicskills.client.config;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.storage.ConfigHolder;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-only bridge between {@link ConfigHolder} and YACL's autogen UI. This is the only
 * class in the project allowed to import {@code dev.isxander.yacl3.*} symbols other than
 * the inert annotations applied to fields in the {@code Handler*Config} POJOs themselves.
 *
 * <p>Sync strategy: before the YACL screen opens, the holder writes its current in-memory
 * state to disk. YACL then reads the file fresh, presents the autogen UI, and on Save writes
 * the user's edits back to the same file. After the user closes the screen we let the next
 * read-cycle ({@code /skillsreload}, the join-time sync packet, or the next
 * {@code .instance()} cache miss) pull the YACL-edited values back through the holder.
 * This avoids depending on YACL's internal field layout.
 *
 * <p>Loaded only from {@code RunicSkillsClient.ClientProxy.clientSetup} (which is itself
 * client-only). This class never appears on the dedicated server classpath, so its
 * {@code dev.isxander.yacl3.*} imports cannot crash a server boot.
 */
public final class YaclConfigUiBuilder {

    private YaclConfigUiBuilder() {}

    /**
     * Entry point reflectively invoked from {@link ConfigHolder#generateGui()}.
     * Returns a YACL {@code YetAnotherConfigLib} (typed as {@code Object} in the holder
     * signature so that class doesn't reference YACL types).
     */
    public static Object buildYacl(ConfigHolder<?> holder) {
        holder.save();
        return adapt(holder).generateGui();
    }

    /**
     * Used by the {@code ConfigScreenFactory} registered in {@code RunicSkillsClient}.
     * Builds a fresh YACL screen for the common config each time the user clicks Configure.
     * Falls back to the parent screen if YACL is missing or screen construction fails.
     */
    public static Screen buildScreen(Minecraft mc, Screen parent) {
        try {
            HandlerCommonConfig.HANDLER.save();
            ConfigClassHandler<HandlerCommonConfig> handler = adapt(HandlerCommonConfig.HANDLER);
            handler.load();
            Screen screen = handler.generateGui().generateScreen(parent);
            return new ReloadOnCloseScreen(screen, parent, HandlerCommonConfig.HANDLER);
        } catch (NoClassDefFoundError | RuntimeException e) {
            RunicSkills.getLOGGER().warn(
                    "Failed to build YACL config screen ({}). Returning to previous screen.",
                    e.getMessage());
            return parent;
        }
    }

    private static <T> ConfigClassHandler<T> adapt(ConfigHolder<T> holder) {
        return ConfigClassHandler.createBuilder(holder.type())
                .id(new ResourceLocation(RunicSkills.MOD_ID, "config"))
                .serializer(c -> GsonConfigSerializerBuilder.create(c)
                        .setPath(holder.path())
                        .setJson5(true)
                        .build())
                .build();
    }
}
