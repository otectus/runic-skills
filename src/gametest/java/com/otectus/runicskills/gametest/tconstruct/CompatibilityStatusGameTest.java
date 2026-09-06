package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Status;
import com.otectus.runicskills.integration.tconstruct.TConstructProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/**
 * Spec §14.4: the compatibility diagnostic says what is actually happening, not what was intended.
 *
 * <p>Conservative mode is the case this exists for. A player on a Tinkers' this release has not read
 * still gets classification and repair, because those are ordinary API calls; they do not get the
 * wear hook, because injecting into unread bytecode is how a compatibility layer becomes a crash on
 * somebody else's update. Both halves have to be true, and the diagnostic has to distinguish them —
 * "unsupported" for all of it would be a lie in one direction and useless in the other.
 *
 * <p>The unknown-profile half is asserted against the pure mapping rather than by running a second
 * server: a gametest cannot change the Tinkers' jar underneath itself, and the decision that would
 * be different is entirely {@link TConstructProfile#allowsMixins()}.
 */
@PrefixGameTestTemplate(false)
public class CompatibilityStatusGameTest {

    private static final String EMPTY = "empty";

    /** On the version this release is built against, everything the profile gates is available. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void theStableProfileIsFullySupported(GameTestHelper helper) {
        TConstructCompatibilityStatus status = TConstructCompatibilityStatus.current();
        if (status.profile() != TConstructProfile.STABLE_311) {
            throw new GameTestAssertException("the M1 gametest run detected profile "
                    + status.profile() + " (version " + status.version()
                    + "); these tests are written against 3.11");
        }
        expect(status, Capability.CLASSIFICATION, Status.SUPPORTED);
        expect(status, Capability.REPAIR, Status.SUPPORTED);
        expect(status, Capability.WEAR_AVOIDANCE, Status.SUPPORTED);
        expect(status, Capability.WORKMANSHIP, Status.SUPPORTED);
        // Off by default, and the diagnostic must say so rather than reporting it as broken.
        expect(status, Capability.STACK_REQUIREMENTS, Status.DISABLED_BY_CONFIG);
        helper.succeed();
    }

    /** Every capability carries a reason, because a status with no reason explains nothing. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void everyCapabilityCarriesAReason(GameTestHelper helper) {
        TConstructCompatibilityStatus status = TConstructCompatibilityStatus.current();
        for (Capability capability : Capability.values()) {
            String reason = status.entry(capability).reason();
            if (reason == null || reason.isBlank()) {
                throw new GameTestAssertException(capability + " reports "
                        + status.entry(capability).status() + " with no reason");
            }
        }
        if (!status.describe().contains(status.version())) {
            throw new GameTestAssertException("the diagnostic summary does not name the version it "
                    + "is describing");
        }
        helper.succeed();
    }

    /** An unrecognised version resolves to conservative mode, and 3.12 is recognised but not injected. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void anUnknownVersionMeansNoMixins(GameTestHelper helper) {
        assertProfile("3.11.2.166", TConstructProfile.STABLE_311, true);
        assertProfile("3.12.0.220", TConstructProfile.BETA_312, false);
        assertProfile("3.9.1.0", TConstructProfile.UNKNOWN, false);
        assertProfile("4.0.0.0", TConstructProfile.UNKNOWN, false);
        if (TConstructProfile.of(null) != TConstructProfile.UNKNOWN) {
            throw new GameTestAssertException("an unreadable version did not resolve to UNKNOWN");
        }
        helper.succeed();
    }

    private static void assertProfile(String version, TConstructProfile expected, boolean mixins) {
        TConstructProfile actual = TConstructProfile.of(new DefaultArtifactVersion(version));
        if (actual != expected) {
            throw new GameTestAssertException("version " + version + " resolved to " + actual
                    + ", expected " + expected);
        }
        if (actual.allowsMixins() != mixins) {
            throw new GameTestAssertException("profile " + actual + " reports allowsMixins()="
                    + actual.allowsMixins() + "; conservative mode means no injection");
        }
    }

    private static void expect(TConstructCompatibilityStatus status, Capability capability,
                               Status expected) {
        Status actual = status.entry(capability).status();
        if (actual != expected) {
            throw new GameTestAssertException(capability + " reports " + actual + " ("
                    + status.entry(capability).reason() + "); expected " + expected);
        }
    }
}
