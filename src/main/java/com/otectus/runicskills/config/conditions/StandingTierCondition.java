package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.integration.ReputationFacade;
import net.minecraft.server.level.ServerPlayer;

/**
 * Title condition on MCA: Reputation standing, as a ladder index (stranger = 0, upward from there).
 *
 * <p>Written {@code StandingTier/<scope>/<comparator>/<index>}, where {@code scope} is
 * {@code here} for the village the player is currently standing in and anything else — {@code best}
 * by convention — for the best standing they hold with any community.
 *
 * <p>Reads through {@link ReputationFacade}, never through the Reputation API, so this class loads
 * on a server without the mod: there it always resolves to the bottom tier and the title is simply
 * unobtainable.
 */
public class StandingTierCondition extends IntegerConditionImpl {

    /** Scope selecting the nearest village rather than the player's best community. */
    private static final String SCOPE_HERE = "here";

    public StandingTierCondition() {
        super("StandingTier");
    }

    @Override
    public void ProcessVariable(String value, ServerPlayer serverPlayer) {
        setProcessedValue(SCOPE_HERE.equalsIgnoreCase(value)
                ? ReputationFacade.tierIndexHere(serverPlayer)
                : ReputationFacade.bestTierIndex(serverPlayer));
    }
}
