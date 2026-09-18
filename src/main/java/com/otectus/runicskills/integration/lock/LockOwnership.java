package com.otectus.runicskills.integration.lock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Which integration owns a piece of content, and therefore whose generated rule about it counts.
 *
 * <p>Two providers can both produce a rule for one id, and the generated layers have no natural
 * order between them: the merge is {@code putIfAbsent}, so whoever ran first wins. That is fine
 * while every generator is a namespace keyword scanner, and wrong the moment one of them is a
 * native adapter that reads the item's real state. A generic id default for
 * {@code tconstruct:pickaxe} inserted before {@link com.otectus.runicskills.integration.tconstruct}
 * gets a look at the stack does not merely compete with the material-tier profile — it disables it,
 * because that resolver deliberately declines when an id rule already exists (spec §7.2).
 *
 * <p>So ownership is resolved <em>before</em> generic defaults are generated, not after. An owner
 * claims a set of ids; a generated rule from anyone else about a claimed id is dropped. Explicit
 * rules are untouched: a pack author naming an exact id outranks every automatic layer including
 * the owner's, and suppressing an authored rule to protect an adapter would invert the precedence
 * contract this exists to enforce.
 *
 * <p>Ids are plain strings here, not {@code ResourceLocation}s, so the precedence logic is testable
 * without a running Minecraft — which is the whole reason the rest of the lock package is
 * source-scanned by its tests rather than exercised.
 */
public final class LockOwnership {

    /** One owner's claim over a set of content ids. */
    public record Claim(String ownerId, Predicate<String> claims) {
        public Claim {
            if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("blank owner id");
            if (claims == null) throw new IllegalArgumentException("null claim predicate");
        }

        /** A claim over every id in one registry namespace. */
        public static Claim namespace(String ownerId, String namespace) {
            String prefix = namespace + ":";
            return new Claim(ownerId, id -> id != null && id.startsWith(prefix));
        }
    }

    private final List<Claim> claims;

    private LockOwnership(List<Claim> claims) {
        this.claims = List.copyOf(claims);
    }

    public static LockOwnership empty() {
        return new LockOwnership(List.of());
    }

    public static LockOwnership of(Claim... claims) {
        return new LockOwnership(List.of(claims));
    }

    /** A copy of this ownership table with one more claim appended, evaluated last. */
    public LockOwnership with(Claim claim) {
        List<Claim> next = new ArrayList<>(claims);
        next.add(claim);
        return new LockOwnership(next);
    }

    /** The registered claims, in the order they are consulted. */
    public List<Claim> claims() {
        return claims;
    }

    /**
     * The owner of {@code id}, or empty when nobody claims it.
     *
     * <p>First claim wins. A claim that throws is treated as not matching: an integration's probe
     * failing must not hand its content to a generic generator, but it must not take the whole rule
     * build down either, so the id simply ends up unowned and the ordinary merge decides.
     */
    public Optional<String> ownerOf(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        for (Claim claim : claims) {
            try {
                if (claim.claims().test(id)) return Optional.of(claim.ownerId());
            } catch (RuntimeException ignored) {
                // Treated as "does not claim"; see above.
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a generated rule for {@code id} from {@code providerId} must be dropped.
     *
     * <p>True only when somebody else owns the id. The owner's own generator is never suppressed by
     * its own claim, and unowned ids behave exactly as they did before ownership existed.
     */
    public boolean suppressesGenerated(String providerId, String id) {
        Optional<String> owner = ownerOf(id);
        return owner.isPresent() && !owner.get().equals(providerId);
    }
}
