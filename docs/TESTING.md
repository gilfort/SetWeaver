# Test plan

There are no GameTests yet. Manual tests cover each change: run `./gradlew runClient` (single player) or
`./gradlew runServer` plus a client, and tick off the checklist of the change.

---

## Editor redesign (branch `feature/editor-redesign`, 2.1.0)

GameTests: none. Automated so far: `build`.

- [ ] `/setweaver editor` opens the browser; GUI scales 2, 3, 4 and auto: nothing overlaps, columns collapse to
      strips (←) when an entry is selected and open again on click; on small windows only one list column is open.
- [ ] Sets: search filters the tag list; `!n` markers match the Validation entry; tag overview lists scopes and items.
- [ ] **+ New set…**: tag picker (icons, search), role picker incl. typing a new role, level −/+ and "All levels";
      "role without level" is refused; Create opens an unsaved set (● marker in the scope list).
- [ ] Set form: display name; per threshold + Effect (icons) with −/+ level and ✕, + Attribute with value field, −/+,
      operation cycle and result line, Copy…; changed fields get an orange dot; invalid value turns red and Save stays
      disabled; Save writes the file and the form reloads ("Saved").
- [ ] Multi-select: + Effect / + Attribute / + Item tick several entries, already added ones are greyed out,
      search "leather" + Select all shown + Add adds all four pieces.
- [ ] Save, Discard, Copy to…, Delete react to clicks in sets, packages and tags.
- [ ] Discard, leaving with unsaved changes (other scope, other tag, Esc) asks first.
- [ ] Copy to… another scope (new) and onto an existing scope (asks to replace); Delete asks and removes the file.
- [ ] Attribute packages: new, rename, priority −/+, enabled, add / remove attributes, delete; Save stores all.
- [ ] Item tags: search, read-only tags show a lock and no buttons; + New tag with namespace / name / items, Save,
      `/reload`, the tag appears; editing a `setweaver:` tag removes / adds items.
- [ ] Validation: broken file listed with ✘ / ⚠ and messages; clicking a loaded file opens its set; "Check again".
- [ ] Two players: a save by one updates the open editor of the other.

## Upgrade to Minecraft 26.3 (branch `feature/upgrade-mc-26.3`)

GameTests: none. Automated so far: `build` and a dedicated GameTest server start (`runSmokeTest`, removed again)
without errors; a set file with `minecraft:generic.armor` loaded without warnings.

- [ ] `runClient` starts; `/setweaver editor` opens; all screens (manager, wizard, editor, popups, packages, tag
      creator) show panels, text and buttons; buttons in the Set Editor are not hidden behind the panels.
- [ ] Mouse: list selection, inline icons, double-click in popups, scrolling; keyboard: arrow keys, Enter, Esc,
      typing into all edit boxes including the inline value / amplifier boxes.
- [ ] Inline edit overlay in the Set Editor covers the row text.
- [ ] Tooltips: SHIFT, ALT, SHIFT + scroll over an item in several sets (inventory does not scroll meanwhile).
- [ ] Set bonuses apply (effects + attributes, max health stays when healed above 20).
- [ ] Tag creator: saved tag loads after `/reload` (no pack format warning in the log).
- [ ] Dedicated server (`runServer` + client): editor actions work, no client class errors in the server log.
- [ ] Old 1.21.1 world (1.0.0) with `config/setweaver/` upgraded to 26.3: sets load (old `generic.` ids), players keep
      role and level, packages still collapse.

## Cleanup and standards for 1.0.0 (branch `refactor/cleanup-1.0.0`)

GameTests: none. Automated so far: `build`.

- [ ] `runClient` and `runServer` start without errors; first start creates `config/setweaver/set_armor/` with
      `HOW_TO_SET_EFFECTS.txt`, the example and `attribute_packages.json`.
- [ ] Set with `+4 minecraft:max_health` at 2 pieces: wear 2 pieces, heal to 24 HP, wait 10 s — health stays
      at 24 (was cut to 20 before).
- [ ] Effects (e.g. speed) apply and run out about 10 s after taking the armor off; attribute bonuses disappear.
- [ ] Two sets for different tags on the same armor both apply.
- [ ] `/setweaver set @s mage 3`, `/setweaver set @s warrior` (level stays 3), `/setweaver setyear @s 2`,
      `/setweaver check @s` — messages translated, tooltip follows the new role/level.
- [ ] Player without role and level: tooltip shows an "All Roles / All Levels" set.
- [ ] Tooltip: SHIFT shows the bonus, ALT expands a package, SHIFT + scroll pages through several sets.
- [ ] Sets Manager: create a set via wizard + editor (also for a tag with `/` in its path), edit, copy into another
      scope, delete (confirmation) — files change in `config/setweaver/set_armor/` and the list refreshes.
- [ ] Inline editing in the Set Editor: amplifier, value, operation; **Copy from…**; Save & Exit.
- [ ] **Validate** with a broken file (unknown effect, `9Part`, bad JSON) shows warning / error lines.
- [ ] **Reload** after editing a file by hand picks up the change for all players.
- [ ] Package editor: add, toggle, delete, save — `attribute_packages.json` changes on the server.
- [ ] Tag creator: create `setweaver:test_tag`, `/reload`, tag appears in the tag browser with **[Edit]**.
- [ ] Dedicated server + client: all editor actions write on the server, not into the client's `config/`; a
      non-operator gets "Permission denied" and the server log shows no client class errors.
- [ ] Old 0.4.0 world and config: sets load, role/level of players are kept.
