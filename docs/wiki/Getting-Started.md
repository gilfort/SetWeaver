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

1. Run `/setweaver editor` to open the Sets Manager.
2. Click **Create** to start the Set Wizard.
3. **Step 1 – Wizard:** choose the item tag (e.g. `c:armors`), optionally a display name, and the scope
   (role and level, or "All").
4. **Step 2 – Editor:** pick the tab for the number of pieces (1–4), add effects and attributes, click **Save**.
5. The server writes the file, reloads and sends the sets to all players. Put on the armor to try it.

See [Set Editor](Set-Editor) for all editor features and [Roles & Levels](Roles-and-Levels) for scopes.
