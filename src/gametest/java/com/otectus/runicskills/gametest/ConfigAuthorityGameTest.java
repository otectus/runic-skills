package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.snapshot.ConfigSchema;
import com.otectus.runicskills.config.snapshot.GameplayConfigSnapshot;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPassives;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.passive.Passive;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Server-authoritative configuration, exercised against the real config class (RS10-005).
 *
 * <p>The unit tests cover the codec with fixture POJOs, which is where the edge cases belong. What
 * they cannot cover is {@code HandlerCommonConfig} itself — 1,133 fields whose static initialiser
 * needs Forge's config directory — and that is exactly where the defect lived: two hand-written
 * packets carried 128 of those fields and the other 1,005 were resolved from whatever file the
 * client happened to have. These tests run on a real server, against the real class.
 *
 * <p>Every test restores the previous authoritative state before returning, so ordering between
 * GameTests cannot matter.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class ConfigAuthorityGameTest {

    private static final String EMPTY = "empty";

    /**
     * The whole config survives a round trip. This is the test that would have caught the original
     * defect: it compares every field, so a field that fails to travel fails here rather than
     * becoming a quietly client-decided gameplay value.
     */
    @GameTest(template = EMPTY)
    public static void everyConfigFieldSurvivesTheRoundTrip(GameTestHelper helper) {
        ConfigSchema<HandlerCommonConfig> schema = GameplayConfigSnapshot.schema();
        if (schema.size() < 1000) {
            throw new GameTestAssertException("the config schema holds only " + schema.size()
                    + " fields; the real class has over a thousand, so this test is not seeing it");
        }

        HandlerCommonConfig source = HandlerCommonConfig.HANDLER.local();
        HandlerCommonConfig target = new HandlerCommonConfig();
        try {
            schema.decodeInto(schema.encode(source), target);
        } catch (IOException e) {
            throw new GameTestAssertException("round trip of the real config failed: " + e);
        }

        List<String> different = new ArrayList<>();
        for (ConfigSchema.Entry entry : schema.entries()) {
            try {
                Field field = entry.field();
                if (!Objects.deepEquals(field.get(source), field.get(target))) {
                    different.add(entry.name());
                }
            } catch (ReflectiveOperationException e) {
                throw new GameTestAssertException("could not compare " + entry.name() + ": " + e);
            }
        }
        if (!different.isEmpty()) {
            throw new GameTestAssertException(different.size()
                    + " field(s) did not survive encode/decode, so they would never reach a client: "
                    + different.subList(0, Math.min(20, different.size())));
        }
        helper.succeed();
    }

    /**
     * A published snapshot decides what gameplay reads, and the local file keeps deciding what gets
     * written. Both halves matter: the previous packets wrote the server's values straight into the
     * client's own in-memory config, mixing two configurations into one object.
     */
    @GameTest(template = EMPTY)
    public static void aPublishedSnapshotOverridesReadsButNotTheLocalFile(GameTestHelper helper) {
        HandlerCommonConfig local = HandlerCommonConfig.HANDLER.local();
        int localValue = local.skillMaxLevel;
        int foreignValue = localValue + 7;

        boolean hadSnapshot = GameplayConfigSnapshot.isServerAuthoritative();
        try {
            HandlerCommonConfig fromServer = decodedCopy(helper);
            fromServer.skillMaxLevel = foreignValue;
            HandlerCommonConfig.HANDLER.setAuthoritative(fromServer);

            if (HandlerCommonConfig.HANDLER.instance().skillMaxLevel != foreignValue) {
                throw new GameTestAssertException(
                        "gameplay reads did not follow the server snapshot: expected "
                        + foreignValue + ", got " + HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
            }
            if (HandlerCommonConfig.HANDLER.local().skillMaxLevel != localValue) {
                throw new GameTestAssertException(
                        "the server's value leaked into this machine's own configuration");
            }
        } finally {
            if (!hadSnapshot) GameplayConfigSnapshot.clear();
        }
        helper.succeed();
    }

    /**
     * The part the two old packets could not do at all: a perk's requirement level is captured when
     * the registry is filled, so changing it in configuration has to re-derive the registered
     * instance the UI, tooltips and eligibility checks read.
     */
    @GameTest(template = EMPTY)
    public static void perkRequirementsFollowTheConfigInForce(GameTestHelper helper) {
        Perk probe = firstPerkWithAPositiveRequirement();
        if (probe == null) {
            throw new GameTestAssertException("no perk has a positive requirement level; the "
                    + "registry looks empty and this test would pass vacuously");
        }
        String fieldName = requirementFieldFor(probe);
        Field field = fieldOrNull(fieldName);
        if (field == null) {
            throw new GameTestAssertException("no config field named " + fieldName
                    + " for perk " + probe.getName() + "; has the naming convention changed?");
        }

        int originalRequirement = probe.requiredLevel;
        boolean hadSnapshot = GameplayConfigSnapshot.isServerAuthoritative();
        try {
            HandlerCommonConfig fromServer = decodedCopy(helper);
            field.setInt(fromServer, originalRequirement + 3);
            HandlerCommonConfig.HANDLER.setAuthoritative(fromServer);

            RegistryPerks.refreshFromConfig();
            if (probe.requiredLevel != originalRequirement + 3) {
                throw new GameTestAssertException("perk " + probe.getName()
                        + " kept its startup requirement " + probe.requiredLevel
                        + " after the configuration changed to " + (originalRequirement + 3)
                        + "; registered metadata is still frozen (RS10-005)");
            }
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("could not set " + fieldName + ": " + e);
        } finally {
            if (!hadSnapshot) GameplayConfigSnapshot.clear();
            RegistryPerks.refreshFromConfig();
        }

        if (probe.requiredLevel != originalRequirement) {
            throw new GameTestAssertException("perk " + probe.getName()
                    + " did not return to its local requirement after the snapshot was dropped: "
                    + "expected " + originalRequirement + ", got " + probe.requiredLevel);
        }
        helper.succeed();
    }

    /** Passives carry per-level values and requirement arrays with the same problem. */
    @GameTest(template = EMPTY)
    public static void passiveLevelArraysFollowTheConfigInForce(GameTestHelper helper) {
        Passive probe = RegistryPassives.getPassive("attack_damage");
        if (probe == null || probe.levelsRequired == null || probe.levelsRequired.length == 0) {
            throw new GameTestAssertException(
                    "the attack_damage passive is missing or has no levels; cannot test");
        }
        int[] original = probe.levelsRequired;
        boolean hadSnapshot = GameplayConfigSnapshot.isServerAuthoritative();
        try {
            HandlerCommonConfig fromServer = decodedCopy(helper);
            fromServer.attackPassiveLevels = new int[]{2, 4, 6};
            HandlerCommonConfig.HANDLER.setAuthoritative(fromServer);

            RegistryPassives.refreshFromConfig();
            if (probe.levelsRequired.length != 3 || probe.levelsRequired[1] != 4) {
                throw new GameTestAssertException("attack_damage kept its startup level array "
                        + java.util.Arrays.toString(probe.levelsRequired)
                        + " after the configuration changed (RS10-005)");
            }
        } finally {
            if (!hadSnapshot) GameplayConfigSnapshot.clear();
            RegistryPassives.refreshFromConfig();
        }

        if (!java.util.Arrays.equals(probe.levelsRequired, original)) {
            throw new GameTestAssertException("attack_damage did not return to its local level "
                    + "array after the snapshot was dropped");
        }
        helper.succeed();
    }

    /**
     * Two builds with different field sets must be told apart rather than misread. The schema hash
     * is what turns "someone is running a slightly different jar" from silently wrong gameplay
     * values into a named refusal.
     */
    @GameTest(template = EMPTY)
    public static void aPayloadFromADifferentFieldSetIsRefused(GameTestHelper helper) {
        byte[] payload = GameplayConfigSnapshot.encodeForClients();
        // Corrupt only the schema hash, leaving the field data intact — the case a plain length or
        // checksum test would miss.
        payload[0] ^= 0x5A;

        boolean hadSnapshot = GameplayConfigSnapshot.isServerAuthoritative();
        try {
            GameplayConfigSnapshot.applyFromServer(payload);
            throw new GameTestAssertException(
                    "a payload from a different field set was accepted; gameplay values would be "
                    + "read out of alignment with no error");
        } catch (ConfigSchema.ConfigSchemaMismatchException expected) {
            // Correct.
        } catch (IOException e) {
            throw new GameTestAssertException(
                    "expected a schema-mismatch refusal, got " + e);
        } finally {
            if (!hadSnapshot) GameplayConfigSnapshot.clear();
        }
        helper.succeed();
    }

    // -- helpers -------------------------------------------------------------------------------

    private static HandlerCommonConfig decodedCopy(GameTestHelper helper) {
        HandlerCommonConfig copy = new HandlerCommonConfig();
        ConfigSchema<HandlerCommonConfig> schema = GameplayConfigSnapshot.schema();
        try {
            schema.decodeInto(schema.encode(HandlerCommonConfig.HANDLER.local()), copy);
        } catch (IOException e) {
            throw new GameTestAssertException("could not copy the config: " + e);
        }
        return copy;
    }

    private static Perk firstPerkWithAPositiveRequirement() {
        for (Perk perk : RegistryPerks.getCachedValues()) {
            if (perk.requiredLevel > 0 && fieldOrNull(requirementFieldFor(perk)) != null) return perk;
        }
        return null;
    }

    /** {@code one_handed} to {@code oneHandedRequiredLevel} — the convention every perk follows. */
    private static String requirementFieldFor(Perk perk) {
        String[] parts = perk.getName().split("_");
        StringBuilder camel = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            camel.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return camel.append("RequiredLevel").toString();
    }

    private static Field fieldOrNull(String name) {
        try {
            Field field = HandlerCommonConfig.class.getField(name);
            return field.getType() == int.class ? field : null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
