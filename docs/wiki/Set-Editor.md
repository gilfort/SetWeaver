# Set Editor

`/setweaver editor` (operator level 2) opens the SetWeaver browser. Everything is edited in one screen; saving,
deleting and validating happen on the server, which writes the files, reloads and syncs all players. On a dedicated
server nothing is written on the client.

## Layout

| Area | Content |
|---|---|
| Left | **Sets** – one row per item tag (search field on top, number of scopes, `!n` = problems), **+ New set…**, then **Attribute packages**, **Item tags** and **Validation**. |
| Middle | The scopes of the selected set (yellow = everyone, cyan = all roles of one level, green = one role and level), the packages or the item tags. |
| Right | The details: an editable form with **Save**, **Discard** and further actions below it. |

Selecting an entry collapses the columns left of it to narrow strips; click a strip (←) to open it again. On small
screens only one list column is open at a time so the form keeps its width. The selection is remembered until the
editor is opened next time. **Reload** (top right) reloads all files on the server.

## Editing a set

- **Display name** – optional; without it the tag path is shown.
- One section per threshold (**1 Piece … 4 Pieces**). Empty thresholds are not saved.
  - **+ Effect** – pick an effect (with icon); **− / +** change its level (I–X); **✕** removes it.
  - **+ Attribute** – pick an attribute; type the value or use **− / +**, click the operation to cycle through
    `addition`, `multiply_base`, `multiply_total`. The grey line below shows the result.
  - **Copy…** – replace the threshold with a copy of another one.
- Changed fields show an orange dot; invalid values are red and block saving.
- Problems found by the server validation are listed at the end of the form.
- **Save** writes the set, **Discard** drops the changes, **Copy to…** copies the set into another tag or scope (an
  existing target asks before it is replaced), **Delete** removes the file after a confirmation.
- Leaving an entry with unsaved changes asks first.

## New sets and scopes

**+ New set…** (left) or **+ Add scope…** (middle) open a dialog: item tag (only tags with armor are listed), role
("All roles", an existing role, or type a new name), level (or "All levels") and an optional display name. If the
scope already exists it is opened, otherwise a new unsaved set is created. A specific role needs a specific level.

## Attribute packages, item tags, validation

- **Attribute packages** – name, priority (lower wins), enabled flag and attributes; all packages are saved
  together. See [Attribute Packages](Attribute-Packages).
- **Item tags** – browse every tag that contains armor; tags in the `setweaver` namespace (and new ones) can be
  edited. See [Custom Tags](Custom-Tags).
- **Validation** – the server's check of every set file; click a file to open its set. See [Validation](Validation).
