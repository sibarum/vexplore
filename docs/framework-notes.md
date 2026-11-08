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
beside a 625 px rail. At the handoff's 1440 px and zoom 1 it does not show.

Since M2 the rail is a share of the room beside the tree, a third (ruled 2026-11-02), not 25 rem: a narrow or zoomed
window narrows it with the list, and the list stays the widest region at any size. Its Select and Condition chips sit one to a
line and it scrolls, so a narrow rail loses width, not a chip. The gap is the same, and the table inside the list has one of
its own (FN-8).

**For the framework:** the free pane's minimum held at layout (a `minWidth` on the free box from its `min*`), and
the sized pane giving way down to its own minimum. Every application with a side panel will meet it.



## FN-3 · A hidden node keeps the keyboard 🔬 (fixed 2026-11-04)

*Fixed in vexelray-gui (`InputDispatcher.handOnHiddenFocus`, held by `FocusHandOnTest`): a node hidden while it has
the keyboard hands it to the next focusable node in Tab order, before the next frame's keys, and `Gui.blur()` lets
go of it on purpose. `Ui.closePath` still focuses the list itself, since that is where it wants the keyboard. Kept
below as found.*

The path field takes the breadcrumb's place while Ctrl+L has it, and Esc hides it again. Hidden, it still had the
keyboard (`ottermate tree` showed it `hidden focused`), so the next keys typed would have gone into a field nobody can
see. `Gui` can move focus to a node but has no way to let it go, and hiding a node does not move it. So `Ui.closePath`
hands the keyboard to the breadcrumb's nearest ancestor button; at a drive's root, which has none, the hidden field
keeps it. Since M1 it hands it to the list instead, which is always there, so the root case is covered too; the gap
is the same.

**For the framework:** a node hidden while it has the keyboard gives it up, to the next focusable node in order, as
a removed node presumably must; or `Gui.blur()`. Any application that swaps one control for another in place meets it.

## FN-4 · Opening a file with the desktop is the framework's, and private 🔬 (fixed 2026-11-05)

*Fixed in vexelray-framework: a part sends `Open(target)` and hears `Opened(target, problem)`, both in
`dev.vexelray.framework.core`, answered on the job lane by the framework with no recipe or `Core` entry. `OpenFile`,
`Start` and `Started` are gone, and `-core` no longer names `vexelray-os-api`. `HandOff` still calls the engine's
opener itself, as its fallback when Rasterware is not installed.*

M1 opens a file on Enter, as the desktop's file manager would. The build's isolation check rightly refuses an
`os.name` test in `-core`, and the engine already has the seam, `NativePlatform.open`, with an opener per OS module.
The framework uses it for the About window's links through `Links`, which is package-private in
`vexelray-framework-shell`. So `OpenFile` calls `NativePlatform.current().open` itself, reaching past the framework
to the engine's OS seam: it works, and it is the one place Vexplore names `vexelray-os-api`.

Every application that opens anything (MainFrame's links, the IDE's "show in folder", Rasterware handing off) will
write the same call and the same "could not open" wording. A framework job for it, `Open(target)` answered by
`Opened(target, problem)`, would be the message-model shape of what `Links` already does.

## FN-5 · Keys typed straight after a chord that moves the keyboard land where it was 🔬 (fixed 2026-11-04)

*Fixed in vexelray-gui, without the declaration proposed below: a chord that sends a message holds back the rest of
that frame's input and delivers it at the start of the next, by when the view has heard the message and moved the
keyboard (`InputDispatcher.holdRest`, held by `FocusHandOnTest`). Every application gets it with nothing to declare.
Driven again with no `settle` between `key CTRL+L` and `type C:/Users/User/Documents`: the field got every
character. Scripts no longer need that `settle`. Kept below as found.*

Ctrl+L is declared as a chord that sends an `Intent`; the view hears it in the next frame and focuses the path
field. Keystrokes that arrive before that frame go to whatever had the keyboard. Driven by ottermate with no
`settle` between `key CTRL+L` and `type C:/Users/...`, the first three characters reached the list, the field got
`Users/...`, and the explorer resolved it against the folder shown. With a `settle` between them the field gets
every character. A person is rarely that fast, but a fast typist's Ctrl+L and first letter can share a frame.

Nothing was worked around; scripts settle after a chord that moves focus. A chord that is declared as "focus this
node" (rather than a message the view acts on a frame later) would let the dispatcher move the keyboard before the
next key is routed, which is the only order that cannot lose a character.

## FN-6 · A status bar's slots overrun each other 🔬

`StatusBar`'s left and right rows never shrink or clip, so a long message on the right is drawn over the counts on
the left (`0 itemsNothing at C:\...`). The application cannot bound a slot: `minWidth` reserves room but nothing
caps it. Vexplore's messages name a folder rather than its whole path where the breadcrumb already says where it
is; "Nothing at" still carries the typed path, since that is what was wrong. The bar could give its right side
what is left after the left and clip there, as a breadcrumb folds its far end.

## FN-7 · A chip flips itself before the model answers 🔬

The rail's Select row is one-of-several: exactly one chip is picked, and which is the explorer's call (a pick that no
longer applies falls back to the selection). `Button.toggle` is the library's chip, and a click flips its own pressed
state and publishes `TOGGLED` before anything has decided. So a picked chip clicked again goes dark, though nothing
changed, and a new chip lights beside the old one until the explorer's answer arrives. `RailPanel` puts a picked
Select or Condition chip back at once, in the frame of the click, and the explorer re-sends the whole rail on every
`Picked`, even one that changed nothing, so every chip is shown as the model has it. Each chip is also
`undoable(false)`: as an undo step, Ctrl+Z would flip a chip with no model behind it.

**For the framework:** a chip that asks rather than flips: a press published as `PRESSED` with the chip left as it
is, for `show` to set. Or a chip group whose one-of-N is the widget's, as `Segment` is, but laid out in lines. Every
filter bar whose model can refuse or redirect a pick meets it.

## FN-8 · A table's growing column has no minimum of its own 🔬

NAME is the list's `grow(1)` column, and TYPE, SIZE and MODIFIED are rem. `Table` floors every column at its own
1.5 rem and gives the growing one what the fixed ones leave. So in a narrow list the name, which is what a row is for,
is the column that goes, down to its icon. Measured: a 772 px window at zoom 1, with the tree and the rail beside it,
left NAME 1.5 rem and every other column whole. At 1024 px and above it does not show. Nothing was worked around: no
width given to the other three survives a narrow enough window, and the rail's share (FN-2) already gives the list two
thirds of what the tree leaves.

Two Folders (M6) meets it at every width: two lists share what one had, and at 1440 px with the places and the rail
beside them each is about 390 px, so NAME is its icon in both. Taking the tree away for Orbit's places (M8) gave back
50 px, not enough.

**For the framework:** a minimum per column (`Column.of(..., Length.grow(1)).min(Length.rem(12))`), with the fixed
columns giving way past it, last column first. Every table whose one growing column is its identity meets it.

## FN-9 · Held Shift and Control have no topic 🔬 (fixed 2026-11-05)

*Fixed in vexelray-framework: the explorer hears `Modifiers` for the main window, holds it in its state, and while
Shift or Control is held the rail's Which files row is what the key proposes (`Intents`, back from 0.1), headed
"a range" or "a rule"; choosing one makes it the selection. A drop between the two lists copies with Control held and
moves with Shift (`DropEffect.byKeys`, 2026-11-07): the list shows it before the release, and `Dropped` carries the
operation the drop became, so no topic of held keys races it.*

The design's second principle reads a held modifier as intent: Shift held proposes where a range ends, Control held
proposes the rule the picks are examples of. 0.1's `Intents` computed both. On 0.2 an application cannot hear which
modifiers are held: `Seams.modifiers()` is the widget library's, and the roadmap's step 16 left the read models with
"no topic for these yet", to be added when a witness asks. Vexplore is that witness, and asks here. Nothing was worked
around: the rail suggests from the selection alone, and `Intents` stays in 0.1's history until a topic exists.

**For the framework:** a state topic of the modifiers held, `Modifiers(Set<Modifier>)`, published on each change, so
a step can hold the last one and suggest within the pause before the second click.

## FN-10 · A field cannot be filled once and then left to the person 🔬

Rename puts a field on the rail's card holding the file's name. The rail is sent again whenever the selection or a
pick changes, and a view that writes the field's text from each one writes over what is being typed. The view
cannot tell whether the field was just shown (a node has `visible(boolean)` and no way to read it back) and must
hold no state of its own. So the explorer sends `Naming(name)` once, when Rename becomes the action, and the view
fills the field from that alone. It is a fair shape, a one-off command rather than a value, but it took a message of
its own for what a form toolkit calls an initial value.

**For the framework:** `TextField.initial(String)`, applied when the field is shown and ignored while it is, or a way
to read a node's visibility. Every form in a panel that is re-shown from a model meets it.

## FN-11 · A list's row menu cannot grey or hide an action, as a tree's can 🔬

The row menu (M4, slice 4) is Open and the rail's actions, declared once with `ListView.action`. Rename is for one
file, so with several selected it should be greyed, as the rail drops its chip. A `TreeView.Entry` carries `hidden`
and `greyed` for exactly this ("Rename on a header, Extract on what is not an archive"), and `MenuActions.offer`
takes both sets, but `ListView` passes `Set.of()` for each, so every row's menu is the whole list whatever is
selected. Vexplore lists Rename always, and the explorer says `Rename is not offered for these` when it is chosen
with several: the person is told after choosing it rather than shown before.

**For the framework:** what a tree's entry has, for a list: a function from the item (and the selection, since a
right click on a selected row is about all of it) to the ids hidden and greyed, consulted as the menu opens. Any
list whose menu holds an action for one item meets it.

## FN-12 · A list row has one mark and no tag 🔬

Fresh files (M5, slice 2) tag a row that landed lately with the word *new* in amber after its name, for twenty
seconds. `ListView.marks` is the one fact a list holds about a row that is not a choice, and the rail's reach already
uses it (a violet wash). There is no second set, and a row's face is built once, from its item, so it cannot redraw
itself when something other than the item changes. Vexplore's `Tags` stands in: each name cell builds its tag hidden
or shown and hands the node over, keyed by path, and a new set shows and hides the tags of the rows realized, pruned
by `realizedItems()`, as ListView's own javadoc asks of anything that hangs state off a row. It is `marks` again,
written outside the list.

**For the framework:** more than one marked set, each with its look, or a per-row tag the list draws: told as data,
the whole set each time, worn by a row realized later, as `marks` is. Two-folder comparison (M6) will want a third
(*newer here*, *only here*), so the shape is a map from a mark's name to its set and its look, rather than a second
field.

**Met again in M6, slice 3 (2026-11-03).** The comparison marks each row with its relation to the other folder, and a
row's relation changes when the *other* folder does, never its own. `Tags` grew into the shape above: `Tags<K>`, each
row wearing at most one key, the name cell building a node per key (six marks, all hidden but one) and the set told
whole. Two instances per list now, *new* and the relation, beside the rail's `marks`. An icon in a cell has no name a
script can read, so each mark's `role` is its words (`newer here`); a per-row tag the list drew would name itself.

## FN-14 · A button is one label, with nothing beside it 🔬 (fixed 2026-11-07)

*Fixed in vexelray-gui: a `Button` is a row holding its words and a badge, `Button.badge(String)`, inside its press, its
focus and its look, and accent while a chip is pressed. The rail's pills carry their counts in it. Nothing outside a
button styles its text now; the overrides that did all set the default except the marks' smaller size, which is gone.*

Orbit's rail (M8, slice 3) draws each *Which files* and *Only if* pill as its words with the count in a badge at its
far end, the badge violet while the pill is picked. `Button` is a text node with the button's role: its label is all
it holds, so a badge cannot sit inside it, and a badge laid beside it would not be part of what is pressed, lit or
focused. Vexplore keeps the count after the words, `Just this file · 1`, as it did before Orbit.

**For the framework:** a button with a trailing slot (`Button.trailing(Node)` or a `badge(String)`), inside its press,
its focus and its pressed look. A chip with a count, a tab with a number, a menu item with a shortcut all meet it.

## FN-15 · A button cannot open a menu 🔬 (fixed 2026-11-07)

*Fixed in vexelray-gui: `Button.menu(Menu)` and `IconButton.menu(Menu)` drop a declared menu from the button on a
left click, Enter or Space. Open with is one now, so its left click names the two programs; the left click that
opened what suits the file is gone.*

Orbit's preview strip (M8, slice 4) has *Open with*: a button whose menu names the program, Rasterware or the
desktop's own. An application declares a menu only as a node's context menu (`Gui.menu`, opened by a right click);
opening one from a left click is `Seams.openMenu`, which is the widget library's and the host's, and which a `@View`
may not import (threading.md, T7.2). Vexplore's *Open with* opens the file with the program that suits it on a left
click (Rasterware for a picture or a video, else the desktop's), and declares the two programs as its context menu, so
a right click names one. A person who does not think to right-click never sees the choice.

**For the framework:** a menu button, declared as a context menu is (`Gui.menuButton(node, menu)`, or
`Button.menu(Menu)`): the menu opens under the button on a left click, Enter or Space, and closes as a context menu
does. Every *Open with*, *Sort by* and overflow button meets it.

## FN-16 · A source's sender cannot be a lambda in a test 🔬 (fixed 2026-11-07)

*Fixed in atchung: `Sender.collecting(topic, list)` adds each send to the list at once, with no bus, tick or inbox.
`CheckSameTest` and `WatchFolderTest` use it. A list rather than a `Consumer`, so a sender stays no callback (T7.2).*

`CheckSame` (M6, slice 4) is a `@Source` that sends a count as it reads, through a `Sender<Checking>` it is built
with. A test that wants to run it on a temporary folder and read the count cannot hand it `counted::add`: `Sender` is
not a functional interface. The test builds a bus, a tick, an inbox subscribed before the run, and
`Sender.outside(bus, topic, Cause.source(...))`, and runs the tick after it, the shape `WatchFolderTest` already
wrote. Six lines to say "collect what it sends".

**For the framework:** a test sender, `Sender.collecting(List<T>)` or `Sender.of(Consumer<T>)`, that delivers to the
consumer at once with no bus. Every source and every job with a progress topic meets it in its test.

## FN-17 · A table's sort is the widget's alone 🔬 (no gap, 2026-11-07)

*Not a gap, and the note below was wrong: `Table` already says its order after every change, header clicks included
(`Table.SORTED`, a column and a direction). Written before reading `Table`. What was missing was Vexplore's: the view
now tells the explorer as `Ordered`, each list keeps its `Order`, the table sorts by `Order`'s own comparators, and
`Intents` reasons over the rows as shown.*

Shift held proposes where a range ends, "down to the end of this run", which is an order: the list's, as the person
sees it. `Table` sorts on a header click and says nothing of it, so the explorer has only the listing's order, and
`Intents` reasons in that. A person who has sorted by size and holds Shift is offered a run they cannot see. Nothing
was worked around: the proposals are in the listing's order, and say so in `Intents`' javadoc.

**For the framework:** the table says its order when it changes (`Table.SORTED`, the column's key and a direction),
so a part that reasons about what is next to what reasons in the order shown.
