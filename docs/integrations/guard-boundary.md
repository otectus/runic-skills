# Owned Guard boundary

Runic Guard is a transient pool shared by the four-mod catalog. It does not use potion
absorption or alter an entity's native absorption field. A grant refreshes to the greater
remaining balance and the later expiry, with hard bounds of four health points and 160
ticks from the grant. Upstream trigger adapters remain responsible for native success,
hostile-action legitimacy, recipient ownership and cooldowns.

`MixGuardDamage` targets the two implementations of `actuallyHurt(DamageSource,float)`:
`LivingEntity` and `Player`. The Forge-patched source was inspected in the local 1.20.1 /
47.4.22 source artifact. The packaged tests run against Forge 47.4.23; source inspection
alone is not the production claim.

Both implementations apply the following order:

1. Invulnerability and Forge `onLivingHurt` processing.
2. Native armor, magic mitigation and native absorption payment.
3. Forge `onLivingDamage`, including every event subscriber and late cancellation.
4. Runic Guard consumes at most the positive final damage and remaining pool balance.
5. Native health loss, combat tracking and statistics use the resulting damage.

The shield boundary is earlier in `hurt`; a fully blocked hit does not spend Guard.
Native absorption already paid before a late Forge cancellation stays paid, exactly as
without Guard. Guard does not refund it. Player absorbed-damage statistics naturally
include Forge-stage mitigation; no additional statistic or health write is introduced.

An invocation-local snapshot captures the pool and grant generation before
`onLivingHurt`. A new or refreshed grant during the damage callbacks cannot protect
that triggering hit. Nested real hits have their own snapshots and draw from the same
finite pool. There is one Guard adjustment per native `actuallyHurt` invocation, without
a thread-local damage frame that could leak after an exception.

`minecraft:bypasses_invulnerability`, `minecraft:bypasses_effects` and the extensible
`runicskills:bypasses_guard` damage tag bypass Guard. Packs cannot remove the first two
native bypass checks by replacing the Runic tag.

State is neither saved nor cloned. Logout, dimension changes and entity removal clear
it; server ticks clear expired pools and committed deaths. A cancellable death event
does not itself clear the pool. At most 4096 weakly held recipients are retained.

A bounded clientbound packet carries the entity ID, UUID, remaining points and expiry
duration. The UUID protects against reuse of an entity ID. A new tracker receives the
current pool; changes and expiry synchronize to trackers and self. The client displays
the balance and remaining seconds for self, the ridden entity and the looked-at entity.
Client maps clear on world changes and disconnect. This is presentation only.

`GuardBudgetTest` verifies conservation, refresh, expiry and invalid input. Three
`GuardGameTest` cases exercise player and non-player damage, armor, native absorption,
late cancellation, callback grants, nested damage, bypass damage, an actual directional
shield block with native wear, lifecycle clearing and packet encoding bounds. Production
results and tested jar hashes are recorded in `validation-2.1.1.json` after those runs pass.
The base, Tide, pinned Simply Swords / More / Tide and all-four server profiles pass these checks.
Apothic Attributes adds random critical hits and changes armor calculations; the comparison
fixture fixes attacker critical chance and uses a hit that leaves damage after native absorption.
Client rendering still requires separate checks. Guard-dependent catalog entries are not enabled
by this foundation alone.
