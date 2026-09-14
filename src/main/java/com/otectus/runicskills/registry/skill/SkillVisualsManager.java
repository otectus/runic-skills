package com.otectus.runicskills.registry.skill;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import java.util.Map;

/** Independent immutable server/client snapshots, including in an integrated-server JVM. */
public final class SkillVisualsManager {
    public static final int MAX_OVERRIDES = 64;
    public static final int MAX_ID_LENGTH = 256;
    private static volatile Map<ResourceLocation, SkillVisuals> server = Map.of();
    private static volatile Map<ResourceLocation, SkillVisuals> client;

    private SkillVisualsManager() {}
    public static Map<ResourceLocation, SkillVisuals> serverSnapshot() { return server; }
    public static Map<ResourceLocation, SkillVisuals> clientSnapshot() { return client == null ? Map.of() : client; }
    public static void replaceServer(Map<ResourceLocation, SkillVisuals> next) { server = checked(next); }
    public static void receive(Map<ResourceLocation, SkillVisuals> next) { client = checked(next); }
    public static void clearClient() { client = null; }
    public static void clearServer() { server = Map.of(); }

    public static SkillVisuals forSkill(ResourceLocation id) {
        Map<ResourceLocation, SkillVisuals> received = client;
        // Client rendering never borrows the server singleton: an integrated world could still
        // be shutting down while the player starts connecting to a different remote server.
        if (EffectiveSide.get().isClient()) return received == null ? null : received.get(id);
        return server.get(id);
    }

    private static Map<ResourceLocation, SkillVisuals> checked(Map<ResourceLocation, SkillVisuals> next) {
        if (next.size() > MAX_OVERRIDES) throw new IllegalArgumentException("Too many skill visual overrides");
        next.forEach((id, visuals) -> {
            checkId(id);
            if (visuals == null) throw new IllegalArgumentException("Missing skill visuals");
            if (visuals.overviewIcon() != null) checkId(visuals.overviewIcon());
            if (visuals.detailIcon() != null) checkId(visuals.detailIcon());
            if (visuals.background() != null) checkId(visuals.background());
        });
        return Map.copyOf(next);
    }

    private static void checkId(ResourceLocation id) {
        if (id == null || id.toString().length() > MAX_ID_LENGTH)
            throw new IllegalArgumentException("Skill visual resource id exceeds the wire bound");
    }
}
