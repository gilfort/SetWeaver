# Roles & Levels

Roles and levels limit set bonuses to groups of players, e.g. classes and progression tiers.

- **Role:** a name such as `mage` or `warrior`.
- **Level:** a number ≥ 1, e.g. a school year.

Each player has at most one role and one level, set with [Commands](Commands). Players without a role or level only
get sets for "All Roles / All Levels".

## Scopes and folders

| Role | Level | Applies to | Folder in `config/setweaver/set_armor/` |
|---|---|---|---|
| All | All | everyone | `all_roles_all_levels/` |
| All | `3` | every player with level 3 | `all_roles/3/` |
| `mage` | `3` | mages with level 3 | `mage/3/` |

A specific role always needs a specific level; "role for every level" is not supported.

## Priority

For each tag the most specific scope that matches the player wins:

1. role + level (`mage/3/`)
2. all roles + level (`all_roles/3/`)
3. all roles + all levels (`all_roles_all_levels/`)

Scopes are **not merged**: the winning file replaces the others for that tag. Sets for *different* tags all apply
and stack.

## Example

```
/setweaver set Alice warrior 1
/setweaver set Bob mage 3
/setweaver set Charlie healer 5
```

With `all_roles_all_levels/c__armors.json`, `warrior/1/c__armors.json` and `mage/3/c__armors.json`:
Alice gets the warrior set, Bob the mage set, Charlie the general one.
