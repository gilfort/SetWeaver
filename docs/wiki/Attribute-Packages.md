# Attribute Packages

Packages group related attributes into one collapsed tooltip line.

Without a package:
```
  +4 Armor
  +2 Armor Toughness
```
With a "Defense" package:
```
  Defense Increased [ALT]
```
Holding **ALT** expands it again.

## Rules

- A package collapses only if **at least 2** of its attributes are in the same set bonus.
- If an attribute is in several packages, the matching package with the **lowest priority value** wins.
- Packages only change tooltips, not the bonuses themselves.

## Editor

Sets Manager → **Packages**.

| Action | Description |
|---|---|
| **+ Package** | New package |
| **+ Attribute** | Add an attribute to the selected package |
| ●/○ | Enable / disable the selected package |
| ✖ | Delete the selected package or one of its attributes |
| Name, Priority | Edit fields; lower priority wins |
| **Save** | Sends all packages to the server, which saves and syncs them |

## Config file

`config/setweaver/attribute_packages.json` (created with a disabled "Defense" example):

```json
{
  "packages": [
    {
      "name": "Defense",
      "enabled": true,
      "priority": 10,
      "attributes": [ "minecraft:generic.armor", "minecraft:generic.armor_toughness" ]
    }
  ]
}
```

| Field | Default | Description |
|---|---|---|
| `name` | – | Text shown in the tooltip |
| `enabled` | `true` | Whether the package is used |
| `priority` | `100` | Lower wins |
| `attributes` | `[]` | Attribute ids |
