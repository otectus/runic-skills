# Runic Skills 2.0.4 — Read-only Audit and Remediation Specification

## 1. Summary

### Baseline and scope

Repository: [otectus/runic-skills](https://github.com/otectus/runic-skills). The requested `2.0.4` source revision does not exist in the repository snapshot exposed by GitHub. This audit is therefore pinned to immutable commit [`e7ea32cc4e8580164e900c5bcddc7e07c7a547f5`](https://github.com/otectus/runic-skills/tree/e7ea32cc4e8580164e900c5bcddc7e07c7a547f5) (`master`, 2026-09-02), whose own version files identify it as **2.0.3**. Do not apply this specification to a different tree without first re-running the inventory and line checks; see NV-01.

Environment evaluated: Minecraft 1.20.1, Forge 47.x, Java 17, ForgeGradle 6. The audit was static and read-only. No mod source, asset, configuration, or repository metadata was changed.

| Severity | Count | Meaning in this report |
|---|---:|---|
| Critical | 0 | No confirmed crash or data-loss defect |
| High | 6 | A selectable/configured feature is inert or unreachable, or socketing can be attributed to the wrong player |
| Medium | 8 | Incorrect calculation, state lifetime, gating, integration behavior, or content-status claim |
| Low | 2 | Resource/UI defect or provably dead scaffolding |
| **Total** | **16** | Confirmed findings only |

Overall health: the core registration, capability, packet, and event-bus paths are substantially wired, and the static resource sweep found no malformed JSON, missing literal textures, missing Power icons, or missing mixin classes. The audited tree is not content-complete, however: one perk and two passive/stat pipelines are inert; two Iron's Spells perks are unreachable; five public config fields are unused; and Apotheosis socket attribution is both stale and cross-player. These should block promotion of this tree as a 2.0.4 release candidate.

Static validation performed:

- Enumerated 10 skills, 445 perks, 38 passives, 75 Powers, and 9 custom attributes: **577 rows total**.
- Parsed all 27 JSON resources with duplicate-key detection: zero parse errors and zero duplicate keys.
- Resolved 533 literal texture references: zero missing files. Resolved all 75 Power icons: zero missing, orphaned, or duplicate files.
- Checked 25 common and 5 client mixin declarations: every declared mixin source exists.
- Compared literal translation calls with `en_us.json`; only vanilla keys and the deliberately dynamic rank prefix were absent. The sound-subtitle scan separately found LOW-01.
- Enumerated every public `HandlerCommonConfig` instance field and searched executable Java references outside generic config serialization. Exactly five have no gameplay read: the three fields in HIGH-06 and the two fields in HIGH-05.
- Stripped comments and Java literals before searching perk/Power references. `scholar` is the only registered perk with no executable reference; all 75 Powers have at least one executable reference. This stricter scan exposes the false positive in HIGH-03.

## 2. Coverage inventory

Paths in these tables are relative to `src/main/java/com/otectus/runicskills/` unless they begin with `src/`. “Wired” means a declaration, effect site, and hook were found; it is not a claim that every runtime combination has been smoke-tested. Rows with a feature-specific defect link to the finding ID that accounts for them in Step 2; cross-cutting subsystem findings are documented once in Step 2. Conditional integration entries are definitions in this inventory even when their optional dependency is absent at runtime. `Power` is the codebase's ability abstraction; there is no separate ability registry.

### Coverage accounting

| Kind | Rows |
|---|---:|
| Skills | 10 |
| Perks | 445 |
| Passives / stat upgrades | 38 |
| Powers / abilities | 75 |
| Custom attributes / stats | 9 |
| **Total** | **577** |

### Skills

| Registry ID | Constant | Declared | Effect implementation | Runtime trigger | Audit status |
|---|---|---|---|---|---|
| `strength` | `STRENGTH` | `registry/RegistrySkills.java:34` | `common/capability/SkillCapability.java:242-303`; child perk/passive/Power gates | `network/packet/common/SkillLevelUpSP.java:45-123`; `common/progression/ProgressionService.java:81-160` | Wired; Iron's school contribution mis-gated — MEDIUM-02 |
| `constitution` | `CONSTITUTION` | `registry/RegistrySkills.java:35` | `common/capability/SkillCapability.java:242-303`; child perk/passive/Power gates | `network/packet/common/SkillLevelUpSP.java:45-123`; `common/progression/ProgressionService.java:81-160` | Wired; Iron's school contribution mis-gated — MEDIUM-02 |
| `dexterity` | `DEXTERITY` | `registry/RegistrySkills.java:36` | `common/capability/SkillCapability.java:242-303`; child perk/passive/Power gates | `network/packet/common/SkillLevelUpSP.java:45-123`; `common/progression/ProgressionService.java:81-160` | Wired; Iron's school contribution mis-gated — MEDIUM-02 |
| `endurance` | `ENDURANCE` | `registry/RegistrySkills.java:37` | `common/capability/SkillCapability.java:242-303`; child perk/passive/Power gates | `network/packet/common/SkillLevelUpSP.java:45-123`; `common/progression/ProgressionService.java:81-160` | Wired; Iron's school contribution mis-gated — MEDIUM-02 |
| `intelligence` | `INTELLIGENCE` | `registry/RegistrySkills.java:38` | `common/capability/SkillCapability.java:242-303`; child perk/passive/Power gates | `network/packet/common/SkillLevelUpSP.java:45-123`; `common/progression/ProgressionService.java:81-160` | Wired; mana/school contributions mis-gated — MEDIUM-02 |
| `building` | `BUILDING` | `registry/RegistrySkills.java:39` | `common/capability/SkillCapability.java:242-303`; child perk/passive/Power gates | `network/packet/common/SkillLevelUpSP.java:45-123`; `common/progression/ProgressionService.java:81-160` | Wired |
| `wisdom` | `WISDOM` | `registry/RegistrySkills.java:40` | `common/capability/SkillCapability.java:242-303`; child perk/passive/Power gates | `network/packet/common/SkillLevelUpSP.java:45-123`; `common/progression/ProgressionService.java:81-160` | Wired; damage/school contributions mis-gated — MEDIUM-02 |
| `magic` | `MAGIC` | `registry/RegistrySkills.java:41` | `common/capability/SkillCapability.java:242-303`; child perk/passive/Power gates | `network/packet/common/SkillLevelUpSP.java:45-123`; `common/progression/ProgressionService.java:81-160` | Wired |
| `fortune` | `FORTUNE` | `registry/RegistrySkills.java:42` | `common/capability/SkillCapability.java:242-303`; child perk/passive/Power gates | `network/packet/common/SkillLevelUpSP.java:45-123`; `common/progression/ProgressionService.java:81-160` | Wired |
| `tinkering` | `TINKERING` | `registry/RegistrySkills.java:43` | `common/capability/SkillCapability.java:242-303`; child perk/passive/Power gates | `network/packet/common/SkillLevelUpSP.java:45-123`; `common/progression/ProgressionService.java:81-160` | Wired |

### Perks

| Registry ID | Constant | Declared | Effect implementation | Runtime trigger | Audit status |
|---|---|---|---|---|---|
| `one_handed` | `ONE_HANDED` | `registry/RegistryPerks.java:75-82` | `registry/events/TickEventHandler.java:64-65` | `onPlayerTick` | Wired |
| `fighting_spirit` | `FIGHTING_SPIRIT` | `registry/RegistryPerks.java:84-92` | `registry/events/CraftingEventHandler.java:188-189` | `onEntityDrops` | Duration mismatch — MEDIUM-08 |
| `berserker` | `BERSERKER` | `registry/RegistryPerks.java:94-101` | `registry/events/CombatEventHandler.java:282-303` | `onPlayerCriticalHit` | Wired |
| `athletics` | `ATHLETICS` | `registry/RegistryPerks.java:103-110` | `mixin/MixPlayer.java:24-26` | `runicskills$modifyMaxAir` | Wired |
| `turtle_shield` | `TURTLE_SHIELD` | `registry/RegistryPerks.java:112-118` | `mixin/MixShulkerBullet.java:37` | `onHitEntity` | Wired |
| `lion_heart` | `LION_HEART` | `registry/RegistryPerks.java:120-127` | `mixin/MixLivingEntity.java:169-171` | `runicskills$adjustIncomingEffect` | Wired |
| `quick_reposition` | `QUICK_REPOSITION` | `registry/RegistryPerks.java:129-137` | `registry/events/CombatEventHandler.java:917-919` | `onPlayerShootArrow` | Duration mismatch — MEDIUM-08 |
| `stealth_mastery` | `STEALTH_MASTERY` | `registry/RegistryPerks.java:139-148` | `mixin/MixLivingEntity.java:224-226`<br>`registry/events/CombatEventHandler.java:888-889` | `getVisibilityPercent`<br>`onPlayerShootArrow` | Wired |
| `cat_eyes` | `CAT_EYES` | `registry/RegistryPerks.java:150-156` | `registry/events/TickEventHandler.java:74-75` | `onPlayerTick` | Wired |
| `snow_walker` | `SNOW_WALKER` | `registry/RegistryPerks.java:158-164` | `mixin/MixPowderSnowBlock.java:17` | `canEntityWalkOnPowderSnow` | Wired |
| `counter_attack` | `COUNTER_ATTACK` | `registry/RegistryPerks.java:166-174` | `registry/events/CombatEventHandler.java:333-338` | `onAttackEntity` | Duration mismatch — MEDIUM-08 |
| `diamond_skin` | `DIAMOND_SKIN` | `registry/RegistryPerks.java:176-184` | `registry/events/TickEventHandler.java:67-78` | `onPlayerTick` | Wired |
| `scholar` | `SCHOLAR` | `registry/RegistryPerks.java:186-192` | — | — | Inert — HIGH-03 |
| `haggler` | `HAGGLER` | `registry/RegistryPerks.java:194-201` | `mixin/MixVillager.java:54-64` | `runicskills$applyHagglerDiscount` | Wired |
| `alchemy_manipulation` | `ALCHEMY_MANIPULATION` | `registry/RegistryPerks.java:203-210` | `mixin/MixLivingEntity.java:176-178` | `runicskills$adjustIncomingEffect` | Wired |
| `obsidian_smasher` | `OBSIDIAN_SMASHER` | `registry/RegistryPerks.java:213-220` | `registry/events/CombatEventHandler.java:253-254` | `onPlayerMining` | Wired |
| `treasure_hunter` | `TREASURE_HUNTER` | `registry/RegistryPerks.java:222-229` | `registry/events/CraftingEventHandler.java:47-48`<br>`registry/perks/TreasureHunterPerk.java:40` | `onPlayerBreakBlock`<br>`drop` | Wired |
| `convergence` | `CONVERGENCE` | `registry/RegistryPerks.java:231-238` | `registry/events/CraftingEventHandler.java:67-71` | `onPlayerCraft` | Wired |
| `locksmith` | `LOCKSMITH` | `registry/RegistryPerks.java:241-248` | `registry/events/CraftingEventHandler.java:118-140` | `onContainerOpen` | Wired |
| `safe_cracker` | `SAFE_CRACKER` | `registry/RegistryPerks.java:250-257` | `registry/events/CraftingEventHandler.java:142-143` | `onContainerOpen` | Wired |
| `master_tinkerer` | `MASTER_TINKERER` | `registry/RegistryPerks.java:259-266` | `registry/events/CraftingEventHandler.java:80` | `onPlayerCraft` | Wired |
| `enchanters_insight` | `ENCHANTERS_INSIGHT` | `registry/RegistryPerks.java:269-276` | `mixin/MixEnchantmentMenu.java:71-73` | `runicskills$discountOffers` | Wired |
| `lore_mastery` | `LORE_MASTERY` | `registry/RegistryPerks.java:278-285` | `registry/events/CraftingEventHandler.java:165-176` | `onPickupXp` | Wired |
| `safe_port` | `SAFE_PORT` | `registry/RegistryPerks.java:287-293` | `registry/events/CombatEventHandler.java:942` | `onPlayerTeleport` | Wired |
| `life_eater` | `LIFE_EATER` | `registry/RegistryPerks.java:295-302` | `registry/events/CraftingEventHandler.java:196-197` | `onEntityDrops` | Wired |
| `wormhole_storage` | `WORMHOLE_STORAGE` | `registry/RegistryPerks.java:304-310` | `mixin/MixInventoryScreen.java:60`<br>`network/packet/common/OpenEnderChestSP.java:38-46` | `render`<br>`handle` | Wired |
| `critical_roll` | `CRITICAL_ROLL` | `registry/RegistryPerks.java:312-320` | `registry/events/CombatEventHandler.java:301-312` | `onPlayerCriticalHit` | Wired |
| `lucky_drop` | `LUCKY_DROP` | `registry/RegistryPerks.java:322-330` | `registry/events/CraftingEventHandler.java:204-211` | `onEntityDrops` | Wired |
| `limit_breaker` | `LIMIT_BREAKER` | `registry/RegistryPerks.java:332-340` | `registry/events/CombatEventHandler.java:164-174` | `onPlayerAttackEntity` | Wired |
| `mana_efficiency` | `MANA_EFFICIENCY` | `registry/RegistryPerks.java:343-351` | `integration/IronsSpellbooksIntegration.java:127` | `onSpellCast` | Wired |
| `spell_echo` | `SPELL_ECHO` | `registry/RegistryPerks.java:352-360` | `integration/IronsSpellbooksIntegration.java:153` | `onSpellCast` | Wired |
| `arcane_shield` | `ARCANE_SHIELD` | `registry/RegistryPerks.java:361-369` | `integration/IronsSpellbooksIntegration.java:228` | `onSpellDamage` | Wired |
| `wellspring` | `WELLSPRING` | `registry/RegistryPerks.java:372-380` | `integration/IronsSpellbooksIntegration.java:456` | `onPlayerTickPhase1a` | Wired |
| `quickening` | `QUICKENING` | `registry/RegistryPerks.java:382-390` | `integration/IronsSpellbooksIntegration.java:464` | `onPlayerTickPhase1a` | Wired |
| `reservoir` | `RESERVOIR` | `registry/RegistryPerks.java:392-400` | `integration/IronsSpellbooksIntegration.java:472` | `onPlayerTickPhase1a` | Wired |
| `tempo` | `TEMPO` | `registry/RegistryPerks.java:402-410` | `integration/IronsSpellbooksIntegration.java:480` | `onPlayerTickPhase1a` | Wired |
| `arcane_recovery` | `ARCANE_RECOVERY` | `registry/RegistryPerks.java:412-421` | `integration/IronsSpellbooksIntegration.java:669-672` | `onLivingDeath` | Wired |
| `focus` | `FOCUS` | `registry/RegistryPerks.java:423-431` | `integration/IronsSpellbooksIntegration.java:689-691` | `onLivingAttackFocus` | Wired |
| `mana_bulwark` | `MANA_BULWARK` | `registry/RegistryPerks.java:433-442` | `integration/IronsSpellbooksIntegration.java:928-930` | `onLivingHurtBulwark` | Wired |
| `arcane_reprieve` | `ARCANE_REPRIEVE` | `registry/RegistryPerks.java:444-453` | `integration/IronsSpellbooksIntegration.java:333-339` | `onChangeMana` | Broken — HIGH-01; lifecycle debt — MEDIUM-06 |
| `mana_surge` | `MANA_SURGE` | `registry/RegistryPerks.java:455-465` | `integration/IronsSpellbooksIntegration.java:530` | `onPlayerTickPhase1a` | Wired |
| `spellweaver` | `SPELLWEAVER` | `registry/RegistryPerks.java:467-476` | `integration/IronsSpellbooksIntegration.java:135` | `onSpellCast` | Lifecycle debt — MEDIUM-06 |
| `resonant_casting` | `RESONANT_CASTING` | `registry/RegistryPerks.java:478-487` | `integration/IronsSpellbooksIntegration.java:238` | `onSpellDamage` | Wired |
| `imbued_focus` | `IMBUED_FOCUS` | `registry/RegistryPerks.java:489-497` | `integration/IronsSpellbooksIntegration.java:295` | `onModifySpellLevel` | Mis-gated — MEDIUM-02 |
| `quickcast` | `QUICKCAST` | `registry/RegistryPerks.java:499-507` | `integration/IronsSpellbooksIntegration.java:912-915` | `onSpellCooldownQuickcast` | Wired |
| `long_channel` | `LONG_CHANNEL` | `registry/RegistryPerks.java:509-517` | `integration/IronsSpellbooksIntegration.java:252` | `onSpellDamage` | Wired |
| `continuous_flow` | `CONTINUOUS_FLOW` | `registry/RegistryPerks.java:519-527` | `integration/IronsSpellbooksIntegration.java:349` | `onChangeMana` | Broken — HIGH-01 |
| `charge_mastery` | `CHARGE_MASTERY` | `registry/RegistryPerks.java:529-537` | `integration/IronsSpellbooksIntegration.java:263` | `onSpellDamage` | Wired |
| `fire_mancer` | `FIRE_MANCER` | `registry/RegistryPerks.java:541-546` | `integration/IronsSpellbooksIntegration.java:552` | `onPlayerTickPhase1a` | Wired |
| `fire_warded` | `FIRE_WARDED` | `registry/RegistryPerks.java:547-552` | `integration/IronsSpellbooksIntegration.java:554` | `onPlayerTickPhase1a` | Wired |
| `fire_catalyst` | `FIRE_CATALYST` | `registry/RegistryPerks.java:553-559` | `integration/IronsSpellbooksIntegration.java:758` | `onSpellDamageDebuffCatalyst` | Wired |
| `ice_mancer` | `ICE_MANCER` | `registry/RegistryPerks.java:562-567` | `integration/IronsSpellbooksIntegration.java:556` | `onPlayerTickPhase1a` | Wired |
| `ice_warded` | `ICE_WARDED` | `registry/RegistryPerks.java:568-573` | `integration/IronsSpellbooksIntegration.java:558` | `onPlayerTickPhase1a` | Wired |
| `ice_catalyst` | `ICE_CATALYST` | `registry/RegistryPerks.java:574-580` | `integration/IronsSpellbooksIntegration.java:760` | `onSpellDamageDebuffCatalyst` | Wired |
| `lightning_mancer` | `LIGHTNING_MANCER` | `registry/RegistryPerks.java:583-588` | `integration/IronsSpellbooksIntegration.java:560` | `onPlayerTickPhase1a` | Wired |
| `lightning_warded` | `LIGHTNING_WARDED` | `registry/RegistryPerks.java:589-594` | `integration/IronsSpellbooksIntegration.java:562` | `onPlayerTickPhase1a` | Wired |
| `lightning_catalyst` | `LIGHTNING_CATALYST` | `registry/RegistryPerks.java:595-601` | `integration/IronsSpellbooksIntegration.java:722` | `onSpellCastBuffCatalyst` | Wired |
| `holy_mancer` | `HOLY_MANCER` | `registry/RegistryPerks.java:604-609` | `integration/IronsSpellbooksIntegration.java:564` | `onPlayerTickPhase1a` | Wired |
| `holy_warded` | `HOLY_WARDED` | `registry/RegistryPerks.java:610-615` | `integration/IronsSpellbooksIntegration.java:566` | `onPlayerTickPhase1a` | Wired |
| `holy_catalyst` | `HOLY_CATALYST` | `registry/RegistryPerks.java:616-622` | `integration/IronsSpellbooksIntegration.java:724` | `onSpellCastBuffCatalyst` | Wired |
| `ender_mancer` | `ENDER_MANCER` | `registry/RegistryPerks.java:625-630` | `integration/IronsSpellbooksIntegration.java:568` | `onPlayerTickPhase1a` | Wired |
| `ender_warded` | `ENDER_WARDED` | `registry/RegistryPerks.java:631-636` | `integration/IronsSpellbooksIntegration.java:570` | `onPlayerTickPhase1a` | Wired |
| `ender_catalyst` | `ENDER_CATALYST` | `registry/RegistryPerks.java:637-643` | `integration/IronsSpellbooksIntegration.java:726` | `onSpellCastBuffCatalyst` | Wired |
| `blood_mancer` | `BLOOD_MANCER` | `registry/RegistryPerks.java:646-651` | `integration/IronsSpellbooksIntegration.java:572` | `onPlayerTickPhase1a` | Wired |
| `blood_warded` | `BLOOD_WARDED` | `registry/RegistryPerks.java:652-657` | `integration/IronsSpellbooksIntegration.java:574` | `onPlayerTickPhase1a` | Wired |
| `blood_catalyst` | `BLOOD_CATALYST` | `registry/RegistryPerks.java:658-664` | `integration/IronsSpellbooksIntegration.java:762` | `onSpellDamageDebuffCatalyst` | Wired |
| `evocation_mancer` | `EVOCATION_MANCER` | `registry/RegistryPerks.java:667-672` | `integration/IronsSpellbooksIntegration.java:576` | `onPlayerTickPhase1a` | Wired |
| `evocation_warded` | `EVOCATION_WARDED` | `registry/RegistryPerks.java:673-678` | `integration/IronsSpellbooksIntegration.java:578` | `onPlayerTickPhase1a` | Wired |
| `evocation_catalyst` | `EVOCATION_CATALYST` | `registry/RegistryPerks.java:679-685` | `integration/IronsSpellbooksIntegration.java:728` | `onSpellCastBuffCatalyst` | Wired |
| `nature_mancer` | `NATURE_MANCER` | `registry/RegistryPerks.java:688-693` | `integration/IronsSpellbooksIntegration.java:580` | `onPlayerTickPhase1a` | Wired |
| `nature_warded` | `NATURE_WARDED` | `registry/RegistryPerks.java:694-699` | `integration/IronsSpellbooksIntegration.java:582` | `onPlayerTickPhase1a` | Wired |
| `nature_catalyst` | `NATURE_CATALYST` | `registry/RegistryPerks.java:700-706` | `integration/IronsSpellbooksIntegration.java:730` | `onSpellCastBuffCatalyst` | Wired |
| `eldritch_mancer` | `ELDRITCH_MANCER` | `registry/RegistryPerks.java:709-714` | `integration/IronsSpellbooksIntegration.java:584` | `onPlayerTickPhase1a` | Wired |
| `eldritch_warded` | `ELDRITCH_WARDED` | `registry/RegistryPerks.java:715-720` | `integration/IronsSpellbooksIntegration.java:586` | `onPlayerTickPhase1a` | Wired |
| `eldritch_catalyst` | `ELDRITCH_CATALYST` | `registry/RegistryPerks.java:721-727` | `integration/IronsSpellbooksIntegration.java:732` | `onSpellCastBuffCatalyst` | Duration-display mismatch — MEDIUM-08 |
| `lord_of_the_dead` | `LORD_OF_THE_DEAD` | `registry/RegistryPerks.java:730-739` | `integration/IronsSpellbooksIntegration.java:590-591`<br>`integration/IronsSpellbooksIntegration.java:607-613` | `onPlayerTickPhase1a`<br>`onSummonJoinLevel` | Wired |
| `life_leech_bound` | `LIFE_LEECH_BOUND` | `registry/RegistryPerks.java:741-749` | `integration/IronsSpellbooksIntegration.java:641-646` | `onSummonHurt` | Wired |
| `socket_virtuoso` | `SOCKET_VIRTUOSO` | `registry/RegistryPerks.java:752-759` | `integration/ApotheosisIntegration.java:345` | `onGetItemSockets` | Wired |
| `affix_affinity` | `AFFIX_AFFINITY` | `registry/RegistryPerks.java:760-768` | `integration/ApotheosisIntegration.java:578-579`<br>`integration/ApotheosisIntegration.java:617-619` | `onPlayerTickPhase2a`<br>`onLivingHurtAffixAffinity` | Wired |
| `apothic_apprentice` | `APOTHIC_APPRENTICE` | `registry/RegistryPerks.java:771-778` | `integration/ApotheosisIntegration.java:351` | `onGetItemSockets` | Wired |
| `gem_threaded_armor` | `GEM_THREADED_ARMOR` | `registry/RegistryPerks.java:781-788` | `integration/ApotheosisIntegration.java:440-447` | `onEquipChangeGemThreaded` | Wired |
| `spellsocket` | `SPELLSOCKET` | `registry/RegistryPerks.java:791-799` | `integration/IronsSpellbooksIntegration.java:864-867` | `onModifySpellLevelSpellsocket` | Wired |
| `resonant_affixes` | `RESONANT_AFFIXES` | `registry/RegistryPerks.java:802-809` | `integration/IronsSpellbooksIntegration.java:889-893` | `onSpellDamageResonantAffixes` | Wired |
| `apothic_critical_mastery` | `APOTHIC_CRITICAL_MASTERY` | `registry/RegistryPerks.java:810-818` | `integration/ApothicAttributesPerksIntegration.java:99-100` | `onPlayerTickApothicAttributes` | Wired |
| `vampiric_fangs` | `VAMPIRIC_FANGS` | `registry/RegistryPerks.java:819-826` | `integration/ApothicAttributesPerksIntegration.java:109` | `onPlayerTickApothicAttributes` | Wired |
| `reapers_edge` | `REAPERS_EDGE` | `registry/RegistryPerks.java:827-834` | `integration/ApothicAttributesPerksIntegration.java:114` | `onPlayerTickApothicAttributes` | Wired |
| `evasive` | `EVASIVE` | `registry/RegistryPerks.java:835-842` | `integration/ApothicAttributesPerksIntegration.java:119` | `onPlayerTickApothicAttributes` | Wired |
| `arrow_mastery` | `ARROW_MASTERY` | `registry/RegistryPerks.java:843-851` | `integration/ApothicAttributesPerksIntegration.java:124-125` | `onPlayerTickApothicAttributes` | Wired |
| `earthbreaker` | `EARTHBREAKER` | `registry/RegistryPerks.java:852-859` | `integration/ApothicAttributesPerksIntegration.java:134` | `onPlayerTickApothicAttributes` | Wired |
| `apothic_scholar` | `APOTHIC_SCHOLAR` | `registry/RegistryPerks.java:860-867` | `integration/ApothicAttributesPerksIntegration.java:139` | `onPlayerTickApothicAttributes` | Wired |
| `spectral_ward` | `SPECTRAL_WARD` | `registry/RegistryPerks.java:868-876` | `integration/ApothicAttributesPerksIntegration.java:144-145` | `onPlayerTickApothicAttributes` | Wired |
| `ghostbound` | `GHOSTBOUND` | `registry/RegistryPerks.java:877-884` | `integration/ApothicAttributesPerksIntegration.java:154` | `onPlayerTickApothicAttributes` | Wired |
| `heart_of_the_healer` | `HEART_OF_THE_HEALER` | `registry/RegistryPerks.java:885-893` | `integration/ApothicAttributesPerksIntegration.java:159-160` | `onPlayerTickApothicAttributes` | Wired |
| `ars_form_projectile` | `ARS_FORM_PROJECTILE` | `registry/RegistryPerks.java:896-903` | `integration/ArsNouveauIntegration.java:268-269` | `onSpellCostCalc` | Wired |
| `ars_form_touch` | `ARS_FORM_TOUCH` | `registry/RegistryPerks.java:904-911` | `integration/ArsNouveauIntegration.java:121-122` | `onSpellDamage` | Wired |
| `ars_form_self` | `ARS_FORM_SELF` | `registry/RegistryPerks.java:912-919` | `integration/ArsNouveauIntegration.java:276-277` | `onSpellCostCalc` | Wired |
| `ars_wild_manipulation` | `ARS_WILD_MANIPULATION` | `registry/RegistryPerks.java:920-927` | `integration/ArsNouveauIntegration.java:286-287` | `onSpellCostCalc` | Wired |
| `ars_hedgewitch` | `ARS_HEDGEWITCH` | `registry/RegistryPerks.java:930-938` | `integration/ArsNouveauIntegration.java:137-141`<br>`integration/ArsNouveauIntegration.java:295-298` | `onSpellDamage`<br>`onSpellCostCalc` | Wired |
| `ars_emberforged` | `ARS_EMBERFORGED` | `registry/RegistryPerks.java:939-946` | `integration/ArsNouveauIntegration.java:143` | `onSpellDamage` | Wired |
| `ars_stormcaller` | `ARS_STORMCALLER` | `registry/RegistryPerks.java:947-954` | `integration/ArsNouveauIntegration.java:145` | `onSpellDamage` | Wired |
| `ars_geomancer` | `ARS_GEOMANCER` | `registry/RegistryPerks.java:955-962` | `integration/ArsNouveauIntegration.java:147` | `onSpellDamage` | Wired |
| `ars_conjurer` | `ARS_CONJURER` | `registry/RegistryPerks.java:963-970` | `integration/ArsNouveauIntegration.java:302-303` | `onSpellCostCalc` | Wired |
| `ars_abjurer` | `ARS_ABJURER` | `registry/RegistryPerks.java:971-978` | `integration/ArsNouveauIntegration.java:149` | `onSpellDamage` | Wired |
| `ars_arcane_weaver` | `ARS_ARCANE_WEAVER` | `registry/RegistryPerks.java:979-986` | `integration/ArsNouveauIntegration.java:151` | `onSpellDamage` | Wired |
| `schoolbridge_fire` | `SCHOOLBRIDGE_FIRE` | `registry/RegistryPerks.java:990-997` | `integration/ArsNouveauIntegration.java:184` | `onSpellDamageSchoolbridge` | Wired |
| `schoolbridge_water` | `SCHOOLBRIDGE_WATER` | `registry/RegistryPerks.java:998-1005` | `integration/ArsNouveauIntegration.java:188` | `onSpellDamageSchoolbridge` | Wired |
| `schoolbridge_air` | `SCHOOLBRIDGE_AIR` | `registry/RegistryPerks.java:1006-1013` | `integration/ArsNouveauIntegration.java:192` | `onSpellDamageSchoolbridge` | Wired |
| `schoolbridge_earth` | `SCHOOLBRIDGE_EARTH` | `registry/RegistryPerks.java:1014-1021` | `integration/ArsNouveauIntegration.java:196` | `onSpellDamageSchoolbridge` | Wired |
| `schoolbridge_abjuration` | `SCHOOLBRIDGE_ABJ` | `registry/RegistryPerks.java:1022-1029` | `integration/ArsNouveauIntegration.java:200` | `onSpellDamageSchoolbridge` | Wired |
| `schoolbridge_manipulation` | `SCHOOLBRIDGE_MANIP` | `registry/RegistryPerks.java:1030-1037` | `integration/ArsNouveauIntegration.java:204` | `onSpellDamageSchoolbridge` | Wired |
| `unified_arcana` | `UNIFIED_ARCANA` | `registry/RegistryPerks.java:1038-1045` | `integration/ArsNouveauIntegration.java:234-235` | `onSpellResolveUnifiedArcana` | Wired |
| `triple_threat` | `TRIPLE_THREAT` | `registry/RegistryPerks.java:1046-1054` | `integration/IronsSpellbooksIntegration.java:814-815` | `onPlayerTickPhase3` | Wired |
| `affix_focus` | `AFFIX_FOCUS` | `registry/RegistryPerks.java:1055-1063` | `integration/IronsSpellbooksIntegration.java:837-840` | `onModifySpellLevelAffixFocus` | Wired |
| `arcane_efficiency` | `ARCANE_EFFICIENCY` | `registry/RegistryPerks.java:1066-1074` | `integration/ArsNouveauIntegration.java:254` | `onSpellCostCalc` | Wired |
| `glyph_mastery` | `GLYPH_MASTERY` | `registry/RegistryPerks.java:1075-1083` | `integration/ArsNouveauIntegration.java:433` | `onSpellModifier` | Wired |
| `arcane_ward` | `ARCANE_WARD` | `registry/RegistryPerks.java:1084-1092` | `integration/ArsNouveauIntegration.java:113` | `onSpellDamage` | Wired |
| `dragon_slayer` | `DRAGON_SLAYER` | `registry/RegistryPerks.java:1096-1104` | `integration/IceAndFireIntegration.java:265` | `onLivingHurt` | Wired |
| `beast_tamer` | `BEAST_TAMER` | `registry/RegistryPerks.java:1105-1113` | `integration/IceAndFireIntegration.java:345` | `onTameAttempt` | Wired |
| `mythic_fortitude` | `MYTHIC_FORTITUDE` | `registry/RegistryPerks.java:1114-1122` | `integration/IceAndFireIntegration.java:278` | `onLivingHurt` | Wired |
| `cataclysm_resistance` | `CATACLYSM_RESISTANCE` | `registry/RegistryPerks.java:1125-1133` | `integration/CataclysmIntegration.java:43` | `onLivingHurt` | Wired |
| `boss_hunter` | `BOSS_HUNTER` | `registry/RegistryPerks.java:1137-1145` | `integration/MowziesMobsIntegration.java:70` | `onLivingHurt` | Wired |
| `master_chef` | `MASTER_CHEF` | `registry/RegistryPerks.java:1148-1156` | `integration/CulinaryIntegration.java:90` | `onItemUseFinish` | Wired |
| `green_thumb` | `GREEN_THUMB` | `registry/RegistryPerks.java:1159-1166` | `registry/events/PerkEffectsHandler.java:1237` | `onBonemeal` | Wired |
| `nourishing_meal` | `NOURISHING_MEAL` | `registry/RegistryPerks.java:1167-1175` | `integration/CulinaryIntegration.java:113` | `onItemUseFinish` | Wired |
| `comfort_food` | `COMFORT_FOOD` | `registry/RegistryPerks.java:1176-1184` | `integration/CulinaryIntegration.java:123` | `onItemUseFinish` | Wired |
| `angler_luck` | `ANGLER_LUCK` | `registry/RegistryPerks.java:1187-1195` | `integration/StarcatcherIntegration.java:73` | `onItemFished` | Wired |
| `catch_of_the_day` | `CATCH_OF_THE_DAY` | `registry/RegistryPerks.java:1196-1204` | `integration/StarcatcherIntegration.java:79` | `onItemFished` | Wired |
| `anglers_insight` | `ANGLERS_INSIGHT` | `registry/RegistryPerks.java:1205-1213` | `integration/StarcatcherIntegration.java:89` | `onItemFished` | Wired |
| `steady_hammer` | `STEADY_HAMMER` | `registry/RegistryPerks.java:1216-1224` | `integration/OvergearedIntegration.java:77` | `onItemCrafted` | Wired |
| `blueprint_savant` | `BLUEPRINT_SAVANT` | `registry/RegistryPerks.java:1225-1233` | `integration/OvergearedIntegration.java:93` | `onItemCrafted` | Wired |
| `metallurgist` | `METALLURGIST` | `registry/RegistryPerks.java:1234-1242` | `integration/OvergearedIntegration.java:117` | `onItemSmelted` | Wired |
| `master_smith` | `MASTER_SMITH` | `registry/RegistryPerks.java:1243-1251` | `integration/OvergearedIntegration.java:84` | `onItemCrafted` | Wired |
| `runic_salvager` | `RUNIC_SALVAGER` | `registry/RegistryPerks.java:1254-1263` | `mixin/MixSalvagingMenu.java:37-38` | `runicskills$bonusSalvage` | Wired |
| `gem_attunement` | `GEM_ATTUNEMENT` | `registry/RegistryPerks.java:1264-1272` | `integration/ApotheosisIntegration.java:513-526` | `onItemSocketing` | Wrong-player risk — HIGH-02 |
| `arcane_reforging` | `ARCANE_REFORGING` | `registry/RegistryPerks.java:1273-1281` | `mixin/MixReforgingResultSlot.java:37-38` | `runicskills$upgradeRarity` | Wired |
| `armor_piercing` | `ARMOR_PIERCING` | `registry/RegistryPerks.java:1284-1291` | `registry/events/CombatEventHandler.java:391-394` | `onLivingHurtStrengthAttacker` | Wired |
| `heavy_strikes` | `HEAVY_STRIKES` | `registry/RegistryPerks.java:1292-1299` | `registry/events/CombatEventHandler.java:402-404` | `onLivingHurtStrengthAttacker` | Wired |
| `cleave` | `CLEAVE` | `registry/RegistryPerks.java:1300-1306` | `registry/events/CombatEventHandler.java:574` | `onLivingHurtStrengthAttacker` | Wired |
| `titans_grip` | `TITANS_GRIP` | `registry/RegistryPerks.java:1307-1314` | `registry/events/CombatEventHandler.java:559` | `onLivingHurtStrengthAttacker` | Wired |
| `samurais_edge` | `SAMURAIS_EDGE` | `registry/RegistryPerks.java:1315-1323` | `integration/SamuraiDynastyIntegration.java:68-71` | `onLivingHurt` | Wired |
| `brutal_swing` | `BRUTAL_SWING` | `registry/RegistryPerks.java:1324-1332` | `registry/events/PerkEffectsHandler.java:1078` | `onOutgoingDamage` | Wired |
| `polearm_mastery` | `POLEARM_MASTERY` | `registry/RegistryPerks.java:1333-1341` | `registry/events/PerkEffectsHandler.java:1081` | `onOutgoingDamage` | Wired |
| `warmonger` | `WARMONGER` | `registry/RegistryPerks.java:1342-1349` | `registry/events/CombatEventHandler.java:411-413` | `onLivingHurtStrengthAttacker` | Wired |
| `execute` | `EXECUTE` | `registry/RegistryPerks.java:1350-1357` | `registry/events/CombatEventHandler.java:421-424` | `onLivingHurtStrengthAttacker` | Wired |
| `bloodlust` | `BLOODLUST` | `registry/RegistryPerks.java:1358-1365` | `registry/events/PerkEffectsHandler.java:369`<br>`registry/events/PerkEffectsHandler.java:1420` | `attrs`<br>`onDeath` | Respawn clock bug — MEDIUM-05 |
| `dragon_bone_mastery` | `DRAGON_BONE_MASTERY` | `registry/RegistryPerks.java:1366-1374` | `registry/events/PerkEffectsHandler.java:1080` | `onOutgoingDamage` | Wired |
| `nichirin_blade` | `NICHIRIN_BLADE` | `registry/RegistryPerks.java:1375-1383` | `integration/NichirinDynastyIntegration.java:51-53` | `onLivingHurt` | Wired |
| `siege_breaker` | `SIEGE_BREAKER` | `registry/RegistryPerks.java:1384-1392` | `registry/events/PerkEffectsHandler.java:1077` | `onOutgoingDamage` | Wired |
| `mowzies_might` | `MOWZIES_MIGHT` | `registry/RegistryPerks.java:1393-1401` | `integration/MowziesMobsIntegration.java:79` | `onLivingHurt` | Wired |
| `spartans_discipline` | `SPARTANS_DISCIPLINE` | `registry/RegistryPerks.java:1402-1410` | `registry/events/CombatEventHandler.java:493-497` | `onLivingHurtStrengthAttacker` | Wired |
| `power_attack` | `POWER_ATTACK` | `registry/RegistryPerks.java:1411-1418` | `registry/events/CombatEventHandler.java:295-296` | `onPlayerCriticalHit` | Wired |
| `unstoppable_force` | `UNSTOPPABLE_FORCE` | `registry/RegistryPerks.java:1419-1426` | `registry/events/CombatEventHandler.java:513-514` | `onLivingHurtStrengthAttacker` | Wired |
| `primal_fury` | `PRIMAL_FURY` | `registry/RegistryPerks.java:1427-1434` | `registry/events/CombatEventHandler.java:481-484` | `onLivingHurtStrengthAttacker` | Wired |
| `vengeance` | `VENGEANCE` | `registry/RegistryPerks.java:1435-1442` | `registry/events/CombatEventHandler.java:439-445`<br>`registry/events/CombatEventHandler.java:689` | `onLivingHurtStrengthAttacker`<br>`onLivingHurtStrengthVictim` | Wired |
| `last_stand` | `LAST_STAND` | `registry/RegistryPerks.java:1443-1450` | `registry/events/CombatEventHandler.java:469-472`<br>`registry/events/CombatEventHandler.java:700-710` | `onLivingHurtStrengthAttacker`<br>`onLivingHurtStrengthVictim` | Wired |
| `warlords_presence` | `WARLORDS_PRESENCE` | `registry/RegistryPerks.java:1451-1458` | `registry/events/PerkEffectsHandler.java:1070` | `onOutgoingDamage` | Wired |
| `chain_lightning_strike` | `CHAIN_LIGHTNING_STRIKE` | `registry/RegistryPerks.java:1459-1467` | `registry/events/PerkEffectsHandler.java:1164` | `onOutgoingDamage` | Wired |
| `blade_storm` | `BLADE_STORM` | `registry/RegistryPerks.java:1468-1475` | `registry/events/CombatEventHandler.java:454-461` | `onLivingHurtStrengthAttacker` | Wired |
| `devastating_blow` | `DEVASTATING_BLOW` | `registry/RegistryPerks.java:1476-1483` | `registry/events/CombatEventHandler.java:431-433` | `onLivingHurtStrengthAttacker` | Wired |
| `sacred_fire` | `SACRED_FIRE` | `registry/RegistryPerks.java:1484-1490` | `registry/events/CombatEventHandler.java:505` | `onLivingHurtStrengthAttacker` | Wired |
| `blood_fury` | `BLOOD_FURY` | `registry/RegistryPerks.java:1491-1498` | `registry/events/PerkEffectsHandler.java:1156` | `onOutgoingDamage` | Respawn clock-marker bug — MEDIUM-05 |
| `cataclysms_wrath` | `CATACLYSMS_WRATH` | `registry/RegistryPerks.java:1499-1507` | `registry/events/PerkEffectsHandler.java:1079` | `onOutgoingDamage` | Wired |
| `gladiator` | `GLADIATOR` | `registry/RegistryPerks.java:1508-1515` | `registry/events/CombatEventHandler.java:538-540` | `onLivingHurtStrengthAttacker` | Wired |
| `trophy_hunter` | `TROPHY_HUNTER` | `registry/RegistryPerks.java:1516-1523` | `registry/events/CombatEventHandler.java:529-531` | `onLivingHurtStrengthAttacker` | Wired |
| `draconic_fury` | `DRACONIC_FURY` | `registry/RegistryPerks.java:1524-1532` | `integration/SaintsDragonsIntegration.java:64-67` | `onLivingHurt` | Wired |
| `mythical_berserker` | `MYTHICAL_BERSERKER` | `registry/RegistryPerks.java:1533-1541` | `registry/events/PerkEffectsHandler.java:1090`<br>`registry/events/PerkEffectsHandler.java:1434` | `onOutgoingDamage`<br>`onDeath` | Respawn clock bug — MEDIUM-05 |
| `stalwart_striker` | `STALWART_STRIKER` | `registry/RegistryPerks.java:1542-1550` | `registry/events/PerkEffectsHandler.java:1424-1427` | `onDeath` | Wired |
| `weapon_master` | `WEAPON_MASTER` | `registry/RegistryPerks.java:1551-1558` | `registry/events/CombatEventHandler.java:376-381` | `onLivingHurtStrengthAttacker` | Wired |
| `runic_might` | `RUNIC_MIGHT` | `registry/RegistryPerks.java:1559-1566` | `registry/events/CombatEventHandler.java:546-549` | `onLivingHurtStrengthAttacker` | Wired |
| `iron_stomach` | `IRON_STOMACH` | `registry/RegistryPerks.java:1569-1575` | `registry/events/PerkEffectsHandler.java:1216` | `onFinishEating` | Wired |
| `second_wind` | `SECOND_WIND` | `registry/RegistryPerks.java:1576-1583` | `registry/events/PerkEffectsHandler.java:744-745` | `onAttributeTick` | Wired |
| `vitality` | `VITALITY` | `registry/RegistryPerks.java:1584-1591` | `registry/events/PerkEffectsHandler.java:355` | `attrs` | Wired |
| `natural_recovery` | `NATURAL_RECOVERY` | `registry/RegistryPerks.java:1592-1599` | `registry/events/PerkEffectsHandler.java:735` | `onAttributeTick` | Wired |
| `thick_skin` | `THICK_SKIN` | `registry/RegistryPerks.java:1600-1607` | `registry/events/PerkEffectsHandler.java:271` | `onIncomingDamage` | Wired |
| `poison_immunity` | `POISON_IMMUNITY` | `registry/RegistryPerks.java:1608-1614` | `registry/events/PerkEffectsHandler.java:1456` | `onEffectApplicable` | Wired |
| `fire_resistance` | `FIRE_RESISTANCE` | `registry/RegistryPerks.java:1615-1622` | `registry/events/PerkEffectsHandler.java:133` | `Reduce` | Wired |
| `draconic_constitution` | `DRACONIC_CONSTITUTION` | `registry/RegistryPerks.java:1623-1631` | `registry/events/PerkEffectsHandler.java:156` | `Reduce` | Wired |
| `culinary_expert` | `CULINARY_EXPERT` | `registry/RegistryPerks.java:1632-1640` | `registry/events/PerkEffectsHandler.java:1203` | `onFinishEating` | Wired |
| `anglers_bounty` | `ANGLERS_BOUNTY` | `registry/RegistryPerks.java:1641-1648` | `registry/events/PerkEffectsHandler.java:1199` | `onFinishEating` | Wired |
| `searing_resistance` | `SEARING_RESISTANCE` | `registry/RegistryPerks.java:1649-1656` | `registry/events/CombatEventHandler.java:736` | `onLivingHurtConstitutionDefense` | Wired |
| `wither_resistance` | `WITHER_RESISTANCE` | `registry/RegistryPerks.java:1657-1664` | `registry/events/CombatEventHandler.java:741` | `onLivingHurtConstitutionDefense` | Wired |
| `undying_will` | `UNDYING_WILL` | `registry/RegistryPerks.java:1665-1672` | `registry/events/PerkEffectsHandler.java:1437` | `onDeath` | Respawn clock bug — MEDIUM-05 |
| `hearty_feast` | `HEARTY_FEAST` | `registry/RegistryPerks.java:1673-1681` | `registry/events/PerkEffectsHandler.java:467` | `onFoodEffectApplied` | Wired |
| `dragon_heart` | `DRAGON_HEART` | `registry/RegistryPerks.java:1682-1690` | `registry/events/PerkEffectsHandler.java:1221-1222` | `onFinishEating` | Wired |
| `swimmers_endurance` | `SWIMMERS_ENDURANCE` | `registry/RegistryPerks.java:1691-1698` | `registry/events/PerkEffectsHandler.java:364` | `attrs` | Wired |
| `explorers_vigor` | `EXPLORERS_VIGOR` | `registry/RegistryPerks.java:1699-1707` | `registry/events/PerkEffectsHandler.java:160` | `Reduce` | Wired |
| `battle_recovery` | `BATTLE_RECOVERY` | `registry/RegistryPerks.java:1708-1715` | `registry/events/PerkEffectsHandler.java:746-749` | `onAttributeTick` | Respawn clock bug — MEDIUM-05 |
| `armor_of_faith` | `ARMOR_OF_FAITH` | `registry/RegistryPerks.java:1716-1723` | `registry/events/CombatEventHandler.java:746` | `onLivingHurtConstitutionDefense` | Wired |
| `soul_sustenance` | `SOUL_SUSTENANCE` | `registry/RegistryPerks.java:1724-1731` | `registry/events/PerkEffectsHandler.java:813-814` | `onXpPickup` | Wired |
| `colonial_nourishment` | `COLONIAL_NOURISHMENT` | `registry/RegistryPerks.java:1732-1739` | `registry/events/PerkEffectsHandler.java:1209` | `onFinishEating` | Wired |
| `obsidian_heart` | `OBSIDIAN_HEART` | `registry/RegistryPerks.java:1740-1747` | `registry/events/CombatEventHandler.java:764` | `onLivingHurtConstitutionDefense` | Wired |
| `potion_mastery` | `POTION_MASTERY` | `registry/RegistryPerks.java:1748-1755` | `registry/events/PerkEffectsHandler.java:380` | `attrs` | Wired |
| `phoenix_rising` | `PHOENIX_RISING` | `registry/RegistryPerks.java:1756-1763` | `registry/events/PerkEffectsHandler.java:1466` | `onRespawn` | Wired |
| `natures_blessing` | `NATURES_BLESSING` | `registry/RegistryPerks.java:1764-1771` | `registry/events/PerkEffectsHandler.java:755` | `onAttributeTick` | Wired |
| `runic_fortification` | `RUNIC_FORTIFICATION` | `registry/RegistryPerks.java:1772-1779` | `registry/events/CombatEventHandler.java:756` | `onLivingHurtConstitutionDefense` | Wired |
| `gourmet` | `GOURMET` | `registry/RegistryPerks.java:1780-1787` | `registry/events/PerkEffectsHandler.java:1196` | `onFinishEating` | Wired |
| `frost_walker_constitution` | `FROST_WALKER_CONSTITUTION` | `registry/RegistryPerks.java:1788-1795` | `registry/events/CombatEventHandler.java:773` | `onLivingHurtConstitutionDefense` | Wired |
| `myrmex_carapace` | `MYRMEX_CARAPACE` | `registry/RegistryPerks.java:1796-1804` | `registry/events/PerkEffectsHandler.java:349` | `attrs` | Wired |
| `enderium_resilience` | `ENDERIUM_RESILIENCE` | `registry/RegistryPerks.java:1805-1812` | `registry/events/PerkEffectsHandler.java:138` | `Reduce` | Wired |
| `survival_instinct` | `SURVIVAL_INSTINCT` | `registry/RegistryPerks.java:1813-1820` | `registry/events/CombatEventHandler.java:751` | `onLivingHurtConstitutionDefense` | Wired |
| `eagle_eye` | `EAGLE_EYE` | `registry/RegistryPerks.java:1823-1830` | `registry/events/CombatEventHandler.java:904` | `onPlayerShootArrow` | Wired |
| `rapid_fire` | `RAPID_FIRE` | `registry/RegistryPerks.java:1831-1838` | `registry/events/PerkEffectsHandler.java:1344` | `onItemUseTick` | Wired |
| `multishot_mastery` | `MULTISHOT_MASTERY` | `registry/RegistryPerks.java:1839-1846` | `registry/events/PerkEffectsHandler.java:1372` | `onArrowSpawn` | Wired |
| `arrow_recovery` | `ARROW_RECOVERY` | `registry/RegistryPerks.java:1847-1854` | `registry/events/PerkEffectsHandler.java:845` | `onMobDrops` | Wired |
| `acrobat` | `ACROBAT` | `registry/RegistryPerks.java:1855-1862` | `registry/events/PerkEffectsHandler.java:132` | `Reduce` | Wired |
| `dodge_roll` | `DODGE_ROLL` | `registry/RegistryPerks.java:1863-1870` | `registry/events/PerkEffectsHandler.java:194` | `onIncomingDamage` | Wired |
| `sprint_master` | `SPRINT_MASTER` | `registry/RegistryPerks.java:1871-1878` | `registry/events/PerkEffectsHandler.java:339` | `attrs` | Wired |
| `silent_step` | `SILENT_STEP` | `registry/RegistryPerks.java:1879-1886` | `registry/events/StealthPerkHandler.java:34` | `onMobPicksTarget` | Wired |
| `precision_shot` | `PRECISION_SHOT` | `registry/RegistryPerks.java:1887-1894` | `registry/events/PerkEffectsHandler.java:1147` | `onOutgoingDamage` | Wired |
| `archery_expansion` | `ARCHERY_EXPANSION` | `registry/RegistryPerks.java:1895-1902` | `registry/events/PerkEffectsHandler.java:1146` | `onOutgoingDamage` | Wired |
| `crossbow_expert` | `CROSSBOW_EXPERT` | `registry/RegistryPerks.java:1903-1910` | `registry/events/PerkEffectsHandler.java:1347` | `onItemUseTick` | Wired |
| `spartan_marksmanship` | `SPARTAN_MARKSMANSHIP` | `registry/RegistryPerks.java:1911-1919` | `registry/events/PerkEffectsHandler.java:1082` | `onOutgoingDamage` | Wired |
| `poison_arrow` | `POISON_ARROW` | `registry/RegistryPerks.java:1920-1927` | `registry/events/PerkEffectsHandler.java:870` | `onProjectileImpact` | Wired |
| `wind_runner` | `WIND_RUNNER` | `registry/RegistryPerks.java:1928-1935` | `registry/events/PerkEffectsHandler.java:341` | `attrs` | Wired |
| `ninja_training` | `NINJA_TRAINING` | `registry/RegistryPerks.java:1936-1944` | `integration/SamuraiDynastyIntegration.java:96-97` | `onMobPicksTarget` | Wired |
| `parkour_master` | `PARKOUR_MASTER` | `registry/RegistryPerks.java:1945-1952` | `mixin/MixPlayerAction.java:45-46` | `runicskills$cheaperParkour` | Wired |
| `sharpshooter` | `SHARPSHOOTER` | `registry/RegistryPerks.java:1954-1961` | `registry/events/PerkEffectsHandler.java:1144` | `onOutgoingDamage` | Wired |
| `evasion` | `EVASION` | `registry/RegistryPerks.java:1962-1969` | `registry/events/PerkEffectsHandler.java:195` | `onIncomingDamage` | Wired |
| `fleet_footed` | `FLEET_FOOTED` | `registry/RegistryPerks.java:1970-1977` | `registry/events/PerkEffectsHandler.java:365` | `attrs` | Wired |
| `ambush` | `AMBUSH` | `registry/RegistryPerks.java:1978-1985` | `registry/events/PerkEffectsHandler.java:1074` | `onOutgoingDamage` | Wired |
| `quick_draw` | `QUICK_DRAW` | `registry/RegistryPerks.java:1986-1993` | `mixin/MixPlayerAction.java:75` | `runicskills$fasterDraw` | Wired |
| `ricochet` | `RICOCHET` | `registry/RegistryPerks.java:1994-2001` | `registry/events/PerkEffectsHandler.java:876` | `onProjectileImpact` | Wired |
| `phantom_strike` | `PHANTOM_STRIKE` | `registry/RegistryPerks.java:2002-2009` | `registry/events/PerkEffectsHandler.java:1084` | `onOutgoingDamage` | Respawn clock bug — MEDIUM-05 |
| `dragon_rider` | `DRAGON_RIDER` | `registry/RegistryPerks.java:2010-2018` | `registry/events/StationPerkHandler.java:62-68` | `onRiderTick` | Wired |
| `ice_arrows` | `ICE_ARROWS` | `registry/RegistryPerks.java:2019-2026` | `registry/events/PerkEffectsHandler.java:868` | `onProjectileImpact` | Wired |
| `spell_dodge` | `SPELL_DODGE` | `registry/RegistryPerks.java:2027-2035` | `registry/events/PerkEffectsHandler.java:196` | `onIncomingDamage` | Wired |
| `zipline_expert` | `ZIPLINE_EXPERT` | `registry/RegistryPerks.java:2036-2043` | `mixin/MixAbstractMinecart.java:34-35` | `runicskills$fasterRails` | Wired |
| `sniper` | `SNIPER` | `registry/RegistryPerks.java:2044-2051` | `registry/events/CombatEventHandler.java:898` | `onPlayerShootArrow` | Wired |
| `smoke_bomb` | `SMOKE_BOMB` | `registry/RegistryPerks.java:2052-2059` | `registry/events/PerkEffectsHandler.java:275` | `onIncomingDamage` | Wired |
| `mounted_combat` | `MOUNTED_COMBAT` | `registry/RegistryPerks.java:2060-2067` | `registry/events/PerkEffectsHandler.java:1076` | `onOutgoingDamage` | Wired |
| `tracking` | `TRACKING` | `registry/RegistryPerks.java:2068-2074` | `registry/events/PerkEffectsHandler.java:1161` | `onOutgoingDamage` | Wired |
| `wind_walker` | `WIND_WALKER` | `registry/RegistryPerks.java:2075-2082` | `registry/events/PerkEffectsHandler.java:340` | `attrs` | Wired |
| `trick_shot` | `TRICK_SHOT` | `registry/RegistryPerks.java:2083-2089` | `registry/events/PerkEffectsHandler.java:873` | `onProjectileImpact` | Wired |
| `blade_dancer` | `BLADE_DANCER` | `registry/RegistryPerks.java:2090-2097` | `registry/events/PerkEffectsHandler.java:352` | `attrs` | Wired |
| `silent_kill` | `SILENT_KILL` | `registry/RegistryPerks.java:2098-2105` | `registry/events/StealthPerkHandler.java:59` | `onStealthKill` | Wired |
| `agile_climber` | `AGILE_CLIMBER` | `registry/RegistryPerks.java:2106-2113` | `registry/events/PerkEffectsHandler.java:343` | `attrs` | Wired |
| `shield_wall` | `SHIELD_WALL` | `registry/RegistryPerks.java:2116-2123` | `registry/events/PerkEffectsHandler.java:248` | `onIncomingDamage` | Wired |
| `heavy_armor_mastery` | `HEAVY_ARMOR_MASTERY` | `registry/RegistryPerks.java:2124-2131` | `registry/events/PerkEffectsHandler.java:345` | `attrs` | Wired |
| `steadfast` | `STEADFAST` | `registry/RegistryPerks.java:2132-2139` | `registry/events/PerkEffectsHandler.java:294` | `onKnockback` | Wired |
| `toughened_hide` | `TOUGHENED_HIDE` | `registry/RegistryPerks.java:2140-2147` | `registry/events/PerkEffectsHandler.java:346` | `attrs` | Wired |
| `fire_proof` | `FIRE_PROOF` | `registry/RegistryPerks.java:2148-2155` | `registry/events/PerkEffectsHandler.java:706` | `onAttributeTick` | Wired |
| `blast_resistance` | `BLAST_RESISTANCE` | `registry/RegistryPerks.java:2156-2163` | `registry/events/CombatEventHandler.java:760` | `onLivingHurtConstitutionDefense` | Wired |
| `warding_rune` | `WARDING_RUNE` | `registry/RegistryPerks.java:2164-2171` | `registry/events/CombatEventHandler.java:783` | `onLivingHurtConstitutionDefense` | Wired |
| `dragon_scale_armor` | `DRAGON_SCALE_ARMOR` | `registry/RegistryPerks.java:2172-2180` | `registry/events/PerkEffectsHandler.java:347` | `attrs` | Wired |
| `bulwark` | `BULWARK` | `registry/RegistryPerks.java:2181-2188` | `registry/events/PerkEffectsHandler.java:282-285` | `onIncomingDamage` | Wired |
| `stoneflesh` | `STONEFLESH` | `registry/RegistryPerks.java:2189-2196` | `registry/events/PerkEffectsHandler.java:238` | `onIncomingDamage` | Wired |
| `poison_resistance` | `POISON_RESISTANCE` | `registry/RegistryPerks.java:2197-2204` | `registry/events/PerkEffectsHandler.java:151` | `Reduce` | Wired |
| `thorns_mastery` | `THORNS_MASTERY` | `registry/RegistryPerks.java:2205-2212` | `registry/events/PerkEffectsHandler.java:1135` | `onOutgoingDamage` | Wired |
| `sentinel` | `SENTINEL` | `registry/RegistryPerks.java:2213-2220` | `registry/events/CombatEventHandler.java:793` | `onLivingHurtConstitutionDefense` | Wired |
| `dragonhide` | `DRAGONHIDE` | `registry/RegistryPerks.java:2221-2229` | `registry/events/PerkEffectsHandler.java:155` | `Reduce` | Wired |
| `fantasy_fortitude` | `FANTASY_FORTITUDE` | `registry/RegistryPerks.java:2230-2238` | `registry/events/PerkEffectsHandler.java:348` | `attrs` | Wired |
| `colony_guardian` | `COLONY_GUARDIAN` | `registry/RegistryPerks.java:2239-2246` | `registry/events/PerkEffectsHandler.java:172` | `Reduce` | Wired |
| `frost_endurance` | `FROST_ENDURANCE` | `registry/RegistryPerks.java:2247-2254` | `registry/events/CombatEventHandler.java:769` | `onLivingHurtConstitutionDefense` | Wired |
| `obsidian_skin` | `OBSIDIAN_SKIN` | `registry/RegistryPerks.java:2255-2262` | `registry/events/PerkEffectsHandler.java:211` | `onIncomingDamage` | Wired |
| `lightning_rod` | `LIGHTNING_ROD` | `registry/RegistryPerks.java:2263-2270` | `registry/events/CombatEventHandler.java:778` | `onLivingHurtConstitutionDefense` | Wired |
| `samurai_resolve` | `SAMURAI_RESOLVE` | `registry/RegistryPerks.java:2271-2279` | `registry/events/PerkEffectsHandler.java:245` | `onIncomingDamage` | Respawn clock bug — MEDIUM-05 |
| `dungeon_resilience` | `DUNGEON_RESILIENCE` | `registry/RegistryPerks.java:2280-2288` | `registry/events/PerkEffectsHandler.java:163` | `Reduce` | Wired |
| `prismarine_shield` | `PRISMARINE_SHIELD` | `registry/RegistryPerks.java:2289-2296` | `registry/events/CombatEventHandler.java:788` | `onLivingHurtConstitutionDefense` | Wired |
| `pain_suppression` | `PAIN_SUPPRESSION` | `registry/RegistryPerks.java:2297-2304` | `registry/events/PerkEffectsHandler.java:177` | `Reduce` | Wired |
| `spell_shield` | `SPELL_SHIELD` | `registry/RegistryPerks.java:2305-2313` | `registry/events/PerkEffectsHandler.java:140` | `Reduce` | Wired |
| `unbreakable` | `UNBREAKABLE` | `registry/RegistryPerks.java:2314-2321` | `mixin/MixItemStack.java:81-82` | `runicskills$reduceDurabilityLoss` | Wired |
| `dragon_breath_shield` | `DRAGON_BREATH_SHIELD` | `registry/RegistryPerks.java:2322-2330` | `registry/events/PerkEffectsHandler.java:178` | `Reduce` | Wired |
| `siege_defense` | `SIEGE_DEFENSE` | `registry/RegistryPerks.java:2331-2338` | `registry/events/PerkEffectsHandler.java:251` | `onIncomingDamage` | Wired |
| `ancient_guardian` | `ANCIENT_GUARDIAN` | `registry/RegistryPerks.java:2339-2346` | `registry/events/PerkEffectsHandler.java:241` | `onIncomingDamage` | Wired |
| `runic_ward` | `RUNIC_WARD` | `registry/RegistryPerks.java:2347-2354` | `registry/events/PerkEffectsHandler.java:139` | `Reduce` | Wired |
| `adaptation` | `ADAPTATION` | `registry/RegistryPerks.java:2355-2362` | `registry/events/PerkEffectsHandler.java:255` | `onIncomingDamage` | Wired |
| `immovable_object` | `IMMOVABLE_OBJECT` | `registry/RegistryPerks.java:2363-2369` | `registry/events/PerkEffectsHandler.java:299` | `onKnockback` | Wired |
| `bookworm` | `BOOKWORM` | `registry/RegistryPerks.java:2372-2379` | `registry/events/PerkEffectsHandler.java:793` | `onMobXp` | Wired |
| `quick_learner` | `QUICK_LEARNER` | `registry/RegistryPerks.java:2380-2387` | `network/packet/common/SkillLevelUpSP.java:178` | `requiredPoints` | Wired |
| `linguist` | `LINGUIST` | `registry/RegistryPerks.java:2388-2395` | `mixin/MixVillager.java:108` | `runicskills$moreTradeOptions` | Wired |
| `cartographer` | `CARTOGRAPHER` | `registry/RegistryPerks.java:2396-2403` | `registry/events/ScholarPerkHandler.java:222` | `onFillMap` | Wired |
| `potion_brewing_expert` | `POTION_BREWING_EXPERT` | `registry/RegistryPerks.java:2404-2411` | `registry/events/PerkEffectsHandler.java:382` | `attrs` | Wired |
| `lore_keeper` | `LORE_KEEPER` | `registry/RegistryPerks.java:2412-2419` | `registry/events/ScholarPerkHandler.java:92` | `onReadBook` | Wired |
| `dragon_lore` | `DRAGON_LORE` | `registry/RegistryPerks.java:2420-2428` | `integration/IceAndFireIntegration.java:351` | `onTameAttempt` | Wired |
| `spellcraft_knowledge` | `SPELLCRAFT_KNOWLEDGE` | `registry/RegistryPerks.java:2429-2437` | `integration/IronsSpellbooksIntegration.java:511-512` | `onPlayerTickPhase1a` | Wired |
| `arcane_scholar` | `ARCANE_SCHOLAR` | `registry/RegistryPerks.java:2438-2446` | `integration/ArsNouveauIntegration.java:317-318` | `onSpellCostCalc` | Wired |
| `apothecary` | `APOTHECARY` | `registry/RegistryPerks.java:2447-2454` | `registry/events/PerkEffectsHandler.java:381` | `attrs` | Wired |
| `siege_engineer` | `SIEGE_ENGINEER` | `registry/RegistryPerks.java:2455-2462` | `integration/SiegeMachinesIntegration.java:44-51` | `onSiegeDamage` | Wired |
| `monster_compendium` | `MONSTER_COMPENDIUM` | `registry/RegistryPerks.java:2463-2470` | `registry/events/ArcanePerkHandler.java:95`<br>`registry/events/ArcanePerkHandler.java:133` | `onOutgoingDamage`<br>`onKill` | Wired |
| `tactical_genius` | `TACTICAL_GENIUS` | `registry/RegistryPerks.java:2471-2478` | `registry/events/PerkEffectsHandler.java:1069` | `onOutgoingDamage` | Wired |
| `enchantment_insight` | `ENCHANTMENT_INSIGHT` | `registry/RegistryPerks.java:2479-2486` | `mixin/MixEnchantmentMenu.java:155-156` | `runicskills$addExtraEnchantments` | Wired |
| `efficient_crafting` | `EFFICIENT_CRAFTING` | `registry/RegistryPerks.java:2487-2494` | `registry/events/PerkEffectsHandler.java:1263` | `onCraft` | Wired |
| `runecrafter` | `RUNECRAFTER` | `registry/RegistryPerks.java:2495-2502` | `registry/events/PerkEffectsHandler.java:1095` | `onOutgoingDamage` | Wired |
| `aquatic_knowledge` | `AQUATIC_KNOWLEDGE` | `registry/RegistryPerks.java:2503-2510` | `registry/events/PerkEffectsHandler.java:1200` | `onFinishEating` | Wired |
| `progressive_mastery` | `PROGRESSIVE_MASTERY` | `registry/RegistryPerks.java:2511-2518` | `registry/events/PerkEffectsHandler.java:800` | `onMobXp` | Wired |
| `scroll_mastery` | `SCROLL_MASTERY` | `registry/RegistryPerks.java:2519-2526` | `registry/events/EnchantingLorePerkHandler.java:203-204` | `onAnvilUse` | Wired |
| `familiar_bond` | `FAMILIAR_BOND` | `registry/RegistryPerks.java:2527-2535` | `integration/ArsNouveauIntegration.java:471-472` | `onFamiliarCombat` | Wired |
| `strategic_mind` | `STRATEGIC_MIND` | `registry/RegistryPerks.java:2536-2543` | `registry/events/PerkEffectsHandler.java:1065` | `onOutgoingDamage` | Wired |
| `brewing_innovation` | `BREWING_INNOVATION` | `registry/RegistryPerks.java:2544-2551` | `registry/events/PerkEffectsHandler.java:383` | `attrs` | Wired |
| `ancient_languages` | `ANCIENT_LANGUAGES` | `registry/RegistryPerks.java:2552-2559` | `registry/events/ScholarPerkHandler.java:89` | `onReadBook` | Wired |
| `master_researcher` | `MASTER_RESEARCHER` | `registry/RegistryPerks.java:2560-2567` | `registry/events/ScholarPerkHandler.java:160` | `onItemCrafted` | Wired |
| `golem_commander` | `GOLEM_COMMANDER` | `registry/RegistryPerks.java:2568-2576` | `integration/ArsNouveauIntegration.java:477-478` | `onFamiliarCombat` | Wired |
| `dimensional_scholar` | `DIMENSIONAL_SCHOLAR` | `registry/RegistryPerks.java:2577-2584` | `registry/events/PerkEffectsHandler.java:798` | `onMobXp` | Wired |
| `war_tactician` | `WAR_TACTICIAN` | `registry/RegistryPerks.java:2585-2592` | `registry/events/PerkEffectsHandler.java:651` | `buffNearbyAllies` | Wired |
| `alchemic_transmutation` | `ALCHEMIC_TRANSMUTATION` | `registry/RegistryPerks.java:2593-2600` | `mixin/MixBrewingStandBlockEntity.java:62-67` | `runicskills$refundIngredient` | Wired |
| `mystic_analysis` | `MYSTIC_ANALYSIS` | `registry/RegistryPerks.java:2601-2608` | `registry/events/PerkEffectsHandler.java:1123` | `onOutgoingDamage` | Wired |
| `sages_focus` | `SAGES_FOCUS` | `registry/RegistryPerks.java:2609-2616` | `registry/events/PerkEffectsHandler.java:1359` | `onItemUseTick` | Wired |
| `efficient_miner` | `EFFICIENT_MINER` | `registry/RegistryPerks.java:2618-2625` | `registry/events/PerkEffectsHandler.java:1299` | `onBreakSpeed` | Wired |
| `vein_miner` | `VEIN_MINER` | `registry/RegistryPerks.java:2626-2632` | `registry/events/PerkEffectsHandler.java:977` | `onBlockBreak` | Wired |
| `silk_touch_mastery` | `SILK_TOUCH_MASTERY` | `registry/RegistryPerks.java:2633-2640` | `registry/events/PerkEffectsHandler.java:960` | `onBlockBreak` | Wired |
| `fortune_miner` | `FORTUNE_MINER` | `registry/RegistryPerks.java:2641-2648` | `registry/events/PerkEffectsHandler.java:930` | `onBlockBreak` | Wired |
| `architect` | `ARCHITECT` | `registry/RegistryPerks.java:2649-2656` | `registry/events/ExplosionPerkHandler.java:106` | `onDetonateShielding` | Wired |
| `lumberjack` | `LUMBERJACK` | `registry/RegistryPerks.java:2657-2664` | `registry/events/PerkEffectsHandler.java:1301` | `onBreakSpeed` | Wired |
| `smelter` | `SMELTER` | `registry/RegistryPerks.java:2665-2672` | `mixin/MixAbstractFurnaceBlockEntity.java:62-63` | `runicskills$speedUpSmelting` | Wired |
| `quarry_master` | `QUARRY_MASTER` | `registry/RegistryPerks.java:2673-2680` | `registry/events/PerkEffectsHandler.java:935` | `onBlockBreak` | Wired |
| `resource_efficiency` | `RESOURCE_EFFICIENCY` | `registry/RegistryPerks.java:2681-2688` | `registry/events/WorkshopPerkHandler.java:111-112` | `onBlockBroken` | Wired |
| `reinforced_construction` | `REINFORCED_CONSTRUCTION` | `registry/RegistryPerks.java:2689-2696` | `registry/events/PerkEffectsHandler.java:137` | `Reduce` | Wired |
| `terraformer` | `TERRAFORMER` | `registry/RegistryPerks.java:2697-2704` | `registry/events/PerkEffectsHandler.java:1303` | `onBreakSpeed` | Wired |
| `ore_detector` | `ORE_DETECTOR` | `registry/RegistryPerks.java:2705-2711` | `client/event/OreDetectorRenderer.java:89`<br>`client/event/OreDetectorRenderer.java:123` | `onClientTick`<br>`onRenderLevel` | Wired |
| `blast_mining` | `BLAST_MINING` | `registry/RegistryPerks.java:2712-2719` | `registry/events/ExplosionPerkHandler.java:61-62` | `onDetonate` | Wired |
| `stone_cutter_efficiency` | `STONE_CUTTER_EFFICIENCY` | `registry/RegistryPerks.java:2720-2727` | `mixin/MixStonecutterMenu.java:52-53` | `runicskills$bonusOutput` | Wired |
| `master_woodworker` | `MASTER_WOODWORKER` | `registry/RegistryPerks.java:2728-2735` | `registry/events/PerkEffectsHandler.java:1265` | `onCraft` | Wired |
| `deep_core_mining` | `DEEP_CORE_MINING` | `registry/RegistryPerks.java:2736-2743` | `registry/events/PerkEffectsHandler.java:934` | `onBlockBreak` | Wired |
| `bridge_builder` | `BRIDGE_BUILDER` | `registry/RegistryPerks.java:2744-2751` | `registry/events/PerkEffectsHandler.java:356` | `attrs` | Wired |
| `runic_mining` | `RUNIC_MINING` | `registry/RegistryPerks.java:2752-2759` | `registry/events/PerkEffectsHandler.java:932` | `onBlockBreak` | Wired |
| `medieval_architecture` | `MEDIEVAL_ARCHITECTURE` | `registry/RegistryPerks.java:2760-2767` | `registry/events/PerkEffectsHandler.java:1266` | `onCraft` | Wired |
| `explosive_expert` | `EXPLOSIVE_EXPERT` | `registry/RegistryPerks.java:2768-2774` | `registry/events/ExplosionPerkHandler.java:68-69` | `onDetonate` | Wired |
| `foundation_layer` | `FOUNDATION_LAYER` | `registry/RegistryPerks.java:2775-2782` | `registry/events/ExplosionPerkHandler.java:110-111` | `onDetonateShielding` | Wired |
| `farmers_hand` | `FARMERS_HAND` | `registry/RegistryPerks.java:2783-2791` | `registry/events/WorkshopPerkHandler.java:193` | `onCropGrow` | Wired |
| `irrigation_expert` | `IRRIGATION_EXPERT` | `registry/RegistryPerks.java:2792-2799` | `registry/events/WorkshopPerkHandler.java:196-197` | `onCropGrow` | Wired |
| `master_breaker` | `MASTER_BREAKER` | `registry/RegistryPerks.java:2800-2807` | `registry/events/PerkEffectsHandler.java:1300` | `onBreakSpeed` | Wired |
| `glowstone_sight` | `GLOWSTONE_SIGHT` | `registry/RegistryPerks.java:2808-2814` | `registry/events/ExplosionPerkHandler.java:270-271` | `onBlockMined` | Wired |
| `salvage_expert` | `SALVAGE_EXPERT` | `registry/RegistryPerks.java:2815-2822` | `registry/events/WorkshopPerkHandler.java:115-116` | `onBlockBroken` | Wired |
| `prospector` | `PROSPECTOR` | `registry/RegistryPerks.java:2823-2830` | `registry/events/PerkEffectsHandler.java:940` | `onBlockBreak` | Wired |
| `underground_explorer` | `UNDERGROUND_EXPLORER` | `registry/RegistryPerks.java:2831-2838` | `registry/events/PerkEffectsHandler.java:342` | `attrs` | Wired |
| `mass_production` | `MASS_PRODUCTION` | `registry/RegistryPerks.java:2839-2846` | `registry/events/PerkEffectsHandler.java:1262` | `onCraft` | Wired |
| `heritage_builder` | `HERITAGE_BUILDER` | `registry/RegistryPerks.java:2847-2854` | `registry/events/PerkEffectsHandler.java:772` | `onAttributeTick` | Wired |
| `enchantment_preservation` | `ENCHANTMENT_PRESERVATION` | `registry/RegistryPerks.java:2857-2864` | `registry/events/EnchantingLorePerkHandler.java:95-96` | `onPlayerDrops` | Wired |
| `disenchant_mastery` | `DISENCHANT_MASTERY` | `registry/RegistryPerks.java:2865-2872` | `registry/events/StationPerkHandler.java:40-41` | `onGrindstoneTake` | Wired |
| `mending_boost` | `MENDING_BOOST` | `registry/RegistryPerks.java:2873-2880` | `registry/events/PerkEffectsHandler.java:766` | `onAttributeTick` | Wired |
| `unbreaking_mastery` | `UNBREAKING_MASTERY` | `registry/RegistryPerks.java:2881-2888` | `mixin/MixItemStack.java:88-89` | `runicskills$reduceDurabilityLoss` | Wired |
| `enchantment_stacking` | `ENCHANTMENT_STACKING` | `registry/RegistryPerks.java:2889-2896` | `registry/events/AnvilPerkHandler.java:158-159` | `applyExtraEnchantment` | Wired |
| `wisdom_of_ages` | `WISDOM_OF_AGES` | `registry/RegistryPerks.java:2897-2904` | `registry/events/PerkEffectsHandler.java:1281` | `onAnvil` | Wired |
| `tome_of_knowledge` | `TOME_OF_KNOWLEDGE` | `registry/RegistryPerks.java:2905-2912` | `mixin/MixEnchantmentMenu.java:200-201` | `runicskills$deepenBookEnchantment` | Wired |
| `runic_enchantment` | `RUNIC_ENCHANTMENT` | `registry/RegistryPerks.java:2913-2920` | `registry/events/PerkEffectsHandler.java:1100` | `onOutgoingDamage` | Wired |
| `apotheosis_wisdom` | `APOTHEOSIS_WISDOM` | `registry/RegistryPerks.java:2921-2929` | `integration/ApotheosisIntegration.java:423-429` | `onGetEnchantmentLevel` | Wired |
| `scroll_scribe` | `SCROLL_SCRIBE` | `registry/RegistryPerks.java:2930-2936` | `registry/events/EnchantingLorePerkHandler.java:229-230` | `onGrindstoneTake` | Wired |
| `mystic_attunement` | `MYSTIC_ATTUNEMENT` | `registry/RegistryPerks.java:2937-2944` | `registry/events/PerkEffectsHandler.java:1116` | `onOutgoingDamage` | Wired |
| `soul_binding` | `SOUL_BINDING` | `registry/RegistryPerks.java:2945-2951` | `registry/events/EnchantingLorePerkHandler.java:62` | `onPlayerDeath` | Wired |
| `experienced_enchanter` | `EXPERIENCED_ENCHANTER` | `registry/RegistryPerks.java:2952-2959` | `mixin/MixEnchantmentMenu.java:76-77` | `runicskills$discountOffers` | Wired |
| `arcane_linguist` | `ARCANE_LINGUIST` | `registry/RegistryPerks.java:2960-2968` | `integration/IronsSpellbooksIntegration.java:521-522` | `onPlayerTickPhase1a` | Wired |
| `ward_master` | `WARD_MASTER` | `registry/RegistryPerks.java:2969-2977` | `integration/ArsNouveauIntegration.java:442` | `onSpellModifier` | Wired |
| `dimensional_wisdom` | `DIMENSIONAL_WISDOM` | `registry/RegistryPerks.java:2978-2985` | `registry/events/EnchantingLorePerkHandler.java:267-268` | `onOutgoingDamage` | Wired |
| `ancient_inscriptions` | `ANCIENT_INSCRIPTIONS` | `registry/RegistryPerks.java:2986-2993` | `registry/events/ScholarPerkHandler.java:258` | `onUseTotem` | Wired |
| `ars_savant` | `ARS_SAVANT` | `registry/RegistryPerks.java:2994-3002` | `integration/ArsNouveauIntegration.java:489-490` | `onFamiliarCombat` | Wired |
| `spell_inscription` | `SPELL_INSCRIPTION` | `registry/RegistryPerks.java:3003-3010` | `registry/events/PerkEffectsHandler.java:1287` | `onAnvil` | Wired |
| `elder_knowledge` | `ELDER_KNOWLEDGE` | `registry/RegistryPerks.java:3011-3018` | `mixin/MixEnchantmentMenu.java:119-120` | `runicskills$refundAfterEnchanting` | Wired |
| `bookcraft` | `BOOKCRAFT` | `registry/RegistryPerks.java:3019-3026` | `mixin/MixVillager.java:59-60` | `runicskills$applyHagglerDiscount` | Wired |
| `mystic_sight` | `MYSTIC_SIGHT` | `registry/RegistryPerks.java:3027-3033` | `registry/events/EnchantingLorePerkHandler.java:340-341`<br>`registry/events/EnchantingLorePerkHandler.java:371` | `onSightTick`<br>`isWatchedByAnyone` | Wired |
| `lapis_conservation` | `LAPIS_CONSERVATION` | `registry/RegistryPerks.java:3034-3041` | `mixin/MixEnchantmentMenu.java:102-103` | `runicskills$refundAfterEnchanting` | Wired |
| `enlightenment` | `ENLIGHTENMENT` | `registry/RegistryPerks.java:3042-3049` | `network/packet/common/SkillLevelUpSP.java:175` | `requiredPoints` | Wired |
| `curse_breaker` | `CURSE_BREAKER` | `registry/RegistryPerks.java:3050-3056` | `mixin/MixGrindstoneMenu.java:56-57` | `runicskills$alsoRemoveCurses` | Wired |
| `enchantment_amplifier` | `ENCHANTMENT_AMPLIFIER` | `registry/RegistryPerks.java:3057-3064` | `registry/events/AnvilPerkHandler.java:123-124` | `applyBonusLevel` | Wired |
| `rune_mastery` | `RUNE_MASTERY` | `registry/RegistryPerks.java:3065-3072` | `registry/events/PerkEffectsHandler.java:175` | `Reduce` | Wired |
| `druidic_knowledge` | `DRUIDIC_KNOWLEDGE` | `registry/RegistryPerks.java:3073-3080` | `registry/events/PerkEffectsHandler.java:950` | `onBlockBreak` | Wired |
| `temporal_wisdom` | `TEMPORAL_WISDOM` | `registry/RegistryPerks.java:3081-3088` | `registry/events/EnchantingLorePerkHandler.java:307-308` | `onEffectAdded` | Respawn clock bug — MEDIUM-05 |
| `grand_sage` | `GRAND_SAGE` | `registry/RegistryPerks.java:3089-3096` | `network/packet/common/SkillLevelUpSP.java:182` | `requiredPoints` | Wired |
| `mana_regeneration` | `MANA_REGENERATION` | `registry/RegistryPerks.java:3099-3107` | `integration/IronsSpellbooksIntegration.java:493-494` | `onPlayerTickPhase1a` | Wired |
| `spell_amplifier` | `SPELL_AMPLIFIER` | `registry/RegistryPerks.java:3108-3116` | `registry/events/ArcanePerkHandler.java:77` | `onOutgoingDamage` | Wired |
| `source_well` | `SOURCE_WELL` | `registry/RegistryPerks.java:3117-3125` | `integration/ArsNouveauIntegration.java:368` | `onManaRegenCalc` | Mis-gated — MEDIUM-02 |
| `potion_splash` | `POTION_SPLASH` | `registry/RegistryPerks.java:3126-3133` | `mixin/MixThrownPotion.java:36-37` | `runicskills$widenSplashArea` | Wired |
| `telekinesis` | `TELEKINESIS` | `registry/RegistryPerks.java:3134-3141` | `registry/events/PerkEffectsHandler.java:360` | `attrs` | Wired |
| `elemental_master` | `ELEMENTAL_MASTER` | `registry/RegistryPerks.java:3142-3150` | `registry/events/ArcanePerkHandler.java:84` | `onOutgoingDamage` | Wired |
| `arcane_barrier` | `ARCANE_BARRIER` | `registry/RegistryPerks.java:3151-3158` | `registry/events/PerkEffectsHandler.java:750-751` | `onAttributeTick` | Wired |
| `spell_quickening` | `SPELL_QUICKENING` | `registry/RegistryPerks.java:3159-3167` | `integration/IronsSpellbooksIntegration.java:504-505` | `onPlayerTickPhase1a` | Wired |
| `source_attunement` | `SOURCE_ATTUNEMENT` | `registry/RegistryPerks.java:3168-3176` | `integration/ArsNouveauIntegration.java:396-397` | `onMaxManaCalc` | Mis-gated — MEDIUM-02 |
| `summoner` | `SUMMONER` | `registry/RegistryPerks.java:3177-3185` | `registry/events/ArcanePerkHandler.java:119` | `onSummonDamage` | Wired |
| `mystic_shield` | `MYSTIC_SHIELD` | `registry/RegistryPerks.java:3186-3193` | `registry/events/PerkEffectsHandler.java:144` | `Reduce` | Wired |
| `astral_projection` | `ASTRAL_PROJECTION` | `registry/RegistryPerks.java:3194-3201` | `registry/events/ArcanePerkHandler.java:206` | `onProjectionTick` | Wired |
| `philosophers_stone` | `PHILOSOPHERS_STONE` | `registry/RegistryPerks.java:3202-3209` | `registry/events/ArcanePerkHandler.java:163` | `onMobDrops` | Wired |
| `mana_shield` | `MANA_SHIELD` | `registry/RegistryPerks.java:3210-3218` | `registry/events/PerkEffectsHandler.java:220` | `onIncomingDamage` | Wired |
| `dragon_magic` | `DRAGON_MAGIC` | `registry/RegistryPerks.java:3219-3227` | `integration/IceAndFireIntegration.java:299` | `onDragonMagicDamage` | Wired |
| `eldritch_power` | `ELDRITCH_POWER` | `registry/RegistryPerks.java:3228-3236` | `registry/events/PerkEffectsHandler.java:1127` | `onOutgoingDamage` | Wired |
| `soul_magic` | `SOUL_MAGIC` | `registry/RegistryPerks.java:3237-3244` | `registry/events/PerkEffectsHandler.java:387` | `attrs` | Wired |
| `dual_casting` | `DUAL_CASTING` | `registry/RegistryPerks.java:3245-3252` | `registry/events/PerkEffectsHandler.java:1401` | `onPotionThrown` | Wired |
| `enchanted_missiles` | `ENCHANTED_MISSILES` | `registry/RegistryPerks.java:3253-3260` | `registry/events/PerkEffectsHandler.java:1130` | `onOutgoingDamage` | Wired |
| `void_magic` | `VOID_MAGIC` | `registry/RegistryPerks.java:3261-3268` | `registry/events/ArcanePerkHandler.java:89` | `onOutgoingDamage` | Wired |
| `treasure_sense` | `TREASURE_SENSE` | `registry/RegistryPerks.java:3271-3278` | `registry/events/PerkEffectsHandler.java:371` | `attrs` | Wired |
| `double_down` | `DOUBLE_DOWN` | `registry/RegistryPerks.java:3279-3286` | `registry/events/PerkEffectsHandler.java:829`<br>`registry/events/PerkEffectsHandler.java:939` | `onMobDrops`<br>`onBlockBreak` | Wired |
| `golden_touch` | `GOLDEN_TOUCH` | `registry/RegistryPerks.java:3287-3294` | `registry/events/PerkEffectsHandler.java:835` | `onMobDrops` | Wired |
| `fortunes_favor` | `FORTUNES_FAVOR` | `registry/RegistryPerks.java:3295-3302` | `registry/events/PerkEffectsHandler.java:943` | `onBlockBreak` | Wired |
| `lucky_fishing` | `LUCKY_FISHING` | `registry/RegistryPerks.java:3303-3310` | `registry/events/PerkEffectsHandler.java:376` | `attrs` | Wired |
| `prospectors_luck` | `PROSPECTORS_LUCK` | `registry/RegistryPerks.java:3311-3318` | `registry/events/PerkEffectsHandler.java:931` | `onBlockBreak` | Wired |
| `scavenger` | `SCAVENGER` | `registry/RegistryPerks.java:3319-3326` | `registry/events/PerkEffectsHandler.java:372` | `attrs` | Wired |
| `critical_mastery` | `CRITICAL_MASTERY` | `registry/RegistryPerks.java:3327-3334` | `registry/events/PerkEffectsHandler.java:1315` | `onCriticalHit` | Wired |
| `looter` | `LOOTER` | `registry/RegistryPerks.java:3335-3342` | `registry/events/PerkEffectsHandler.java:838-839` | `onMobDrops` | Wired |
| `jackpot` | `JACKPOT` | `registry/RegistryPerks.java:3343-3350` | `registry/loot/RunicLootModifier.java:71` | `doApply` | Wired |
| `enchanted_fortune` | `ENCHANTED_FORTUNE` | `registry/RegistryPerks.java:3351-3358` | `registry/events/PerkEffectsHandler.java:832` | `onMobDrops` | Wired |
| `dragon_hoard` | `DRAGON_HOARD` | `registry/RegistryPerks.java:3359-3367` | `registry/loot/RunicLootModifier.java:88` | `doApply` | Wired |
| `cataclysm_spoils` | `CATACLYSM_SPOILS` | `registry/RegistryPerks.java:3368-3376` | `registry/loot/RunicLootModifier.java:80` | `doApply` | Wired |
| `runic_fortune` | `RUNIC_FORTUNE` | `registry/RegistryPerks.java:3377-3384` | `registry/events/PerkEffectsHandler.java:933` | `onBlockBreak` | Wired |
| `apotheosis_gems` | `APOTHEOSIS_GEMS` | `registry/RegistryPerks.java:3385-3393` | `registry/loot/RunicLootModifier.java:109` | `doApply` | Wired |
| `lucky_charm` | `LUCKY_CHARM` | `registry/RegistryPerks.java:3394-3401` | `registry/events/WorkshopPerkHandler.java:46` | `onHarmfulEffect` | Wired |
| `coin_flip` | `COIN_FLIP` | `registry/RegistryPerks.java:3402-3409` | `registry/events/PerkEffectsHandler.java:805` | `onMobXp` | Wired |
| `salvage_luck` | `SALVAGE_LUCK` | `registry/RegistryPerks.java:3410-3417` | `registry/events/WorkshopPerkHandler.java:145` | `salvagedCount` | Wired |
| `adventurers_luck` | `ADVENTURERS_LUCK` | `registry/RegistryPerks.java:3418-3426` | `registry/events/PerkEffectsHandler.java:377` | `attrs` | Wired |
| `midas_touch` | `MIDAS_TOUCH` | `registry/RegistryPerks.java:3427-3434` | `registry/events/PerkEffectsHandler.java:836` | `onMobDrops` | Wired |
| `lucky_break` | `LUCKY_BREAK` | `registry/RegistryPerks.java:3435-3442` | `registry/events/PerkEffectsHandler.java:771` | `onAttributeTick` | Wired |
| `jewelers_eye` | `JEWELERS_EYE` | `registry/RegistryPerks.java:3443-3450` | `registry/events/FortunePerkHandler.java:80` | `onItemCrafted` | Wired |
| `fortune_cookie` | `FORTUNE_COOKIE` | `registry/RegistryPerks.java:3451-3459` | `registry/events/PerkEffectsHandler.java:1218` | `onFinishEating` | Wired |
| `ethereal_luck` | `ETHEREAL_LUCK` | `registry/RegistryPerks.java:3460-3467` | `registry/loot/RunicLootModifier.java:120` | `doApply` | Wired |
| `rare_find` | `RARE_FIND` | `registry/RegistryPerks.java:3468-3475` | `registry/events/PerkEffectsHandler.java:373` | `attrs` | Wired |
| `lucky_star` | `LUCKY_STAR` | `registry/RegistryPerks.java:3476-3483` | `registry/events/PerkEffectsHandler.java:357` | `attrs` | Wired |
| `serendipity` | `SERENDIPITY` | `registry/RegistryPerks.java:3484-3491` | `registry/events/PerkEffectsHandler.java:485` | `dropSerendipityFind` | Wired |
| `greed` | `GREED` | `registry/RegistryPerks.java:3492-3499` | `registry/events/PerkEffectsHandler.java:834` | `onMobDrops` | Wired |
| `rainbow_loot` | `RAINBOW_LOOT` | `registry/RegistryPerks.java:3500-3507` | `registry/events/PerkEffectsHandler.java:851` | `onMobDrops` | Wired |
| `fishermans_luck` | `FISHERMANS_LUCK` | `registry/RegistryPerks.java:3508-3515` | `registry/loot/RunicLootModifier.java:95` | `doApply` | Wired |
| `lucky_explorer` | `LUCKY_EXPLORER` | `registry/RegistryPerks.java:3516-3523` | `registry/events/PerkEffectsHandler.java:375` | `attrs` | Wired |
| `chaos_roll` | `CHAOS_ROLL` | `registry/RegistryPerks.java:3524-3531` | `registry/events/FortunePerkHandler.java:133` | `rollForBlessing` | Respawn clock bug — MEDIUM-05 |
| `critical_fortune` | `CRITICAL_FORTUNE` | `registry/RegistryPerks.java:3532-3539` | `registry/events/PerkEffectsHandler.java:830` | `onMobDrops` | Wired |
| `master_looter` | `MASTER_LOOTER` | `registry/RegistryPerks.java:3540-3547` | `registry/events/PerkEffectsHandler.java:374` | `attrs` | Wired |
| `blessing_of_luck` | `BLESSING_OF_LUCK` | `registry/RegistryPerks.java:3548-3555` | `registry/events/PerkEffectsHandler.java:440` | `onLuckApplied` | Wired |
| `repair_expert` | `REPAIR_EXPERT` | `registry/RegistryPerks.java:3558-3565` | `registry/events/PerkEffectsHandler.java:1280` | `onAnvil` | Wired |
| `disassembler` | `DISASSEMBLER` | `registry/RegistryPerks.java:3566-3573` | `registry/events/WorkshopPerkHandler.java:80` | `onItemDestroyed` | Wired |
| `auto_repair` | `AUTO_REPAIR` | `registry/RegistryPerks.java:3574-3581` | `registry/events/PerkEffectsHandler.java:764` | `onAttributeTick` | Wired |
| `gadgeteer` | `GADGETEER` | `registry/RegistryPerks.java:3582-3589` | `mixin/MixItemStack.java:97-98` | `runicskills$reduceDurabilityLoss` | Wired |
| `trap_maker` | `TRAP_MAKER` | `registry/RegistryPerks.java:3590-3597` | `registry/events/ExplosionPerkHandler.java:217-224` | `onTrapDamage` | Wired |
| `lock_expert` | `LOCK_EXPERT` | `registry/RegistryPerks.java:3600-3608` | `mixin/MixItemStack.java:107-108` | `runicskills$reduceDurabilityLoss` | Wired |
| `key_forge` | `KEY_FORGE` | `registry/RegistryPerks.java:3609-3616` | `integration/LocksIntegration.java:42` | `onItemCrafted` | Wired |
| `mechanical_knowledge` | `MECHANICAL_KNOWLEDGE` | `registry/RegistryPerks.java:3617-3624` | `mixin/MixHopperBlockEntity.java:39-41` | `runicskills$fasterTransfers` | Wired |
| `siege_mechanic` | `SIEGE_MECHANIC` | `registry/RegistryPerks.java:3625-3632` | `registry/events/PerkEffectsHandler.java:1353` | `onItemUseTick` | Wired |
| `weapon_smith` | `WEAPON_SMITH` | `registry/RegistryPerks.java:3633-3640` | `registry/events/PerkEffectsHandler.java:770` | `onAttributeTick` | Wired |
| `armor_smith` | `ARMOR_SMITH` | `registry/RegistryPerks.java:3641-3648` | `registry/events/PerkEffectsHandler.java:350` | `attrs` | Wired |
| `tool_smith` | `TOOL_SMITH` | `registry/RegistryPerks.java:3649-3656` | `registry/events/PerkEffectsHandler.java:769` | `onAttributeTick` | Wired |
| `salvage_master` | `SALVAGE_MASTER` | `registry/RegistryPerks.java:3657-3664` | `registry/events/WorkshopPerkHandler.java:83` | `onItemDestroyed` | Wired |
| `enchantment_transfer` | `ENCHANTMENT_TRANSFER` | `registry/RegistryPerks.java:3665-3672` | `registry/events/AnvilPerkHandler.java:70-71` | `onAnvilTransfer` | Wired |
| `overclock` | `OVERCLOCK` | `registry/RegistryPerks.java:3673-3680` | `mixin/MixAbstractFurnaceBlockEntity.java:66-67`<br>`mixin/MixBrewingStandBlockEntity.java:101-102` | `runicskills$speedUpSmelting`<br>`runicskills$speedUpBrewing` | Wired |
| `runic_engineering` | `RUNIC_ENGINEERING` | `registry/RegistryPerks.java:3681-3688` | `registry/events/PerkEffectsHandler.java:767` | `onAttributeTick` | Wired |
| `brewing_apparatus` | `BREWING_APPARATUS` | `registry/RegistryPerks.java:3689-3696` | `mixin/MixBrewingStandBlockEntity.java:97-98` | `runicskills$speedUpBrewing` | Wired |
| `mechanical_arm` | `MECHANICAL_ARM` | `registry/RegistryPerks.java:3697-3704` | `registry/events/PerkEffectsHandler.java:363` | `attrs` | Wired |
| `precision_tools` | `PRECISION_TOOLS` | `registry/RegistryPerks.java:3705-3712` | `registry/events/PerkEffectsHandler.java:765` | `onAttributeTick` | Wired |
| `assembly_line` | `ASSEMBLY_LINE` | `registry/RegistryPerks.java:3713-3720` | `registry/events/PerkEffectsHandler.java:1261` | `onCraft` | Wired |
| `explosive_ordinance` | `EXPLOSIVE_ORDINANCE` | `registry/RegistryPerks.java:3721-3728` | `registry/events/ExplosionPerkHandler.java:56-57` | `onDetonate` | Wired |
| `modular_equipment` | `MODULAR_EQUIPMENT` | `registry/RegistryPerks.java:3732-3740` | `integration/ApotheosisIntegration.java:361` | `onGetItemSockets` | Wired |
| `forge_master` | `FORGE_MASTER` | `registry/RegistryPerks.java:3741-3748` | `registry/events/ExplosionPerkHandler.java:244-245` | `onSmeltCollected` | Wired |
| `inventor` | `INVENTOR` | `registry/RegistryPerks.java:3749-3756` | `registry/events/ScholarPerkHandler.java:164` | `onItemCrafted` | Wired |
| `spring_loaded` | `SPRING_LOADED` | `registry/RegistryPerks.java:3757-3764` | `registry/events/MobilityPerkHandler.java:32` | `onJump` | Wired |
| `ballistic_expert` | `BALLISTIC_EXPERT` | `registry/RegistryPerks.java:3765-3772` | `registry/events/PerkEffectsHandler.java:1140` | `onOutgoingDamage` | Wired |
| `safe_builder` | `SAFE_BUILDER` | `registry/RegistryPerks.java:3773-3781` | `integration/LocksIntegration.java:81` | `onLockCrafted` | Wired |
| `tinkers_touch` | `TINKERS_TOUCH` | `registry/RegistryPerks.java:3782-3789` | `registry/events/PerkEffectsHandler.java:768` | `onAttributeTick` | Wired |
| `alloy_master` | `ALLOY_MASTER` | `registry/RegistryPerks.java:3790-3797` | `registry/events/PerkEffectsHandler.java:1264` | `onCraft` | Wired |
| `mechanism_mastery` | `MECHANISM_MASTERY` | `registry/RegistryPerks.java:3798-3805` | `mixin/MixDispenserBlock.java:46-50` | `runicskills$fasterActivation` | Wired |
| `power_tools` | `POWER_TOOLS` | `registry/RegistryPerks.java:3806-3813` | `registry/events/EnchantingLorePerkHandler.java:138` | `onBreakSpeed` | Wired |
| `waystone_tinker` | `WAYSTONE_TINKER` | `registry/RegistryPerks.java:3814-3821` | `registry/events/MobilityPerkHandler.java:57` | `onPearlLanded` | Wired |
| `master_artificer` | `MASTER_ARTIFICER` | `registry/RegistryPerks.java:3822-3829` | `registry/events/EnchantingLorePerkHandler.java:170-171` | `onItemCrafted` | Wired |

### Passives / stat upgrades

| Registry ID | Constant | Declared | Effect implementation | Runtime trigger | Audit status |
|---|---|---|---|---|---|
| `attack_damage` | `ATTACK_DAMAGE` | `registry/RegistryPassives.java:47` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `attack_knockback` | `ATTACK_KNOCKBACK` | `registry/RegistryPassives.java:49` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `max_health` | `MAX_HEALTH` | `registry/RegistryPassives.java:51` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `knockback_resistance` | `KNOCKBACK_RESISTANCE` | `registry/RegistryPassives.java:53` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `movement_speed` | `MOVEMENT_SPEED` | `registry/RegistryPassives.java:55` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `projectile_damage` | `PROJECTILE_DAMAGE` | `registry/RegistryPassives.java:57` | `registry/RegistryAttributes.java:77-100`, `199-215`<br>`registry/events/CombatEventHandler.java:876-887` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160`<br>`onPlayerShootArrow(ProjectileImpactEvent)` | Wired |
| `armor` | `ARMOR` | `registry/RegistryPassives.java:59` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `armor_toughness` | `ARMOR_TOUGHNESS` | `registry/RegistryPassives.java:61` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `attack_speed` | `ATTACK_SPEED` | `registry/RegistryPassives.java:63` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `entity_reach` | `ENTITY_REACH` | `registry/RegistryPassives.java:65` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `block_reach` | `BLOCK_REACH` | `registry/RegistryPassives.java:68` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `break_speed` | `BREAK_SPEED` | `registry/RegistryPassives.java:70` | `registry/RegistryAttributes.java:77-100`, `199-215`<br>`registry/events/CombatEventHandler.java:229-265` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160`<br>`onPlayerMining(PlayerEvent.BreakSpeed)` | Over-applied — MEDIUM-01 |
| `enchanting_power` | `ENCHANTING_POWER` | `registry/RegistryPassives.java:73` | `registry/RegistryAttributes.java:77-100`, `199-215`<br>— | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160`<br>— | Inert — HIGH-05 |
| `xp_bonus` | `XP_BONUS` | `registry/RegistryPassives.java:75` | `registry/RegistryAttributes.java:77-100`, `199-215`<br>— | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160`<br>— | Inert — HIGH-04 |
| `beneficial_effect` | `BENEFICIAL_EFFECT` | `registry/RegistryPassives.java:77` | `registry/RegistryAttributes.java:77-100`, `199-215`<br>`mixin/MixLivingEntity.java:175-184` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160`<br>`LivingEntity.addEffect mixin` | Wired |
| `magic_resist` | `MAGIC_RESIST` | `registry/RegistryPassives.java:79` | `registry/RegistryAttributes.java:77-100`, `199-215`<br>`mixin/MixLivingEntity.java:83-99` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160`<br>`LivingEntity.getDamageAfterArmorAbsorb mixin` | Wired |
| `critical_damage` | `CRITICAL_DAMAGE` | `registry/RegistryPassives.java:81` | `registry/RegistryAttributes.java:77-100`, `199-215`<br>`registry/events/CombatEventHandler.java:268-280` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160`<br>`onPlayerCriticalHit(CriticalHitEvent)` | Wired |
| `fortune` | `FORTUNE` | `registry/RegistryPassives.java:83` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `spell_power` | `SPELL_POWER` | `registry/RegistryPassives.java:86` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `max_mana` | `MAX_MANA` | `registry/RegistryPassives.java:87` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `cast_time_reduction` | `CAST_TIME_REDUCTION` | `registry/RegistryPassives.java:88` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `ars_spell_damage` | `ARS_SPELL_DAMAGE` | `registry/RegistryPassives.java:91` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `ars_flat_mana` | `ARS_FLAT_MANA` | `registry/RegistryPassives.java:92` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `ars_warding` | `ARS_WARDING` | `registry/RegistryPassives.java:93` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `life_steal` | `APOTHIC_LIFE_STEAL` | `registry/RegistryPassives.java:96` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `healing_received` | `APOTHIC_HEALING_RECEIVED` | `registry/RegistryPassives.java:97` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `draw_speed` | `APOTHIC_DRAW_SPEED` | `registry/RegistryPassives.java:98` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `dodge_chance` | `APOTHIC_DODGE_CHANCE` | `registry/RegistryPassives.java:99` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `experience_gained` | `APOTHIC_EXPERIENCE_GAINED` | `registry/RegistryPassives.java:100` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `mining_speed` | `APOTHIC_MINING_SPEED` | `registry/RegistryPassives.java:101` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `cold_damage` | `APOTHIC_COLD_DAMAGE` | `registry/RegistryPassives.java:102` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `crit_chance` | `APOTHIC_CRIT_CHANCE` | `registry/RegistryPassives.java:103` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `fire_damage` | `APOTHIC_FIRE_DAMAGE` | `registry/RegistryPassives.java:104` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `arrow_velocity` | `APOTHIC_ARROW_VELOCITY` | `registry/RegistryPassives.java:105` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `armor_pierce` | `APOTHIC_ARMOR_PIERCE` | `registry/RegistryPassives.java:106` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `swim_speed` | `SWIM_SPEED` | `registry/RegistryPassives.java:109` | `registry/RegistryAttributes.java:77-100`, `199-215` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160` | Wired |
| `repair_efficiency` | `REPAIR_EFFICIENCY` | `registry/RegistryPassives.java:112` | `registry/RegistryAttributes.java:77-100`, `199-215`<br>`registry/events/CraftingEventHandler.java:103-110` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160`<br>`onAnvilRepair(AnvilRepairEvent)` | Wired |
| `crafting_luck` | `CRAFTING_LUCK` | `registry/RegistryPassives.java:113` | `registry/RegistryAttributes.java:77-100`, `199-215`<br>`registry/events/CraftingEventHandler.java:65-100` | `network/packet/common/AdjustPassiveSP.java:95-130`; `registry/events/PlayerLifecycleHandler.java:197-230`; `common/progression/ProgressionService.java:150-160`<br>`onCraft(PlayerEvent.ItemCraftedEvent)` | Wired |

### Powers / abilities

| Registry ID | Constant | Declared | Effect implementation | Runtime trigger | Audit status |
|---|---|---|---|---|---|
| `ember_trail` | `EMBER_TRAIL` | `registry/RegistryPowers.java:147` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:268-269`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:955-956` | `emberTrailOnDamage`<br>`emberTrailOnTick` | Wired |
| `kindle` | `KINDLE` | `registry/RegistryPowers.java:148` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:286-288` | `onSpellDamage` | Wired |
| `heat_haze` | `HEAT_HAZE` | `registry/RegistryPowers.java:149` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:723-725` | `heatHazeOnHurt` | Wired |
| `scorched_earth` | `SCORCHED_EARTH` | `registry/RegistryPowers.java:150` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:282-283`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:519-522` | `scorchedEarthOnDamage`<br>`scorchedEarthOnFieldSpawn` | Partially implemented — MEDIUM-04 |
| `pyroclasm` | `PYROCLASM` | `registry/RegistryPowers.java:151` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:620-622` | `onLivingDeath` | Wired |
| `brittle` | `BRITTLE` | `registry/RegistryPowers.java:154` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:520-522` | `onLivingDamage` | Wired |
| `frost_echo` | `FROST_ECHO` | `registry/RegistryPowers.java:155` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:632-634` | `onMagicProjectileBlockHit` | Wired |
| `shatter` | `SHATTER` | `registry/RegistryPowers.java:156` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:800-802` | `onLivingDamage` | Wired |
| `reforge_the_shadow` | `REFORGE_THE_SHADOW` | `registry/RegistryPowers.java:157` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:600-608`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:838` | `reforgeTheShadowOnSummon`<br>`reforgeTheShadowOnBearDeath` | State loss — MEDIUM-03; declared APPROXIMATE |
| `glacial_sovereign` | `GLACIAL_SOVEREIGN` | `registry/RegistryPowers.java:158` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:951-952` | `onPlayerTick` | Wired; declared APPROXIMATE |
| `static_cling` | `STATIC_CLING` | `registry/RegistryPowers.java:161` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:349-363`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:407` | `chainLightningOnDamage`<br>`staticClingAmplifyOnDamage` | Wired |
| `crackle_arc` | `CRACKLE_ARC` | `registry/RegistryPowers.java:162` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:555-557` | `onLivingDamage` | Wired |
| `skybreaker` | `SKYBREAKER` | `registry/RegistryPowers.java:163` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:109-111`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:306-309`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:661` | `onSpellOnCast`<br>`onSpellDamage`<br>`onLivingDeath` | Wired |
| `conduit_mark` | `CONDUIT_MARK` | `registry/RegistryPowers.java:164` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:348-356`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:382-385` | `chainLightningOnDamage`<br>`conduitDetonationOnDamage` | Wired |
| `thunder_lord` | `THUNDER_LORD` | `registry/RegistryPowers.java:165` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:399-400`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:922-923` | `onSpellDamage`<br>`onPlayerTick` | Wired; declared APPROXIMATE |
| `sanctified_strike` | `SANCTIFIED_STRIKE` | `registry/RegistryPowers.java:168` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:276-278` | `onSpellDamage` | Wired |
| `fortifying_bond` | `FORTIFYING_BOND` | `registry/RegistryPowers.java:169` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:95-97` | `onSpellOnCast` | Wired |
| `guided_fate` | `GUIDED_FATE` | `registry/RegistryPowers.java:170` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:318-320` | `onSpellDamage` | Wired |
| `wings_of_judgment` | `WINGS_OF_JUDGMENT` | `registry/RegistryPowers.java:171` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:298-300` | `wingsOfJudgmentOnDamage` | Wired |
| `herald_of_dawn` | `HERALD_OF_DAWN` | `registry/RegistryPowers.java:172` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:757-758` | `onHeraldHurt` | Wired; declared APPROXIMATE |
| `step_between` | `STEP_BETWEEN` | `registry/RegistryPowers.java:175` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:696-697`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:836-837` | `onSpellTeleport`<br>`onStepBetweenDamage` | Wired; declared APPROXIMATE |
| `arcane_echo` | `ARCANE_ECHO` | `registry/RegistryPowers.java:176` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:325-327` | `arcaneEchoOnDamage` | Wired |
| `counterspell_riposte` | `COUNTERSPELL_RIPOSTE` | `registry/RegistryPowers.java:177` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:154-155`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:360-364` | `onSpellOnCast`<br>`onSpellDamage` | Wired; declared APPROXIMATE |
| `black_hole_resonance` | `BLACK_HOLE_RESONANCE` | `registry/RegistryPowers.java:178` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:505-507`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:757-759`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:860-864` | `trackBlackHole`<br>`blackHoleResonanceOnHurt`<br>`blackHoleResonanceOnKill` | Wired |
| `unraveled` | `UNRAVELED` | `registry/RegistryPowers.java:179` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:590-591` | `onLivingDeath` | Wired |
| `fang_follow_through` | `FANG_FOLLOW_THROUGH` | `registry/RegistryPowers.java:182` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:212-213`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:704-705` | `fangFollowThroughOnCast`<br>`fangFollowThroughOnHurt` | Wired |
| `vex_taunt` | `VEX_TAUNT` | `registry/RegistryPowers.java:183` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:808-813` | `onSummonHurt` | Wired |
| `creeper_cascade_mastery` | `CREEPER_CASCADE_MASTERY` | `registry/RegistryPowers.java:184` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:224-225`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:425-427`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:533-534` | `creeperCascadeOnCast`<br>`creeperCascadeOnDamage`<br>`creeperCascadeOnHeadSpawn` | Wired |
| `shield_wall` | `SHIELD_WALL` | `registry/RegistryPowers.java:185` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:232-233`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:553-556` | `shieldWallOnCast`<br>`shieldWallOnShieldSpawn` | Wired |
| `tricksters_aria` | `TRICKSTERS_ARIA` | `registry/RegistryPowers.java:186` | `mixin/MixTrueInvisibilityEffect.java:39-40`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:169-170`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:345-346` | `runicskills$tricksterAriaSuppress`<br>`onSpellOnCast`<br>`onSpellDamage` | Wired |
| `poisoners_thumb` | `POISONERS_THUMB` | `registry/RegistryPowers.java:189` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:502-504` | `onLivingDamage` | Wired |
| `rooted` | `ROOTED` | `registry/RegistryPowers.java:190` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:982-985` | `onPlayerTick` | Wired |
| `blight_spread` | `BLIGHT_SPREAD` | `registry/RegistryPowers.java:191` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:883-885` | `blightSpreadOnKill` | Wired |
| `venomous_harvest` | `VENOMOUS_HARVEST` | `registry/RegistryPowers.java:192` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:449-450`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:776-777`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:914-915` | `venomousHarvestAmplifyOnDamage`<br>`venomousHarvestDotOnHurt`<br>`venomousHarvestOnKill` | Wired |
| `the_grove_remembers` | `THE_GROVE_REMEMBERS` | `registry/RegistryPowers.java:193` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:533-541` | `onLivingDamage` | Wired |
| `crimson_tithe` | `CRIMSON_TITHE` | `registry/RegistryPowers.java:196` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:296-297` | `onSpellDamage` | Wired |
| `marrow_sense` | `MARROW_SENSE` | `registry/RegistryPowers.java:197` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:128-130` | `onSpellPreCast` | Wired |
| `sacrifice_cascade` | `SACRIFICE_CASCADE` | `registry/RegistryPowers.java:198` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:132-133` | `onSpellOnCast` | Wired; declared APPROXIMATE |
| `harvest_the_weak` | `HARVEST_THE_WEAK` | `registry/RegistryPowers.java:199` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:647-650` | `onLivingDeath` | Wired |
| `the_hearts_toll` | `THE_HEARTS_TOLL` | `registry/RegistryPowers.java:200` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:181-182`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:330-331` | `onSpellOnCast`<br>`onSpellDamage` | Wired |
| `forbidden_knowledge` | `FORBIDDEN_KNOWLEDGE` | `registry/RegistryPowers.java:203` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:119-120`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:856-857` | `onSpellOnCast`<br>`onLivingDrops` | Wired; declared APPROXIMATE |
| `blind_witness` | `BLIND_WITNESS` | `registry/RegistryPowers.java:204` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:511-513` | `onLivingDamage` | Wired |
| `kinetic_affinity` | `KINETIC_AFFINITY` | `registry/RegistryPowers.java:205` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:686-693`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:978-983` | `kineticAffinityOnHurt`<br>`kineticAffinityOnTick` | Wired |
| `piercing_insight` | `PIERCING_INSIGHT` | `registry/RegistryPowers.java:206` | `registry/events/IronsSpellbooksSchoolPowerDispatcher.java:174-175`<br>`registry/events/IronsSpellbooksSchoolPowerDispatcher.java:465-466` | `piercingInsightOnCast`<br>`piercingInsightOnDamage` | Wired |
| `the_apocrypha_awakens` | `THE_APOCRYPHA_AWAKENS` | `registry/RegistryPowers.java:207` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:194-195`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:376-377` | `onSpellOnCast`<br>`onSpellDamage` | Wired |
| `trueshot` | `TRUESHOT` | `registry/RegistryPowers.java:214` | `registry/events/VanillaPowerEventDispatcher.java:190-191` | `onProjectileHurt` | Wired |
| `ricochet_primer` | `RICOCHET_PRIMER` | `registry/RegistryPowers.java:215` | `registry/events/VanillaPowerEventDispatcher.java:134-135` | `onProjectileLaunched` | Wired |
| `volley_memory` | `VOLLEY_MEMORY` | `registry/RegistryPowers.java:216` | `registry/events/VanillaPowerEventDispatcher.java:212-213` | `onProjectileHurt` | Wired |
| `gravity_well` | `GRAVITY_WELL` | `registry/RegistryPowers.java:217` | `registry/events/VanillaPowerEventDispatcher.java:202-203` | `onProjectileHurt` | Wired |
| `arcanists_barrage` | `ARCANISTS_BARRAGE` | `registry/RegistryPowers.java:218` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:424-426`<br>`registry/events/VanillaPowerEventDispatcher.java:225`<br>`registry/events/VanillaPowerEventDispatcher.java:239` | `onSpellDamage`<br>`onProjectileHurt`<br>`applyBarrage` | Wired |
| `unbroken_focus` | `UNBROKEN_FOCUS` | `registry/RegistryPowers.java:221` | `registry/events/ChannelPowerHandler.java:148-149` | `onProjectileLaunched` | Wired |
| `tidal_draw` | `TIDAL_DRAW` | `registry/RegistryPowers.java:222` | `registry/events/ChannelPowerHandler.java:101-102` | `applyTidalDraw` | Wired |
| `harmonic_resonance` | `HARMONIC_RESONANCE` | `registry/RegistryPowers.java:223` | `registry/events/ChannelPowerHandler.java:159-160`<br>`registry/events/ChannelPowerHandler.java:216` | `onProjectileLaunched`<br>`applyResonanceSplash` | Wired |
| `siphon_bond` | `SIPHON_BOND` | `registry/RegistryPowers.java:224` | `registry/events/ChannelPowerHandler.java:191-192` | `onChannelledProjectileHit` | Wired |
| `the_long_note` | `THE_LONG_NOTE` | `registry/RegistryPowers.java:225` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:224`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:446-450` | `onSpellOnCast`<br>`onSpellDamage` | Wired |
| `pack_tactics` | `PACK_TACTICS` | `registry/RegistryPowers.java:228` | `registry/events/SummonPowerHandler.java:82-83` | `onSummonDealtDamage` | Wired |
| `fallen_echo` | `FALLEN_ECHO` | `registry/RegistryPowers.java:229` | `registry/events/SummonPowerHandler.java:135-136`<br>`registry/events/SummonPowerHandler.java:155-157` | `onSummonDied`<br>`onOwnerDamageDuringEcho` | Wired |
| `soul_tether` | `SOUL_TETHER` | `registry/RegistryPowers.java:230` | `registry/events/SummonPowerHandler.java:112-114` | `onSummonHurt` | Wired |
| `lingering_binding` | `LINGERING_BINDING` | `registry/RegistryPowers.java:231` | `registry/events/SummonPowerHandler.java:93`<br>`registry/events/SummonPowerHandler.java:144`<br>`registry/events/SummonPowerHandler.java:174` | `onSummonDealtDamage`<br>`onSummonDied`<br>`releaseBinding` | Wired |
| `the_conductor` | `THE_CONDUCTOR` | `registry/RegistryPowers.java:232` | `registry/events/SummonPowerHandler.java:211-216` | `onOwnerStruckTarget` | Wired |
| `phase_recoil` | `PHASE_RECOIL` | `registry/RegistryPowers.java:235` | `registry/events/VanillaPowerEventDispatcher.java:149-150`<br>`registry/events/VanillaPowerEventDispatcher.java:294-295` | `onProjectileLaunched`<br>`onPlayerTeleported` | Wired |
| `vanishing_trail` | `VANISHING_TRAIL` | `registry/RegistryPowers.java:236` | `registry/events/VanillaPowerEventDispatcher.java:320-321` | `onPlayerTeleported` | Wired |
| `clean_exit` | `CLEAN_EXIT` | `registry/RegistryPowers.java:237` | `registry/events/VanillaPowerEventDispatcher.java:310-311` | `onPlayerTeleported` | Wired |
| `blink_strike` | `BLINK_STRIKE` | `registry/RegistryPowers.java:238` | `registry/events/VanillaPowerEventDispatcher.java:177-178`<br>`registry/events/VanillaPowerEventDispatcher.java:301-302` | `onProjectileHurt`<br>`onPlayerTeleported` | Wired |
| `folded_space` | `FOLDED_SPACE` | `registry/RegistryPowers.java:239` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:208-213`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:708-709`<br>`registry/events/VanillaPowerEventDispatcher.java:336-337` | `onSpellOnCast`<br>`onSpellTeleport`<br>`onPlayerTeleported` | Wired; declared APPROXIMATE |
| `staff_strike` | `STAFF_STRIKE` | `registry/RegistryPowers.java:242` | `registry/events/WeaponCasterPowerHandler.java:78-79`<br>`registry/events/WeaponCasterPowerHandler.java:107-109` | `onMeleeHit`<br>`onArrowLoose` | Wired |
| `spell_parry` | `SPELL_PARRY` | `registry/RegistryPowers.java:243` | `registry/events/WeaponCasterPowerHandler.java:136-145` | `onShieldBlock` | Wired |
| `imbued_rhythm` | `IMBUED_RHYTHM` | `registry/RegistryPowers.java:244` | `registry/events/WeaponCasterPowerHandler.java:179-180` | `applyRhythm` | Wired |
| `arcane_riposte` | `ARCANE_RIPOSTE` | `registry/RegistryPowers.java:245` | `registry/events/WeaponCasterPowerHandler.java:85-86` | `onMeleeHit` | Wired |
| `warmages_covenant` | `WARMAGES_COVENANT` | `registry/RegistryPowers.java:246` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:409-413`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:736-737` | `onSpellDamage`<br>`onCriticalHit` | Wired; declared APPROXIMATE |
| `lingering_grace` | `LINGERING_GRACE` | `registry/RegistryPowers.java:249` | `registry/events/UtilityPowerHandler.java:108-110` | `onEffectExpired` | Wired |
| `shared_flame` | `SHARED_FLAME` | `registry/RegistryPowers.java:250` | `registry/events/UtilityPowerHandler.java:69-71` | `onEffectAdded` | Wired |
| `shield_break_counter` | `SHIELD_BREAK_COUNTER` | `registry/RegistryPowers.java:251` | `registry/events/UtilityPowerHandler.java:152-153`<br>`registry/events/UtilityPowerHandler.java:191-196` | `onDamage`<br>`onPlayerTick` | Wired |
| `empowered_dispel` | `EMPOWERED_DISPEL` | `registry/RegistryPowers.java:252` | `registry/events/UtilityPowerHandler.java:134-136`<br>`registry/events/UtilityPowerHandler.java:162-164` | `onDebuffRemoved`<br>`onDamage` | Wired |
| `the_still_mind` | `THE_STILL_MIND` | `registry/RegistryPowers.java:253` | `registry/events/IronsSpellbooksPowerEventDispatcher.java:234-243`<br>`registry/events/IronsSpellbooksPowerEventDispatcher.java:476-480` | `onSpellOnCast`<br>`onSpellDamage` | Wired |

### Custom attributes / stats

| Registry ID | Constant | Declared | Effect implementation | Runtime trigger | Audit status |
|---|---|---|---|---|---|
| `break_speed` | `BREAK_SPEED` | `registry/RegistryAttributes.java:31` | `registry/events/CombatEventHandler.java:229-265` | `onPlayerMining(PlayerEvent.BreakSpeed)` | Over-applied — MEDIUM-01 |
| `critical_damage` | `CRITICAL_DAMAGE` | `registry/RegistryAttributes.java:32` | `registry/events/CombatEventHandler.java:268-280` | `onPlayerCriticalHit(CriticalHitEvent)` | Wired |
| `projectile_damage` | `PROJECTILE_DAMAGE` | `registry/RegistryAttributes.java:33` | `registry/events/CombatEventHandler.java:876-887` | `onPlayerShootArrow(ProjectileImpactEvent)` | Wired |
| `beneficial_effect` | `BENEFICIAL_EFFECT` | `registry/RegistryAttributes.java:34` | `mixin/MixLivingEntity.java:175-184` | `LivingEntity.addEffect mixin` | Wired |
| `magic_resist` | `MAGIC_RESIST` | `registry/RegistryAttributes.java:35` | `mixin/MixLivingEntity.java:83-99` | `LivingEntity.getDamageAfterArmorAbsorb mixin` | Wired |
| `enchanting_power` | `ENCHANTING_POWER` | `registry/RegistryAttributes.java:36` | — | — | No consumer — HIGH-05 |
| `xp_bonus` | `XP_BONUS` | `registry/RegistryAttributes.java:37` | — | — | No consumer — HIGH-04 |
| `repair_efficiency` | `REPAIR_EFFICIENCY` | `registry/RegistryAttributes.java:38` | `registry/events/CraftingEventHandler.java:103-110` | `onAnvilRepair(AnvilRepairEvent)` | Wired |
| `crafting_luck` | `CRAFTING_LUCK` | `registry/RegistryAttributes.java:39` | `registry/events/CraftingEventHandler.java:65-100` | `onCraft(PlayerEvent.ItemCraftedEvent)` | Wired |

## 3. Findings

The tasks below are intentionally ordered within severity so a coding agent can work straight down the document. A fix is not complete until its Verification bullets pass.

## Critical

No confirmed Critical findings.

## High

### [HIGH-01] Arcane Reprieve and Continuous Flow are unreachable

- **Location:** `src/main/java/com/otectus/runicskills/integration/IronsSpellbooksIntegration.java:303-359`; declarations at `src/main/java/com/otectus/runicskills/registry/RegistryPerks.java:444-453` and `519-527`
- **Problem:** The mana-change handler returns for every non-increasing mana event before either spending-side perk is evaluated. Arcane Reprieve also uses an overflowing “never used” timestamp sentinel.
- **Evidence:** Lines 308-309 return when `newMana <= oldMana`. Arcane Reprieve at lines 332-345 requires `newMana <= 0 && oldMana > 0`; Continuous Flow at lines 348-357 requires `newMana < oldMana`. Neither predicate can be true after line 309. Lines 336-338 compute `now - Long.MIN_VALUE`; Java signed overflow makes the first comparison negative, so a first-ever reprieve would still fail even after moving the branch. The English descriptions at `src/main/resources/assets/runicskills/lang/en_us.json:672-673` and `686-687` promise both effects.
- **Impact:** Arcane Reprieve never restores mana and Continuous Flow never reduces continuous-cast drain. Both are registered, visible, rankable perks whose effects cannot occur.
- **Fix:** Refactor `onChangeMana` into independent deltas. Resolve player/capability once; execute Magic/Intelligence regeneration only for `new > old` and only under their own toggles; execute Continuous Flow for `new < old` while a continuous cast is active; execute Arcane Reprieve on the `old > 0 && new <= 0` crossing. Replace the sentinel with an absent-entry check, for example `Long last = map.get(id); if (last == null || now < last || now - last >= cdTicks)`. Run Reprieve after drain reduction and define the crossing against the event's pre-handler `newMana`, so Continuous Flow cannot accidentally prevent a true zero-mana trigger.
- **Verification:** Add unit/event-harness cases for gain, ordinary spend, continuous spend, exact-zero spend, and first-ever Reprieve. Assert disabling `ironsEnableManaRegen` does not disable either perk or the Intelligence synergy. In game, drain a continuous spell with and without the perk, then exhaust mana twice around the configured cooldown.
- **Depends on:** none

### [HIGH-02] Apotheosis socket events can use a stale or different player

- **Location:** `src/main/java/com/otectus/runicskills/integration/ApotheosisIntegration.java:67-114`, `200-207`, `262-292`, `301-320`, `510-541`, `555-565`
- **Problem:** Player attribution for playerless socket events is inferred from a process-global “most recent interactor” map. The map writes one clock and reads another, and even with one clock it chooses the latest player globally rather than the player performing the socket.
- **Evidence:** `recordInteraction` stores `p.level().getGameTime()` at lines 82-85. `resolveInteractor` and `pruneInteractors` compare those values to `MinecraftServer.getTickCount()` at lines 92-114. World game time is persisted, while server tick count restarts; after a normal restart, `now - storedGameTime` is negative and passes the `age <= 20` test indefinitely. Lines 97-103 select the globally greatest timestamp. That inferred player can deny `CanSocket` at lines 305-319 and receive the Gem Attunement refund at lines 519-540. The claim at lines 515-517 that concurrent players cannot cross-pollinate is contradicted by the selection algorithm.
- **Impact:** One player's Fortune can gate another player's gem, a refund can be inserted into the wrong inventory, and stale entries need not expire. This is observable cross-player item/state corruption even though it does not overwrite save files.
- **Fix:** Remove global recency inference from the socket path. Capture the actual menu/player around Apotheosis's synchronous socket recipe/result-take call using an optional-mod mixin or scoped context: set a `ThreadLocal<ServerPlayer>` immediately before the upstream code posts `ItemSocketingEvent`, clear it in `finally`, and read only that scoped actor in both handlers. If upstream exposes a menu/container identity, key by that identity instead. Do not fall back to “latest online player.” Clear any compatibility cache on logout and server stop. If a short-lived cache is retained temporarily, use one clock, reject negative ages, and scope entries to the socket container.
- **Verification:** With two players socketing in the same tick, assert each gate reads its own Fortune and each forced refund returns to its own inventory. Repeat after loading an old world into a fresh server process, after logout, and after server restart. Add a test where unrelated right-click/equipment events occur immediately before socketing; they must not become the actor.
- **Depends on:** none

### [HIGH-03] Scholar is a selectable perk with no executable effect

- **Location:** `src/main/java/com/otectus/runicskills/registry/RegistryPerks.java:186-192`; `src/main/java/com/otectus/runicskills/mixin/MixItemStack.java:24-54`; `src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java:214-217`; `src/test/java/com/otectus/runicskills/registry/PerkEffectCoverageTest.java:19-36`, `40-89`
- **Problem:** `runicskills:scholar` is registered but no executable Java code reads `RegistryPerks.SCHOLAR`. The coverage test incorrectly treats a Javadoc mention as an effect site.
- **Evidence:** A comment/literal-stripped repository search returns no `RegistryPerks.SCHOLAR` reference outside its declaration. The only raw-text hit is historical Javadoc at `MixItemStack.java:30`; the actual injection at lines 42-53 checks only `enableScholarEnchantmentHiding`. `PerkEffectCoverageTest` defines “effect” as an unparsed regex hit at lines 22-25 and scans whole source text at lines 69-89, so the comment makes the test pass. The perk remains player-facing at `src/main/resources/assets/runicskills/lang/en_us.json:305` and `330`. The config comment at lines 214-217 is internally inconsistent as well: it says the `disabledPerks` list “enables” Scholar and claims an XP/enchanting bonus for which Scholar has no value fields or handler.
- **Impact:** Players can spend a perk slot/rank on Scholar and receive nothing. CI's advertised no-inert-perk invariant does not catch this class of regression.
- **Fix:** Make the localized, player-facing “read enchantments” description canonical and correct the stale config comment. Move the hiding decision to a player-aware client tooltip hook: when `enableScholarEnchantmentHiding` is on, a local player without an active Scholar rank sees the locked text and a Scholar sees normal enchantment names. When the gate is off, make Scholar unavailable for activation and hide/mark it disabled so it is not a selectable no-op. Remove the static global `appendEnchantmentNames` cancellation once the player-aware path is authoritative. Harden both effect-coverage tests to strip comments/string literals and to count references only from an allowlisted set of gameplay packages or annotated/mixin methods; add a fixture proving a Javadoc-only reference fails.
- **Verification:** Automated tests: raw comment reference does not satisfy coverage; enabled Scholar does. Client checks with the config both off and on, two players with different Scholar ranks, enchanted equipment/books, and `disabledPerks=["scholar"]`.
- **Depends on:** none

### [HIGH-04] XP Bonus passive and `xp_bonus` stat have no consumer

- **Location:** `src/main/java/com/otectus/runicskills/registry/RegistryAttributes.java:37`; `src/main/java/com/otectus/runicskills/registry/RegistryPassives.java:75`; `src/main/java/com/otectus/runicskills/RunicSkills.java:191-198`; `src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java:625-632`
- **Problem:** The passive installs a modifier on `runicskills:xp_bonus`, but no gameplay code reads that attribute.
- **Evidence:** Executable symbol search finds `XP_BONUS` only in its attribute declaration, passive declaration, and attribute attachment. Existing XP hooks in `PerkEffectsHandler.java:786-812` and `CraftingEventHandler.java:162-179` do not query it. The config documents `0.25 = 25%`, and `en_us.json:1006-1007` promises XP from all sources.
- **Impact:** Every rank of the Wisdom XP Bonus passive is inert.
- **Fix:** Subscribe server-side to `PlayerXpEvent.XpChange`; for positive amounts only, read `player.getAttributeValue(XP_BONUS)` and replace the event amount with `amount + floor(amount * bonus + carry)`, retaining the new fractional carry by player UUID. Modify the event rather than calling `giveExperiencePoints`, which would recurse through the same event. Do not multiply negative XP expenditure. Clear the accumulator on clone/logout/server stop, clamp non-finite/negative attribute values to zero, and use saturating integer arithmetic.
- **Verification:** Tests for positive, zero, and negative changes; 100 one-point awards at a 25% bonus must total 125, not 100; disabled/zero-rank passive must remain vanilla. Check mob orbs, furnace XP, commands, and direct `giveExperiencePoints` calls.
- **Depends on:** none

### [HIGH-05] Enchanting Power and Apotheosis enchanting scaling are inert

- **Location:** `src/main/java/com/otectus/runicskills/registry/RegistryAttributes.java:36`; `src/main/java/com/otectus/runicskills/registry/RegistryPassives.java:73`; `src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java:5410-5420`; `src/main/java/com/otectus/runicskills/mixin/MixEnchantmentMenu.java:52-87`
- **Problem:** Nothing consumes `runicskills:enchanting_power`, and the two Apotheosis scaling settings are never read.
- **Evidence:** Executable symbol search finds `ENCHANTING_POWER` only in declaration/attachment/passive registration. `MixEnchantmentMenu` captures the table user and changes only displayed/charged costs at lines 65-86; it never supplies extra power to enchantment generation. `apothEnableEnchantingScaling` and `apothEnchantingScalePerLevel` have zero Java references outside their declarations. `en_us.json:1004-1005` promises stronger table rolls.
- **Impact:** The Wisdom passive never improves enchantments, and enabling/disabling or tuning Apotheosis Intelligence+Wisdom scaling changes nothing.
- **Fix:** First make the custom attribute functional: modify the bookshelf-power argument used by `EnchantmentHelper.getEnchantmentCost`/offer generation in `EnchantmentMenu.slotsChanged`, adding `floor(player.getAttributeValue(ENCHANTING_POWER))`. Target the mapped 1.20.1 call (including its synthetic lambda if necessary); do not merely raise `costs[]` after generation, because that changes price without regenerating enchantments. Then, in the Apotheosis integration's server reconciliation tick, add/remove a stable transient modifier on this attribute with amount `max(0, intelligence + wisdom) * apothEnchantingScalePerLevel` when both the integration and toggle are active. Register the UUID in `RunicAttributeModifiers` and its ownership/migration table.
- **Verification:** With a deterministic enchanting seed and fixed shelves/item, increasing only the passive must raise generated offer power; cost-only changes are insufficient. Test Apotheosis scaling at 0/one/max skill, live toggle off/on, config reload, logout/login, and repeated ticks for no stacking. Validate dedicated-server startup without client classes.
- **Depends on:** none

### [HIGH-06] Three Iron's cooldown-scaling settings are never read

- **Location:** `src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java:4180-4195`; available reconciliation path at `src/main/java/com/otectus/runicskills/integration/IronsSpellbooksIntegration.java:446-485`
- **Problem:** `ironsEnableCooldownReduction`, `ironsCooldownReductionPerLevel`, and `ironsMaxCooldownReduction` are public, synced configuration but have no runtime consumer.
- **Evidence:** A field-by-field executable Java search returns only the three declarations. The integration already reconciles the independent Tempo modifier on Iron's `COOLDOWN_REDUCTION` at lines 480-485, proving the target attribute and update path exist.
- **Impact:** Magic skill never provides the advertised cooldown reduction; all three server settings are inert.
- **Fix:** Add a separate, stable-UUID transient modifier to `AttributeRegistry.COOLDOWN_REDUCTION` in `onPlayerTickPhase1a`. Match the spell-damage baseline convention: `amount = min(ironsMaxCooldownReduction, max(0, magicLevel - 1) * ironsCooldownReductionPerLevel)`. The modifier must be removed when the integration or feature toggle is off and must stack independently with Tempo. Add the UUID to `RunicAttributeModifiers` and the legacy-removal ownership table.
- **Verification:** Attribute-level tests at Magic 1, intermediate, and cap; live config reload; integration toggle; Tempo plus scaling; logout/login; repeated reconciliation without stacking. Confirm the three fields are now referenced by executable code.
- **Depends on:** none

## Medium

### [MEDIUM-01] Break-speed passive doubles vanilla speed at zero rank

- **Location:** `src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java:229-265`; declaration at `src/main/java/com/otectus/runicskills/registry/RegistryAttributes.java:31`
- **Problem:** The handler calculates a complete boosted speed and then adds it to the event's existing speed, counting the baseline twice.
- **Evidence:** Lines 248-249 set `modifier = originalSpeed * (1 + attribute)`. Lines 259, 263, and 265 add that value to `event.getNewSpeed()`, which normally already contains the original speed. With attribute `0`, the result is `original + original = 2 * original`; at the shipped max `0.5`, it becomes `2.5 * original` rather than `1.5 * original`. The path is skipped only when Apothic mining-speed delegation is active.
- **Impact:** In installations without delegated Apothic mining speed, every pickaxe, shovel, and axe user receives an unintended 2× baseline speed even with no Building passive; ranked players receive an extra full baseline as well.
- **Fix:** Compute only the delta, `originalSpeed * attribute`, and add that delta to `event.getNewSpeed()`. Keep Obsidian Smasher's multiplier and other handlers' prior modifications intact; do not reset the event to `originalSpeed`. Add a pure helper for the delta so it can be tested without Forge.
- **Verification:** For attribute 0/0.25/0.5, assert final speed is 1.0×/1.25×/1.5× before other perks. Test pickaxe, shovel, axe, unsupported tool, Obsidian Smasher, and Apothic delegation.
- **Depends on:** none

### [MEDIUM-02] Integration scaling toggles suppress unrelated perks and synergies

- **Location:** `src/main/java/com/otectus/runicskills/integration/IronsSpellbooksIntegration.java:167-211`, `275-299`, `303-359`; `src/main/java/com/otectus/runicskills/integration/ArsNouveauIntegration.java:72-96`, `338-400`; independent config declarations at `src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java:412-433`, `4168-4231`, `5019-5047`
- **Problem:** Broad Magic-scaling switches act as early/outer gates for separately configured cross-skill bonuses and independently purchased perks.
- **Evidence:** Iron's `ironsEnableSpellDamageScaling` encloses Wisdom and school bonuses at lines 171-208; `ironsEnableSpellLevelBonus` returns before Imbued Focus at lines 278-298; `ironsEnableManaRegen` returns before Intelligence, Arcane Reprieve, and Continuous Flow at lines 306-357. Ars `arsEnableSpellDamageScaling` encloses Wisdom at lines 76-93; `arsEnableManaRegen` returns before Intelligence and Source Well at lines 343-370; `arsEnableMaxManaBonus` returns before Source Attunement at lines 379-399. The config comments describe each broad switch only as its corresponding Magic-based scaling, while the synergies have their own switches and the perks have their own enablement.
- **Impact:** Pack authors cannot disable one scaling formula without silently disabling other systems. Default settings mask the bug; it appears during documented configuration changes.
- **Fix:** Hoist only common player/capability validation. Guard each additive calculation with its own config switch or perk enablement. In particular, no `ironsEnable*`/`arsEnable*` gate may wrap an independently named perk or cross-mod synergy. HIGH-01 supplies the spending/gain split for Iron's mana and should be used rather than duplicated.
- **Verification:** Add a boolean matrix test for every master toggle, synergy toggle, and affected perk. Each cell must show that toggling one feature changes only its own contribution. Repeat after live config reload.
- **Depends on:** HIGH-01

### [MEDIUM-03] Reforge the Shadow loses its death-burst owner across reload/restart

- **Location:** `src/main/java/com/otectus/runicskills/registry/events/IronsSpellbooksSchoolPowerDispatcher.java:98-100`, `597-615`, `817-855`, `1052-1069`
- **Problem:** The bear's health modifier is persistent, but the summoner mapping required for the death burst exists only in a static map and is not rebuilt when the modifier already exists.
- **Evidence:** The owner map is static at lines 98-99. On join, line 606 returns when the permanent modifier survived NBT load; the owner is inserted only at line 614, after that return. Death requires and removes the map entry at lines 833-835. Logout deliberately retains entries at lines 1067-1068, but a server process restart necessarily empties the static map.
- **Impact:** A reforged polar bear still has +40% HP after reload, but can die without the advertised chill burst. Orphaned map entries can also persist until process exit when entities disappear without a death event.
- **Fix:** Store the owner UUID in the bear's persistent entity data when first reforged. On entity join, rebuild the map from that tag before the duplicate-modifier return; on death, read the tag directly as the source of truth and use the map only as a cache. Handle an offline owner by applying enemy effects with the fallback damage source, and prune cache entries when bears leave/remove.
- **Verification:** Serialize and reload a reforged bear, restart the server, then kill it with its owner online and offline; the burst must occur once. Test a chunk unload/reload and removal without death; cache size must return to baseline.
- **Depends on:** none

### [MEDIUM-04] Scorched Earth claims full duration support but skips Wall of Fire

- **Location:** `src/main/java/com/otectus/runicskills/registry/events/IronsSpellbooksSchoolPowerDispatcher.java:477-526`; `src/main/java/com/otectus/runicskills/integration/IronsSpellbooksPowerCompat.java:468-493`; `src/main/java/com/otectus/runicskills/registry/content/ContentStatusIndex.java:35-80`
- **Problem:** The description says fire-field spells last 40% longer, but Wall of Fire is tracked only for armor shred and its lifetime is never extended. Unlike other substitutions, the Power is not marked approximate.
- **Evidence:** The join switch sends both `KIND_FIRE_FIELD` and `KIND_WALL_OF_FIRE` to the same method at lines 491-495. That method documents the omission at lines 510-515, but `aoeDuration`/`setAoeDuration` support only `AoeEntity` and `BlackHole`, returning `-1`/`false` for `WallOfFireEntity` at compat lines 469-493. The English promise is unqualified at `en_us.json:2213-2214`. `ContentStatusIndex` lists ten approximate Powers at lines 50-75 and omits Scorched Earth, so it defaults to FULL.
- **Impact:** One of the primary Iron's fire-field spells receives only half the advertised Power while the UI presents the implementation as complete.
- **Fix:** Preferred: add an optional-mod accessor/mixin for Wall of Fire's duration field and extend it through `IronsSpellbooksPowerCompat`, with graceful failure on upstream field drift. If that cannot be made version-safe, narrow the tooltip to the supported entity and add `scorched_earth -> APPROXIMATE` to `ContentStatusIndex`. Do not retain the current FULL/unqualified combination.
- **Verification:** Spawn Fire Field and Wall of Fire with and without the Power, measure server-side lifetimes, and assert 1.4× for both under the preferred fix. Confirm armor shred still works. Add a content-status test matching the chosen implementation.
- **Depends on:** none

### [MEDIUM-05] Entity-relative tick timers become stale after player clone/respawn

- **Location:** `src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java:76-108`, `181-198`, `244-246`, `746-749`, `1083-1091`, `1319-1335`, `1417-1447`; `src/main/java/com/otectus/runicskills/registry/events/FortunePerkHandler.java:35-60`, `131-143`; `src/main/java/com/otectus/runicskills/registry/events/EnchantingLorePerkHandler.java:37-53`, `286-318`; `src/main/java/com/otectus/runicskills/registry/events/PlayerLifecycleHandler.java:197-218`
- **Problem:** UUID-keyed state stores `Player.tickCount`, which resets when Forge replaces the Player entity on death/dimension clone, but the maps are not translated or cleared on clone.
- **Evidence:** The listed handlers store/compare entity `tickCount` for Bloodlust, damage/dodge windows, Samurai Resolve, Battle Recovery, Phantom Strike, Mythical Berserker, survive-lethal cooldowns, Chaos Roll, Temporal Wisdom, and the current critical swing. `PlayerLifecycleHandler.onPlayerClone` copies capability data at lines 197-218 but touches none of these maps. Logout cleanup does not run during a normal death clone. A new entity at tick 0 minus an old positive timestamp is negative, so `< window` predicates can remain true and `> window` predicates can remain false until the new entity catches up; “ready at” values can lock out for the old entity's entire age.
- **Impact:** After respawn, some combat windows can last minutes/hours, recovery can remain disabled, and cooldowns can remain locked far beyond their configured duration.
- **Fix:** Convert duration/cooldown state to `ServerLevel.getGameTime()` in `Map<UUID, Long>` so clones share one monotonic world clock. Keep same-tick markers on game time equality or clear them explicitly. Add `clearPlayer`/`clearAll` APIs for each handler and invoke them on clone, logout, and server stop for state that should not survive those boundaries. Decide and test survive-lethal cooldown policy explicitly; if it should survive death, game time preserves it correctly.
- **Verification:** Seed each map with an old entity at tick 72,000, clone to a new entity at tick 0, and assert every window/cooldown has the intended remaining time. Include death, non-death clone/dimension change, logout/login, and switching worlds in one JVM.
- **Depends on:** none

### [MEDIUM-06] Iron's transient perk maps are never lifecycle-cleaned

- **Location:** `src/main/java/com/otectus/runicskills/integration/IronsSpellbooksIntegration.java:133-149`, `361-369`, `409-412`; server cleanup at `src/main/java/com/otectus/runicskills/registry/events/PlayerLifecycleHandler.java:100-124`
- **Problem:** Spellweaver and Arcane Reprieve keep UUID state forever within the JVM, despite a comment claiming entries are bounded by online players.
- **Evidence:** Three static maps are declared at lines 409-412 and populated at lines 133-149/332-345. There is no logout/server-stop subscriber or clear method in the integration. The comment at lines 366-369 explicitly says entries are not cleaned and incorrectly says they cap at online-player count; they actually cap at every UUID seen in the process. Central server-stop cleanup at `PlayerLifecycleHandler.java:120-123` clears other owners but not these maps.
- **Impact:** Long-running servers leak entries per caster. Loading another world in the same client JVM can carry combo/cooldown timestamps into a different clock domain, producing stale combos or extended lockouts once HIGH-01 makes Reprieve reachable.
- **Fix:** Add `clearPlayer(UUID)` and `clearAll()` in the integration and subscribe inside the optional-mod class to logout and server-stop events. Reject negative elapsed time defensively. Keep common lifecycle code from statically referencing the Iron's-typed class when the dependency is absent.
- **Verification:** Join/cast/logout repeatedly with unique UUIDs and assert map sizes return to zero. Load two worlds sequentially in one JVM and verify the first cast/Reprieve in world two has fresh state.
- **Depends on:** HIGH-01

### [MEDIUM-07] Power override reload consumes and syncs every namespace's `powers/` JSON

- **Location:** `src/main/java/com/otectus/runicskills/registry/powers/PowerOverridesReloadListener.java:17-74`; `src/main/java/com/otectus/runicskills/registry/powers/PowerOverridesManager.java:21-65`; `src/main/java/com/otectus/runicskills/network/packet/client/PowerOverridesSyncCP.java:30-75`
- **Problem:** The reload listener owns the generic `powers` folder and accepts every namespace/object without verifying that the ID is a registered Runic Power. Foreign data becomes no-op state that is sent to every client.
- **Evidence:** `FOLDER = "powers"` at line 28 causes `data/<any namespace>/powers/*.json` to enter the map. `apply` stores every parsed ID at lines 35-47; `parse` accepts any object at lines 50-73. Runtime lookups only use exact registered `power.key` values at manager lines 34-65. The login packet serializes all stored entries at packet lines 30-75 and its decoder rejects counts above 8,192, while the sender/reload path imposes no corresponding cap.
- **Impact:** Datapacks or other mods using the same conventional folder inflate login packets; enough foreign files can make clients reject the packet and disconnect even though none target Runic Skills.
- **Fix:** Move overrides to a mod-specific folder such as `runicskills/powers`, or filter the generic listener to IDs present in `RegistryPowers.POWERS_REGISTRY`. For migration, optionally accept only `runicskills:*` files from the old folder for one release and log a deprecation. Enforce the same count/key/value limits before storing and before encoding.
- **Verification:** A foreign `data/other/powers/foo.json` must be ignored and absent from the sync payload; a valid Runic override must load. Boundary-test 8,192/8,193 entries and oversized value maps without disconnecting a client.
- **Depends on:** none

### [MEDIUM-08] Duration values use conflicting seconds/ticks conversions

- **Location:** `src/main/java/com/otectus/runicskills/registry/perks/Perk.java:230-241`; `src/main/java/com/otectus/runicskills/registry/RegistryPerks.java:82-92`, `127-137`, `166-174`, `721-727`; `src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java:333-340`, `917-919`; `src/main/java/com/otectus/runicskills/registry/events/CraftingEventHandler.java:188-189`; `src/main/java/com/otectus/runicskills/integration/IronsSpellbooksIntegration.java:720-734`
- **Problem:** Values presented through `ValueType.DURATION` are formatted as seconds, but their runtime conversions range from `20×`, to `40×`, to `10 + 20×`, to raw ticks.
- **Evidence:** `Perk.getParameter` appends `s` to every DURATION at lines 230-241. Counter Attack registers a DURATION but opens `value * 40` ticks at combat lines 333-340, so displayed 3s lasts 6s. Fighting Spirit and Quick Reposition add `10 + 20 * value`, so displayed 3s lasts 3.5s. Every catalyst except Eldritch multiplies seconds by 20 at integration lines 722-733; Eldritch passes raw 10 ticks, while its lang line `en_us.json:752` also appends the word `ticks`, producing a seconds-suffixed value followed by “ticks.”
- **Impact:** Tooltips/config do not predict actual durations; balancing one field requires knowing a hidden, feature-specific conversion.
- **Fix:** Introduce explicit unit helpers/types. Use `secondsToTicks(value)` (exactly `value * 20`, saturating) for Counter Attack, Fighting Spirit, and Quick Reposition; remove the unexplained 10-tick padding. Preserve Eldritch's documented tick unit by adding `ValueType.TICKS` and formatting it once, or migrate the field to seconds with a versioned config conversion. Add a test that every registered duration's displayed unit matches its runtime conversion.
- **Verification:** At value 3, measure exactly 60 ticks for the three second-based effects. At Eldritch default 10 ticks, show exactly “10 ticks” and apply 10 ticks. Test overflow bounds and config reload.
- **Depends on:** none

## Low

### [LOW-01] Mortal Strike sound subtitle key is missing from English localization

- **Location:** `src/main/resources/assets/runicskills/sounds.json:2-6`; `src/main/resources/assets/runicskills/lang/en_us.json:147`; registration at `src/main/java/com/otectus/runicskills/registry/RegistrySounds.java:11-15`
- **Problem:** The registered `mortal_strike` sound references `screen.skill.mortal_strike`, but `en_us.json` does not define that key.
- **Evidence:** `sounds.json` line 3 names the missing subtitle. A full key search finds only that reference; the other sound's `screen.title.sound_effect` key exists at `en_us.json:147`.
- **Impact:** With subtitles enabled, players see the raw translation key instead of readable text when Limit Breaker/Mortal Strike plays.
- **Fix:** Add `"screen.skill.mortal_strike": "Mortal strike"` (or approved copy) to `en_us.json` and mirror it in maintained locales/fallback policy.
- **Verification:** Resource test asserting every `sounds.json` subtitle exists in `en_us.json`; in-game subtitle check with Limit Breaker forced to proc.
- **Depends on:** none

### [LOW-02] Obsolete configuration and integration scaffolding is provably dead

- **Location:** `src/main/java/com/otectus/runicskills/handler/HandlerConfigCommon.java:10-194`; `src/main/java/com/otectus/runicskills/config/ItemListGroup.java:13-23`; `src/main/java/com/otectus/runicskills/integration/BossesOfMassDestructionIntegration.java:5-11`; `src/main/java/com/otectus/runicskills/integration/JetAndEliasIntegration.java:5-11`; `build.gradle:447-457`
- **Problem:** Four top-level types have no executable caller. One builds an explicitly unused Forge config at class initialization; one YACL value factory returns null; two integration classes only expose unused mod-presence checks.
- **Evidence:** `HandlerConfigCommon` labels its configuration unused at lines 126-130 and no source references the type. `ItemListGroup.provideNewValue()` returns null at lines 20-22; its only external mention is the build lint allowlist at `build.gradle:456`. Comment/literal-stripped symbol search finds no reference to either integration class, and neither appears in the registration list at `RunicSkills.java:115-160`.
- **Impact:** No player-facing malfunction, but the code and build allowlist imply supported paths that do not exist, increase static-initialization surface, and can mislead future fixes.
- **Fix:** Delete the four dead types and remove the `ItemListGroup` allowlist entry/import dependencies. If either integration is intended future work, replace the class with a tracked issue/documentation entry rather than inert production code.
- **Verification:** Compile, run the YACL lint, and search for all four symbols. Confirm common config still loads from `runicskills.common.json5` and optional-mod detection behavior is unchanged.
- **Depends on:** all behavior fixes should land first; cleanup last

## 4. Needs Verification

These are not counted as findings because the available source/runtime evidence is insufficient to assert the outcome.

### [NV-01] Requested 2.0.4 source is unavailable

- GitHub's default branch resolves to `e7ea32cc4e8580164e900c5bcddc7e07c7a547f5`.
- `gradle.properties:55`, `VERSION:1`, and `CHANGELOG.md:3` all say 2.0.3. The repository exposes no `2.0.4` branch/tag/release in the queried refs; the only published tag observed was `v1.1.0`.
- Before implementation, obtain the exact 2.0.4 commit/tag from the maintainer. Re-run the 577-row inventory and remap every location if that tree differs. Do not silently assume current `master` is 2.0.4.

### [NV-02] Build and automated tests could not be executed in the audit environment

- `./gradlew test` could not download Gradle 8.10 because the execution environment blocks that host.
- An available offline Gradle 8.6 distribution failed before project configuration because its external `gradle-dependency-management-8.6.jar` was corrupt (`ZipException: zip END header not found`). This is not evidence of a repository build failure.
- Required before release: Java 17 `./gradlew clean test build`, then the dedicated-server/client smoke matrix from `docs/SMOKE_TESTS.md` with exact Minecraft/Forge versions.

### [NV-03] Projectile damage may compound on piercing-arrow impacts

- `CombatEventHandler.java:876-889` handles `ProjectileImpactEvent`, reads the arrow's current base damage, adds the passive/stealth bonus, and writes it back. A piercing arrow can produce more than one impact; if Forge posts this event for each continued entity hit in 1.20.1, each hit will add the bonus again.
- Confirm with a Piercing crossbow against aligned targets while logging `arrow.getBaseDamage()` at each impact. If it grows, move the adjustment to projectile launch and mark the arrow with persistent data so it applies exactly once. Until that event behavior is observed, do not count this as a confirmed defect.

### [NV-04] Optional-mod API and mixin compatibility matrix

- Static inspection confirms conditional registration and side guards, but it cannot prove that all optional upstream event/method signatures match every declared supported dependency version.
- Smoke-test at minimum: no optional mods; Iron's Spells only; Ars Nouveau only; Apotheosis/AttributesLib only; all three together; dedicated server without YACL; and the relevant weapon/content integrations. Treat a mixin application failure or missing optional API class as a new finding with the exact upstream version and stack trace.

## 5. Suggested execution order

1. **Lock the actual release source (NV-01)** and make CI/build usable (NV-02). Rebase line locations only if the supplied 2.0.4 tree differs.
2. **Harden coverage first (HIGH-03 test portion)** so subsequent work cannot hide behind comments; then implement Scholar's player-aware behavior.
3. **Repair inert base stats:** HIGH-04 (XP Bonus), then HIGH-05 (Enchanting Power plus Apotheosis modifier), then HIGH-06 (Iron's cooldown scaling). HIGH-05's consumer and scaling modifier must land together; shipping only the modifier leaves the pipeline inert.
4. **Repair the Iron's mana state machine:** HIGH-01 and MEDIUM-02 together, followed immediately by MEDIUM-06 cleanup. These three touch the same handler and should be one review/merge unit.
5. **Replace Apotheosis actor inference (HIGH-02).** Land scoped actor capture, both socket handlers, cleanup, and two-player tests together; do not temporarily switch to a different global heuristic.
6. **Correct deterministic calculations:** MEDIUM-01 and MEDIUM-08. Both should add pure math/unit tests before changing event code.
7. **Repair persistent Power behavior:** MEDIUM-03, then MEDIUM-04. Run the Iron's entity lifecycle/lifetime smoke tests after both.
8. **Normalize timer clocks (MEDIUM-05).** Convert all listed maps in one change so mixed clock domains are not left behind.
9. **Namespace and bound datapack overrides (MEDIUM-07).** Include migration behavior and packet boundary tests in the same change.
10. **Finish presentation and cleanup:** LOW-01, then LOW-02 last.
11. **Release gate:** run the full Java 17 build, resource tests, dedicated-server boot, client boot, two-player Apotheosis test, respawn/clone suite, and optional-mod matrix. Re-run the inventory; it must still total 577 unless removals/additions are intentional and documented.
