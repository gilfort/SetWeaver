# Armor Set JSON Format

The editor writes these files for you; the format matters for bulk editing.

## File name and location

```
config/setweaver/set_armor/<scope folder>/<namespace>__<path>.json
```

The file name is the item tag: the first `__` stands for `:`, every further `__` for `/`.

| Tag | File name |
|---|---|
| `c:armors` | `c__armors.json` |
| `c:armors/iron` | `c__armors__iron.json` |
| `setweaver:heavy_armor` | `setweaver__heavy_armor.json` |

Scope folders: see [Roles & Levels](Roles-and-Levels). Only files ending in `.json` are loaded.

## Example

```json
{
  "displayName": "Iron Guardian",
  "parts": {
    "2Part": {
      "Effects": [ { "Effect": "minecraft:resistance", "Amplifier": 0 } ],
      "Attributes": [ { "attribute": "minecraft:armor", "value": 2.0, "modifier": "addition" } ]
    },
    "4Part": {
      "Effects": [
        { "Effect": "minecraft:resistance", "Amplifier": 1 },
        { "Effect": "minecraft:fire_resistance", "Amplifier": 0 }
      ],
      "Attributes": [
        { "attribute": "minecraft:armor", "value": 4.0, "modifier": "addition" },
        { "attribute": "minecraft:armor_toughness", "value": 0.2, "modifier": "multiply_base" }
      ]
    }
  }
}
```

## Fields

| Field | Type | Description |
|---|---|---|
| `displayName` | string, optional | Name in tooltips; without it the tag path is used (`c:iron_armors` → "Iron") |
| `parts` | object | Thresholds `1Part` … `4Part` |
| `Effects[].Effect` | string | Effect id, e.g. `minecraft:speed` |
| `Effects[].Amplifier` | int 0–9 | 0 = level I, 9 = level X |
| `Attributes[].attribute` | string | Attribute id, e.g. `minecraft:max_health` |
| `Attributes[].value` | number | Amount |
| `Attributes[].modifier` | string | `addition`, `multiply_base` or `multiply_total` |

The old map form `"Attributes": { "<id>": { "value": …, "modifier": … } }` is still read. Vanilla attribute ids
of Minecraft 1.21.1 (`minecraft:generic.armor`, `minecraft:player.block_interaction_range`, …) are accepted and
matched under their current name (`minecraft:armor`); the editor writes the current names.

## Thresholds

- The **highest reached** threshold is active; 3 pieces with `2Part` and `4Part` defined → `2Part`.
- Each threshold is complete on its own; reaching `4Part` replaces `2Part`.
- Effects last 10 seconds and are refreshed every 3 seconds while worn.

## Operations

| `modifier` | Value `0.4` on armor | Meaning |
|---|---|---|
| `addition` | +0.4 armor | added to the base value |
| `multiply_base` | +40 % of base | `(base + additions) × 0.4` is added |
| `multiply_total` | ×1.4 | the final value is multiplied by `1 + value` |
