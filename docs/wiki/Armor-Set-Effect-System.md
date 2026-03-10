# Armor Set Effect System

The Zauberei mod provides a **fully data-driven armor set effect system** that allows modpack developers to define custom set bonuses for any combination of armor items, including items from other mods. The system is based on **item tags** and **JSON configuration files**, requiring **zero Java code** from modpack developers.

---

## Table of Contents

- [Overview](#overview)
- [Core Concepts](#core-concepts)
- [Folder Structure](#folder-structure)
- [JSON File Format](#json-file-format)
- [Step-by-Step Guide for Modpack Developers](#step-by-step-guide-for-modpack-developers)
- [Complete Examples](#complete-examples)
- [Attribute Modifier Types](#attribute-modifier-types)
- [Supported Effects and Attributes](#supported-effects-and-attributes)
- [Troubleshooting](#troubleshooting)
- [Technical Details](#technical-details)

---

## Overview

The armor set effect system works as follows:

1. The mod reads JSON files from a **config folder** at startup.
2. Each JSON file defines **what bonuses** a player gets when wearing a certain number of armor pieces that match a specific **item tag**.
3. Every few ticks, the mod checks what the player is wearing and applies the matching effects and attributes.

This means you can create set bonuses for **any armor** -- vanilla, Zauberei, or from any other mod -- as long as you can tag the items.

---

## Core Concepts

### Major and Year

The Zauberei mod features a progression system where each player has a **Major** (a specialization or school, e.g. `naturalist`, `alchemist`) and a **Year** (a progression level, e.g. `1`, `2`, `3`). Set bonuses are scoped to a specific major and year, meaning:

- A player with Major `naturalist` and Year `3` will only receive bonuses defined under the `naturalist/3/` folder.
- This allows you to create **progression-based set bonuses** that unlock as players advance.

### Item Tags

The system identifies armor sets using **Minecraft item tags** (not armor materials). This is powerful because:

- You can group items from **different mods** into one set.
- You can create **multiple overlapping sets** (an item can be in multiple tags).
- Tags are standard Minecraft/NeoForge data and can be created via datapacks.

A tag like `zauberei:magiccloth_armor` groups all items that belong to the Magic Cloth Armor set.

### Part Thresholds

Set bonuses are defined per **number of matching pieces worn**. You can define different bonuses for wearing 1, 2, 3, or 4 pieces:

| Key | Meaning |
|-----|---------|
| `1Part` | Bonus when wearing exactly 1 matching piece |
| `2Part` | Bonus when wearing exactly 2 matching pieces |
| `3Part` | Bonus when wearing exactly 3 matching pieces |
| `4Part` | Bonus when wearing all 4 matching pieces |

> **Note:** You do not have to define all thresholds. For example, you can define only `4Part` if you want the bonus to apply only when the full set is worn. The system only applies the bonus for the **exact count** of pieces worn -- it does **not** stack lower thresholds.

---

## Folder Structure

All set definitions are placed in the game config directory:

```
config/
  zauberei/
    set_armor/
      {major}/
        {year}/
          {namespace}__{tagpath}.json
```

### Filename Convention

The JSON filename encodes the **item tag** it references:

- Replace the `:` in the tag with `__` (double underscore).
- Add `.json` at the end.

| Item Tag | Filename |
|----------|----------|
| `zauberei:magiccloth_armor` | `zauberei__magiccloth_armor.json` |
| `arsnouveau:tier2armor` | `arsnouveau__tier2armor.json` |
| `minecraft:leather_armor` | `minecraft__leather_armor.json` |
| `mymod:custom_set` | `mymod__custom_set.json` |

> **Important:** The filename **must** contain exactly one `__` (double underscore) to separate namespace and path. Files without `__` will be skipped with an error.

---

## JSON File Format

Each JSON file defines the set bonuses using a `parts` object:

```json
{
  "parts": {
    "1Part": { },
    "2Part": { },
    "3Part": { },
    "4Part": { }
  }
}
```

Each part can contain **Effects** (potion effects) and/or **Attributes** (stat modifiers).

### Effects

Potion effects are applied continuously while the player wears the required number of pieces.

```json
{
  "Effects": [
    {
      "Effect": "minecraft:speed",
      "Amplifier": 0
    },
    {
      "Effect": "minecraft:night_vision",
      "Amplifier": 0
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `Effect` | String | The effect registry name (e.g. `minecraft:speed`, `mymod:custom_effect`). If no namespace is given, `minecraft:` is assumed. |
| `Amplifier` | Integer | The effect level minus 1. `0` = Level I, `1` = Level II, etc. Maximum supported is `4` (Level V). |

Effects are re-applied every ~3 seconds (60 ticks) with a 10-second duration, creating a seamless continuous effect.

### Attributes

Attribute modifiers change player stats (e.g. max health, movement speed, attack damage).

```json
{
  "Attributes": {
    "minecraft:generic.max_health": {
      "value": 4.0,
      "modifier": "addition"
    },
    "minecraft:generic.movement_speed": {
      "value": 0.1,
      "modifier": "multiply_base"
    }
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| Key (attribute name) | String | The attribute registry name (e.g. `minecraft:generic.max_health`). |
| `value` | Double | The numeric value of the modifier. |
| `modifier` | String | The operation type: `addition`, `multiply_base`, or `multiply_total`. |

---

## Step-by-Step Guide for Modpack Developers

### Step 1: Plan Your Set

Decide:
- Which armor pieces should form a set?
- What major and year should this set be available for?
- What bonuses should each threshold grant?

### Step 2: Create or Use an Item Tag

You need an **item tag** that groups all armor pieces of your set. You can either:

**A) Use an existing tag** from a mod (check the mod's `data/{namespace}/tags/item/` folder).

**B) Create a new tag** via a datapack. Create a file at:

```
data/{your_namespace}/tags/item/{your_tag_name}.json
```

Example -- `data/mypack/tags/item/fire_warrior_armor.json`:

```json
{
  "replace": false,
  "values": [
    "minecraft:netherite_helmet",
    "minecraft:netherite_chestplate",
    "minecraft:netherite_leggings",
    "minecraft:netherite_boots"
  ]
}
```

This creates the tag `mypack:fire_warrior_armor`.

### Step 3: Create the Folder Structure

Navigate to your Minecraft instance's `config/` folder and create:

```
config/zauberei/set_armor/{major}/{year}/
```

For example, for the `naturalist` major at year `3`:

```
config/zauberei/set_armor/naturalist/3/
```

> **Tip:** The `config/zauberei/set_armor/` base directory is automatically created by the mod on first launch if it does not exist.

### Step 4: Write the JSON File

Create a JSON file with the correct naming convention in the folder from Step 3.

For the tag `mypack:fire_warrior_armor` the filename is: `mypack__fire_warrior_armor.json`

Full path:
```
config/zauberei/set_armor/naturalist/3/mypack__fire_warrior_armor.json
```

Write your set definition:

```json
{
  "parts": {
    "2Part": {
      "Effects": [
        {
          "Effect": "minecraft:fire_resistance",
          "Amplifier": 0
        }
      ]
    },
    "4Part": {
      "Effects": [
        {
          "Effect": "minecraft:fire_resistance",
          "Amplifier": 0
        },
        {
          "Effect": "minecraft:strength",
          "Amplifier": 1
        }
      ],
      "Attributes": {
        "minecraft:generic.max_health": {
          "value": 6.0,
          "modifier": "addition"
        }
      }
    }
  }
}
```

### Step 5: Restart the Server

The set definitions are loaded at server startup. After creating or modifying JSON files, **restart the server** (or the singleplayer world) for changes to take effect.

Check the server log for confirmation messages:
```
[Zauberei] Loaded set definition: major=naturalist, year=3, tag=mypack:fire_warrior_armor
```

Or error messages if something went wrong:
```
[Zauberei] Unknown effect 'minecraft:invalid_effect' in ... -- skipped
[Zauberei] Unknown attribute 'minecraft:invalid.attribute' in ... -- removed
```

---

## Complete Examples

### Example 1: Simple Potion Effect Set

A basic set that grants Speed I with 2 pieces and Speed II + Jump Boost I with all 4 pieces.

**File:** `config/zauberei/set_armor/naturalist/1/zauberei__apprentice_robes.json`

```json
{
  "parts": {
    "2Part": {
      "Effects": [
        {
          "Effect": "minecraft:speed",
          "Amplifier": 0
        }
      ]
    },
    "4Part": {
      "Effects": [
        {
          "Effect": "minecraft:speed",
          "Amplifier": 1
        },
        {
          "Effect": "minecraft:jump_boost",
          "Amplifier": 0
        }
      ]
    }
  }
}
```

### Example 2: Attribute-Based Set with Scaling

A tank set that grants increasing health and armor toughness.

**File:** `config/zauberei/set_armor/alchemist/2/mymod__heavy_plate.json`

```json
{
  "parts": {
    "1Part": {
      "Attributes": {
        "minecraft:generic.armor_toughness": {
          "value": 1.0,
          "modifier": "addition"
        }
      }
    },
    "2Part": {
      "Attributes": {
        "minecraft:generic.max_health": {
          "value": 4.0,
          "modifier": "addition"
        },
        "minecraft:generic.armor_toughness": {
          "value": 2.0,
          "modifier": "addition"
        }
      }
    },
    "3Part": {
      "Attributes": {
        "minecraft:generic.max_health": {
          "value": 6.0,
          "modifier": "addition"
        },
        "minecraft:generic.armor_toughness": {
          "value": 3.0,
          "modifier": "addition"
        },
        "minecraft:generic.knockback_resistance": {
          "value": 0.25,
          "modifier": "addition"
        }
      }
    },
    "4Part": {
      "Attributes": {
        "minecraft:generic.max_health": {
          "value": 10.0,
          "modifier": "addition"
        },
        "minecraft:generic.armor_toughness": {
          "value": 5.0,
          "modifier": "addition"
        },
        "minecraft:generic.knockback_resistance": {
          "value": 0.5,
          "modifier": "addition"
        }
      },
      "Effects": [
        {
          "Effect": "minecraft:resistance",
          "Amplifier": 0
        }
      ]
    }
  }
}
```

### Example 3: Cross-Mod Armor Set

Using a custom tag to combine armor from different mods into one set.

**Step 1 -- Create the item tag** (in your datapack):

`data/mypack/tags/item/arcane_mix_armor.json`:
```json
{
  "replace": false,
  "values": [
    "arsnouveau:novice_helmet",
    "zauberei:magiccloth_chestplate",
    "ars_elemental:fire_leggings",
    "minecraft:golden_boots"
  ]
}
```

**Step 2 -- Create the set definition:**

`config/zauberei/set_armor/naturalist/3/mypack__arcane_mix_armor.json`:
```json
{
  "parts": {
    "4Part": {
      "Effects": [
        {
          "Effect": "minecraft:night_vision",
          "Amplifier": 0
        },
        {
          "Effect": "minecraft:haste",
          "Amplifier": 1
        }
      ],
      "Attributes": {
        "minecraft:generic.movement_speed": {
          "value": 0.15,
          "modifier": "multiply_base"
        }
      }
    }
  }
}
```

---

## Attribute Modifier Types

| Modifier Value | Operation | Description | Example |
|----------------|-----------|-------------|---------|
| `addition` | ADD_VALUE | Adds a flat value to the base stat. | +4.0 max health = 2 extra hearts |
| `multiply_base` | ADD_MULTIPLIED_BASE | Multiplies the **base** value and adds it. | 0.1 = +10% of base movement speed |
| `multiply_total` | ADD_MULTIPLIED_TOTAL | Multiplies the **total** value (after all other modifiers). | 0.1 = +10% of final movement speed |

### Common Attribute Registry Names

| Attribute | Registry Name |
|-----------|---------------|
| Max Health | `minecraft:generic.max_health` |
| Movement Speed | `minecraft:generic.movement_speed` |
| Attack Damage | `minecraft:generic.attack_damage` |
| Attack Speed | `minecraft:generic.attack_speed` |
| Armor | `minecraft:generic.armor` |
| Armor Toughness | `minecraft:generic.armor_toughness` |
| Knockback Resistance | `minecraft:generic.knockback_resistance` |
| Luck | `minecraft:generic.luck` |

You can also use attributes added by other mods using their full registry names.

---

## Supported Effects and Attributes

- **Effects:** Any mob effect registered in the game is supported, including effects from other mods. Use the full registry name (e.g. `minecraft:regeneration`, `arsnouveau:mana_regen`).
- **Attributes:** Any attribute registered in the game is supported, including attributes from other mods.

The system validates all effects and attributes at load time and logs warnings or errors for invalid entries. The rest of the file will still work even if some entries are invalid.

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Set bonus not applying | Check that the player's **major** and **year** match the folder path. |
| `Invalid filename` error in log | Ensure the filename uses `__` (double underscore) to separate namespace and tag path. |
| `Unknown effect` error in log | Verify the effect registry name is correct and the mod providing it is loaded. |
| `Unknown attribute` error in log | Verify the attribute registry name is correct and the mod providing it is loaded. |
| `No 'parts' found` error | Check your JSON syntax. The root object must contain a `"parts"` key. |
| `Invalid year folder name` error | The year folder name must be a valid integer (e.g. `1`, `2`, `3`). |
| Effects flickering | This should not happen. Effects are applied with a 10-second duration and refreshed every ~3 seconds. If it occurs, check for conflicting mods. |
| Bonuses not stacking from multiple sets | Multiple sets **do** stack. Each set modifiers are applied independently. |

---

## Technical Details

For developers interested in how the system works internally:

1. **ZaubereiReloadListener** walks the `config/zauberei/set_armor/` directory tree at startup, parses each JSON file using Gson into `ArmorSetData` objects, validates all effects and attributes against the game registries, and stores them in `ArmorSetDataRegistry`.

2. **ArmorSetDataRegistry** is an in-memory map keyed by `major:year:namespace:tagpath`. It provides lookup methods to find all registered tags for a given major/year combination.

3. **ArmorEffects** hooks into the `PlayerTickEvent.Post` event and runs every 60 ticks (~3 seconds). It reads the player's current major and year, checks which registered item tags match the player's worn armor, and applies the corresponding effects and attribute modifiers.

4. **Attribute modifiers** are applied as **transient modifiers** (not saved to NBT) with a ResourceLocation in the `zauberei` namespace. Old modifiers are removed before new ones are applied to prevent stacking issues.

5. **Player data** (major, year) is stored via NeoForge's AttachedData system using CompoundTag on the ServerPlayer.

### Architecture Diagram

```
config/zauberei/set_armor/
        |
        v
ZaubereiReloadListener  --->  ArmorSetDataRegistry (in-memory map)
   (reads JSON at startup)          |
                                    v
                             ArmorEffects (PlayerTickEvent.Post)
                                    |
                                    v
                          Check worn armor against tags
                                    |
                          Apply Effects + Attributes
```

### Source Files

- `ArmorSetData.java` -- Data model for JSON deserialization
- `ArmorSetDataRegistry.java` -- In-memory registry for loaded set definitions
- `ZaubereiReloadListener.java` -- JSON file loader and validator
- `ArmorEffects.java` -- Tick-based effect and attribute application logic
- `PlayerDataHelper.java` -- Helper for reading player major and year

---

*This documentation applies to the Zauberei mod for Minecraft 1.21.1 with NeoForge.*
