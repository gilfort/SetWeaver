# Changelog

All notable changes between mod versions, newest first. Each entry lists everything that changed since the
previous version. Version lines: `2.x` = Minecraft 26.3 (branch `main`), `1.x` = Minecraft 1.21.1
(branch `1.21.1`).

---

## 2.0.0 — Minecraft 26.3

**Requires:** NeoForge 26.3, Java 25. Install on server and clients.

### Changed
- Ported to Minecraft 26.3 / NeoForge 26.3.
- Vanilla attribute ids lost their `generic.` / `player.` prefix in Minecraft (e.g. `minecraft:generic.armor` →
  `minecraft:armor`). Old ids in set files and `attribute_packages.json` keep working; the editor saves the new names.
- The editor tag datapack `setweaver_custom` uses the new pack format (`min_format` / `max_format`); it is rewritten the
  next time a tag is saved.

### Upgrading a 1.21.1 world
- Copy `config/setweaver/` along with the world. Role and level of players are kept.

---

## 1.0.0 — Minecraft 1.21.1

**Install on server and clients** – the network protocol changed, older clients cannot join.

### Added
- `/setweaver set <player> <role>` without a level (keeps the current level).
- Deleting a set in the Sets Manager asks for confirmation.
- Validation lists every problem per file; the same messages appear in the server log on load.

### Changed
- Deleting sets, saving attribute packages and validating now happen on the server. Before, these touched the
  client's own config folder and did nothing on a dedicated server.
- Invalid entries in set files (unknown effects, attributes or modifiers, thresholds outside 1–4) are skipped with a
  message; amplifiers are limited to 0–9.
- `/setweaver check` accepts selectors like `@s`.
- Any item worn in an armor slot counts as a set piece, not only classic armor items.
- Tags with a `/` in their path are stored as `namespace__path__sub.json`. **Check your files:** a file name with
  more than one `__` is now read as a path with `/` (`a__b__c.json` → `a:b/c`).
- All texts are translatable (`assets/setweaver/lang/en_us.json`).
- The editor tag datapack `setweaver_custom` gets the correct pack format.

### Fixed
- Max health bonuses were cut back every 3 seconds (health capped to the base maximum).
- Single player: receiving the set sync could break set effects (shared data between client and server).
- Editor tags with unusual names could write outside the datapack folder.
- Players without role or level now see bonuses of "All Roles / All Levels" sets in the tooltip.
- Sets were loaded twice on server start; login sync was sent twice; role/level was sent every 3 seconds.
- Files are read and written as UTF-8.
- Server log messages about invalid set files are written in English instead of showing translation keys.

### Removed
- Unused template code (empty config, data generators).

---

## 0.4.0 — Minecraft 1.21.1

### Added
- Set Wizard: copy an existing set into another scope.
- Live attribute preview with human-readable values in the Set Editor.
- Attribute packages: collapse related attributes in tooltips (hold ALT), with an in-game editor.
- In-game creation and editing of custom item tags.
- **Reload** button in the Sets Manager reloads on the server and syncs all players.
- `/setweaver setyear <players> <level>`.

### Changed
- An attribute may appear several times in one threshold (e.g. addition and multiply_base).
- Role / level terminology throughout the mod.

### Fixed
- Display name reset in the Set Wizard when choosing role or level.
- Number formatting of attribute values on systems with a comma decimal separator.

---

## 0.3.0 — Minecraft 1.21.1

### Added
- First release as a standalone mod (split from Zauberei): tag-based armor set effects with roles and levels,
  Sets Manager, Set Wizard and Set Editor, tooltips with pagination, `/setweaver editor|set|check`.
