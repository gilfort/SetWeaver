# Getting Started

## Installation

1. Install NeoForge for the Minecraft version of your SetWeaver build (see [Home](Home)).
2. Put the SetWeaver jar into the `mods/` folder of the **server and every client**.
3. Start the server (or a single player world). SetWeaver creates its config folder.

## Created files

```
config/setweaver/
├── set_armor/                     set definitions
│   ├── HOW_TO_SET_EFFECTS.txt     short format reference
│   ├── _example/1/…json.disabled  example set (not loaded)
│   ├── all_roles/                 sets for all roles of one level
│   └── all_roles_all_levels/      sets for everyone
└── attribute_packages.json        tooltip grouping (see Attribute Packages)
```

## Your first set

1. Run `/setweaver editor` to open the editor.
2. Click **+ New set…** in the left column.
3. In the dialog choose the item tag (e.g. `c:armors`), the scope (role and level, or "All") and optionally a
   display name, then click **Create**.
4. In the form add effects and attributes to the thresholds (1–4 pieces) and click **Save**.
5. The server writes the file, reloads and sends the sets to all players. Put on the armor to try it.

See [Set Editor](Set-Editor) for all editor features and [Roles & Levels](Roles-and-Levels) for scopes.
