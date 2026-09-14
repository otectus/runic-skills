# Optional integrations and interface audit — September 2026

The current source and pinned local artifacts are the authority for this review. `CLAUDE.md`,
`MODMAP.md`, `CONTENT_TRACE_2.1.0.md` and `FOUR_MOD_INTEGRATION_2.1.1.md` were orientation;
historical validation in those documents is not evidence of a new runtime test. The refreshed
repository-wide content trace supplies individual registration and consumer references.

## Priorities and implemented corrections

1. **P1 — Unified Arcana generated mana from hit count instead of payment.**
   `ArsNouveauIntegration.onSpellResolveUnifiedArcana` used `SpellResolveEvent.Post` and
   `spell.getCost()`: repeated resolution, AoE and discounted/free casts could refund the raw
   cost repeatedly. The pinned Ars 4.12.7 `SpellResolver` bytecode confirms that resolution can
   repeat independently, while successful normal casts call `expendMana()` after their cast
   method returns successfully. `MixArsSpellPayment` now observes the actual mana capability
   immediately before and after that single native debit, after price-calculation callbacks.
   Nested casts during price calculation therefore cannot be counted twice. Failed/throwing operations award
   nothing; client, creative, spectator and fake-player paths receive no refund. The per-hit
   listener is removed. Native costs are unchanged. Both integration switches are checked live.

2. **P1 — Schoolbridge transferred the neutral base as earned power.**
   Iron's 3.16.3 `AttributeRegistry.newPowerAttribute` initializes each school power at `1.0`.
   The former calculation interpreted `1.0` as a +100% bonus, giving every bridge a free bonus
   even without school investment. `ArsSpellMath.schoolBridgeMultiplier` now transfers only
   `max(0, schoolPower - 1)`; a 50% bridge and 1.20 school power gives +10% Ars damage. Negative
   modifiers cannot make the purchased bridge a penalty. The existing finite-damage guard is
   used, both integration toggles are respected, and all six descriptions explain the base.

3. **P2 — Ars discounts could make an already-free spell cost one mana.**
   Form Focus, Wild Manipulation, Hedgewitch, Conjurer and Arcane Scholar independently floored
   results at one, including native zero-cost spells and spells made free by Arcane Efficiency.
   The shared cost arithmetic preserves zero and positive-cost conservation floors, never
   increases a cost, and uses `long` multiplication for Scholar's glyph discount.

4. **P2 — Paid refunds were mistaken for natural regeneration.**
   Iron's `ChangeManaEvent` does not describe why mana changed. Runic's handler applies flat
   Magic/Intelligence regeneration whenever mana rises; this would overpay even a correctly
   priced Unified Arcana refund. Its explicit grant now runs through a scoped guard that skips
   only those flat regeneration additions, preserving native caps and other change listeners.
   The scope restores correctly when nested or when downstream listeners throw.

5. **P2 — Passive descriptions showed stored ranks as current strength.**
   The progression correction retains allocated passive ranks through level loss. `PassiveTooltip`
   now displays the effective rank and actual effective value, plus a clear explanation when
   some stored ranks are dormant. No division by zero occurs for an empty rank definition.
   The new explanation has an explicit English-fallback entry in the translation-debt manifest.

6. **P2 — Efficient Crafting tooltip now states the exploit-prevention boundary.**
   It describes consumed-ingredient refunds for ordinary manufacture and exclusions for
   repairs, copying, special recipes and reversible compression/decompression. Curse Breaker's
   existing description already covers the corrected enchanted-book behavior.

7. **P1 — Datapack skill art never reached dedicated-server clients.**
   `SkillVisualsReloadListener` formerly mutated only a shared `Skill` field. Its per-listener
   previous-key set also missed removals when Forge recreated reload listeners. The server now
   publishes an immutable full snapshot, sends it through `SkillVisualsSyncCP` on login and
   datapack reload, and the client maintains an independent snapshot even in an integrated
   server. Disconnect and server-stop clear their respective state. Three fields per skill are
   bounded to 256-character resource IDs; at most 64 overrides / 128 source files are accepted.
   Every candidate validates before publication; malformed reloads retain the previous state.
   Removing all files publishes an empty snapshot. Different resources targeting one skill have
   deterministic resource-ID ordering and a duplicate warning. Client resource-pack lookup is
   cached, reset on resource reload, and falls back to the existing art when a texture is absent.

No identifiers, saved ranks, inventory NBT or existing packet layouts changed for these fixes.
The added visual packet requires protocol 15 / matching 2.1.2 clients and servers; older peers
are rejected during negotiation instead of receiving unknown message IDs.
The new Ars-targeted mixin is gated on Ars presence, uses a string target and optional injector
matching (`require = 0, expect = 1`); neither Ars nor Iron's is bundled. Its declared native method
and field are unobfuscated, so `remap = false` is intentional. The complete mixin inventory in
`CLAUDE.md` was refreshed from `runicskills.mixins.json` rather than extending its stale short list.

## Definition-to-behavior trace by integration family

| Definitions / feature | Gameplay landing point and checks | Review outcome |
| --- | --- | --- |
| Iron's spell gates and scaling | `IronsSpellbooksIntegration`: pre-cast skill/school requirements; cast cost and echo; spell damage, per-school multipliers, Constitution defense; modification events for spell level | Event paths checked; live integration gate retained. |
| Iron's school Adept/Warded/Catalyst perks | Server END tick reconciles stable transient attribute UUIDs; successful native cast or spell-damage callbacks apply school effects | Attributes are removed when disabled; catalyst duration declarations distinguish seconds from the Eldritch tick value. |
| Iron's summon/utility/cross-mod perks | Summon join/hurt/death ownership, charge state and cooldowns; Affix Focus, Spellsocket, Resonant Affixes, Quickcast and Mana Bulwark events | Optional boundaries and effect consumers checked; no broad balance rewrite. |
| Ars spell progression and form/school perks | Resolve-pre skill gate; damage-pre scaling and defense; spell-cost, regeneration, pool and glyph-modifier events | Cost-floor defect fixed. School classification remains recipe-based as implemented. |
| Ars familiar perks | `IFamiliar.getOwnerID()` resolves current owner; familiar outgoing damage and incoming reduction; golem classification | Existing ownership is retained; effect strength is bounded by configured values. |
| Six Schoolbridges / Unified Arcana | Ars damage-pre reads corresponding ISS school attribute; native Ars payment hook restores ISS mana | Base-value and repeated-resolution bugs fixed as above. |
| Apotheosis rarity, gems, reforging and affix perks | `ApotheosisIntegration`, menu/slot mixins and native socket/enchantment events; canonical rarity paths and manual container checks | Existing unknown-rarity rejection, transient modifier cleanup and separated AttributesLib adapter retained. |
| Ten Apothic attribute perks | `ApothicAttributesPerksIntegration` reconciles central UUIDs against `ALObjects.Attributes` every ten server ticks | AttributesLib plus Apotheosis presence and live integration toggle required; no permanent bonus NBT introduced. |
| Simply Swords / Simply More combat and Powers | `WeaponCombat` → native primary-hit scope and committed health loss; `SwordsActivations`, `SwordsGems`, `SwordsReturns`, `MountedLance`, `MoreReach`, `MoreShield`, `MoreMimicry` | Actual action ownership, paid activation, eligible targets, shared Guard limits and lifecycle cleanup retained. |
| T.O. Aqua/cast/combat/relic Powers and perks | Optional companion → `TomPaidCasts` payment receipts → `TomCastRewards`, `TomCombatRewards`, `TomNativeRewards`; Aqua attribute binding and bounded Confluence budgets | Exact artifact/companion requirements preserved. Source ownership does not fall back to matching arbitrary names. |
| Tide perks, Angling Powers and field notes | Native cast/retrieval ledger → accepted fish delivery/species record → final retrieval wear; preparation, minigame, bait, weighting and journal adapters | Actor/hook/hand/generation/rod-snapshot checks and single-use committed rewards retained. No duplicate fish or XP granted. |
| Four-mod integration rules | Atomic `IntegrationRules`/`IntegrationRuleIndex` resolution and diagnostics | Automatic equipment/native-ability enforcement and client rule synchronization are still pending; they are not represented as completed. |
| FTB Quests' six task types | Registry → no-op facade when absent → progression notifications/login backfill → `AbstractRunicTask.evaluate` → team progress | Startup-only enablement and sticky progress semantics retained. Referenced quest icons exist. |
| KubeJS / reputation / other equipment integrations | Reflective bootstrap or Forge-only adapters; registry namespace classification; progression hooks and optional providers | No new optional class references introduced into always-loaded consumers. Source references are covered by the global trace. |
| Inventory tabs, skill/perk/Power interfaces | Reflective native-tab registration → evidence-based fallback strip; authoritative selection refresh; existing authored icons and wrapped tooltips | Existing fallback and icon system retained; no unnecessary new raster assets. Dormant passive values corrected. |
| Datapack skill art | Validated `SkillVisualsReloadListener` candidate → immutable server snapshot → login/reload packet → independent client snapshot → resource-pack existence fallback in `RunicSkillsScreen` | Multiplayer delivery, deleted-file cleanup, disconnect isolation and deterministic collisions corrected. |

## Validation and acceptance limits

- `ArsSpellMathTest` covers neutral/positive/negative school power, non-finite values, zero and
  positive discount floors, extreme glyph counts and committed-versus-free payment arithmetic.
- `ArsPaidCastGameTest.backflowTracksPaymentNotHits` is registered by name only when both native
  mods exist. It calls the real transformed `expendMana`, posts repeated native resolve events,
  checks free payments and disables the ISS integration mid-test. It deliberately overrides
  only the quoted price, leaving the native debit and refund hooks intact. A quote-time nested
  payment must refund exactly the combined mana actually paid.
- `SkillVisualsGameTest` verifies wire round-trip, independent client/server snapshots, empty
  snapshot and disconnect reset, malformed/oversized/duplicate packet rejection, deterministic
  override order, atomic rejection and removed-file cleanup across fresh reload listeners.
- The root validation report records actual Gradle/unit/GameTest outcomes; this document does
  not claim a profile passed merely because a test was authored.
- Reconfirm touch, projectile, AoE, repeating casts, real equipment discounts and modded mana
  listeners in game; inspect GUI scale, translation wrapping and remote-client dormant ranks.
- Native-only add-on versions not present in an executed profile remain source-reviewed only.
- FTB non-sticky task progress is team-owned but evaluated from one triggering player; team
  member joins and mixed progression deserve explicit product semantics and live acceptance.
- Other positive ISS mana changes still have the existing broad regeneration interpretation;
  only the priced Unified Arcana refund is isolated here. A native regeneration-specific seam
  would be needed before reworking every upstream and third-party mana grant safely.

## Skill visual pack compatibility

Existing valid JSON keeps the same folder, required lowercase `skill` name, and optional
`overview_icon`, `detail_icon`, `background` fields. A missing/null texture field uses existing
art. Bare texture paths resolve to `runicskills`; qualified IDs retain their namespace. PNGs
must still be shipped through a client resource pack; synchronization sends IDs, not image bytes.
Same-path resource overrides obey Minecraft's pack priority. If different files target the same
skill, the final resource-ID-sorted file wins. Pack authors relying on an accidental iteration
order should consolidate those files. Unknown fields, non-string values, invalid IDs and
excessive sizes now reject the candidate instead of partially changing it. Unknown skills still
warn and skip, allowing definitions to remain dormant when their skill is absent.

## Additional issue identified for follow-up

Title `HideRequirements` is construction-time metadata while requirements refresh separately.
Client title metadata is likewise distinct from unlocked/selected capability state. Neither
problem should be concealed with client inference from local configuration.
