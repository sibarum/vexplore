# Components

Which GUI components the interface needs, which of them `vexelray-gui-widget` had, which were added for this
project, and which are still missing. This is the answer to "what do we have to build before we can build
Vexplore", kept as a table because the answer changes with every milestone.

Ordered by region of the screen, using the [design handoff](design/HANDOFF.md)'s names. **Status** is one of:

| | |
| --- | --- |
| **had** | already in `vexelray-gui-widget`, used as it was |
| **added** | did not exist; written for this project in `vexelray-gui-widget`, tested there, reusable |
| **extended** | existed, gained what Vexplore needed |
| **app** | belongs to Vexplore, not the framework — see the note at the end |
| **missing** | needed, not built |

## The screen

| Region | Component | Status | Notes |
| --- | --- | --- | --- |
| Title bar | `TitleBar` | had | Framework-owned chrome; comes with the template. |
| Top bar | `Breadcrumb<T>` | **added** | Ancestors are ghost buttons, reachable by keyboard. Collapses from the left by *count*, never by measuring, so it does not reflow while the window resizes. |
| Top bar, right | sort note | app | A text node driven by `Table.onSort`. |
| Tree | `TreeView<T>` | had | Lazy children, one tab stop, Ctrl+F. Used with a `Place` record so a drive is `C:` and not empty. Does **not** yet adopt `SelectionModel`, mark rows, or follow the current folder (see TODO). |
| Tree ⇄ list, list ⇄ dock | `SplitPane` | **added** | One pane has the size and the other takes the rest, so a resized window moves the far edge. The dock's height is the sized pane. Uses `GRAB` for a cursor until a resize cursor exists. |
| File list | `Table<T>` over `ListView<T>` | had, **extended** | Virtualised, sortable, resizable, range selection with Shift/Ctrl. Gained `headers(face, size, ink)`, `onSort(...)`, and, on the `ListView`, `marked(predicate)` / `looks(selected, marked)`. |
| File list, rows a suggestion would reach | `ListView.marked` | **extended** | A fill that is not a selection, so a preview of a suggestion never has to be undone. Selected outranks marked. |
| Suggestion Rail | a fixed column | app | The rail is *layout*, not a widget: it is a place. What goes in it is Vexplore's. |
| Rail: Select / Condition / Action chips | `Button` (toggle) | **added** | A chip is a button that holds a value. `pressed(v)` acts as the user would, `show(v)` does not, so a panel re-reading its model cannot loop. |
| Rail: summary card, primary action | `Button` (`PRIMARY`) | **added** | The one filled control on the screen. Amber comes from the palette's `action` anchor. |
| Rail: chips wrap to rows | wrapping row | **missing** | The layout engine has no wrap. The rail counts chips two to a row. See TODO. |
| Preview Dock | `Dock` | app | A header, a tier tag, a body. Text and hex today. |
| Dock: pop out | `Popout` | had | Not wired yet; the button is present and disabled. |
| Dock: hex dump | text lines | app | Sixteen bytes a row. A virtualised `ByteView` widget is worth extracting once a second application wants one. |
| Dock: entropy bar | two `grow` boxes | app | `grow(e)` and `grow(8-e)`, the same trick `Toggle` and `Slider` use. |
| Status bar | `StatusBar` | **added** | Declared slots on a left and a right side; a slot's place never changes, only what it says. |
| Modifier keys as intent | `Gui.modifiers()` | had | A `State<Set<Modifier>>`. Nothing subscribes yet. |
| Keyboard reachability | `ClaimScope` claims | had | Every chip and button takes Enter and Space. |

## Still needed for the rest of the design

| Screen | Component | Status | Notes |
| --- | --- | --- | --- |
| 02 Fresh file, drag start | "new" tag on a row, drag ghost replaced by a *Carrying* card | missing | `TreeView` and `Reorder` do drag and drop between tree nodes; dragging a *list row* to the tree does not exist. The card that stands in for the drag ghost is app code over `Gui.drag()`. |
| 02 | Marks on tree rows (destination counts) | missing | `TreeView.rowNode(item)` gives the row, so it is expressible, but a `TreeView.marked(...)` mirroring the list's would be the reusable form. |
| 03 | Inline row previews: thumbnail, sparkline, waveform, first line, page count | missing | Sparkline and waveform are `Sketch` → `Picture` marks (draw lane). Thumbnails need an image decode and `Node.image`, which wants a device-owned `SampledImage`. |
| 03/04 | Preview tier 1 (image, PDF, video, audio) | missing | Needs image decode (`imagelib-wrapper` exists) and a PDF and video story. |
| 04 | Preview tier 2 (CSV table, JSON tree, archive listing) | missing | CSV is a `Table`, JSON is a `TreeView`, an archive is a `TreeView`: composition, not new widgets. |
| 04 | Executable signature info | missing | Read the PE header as bytes. Never run anything. |
| 05 | Two-folder mode, per-row comparison status | missing | Two `Table`s side by side inside a `SplitPane`, a status column, and a comparison engine beside `Suggestions`. |
| All | Folder chooser for Move/Copy | missing | `vexelray-gui-nfd` has a native dialog. A destination the rail *suggests* is the design's point, so the dialog is the fallback. |
| All | Undo history for actions ("recoverable by default") | missing | `Gui.history` and `dropHistory` exist for edits; file operations need their own journal. |
| All | `Progress` | missing | Long copies need a value that moves and a cancel beside it (gui `todo.md` §4.6). |
| All | `CommandPalette` | missing | Every action keyboard-reachable; `FindBar` is the precedent (gui `todo.md` §4.3). |
| All | Disclosure, Toolbar with overflow | missing | Likely for Two Folders' pane headers. |

## Application-level, deliberately

The rail, the dock and the card are **not** widgets, and it is worth saying why so nobody extracts them early.
`vexelray-gui-widget`'s own rule is that a component earns a place only when it carries an invariant an
application cannot be trusted to re-derive: a selection anchor, a commit-or-revert, a virtualised window, a claim
on a chord. The rail is a *place* whose contents are Vexplore's suggestions; there is no invariant in it that
another application would share. What it *is made of* — chips, buttons, a marked list — is where the reuse is,
and that is what was extracted.

The one to watch is the **hex view**. If a second application wants one it becomes a `ByteView` with a virtualised
body, because sixteen bytes a row over a gigabyte file is a `ListView`, not sixty text nodes.

## Names that collide

The design's **Suggestion Rail** and `vexelray-gui-widget`'s **`Rail`** are unrelated. `Rail` is a row of tool
icons with a panel that may be put away; the Suggestion Rail is a fixed column that may be empty. Vexplore does
not use `Rail`. The code calls the column `rail` in landmarks and `railBody` in `Ui`, and the widget is never
imported there, so the word means one thing per file.
