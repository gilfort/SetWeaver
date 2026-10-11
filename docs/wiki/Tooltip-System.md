# Tooltip System

Every item that belongs to a loaded set shows `[SHIFT to view Set Bonus]`. With **SHIFT**:

```
[Set Bonus 2/4]
[Set: Iron Guardian]
- Resistance I
[Bonus Attributes]
  Armor +2
```

- `2/4`: 2 matching pieces worn, the highest threshold of the set is 4.
- Below the first threshold: `Equip N more piece(s) for a bonus`.
- If no set of the item applies to your role and level: `[No set bonus for your current role]`.

## Several sets on one item

```
[Set Bonus 2/4]  ◄ 1/3 — Scroll ►
```

In an inventory, **SHIFT + mouse wheel** over the item pages through its sets.

## Attributes

| Operation | Example |
|---|---|
| `addition` | `Armor +4` |
| `multiply_base` | `Armor +40% (base)` |
| `multiply_total` | `Armor +10% (total)` |

Values are formatted by the attribute itself, so percentage attributes of other mods show as percentages.
Grouped attributes collapse into one line, see [Attribute Packages](Attribute-Packages).

## Colors

| Color | Element |
|---|---|
| Aqua | header and `[Bonus Attributes]` |
| Gold | set name |
| Dark purple | effects |
| Green / dark green | attributes / `(base)`, `(total)` |
| Yellow | expanded package name |
| Gray | hints |
