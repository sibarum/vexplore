# TODO

What is known about and not done, ordered by what the design says matters most. Delete an entry when it is done
rather than ticking it. Anything that is a *framework* difficulty is written down in
[`vexelray-framework/docs/TODO.md`](../../vexelray-framework/docs/TODO.md) (and summarised in
[framework-notes.md](framework-notes.md)); this file is Vexplore's own work.

[docs/design/README.md](design/README.md) holds the principles and they are the requirements; the mockups are one
drawing of them. Where this list reprioritises the design, it says so.

## Left over from milestone 2

- [ ] **Watch a person use the folder dialog.** *Choose folder…* posts `FileDialog.pickFolder` to the GUI thread
      through `Chooser` and waits on the answer. It compiles, the processor accepted the binding, and nothing has
      ever opened it: a native dialog cannot be clicked from `ottermate`. Until somebody has, treat it as unverified.
      Also unknown, and now worth checking by hand: whether the NFD library loader is happy in the native `.exe`
      (2026-10-05: the exe builds and runs, and this dialog was never opened in it).

## The suite: being started by, and starting, the other apps

Vexplore, mainframe and the text editor install separately and are meant to start each other, each as a **new
process and a new window**. The framework side (finding a sibling, the helper) is in
[`vexelray-framework/docs/TODO.md`](../../vexelray-framework/docs/TODO.md); this is what Vexplore itself owes.

- [ ] **Only a launch with no arguments may restore or write saved state.** The same rule as the text editor's. A
      Vexplore started with a folder or file (`vexplore <path>`, which is how the editor's *Open in Vexplore* starts
      it) should start clean and save nothing, or two windows overwrite each other's window placement and settings.
      Vexplore keeps no session of its own yet, so what is left is upstream: window placement is the framework's
      `WindowMemory` and shares `Settings` with everything else (*Settings and the session* in the framework TODO).
      Marks, once they survive a restart (below), are session state and follow the same rule.
- [ ] **A row menu.** Opening is Enter, double-click, or the dock's *Open in Vex*; `ListView` has no
      context menu (the editor's tree and tabs do), so there is no *Open with* and no *Copy path* on a row. A
      framework change first.
- [ ] **Two running windows share one log directory.** The native exe writes `vexplore.log` and
      `vexplore-probe.csv` under `~/.vexplore/logs`. Unchecked whether two processes at once overwrite or
      interleave them; spawned windows make that normal, not rare.
- [ ] **The native `.exe` has only been exercised on the paths its metadata was traced with.** Over the automation
      socket it was shown to render, take a screenshot and respond to clicks (rows, suggestions, the hex preview).
      Not exercised in the exe: the 02-move and 03-delete-and-mark scenes, the folder dialog, the screenshot-save
      dialog and `--capture`. A missing FFM descriptor or reflection entry shows only when its path runs (the first
      build died at startup on one). Run the scenes against the exe, and re-trace after a change that adds a new
      kind of call. The metadata is in the app for now and belongs upstream (framework TODO, *native-image
      metadata*).
- [ ] **Rename.** The chip exists and is disabled. By pattern (prefix, counter, change extension), with the plan
      listing every `old -> new` before anything runs; the plan type already carries steps, so it is a new
      `Plan.Kind` and a pattern editor, not a new idea.
- [ ] **Marks survive a restart.** They are in memory. `Settings` is the place, beside window memory. A mark holds
      paths, so it should also notice that a path has gone.
- [ ] **Undo outlives the session.** `Operations.Done` is in the document and gone on exit; the trash is not. A
      journal file in `~/.vexplore/` would let the last day's operations be undone after a restart, and is also what
      "recoverable by default" is honestly promising.
- [ ] **A destination history.** Suggestions come from neighbouring folders that hold the kind. The design also
      wants "the last folder similar files went to"; the journal above is where that is remembered.
- [ ] **Executing needs a second look at cross-volume moves.** `moveTree` falls back to copy-then-delete when an
      atomic move fails, which is right and has only been run on one volume. It also copies attributes but not
      alternate streams or ACLs.
- [ ] **Operations are not cancellable and report progress only as a count.** `Lanes.offload()` has no cancellation
      token (framework TODO). A multi-gigabyte copy wants bytes done and a cancel beside it: `Progress` is a missing
      widget (see [components.md](components.md)).
- [ ] **The trash is never emptied.** Needs an age and a size limit, and a way to look inside it.
- [ ] **Include subfolders** as a *Select* chip. It needs a recursive scan, which is the first thing that genuinely
      wants cancellation.
- [ ] **Modifier suggestions only know the list.** Shift reaches along the displayed order and Control along the
      current folder. The design's "everything from this date cluster" across subfolders waits on the scan above.
      Candidate sets are computed when the key goes down, which is fast enough at hundreds of rows and has not been
      measured at fifty thousand; the design wants them *ready* before the key.
- [ ] **A "finisher" for a single file with a modifier held.** The design says to offer the common moves for that
      file. Today the Action chips are simply always there; the version that matters puts the likeliest destination
      beside the file the moment a key goes down.
- [ ] **A held modifier is not testable from outside.** `ottermate key` is press-and-release. A `keydown` / `keyup`
      pair is in the framework TODO; until then the live behaviour is checked by unit tests (`IntentsTest`) and by
      captures with `-Dvexplore.hold`.

## The design's own promises, still open

- [ ] **A file that landed five seconds ago is highlighted** (screen 02). Needs a `WatchService` on the shown folder,
      a "fresh" mark in amber (`Look.ANCHOR_DIM` exists), and the *new* tag. Mark, don't select.
- [ ] **Drag and drop as the fallback** (screen 02). A dragged list row, a *Carrying* card at the top of the rail,
      likely destinations listed with reasons (`Destinator` already computes them), and the same numbers marked on
      the tree's folders. The rail already never moves anything, so this is content, not layout.
- [ ] **Inline previews in rows** (screen 03): thumbnail, sparkline, waveform, first line, page count. Cheap
      ones first: *first line* and *page count* need no decoding.
- [ ] **Preview tiers 1 and 2.** Tier 4 (bytes) and tier 3 (text) are built and are the floor, and tier 1 has its
      images (`imagelib-wrapper`: raster, animated, SVG). Next: CSV (a `Table`), JSON (a `TreeView`), archive listing
      (a `TreeView`), then PDF. Executable signature info reads the PE header as bytes and runs nothing.
- [ ] **Images in the native executable are unverified.** The pom links imagelib's static library into the image
      (`imagelib.static.dir`, staged by imagelib-wrapper's `native/build.sh`), and the JVM run, the tests and a
      driven session all decode. Nobody has built `-Pnative` since, opened an image in it, and checked that the
      DLL was not extracted. `natives/.*` is still included as a resource, so the DLL rides along unused.
- [x] **A transparent image sits on the well colour, not a checkerboard.** The viewer has five backdrops (dark,
      light, checker, magenta, green). The dock stays on dark.
- [ ] **A large decode runs to the end after the selection has moved on.** The Previewer is now latest-wins, so
      arrowing through a folder decodes only where the worker comes free, not every file passed; but the one decode
      already running still finishes. Abandoning it needs imagelib to offer a cancel, which it does not.
- [x] **Pop out** the dock: for an image, the **viewer**, a window of its own (`Viewer`). Not `Popout`, which is a
      panel against a window's edge; the dock is in a `SplitPane`, and what was wanted was a different view, not the
      same panel moved.
- [ ] **The viewer forgets its backdrop and its size on close.** Both are per-run. A backdrop belongs in settings;
      the window's place could go through `WindowMemory` as the main window's does.
- [ ] **Pop out for the other tiers.** Text and bytes have the button greyed. A text pop-out is the editor (Vex),
      which the dock already offers, so this may never be wanted.
- [ ] **The viewer has no zoom or pan.** It fits the picture to the window and never enlarges a raster. 1:1 and a
      drag to pan are the next thing an image viewer is asked for.
- [ ] **Two Folders mode** (screen 05). Two tables, a status per row, suggestions from the comparison. **Open
      question from the design:** how "identical" is decided. Size and date are cheap; hashing is exact and slow for
      a multi-gigabyte video. Proposal: size + date first, hash only on demand and only when they disagree in a way
      that matters, with the row saying which it used.
- [ ] **The tree follows the list.** Clicking a breadcrumb or opening a folder in the list changes the folder, and
      the tree does not reveal it. `TreeView.revealPath(chain, then)` exists; it needs the tree's items to be the
      same values as the chain's.
- [ ] **Back / forward / up.** Alt+Left, Alt+Right, Alt+Up, Backspace. History belongs in the model.

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
- [ ] **Folders are not pinned to the top once a column is chosen.** Explorer pins them.
- [ ] **A hidden or system file** is shown like any other. A setting, and a mark, not a filter.
- [ ] **The rail rebuilds its nodes on every change.** Fine at this size and it drops focus from a chip you just
      pressed; a keyboard user pressing Space on a chip loses their place. Diff the chips instead of rebuilding.
- [ ] **`Startup` reads system properties** so a capture can be put in a known state. It is a stand-in for a real
      session-restore seam; the framework's generated wiring now lets a holder read back what it built, so this can
      probably be replaced by that.

## Tests that are missing

- [ ] **`Ui`, `RailView` and `Actor` have no test.** Everything under `suggest/`, `files/`, `ops/` and the model is
      tested without a window; the rest is verified by photographing it and by the three `ottermate` scenes.
      `vexelray-gui-harness` can host a tree headlessly and is the place for "selecting a file fills the rail" as an
      assertion instead of a screenshot.
- [ ] **A scene that checks instead of photographs.** The scenes `await` the states they expect and fail on `err`;
      they should also assert on what is on disk afterwards, which is what actually matters about a move.
