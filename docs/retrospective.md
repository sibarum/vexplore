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
