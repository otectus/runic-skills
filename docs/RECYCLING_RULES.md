# Recycling Rules — grindstone salvage datapack format

Recycling rules govern what materials return when Resource Efficiency, Salvage Expert, Disassembler, or Salvage Master apply at a grindstone, and what happens when an item breaks in the player's hands or inventory.

## Format

Place `.json` files under `data/<namespace>/runicskills/recycling/`. Each file describes one input item and what it yields.

### JSON schema

```json
{
  "input": "minecraft:crafting_table",
  "consumed": 1,
  "outputs": [ 
    { "item": "minecraft:oak_planks", "count": 2 }
  ],
  "max_recovery": 2,
  "requires_empty_nbt": true
}
```

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `input` | item ID | yes | — | The item consumed to retrieve materials |
| `consumed` | int | no | `1` | How many of the input item one salvage operation consumes; must be ≥1 |
| `outputs` | array | yes | — | Non-empty list of `{"item": "...", "count": N}` pairs. An output naming an absent item is silently dropped from the outputs list (useful for rules spanning multiple modpacks); if all outputs drop, the entire rule is skipped. Each `count` must be between 1 and 64. |
| `max_recovery` | int | no | sum of output counts | Ceiling on total items one salvage may return before perk multipliers (e.g., Salvage Expert). Pinned at most 64. |
| `requires_empty_nbt` | bool | no | `true` | When `true`, an enchanted or renamed item is rejected (the item carries extra value beyond its materials). When `false`, NBT is ignored. |

### Validation

- **Unknown fields are rejected.** A misspelled `"output"` is caught at load time, not silently skipped. This catches mistakes early.
- **Absent items are skipped quietly.** A rule for `modname:thing` in a modpack that does not include the mod is normal; the rule simply does not apply.
- **Load failure is non-fatal.** If one rule file fails to parse (syntax error, invalid field, etc.), the file is logged as WARN and the rule is skipped. Other rules continue to load. If a reload fails part-way, the previous rules are retained and the new ones are discarded.

## Shipped defaults

Runic Skills ships recycling rules under `data/runicskills/runicskills/recycling/`. Addons and modpack authors can extend with their own rules under any namespace (e.g., `data/mypack/runicskills/recycling/my_custom_item.json`).

| Item | Consumed | Returns | Max | Notes |
|---|---|---|---|---|
| Crafting Table | 1 | 2x Oak Plank | 2 | Conservative recovery (~67% of recipe) |
| Furnace | 1 | 4x Cobblestone | 4 | Full recovery |
| Smoker | 1 | 4x Cobblestone, 2x Oak Log | 6 | Full recovery |
| Blast Furnace | 1 | 2x Iron Ingot, 1x Cobblestone | 3 | Conservative recovery (~75% of recipe) |
| Grindstone | 1 | 1x Stick, 1x Oak Plank | 2 | Conservative recovery (~67% of recipe) |
| Anvil (chipped) | 1 | 8x Iron Ingot | 8 | Full recovery |
| Anvil (damaged) | 1 | 4x Iron Ingot | 4 | Reduced recovery |
| Iron Pickaxe | 1 | 9x Iron Nugget | 9 | Full recovery from pristine tool (~2.8 ingots at nugget rate) |
| Iron Sword | 1 | 4x Iron Nugget | 4 | Full recovery from pristine tool (~1.25 ingots at nugget rate) |
| Golden Pickaxe | 1 | 9x Gold Nugget | 9 | Full recovery from pristine tool |
| Golden Sword | 1 | 4x Gold Nugget | 4 | Full recovery from pristine tool |
| Diamond Pickaxe | 1 | 1x Diamond | 1 | Conservative recovery (one diamond per pick) |

All shipped defaults require `requires_empty_nbt: true` — an enchanted or renamed item is too valuable to salvage at material value.

## How recycling works for the player

### At the grindstone (Resource Efficiency, Salvage Expert)

1. Place an item in the top slot.
2. If you have the Resource Efficiency or Salvage Expert perk:
   - The grindstone checks for a matching rule in the allowlist.
   - If found, it rolls the perk's chance. On success, the output is shown in the result slot.
   - On take, the input is consumed once and the output is delivered.
3. If no rule matches, the grindstone behaves normally (removes enchantments).

### On item break (Disassembler, Salvage Master)

1. An item in your hands or inventory breaks (reaches 0 durability).
2. If you have Disassembler (hand-break) or Salvage Master (inventory-break):
   - The mod checks for a matching rule in the allowlist.
   - If found, it rolls the perk's chance. On success, the materials appear in your inventory.
3. If no rule matches, the item is lost normally.

## Reload behaviour

Recycling rules are read from datapacks on every `/reload` (singleplayer) or server restart. The `RecyclingRuleLoader` is a `SimpleJsonResourceReloadListener`, so it participates in the standard datapack reload chain.

**Reload failure:** if a rule file fails to parse, it is logged (once per file per reload) and the rule is skipped. Other rules continue to load. If the entire load fails, the previous rules are retained — salvage does not shut down mid-reload.

## Extending with your own rules

Ship rule files under your namespace to add salvage options for your mod's items:

```
datapacks/mypack/data/mymod/runicskills/recycling/custom_block.json
{
  "input": "mymod:custom_crafting_block",
  "consumed": 1,
  "outputs": [
    { "item": "mymod:ingot", "count": 3 },
    { "item": "minecraft:stone", "count": 2 }
  ],
  "max_recovery": 5,
  "requires_empty_nbt": true
}
```

Runic Skills will automatically load and apply it alongside shipped defaults and rules from other addons.
