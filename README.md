# Vexplore

A file explorer that suggests instead of asking.

A VexelRay desktop application, being rebuilt on VexelRay 0.2 from the `vexel-desktop` template. The design
([docs/design/README.md](docs/design/README.md)) is the requirement; [docs/rebuild.md](docs/rebuild.md) is the plan and
the order the features come back in, and [docs/shape.md](docs/shape.md) the shape they come back into. **Where it
stands:** M2. Browsing: the tree of places, the breadcrumb and its path field, the list with its columns, sort and
selection, the status line's counts, and Enter to go into a folder or open a file. The Suggestion Rail: select a
file, and Select, Condition and Action chips say what Vexplore would do with the others, the rows it would reach are
marked in the list, and a card states the effect first. M3, actions: Delete (into Vexplore's own trash), Move and
Copy (to a place suggested, or one chosen), Archive and Rename carry out, Ctrl+Z takes any of them back, and Keep
as Mark keeps a set of files to come back to, across runs.

## Run it

From this folder, the parent of the two modules:

```
mvn compile exec:exec
```

`exec:exec` rather than `exec:java`, so native access can be enabled for Panama FFM. The window comes up where you
last left it, at 1440x900 the first time, with the folder you last had open. The first time,
it shows the working directory.

```
mvn compile exec:exec "-Dapp.args=C:/notes"               # open a folder (or files) named on the command line
mvn compile exec:exec -Dapp.args=120                      # 120 frames and quit
mvn compile exec:exec -Dautomation=0                      # a driving socket on a free port (see Taking a screenshot)
mvn test                                                  # the explorer, the jobs, the real tree headless
```

## A native executable

Two builds, made with GraalVM native-image (a GraalVM JDK as `JAVA_HOME`, and Visual Studio with the "Desktop
development with C++" workload):

```
native-build.cmd -Pnative-release package -DskipTests   # vexplore-gui/target/vexplore.exe        what ships: no console
native-build.cmd -Pnative package -DskipTests           # vexplore-gui/target/vexplore-debug.exe  a console: prints the automation port line
```

Both carry the automation sockets, off until asked for ([Automation](#automation)), so ottermate can drive either.

On Linux there is one build, with no console subsystem or icon step to choose, and plain `mvn`:

```
mvn -Pnative-linux package -DskipTests                  # vexplore-gui/target-linux/vexplore
```

`native-build.cmd` is `mvn` inside a Visual Studio developer environment: native-image needs the MSVC `link.exe` and
the icon step needs `rc.exe`, and neither is on `PATH` in a plain shell. Under Git Bash it is worse, since coreutils'
own `link.exe` comes first. The script finds `vcvars64.bat` with `vswhere`, loads it, and runs `mvn` with the
arguments it was given (`./native-build.cmd ...` from Git Bash). Plain `mvn` works too from a developer prompt; from
anywhere else the icon step stops and says to use the script.

This program's own native-image metadata is `vexplore-gui/src/main/resources/META-INF/native-image/.../reachability-metadata.json`,
and it is short because almost nothing is this program's: the libraries register their own, and the processor
registers the icon. If a native run fails on something the JVM run did not (a `Missing...RegistrationError`), run it
on a JVM under `-agentlib:native-image-agent=config-output-dir=...`, do the thing that failed, and add
only the entries that name this program.

## The icon

`vexplore-gui/src/main/rc/vexplore.ico` is the application's mark, and it is one file read three ways: the window and its
taskbar button wear it (`@VexelApp(icon = Vexplore.ICON)`; the pom puts it on the class path), and so does the
title bar the framework draws; the `.exe` links it (`src/main/rc/vexplore.rc`), and so Explorer, a shortcut and a
pinned button show it too. It is the suite icon canvas's Vexplore, whose source is `vexplore.svg` beside it.

A second window — a viewer, a settings window — wears the window mark, on the taskbar and in its title bar, with
nothing to write: declare it in a recipe with `windows.named(...)`, and the framework builds its tree and claims it
wearing `shell.windowMark()`. That mark is this same one unless
a `vexplore-window.ico` sits beside it, which is then found by name and registered for the native image with
the mark. The VexelRay suite's applications ship one: a band of the application's colour over its glyph, so a
second window reads as its own and is still told apart from the main one.

To make it yours, draw a 64x64 SVG and render it at every size Windows asks for, 16 to 256 px, into that `.ico` —
`vex-suite-common`'s `tools/Ico.java` does exactly this, from each size's own rasterisation rather than shrinking the
largest. Replace the file, and the window and the executable change together. An `.ico` or `.png` that is missing is
a compile error; one that does not decode costs the window its mark, logged, and nothing else.

Run from `mvn exec:exec`, the process is `java.exe`, so the window and its taskbar button wear the mark but Windows
groups the button with other Java programs, and pinning it pins Java. The native executable has neither problem.

## Using it

| Keys | Does |
| --- | --- |
| Ctrl+L | the path field, in the breadcrumb's place: type a folder to show it, or a file to show its folder; Enter goes, Esc puts the breadcrumb back |
| Ctrl+Z / Ctrl+Y | take back what the rail's button did, or do it again |
| Ctrl+= / Ctrl+- / Ctrl+0 | zoom |

A click on a breadcrumb segment goes to that folder. `vexplore <path>` shows that folder, or the folder a file is in;
with no path it shows the folder it showed last.

## How it is put together

Two modules under this folder, split where the dependency edge falls, and four kinds of part that never hold each
other: **components** (logic, reached only by messages: the explorer runs on the framework's logic lane), **views**
(presentation, run on the GUI thread in each frame, `@View`), **jobs** (slow work on the offload lane, a request in
and a reply out), and **recipes** (a part built once and handed to whoever takes it). The parts talk by messages, and
every message is in one file. Read these in this order.

**`vexplore-core`, the controller.** It builds against no widget, so its tests need no window.

| File | What belongs in it |
| --- | --- |
| `Messages.java` | **every message the parts send each other**, grouped by who sends it to whom, each record beside its topic. A record crosses as itself, so it holds only immutable values; a list it holds is copied in its constructor. |
| `Explorer.java` | **the controller**: every decision (which folder to show, what a typed path names, what comes back next time) as a declared step on one immutable state, held by a ledger. Each message it hears runs one step. It needs no lock, because only its steps change its state and they run one at a time. |
| `Pane.java` | one list's folder as the explorer knows it: its folder, listing ticket, rows and selection. `State` holds FROM's, and TO's in Two Folders. |
| `ListFolder.java`, `ListPlaces.java`, `FindPath.java`, `OpenFile.java`, `ReadSession.java`, `WriteSession.java` | the jobs: every touch of the disk, on the offload lane. What can go wrong is part of the reply. |
| `Plan.java`, `Done.java`, `Operations.java` | what an action would do, what it did, and carrying it out: nothing overwritten, a delete into Vexplore's own trash, and an undo that is the reverse plan. |
| `RunAction.java` | **the source** that carries out a plan, forward or back, on a thread of its own, with a count and a Stop. |
| `WatchFolder.java` | **the source** that watches the folder shown and says when it changed, once a burst is over, so the list follows the disk; and keeps what landed in the last twenty seconds, for the *new* tag. |
| `PlanAction.java`, `SuggestDestinations.java` | the jobs behind a move or a copy: what going there would do, read from the destination, and the places nearby that already hold the kind. |
| `Work.java` | the explorer's run state: the trash, the run in flight, and the journal of what can be taken back. |
| `Suggestions.java` | **the rail's engine**, a pure function: a folder's rows, the selection and the pick in, the chips to offer and the rows the effect would reach out. The explorer runs it in a step. |
| `Preview.java`, `Previews.java`, `ReadPreview.java` | the Preview Dock's ladder: what one file is (its thumbnail if it is a picture or a video, its first lines if it is text, its bytes if not), and the job that reads it. Nothing in a file is executed. |
| `Kind.java`, `Bytes.java`, `Dates.java` | what a file is, a size and a time in a person's words: no disk and no widget, so they live here. |
| `Core.java` | the list of this module's component and jobs. The processor compiling `-gui` sees only `-gui`'s sources, so this list, named among `VexploreApp`'s starters, is how the wiring hears of them. A new component or job goes on it. |

**`vexplore-gui`, the view and the framework integration.**

| File | What belongs in it |
| --- | --- |
| `Recipes.java` | **what this module builds.** One `@Provides` method per part; `VexploreAppWiring`, which builds them and the components and jobs in order, is generated while it compiles. A part's phase is the latest phase of anything it takes, so there is none to declare. |
| `View.java` | **the view**, a `@View`: run on the GUI thread in each frame, hearing the explorer and the widgets as messages; the explorer's messages shown (`Shown` is the breadcrumb and the list) and the person's gestures told (`Go`, `Navigate`, `Chosen`, `Activated`, `Picked`, `Menu`, `OpenWith`, `Use`, `TwoFolders`, `Swap`, `Back`). It asks `ListPlaces` for the places itself, once the window opens. It decides nothing else. |
| `Intent.java` | what a key chord asks of the view: the message the gesture was declared to send. |
| `Ui.java` | the window, in the design's regions. Holds no state; it shows what the view hands it. |
| `Columns.java` | the list's columns, drawn from a row alone, and the *new* tag after a name. |
| `Sidebar.java` | Orbit's sidebar: the places, flat, the one the folder shown is in filled, and the marks under them. |
| `FolderList.java` | one file list with its header in Two Folders (FROM or TO, and the folder's name, in the accent while used) and its tags; `Ui` builds two. |
| `Tags.java` | the rows tagged *new*: the list's second marked set, told whole, which a list cannot hold (FN-12). |
| `DockPanel.java` | the preview strip under the list, in Orbit's shape: one file's preview, or a note, and *Open with* beside it (a left click opens what suits the file, its menu names a program); every node built once. |
| `HandOff.java` | the job that hands a file to Rasterware where the installer put it, else to the desktop's opener, or to the desktop's opener alone when that was named, and says which. In `-gui` because `Apps` is the framework shell's. |
| `Journaled.java` | an entry of the journal in the window's history: applied, it asks the explorer to take it back or do it again. |
| `RailPanel.java` | the Suggestion Rail in Orbit's words (Which files, Only if, Then): pills, six action tiles and the card with its round run button, every node built once and relabelled. A pill or a tile pressed is said as its row and place. |
| `Motion.java` | the one tempo every widget moves at, on the framework's clock. |

`Vexplore.java` is the entry point and the constants, and nothing else. The application edge — input, the
clipboard, window memory, the clock, the frame loop with its wakes and its pacing, the dialogs, the command line and
the shutdown order — is `vexelray-framework`'s. `Look.java`, `Type.java` and `Landmarks.java` are the three
vocabularies: colour and shape (Orbit's palette, panels and gaps; rebuild.md, M8), size (the handoff's regions in
rem), and the names an automation script is allowed to depend on.

### Phases

A phase is a **correctness** constraint rather than a scheduling detail, and having them as a type is what makes the
constraints structural instead of remembered:

| phase | what exists by then |
| --- | --- |
| `CONFIG` | the settings store and the look — values, before there is a `Gui` to apply them to |
| `MODEL` | what the application knows, before there is anything to draw it with |
| `GUI` | the `Gui` and the clock; the theme applied and the zoom range set, before the first widget |
| `TREE` | the widgets: the first phase a part is handed the `Gui`, so anything taking it lands here. Buildable with no window, which is what lets a test build it with no GPU |
| `WINDOW` | the device and the window handle. Main-thread from here on |
| `ATTACH` | anything that needed the handle: chrome controls, the close gate, the driving socket |

Open `vexplore-gui/target/generated-sources/annotations/.../VexploreAppWiring.java` after a build to see which part landed where.

### Threading, in one paragraph

The GUI loop runs on the main thread, which owns Vulkan, the window and present, and nothing of this application's
runs there but the view, which is presentation and nothing else (threading.md, T7.4). The explorer runs on the
framework's logic lane and the view on the GUI thread, in each frame's widget tick; each handles one message at a
time and owns its state outright, so there is no lock anywhere in the application. The explorer's state is changed
only by its declared steps, which the processor reads: a step that touches the disk, waits or reaches outside itself
does not compile. A widget says what it did as a message, which the view hears in the frame the input arrived, and a
key chord or a menu item is declared with the message it sends, so nothing of the application's runs at the click.
Every touch of the disk is a job on the offload lane, so a slow disk is a slow reply and never a frozen window (the
lanes are named in the framework's `docs/threading.md`).

Two things follow from messages, and both are written where they bite. **One sender's messages to one receiver keep
their order.** **Jobs are in no order at all**: so a step that sends two requests never counts on which answer comes
first. The processor checks every message it can see: a mailbox's payload is deeply immutable, a topic carries one
type, and no cycle of sends can block.

### Motion

The widgets animate only when handed a ramp, and without one they cut — so leaving the clock out costs no error,
only a stiffer window. `Motion` makes the ramps from the framework's clock, and a cue rings the status line when
something was refused.

## The stack

Everything below is installed locally rather than downloaded, so `mvn install` in each of those projects has to have
happened at least once. If the build cannot resolve them, that is why.

| what | why it is here |
| --- | --- |
| `vexelray-framework-api`, `vexelray-gui-core` | all `-core` builds against: the framework's annotations, and the values (a span, a colour, the settings store) and the bus that are safe from any thread |
| `vexelray-framework-shell`, `-automation`, `-processor` | the application edge, the driving socket, and the processor that checks both modules and writes the wiring |
| `vexelray-gui-widget` | the widgets: `Breadcrumb`, `ListView` (the places), `IconButton` (Back, Up, the view switch), `Table`, `TextField`, `SplitPane`, `StatusBar`, `Button` (the rail's chips), `Cues` |
| `vexelray-gui-krono` | the clock: every transition and cue |
| `vexelray-gui-automation` | driving the running app the way a person does — a debugging instrument |
| `tactroller-atchung` | keyboard and pointer input onto an atchung bus (brings `atchung-core`) |
| `tactroller-clipboard` | the OS clipboard, with no input-subsystem coupling |
| `vexelray-os-*`, `tactroller-*` | picked by an OS-activated profile; exactly one activates |

## Automation

Vexplore can be driven and read from outside, by a script, a macro or an agent, through the framework's two loopback
sockets: **remote control** (the keys, clicks and reads a person has) and **diagnostics** (the flight recorder, the
log, the Inspector). Both are in every build, the release one too, and both start off.

- **Switched on by hand**: the plug in the title bar opens the switches, one per socket, each with a port (blank for
  a free one).
- **Switched on at launch**, for an agent that means to: `--automation=0` (both, on a free port),
  `--automation.control=<port>`, `--automation.diagnostics=<port>`, or `VEXELRAY_AUTOMATION=0` in the environment.
  **The diagnostics socket is all the Inspector needs**: a run that opens it at launch records everything the
  Inspector reads, whatever `-Dprobe` says, so there is nothing else to set.
- **Not there at all**: `--automation.available=none` (or `control`, or `diagnostics`), or
  `VEXELRAY_AUTOMATION_AVAILABLE` in the environment. A socket that is not available cannot be switched on by
  anything, and with neither available the plug is gone.

Whatever turned it on, **the plug's lamps say what each socket is doing**, remote control's (red) left of the plug and
diagnostics' (blue) right:

| Lamp | Means |
| --- | --- |
| none | off, and nothing has connected to it this session |
| ring | open, and nothing has connected yet |
| filled ring | open, and something is connected now |
| ring with a grey dot | open; something was connected and has gone |
| grey dot | off, after something had connected to it this session |

The plug's tooltip and the switches' window say the same in words, with the port and how many are connected.

## Taking a screenshot

Screenshots are `ottermate`'s, and only its: it photographs the running window on the application's own device, so
the picture is right about content as well as chrome. There is no `--capture` flag in this project on purpose.

```
ottermate shot out.png --launch mvn compile exec:exec -Dautomation=0
```

Say what the picture is *of* with options, and `ottermate` sets it first and stops if it cannot. Everything before
`--launch` is ottermate's; everything after it is the application's command line (on Windows, `mvn.cmd`):

```
ottermate --zoom 1.5 --dpi 2 --size 1600x900 shot big.png --launch ...   # pixels
ottermate --size 36emx20em shot smallest.png --launch ...                # the minimum, in em
```

The landmarks in `Landmarks.java` are the names a script uses: `await path <folder>` waits for a folder to be
shown, and `click crumbs` reaches the breadcrumb. `ottermate help` lists every verb.

## Logging

The framework configures logging for you, before anything else runs, and the defaults follow what kind of run it is:

| Run | Console | File |
| --- | --- | --- |
| a test | warnings | none |
| from your checkout (`mvn exec:exec`) | info | `vexplore-gui/target/logs/vexplore.log`, debug |
| driven by `ottermate` or `-Dautomation` | debug | `…/vexplore.log`, **trace**, with a probe trace beside it |
| a native image | warnings | `~/.vexplore/logs/vexplore.log`, info |

Write to it with a logger, not `System.out` — the jobs do, as `vexplore.files`:

```java
private static final Log LOG = Log.of("vexplore.files");   // sibarum.probe.Log
LOG.warn("could not save {}", path, exception);               // the exception goes last; its stack reaches the file
```

`--log=debug` (or `off`) changes the level for one launch, and `-Dlog.level.vexplore.files=trace` raises one
logger. Levels, settings and the rules for what belongs at which level are in `atchung/docs/logging.md`.

## The three documents

`docs/framework-notes.md` and `docs/TODO.md` start nearly empty and are meant to be filled in as you go. See the note
at the top of the first one — it is the more important of the two. `docs/retrospective.md` is filled in at the end of
each milestone: the pass over the notes that looks for what they have in common, and the one the framework reads
first.
