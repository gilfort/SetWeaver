# Test plan

There are no GameTests yet. Manual tests cover each change: run `./gradlew runClient` (single player) or
`./gradlew runServer` plus a client, and tick off the checklist of the change.

---

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
