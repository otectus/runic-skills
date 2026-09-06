# Tinkers' Construct pack rules — `tconstruct_rules` datapack format

A datapack-driven way for a pack author to state, in plain data, what a Tinkers' Construct tool
requires to use and whether a Tinkers' station craft may be paid a bonus copy — without writing
Java. Parsed and enforced by `TConstructRulesLoader` / `PackRuleIndex` / `PackRule`
(`src/main/java/com/otectus/runicskills/common/rules/`), and applied on the Tinkers' side by
`TConstructPackRuleSource` and `TConstructRequirementResolver`
(`src/main/java/com/otectus/runicskills/integration/tconstruct/`).

No default rule ships with the mod — `src/main/resources/data/runicskills` has no
`tconstruct_rules` folder. Everything in this document only takes effect once a pack adds files of
its own, and the material-tier lock this schema can replace is itself opt-in: it only applies at
all when `enableTConstructLockItems` is turned on (default `false`).

## Where files go

```
data/<namespace>/runicskills/tconstruct_rules/*.json
```

The loader is a plain `SimpleJsonResourceReloadListener` registered on every install, Tinkers' or
not — it never names a `slimeknights` class, so it loads and validates identically without that mod
(`TConstructRulesLoader.java:62-67`). It is registered unconditionally in the `AddReloadListenerEvent`
handler (`registry/events/PlayerLifecycleHandler.java:226`).

## Document schema

| Field | Type | Required | Notes |
|---|---|---|---|
| `schema_version` | int | yes | Must equal `1`, the only version this release understands. |
| `requires_mods` | array of mod ids | no | If any named mod is not loaded, the whole file is skipped (not an error). |
| `rules` | array | no | Absent (or empty) yields zero rules for the document; only `schema_version` is enforced present. |

Unknown fields anywhere in a document, a rule, or a `match` object make the loader refuse the file
by name, naming the field (`TConstructRulesLoader.rejectUnknown`).

## Rule schema

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `id` | resource id | yes | — | Merge key across files and packs. |
| `kind` | string | yes | — | `use_requirement` or `craft_reward_policy`. |
| `priority` | int | no | `0` | Higher wins within a kind. |
| `match` | object | no | matches everything | See below. |
| `requirements` | object (skill → level) | no | `{}` | Known on any rule; only read and validated on `use_requirement` (skill names must be real, levels ≥ 0). Silently accepted and ignored on `craft_reward_policy`. |
| `composition` | string | no | — | Known on any rule; only read and validated on `use_requirement`, where only `"replace_automatic"` is accepted in schema 1. Silently accepted and ignored on `craft_reward_policy`. |
| `allow_extra_output` | bool | required for `craft_reward_policy` | — | See precedence below — an allow does not override the mandatory exclusions. |

## `match` schema

| Field | Type | Applies to | Notes |
|---|---|---|---|
| `definitions` | array of resource ids | `use_requirement` | The tool's definition id. |
| `materials` | array of resource ids | `use_requirement` | Any of the tool's material ids. |
| `roles` | array of role names | `use_requirement` | `EquipmentRole` names, lowercase, plus the alias `mining` → `DIGGER`. |
| `native_material_tiers` | array of ints ≥ 0 | `use_requirement` | The tool's highest functional material tier. |
| `actions` | array of `LockAction` names | `use_requirement` | e.g. `USE`, `ATTACK`, `EQUIP`. |
| `equipment_provider` | string | `craft_reward_policy` | Which equipment adapter claimed the result item. |
| `items` | array of item ids | `craft_reward_policy` | The result item. An id whose mod is not installed is skipped (the rule is dropped, not an error); an id whose mod **is** installed but is unknown to it is invalid. |

Every populated selector is ANDed together; within one selector, any one listed value matches
(`PackRule.Match.matchesTool` / `matchesResult`, `common/rules/PackRule.java:56-102`).

## Limits

| Limit | Value | Source |
|---|---|---|
| Document size | 256 KiB (measured on the re-serialised JSON) | `TConstructRulesLoader.MAX_DOCUMENT_CHARS` |
| Rules per reload | 2,048, across every namespace together | `TConstructRulesLoader.MAX_RULES_PER_RELOAD` |
| Values per selector array or per `requirements` map | 64 | `TConstructRulesLoader.MAX_SELECTOR_VALUES` |

## Validation rules

- **Unknown field → refused by name.** Any field not in the known set for a document, rule, or
  `match` object rejects that file, naming the field.
- **A rule for an absent mod is skipped; a rule naming something unknown while its mod *is*
  installed is invalid.** `requires_mods` at the document level and an item id under `items` both
  follow this split (`TConstructRulesLoader.java:252-256`, `:326-335`).
- **Duplicate id at the same priority is rejected — both copies, not one.** Deciding by filesystem
  order is explicitly what this refuses (`TConstructRulesLoader.parse`, `:205-213`).
- **Any unreadable file refuses the whole reload**, and the previously loaded rules stay in force —
  *unless* there is no previous ruleset to keep, in which case the files that did parse are
  installed and the failure is only reported (`TConstructRulesLoader.install`, `:132-146`).

## Precedence

For a `use_requirement` lock on a native Tinkers' tool (`TConstructRequirementResolver`, §7.2 in
its own javadoc):

1. **An explicit pack rule** matching this tool's definition, materials, roles, tier and action.
2. **An existing configured item-id lock.** If one exists, this provider declines outright and the
   ordinary id-lock path decides instead — a manual override replaces the generated rule rather
   than being combined with it.
3. **The automatic material-tier profile**, only when `enableTConstructLockItems` is `true`.
4. Otherwise the tool is allowed.

A pack rule that requires nothing (`requirements: {}`) is itself a valid explicit allow — it is how
a pack exempts a tool the automatic profile would otherwise have locked.

For `craft_reward_policy`, **only a refusal is honoured.** `allow_extra_output: false` denies a
bonus copy for the matched result outright. `allow_extra_output: true` is logged and kept as a
legitimate statement that the pack does not object, but it **cannot** lift the mandatory
exclusions in `CraftRewardPolicy` — equipment (unstackable or damageable results), items carrying
an inventory/fluid/energy capability, and (unless the result also carries the
`runicskills:craft_reward_allowed` tag) NBT-carrying results or single-ingredient compressions
still deny a bonus copy regardless of what the rule says
(`common/crafting/CraftRewardPolicy.java:65-96`, `TConstructRulesLoader.java:306-314`).

## Reload behaviour

`PackRuleIndex` is immutable and swapped whole on a successful reload, never mutated in place, so a
lookup never observes a half-loaded ruleset. It carries a `revision` that increments once per
successful install and never rolls back, so a stale profile or preview computed against an old
ruleset can tell it is stale (`common/rules/PackRuleIndex.java:14-31`).

**Equal-priority disagreement produces no verdict**, not a filesystem-order pick. If two loaded
rules of the same priority both match a lookup and disagree, neither applies; the conflict is
logged once, naming both ids, and the automatic profile (or "no reward") is used instead
(`PackRuleIndex.winner`, `:148-167`).

## Two worked examples

### `use_requirement` — a training gate on tier-3 mining tools

```json
{
  "schema_version": 1,
  "requires_mods": ["tconstruct"],
  "rules": [
    {
      "id": "examplepack:pickaxe_training",
      "kind": "use_requirement",
      "priority": 100,
      "match": { "roles": ["mining"], "native_material_tiers": [3] },
      "requirements": { "tinkering": 16, "endurance": 8 },
      "composition": "replace_automatic"
    }
  ]
}
```

Any tool holding the `mining` role (the schema's alias for `EquipmentRole.DIGGER`) built from a
tier-3 material requires Tinkering 16 and Endurance 8 to use, replacing whatever the automatic
material-tier profile would otherwise have computed for it.

### `craft_reward_policy` — deny bonus copies for a specific result

```json
{
  "schema_version": 1,
  "rules": [
    {
      "id": "examplepack:no_bonus_smeltery_controllers",
      "kind": "craft_reward_policy",
      "priority": 10,
      "match": { "items": ["tconstruct:smeltery_controller"] },
      "allow_extra_output": false
    }
  ]
}
```

No `requires_mods` is needed here: `items` selectors already skip themselves quietly when their
mod is absent (`TConstructRulesLoader.java:324-336`), so the file loads on every install and simply
never matches anything on a server without Tinkers' Construct.
