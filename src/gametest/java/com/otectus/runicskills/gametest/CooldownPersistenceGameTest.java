package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.integration.tconstruct.TConstructPowers;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Spec 18.3 L01: an Artifice cooldown survives the save file.
 *
 * <p>{@code PowerRuntime.InternalCooldowns} is memory and is cleared on logout, which is correct
 * for a proc window and wrong for an hour-long Crown -- before 2.0.7 a player could reset Last
 * Temper by reconnecting. Section 15.1 asks for the debt to be serialized as <em>remaining</em>
 * ticks, because the server tick counter restarts at zero and a saved absolute deadline reads as
 * long elapsed after every restart.
 *
 * <p>In the base package with {@code @GameTestHolder}: none of this needs Tinker's Construct. The
 * debt is Runic state on the Runic capability, and it has to keep working on a server that no
 * longer has the mod the Power acts through -- which is exactly the case where losing it would be
 * silent.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class CooldownPersistenceGameTest {

    private static final String EMPTY = "empty";

    /** Comfortably inside the one-day bound, and long enough that no test tick can retire it. */
    private static final int LONG_COOLDOWN = 3600;

    /** L01: start a cooldown, write the capability, read it back, and still be on cooldown. */
    @GameTest(template = EMPTY)
    public static void anArtificeCooldownSurvivesARoundTrip(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "debt_saver");
        Power power = artifice(TConstructPowers.LAST_TEMPER);
        long now = tick(player);

        if (!PowerCooldownDebt.checkAndStart(player, power, now, LONG_COOLDOWN)) {
            throw new GameTestAssertException("a fresh player could not start a cooldown");
        }
        long recorded = PowerCooldownDebt.remaining(player, power, now);
        if (recorded != LONG_COOLDOWN) {
            throw new GameTestAssertException("starting a " + LONG_COOLDOWN
                    + "-tick cooldown recorded " + recorded + " ticks of debt");
        }

        CompoundTag saved = capabilityOf(player).serializeNBT();

        ServerPlayer reloaded = newPlayer(helper, "debt_loader");
        SkillCapability restored = capabilityOf(reloaded);
        restored.deserializeNBT(saved);

        long carried = PowerCooldownDebt.remaining(reloaded, power, tick(reloaded));
        if (carried <= 0L) {
            throw new GameTestAssertException("the cooldown debt did not survive the round trip;"
                    + " relogging would reset " + power.getName() + " (L01)");
        }
        if (carried > LONG_COOLDOWN) {
            throw new GameTestAssertException("the round trip lengthened the debt from "
                    + LONG_COOLDOWN + " to " + carried + " ticks");
        }

        // And it is put back into the runtime map at login, as an absolute deadline against the
        // clock that is running now -- not against the one that wrote the file.
        PowerRuntime.clearPlayer(reloaded.getUUID());
        if (PowerCooldownDebt.restore(reloaded) < 1) {
            throw new GameTestAssertException("login restored no cooldown from a saved debt");
        }
        if (PowerRuntime.InternalCooldowns.isAvailable(reloaded.getUUID(), power.getName(),
                tick(reloaded))) {
            throw new GameTestAssertException(power.getName() + " was available immediately after"
                    + " its saved debt was restored");
        }
        helper.succeed();
    }

    /**
     * Only the twelve are written. Cooldowns are keyed by whatever name started them, so persisting
     * every key would let an addon grow player NBT without a bound.
     */
    @GameTest(template = EMPTY)
    public static void ordinaryAndArtificeDebtKeepTheirCompatibleStorageMaps(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "debt_scope");
        Power crossPower = RegistryPowers.getPower("trueshot");
        if (crossPower == null) {
            throw new GameTestAssertException("the cross-cutting Power trueshot is not registered");
        }
        long now = tick(player);
        PowerCooldownDebt.checkAndStart(player, crossPower, now, LONG_COOLDOWN);

        if (PowerCooldownDebt.remaining(player, crossPower, now) != LONG_COOLDOWN
                || !capabilityOf(player).powerCooldowns.containsKey(crossPower.getName())
                || capabilityOf(player).tcPowerCooldowns.containsKey(crossPower.getName())) {
            throw new GameTestAssertException("ordinary Power debt must use the generic map without changing Artifice storage");
        }
        Power nativePower = artifice(TConstructPowers.FIRST_HEAT);
        PowerCooldownDebt.checkAndStart(player, nativePower, now, LONG_COOLDOWN);
        if (!capabilityOf(player).tcPowerCooldowns.containsKey(nativePower.getName())) {
            throw new GameTestAssertException("Artifice debt no longer uses its existing save map");
        }
        if (PowerRuntime.InternalCooldowns.isAvailable(player.getUUID(), crossPower.getName(), now)) {
            throw new GameTestAssertException("the runtime cooldown was not started either;"
                    + " persistence is an addition to the memory map, not a replacement");
        }
        helper.succeed();
    }

    /**
     * A file claiming a longer debt than any Power can carry is trimmed on the way in. Player NBT
     * is not a trusted input, and a debt of two billion ticks is a Power the player never gets back.
     */
    @GameTest(template = EMPTY)
    public static void anAbsurdSavedDebtIsBoundedOnLoad(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "debt_bounds");
        Power power = artifice(TConstructPowers.GREAT_WORK);
        long now = tick(player);
        PowerCooldownDebt.checkAndStart(player, power, now, LONG_COOLDOWN);

        CompoundTag saved = capabilityOf(player).serializeNBT();
        CompoundTag cooldowns = saved.getCompound("runicskills:tc_state").getCompound("cooldowns");
        if (!cooldowns.contains(power.getName())) {
            throw new GameTestAssertException("the saved compound has no record for "
                    + power.getName() + "; the schema moved without this test");
        }
        cooldowns.putInt(power.getName(), Integer.MAX_VALUE);

        ServerPlayer victim = newPlayer(helper, "debt_bounded");
        capabilityOf(victim).deserializeNBT(saved);
        long carried = PowerCooldownDebt.remaining(victim, power, tick(victim));
        if (carried > 24000L) {
            throw new GameTestAssertException("a hand-edited save produced " + carried
                    + " ticks of debt; section 15.1 bounds it at one day of ticks");
        }
        if (carried <= 0L) {
            throw new GameTestAssertException("bounding an oversized debt discarded it entirely;"
                    + " it should be clamped, not dropped");
        }
        helper.succeed();
    }

    // -- helpers -------------------------------------------------------------------------------

    private static Power artifice(String id) {
        Power power = RegistryPowers.getPower(id);
        if (power == null) {
            throw new GameTestAssertException("Artifice Power " + id + " is not registered");
        }
        return power;
    }

    private static long tick(ServerPlayer player) {
        return player.getServer() == null ? 0L : player.getServer().getTickCount();
    }

    private static ServerPlayer newPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("runicskills-gametest:" + name).getBytes()), name);
        return new ServerPlayer(level.getServer(), level, profile);
    }

    private static SkillCapability capabilityOf(ServerPlayer player) {
        return player.getCapability(RegistryCapabilities.SKILL).orElseThrow(
                () -> new GameTestAssertException("player has no Runic Skills capability"));
    }
}
