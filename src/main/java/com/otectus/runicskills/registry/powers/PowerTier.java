package com.otectus.runicskills.registry.powers;

/**
 * Powers tier model from RUNIC_SKILLS_POWERS.md §3.1.
 * <ul>
 *   <li>{@link #MARK} (T1): 5 equipped, 1 PP each, unlocked at governing skill ≥ 30.</li>
 *   <li>{@link #SEAL} (T2): 3 equipped, 2 PP each, requires one same-school Mark slotted.</li>
 *   <li>{@link #CROWN} (T3): 1 equipped, 3 PP, requires one same-school (or category) Seal slotted.</li>
 * </ul>
 * The PP budget is checked server-side in PowersEquipSP. The doc's fallback formula
 * (1 PP per 50 total skill, 14 PP at 700 cap = 5×1 + 3×2 + 1×3) is the default.
 */
public enum PowerTier {
    MARK(5, 1),
    SEAL(3, 2),
    CROWN(1, 3);

    public final int maxEquipped;
    public final int pointCost;

    PowerTier(int maxEquipped, int pointCost) {
        this.maxEquipped = maxEquipped;
        this.pointCost = pointCost;
    }

    public String getKey() {
        return "tier.runicskills." + name().toLowerCase();
    }

    public static PowerTier fromString(String s) {
        if (s == null) return null;
        try {
            return PowerTier.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
