package com.otectus.runicskills.client.config;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.storage.ConfigHolder;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.ConfigSerializer;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.FieldAccess;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-only bridge between {@link ConfigHolder} and YACL's autogen UI. This is the only
 * class in the project allowed to import {@code dev.isxander.yacl3.*} symbols other than
 * the inert annotations applied to fields in the {@code Handler*Config} POJOs themselves.
 *
 * <p>The editor loads the local file and saves through ConfigHolder's atomic writer.
 * Saving publishes the edited local instance immediately; reopening never writes a stale cache
 * over the file. An integrated server refreshes and synchronizes its gameplay state after Save.
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
    public static <T> Object buildYacl(ConfigHolder<T> holder) {
        var handler = adapt(holder);
        handler.load();
        return gui(holder, handler);
    }

    /**
     * YACL build this mod compiles against. Kept honest against {@code yacl_version} in
     * gradle.properties by the {@code checkVersionConsistency} build task — nothing else stops a
     * dependency bump from silently making the error message below lie about what we built against.
     */
    private static final String YACL_COMPILED_VERSION = "3.5.0+1.20.1-forge";
    /** Forge mod id YACL v3 registers under. */
    private static final String YACL_MOD_ID = "yet_another_config_lib_v3";

    /**
     * Used by the {@code ConfigScreenFactory} registered in {@code RunicSkillsClient}.
     * Builds a fresh YACL screen for the common config each time the user clicks Configure.
     *
     * <p>On failure it returns a {@link YaclUnavailableScreen} pointing at the log and records one
     * actionable error. The two failure classes are reported separately on purpose: a bad YACL
     * install and a bug in this mod's own config schema need completely different fixes from the
     * player, and conflating them is what kept the 1.5.0-1.8.0 {@code disabledPerks} bug
     * misreported as a YACL version mismatch.
     */
    public static Screen buildScreen(Minecraft mc, Screen parent) {
        try {
            ConfigClassHandler<HandlerCommonConfig> handler = adapt(HandlerCommonConfig.HANDLER);
            handler.load();
            return gui(HandlerCommonConfig.HANDLER, handler).generateScreen(parent);
        } catch (ConfigHolder.EditException e) {
            return new YaclUnavailableScreen(parent, Component.literal(e.getMessage()));
        } catch (LinkageError e) {
            // YACL is absent, or present with an API that has drifted from the build we compile
            // against. NoClassDefFoundError / NoSuchMethodError / NoSuchFieldError /
            // AbstractMethodError / VerifyError are all LinkageErrors, NOT RuntimeExceptions, so
            // they must be caught explicitly or the "Configure" button silently does nothing.
            // This is the ONLY branch where blaming the YACL install is correct.
            String found = detectYaclVersion();
            RunicSkills.getLOGGER().error(
                    "Runic Skills config UI could not open: the installed Yet Another Config Lib (YACL) is "
                    + "missing or binary-incompatible. This mod is built against YACL {} for Minecraft "
                    + "1.20.1; installed YACL: '{}'. Install a YACL v3 build for Minecraft 1.20.1.",
                    YACL_COMPILED_VERSION, found, e);
            return new YaclUnavailableScreen(parent,
                    Component.translatable("runicskills.config.unavailable.body", found));
        } catch (RuntimeException e) {
            // NOT a YACL version problem — do not send players off to reinstall YACL. YACL loaded
            // fine and rejected something about *this mod's* config schema. The canonical case is
            // YACLAutoGenException from an illegal annotation combination on a Handler*Config
            // field; `checkYaclAutogen` in build.gradle now catches the known form of that at build
            // time. Log the whole cause chain: the previous handler printed only
            // `e.getClass().getName(): e.getMessage()`, which hid the real reason and let an
            // annotation bug survive three releases misreported as a YACL incompatibility.
            String field = autoGenFieldName(e);
            RunicSkills.getLOGGER().error(
                    "Runic Skills config UI could not open. This is a bug in Runic Skills' own config "
                    + "schema, NOT a problem with your YACL install (installed YACL: '{}', compiled "
                    + "against '{}') - reinstalling or downgrading YACL will not help. Offending config "
                    + "field: '{}'. Please report the stack trace below to the Runic Skills issue tracker.",
                    detectYaclVersion(), YACL_COMPILED_VERSION, field, e);
            return new YaclUnavailableScreen(parent,
                    Component.translatable("runicskills.config.unavailable.schema", field));
        }
    }

    /** Matches the {@code field 'name'} fragment in YACL's autogen wrapper message. */
    private static final Pattern AUTOGEN_FIELD = Pattern.compile("field '([^']+)'");

    /**
     * Best-effort field name from a YACL autogen failure. YACL puts the field name on the OUTER
     * {@code YACLAutoGenException} ("Failed to create option for field 'x'") and the actual reason
     * on its cause, so walk the chain - bounded, in case a cause loops - and take the first name we
     * find. Naming the field is what turns a bug report into a one-line fix. References no YACL
     * type, so it is safe to call after any failure.
     */
    private static String autoGenFieldName(Throwable t) {
        Throwable cur = t;
        for (int depth = 0; cur != null && depth < 10; cur = cur.getCause(), depth++) {
            String msg = cur.getMessage();
            if (msg != null) {
                Matcher m = AUTOGEN_FIELD.matcher(msg);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        return "unknown";
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

    private static boolean remote() {
        return Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().getSingleplayerServer() == null;
    }

    private static <T> YetAnotherConfigLib gui(ConfigHolder<T> holder, ConfigClassHandler<T> handler) {
        var generated = handler.generateGui();
        var builder = YetAnotherConfigLib.createBuilder().title(Component.translatable("runicskills.config.title"));
        if (handler.instance() instanceof HandlerCommonConfig common) builder.category(ProgressionConfigCategory.create(common, remote()));
        builder.categories(generated.categories()).save(generated.saveFunction()).screenInit(generated.initConsumer());
        var config = builder.build();
        if (remote() && holder == HandlerCommonConfig.HANDLER) {
            dev.isxander.yacl3.api.utils.OptionUtils.forEachOptions(config, option -> {
                if (!(option instanceof dev.isxander.yacl3.api.LabelOption)) option.setAvailable(false);
            });
        }
        return new YetAnotherConfigLib() {
            public Component title() { return config.title(); }
            public com.google.common.collect.ImmutableList<dev.isxander.yacl3.api.ConfigCategory> categories() { return config.categories(); }
            public Runnable saveFunction() { return config.saveFunction(); }
            public java.util.function.Consumer<dev.isxander.yacl3.gui.YACLScreen> initConsumer() { return config.initConsumer(); }
            public Screen generateScreen(Screen parent) { return new TransactionalConfigScreen(this, parent); }
        };
    }

    private static <T> ConfigClassHandler<T> adapt(ConfigHolder<T> holder) {
        boolean readOnly = remote() && holder == HandlerCommonConfig.HANDLER;
        var session = new java.util.concurrent.atomic.AtomicReference<ConfigHolder.EditSession<T>>(
                readOnly ? new ConfigHolder.EditSession<>(holder.instance(), null) : holder.beginEdit());
        return ConfigClassHandler.createBuilder(holder.type())
                .id(new ResourceLocation(RunicSkills.MOD_ID, "config"))
                .serializer(c -> new ConfigSerializer<T>(c) {
                    @Override public LoadResult loadSafely(java.util.Map<ConfigField<?>, FieldAccess<?>> fields) {
                        try {
                            for (var entry : fields.entrySet()) copyDraftField(holder, session.get().draft(), entry.getValue());
                            return LoadResult.SUCCESS;
                        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Could not prepare config draft", e); }
                    }
                    @Override public void save() {
                        if (readOnly) throw new IllegalStateException("Server gameplay settings are read-only");
                        var server = Minecraft.getInstance().getSingleplayerServer();
                        Runnable save = () -> {
                            holder.commit(session.get(), c.instance()).orThrow();
                            session.set(holder.beginEdit());
                            if (server != null && holder == HandlerCommonConfig.HANDLER) {
                                try { com.otectus.runicskills.common.command.SkillsReloadCommand.reload(server); }
                                catch (RuntimeException e) { throw new IllegalStateException("Saved to disk, but live reload failed: " + e.getMessage(), e); }
                            }
                        };
                        if (server != null && holder == HandlerCommonConfig.HANDLER) server.submit(save).join();
                        else save.run();
                    }
                }).build();
    }
    private static <T, V> void copyDraftField(ConfigHolder<T> holder, T draft, FieldAccess<V> field)
            throws ReflectiveOperationException {
        Object value = holder.type().getField(field.name()).get(draft);
        @SuppressWarnings("unchecked") V copy = (V) new com.google.gson.Gson().fromJson(
                new com.google.gson.Gson().toJsonTree(value), field.type());
        field.set(copy);
    }
}
