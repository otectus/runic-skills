package com.otectus.runicskills.support;

/**
 * Removes comments and string/char literals from Java source text so a source-scanning test counts
 * only <em>executable</em> references.
 *
 * <p>Written for the coverage tests ({@code PerkEffectCoverageTest}, {@code PowerEffectCoverageTest}):
 * a perk mentioned solely in a javadoc line such as "see RegistryPerks.SCHOLAR" used to satisfy the
 * "has an effect site" rule, which is exactly the kind of silent inertness those tests exist to
 * catch (HIGH-03 shipped that way).</p>
 *
 * <p>Replaced regions are collapsed to whitespace rather than deleted, so line and column offsets in
 * the stripped text still line up with the original — a test that reports a line number stays honest.</p>
 */
public final class SourceStripper {

    private SourceStripper() {
    }

    /** Blanks out {@code //} comments, {@code /* *}{@code /} comments, and string/char literals. */
    public static String strip(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            char next = (i + 1 < n) ? source.charAt(i + 1) : '\0';

            if (c == '/' && next == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && next == '*') {
                out.append("  ");
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("  ");
                    i += 2;
                }
            } else if (c == '"' || c == '\'') {
                char quote = c;
                out.append(' ');
                i++;
                while (i < n) {
                    char d = source.charAt(i);
                    if (d == '\\' && i + 1 < n) {
                        out.append("  ");
                        i += 2;
                        continue;
                    }
                    out.append(d == '\n' ? '\n' : ' ');
                    i++;
                    if (d == quote || d == '\n') break;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
