# Contributing to SetWeaver

These rules apply to everyone working on the repository — people, local AI sessions and **cloud / remote AI
sessions** alike. Cloud sessions start from a fresh clone and only see committed files, so this file is the
binding reference for them.

## Target

- Minecraft **26.3**, NeoForge 26.3.x (`gradle.properties` → `neo_version`), Java **25** (auto-provisioned toolchain)
- Mod id `setweaver`, base package `com.gilfort.setweaver`, Mojang official names (`Identifier`, not
  `ResourceLocation`); ids created with `SetWeaver.id(path)`
- Minecraft 1.21.1 (mod versions `1.x`) is maintained on the branch `1.21.1`.
- No dependencies on other mods.
- Each registry gets its own class with a `register(IEventBus)` method (or a public `DeferredRegister`), wired in the
  `SetWeaver` constructor.

## Code conventions

1. **English only:** all identifiers (classes, methods, variables, registry names, config keys) and all comments.
2. **Javadoc** on every public / protected class, method and field (overrides inherit it). Short `//` line comments
   inside method bodies (explaining a non-obvious step) are fine. No commented-out code, no template leftovers.
3. **Every player-facing text goes into the lang file** `src/main/resources/assets/setweaver/lang/en_us.json`
   — never hardcode display strings, use `Component.translatable(...)` (screens may use `I18n.get` via
   `GuiUtil.tr`). Key scheme: `item.setweaver.<name>`, `itemGroup.setweaver.<name>`, `gui.setweaver.<name>`,
   `message.setweaver.<name>`, `tooltip.setweaver.<name>`, `setweaver.configuration.<key>`. Log messages are not
   player-facing.
4. Prefer vanilla / own-code solutions over mixins; write a mixin only when nothing else works.
5. **Content:** set definitions live in `config/setweaver/set_armor/` and attribute packages in
   `config/setweaver/attribute_packages.json` — a deliberate exception to "content via datapack", because the
   in-game editor writes these server files. Custom item tags are datapack content (`setweaver_custom` world
   datapack). A TOML config, if ever added, holds global options only.
6. **Never remove a persistent registry entry** (data component, attachment, item) that may be stored in
   existing worlds — keep it registered as legacy (`@Deprecated`, documented) or migrate the data first.
   `component/ComponentRegistry` is such a legacy class.
7. `@EventBusSubscriber` without `bus`: FML routes mod bus events automatically.
8. **Client / server separation:** client-only classes (screens, tooltip handler) live in `client/` and are reached
   from common code only through `client/ClientHooks`; client-bound packet handlers are registered in
   `client/SetWeaverClient` (`RegisterClientPayloadHandlersEvent`), so a dedicated server never loads them.
   Server and client keep separate registries (`ArmorSetDataRegistry.server()` / `.client()`) — never share mutable
   static state between the sides.
9. **Packets from clients are untrusted:** check operator level 2, validate every path (`SetFiles`), id and size,
   and never throw from a handler.

## Branches and versions

- `main` always targets the **newest supported Minecraft version**.
- Every older Minecraft version keeps a long-lived branch named after it (e.g. `1.21.1` → mod versions `1.x`). It
  only receives bug fixes: branch `fix/<mc-version>-<short-name>` from that branch, PR back into it. A fix that
  applies to several versions is ported with `git cherry-pick` in its own PR per branch.
- Version branches are never deleted or force-pushed.

## Changelog

- `docs/wiki/Changelog.md` is mandatory. **Every change of `mod_version` adds a section for the new version**
  (`## <version> — Minecraft <mc version>`, newest first) listing everything that changed since the previous
  version, grouped into `Added` / `Changed` / `Fixed` / `Removed`.
- Write it for players and modpack makers (what changed, what they must do — e.g. renamed files), not as a commit
  list.
- On a version branch, the changelog entry goes into that branch's copy; mirror it to `main` so the wiki shows all
  versions.
- Versions follow SemVer; a big or incompatible change raises the major version.

## Workflow

- Work is sliced into GitHub issues labeled `user story` (`US-XX`). **One story at a time.**
- **One branch and one PR per story:** `feature/us-XX-<short-name>`, created from up-to-date `main`.
  - PR body contains `Implements #N`, the story's final commit contains `Closes #N`.
  - Fix-up commits reference the story, e.g. `fix: … (US-XX)`.
  - Merge **only after the maintainer's OK** and with a green build.
- Never bundle several stories in one branch or PR.
- No `wip:` commits on branches that get merged — squash them or commit only working states.
- Commit messages follow Conventional Commits (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `ci:` …). Breaking
  changes get `!` after the type and a `BREAKING CHANGE:` footer.
- No GitHub release without an explicit request from the maintainer.

### Cloud / remote sessions

Cloud sessions (claude.ai/code, auto-created `ccr-*` branches) follow the **same workflow, no exceptions**:

- If the session starts on a `ccr-*` branch, create `feature/us-XX-…` from `main` for each story instead of
  committing everything to the session branch.
- Finish one story completely (branch → PR → OK → merge) before starting the next from the updated `main`.
- **Do not discover APIs by pushing temporary CI debug steps.** Look up signatures locally: `javap` on the jars
  under `build/moddev/artifacts` / the Gradle cache, or extract the sources jar. Check the versions this project
  actually resolves (`./gradlew dependencies`) — other projects in the Gradle cache use different versions.

## Documentation

- **The wiki is mandatory:** `docs/wiki/*.md` (English) is the single source of truth, mirrored to the GitHub wiki
  by `.github/workflows/wiki.yml` on push to `main`. Every story PR updates the affected wiki pages (and
  `_Sidebar.md` for new pages). Never edit the GitHub wiki directly. Docs write commands as `/setweaver …`.
- The README stays short (features, install, development, links) and never duplicates wiki content.
- Every story adds its manual in-game checks to `docs/TESTING.md`:
  `## US-XX (#N): <title>`, the covering GameTests first, then a checkbox list.

## Tests

- GameTests belong in a `src/gametest` source set (never part of the jar) and run with
  `./gradlew runGameTestServer` (see Architect's Trials for the setup). The source set does not exist yet — the
  first story that adds testable server logic sets it up. Until then, manual checks in `docs/TESTING.md` cover
  each change.
- A story PR needs a green build.

## 26.3 API notes

- Screens: `extractBackground` / `extractRenderState(GuiGraphicsExtractor, …)`; text via `graphics.text(…)` with ARGB
  colors (alpha!); `graphics.nextStratum()` puts later elements above earlier text.
- Input: `mouseClicked(MouseButtonEvent, boolean doubleClick)`, `keyPressed(KeyEvent)` (`event.isEscape()`,
  `isConfirmation()`, `isUp()`), `charTyped(CharacterEvent)`; modifier state via `Minecraft.getInstance().hasShiftDown()`.
- Screens are opened with `minecraft.gui.setScreen(…)`, the current one is `minecraft.gui.screen()`; packets to the
  server go through `ClientPacketDistributor.sendToServer`.
- Permissions: `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` / `player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`.
- Reload listeners: `AddServerReloadListenersEvent.addListener(id, listener)`.
- Attribute ids have no `generic.` prefix any more; `SetIds.attribute` resolves old ids.
- A dedicated server does not load mod lang files: log translatable messages with `LogText.of`.
- Dev runs use `runs/<run name>`; the old `run/` folder belongs to 1.21.1 and is left alone.
- Dedicated server smoke test without accepting the EULA: add `smokeTest { type = "gameTestServer" }` to the runs
  temporarily, run `./gradlew runSmokeTest`, check `runs/smokeTest/logs/latest.log` for `ERROR`, remove the run.

## Commands

```bash
./gradlew build      # jar -> build/libs/
./gradlew runClient
./gradlew runServer
```
