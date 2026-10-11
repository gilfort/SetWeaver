# Validation

The editor entry **Validation** (left column) shows the server's check of every `.json` file in
`config/setweaver/set_armor/`, one result per file; clicking a file opens its set. The set and scope rows show the
number of problems (`!n`), and the form of a set lists them at the end. The same checks run on every load;
problems are also written to the server log.

| Result | Meaning |
|---|---|
| ✔ green | loaded without changes |
| ⚠ yellow | loaded, but invalid entries were skipped or corrected |
| ✘ red | not loaded |

## Checks

Errors (file not loaded):
- Folder is not `{role}/{level}/`, `all_roles/{level}/` or `all_roles_all_levels/`; level folder is not a number.
- File name is not `namespace__path.json` or not a valid tag id.
- No valid JSON object, missing or empty `parts`.

Warnings (entry skipped or corrected):
- Threshold key not `1Part` … `4Part`.
- Unknown effect or attribute id (e.g. from a mod that is not installed).
- Unknown `modifier` (allowed: `addition`, `multiply_base`, `multiply_total`).
- Amplifier outside 0–9 (clamped).
- No threshold grants anything.
