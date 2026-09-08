package com.otectus.runicskills.integration.tide;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** A transient next-cast benefit backed by a persisted, four-habitat session budget. */
public final class TideManyWaters {
    public static final String ID = "tide_many_waters";
    private static final String DEBT = "runicskills:many_waters_session";
    private TideManyWaters() {}
    public static boolean available() {
        return IntegrationRuntime.check(IntegrationModule.TIDE, Feature.PERKS, Capability.CATCH_COMMIT, Capability.CAST_PREPARATION).available();
    }
    private static boolean active(Player player) {
        return player != null && !(player instanceof FakePlayer) && player.isAlive() && available()
                && RegistryPerks.TIDE_MANY_WATERS.get().isEnabled(player);
    }
    /** Called only after the catch bridge commits a delivered native fish. */
    public static void caught(ServerPlayer player, TideHabitat.Family family) {
        if (!active(player)) return;
        var cap = SkillCapability.get(player);
        if (cap == null) return;
        long now = player.serverLevel().getGameTime();
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        var debt = persisted.getCompound(DEBT);
        var session = HabitatSession.restore(now, debt.getLong("started"), debt.getLong("expires"), debt.getInt("visited"));
        var visit = session.visit(now, family.ordinal());
        var updated = visit.session();
        CompoundTag saved = new CompoundTag();
        saved.putLong("started", updated.started()); saved.putLong("expires", updated.expires()); saved.putInt("visited", updated.visited());
        persisted.put(DEBT, saved); player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        if (visit.reward()) {
            cap.setPowerWindow(ID, updated.expires());
            if (player.connection != null) SyncSkillCapabilityCP.send(player);
        }
    }
    /** The shared cast-bar/release path only reads this synchronized pending window. */
    public static double preparation(Player player) {
        var cap = SkillCapability.get(player);
        return active(player) && cap != null && cap.isPowerWindowActive(ID, player.level().getGameTime())
                ? Math.min(25, Math.max(0, HandlerCommonConfig.HANDLER.instance().tideManyWatersPercent)) / 100.0 : 0;
    }
    public static void clearCharge(Player player) {
        var cap = SkillCapability.get(player);
        if (cap != null && cap.getPowerWindowExpiry(ID) != 0) {
            cap.setPowerWindow(ID, 0);
            if (player instanceof ServerPlayer server && server.connection != null) SyncSkillCapabilityCP.send(server);
        }
        // The persisted habitat budget survives logout, clone, dimension and configuration changes.
    }
    public static void tick(ServerPlayer player) {
        var cap = SkillCapability.get(player);
        if (cap != null && cap.getPowerWindowExpiry(ID) != 0
                && (!active(player) || !cap.isPowerWindowActive(ID, player.level().getGameTime()))) clearCharge(player);
    }
}
