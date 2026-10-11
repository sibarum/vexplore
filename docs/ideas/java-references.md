# The first provider: a Java references explorer

Status: **idea**, 2026-10-11. This is the first provider under the [providers](providers.md) contract, chosen
because everything it needs already exists. [Concordance Indexer](../../../concordance-indexer) builds the index,
and the text editor (Vex) already reads that same index.

Open a Maven project in Vexplore and it can be browsed as code instead of as files: modules, packages, types,
members. A declaration is a folder holding everywhere it is used. A use is a file that opens in Vex at its line.
The Suggestion Rail stops proposing where to move things. Instead it proposes **what to filter out**, because what
a references list has too much of is rows.

## Why this one first

- **Nothing new has to be built underneath it.** `ProjectIndex` reads a Maven project and its library sources
  jars, keeps itself up to date by reparsing only what changed, and saves to disk. `Index` already answers the
  questions a folder view needs: `types`, `declaredIn`, `usagesOf(symbol)`, `implementationsOf`,
  `implementationsOfMethod` and `namesContaining`.
- **It is not a tree of names, which is the point.** Disk and zip are both paths. An index is a graph: a method is
  inside a type, and also used from forty places in other types. If the contract survives a graph, it will survive
  a web page.
- **The suite already shares it.** Vex holds a Concordance index of the open folder for go-to-declaration
  (`text-editor-vexel-demo`, `ProjectIndex.java` and `Outline.java`). The two apps would read the same project
  through the same library, so they agree about what a symbol is and where it is.

## What it looks like

A project is a folder, and its children are what the project is made of:

```
vexelray-gui/                      the project
  Modules/
    vexelray-gui-core/
      dev.vexelray.gui/            a package
        Gui                        a type: a folder
          offload()                a method: a folder
            Used by (31)/          every use, one row each: Actions.java:212  CALL
            Overridden by (0)/
          …
  Libraries/                       sources jars: only what each one exports
    javaparser-core-3.28.2/
  Search/                          namesContaining, as a folder you type into
```

The folders of a declaration come straight from index queries:

| Node | Children | From |
| --- | --- | --- |
| Type | Its members, then *Used by*, *Implemented by*, *Extends* | `declaredIn`, `usagesOf(symbol)`, `implementationsOf` |
| Method | *Used by*, *Overridden by* | `usagesOf(symbol)` (matched by name and arity), `implementationsOfMethod` |
| Field | *Used by* | `usagesOf(symbol)` |
| *Used by* | One row per `Reference`, in source order | already sorted by file, line and column |

A **reference row is a leaf**. *Open* starts Vex at that line, and the Preview Dock shows the lines around it with
the use marked. A **declaration** opens at its own line the same way.

The list's columns:

| Column | From |
| --- | --- |
| **Name** | `Symbol.name`, or for a use, the declaration it sits in (`Reference.from`) |
| **Kind** | `Symbol.Kind`, or `Reference.Kind`: call, construct, type use, read, extends, implements |
| **Where** | `path:line`, which is `SourceRef.toString()`, a form both an editor and a shell read |
| **Module** | Which Maven module the source belongs to, or the library jar |
| **Uses** | How many references a declaration has. Sorting by it finds what's dead and what everything leans on |
| **Test** | Whether the source is under `src/test` |

## Suggestions are filters

Concordance matches references **by name, not by resolution**, and says so on purpose. A call to `close()` is
recorded as a use of `close` with no arguments, whatever it is called on. "Used by" is an over-approximation: it
finds everything real and some things besides. That's the right default for an index (an index that silently
drops a usage is worse than one that offers an extra). It does mean the user's next job is always to take the
extras away, and that's what the rail can do.

So the three columns of the rail mean something new here:

- **Select** reaches rows: every use in this module, every use in tests, every call from one type.
- **Condition** narrows by a rule: **only in files that also name the owner type**, only calls (not reads), only
  outside this type, not in generated sources.
- **Action** does something to what is left: hide, keep only, copy as a list, open each in Vex.

Each chip states its effect first, as the design asks for every action: *Hide 14 uses in tests — 17 left*. A
filter is a view, so undoing one costs nothing and it never touches a file. Stacked filters show in the breadcrumb,
each with its own ×, so the list always says why it is as short as it is.

**"Only in files that also name the owner type"** is the most useful one, and the index can compute it. A call to
`close()` from a file that never mentions `ZipFile` is very unlikely to be `ZipFile.close()`. `usagesOf(ownerType)`
gives the files that name it, and intersecting gives the narrowed list. It is a heuristic, so the chip says it is a
guess (*likely `ZipFile`*), and the rows it hides are a click away. When Concordance gains a resolver, `Reference`
gets a qualified target and this filter becomes exact without the UI changing.

Other filters worth having from day one, all computed from what the index already holds:

| Filter | Removes | How |
| --- | --- | --- |
| Not in tests | Uses under `src/test` | The source's path |
| Only project, not libraries | Uses inside sources jars | `Source.InArchive` against `Source.OnDisk` |
| Outside this type | A class's calls to its own methods | `Reference.from` starts with the owner |
| Only calls / only type uses | The other `Reference.Kind`s | The kind |
| Wrong arity, shown | Uses of the same name with a different number of arguments, normally hidden | `usagesOf(name)` minus `usagesOf(symbol)` |

The last one runs the other way. It *adds* rows, for when an overload or varargs call was missed by the arity
match.

Inside a git repository, the history filters join these: *in files with unstaged changes*, *touched by the last
commit*, *changed on this branch*. They come from intersecting each row's `SourceRef` with git's status and diff,
so they need no change to Concordance. See [git.md](git.md#concordance-history-aware), which also covers making
Concordance itself history-aware.

## Synergy with Vex

The two apps already start each other as separate processes (*Open in Vex*, and Vex's *Open in Vexplore*). This
provider gives that link a second meaning:

- **Vexplore → Vex: open at a line.** Every reference row opens in Vex at the use. Vex can't be told a line from
  the command line yet (`TextEditor.main` passes arguments straight to the framework). `text-editor <file>:<line>`,
  or a `--line` option, is the one change Vex needs. It should be agreed between the two apps the way `pix <file>`
  was.
- **Vex → Vexplore: find usages as a folder.** Vex's go-to-declaration already finds a symbol under the caret.
  *Find usages in Vexplore* would start `vexplore --symbol <qualified> <project>` and open straight on that
  declaration's *Used by*, with the filters. A references list is a list, and Vexplore is the suite's list
  application, so Vex doesn't have to grow one.
- **One index on disk, not two.** Concordance's `ProjectIndex` saves and loads (`save(cacheFile)`, `load(cacheFile,
  root)`) and brings itself up to date by stat and hash. Vex's wrapper currently rebuilds each time. If both apps
  load and save the same cache file, whichever opens the project second starts warm. Where that file lives and how
  two processes share it (write beside it, then atomically replace) is the agreement to make, probably in
  Concordance itself so neither app owns it.

## What it needs

From the contract ([providers.md](providers.md)):

- **Content with a position.** A use is a place in a file, not a file. The Preview Dock's text tier needs to scroll
  to a line and mark it. That is a small addition to the contract and is needed for code generally, not just Java.
- **Graph-shaped containers.** The same declaration is reached from its package and from every *Used by* that
  mentions it. Node ids are qualified names (`a.b.C#m(int)`), so a mark or selection on one is the same mark
  wherever it appears.
- **Provider-defined suggestions.** The rail's chips come from the provider, and each provider's chips are typed
  over its own columns. The filters above are the first suggestions that aren't about files.

From Vexplore:

- A way in. Either a route on a folder that contains a `pom.xml` (*Browse as Java*, beside *Open*), or a mount, so
  the project shows both ways. Starting with the route is simpler.
- Dependencies on `concordance-index` and `concordance-project`, built in at compile time: the first row of the
  modules table in [providers.md](providers.md#modules-with-a-lifecycle). Vex already carries the same libraries,
  so its native-image configuration for them is the place to start. Whether it is complete for every path Vexplore
  would take is unchecked.

From Concordance, eventually and not first:

- **Where a declaration ends.** The index records where each one starts (Vex's `Outline` notes this). A preview
  that shows a whole method needs the end too.
- **A resolver**, which turns the "likely" filter into an exact one.

## Open questions

- **Gradle and plain folders.** `concordance-project` reads Maven. Is a folder of `.java` files without a build
  file indexable with no libraries at all, which is better than nothing?
- **How big a reactor is comfortable?** The JavaParser evaluation extrapolates a cold full parse of 100k lines to
  tens of seconds. With a shared warm cache that happens once, but the first open needs progress shown, and
  cancellation (still missing from `Lanes.offload()`).
- **Do filters persist?** "Never show me tests" per project is a setting. A filter for one search is not. Which
  ones become sticky, and where does that live?
- **Does the file view mark what the code view knows?** A `.java` row on disk could show its *Uses* count, or show
  that nothing references it. That is the disk provider borrowing a column from the Java one, and it might be the
  first test of providers composing.
