# TODO

What is known about and not done, ordered by what the design says matters most. Delete an entry when it is done
rather than ticking it. Anything that is a *framework* difficulty is written down in
[`vexelray-framework/docs/TODO.md`](../../vexelray-framework/docs/TODO.md) (and summarised in
[framework-notes.md](framework-notes.md)); this file is Vexplore's own work.

[docs/design/README.md](design/README.md) holds the principles and they are the requirements; the mockups are one
drawing of them. Where this list reprioritises the design, it says so.

## Next: make it do something

- [ ] **Actions.** The rail states an effect and nothing more; every action button and *Keep as Mark* is disabled,
      and the card says so. Order: **Mark** first (in memory, no disk risk, and it is what makes the rest
      composable), then **Copy**, then **Move**, then **Delete (recoverable)** through the Recycle Bin. Each states
      what it will do first ("9 files, 6.4 GB, nothing has changed yet"), and needs a journal so "recoverable
      by default" is true of Move and Copy too. Runs on `Gui.offload()` with `Progress` (missing, see
      [components.md](components.md)) and lands back through the model.
- [ ] **Destinations.** *Move to…* needs somewhere to go. The design's answer is suggested destinations (the last
      folder similar files went to, the sibling folder that holds that type); the folder dialog
      (`vexelray-gui-nfd`) is the fallback. Suggestions need a small history, kept beside window memory.
- [ ] **The tree follows the list.** Clicking a breadcrumb or opening a folder in the list changes the folder, and
      the tree does not reveal it. `TreeView.revealPath(chain, then)` exists; it needs the tree's items to be the
      same values as the chain's.
- [ ] **Back / forward / up.** Alt+Left, Alt+Right, Alt+Up, Backspace. History belongs in the model.
- [ ] **Open a file.** Enter or double-click on a file does nothing yet; on a folder it navigates.

## The design's own promises

- [ ] **Modifier keys as intent signals** (`Gui.modifiers()` is a state; nothing reads it). Shift: propose where a
      range likely ends (end of this type run, this date cluster, this shared name prefix). Ctrl: propose the rule
      the picked examples suggest and offer *Keep as Mark*. This is the design's centre of gravity and the
      current rail only reacts to the selection, so **it is the next thing after actions**, not after polish.
- [ ] **Candidate sets computed ahead of time per folder**, so a suggestion is ready in the pause after a modifier
      goes down and *a suggestion that arrives late does not appear*. `Suggestions.of` is pure and fast enough for
      hundreds of rows; it has not been measured on fifty thousand.
- [ ] **A file that landed five seconds ago is highlighted** (screen 02). Needs a `WatchService` on the shown folder,
      a "fresh" mark in amber (`Look.AMBER_WASH` exists), and the *new* tag. Mark, don't select.
- [ ] **Drag and drop as the fallback** (screen 02). A dragged list row, a *Carrying* card at the top of the rail,
      likely destinations listed with reasons, and the same numbers marked on the tree's folders. The rail
      already never moves anything, so this is content, not layout.
- [ ] **Inline previews in rows** (screen 03): thumbnail, sparkline, waveform, first line, page count. Cheap
      ones first: *first line* and *page count* need no decoding.
- [ ] **Preview tiers 1 and 2.** Tier 4 (bytes) and tier 3 (text) are built and are the floor. Next: CSV (a `Table`),
      JSON (a `TreeView`), archive listing (a `TreeView`), then images (`imagelib-wrapper`), then PDF. Executable
      signature info reads the PE header as bytes and runs nothing.
- [ ] **Pop out** the dock (`Popout` exists).
- [ ] **Two Folders mode** (screen 05). Two tables, a status per row, suggestions from the comparison. **Open
      question from the design:** how "identical" is decided. Size and date are cheap; hashing is exact and slow for
      a multi-gigabyte video. Proposal: size + date first, hash only on demand and only when they disagree in a way
      that matters, with the row saying which it used.
- [ ] **Include subfolders** as a *Select* chip. It needs a recursive scan, which is the first thing that genuinely
      wants the offload lane's cancellation (`Lanes.offload()` has none).

## Details found while building

- [ ] **`Suggestions` offers "Videos over 1 GB · 2" by picking the largest round number below the selected file.**
      Good for one file; for several selected files it uses the *smallest* one. Both are guesses; worth a look once
      there is real use to look at.
- [ ] **Row separators.** The mockups draw a hairline between rows; `ListView` has none. A framework change
      (`ListView.divider(Role)`), not an app one.
- [ ] **Type faces.** The design specifies Instrument Sans and IBM Plex Mono; the framework ships its own faces.
      `Type.UI` and `Type.MONO` are indices, so this is a font-loading question, not a code one.
- [ ] **Zoom range and minimum size.** `Vexplore.MIN_W_EM/MIN_H_EM` are the template's 24 × 16 and have not been
      checked against this layout, whose rail alone is 25 rem.
- [ ] **Tree marks the current folder.** The tree shows no active row (`#252a30` in the design).
- [ ] **The listing is "folders first, then by name" and the table then sorts by modified.** Sorting is by column
      only; folders are not pinned to the top once a column is chosen. Explorer pins them.
- [ ] **A hidden or system file** is shown like any other. A setting, and a mark, not a filter.
- [ ] **`Startup` reads system properties** so a capture can be put in a known state. It is a stand-in for a real
      session-restore seam and for the wiring exposing its parts; when either exists, delete it.

## Tests that are missing

- [ ] **`Ui` has no test.** Everything under `suggest/`, `files/` and the model is tested without a window; the
      layout is verified by photographing it and by `ottermate` scripts. `vexelray-gui-harness` can host a tree
      headlessly and is the place for "selecting a file fills the rail" as an assertion instead of a screenshot.
- [ ] **A scripted scene ladder** in `docs/scenes/` (`ottermate --script`), one per mockup, that produces the
      screenshots to compare with `docs/design/screenshots/`. Write it as each screen lands.
