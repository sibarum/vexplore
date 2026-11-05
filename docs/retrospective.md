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

## M6 · Two Folders · 2026-11-04

One new finding (FN-16), small, and FN-12 met again and grown. FN-14 and FN-15 were written while M6 waited, by M8's
slices 3 and 4, and are read back with M8. Two Folders went where the stack said: the steps that knew nothing of two
folders read the used pane through methods and passed unchanged across the move, a drop is planned in the step that
hears it because the explorer already holds the other list's names, and a comparison of fifty thousand rows a side is
a pure function well inside a step's budget. Every action the comparison offers (a drop, *Remove*, *Move*, *Keep
newer*, *Keep both*) is a `Plan` the existing run carries out and the existing `Done.reverse` takes back with one
Ctrl+Z; M6 added no `Op` and no undo of its own.

### The shapes

| | The promise | What happened | How it looked |
| --- | --- | --- | --- |
| FN-12 | A list says what it knows about a row that is not a choice | One marked set, and a relation set by the other folder | `Tags<K>`: six nodes a row, two per list |
| FN-16 | A part is tested by handing it what it needs | `Sender` is no lambda | A bus, a tick and an inbox to collect a count |

### What would stop each

12. As M5 said, now with its third and fourth set: a list holds named marks, a map from a mark's name to its set and
    its look, told whole and worn by a row realized later. A mark the list drew would also name itself to a script,
    where an icon in a cell needs its words put in its `role` (FN-12).
16. A test sender, `Sender.of(Consumer<T>)` or `Sender.collecting(List<T>)`, delivering at once with no bus. Every
    source and every job with a progress topic meets it (FN-16).

### My own mistakes

- **A question the design had already answered.** Planning slices 3 to 5, I asked the person where a STATUS column
  should sit and how wide, when design C (Orbit) had since grown a Two Folders board that draws the comparison as
  marks and cards, with no such column. They pointed me at it; the slices were re-planned against the board. The
  rule: before asking how something should look, look at whether the chosen design draws it.
- **A script that expected the wrong count.** Slice 4's live run waited for *Moved 12 files*; the offer moved 13.
  The run stopped after the move and before its undo, so the disk was left changed and I moved the files back by hand
  before running again. As in M3 and M4: wait on what the window says, counted from the fixture, not from memory, and
  a script that changes the disk undoes itself before it checks anything it might miss.
- **A note that claimed a test before it existed.** Slice 5's notes in shape.md said selection picking the conflict
  was tested in `-core`; it was not yet. Caught on reading the notes back, and the test written. A note says what was
  done, and is written after it.
- **A test that heard nothing, again.** `ExplorerTest` saw no `Conflict` because `CONFLICT_TOPIC` was not among the
  topics it listens to: M4's mistake, word for word, and M4's rule (a new topic goes on that list in the same edit)
  was not followed.
- **A view test that forgot the session.** `ViewTest`'s *Keep both* was refused with *Nothing can be done before the
  session is read*, because the test never published `Recalled`. `start()` says why it refuses, which made it a
  one-line fix; a test of anything that runs publishes the session first.
- **Words read only after they were seen.** *Downloads's copy* went into the status line and was reworded to
  *kiln-cam.mp4 from Downloads* after the live run, so the wording the window now shows was tested and not seen.
- **A gesture the tool made too fast.** Slice 2's first drag was `ottermate`'s one-step `drag`, which carries and
  drops with no frame between; the framework rightly commits nothing for that. The step-by-step gesture (`down`,
  `move`, `settle`, `up`) worked. Not a gap in either: a drag a person could not make is not a drag.

### Not seen

- **The names.** At 1440 px NAME is still four or five letters in each list (FN-8, TODO); hiding the places did not
  give it enough.
- **A copy across two drives**, in a drop, a *Move* offer or a *Keep*: tested, never driven.
- **A TO copy that is the newer, neither newer, a stale press, Stop during a check**: tested in `-core`, not driven.
- **The check's count.** Two small files are read before a frame shows how far it has got.

### What to keep exactly as it is

- **One plan, one run, one undo.** Four new actions and not one new way to touch the disk: each says its effect in
  an entry's words before it runs and in the past tense after, and Ctrl+Z reverses each whole.
- **Facts derived, not remembered.** The comparison, the offers and the conflict are worked out from both listings
  whenever either changes and sent whole; a press is checked against the listings, so a pair that changed since it was
  shown is refused and said, with no copy of what was shown to go stale.
- **The used pane behind methods.** Making `State` two panes and its old fields methods let 43 tests prove the move
  before any test of two folders was written.
- **Nothing built after the first frame.** The marks, the tiles and the dock's two halves are kept nodes, shown,
  hidden and rewritten; an `IconView` changing its source passed `Cuts`.

## M5 · Fresh files · 2026-11-02

One finding (FN-12): a list marks one set of rows, and the rail's reach already has it, so the *new* tag is a second
set kept beside the list. The rest went where the stack said. A `@Source` was the right shape for a watch, a newer
`Watch` stopping the old run with no code of Vexplore's, and the rule that a step cannot read the clock decided the
design for the better: the watcher keeps time, and the explorer holds a set. Both timings (`Burst`, `Arrivals`) are
values tested without a clock or a disk.

### The shapes

| | The promise | What happened | How it looked |
| --- | --- | --- | --- |
| FN-12 | A list says what it knows about a row that is not a choice | One marked set, and a face built once from its item | `Tags`: the name cells hand their tags over by path, and a new set shows and hides them |

### What would stop each

12. A list holds named marks, each a set with its look, told whole and worn by a row realized later, as `marks` is
    now (FN-12). M6's comparison wants a third (*newer here*, *only here*).

### My own mistakes

- **The sketch half-proposed a predicate.** shape.md said the face would read the set the view kept. `ListView`'s
  own javadoc on `marks` says why a predicate read in the frame is the wrong shape (threading.md, T7.2), and I read it
  only when I came to write the face. Built as `marks` is instead, told whole. The rule: read the widget's javadoc for
  the nearest thing it already does before a sketch says how a view will do the next.
- **A path for the wrong shell.** The first live script typed the folder as `/c/Users/…`, which Git Bash understands
  and the app's path field does not; the run waited 30 seconds on a folder it never opened. Worse, the helper beside
  it was set to drop a file into the folder whenever it saw its cue, so a stopped run could have left it there; it was
  stopped by hand. A helper that changes the disk acts only on its cue and gives up otherwise.
- **Edits by pattern that hit the wrong line.** A substitution for `Work`'s constructor calls rewrote `withDock` so
  that it set the watched folder, and a line-range delete in `Ui` took the next method's `/**` with it. Both were
  caught by reading the result back, neither by the build. A record with one more component is edited call by call.
- **`wait` is `Object`'s.** `Burst.wait(long)` did not compile; the compiler caught it at once.
- **An assertion that tested nothing** (`dir.resolve("b").equals(path("b"))`) went into a test and was taken out on
  reading it back.

### Not seen

- The tag fading in and out. The stills show it there and gone; whether it faded between is the framework's promise
  for a node shown and hidden, and `ViewTest`'s `Cuts` check does not reach a list row's tag.
- A folder stirring for a minute (a download). The burst's timing is tested; what a listing a second looks like in
  the list, whose rows are rebuilt on every `items()`, was not watched.

### What to keep exactly as it is

- **Time kept by the part that waits.** The watcher already waits on the disk with a timeout, so it waits on the
  tags' ends too, and the explorer stays a function of what it was told.
- **A re-listing that keeps what the person was doing.** A stir keeps the selection and the pick; the list reselects
  by path, so a file that grew stays selected.

## M4 · the Preview Dock and the row menu · 2026-11-02

One finding (FN-11), small: a list's row menu is the same for every row, where a tree's can grey an action, so
Rename is listed with several selected and refused when chosen. The rest of M4 went where the stack said it would.
The codecs gave a picture and a video's poster frame as a `Feed` that the dock crossfades, `Apps.spawn` found the
installed Rasterware, and `ListView.action` gave the menu its selection, its landmarks and its message with no code
of the application's at the right click. Nothing in the dock needed a node built or removed after the first frame.

### The shapes

| | The promise | What happened | How it looked |
| --- | --- | --- | --- |
| FN-11 | A widget's item menu offers what applies to the item | A list's menu is declared once and offers everything | Rename on the menu with three files selected, refused when chosen |

### What would stop each

11. A list's menu consults the item and the selection for the ids hidden and greyed as it opens, as a tree's entry
    already carries them (FN-11).

### My own mistakes

- **The sketch reused a reply type.** shape.md had `HandOff` answer `Started`, which `OpenFile` already answers; a
  record is one topic, so the explorer could not have told them apart. It was approved that way and caught only when
  the slice was built, and it cost the person a second decision. The rule: before a sketch names a reply, check
  whether any other part already sends that record.
- **A catch that blamed the wrong program.** `HandOff`'s first draft caught the desktop opener's failure in the same
  block as Rasterware's spawn and said Rasterware had failed. Split before the first build, but only because it was
  read back. Each fallback gets its own `try`.
- **A test that heard nothing.** `ExplorerTest` records only the topics it subscribes to, and the first `Handing`
  test failed because `HANDING_TOPIC` was not among them. A new topic the explorer sends goes on that list in the
  same edit.
- **Layout seen only in the screenshot, again.** The picture's limit was the dock's whole height, so Open in
  Rasterware sat half below the edge; and an import placed by a one-line substitution landed out of order. As in M3,
  the screenshot and the diff found what the build could not.
- **Scripts that assumed.** One counted rows instead of waiting for the dock's name, one pressed keys after a click
  had moved the focus to a button, and one waited on `crumbs`, whose name is empty, instead of `path`. Each cost a
  run. The rule from M3 holds: wait on a landmark that names what is wanted, and look at `Landmarks.java` before
  writing the wait.
- **Rasterware's own window was never seen.** `ottermate --launch` ends the whole process tree, and the Rasterware it
  spawned with it; the run proves the spawn and the status, not that Rasterware showed the picture.

### What to keep exactly as it is

- **The dock never builds a node after the first frame.** Sixty lines, thirty-two cells, one picture box and one
  button, rewritten and shown or hidden: tiers 4, 3 and 1 changed nothing the Cuts check could catch.
- **A preview is decoded only when the name or the first bytes promise one**, and falls a tier saying why when it
  does not, so a renamed file never surprises and a text file never reaches FFmpeg.
- **One reply per job.** `HandedOff` says what opened the file, which is what let the status say the fallback.
- **The menu picks; the card carries out.** Every way into an action ends at the same card and the same button, so
  there is one place that states the effect before anything happens.

## M3 · actions · 2026-11-02

One finding (FN-10), and it is small: a field has no initial value, so filling it once took a message of its own.
The rest of M3 went where the framework said it would. A `@Source` with a Stop carried every run, the window's
`History` held the undo with changes that send messages, `PickFile.folder` answered *Choose folder…*, and two
ticketed jobs planned a move against its destination. FN-7 (a chip that flips itself) and FN-3 (a hidden node that
keeps the keyboard: the card's button, hidden as its run starts) were met again and needed nothing new.

### The shapes

| | The promise | What happened | How it looked |
| --- | --- | --- | --- |
| FN-10 | A view writes what the model says, every time | A field written every time loses what is being typed | Rename's field, filled once by `Naming` instead of by each rail |

### What would stop each

10. `TextField.initial(String)`, applied when the field is shown, or a node's visibility read back (FN-10).

### My own mistakes

- **A test helper that forgets as it reads, read twice.** `took` clears everything seen, and four tests took one
  kind and then looked for another, or took the same kind twice in one expression; two of them would have passed
  with nothing checked. Fixed with `seen`, which forgets nothing. The rule: a reader with a side effect is named for
  it, and a test reads with the one that has none unless it means to start afresh.
- **Text substitutions whose delimiter was in the text.** A `|`-delimited Perl edit met `||` and spliced code onto the
  top of `Suggestions.java`; a `{}`-delimited one met an unbalanced brace and swallowed `Explorer.java` (restored from
  the commit); a `#`-delimited one met `{@link #undone}`; and a heredoc turned `\n` and `\t` into real line breaks
  inside Java strings. Each was caught by the compiler, none was committed, and each cost a round. The rule: a
  multi-line Java edit goes through the Edit tool, and a shell substitution is for one line of known text.
- **A script read the first row as the first file.** With folders listed first, `HOME` selected `clips`, and the
  first Move went to plan a folder. A `tree rail` before the click showed it at once.
- **The card's button was laid out where a fourth row pushed it below the fold**, and the list kept a selection the
  explorer had dropped. Both were seen only in the screenshots, which is what they are for.

### What to keep exactly as it is

- **An undo is the reverse plan, run the same way.** One source, one count, one Stop, and one rule against
  overwriting, for a run and its undo alike; each `Op` knows its own undo, so `Done.reverse` has no cases.
- **The history holds numbers; the explorer holds what they did.** `Journaled` sends `Undo(entry)`, and whether the
  entry can be taken back is a step's decision.
- **Every job answer carries its ticket**, the listing's rule from M1, now for places and plans too: a plan for the
  folder chosen before is dropped, tested by sending it late.
- **Each slice was a commit with its own live run**, on scratch files only, with the person's real folders shown
  and never touched.

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
