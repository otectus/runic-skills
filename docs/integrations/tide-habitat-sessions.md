# Many Waters habitat sessions

Many Waters requires Endurance 18 by default, scaled from a skill cap of 32.
The first committed native fish catch in a new habitat family grants one 10%
next-cast preparation benefit. It shares the 25% preparation ceiling and six-tick
floor. Native fish delivery remains the only reward trigger.

A session lasts 12,000 game ticks from its first eligible catch. Catching again
does not extend its deadline. It grants at most four habitat rewards and remembers
visited families, so moving back and forth across a boundary cannot rearm it.
The pending charge expires with the session and is consumed only after a native
cast with the owned, permitted rod succeeds. Charge-bar reads do not consume it.

The hook's biome is captured at the start of its native retrieval. No neighboring
biome lookup or global scan is performed. The mutually exclusive families use
this priority order:

| Family | Server biome evidence |
| --- | --- |
| Nether, End | Corresponding vanilla dimension biome tags |
| Ocean, river, coast | `is_ocean`, `is_river`, `is_beach` tags |
| Swamp | Vanilla swamp and mangrove swamp IDs |
| Jungle, taiga, forest, savanna, badlands | Corresponding vanilla biome tags |
| Mountain | `is_mountain` or `is_hill` tag |
| Desert | Vanilla desert ID |
| Plains | Vanilla plains, sunflower plains and snowy plains IDs |
| Other | Any remaining biome, including unclassified mod biomes |

Ocean variants therefore share a family. An unclassified mod biome cannot create
an unlimited number of family rewards. Server data packs can affect tag membership;
the four-reward ceiling applies independently of those classifications.

The charge uses the existing bounded, synchronized capability window. The session
budget stores only three numeric values under
`PlayerPersisted/runicskills:many_waters_session`: start, expiry and a bounded
visited-family bitset. Forge's persisted player data retains the budget across
death/cloning. Login, logout, death, dimension changes, skill loss and disabled
configuration clear the pending benefit without resetting this budget. A clock
rollback preserves the visited set and starts a conservative replacement deadline.
No fish, journal, attachment or native unlock state is written.

Pure session tests cover distinct habitats, revisits, quota, fixed expiry,
restoration and rollback. The native test covers delivery-to-charge, actual
cast consumption, repeated previews, the shared preparation cap, session debt,
expiry and lifecycle cleanup. See [the validation report](validation-2.1.1.json)
for the actual release hash and tested profiles; client rendering is not certified.
