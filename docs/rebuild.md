# The rebuild

How Vexplore comes back on VexelRay 0.2, proposed before any of it is built. The design
([design/README.md](design/README.md), [HANDOFF.md](design/HANDOFF.md)) is still the requirement and is not reopened
here. 0.1's README and code (`git show 47d0016:README.md`) say what 0.1 did, and its port inventory
(`git show 9ea1c65:docs/framework-notes.md`, *The v0.2 port inventory*) says which APIs went. This file says what the
0.2 version is made of, in what order it comes back, and what it waits on.

The author's rulings are marked **(ruled)**, with the date.

## The rule for the port

0.1 is not ported. The project is the template, and each feature comes back as a slice: a change from the model to
the screen, with its test and its `ottermate` check, one at a time, as Rasterware's did. Code from history is copied only
where it has no GUI and no threading in it: `files/` (`Entry`, `Kind`, `Names`, `Destinations`), `suggest/`
(`Suggestions`, `Intents`, `Order`, `Dates`, `Bytes`) and `ops/Plan` are pure functions over values, tested without a
window, and can come back almost as they were. `Model`, `Browser`, `Previewer`, `Destinator`, `Actor`, `Ui`,
`RailView`, `Dock` and `Lens` are rewritten: each existed to move work off the frame loop and back by hand, which is
now the framework's.

## What it is made of

**`-core`, the explorer.** The template's `Editor` becomes `Explorer`: one immutable `State`, changed only by
declared steps, the shape the template already has. 0.1's `Doc` is most of it:

| Field | What it holds |
| --- | --- |
| `folder`, `rows`, `listing` | the folder shown, its rows, and the listing in flight (a stale reply is dropped by comparing it) |
| `selected` | the paths selected in the list, as `Table.SELECTED` last said |
| `pick` | the rail's Select / Condition / Action so far, and the destination |
| `plan` | what the chosen action would do, as the card states it, or nothing |
| `running`, `done` | the operation in flight, and the journal of what can be undone |
| `marks` | named sets of paths kept for later |
| `preview` | what the dock shows for the single selection |
| `git`, `fresh` | git's word on the folder, and which rows landed in the last few seconds |

What the rail suggests is computed in a step from the state (`Suggestions` is pure and fast at hundreds of rows), not
in a job. Measure it at fifty thousand before deciding otherwise.

**The disk, as jobs and sources.**

| Part | Kind | Does |
| --- | --- | --- |
| `ListFolder` | `@Job` | the template's, widened: a folder's rows with size, times and kind; also answers `TreeView.CHILDREN_WANTED` |
| `PlanAction` | `@Job` | reads the destination to say what an action would do, collide with, or leave alone |
| `RunAction` | `@Source` | carries out a plan, sends progress as it goes, stops on a stop message; its reply is the journal entry |
| `Undo` | `@Job` | reverses a journal entry |
| `ReadPreview` | `@Job` | the dock's tiers: bytes, text, and a picture's thumbnail as a `Feed`, as Rasterware's `ReadThumbnail` does |
| `WatchFolder` | `@Source` | one per shown folder, a `WatchService` telling the explorer what arrived |
| `GitStatus` | `@Job` | `vex-suite-common-git`'s `Git.status`, crossed as `Status.text()` |
| `Launch` | `@Job` | finds Rasterware or the IDE through `Apps.find` and starts it, or the shell's own open |
| `ReadSession` / `WriteSession` | `@Job` | the template's: the window, the last folder, the marks |

A copy is long and wants a cancel, which a `@Job` cannot give: that is why `RunAction` is a `@Source`.

**`-gui`, the view.** `Ui` lays out the handoff's regions: top bar (`Breadcrumb`), tree (`TreeView`), file list
(`Table`), rail, dock (under the list, in a `SplitPane`), status bar (`StatusBar`). The rail and the dock are
Vexplore's own, as in 0.1. The view hears widget topics and tells the explorer; it decides nothing. The template's
tabs, `Buffer`, `Markdown` and text-file jobs go: text is the IDE's, as it was in 0.1.

## The order

Each milestone ends with its retrospective section, as `CLAUDE.md` asks. It follows 0.1's plan, with 0.1's first two
milestones split so the window is useful early. **(ruled 2026-11-01)** The order stands.

**M0. The template becomes an explorer.** *Done 2026-11-01.* Delete the editor (tabs, buffers, Markdown, the text
jobs); the layout's regions in place and empty; `Look` on the design system's tokens, which replaced the handoff's
colours (violet is the person's choice, amber is Vexplore suggesting; [shape.md](shape.md) says why);
`Type` on the atlas's faces, since the design's Instrument Sans and IBM Plex Mono cannot be added
(framework-notes FN-1). **(ruled 2026-11-01)** Two steps, not one. First the deletion: the editor's parts go and nothing
new comes in, leaving the navigator over an empty window, building and green. Then the new shape (`Explorer`'s
`State`, the M1 messages, `Recipes`) proposed as a sketch of the records and steps, and built once agreed. Each
reviews on its own, and a wrong shape is caught before the code is written against it.

**M1. Browsing, read only.** *Done 2026-11-01.* The tree of the user's folders and drives (`TreeView`, lazy), the
breadcrumb, the list with its columns, sort and range selection, the status line. Enter opens: a folder navigates, a
file goes to `Launch`. A path on the command line opens that folder, or the folder with that file selected.

**M2. The rail states its effect.** *Done 2026-11-02.* Select, Condition, Action as chips; the rows a suggestion
would reach shown with `ListView.marks` (marked, never selected); the card at the bottom that says what would happen
first. No action runs yet. **(ruled 2026-11-02)** The rail is a third of the room beside the tree, not 25 rem, so a
narrow window narrows it with the list (framework-notes FN-2). Held Shift and Control wait for a modifiers topic
(FN-9).

**M3. Actions.** *Done 2026-11-02, in four slices ([shape.md](shape.md)).* Move, Copy, Archive, Delete into
Vexplore's trash, Rename one, Mark. Planned by `PlanAction`, run by `RunAction` with a count and a cancel in the
rail, undone by Ctrl+Z through a `History` whose changes send messages (the ruled pattern; `ValueWidgetUndoTest` is
the example). Suggested destinations, and `PickFile.folder` behind *Choose folder…*. Marks kept across runs through
the session jobs, which 0.1 never did.

**M4. The Preview Dock and the row menu.** *Done 2026-11-02, in four slices ([shape.md](shape.md)).* Tier 4 (hex,
strings, entropy) first, since it is the floor, then text, then pictures. The row menu through `ListView.action` /
`CHOSEN`. **(ruled 2026-11-01)** A picture or a video shows only as its thumbnail in the dock, and opening its preview
hands it to Rasterware. Vexplore has no viewer of its own, now or once the suite's is ported.

**M5. Fresh files.** `WatchFolder`; a row that landed in the last few seconds tagged *new* in amber, never selected.

**M6. Two Folders and the drag.** Two lists sharing a `PayloadType`, a drop between them through `ListView.LANDED`,
copied or moved by drive and key, undoable. Then the design's part 0.1 never built: a status per row and suggestions
from the comparison, with the rail beside both lists rather than swapped out.

**(ruled 2026-11-01)** What "identical" means, in two tiers. The listing decides first, for free: same size and same modified time
is *identical*, a different size is *different*, and the newer side is named. Same size with a different time is
*probably identical* and shown as such, not guessed. Content is read only where it changes what an action does: the
rows a suggestion would skip as identical, or a conflict about to be resolved, are hashed by a `@Source` with a count
and a cancel before the card offers the action. So a folder of videos never pays for hashing it was not about to act
on, and nothing is skipped on a guess.

**M7. Git.** The GIT column and the branch in the status line, asked after the listing so it never holds the rows up.

**M8. The redesign.** Three directions, drawn 2026-11-02 against the rebuilt window (the scratch Downloads folder,
`a-notes.md` in the dock), on the canvas *Vexplore Redesign*
(<https://claude.ai/artifact/JzjQM7RGs16CLxrZpV4QQW>). None is chosen yet, and choosing one is the milestone's first
step. Then its sketch in [shape.md](shape.md), for whatever it adds to `Messages` or `State`, before any code.

- **A · Lumen**: floating panels with luminous edges. The layout stays (tree, list over dock, rail). Each family of
  file (folder, text, video, table, PDF, data, program) gets its own colour and tile, each row a size bar, and the
  text preview line numbers.
- **B · Vector**: an instrument HUD. An *Arrivals* time strip over the list, with a *now* marker; numbered rows with
  a size meter in ticks; the preview's facts as a grid (tier, encoding, lines, size, modified); the rail's steps
  tagged *set* or *pending*, and the card as target, delta and anchor.
- **C · Orbit**: a calm gallery. Cards grouped by day (*Today*, *Oct 30*), newest first, a text card showing its
  first lines; *Pop out* and *Open with* on the preview; the rail worded *Which files*, *Only if*, *Then*.

What the choice has to settle: whether a direction's per-family colours sit beside the design system's tokens or
replace them (M0 put `Look` on the tokens), and what it asks of the framework. Glows and floating panels, a time strip
and a wrapping grid of cards are each to be checked against the widget library before the sketch. A wrap is already
missing (*Chips that wrap*, below), and every gap found goes in framework-notes as usual.

**Last. Packaging.** The native builds, the ffmpeg-record zip, `installer.json`'s `appId`, the scenes in
`docs/scenes` rewritten against the new landmarks.

**After the rebuild: remote folders.** **(ruled 2026-11-01)** SFTP waits for the providers idea
([ideas/providers.md](ideas/providers.md)) rather than coming back as 0.1 had it. When it comes, one connection per
host is what `@Destination` was ruled for: a part that is both `@Source` (what the server answers) and `@Destination`
(what is asked of it), keyed by host.

## What it waits on

Vexplore is the named witness for several of these: each gets an FN entry the moment it is worked around, and is
taken to the framework rather than worked around for long.

| Need | Where 0.2 stands | Until then |
| --- | --- | --- |
| **Held Shift / Ctrl as intent** (the design's second principle) | `Seams.modifiers()` is the widget library's; the roadmap gives an application a state topic once a witness asks | Asked at M2 (framework-notes FN-9). Suggestions work from the selection alone until it lands |
| **A drop onto a tree row** | `TreeView` takes only its own items; no declared drop zone (roadmap: "Vexplore is that witness") | drops between lists only (M6) |
| **Chips that wrap** | no wrap in `FlexLayout`; `Node.wrap` planned and paused | two chips to a row, counted, as 0.1 did |
| **Progress** | no widget | a count in the rail's notice line, from `RunAction` |
| **Greying a row menu's action per row** | trees have it, lists do not | an action that would do nothing says so on the card |
| **Video, animated images, the suite's viewer** | the codec's players wait on the frame contract's step 5; `PictureViewer` and `Filmstrip` are out of vex-suite-common's build | nothing to wait for: the dock shows a thumbnail (a video's first frame) and Rasterware plays and views (ruled above) |
| **A marked tree row** (destination counts, screen 02) | the tree has ticks, not fills | the counts on the rail only |

## The author's rulings

Of 2026-11-01: the order stands; remote folders after providers; pictures as thumbnails, viewed in Rasterware;
"identical" in two tiers, content hashed only for the rows an action is about to act on (M6); M0 in two steps, the
deletion and then the new shape as a sketch before it is built (M0). 2026-11-02: the rail a third of the room beside
the tree (M2).
