# Keeper of the Banks

`runicskills:tide_keeper_of_the_banks` is a Wisdom Seal in Angling. It uses the normal
Seal skill, Intelligence, Power Point and equipped Angling Mark requirements.

## Trigger and bounded state

Only the existing native catch ledger's accepted, single-use fish commit advances the
sequence. Canonical species come from delivered fish that Tide processed; habitat comes
from the actual hook's biome. Rejected outputs, items, pulled entities, illegal mediums,
changed rods and duplicate retrievals do not count. Multiple outputs can supply multiple
species, but one retrieval supplies only one habitat family.

The first qualifying catch starts a five-minute window. Three distinct species across
two habitat families within that window arm the reward. At most three species IDs and
a fifteen-family bitmask are retained per capability, with 1,024 admitted capabilities.
An expired window restarts on the next catch; clock rollback discards the old window.
During cooldown or while a reward remains pending, catches cannot bank another sequence.

## Independent charges and shared limits

The reward starts a 120-second cooldown and two charges with the same 90-second expiry:

- Preparation: 15% faster native preparation, shared with all other Runic preparation
  contributions under the existing 25% cap and six-tick floor. Repeated server/client
  duration queries only read the synchronized charge. An accepted native cast spends it.
- Bait: a 20% contribution to the next eligible native bait decrement. Baitkeeper and
  Keeper share one roll per cast, a 25% total cap and at most one conserved unit across
  all slots and outputs. Both a successful and a failed roll spend this charge. Empty
  retrieval, rejected delivery, missing bait and unrelated consumption do not spend it.

The bait charge survives spending preparation. The catch that arms Keeper cannot use
its new bait charge retroactively: bait consumption precedes final catch commitment.
Native loops continue normally, including spending a preserved last unit on a later
output. No copied bait is refunded and no new fish, XP or journal entry is created.

Unequip, login, logout, death, dimension change and loss of eligibility clear the
sequence and both charges. Cooldown debt persists through the existing registered-Power
save mechanism. Perks and Powers retain independent switches. Missing any of the catch,
preparation or bait capabilities makes Keeper unavailable while retaining its saved ID.

## Overrides and verification

The existing Power override resource accepts `values.bait_percent` (0..25, default 20),
`values.preparation_percent` (0..25, default 15), `values.charge_seconds` (1..90, default 90)
and `icd_ticks` (1..72000, default 2400). Disabling both benefits disables the Power.
Descriptions use these same bounded values; non-finite values use defaults.

`KeeperSequenceTest` checks species/family requirements, multi-output limits, exact expiry,
rollback and invalid observations. `keeperNativeSequenceAndIndependentCharges` exercises
native species delivery with temporary, restored biome fixtures, Seal prerequisites,
sequence reset, independent preparation/bait consumption, eight bait scenarios, shared
caps and eleven lifecycle states. The production harness runs it against the packaged
release. See [current validation](validation-2.1.1.json) for tested profiles and hashes.
Client rendering/prediction and remote multiplayer still require separate validation.
