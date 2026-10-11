# Commands

All commands need **operator level 2**.

| Command | Description |
|---|---|
| `/setweaver editor` | Opens the editor (see [Set Editor](Set-Editor)). |
| `/setweaver set <player> <role>` | Sets the player's role, keeps the level. |
| `/setweaver set <player> <role> <level>` | Sets role and level (level ≥ 1). |
| `/setweaver setyear <players> <level>` | Sets only the level of one or more players. |
| `/setweaver check <player>` | Shows role and level of a player. |

`<player>` and `<players>` accept names and selectors (`@s`, `@a[…]`). Role names are stored in lower case and may
contain `a-z 0-9 _ . + -`. Role and level are saved with the player and survive death and relogging.

```
/setweaver set @s mage 3
/setweaver setyear @a[team=students] 2
/setweaver check Steve
```

`/reload` also reloads all set files and attribute packages.
