# Custom Tags

SetWeaver can create item tags in-game, e.g. a mixed chainmail + iron set or one modded armor set.

## Creating a tag

1. Editor → **Item tags** → **+ New tag**.
2. Enter **Namespace** (default `setweaver`) and **Tag Name** (e.g. `heavy_armor`; `a-z 0-9 _ - . /`).
3. **+ Item** adds armor items (everything that can be worn in a helmet, chestplate, leggings or boots slot); tick
   several at once or search (e.g. `leather`) and use **Select all shown**.
4. **Save** – the server writes the tag.
5. Run `/reload` so Minecraft loads it. It then shows up in the tag browser and the Set Wizard.

## Editing

In **Item tags**, tags in the `setweaver` namespace can be edited (others are read-only, marked with a lock). Saving overwrites the tag file.

## Files

Tags are stored in a datapack inside the world:

```
<world>/datapacks/setweaver_custom/
├── pack.mcmeta
└── data/<namespace>/tags/item/<name>.json
```

```json
{ "replace": false, "values": [ "minecraft:chainmail_helmet", "minecraft:iron_helmet" ] }
```

`"replace": false` merges the tag with other tags of the same id. Unknown item ids are dropped by the server.
