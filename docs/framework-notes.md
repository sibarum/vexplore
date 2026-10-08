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
with that framing, so the retrospective has a candidate rather than a complaint.

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

Written when each workaround was written. The fuller framework-side account, with what each would cost to fix and
whether it blocks v1, is in `vexelray-framework/docs/TODO.md` under *Found by Vexplore*.

### FN-1 · There is no button component 🔬 — **fixed, in `vexelray-gui-widget`**

This was the template's own finding (FN-0) and it was right: every project writes the same four calls. Writing
them a fifth time showed what the four calls leave out, which is the keyboard. A text node with a click handler
cannot be pressed with Enter or Space, and a "disabled" one that only ignores clicks still takes Tab. `Button` now
claims both keys at `ClaimScope.FOCUSED`, leaves the focus order when disabled, and has a toggle form for chips.
`Breadcrumb`, `StatusBar` and `SplitPane` came out of the same pass.

### FN-2 · The listener on a model can hear it out of order, and twice at once 🔬

The template's advice is that `show(Doc)` runs on the committing thread, which is a worker. It is a worker of a
*pool*. `State.commit` swaps the value with a compare-and-set and then delivers to listeners on the committing
thread, so two handlers finishing together deliver as version 6 then 5, or at the same instant. Seen here as a
rail with the wrong chips in it and, before the gate, landmarks that had vanished from a rebuilt panel. Fixed in
`Model.onChange` with a lock and a version check that drops a late, older document; a test drives eight threads and
asserts the listener finishes on the newest. The framework's template `Model` has the unguarded shape, and so does
every application that copied it.

Related: `State.commit` counts a function that returned the *same* value as a new version, so re-announcing an
unchanged selection woke every listener. `Model.select` checks before it commits.

### FN-3 · A wiring keeps its parts to itself, so a capture cannot reach the model 🔬

`--capture` and a test both want to put the application in a known state and look at it. The generated
`VexploreWiring` holds `model`, `ui` and the rest in private fields with no accessor. `Startup` reads four system
properties (`vexplore.folder`, `.select`, `.scope`, `.act`) and applies them after the first listing — a stand-in
that is honest about being one. It has a second use, restoring a session, which is the only reason it is not
purely a test seam.

### FN-4 · `settle` does not wait for work that left the frame loop 🔬

Listing a folder is on `Gui.offload()` and rebuilding the rail is on a handler thread. A script that clicked a row,
`settle`d and photographed got the rail from *before* the click, every time. `settle` says so honestly in its own
javadoc ("exact about the frame loop and the clock and blind to application work in flight"), and the workaround
is small once known: write one status slot **last** in `Ui.show`, give it a landmark, and `await` that landmark for
the text that means "done". `Landmarks.ITEMS` and `Landmarks.SELECTED` are those. It works and it is a convention
every application would have to reinvent; `Lanes` knows its queue depth, so `settle` could know too.

### FN-5 · The layout has no wrapping row 🔬

Chips want to flow: as many as fit, then a new line. `FlexLayout` has `Direction`, `Justify` and `AlignItems` and
no wrap, so the rail counts chips two to a row. It works because the chips are the same size whatever they say,
and it is exactly the kind of number that is right at one zoom and wrong at another.

### FN-6 · The design system names colours the palette's ladders cannot reach 🔬💡

The Vexplore design system (violet, about 258°) gives every surface its own token. `Look` reads the palette's
anchors from those tokens and steps the ladder 0.03 from `bg-0`, so the framework's levels land within 0.01 of
the matching tokens; `LookTest` pins each gap. Violet is both `accent` and `action`, because a chosen chip and the
primary button are both the user's choice. Amber (`anchor`) is provenance only and is not in the palette at all.
The tokens that sit between rungs (`bg-1` as the list, `chrome` darker than the list, `rail`, `card`,
`line-strong`, the two row washes, amber) are `Role`s in `Look`, named after their tokens.

What the palette cannot fix, because a widget names the role:

- **List and tree bodies are `WELL`**, one rung *below* the page (`#08060c`), where the design wants `bg-1`
  (`#15121d`) for the list. Out-of-scope rows are darker than designed.
- **`Button` (`SECONDARY`) borders are `EDGE`** (rung 6). The design's `line-strong` is 3:1 against every surface,
  about rung 13, so chip edges are fainter than designed.
- **A chosen toggle fills with `HIGHLIGHT`**, the accent at 35% alpha. The design wants an opaque `accent-fill`
  with a `text-bright` label ("interactive means opaque").
- **A disabled button keeps a `LINE` border.** The design wants it to blend into its panel, with no border.
- **A row has no edge marker**, so the anchor row's 3px amber `anchor-edge` is not drawn. It has the
  `anchor-wash` fill only.

Each wants a role the widget asks for by purpose (a control's edge, a chosen fill, a list body), so an
application can point it at a token.

### FN-7 · `Table` could not be styled, and marks did not exist 🔬 — **partly fixed**

Fixed: `Table.headers(face, size, ink)`, `Table.onSort(...)`, and `ListView.marked(...)` with
`looks(selected, marked)`. A mark is a fill that is not a selection, and without it the choice was to preview a
suggestion by *selecting* it, which the design rules out. Still missing: a hairline between rows.

### FN-8 · No resize cursor 🔬

Known (`vexelray-gui/docs/plans/todo.md` §4.2). Two widgets now use `GRAB` for a divider: `Table`'s column grip and
`SplitPane`.

### FN-9 · The template ships what the docs retire 🔬

`Capture.java` is in the generated project; `vexelray-gui/docs/reference/automation-cli.md` §2 calls per-application
`--capture` "correct about the chrome and silently wrong about the content" and retires it. Vexplore has no
marched viewport, so its capture is right, and it was the fastest way to see a layout — but `ottermate`'s `shot`,
which is the replacement, worked at once and is what the scene ladder should use. The two documents disagree about
which to reach for, and a new project follows the template.

### FN-10 · Starting a project has no documented route that works today 🔬

The framework README describes what a project looks like and never how to get one. `new vexel-desktop` lives in
`mainframe`, and `mainframe-dist`'s jar on disk fails with `NoClassDefFoundError: Desktop$Apps`. The way that worked
was a twenty-five-line Java program against `vexelray-framework-template`'s `Catalogue`, `Answers`, `Scaffold` and
`Blueprint.Writing` — the same calls the acceptance test's `Support.generate` makes. It should be a `main` in the
template module, or a script beside it.

### FN-11 · The generated `.gitignore` ignores every PNG 🔬

It means to ignore captures. A project with a `docs/` folder of screenshots (this one is built *from* some) loses
them silently. `!docs/**/*.png` is appended here. Better for the template to ignore `capture*.png` and `target/`.

## Milestone 2 (actions and modifier keys)

### FN-12 · The processor earned its keep, and then there was no sanctioned way to say "give this a window later" 🔬💡

Two compile errors, both right. A `@Provides` returning a record is "the smell rather than the exception", and a part
taking `GuiApp` has to be `@MainThread` itself. The second is the design working: the folder dialog needs the window
handle, and a part that took `GuiApp` would put `Ui` in the window's phase, after which a headless capture could not
build the tree. What worked is the shape the framework README shows for a clipboard: a part in the tree's phase
(`Chooser`) and a `@MainThread @Provides AutoCloseable` that binds it to the window when one exists and un-binds on
close. It is the right shape and a little roundabout for something every dialog-using application will need; a
`GuiApp`-phase `@Provides` that returns "a thing to call when the window exists" would say it directly.

### FN-13 · A held modifier cannot be driven from outside 🔬

The design's centre is Shift and Control as intent signals, and `ottermate key` is press-and-release, so the
behaviour is verified by unit tests of the pure engine (`IntentsTest`) and by captures with `-Dvexplore.hold=`. A
`keydown <KEY>` / `keyup <KEY>` pair on the automation socket would let a scene hold Shift, click the second file and
photograph the range the rail proposed. `Gui.modifiers()` itself worked first time and needed nothing.

### FN-14 · `await` on a landmark is how a scene waits, and it only works when something changes text 🔬

`await <landmark> <text>` is what made scenes deterministic, and `click <landmark>` (which takes a name, not only a
ref) is what made them independent of layout. Both needed the application to give the thing a landmark and to make
the state change visible as text: the card's primary button reads "Choose where to move", then "Move 9 files", so a
scene can await the choice. That is a good discipline and an accident of this application; `await <landmark>` with no
text (for "it exists") and a way to wait for the handler and offload lanes to drain (FN-4) would make it the
default rather than a habit.

### FN-15 · Everything else about building actions went to plan

Worth recording what did not hurt: `Gui.offload()` for disk work landing back through the model, the model's
single committer, `Gui.modifiers()`, `Gui.shortcut(Key.Z, ..., Modifier.CONTROL)`, and `Button` all did what their
documentation said. `Plan` as a value the card draws and `Operations.execute` carries out meant the "state the effect
first" rule fell out of the types: the card cannot describe something the action will not do, because it is the same
object.

## Milestone 4 (image previews)

### FN-16 · There is no box that keeps its aspect ratio 🔬

An image wants to be shown whole, at its own aspect, as large as the dock allows. `Length` has no aspect-ratio
unit and a node cannot size one axis from the other, so the picture sits in a well that fills the dock, `Gui.onResize`
reports the well's content box, and the picture is sized in `Length.percent` of it, recomputed on every resize.
Percent was the right unit because it needs neither the density nor a pixel length, which `Length` deliberately
lacks. It works and it is a dozen lines every image viewer, video surface and thumbnail grid will write again. A
`Node.aspect(w, h)` (contain-fit inside whatever the flex gives it) would make it a declaration.

### FN-17 · Two late-bound parts cannot each have their own window binding 🔬

`Textures` needs the window for the same reason `Chooser` does (FN-12), so it got the same shape: a tree-phase part
and a `@MainThread @Provides AutoCloseable` that binds it. The processor then refused the build, rightly, because two
providers of `AutoCloseable` is an ambiguity. The fix was one `windowBinding` that binds both. Fine for two; it means
every future late-bound part edits one shared method rather than declaring its own binding beside itself. This is
FN-12's "give this a window later" provider again, with a second witness.

### FN-18 · A scene cannot photograph an animation 🔬

`settle` errors after 10 s while an animation runs, which its message says and which is right. What it leaves is no
way to wait *a while* and then shoot: the workaround was an `await` on text that never appears, which waits out its
30 s and fails. Seen and not pinned down: after a `settle` that timed out, the shot showed the GIF's first frame both
times, while after an idle 30 s `await` the animation had stepped 300 times. Whether `settle` holds the clock while it
waits is worth checking in `vexelray-gui-automation`; a `wait <ms>` verb would remove the need either way.

### FN-19 · Pixels in, texture out, texture back: the image path worked as documented

`imagelib-wrapper` decoded every format tried, across the native boundary, in the JVM and in tests. `GuiApp.texture`,
`Node.image(image, ImageRegion.cell(...))` and `GuiApp.release` did exactly what their Javadoc said, and the one
thing `release` asks of an application (stop naming the texture first) is what the dock does anyway when it leaves the
image tier. An animation as one sheet and a moving region needed no per-frame upload, as promised.

## Milestone 4 (the image viewer)

### FN-20 · A selection pushed into a list comes back as if the user had made it 🔬

`SelectionModel.set` is documented as the application's way in ("following a change somewhere else in the model")
and it notifies `onChange` exactly as a click does. Vexplore's list forwards `onChange` to the model, and the model
pushes its selection into the list, so every outside change made a round trip. It was harmless until something
changed the selection faster than the trip: the viewer, stepping on a held arrow key, had each step undone by the
list repeating the one before it (20 presses moved 6 to 10 places). The fix is a thread-local guard around the push.
A `set` that does not announce, or an `onChange` that is told whether the user made the change, would make the
loop impossible to write rather than easy to guard.

### FN-21 · A checkerboard needs either a repeating sampler or a few thousand marks 🔬

Every texture is sampled linear-clamp, so a small checker texture cannot tile with a UV region past 1. The
checkerboard is therefore a `Sketch` of fills on a box behind the picture, rebuilt when the box changes size
(about 7,000 squares for a full-window picture). It draws correctly and costs one picture value per resize. A
repeat-addressed sampler as a texture option, or a pattern fill in the draw lane, would make it one quad.

### FN-22 · A window opened by a click is not in `windows` until a frame later 🔬

`click popout` then `windows` lists only the main window: the open is posted and happens at the top of the next
iteration. `settle` between them is enough. A script has no way to say "await window viewer"; with one, opening a
window would be as easy to script as awaiting a landmark.

### FN-23 · A second window was cheap

The viewer is `Popout`'s recipe applied by hand: its own `Gui` on the host's lanes, the application's `Appearance`,
a `TitleBar` composed onto the `WindowSpec` with `commands`, and `GuiApp.window(key, spec)`, which made opening it
twice raise the one that exists. Textures uploaded for the main window drew in the viewer unchanged, the krono
clock animated nodes in either tree, and `GuiApp.release` already looked in every window before closing anything.

## The viewer, redesigned

### FN-24 · A control that is an icon over a word has to be built from parts 🔬

The redesigned viewer makes every control the same square, an icon over a label, so nothing is icon-only. `Button` is
one text node with no icon and no content of its own, and there is no UI icon set: `vexelray-icons` holds application
marks, and the fonts have no star, rotate or cross (`×` and `‹ ›` are there). So `Tile` is `Popout.button`'s recipe
again (a box, `onClick`, `onState` for hover, `focusable`, `cursor`, and Enter and Space claimed while focused), and
`Icons` draws each icon as `Sketch` strokes on a 24-unit grid, redrawn when its box changes size. Every application
with a toolbar will write both. A `Button` that takes an icon node (or any content), and a small stroke icon set in the
draw lane, would make them declarations.

### FN-25 · The renderer draws flat boxes, so a lit design has to be faked or wait 🔬

The redesign is lit: radial glows, gradients, a blurred copy of the picture behind it, coloured shadows, glass with a
specular highlight. The engine draws a rounded box with one solid fill, one uniform border, a drop shadow in the
theme's colour at a fixed offset, and the `lit` bevel; `architecture.md` defers blur and bloom, and lists scale,
rotation and tint as not built. The first pass is therefore flat by choice. What would be needed, cheapest first:
gradient fills (a 1×N texture stretched over a box works today, at one upload per gradient), a shadow or glow colour
per node, image tint and opacity, and a blurred sample of a texture (or a downscaled copy made on the CPU, which also
works today). Rotation for the viewer's Rotate is the same gap.

### FN-26 · Nothing can hide the cursor 🔬

A viewer at rest hides its controls and the pointer. `CursorShape` has no `NONE`, and the native window maps only an
arrow, a text beam and the resize cursors; the cursor is hidden only during a pointer-locked drag. The bar fades and
the arrow stays. A `CursorShape.HIDDEN` on a node would cover it. Related: fading the bar on idle reads pointer moves
off the input bus, since there is no per-node move hook, and the framework's own rule is that nothing appears on hover.
This is a fade after *no* input, which brings the bar back on any input, so it is kept; worth a word in that rule.

### FN-27 · Motion has no "reduce" switch 🔬

The viewer's step now slides and fades its picture and glides its filmstrip, about a quarter of a second each. There
is nowhere to ask whether the person wants less motion: no setting in the framework, and nothing read from the OS's
own (Windows' "Show animations"). Each application that animates will invent its own flag. A `Gui.reducedMotion()`
state, fed from the OS, that `KronoGui.ramp` and `animate` honour by jumping to the end, would cover every one.
