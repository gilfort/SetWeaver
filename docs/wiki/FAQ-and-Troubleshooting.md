# FAQ & Troubleshooting

### How do I give different armor different bonuses?
Use one set per item tag, e.g. `c:armors/iron` and `c:armors/diamond`, or create your own tag
([Custom Tags](Custom-Tags)).

### Can one item be in several sets?
Yes. Every set whose tag contains the item applies; the tooltip pages through them with SHIFT + scroll.

### Do thresholds stack?
No. The highest reached threshold replaces the lower ones. Different sets do stack.

### How do I give a bonus to everyone?
Use scope "All Roles / All Levels" (folder `all_roles_all_levels/`).

### How do I remove a set?
Select it in the Sets Manager and click ✖, or delete the file and run `/reload`.

### My new tag does not show up.
Run `/reload` after saving a tag.

### Changes made directly in the files are not active.
Run `/reload` or click **Reload** in the Sets Manager.

## Common problems

**Set bonus not active**
1. `/setweaver check @s` – do role and level match the scope?
2. Is the armor in the tag? (Sets Manager → Tags)
3. Does the file pass **Validate**?
4. Are enough pieces worn for the first threshold?

**Attribute values look wrong** – check the operation: `addition` is flat, `multiply_base` `0.4` = +40 %,
`multiply_total` `0.1` = ×1.1.

**Package does not collapse** – at least two of its attributes must be in the same bonus.

## Files

| File | Path |
|---|---|
| Set definitions | `config/setweaver/set_armor/…/<namespace>__<path>.json` |
| Attribute packages | `config/setweaver/attribute_packages.json` |
| Custom tags | `<world>/datapacks/setweaver_custom/data/<ns>/tags/item/<name>.json` |
