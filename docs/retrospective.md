# Retrospective

What each milestone of **Vexplore** showed when it was read back: the shapes in
[framework-notes.md](framework-notes.md) since the last one, what would stop each, what went wrong in how the work
was done, and what to keep. The notes are written one at a time, as each workaround is written. This file is the pass
over them, and it is where the framework takes its proposals from.

**When.** At the end of every milestone, before the next one starts, and always before a rebuild or an archive. A
milestone with no new findings gets one line saying so. The framework's `docs/developers/retrospective.md` has the
reasons and the full method.

**Never delete a section.** Newest milestone first. A rebuild carries this file over.

## How to write one

    ## Milestone name · 2026-10-22
    
    The count: N findings (FN-a to FN-b), how many of them architectural.
    
    ### The shapes
    | | The promise | What happened | How it looked |
    
    ### What would stop each
    1. One structural change per shape, numbered so a plan can cite it.
    
    ### My own mistakes
    What was trusted, what it turned out to be, and the rule that covers it.
    
    ### What to keep exactly as it is
    What carried the milestone, and why.

## Milestones

## M2 · the rail states its effect · 2026-11-02

Three findings (FN-7 to FN-9), none architectural: a chip that decides for itself, a table column with no floor of its
own, and a topic the roadmap already expected a witness to ask for. FN-2 was answered by the ruling rather than
worked around: the rail is a share, not a width.

### The shapes

| | The promise | What happened | How it looked |
| --- | --- | --- | --- |
| FN-7 | A chip is a button that holds a value | It holds its own, and changes it before the model answers | a picked chip clicked again went dark; two chips lit at once for a frame |
| FN-8 | A table gives its growing column the room | And floors it at the same 1.5 rem as any other, so it goes first | a 772 px window: icons under NAME, every date whole |
| FN-9 | The read models get a topic when a witness asks | None yet for the modifiers held | the rail works from the selection alone; Shift and Control are only themselves |
| FN-2 again | A split's minimum keeps a pane usable | Ruled around: the rail is a third of the room beside the tree | at 1440 px exactly the handoff; at 1024 px the list keeps every column |

### What would stop each

7. A chip that publishes its press and waits for `show`, or a one-of-N chip group in lines (FN-7).
8. A minimum per column, the fixed ones giving way past it (FN-8).
9. A state topic of the modifiers held (FN-9).

### My own mistakes

- **The sketch put `reach` as paths without asking the list how to mark.** `ListView.marks` takes the list's own
  items and nothing reads them back, so paths would have needed `Ui` to keep the rows. Rows are values; the message
  changed after it was agreed. The rule: before sketching a message a widget consumes, read the widget's setter.
- **The rail's chips were laid out two to a line from 0.1 without a narrow run first.** The first screenshot was
  the saved 772 px window: chips sliced flat by a column that could not scroll, and a second chip clipped out of
  reach. Two fixes, both found by looking. Drive the narrow window and the design's size before calling a layout done.
- **The sketch said amber for the marked rows.** The design system (and 0.1, which had measured it) keeps amber off
  every fill. Read `Look`'s own doc before naming a colour in a proposal.
- **A stale frame almost became a framework note.** The table's columns looked stuck after a resize; a second
  `settle` showed them re-solved. One more frame before writing an FN.

### What to keep exactly as it is

- **The engine is a pure function in a step.** 0.1's `Suggestions` came back with its tests nearly unchanged, and
  the explorer's three steps (`chosen`, `picked`, `listed`) only decide when to ask it.
- **The pick kept is the one asked for, not the one that applied.** A pick too wide for the selection falls back,
  and comes back when the selection allows it again, with nothing remembered beside it.
- **The card's words are the explorer's.** `ExplorerTest` reads "Move 3 files · 60 B" with no window, as it reads the
  status line's counts.
- **Every rail node is built once.** Labels change and nodes hide; `ViewTest`'s cut watch passes with the rail
  changing under it.

## M1 · browsing, read only · 2026-11-01

Three findings (FN-4 to FN-6). One is close to architectural: FN-5, where a chord that moves the keyboard acts a
frame after the keys behind it are routed, which is the message model's own timing. The other two are missing knobs.
FN-2 from M0 came back the moment the list had rows.

### The shapes

| | The promise | What happened | How it looked |
| --- | --- | --- | --- |
| FN-4 | The framework opens things with the desktop | It does, for the About window only; `Links` is package-private | `OpenFile` calls the engine's `NativePlatform.open` past the framework |
| FN-5 | A chord is declared, and the dispatcher does the rest | The view acts on it a frame later, and keys in that frame go to the old focus | `C:/` typed into the list, `Users/...` into the field, a path resolved under the wrong folder |
| FN-6 | A status bar's slots never jump | Nor do they shrink, so the right one is drawn over the left | `0 itemsNothing at C:\...` |
| FN-2 again | A split's minimum keeps a pane usable | The empty 25 rem rail left the list 181 px | Folder icons and a TYPE column, with no names |

### What would stop each

4. A framework job, `Open(target)` → `Opened(target, problem)`, over what `Links` already does (FN-4).
5. A chord that can be declared as "focus this node", applied by the dispatcher before the next key is routed
   (FN-5).
6. A status bar whose right side gets what the left leaves, clipped at its far end (FN-6).

### My own mistakes

- **The sketch named a record `List` and a job `Launch`.** Both were agreed, and both collide: one with
  `java.util.List` in the same file, the other with the framework's command line that `ReadSession` already takes.
  The rule: before proposing a name, look for it among the imports of the file it will live beside.
- **An `os.name` test in `-core`.** The build refused it, rightly. The engine already had an opener per OS, and the
  global rule says to look in the sibling checkouts before writing a replacement for anything. I looked after.
- **The rail was laid out again knowing FN-2.** M0's retrospective had the 181 px list in it, and M1 put rows in that
  list beside the same empty rail. It should have been taken out before the first run, not after the screenshot.
- **A script's race read as a path bug.** `key CTRL+L` then `type` with no `settle` lost three characters, and the
  first reading was that `Go` resolved paths wrongly. A `find path` before Enter would have shown the field's text
  at once. When a typed value comes out wrong, read the field before reading the code.
- **Appending LF lines to CRLF files.** The working tree is CRLF (autocrlf), the repository LF; appending with a
  heredoc mixed them in four docs. Normalised before the commit. Edit with the Edit tool, or normalise after.

### What to keep exactly as it is

- **A listing carries a ticket.** Jobs answer in no order, and the explorer drops a listing a later ask has
  overtaken, in a step, tested with the replies published by hand in the wrong order.
- **A tree row is a place and a folder (`Spot`).** The same folder under two places is two rows, so the tree's
  identity rule and the file system's shape never fight.
- **`Shown` is the breadcrumb and the list together.** Sent when the rows land, so the two never name different
  folders, and a folder that cannot be read is still where the person asked to be.
- **The jobs are tested by calling `run`**, against a real temporary folder, with no lane: `FilesTest` is where a
  listing's order and its problem words are pinned.

## M0 · the template becomes an explorer · 2026-11-01

Three findings (FN-1 to FN-3), none architectural: each is a missing knob on something that works.

### The shapes

| | The promise | What happened | How it looked |
| --- | --- | --- | --- |
| FN-1 | The design names its faces | The atlas's two faces are the only ones, and an application cannot add one | Vexplore in the stack's sans and mono |
| FN-2 | A split's minimum keeps a pane usable | It holds against a drag, not against a narrow window | The list squeezed to 181 px beside a 625 px rail |
| FN-3 | Hiding a control hides it from the keyboard | A hidden field kept the keyboard | Keys after Esc would have gone into an unseen field |

### What would stop each

1. Fonts an application names for its atlas, baked as its icons are (FN-1).
2. A split's minimums held at layout as well as on a drag (FN-2).
3. A node hidden with the keyboard gives it up, or a `Gui.blur()` (FN-3).

### My own mistakes

- **The sketch took the handoff's colours as the design's.** 0.1 had already found that the design system replaced
  them (teal became violet), and its `Look` said so. The rule: before taking a value from the design folder, read
  what 0.1 last did with it, since 0.1 is where the design met the code.
- **A token guessed instead of looked up.** `LookTest` first pinned `raised` at a value written from memory;
  `SuiteLookTest` had the real one. Copy a measured value from where it was measured.
- **Stale test classes counted as passing tests.** Deleted tests still ran from `target/` until a `clean`. After
  deleting a test, build clean before reading the count.
- **The regions were sized in rem without checking what a rem is here.** It is 16 px times zoom and density, and
  the run's zoom was 1.563, so every region was half again what the handoff draws. Right for zoom, but it should
  have been measured before the screenshot surprised me.

### What to keep exactly as it is

- **Deleting first, as its own green commit.** The new shape was sketched against a tree that only did what was
  left, so nothing of the editor leaked into it.
- **`ottermate tree` after every visual change.** The numbers (a 1385 px breadcrumb in a 1207 px window, a 181 px
  list) said what was wrong faster than the screenshot did.
