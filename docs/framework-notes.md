# Framework notes

Findings about VexelRay, vexelray-gui, Kronometer, tactroller and atchung that came out of building
**Vexplore** — things the framework does not have, does not document, or does in a way that cost time to
discover.

**Why this file exists.** An application built on a framework is the only place its gaps are visible, and they
are visible exactly once: at the moment they are worked around. A workaround with no note beside it becomes a
piece of application code nobody can tell from a design decision, and the framework never hears about it. So
the rule is to write the note *when the workaround is written*, not in a retrospective, and to write down what
was measured rather than what was assumed.

**Before writing a workaround, ask whether it is a component.** If the answer is "every project on this
framework will write these same four calls" — that is a finding, and the fix belongs upstream. Say so here
with that framing, so the [retrospective](retrospective.md) at the end of the milestone has a candidate rather than a
complaint.

## How to write one

    ## FN-1 · One line saying what is missing 🔬
    
    What was wanted, what the framework offers instead, and what was done about it.
    Then: what it costs, and what the framework could do about it.

The markers are a filter, not decoration:

| | |
| --- | --- |
| 🔬 | a framework gap — something upstream could fix |
| 💡 | an idea, not yet a finding |
| 📋 | carried over: needs re-verifying against a newer build before it is repeated |

## Findings

## FN-1 · An application cannot bring its own faces into the text atlas 🔬

The design system names Instrument Sans for text and IBM Plex Mono for paths, sizes and anything tabular. The text
atlas is baked by `vexelray-text` at build time from its own sans and mono, and `Type` can only pick face 0 or face 1
of it. So Vexplore draws in the atlas's faces, and the design's two are not in it at all. Nothing was worked around:
the faces are simply not the design's.

**For the framework:** a way for an application to name fonts for its atlas (a pom setting the atlas build reads,
as the icon bake reads `src/main/icons`), with the charset beside it. Or `vexelray-text` shipping these two as
faces 2 and 3, if the suite is to share them.

## FN-2 · A split's minimum holds against a drag, not against the window 🔬

The Suggestion Rail is the sized pane of a `SplitPane` (25 rem), and the file list takes the rest, with
`minFirst(20 rem)` so that a narrow window takes room from the rail, never from the list. `SplitPane` uses its
minimums only to clamp a drag (`drag`); laying out, it gives the sized pane its size and the free pane `grow(1)`, so a
narrow window shrinks the list to whatever is left. Measured: a 1207 px window at zoom 1.563 left the list 181 px
beside a 625 px rail. Nothing was worked around; at the handoff's 1440 px and zoom 1 it does not show.

**For the framework:** the free pane's minimum held at layout (a `minWidth` on the free box from its `min*`), and
the sized pane giving way down to its own minimum. Every application with a side panel will meet it.



## FN-3 · A hidden node keeps the keyboard 🔬

The path field takes the breadcrumb's place while Ctrl+L has it, and Esc hides it again. Hidden, it still had the
keyboard (`ottermate tree` showed it `hidden focused`), so the next keys typed would have gone into a field nobody can
see. `Gui` can move focus to a node but has no way to let it go, and hiding a node does not move it. So `Ui.closePath`
hands the keyboard to the breadcrumb's nearest ancestor button; at a drive's root, which has none, the hidden field
keeps it.

**For the framework:** a node hidden while it has the keyboard gives it up, to the next focusable node in order, as
a removed node presumably must; or `Gui.blur()`. Any application that swaps one control for another in place meets it.
