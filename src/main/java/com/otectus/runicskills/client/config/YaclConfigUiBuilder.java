package com.otectus.runicskills.client.config;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.storage.ConfigHolder;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

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
    /** YACL build this mod compiles against — see {@code yacl_version} in gradle.properties. */
    private static final String YACL_COMPILED_VERSION = "3.5.0+1.20.1-forge";
    /** Forge mod id YACL v3 registers under. */
    private static final String YACL_MOD_ID = "yet_another_config_lib_v3";

    public static Screen buildScreen(Minecraft mc, Screen parent) {
        try {
            HandlerCommonConfig.HANDLER.save();
            ConfigClassHandler<HandlerCommonConfig> handler = adapt(HandlerCommonConfig.HANDLER);
            handler.load();
            Screen screen = handler.generateGui().generateScreen(parent);
            return new ReloadOnCloseScreen(screen, parent, HandlerCommonConfig.HANDLER);
        } catch (LinkageError | RuntimeException e) {
            // Catch LinkageError (not just NoClassDefFoundError): a present-but-incompatible YACL —
            // the "downloaded the most recent YACL" case, allowed by the open-ended [3.4.2,) range —
            // throws NoSuchMethodError / NoSuchFieldError / AbstractMethodError / VerifyError while
            // the screen is built. Those are LinkageErrors, NOT RuntimeExceptions, so the old
            // `NoClassDefFoundError | RuntimeException` catch let them escape to Forge and the
            // "Configure" button silently did nothing (Report 2). Detect the actual YACL version,
            // log ONE actionable ERROR, and return a vanilla pointer screen instead of a no-op.
            String found = detectYaclVersion();
            RunicSkills.getLOGGER().error(
                    "Runic Skills config UI could not open. It needs Yet Another Config Lib (YACL) v3 "
                    + "for Minecraft 1.20.1 (compiled against {}); installed YACL: '{}'. If you grabbed a "
                    + "newer YACL, install the 1.20.1 build instead. Cause: {}: {}",
                    YACL_COMPILED_VERSION, found, e.getClass().getName(), e.getMessage());
            return new YaclUnavailableScreen(parent,
                    Component.translatable("runicskills.config.unavailable.body", found));
        }
    }

    /**
     * Best-effort read of the installed YACL version via Forge's mod list. References no YACL type,
     * so it is safe to call from the catch block after a YACL {@link LinkageError}. Returns
     * {@code "absent"} when YACL is not installed.
     */
    private static String detectYaclVersion() {
        try {
            return ModList.get().getModContainerById(YACL_MOD_ID)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("absent");
        } catch (Throwable t) {
            return "unknown";
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
