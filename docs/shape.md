# The shape, for M0's second step

What [rebuild.md](rebuild.md)'s M0 builds once the editor is gone: the controller's state, the messages M1 needs,
the parts the wiring builds, and the window's regions. A sketch to agree before code is written against it, as
`CLAUDE.md` asks of anything that touches `Messages`, `Recipes`, a lane or the state. Names are proposals.

M0's second step built the controller and the regions, empty; M1 adds the messages below and fills them. So this is
sized for M1 and stops there. M2 and later add fields and records; nothing here should have to change shape for them.

## The controller: `Explorer`

`Editor` is renamed `Explorer`. Same pattern: one `State` on a `Ledger`, static steps, a `@Component` on the logic
lane that takes an `Inbox`.

```java
record State(
    Optional<Path> folder,       // the folder shown
    long asked,                  // the listing in flight: its ticket; a reply with another is stale and dropped
    Optional<Path> select,       // what to select when that listing lands (a file named on the command line)
    List<Row> rows,              // the folder's rows, as listed
    Set<Path> selected,          // as the list last said
    Optional<Path> started, boolean armed, Optional<Remember> last)   // the session, as now
```

`rows` is in the state because M2's suggestions are computed from it in a step. The order the list shows is the
table's, not the state's, until M2 needs it (Shift reaches along the order shown).

## The messages M1 adds

The view asks the explorer:

| Record | Sent when |
| --- | --- |
| `Go(String text)` | the path bar is submitted (kept) |
| `Navigate(Path folder)` | the breadcrumb, a tree row, Enter on a folder row, Backspace or Alt+Up |
| `Chosen(Set<Path> paths)` | `Table.SELECTED`, rows turned back into paths by the view |
| `Activated(Path path)` | Enter or a double click on a row |

The explorer tells the view:

| Record | Topic | Says |
| --- | --- | --- |
| `Shown(Path folder, List<Row> rows, Optional<Path> select)` | state | what the list shows; the breadcrumb is the folder's own parts |
| `Summary(int items, int selected, long bytes)` | state | the status line's counts; the view words them |
| `Status(String message, boolean alert)` | edge | kept |

The explorer and its jobs:

| Request → reply | Job | Does |
| --- | --- | --- |
| `List(long ticket, Path folder)` → `Listed(long ticket, Path folder, List<Row> rows, String problem)` | `ListFolder` | a folder's rows; a folder it cannot read is a `problem`, never a throw |
| `Find` → `Found` | `FindPath` | kept |
| `Start(Path file)` → `Started(Path file, String problem)` | `Launch` | the shell's own open, for M1; Rasterware and the IDE through `Apps.find` with M4 |
| `Recall` → `Recalled`, `Remember` | the session jobs | kept |

`Row(Path path, String name, boolean folder, long size, Instant modified, Kind kind)` is 0.1's `files/Entry` with its
name and kind computed once in the job, so nothing on the GUI thread works them out per frame. `Kind` comes back from
0.1's `files/Kind` (folder, video, image, audio, document, text, code, archive, program, other).

The tree is the view's and its job's, as the template's navigator is, and never the explorer's: it asks
`TreeView.CHILDREN_WANTED`, the view asks `ListPlaces` (the top: the person's folders, then the drives with their
labels) or `ListLevel` (a folder's subfolders), and answers `TreeView.CHILDREN`. A tree row clicked is a `Navigate`.

## What the wiring builds

`Core` lists `Explorer` and the jobs `ListFolder`, `ListPlaces`, `ListLevel`, `FindPath`, `Launch`, `ReadSession`,
`WriteSession`. `Recipes` is unchanged: `look()` and `ui(gui, titleBar)`. `View`'s constructor takes one `Sender` per
record it sends.

## The window

`Ui` builds the handoff's regions, at its reference sizes and in its colours:

```
title bar (the framework's)
top bar     Breadcrumb, 48 px                            | sort note
tree 250    | file list, header 32, rows 34              | Suggestion Rail 400 (empty in M0 and M1)
            | Preview Dock under the list (none until M4)
status bar, 30 px: items · selected · size               | message
```

Three `SplitPane`s: the tree beside the rest, the list beside the rail, and (from M4) the dock under the list. The
template's `Navigator` goes: its path bar becomes Ctrl+L on the top bar (below), its tree becomes the places tree.

`Look` takes the Vexplore design system's tokens, not the handoff's: 0.1 found the design system came after the
handoff and replaced its teal with violet (VexelRay IDE owns cyan in the suite), and measured the ladder against it
(0.1's FN-6). The anchors are the suite's `SuiteLook`, copied while `vex-suite-common` is out of the v0.2 build; the
surfaces between rungs (chrome, the list's `bg-1`, the rail) are roles of Vexplore's own, and `LookTest` pins where
the ladder lands.

The regions are rem, the handoff's pixels over its 16 px root, so they grow with the zoom as their text does. A
narrow window at a high zoom gives the rail its 25 rem and the list what is left (framework-notes FN-2).

## The author's rulings

All of 2026-11-01, and built in M0: Ctrl+L turns the breadcrumb into a field in its place, and Esc or Enter puts it
back; the atlas's faces, with the design's two asked for upstream (framework-notes FN-1); `ListLevel` for the tree
and `ListFolder` for the list, kept apart (M1).

## What M1 built differently

Built 2026-11-01. Where the code departs from the sketch above, and why:

- **`Listing`, not `List`.** A record named `List` in `Messages` would hide `java.util.List` from every record
  beside it.
- **`OpenFile`, not `Launch`.** `dev.vexelray.framework.core.Launch` is the framework's command line, which
  `ReadSession` already takes. `OpenFile` opens through the engine's `NativePlatform.open` (framework-notes FN-4),
  since the build refuses an `os.name` test outside an OS module.
- **FindPath's answer is `Is`, not `Kind`.** `Kind` is 0.1's file kind, back as its own enum in `-core`, and a
  `Row` carries one; FindPath's folder-file-nothing became `Messages.Is`.
- **`ShowFolder` is gone.** `Shown` is the breadcrumb and the list together, sent when the listing lands, so the two
  never name different folders.
- **The tree's messages:** `PlacesWanted` → `Places(List<Place>)` and `LevelWanted(place, folder)` →
  `Level(place, folder, folders)`. A tree row is `Ui.Spot(place, folder)`: Documents under the places and Documents
  under `C:\Users\…` are two rows, and a tree's items are its rows' identities.
- **The rail waits for M2.** At 25 rem beside a list a split's minimum cannot protect (FN-2), the empty rail left
  the list 181 px wide, too narrow for a name. The body is the tree beside the list until the rail has something to
  say.
- **Esc puts the keyboard in the list**, not on the breadcrumb's last button: the list is where the folder the field
  named is about to be, and a drive's root, which has no button, is covered too (FN-3).

## The messages M2 adds

Proposed 2026-11-01, before any of it is written. M2 is the rail stating its effect: Select, Condition and Action as
chips, the rows a suggestion would reach marked in the list (never selected), and the card that says what would
happen. Nothing runs yet. Held Shift and Control wait for a modifiers topic (rebuild.md, *What it waits on*), so the
rail works from the selection alone.

**The engine is 0.1's `Suggestions`, back as it was**, in `-core` over `Row` instead of `Entry`: a pure function of
the rows, the selection, the pick and today. The explorer runs it in a step whenever one of those changes, as
rebuild.md has it; at hundreds of rows it is far under a frame, and a step cannot be late.

`Explorer.State` gains two fields:

| Field | Holds |
| --- | --- |
| `pick` | `Pick(Scope, Condition, Act)` so far, `Pick.NONE` when a folder is shown |
| `rail` | what the rail last offered, so a chip pressed is resolved against what the person saw |

The view tells the explorer:

| Record | Sent when |
| --- | --- |
| `Picked(Slot slot, int index)` | a chip is pressed; `Slot` is `SELECT`, `CONDITION`, `ACTION`, and the index is the chip's place in that row of the rail |

The explorer tells the view:

| Record | Topic | Says |
| --- | --- | --- |
| `Suggested(String basis, List<Chip> select, List<Chip> condition, List<Chip> action, Set<Path> reach, Card card)` | state | the whole rail; empty lists and an empty card when nothing is selected |
| `Chip(String label, int count, boolean on)` | (a field) | one chip, worded, and whether it is the one picked |
| `Card(String headline, String detail)` | (a field) | `9 files · 6.5 GB` (or `Move 9 files · 6.5 GB` once an action is picked) and `trip.mp4, IMG_2231.mov and 7 more, all marked in the list` |

`reach` is the paths the effect would touch: the view marks those rows with `ListView.marks`, in the handoff's amber
(a role of `Look`'s, as `Role.HIGHLIGHT` is the widget's default). The card's words are the explorer's, as the
status line's are, so a test reads them with no window.

`Listed` gains `today` and `zone`. "From today" and "If modified today" need the date, and a step may not read the
clock, so `ListFolder` says when it listed. A folder left open past midnight says "today" of the day before until it
is listed again (M5's watching relists).

**Why `Picked` is an index.** The chips are a fixed set of buttons, kept and relabelled (nothing on screen changes in
one frame), so a pressed chip knows only its place. The explorer resolves the place against `rail`, the rail it last
sent. If a listing lands between the person reading a chip and pressing it, the place may name a different chip;
that needs a click in the one frame where the rail changed under the pointer. The alternative, the view remembering
what each chip stood for, breaks `Ui` holding no state.

**The pick when the selection changes** is kept, as 0.1 kept it: a pick no longer on offer falls back to the
selection itself (`Just this file`), and one still on offer (`All videos here`, with one more video ctrl-clicked)
stays. A new folder resets it.

### The rail's room, against FN-2

The rail must be there whenever the list is, empty or not: a rail that appeared with its first suggestion would move
every row, which the design's third rule forbids. At the handoff's 25 rem it squeezed the list to 181 px in a narrow
window at a high zoom (M1). Three ways to size it:

1. **A share of the room beside the tree, a third** (proposed). 400 of the 1190 px the handoff's tree leaves. A narrow
   or zoomed window narrows the rail with the list, and the rail's chips clip before the list does; a drag of its
   divider still sets an exact width. The list stays the widest region at any size.
2. **25 rem, as the handoff.** Exactly the design at 1440 px and zoom 1, and FN-2 as measured elsewhere.
3. **400 dp.** Not grown by zoom, so the rail's text grows inside a fixed box. Against `Type`'s rule that a region
   holding text is rem.

### The card in M2

It states the effect and has no buttons: *Move 9 files* and *Keep as Mark* come with M3, when they do something.
A button that does nothing would be a lie the card's whole job is not to tell. Its tag says
`nothing has changed yet`.

## What M2 built differently

Built 2026-11-02. Where the code departs from the sketch above, and why:

- **`reach` is rows, not paths.** `ListView.marks` takes the list's own items, and a list has no way to read them
  back, so turning paths into rows would have meant `Ui` keeping the rows it was given. A `Row` is a value, so the
  explorer's rows are the list's.
- **The marked rows are violet, not amber.** The design system's `scope-wash` (0.1's), as `Look.TARGETED`: the
  design system keeps amber off every fill, and the reach is the person's pick. Amber is the "based on" line.
- **The explorer answers every `Picked`**, even one that changed nothing, and the rail puts a picked Select or
  Condition chip back in the frame it was clicked: the library's chip flips itself before the model answers
  (framework-notes FN-7).
- **Select and Condition one chip to a line**, Action two. A second phrase on a line was clipped out of reach in a
  narrow rail. "based on" sits under the title, and the card's tag under its headline, for the same reason. The rail
  scrolls when a short window cannot hold it; laid out without scrolling, it shrank the chips to slivers.
- **Rename is offered only when the effect is one row**, since it renames one. A rename picked falls back to no action
  when the reach grows past one.
- **The rail is a third of the room beside the tree** (option 1 above, ruled 2026-11-02), with drag minimums of
  20 rem for the list and 15 rem for the rail.

## The messages M3 adds

Proposed 2026-11-02, before any of it is written. M3 makes the card's button do what the card says: Move, Copy,
Archive, Delete into Vexplore's trash, Rename one, and Mark, each undone by Ctrl+Z. It is big enough to come in four
slices, each a commit with its test and its ottermate run, in this order:

1. **Delete, and undo.** The whole machinery with the action that needs no destination: the plan, the run with its
   count and its Stop, the journal, Ctrl+Z and Ctrl+Y, and the folder listed again afterwards.
2. **Move and Copy**: suggested destinations as a fourth row of chips, *Choose folder…* through `PickFile.folder`, and
   a plan that reads the destination for collisions.
3. **Archive and Rename**: a zip beside the files, and a name field in the card.
4. **Marks**: *Keep as Mark*, the marks listed in the rail, recalled as the reach, kept across runs by the session
   jobs.

**0.1's `Plan` and `Operations` come back** in `-core`, as pure values and a blocking executor, with their tests.

### One run does everything, undo included

`RunAction` is a `@Source` (a job cannot be stopped): it carries out a plan step by step, sends a count as it goes,
and returns what took effect. **An undo is a plan too**: the reverse of what was done (moved back, taken out of the
trash, renamed back, and the copies and zip Vexplore made removed outright, since nothing else had them). So the
same run, the same count and the same Stop serve both, and an undo that meets a surprise (a name taken since)
reports it as a run does, with nothing overwritten.

### Undo is the window's, the facts are the explorer's

The design wants Ctrl+Z, and the window's `History` is the framework's (the ruled pattern; `ValueWidgetUndoTest`).
It holds only numbers: when a run lands, the explorer sends `Recorded(entry, words)`, and the view records a `Change`
whose `apply` sends `Undo(entry)`, returning one that sends `Redo(entry)`. What each entry did is in the explorer's
state (`journal`), so the history never holds a path and the decision stays in a step.

`Explorer.State` gains:

| Field | Holds |
| --- | --- |
| `plan` | what the card's button would do, as the last `Planned` said, with its ticket |
| `running` | the run in flight: its number, its verb, how far it has got |
| `journal` | each entry's `Done`, by number, and whether it stands done or undone |
| `destination`, `destinations` | the folder picked for a move or copy, and the ones suggested (slice 2) |
| `marks` | named sets of paths (slice 4) |

The view tells the explorer:

| Record | Sent when |
| --- | --- |
| `Carry()` | the card's button: carry out the plan the card states |
| `Stop()` | the card's Stop, while a run goes |
| `Undo(long entry)`, `Redo(long entry)` | the window's history applies an entry |
| `Destine(Path folder)` | *Choose folder…* answered (slice 2) |
| `Rename(String name)` | the name field submitted (slice 3) |
| `KeepMark()`, `Recall(int index)`, `Forget(int index)` | the marks (slice 4) |

`Picked` gains a fourth `Slot`, `DESTINATION` (slice 2). The framework has a `Picked` of its own (`PickFile`'s
answer); the two never meet but in `View`, which names the framework's in full.

The explorer tells the view: `Suggested`'s `Card` gains the button's words (`Delete 4 files`, or empty when there is
nothing to do and the detail says why), what will be left alone and why, and, while a run goes, `3 of 9` and the Stop.
`Recorded(long entry, String words)` is an edge for the view to put in the history. After a run, the folder shown is
listed again, so the list says what happened (M5's watching will make that unnecessary).

The explorer and its parts:

| Request → reply | Part | Does |
| --- | --- | --- |
| `Planning(long ticket, Act act, List<Row> targets, Path destination)` → `Planned(long ticket, Plan plan)` | `PlanAction`, a job | reads the destination's names for collisions; delete and rename need none and are planned in the step |
| `Run(long run, Plan plan)` → `Ran(long run, Done done)`, sending `Progress(long run, int done, int total)`; stopped by `Stop` | `RunAction`, a source | carries out a plan, forward or back |
| `Destining(long ticket, Path folder, Kind kind)` → `Destinations(long ticket, List<Dest> places)` | `SuggestDestinations`, a job (slice 2) | 0.1's `Destinations`: nearby folders that already hold that kind |

### The trash

Vexplore's own, `~/.vexplore/trash/<when>/`, as 0.1's: the only route to the system's recycle bin from Java is
through AWT, which nothing here links, and a trash of its own is the same on every system. M3 never empties it; the
handoff's "undoable for 30 days" is a later TODO, and the card does not promise it.

### What M3 does not do

Held Shift and Control (FN-9), a drop onto a tree row, and remote folders, as rebuild.md has them. One run at a
time: `Carry` while a run goes is refused on the status line, not queued.

## What M3 built differently

Slice 1, delete and undo, built 2026-11-02:

- **0.1's `Plan.Kind` is `Plan.Op`**, since `Kind` here is what a file is, and **`Done` is a file of its own**, with
  `reverse()`: a move or delete moves back, a copy is removed (`Op.REMOVE`), a remove is copied again. Undo and redo
  are both "run the reverse of what the entry last did", so the journal keeps each entry's latest `Done` and a flag.
- **The explorer's run state is one field, `work`** (`Work`: the trash, the run in flight, the journal), not five.
  `Work.Way` (do, undo, redo) words the card and the status line, so `ran` has no switch.
- **`Recalled` carries the trash** (`~/.vexplore/trash`, from `ReadSession`): a step cannot read the home folder, and
  a test gives its own.
- **The card's tag is the run's line**: `nothing has changed yet`, then `Deleting · 3 of 9` while it runs, with Stop
  in the button's place. No `Planning` job yet: a delete is planned in the step from the rows.
- **Only Delete has a button in slice 1.** Every other action still states its effect, with no button, until its
  slice (`Act.plan` is null for them).

Slice 2, move and copy, built 2026-11-02:

- **Where it goes is `Aim`, a field of `Work`**, not of `State`: the places offered, the destination picked, and the
  plan for going there, each answer carrying its ticket. The destination is kept from one folder to the next.
- **The person's own Pictures or Videos folder comes first** among the places, as 0.1's `Destinations.home` had
  it, then the neighbours that already hold the kind. Mixed kinds get no suggestions; *Choose folder…* is always
  there.
- **0.1's `Plan.of` is `Plan.to`**, its skips worded for the card ("it is already there", "the destination is inside
  it", "a file of that name is already there"). A destination that cannot be read plans nothing and says why.
- **The card is pinned under the steps**, which scroll on their own: with a fourth row, the button had gone below
  the fold.
- **After a run the explorer keeps what is still selected.** The list keeps its selection across a listing of the
  same folder (its rows are values), and the explorer used to clear its own, leaving the rail empty beside a
  selected row.

Slice 3, archive and rename, built 2026-11-02:

- **Each `Op` knows its undo** (`undone()`, and `swaps()` for whether it goes back along each step), so
  `Done.reverse` has no list of cases. `ARCHIVE` is taken back by `UNARCHIVE`, which removes the zip, and the other
  way round for the redo; `RENAME` by renaming back.
- **An archive goes beside its files** as `Archive.zip`, or the first `Archive-n.zip` not taken, planned in the step
  from the folder's names (`Act.plan` takes them). Its run writes one zip and never over one already there.
- **Rename is planned when its name is typed** (`Rename(name)`), and Enter carries it out: the card has a field
  (`Card.naming`) instead of a button. The explorer sends `Naming(name)` once when Rename becomes the action, and the
  view fills the field from it, the name before the extension selected; a rail sent again never writes over what is
  being typed. A name that will not do is said on the status line, and nothing runs.

Slice 4, marks, built 2026-11-02:

- **A mark is the rail's reach, by path, named for where and what** (`Downloads: All videos here · 8`), newest first,
  six at most; one of the same files replaces the one before. Kept in `Work`, sent as `Marks(names)` (a state
  topic), and kept across runs in `Remember`/`Recalled` (one line a mark in the session store).
- **The card's button for Mark is *Keep as Mark***, and it changes no file, so there is no run and nothing for
  Ctrl+Z; a mark is forgotten by its ×. The marks are listed whether anything is selected or not.
- **A mark recalled shows its folder with its rows selected**, rather than marked: recalling is the person asking
  for those files. So `Shown.select` became a set (a typed file is a set of one).

## The messages M4 adds

Proposed 2026-11-02, before any of it is written. M4 is the Preview Dock under the list and the row menu, as
rebuild.md has it: tier 4 (bytes) first, since it is the floor every file reaches, then text, then pictures. As M3
did, it comes in slices, each a commit with its test and its ottermate run:

1. **The dock, and tier 4.** A region under the list (a `SplitPane`, stacked, the dock its sized pane), with the rail
   beside both, as screen 03 has it. One file selected: its name, what its first bytes say it is, its size, the
   tier's tag, and a hex dump, the printable strings in it and an entropy bar. A folder says it is one, with its
   count. None selected says how to fill the dock; several say how many and how much.
2. **Tier 3, text.** The first lines of anything that decodes as text, monospaced. Tier 2 (an archive's listing, a
   JSON tree, a CSV table) is not in M4: rebuild.md lists 1, 3 and 4.
3. **Tier 1, pictures and videos, as thumbnails.** A picture through `vexelray-codec-image`'s `Pictures`, a video's
   first frame through `vexelray-codec-video`'s `Videos.poster`, each at the dock's size, as Rasterware's
   `ReadThumbnail` does it. **Open in Rasterware** beside it (ruled 2026-11-01: Vexplore has no viewer of its own),
   found with the framework's `Apps.find`; where it is not installed, the desktop's own opener, said so.
4. **The row menu** (`ListView.action`, answered by `ListView.CHOSEN`): Open, then the rail's actions. **An action
   chosen from the menu picks it on the rail**, so the card states its effect and its button carries it out, as
   every other way in does; the menu is a shortcut to the pick, not a second way to act.

**0.1's `Previews` comes back** in `-core` for tiers 3 and 4 (a bounded read of the file's first 4 KB, nothing
executed), with its tests, as `Suggestions` did.

### The messages

The explorer asks the dock's job, and tells the view:

| Record | Topic | Says |
| --- | --- | --- |
| `Previewing(long ticket, Path path)` → `Previewed(long ticket, Preview preview)` | `ReadPreview`, a job | one file's preview, ticketed as a listing is |
| `Preview(Path path, Tier tier, String identity, long size, List<String> lines, double entropy, List<String> strings, boolean truncated, Optional<Feed> picture)` | (a value) | 0.1's, with the picture as Rasterware carries one |
| `Docked(Optional<Preview> preview, String note)` | state | what the dock shows: a preview, or a note (`Select one file to see it here`, `3 selected · 4.2 MB`) |

`Explorer.State` gains `dock`: the ticket asked and what is shown. The explorer asks when the selection becomes one
file, and a preview for a file no longer the one selected is dropped by its ticket.

The view tells the explorer: `Menu(String action)` for a row menu's choice (the right click has already made that
row the selection, as the list does), and, from slice 3, `OpenIn()` for the dock's button.

| Request → reply | Part | Does |
| --- | --- | --- |
| `Handing(Path file)` → `HandedOff(Path file, String to, String problem)` (was `Started`; see below) | `HandOff`, a job, slice 3 | Rasterware where installed, else the desktop's opener; in `-gui`, because `Apps` is the framework shell's |

### Not in M4

- **The inline preview in each row** (screen 03's PREVIEW column: a sparkline, a waveform, a first line). The design
  wants one; rebuild.md's M4 does not list it. Proposed as its own milestone after M7, since it reads every file in
  a folder and wants the watching of M5 first.
- **Pop out** (the dock in a window of its own). `Popout` is a widget of the library's; proposed for after slice 3,
  if wanted then.
- Tier 2, and PDF pages.

## What M4 built differently

Slice 1, the dock and tier 4, built 2026-11-02:

- **The sample is 64 KB, as 0.1 had it**, not the sketch's 4 KB: enough for an entropy that means something, still
  one read. The dump shows its first 256 bytes.
- **The dock's state is `Work.Dock`** (the ticket and the file asked for), beside `Aim`, not a field of `State`. The
  preview shown stays until the next lands, so the dock never flashes empty between two files.
- **The entropy bar is 32 cells, lit or not**, rather than a bar that grows: a new file changes colours, which fade,
  and never a width.
- **What the bytes say comes first, and the dump under it**: entropy, its reading and the strings found, then the
  dump, which scrolls. With the dump first, a dock of sixteen rows pushed the summary out of sight.

Slice 2, tier 3, built 2026-11-02:

- **0.1's rule for text, unchanged**: valid UTF-8 and no control characters but whitespace, with a character cut in
  half by the end of the sample forgiven only when the file goes on past it. An empty file is an empty text.
- **Sixty lines, each cut at 240 characters, tabs as four spaces**: the atlas has no glyph for a tab. A long line is
  clipped at the dock's edge rather than wrapped, so a line on screen is a line in the file.
- **The text's lines and the dump's rows are one pool of sixty nodes**, drawn in ink for a text and dimmer for a dump.
  No message changed: `Preview.Tier` gained `TEXT`.

Slice 3, tier 1, thumbnails and Open in Rasterware, built 2026-11-02:

- **`Preview` gained `Optional<Feed> picture`, as sketched, and nothing else**: the feed knows its own size, which is
  all the dock needs to draw it at its aspect. Tier 1 is two tags, `PICTURE` and `VIDEO`.
- **Only a file named or signed as a picture or a video is decoded as one**, so a text file never reaches imagelib and
  FFmpeg sees only what looks like a container. One that promised a picture and gave none falls a tier with the
  decoder's reason beside what it is (`unknown binary · not a video`), as 0.1 said it.
- **Decoded at 640 px on its longer side**, which the codec scales to as it decodes; drawn whole, at its aspect, at
  most 12 by 36 rem, so Open in Rasterware fits under it, and never larger than its own pixels at the root's 16 px.
- **The view gives the feeds back**: it keeps the picture shown and the one it replaced, which may still be
  crossfading out, and closes the one before. A preview dropped as stale was never drawn and needs no closing.
- **Open in Rasterware has its own reply, `HandedOff(Path file, String to, String problem)`**, not the sketch's
  `Started`. `OpenFile` already answers `Started`, and a record is one topic, so the explorer could not have told the
  two jobs apart, nor said that the desktop's opener stood in. `to` is `Rasterware` or `desktop`; the status says
  `Opened a.png in Rasterware`, or that Rasterware isn't installed and the desktop's own program opened it.
- **The button is under the picture, shown only with one**, and sends `OpenIn()`; the explorer hands on the file in
  the dock, so the view names nothing. `HandOff` is in `-gui`, beside the view, since `Apps` is the framework
  shell's, and asks for Rasterware as `pix`, its installer id until the suite's commands are renamed together.
