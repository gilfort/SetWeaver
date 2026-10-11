# Set Editor

All editor actions run on the server: it checks operator level 2, writes the files, reloads and sends the result to
every player. On a dedicated server nothing is written on the client.

## Sets Manager

Open with `/setweaver editor`.

| Panel | Content |
|---|---|
| Left | All loaded sets, grouped by tag. Sets without any effect or attribute are listed under "Not Active". Colored dots: yellow = everyone, blue = all roles of one level, green = one role and level. |
| Right | Details of the selected set, or validation results. |

Bottom buttons:

| Button | Action |
|---|---|
| **Reload** | Server reloads all set files and attribute packages and syncs them |
| **Validate** | Server checks all set files, results appear on the right (see [Validation](Validation)) |
| **Tags** | Tag browser: every item tag that contains armor, with its items |
| **Packages** | [Attribute Packages](Attribute-Packages) editor |
| **Create** | Set Wizard for a new set |
| **Close** | Closes the screen |

Icons next to the selected set: **✎** edit, **❏** copy into another scope (opens the wizard), **✖** delete (asks
for confirmation). Arrow keys move the selection.

## Set Wizard (step 1)

1. **Tag** – which items belong to the set (only tags that contain armor are listed).
2. **Display Name** – optional name for tooltips.
3. **Role** – "All Roles" or a role; type a new name to create one.
4. **Level** – "All Levels" or 1–99. A specific role needs a specific level.

If a set with exactly this scope exists, it is opened for editing. When copying, the source's thresholds are taken
over unless the target scope already exists.

## Set Editor (step 2)

- Tabs **1 Piece … 4 Pieces** are the thresholds; the number on a tab counts its entries. Empty thresholds are not
  saved.
- **+ Effect**: pick an effect, then the amplifier (0 = level I).
- **+ Attribute**: pick an attribute, value and operation; a live preview shows the result.
- **- Remove** removes the selected entry.
- Click an effect's level, an attribute's value or its operation to change it in place (Enter confirms, Esc
  cancels; clicking the operation cycles through the three operations).
- **Copy from…** replaces the current threshold with a copy of another one.
- **Save** sends the set to the server; **Save & Exit** also returns to the manager; **Back** returns without
  saving.
