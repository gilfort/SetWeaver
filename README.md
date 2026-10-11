# SetWeaver

[![Build](https://github.com/gilfort/SetWeaver/actions/workflows/build.yml/badge.svg)](https://github.com/gilfort/SetWeaver/actions/workflows/build.yml)

A NeoForge mod for Minecraft 1.21.1 that adds armor set bonuses: mob effects and attribute modifiers for wearing
several pieces of a set. A set is any item tag, so armor from every mod works.

**📖 Documentation: [Wiki](https://github.com/gilfort/SetWeaver/wiki)**

## Features

- **Tag-based sets** with thresholds for 1–4 worn pieces.
- **Roles & levels** — limit bonuses to player groups (`/setweaver set <player> <role> <level>`).
- **In-game editor** (`/setweaver editor`) for sets, attribute packages and custom item tags; saving happens on the
  server.
- **Tooltips** with the set bonus (SHIFT), collapsible attribute packages (ALT) and paging for items in several sets.

## Installation

Requires NeoForge for Minecraft 1.21.1. Put the mod jar into the `mods` folder of server and clients.

## For developers

```bash
./gradlew build      # mod jar -> build/libs/
./gradlew runClient
```

The wiki is generated from [`docs/wiki/`](docs/wiki) by a GitHub Action on every push to `main` — edit the
Markdown files there, never the wiki directly.

Conventions, branch / PR workflow and test rules: [CONTRIBUTING.md](CONTRIBUTING.md).
