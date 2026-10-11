# Providers: anything that implements the contract can be browsed

Status: **idea**, 2026-10-11. Prompted by [a web page as a folder](web-page-as-folder.md), but that page is only
the most unusual case.

Vexplore browses a disk. It could browse anything that offers a set of interfaces: things that contain things,
things that have bytes, columns to sort by, and a menu of what can be done to each. A zip file, a web page, a
Concordance index, a git commit, the trash. Implement the interfaces and you get a virtual filesystem, with the
tree, the list, the Preview Dock, the menu and the rail working as they do for a folder.

## Where it starts from today

Vexplore is closer to this than it looks, and three pieces already have the right shape:

- **`Folders` is the only reader of the file system.** It blocks, it runs off the frame loop, and an unreadable
  folder is an empty one. Those are the rules any provider would have to keep, and only one class keeps them now.
- **`Entry` is read once and drawn many times.** It never goes back to the disk. It is a `Path` plus three facts,
  and the `Path` is what ties it to disk.
- **`Routes` (62f497a, *Opening is one list of routes*)** is the menu half of the contract already: a label, which
  entries a route takes, whether it is ready, and how to open. The first route that takes an entry and is ready
  wins. It is `Predicate<Path>`, and turning that into a node is most of the change.

The Preview Dock decides its tier from the bytes, not from where they came from. A captured response body, a zip
entry or a cookie would preview as an image, text or hex without the dock changing.

## The contract, roughly

A sketch to argue with, not an API:

| Interface | Says | Disk | Zip | Web page | Concordance |
| --- | --- | --- | --- | --- | --- |
| **Node** | An id that survives a reload, a name, folder or leaf | `Path` | archive + entry | URL + role | qualified name |
| **Container** | Its children. Blocking, run off the frame loop, cancellable | `Folders.list` | entries under a prefix | the page's parts | members |
| **Content** | Its bytes, and their length if known | the file | the entry | the response body | the source, from `Source.read` |
| **Columns** | Which columns it has and how each sorts | Name, Size, Modified, Type | the same | Tab order, Area, Fold, … | Kind, Line |
| **Actions** | What its menu offers, as routes | Open, Open in Pix/Vex, Move, Delete | Extract | Open in browser, Copy URL | Go to line |
| **Suggestions** | What the rail may offer, or nothing | today's rail | none | see the page idea | none |
| **Writable** | Plans and undoable operations | `Operations` | maybe later | never | never |

Two rules would hold the contract together:

- **Common semantics are the framework's, and they're defined once.** *Save as file* turns any **Content** into a
  real file, and the menu offers it on every leaf without any provider writing it. That is also how everything
  reaches the suite: *Open in Pix* on a picture inside a zip or a page means saving it to a temporary file and then
  taking the disk route. *Copy reference* (a path, a URL or a qualified name) and *Open in…* are common in the same
  way.
- **Writable is a capability, not an assumption.** Move, Delete and undo exist only where a provider says so. A
  read-only provider is still a complete one.

## Where it lives

The contract doesn't belong in Concordance, which is a static source index (see
[the page idea](web-page-as-folder.md#where-concordance-indexer-fits)). It doesn't belong in Vexplore either:
Vexplore is a framework witness, and its reusable parts go upstream. The contract is suite-level, in
`vex-suite-common` or a module of its own. Then the text editor's file pane or Pix's folder strip could browse the
same providers.

**Two providers that aren't disk should exist before the contract is called finished.** The first is
[the Java references explorer](java-references.md), a Concordance index browsed as folders. Everything under it
already exists, the text editor already uses the same index, and it is a graph rather than a tree of names, so it
tests the contract where it is hardest. A zip is the cheap second one: read-only and local, it already has a
`Source.InArchive` precedent, and it is useful on day one. The web page should come third, and it will stretch the
contract least if the first two have already shaped it.

## Modules with a lifecycle

If providers are separate pieces, the next question is whether they can be added to an installed Vexplore, or
started and stopped while it runs, the way OSGi does on the JVM. The native image is the obstacle: it is a closed
world, and it cannot load classes it did not see at build time. There are four ways to get modules anyway, from
cheapest to hardest:

| Way | Lifecycle | Native image | Cost |
| --- | --- | --- | --- |
| **Compile-time composition.** Each provider is a Maven module, found through `ServiceLoader`, which native-image supports for services known at build time | None at runtime. A different set means a different build | Works today | The installer chooses the set, not the user |
| **Out of process.** Each provider is its own executable speaking a protocol over a pipe or socket, as LSP does and as the automation socket already does | Start and stop are process start and stop. A crash takes down only the provider | Works. Each side is its own image | Serialisation, and latency on a 50,000-row listing (disk stays in process) |
| **Native shared libraries.** A provider built with `native-image --shared`, exporting `@CEntryPoint` functions, loaded through FFM as imagelib already is | Load and start in process. Whether it can be cleanly unloaded is unknown | Each library brings its own isolate and heap. No Java object crosses, only bytes and handles | A C ABI to design and version |
| **A guest JVM.** Espresso (Java on Truffle) inside the image, loading ordinary jars at runtime | Real OSGi-style lifecycle | Possible in principle | Heavy, experimental, and a second JIT in a file browser |

**The decision that matters now is making the contract's boundary data, not objects.** Ids, records and bytes
should cross it. Callbacks into Vexplore's object graph should not. Then the first row can be used today, and the
second or third added later, without any provider changing. Both the out-of-process and the shared-library forms
need a serialisable contract anyway. A contract designed in-process with shared mutable objects would rule them out
for good.

A lifecycle worth having is small: installed, started, stopped. OSGi's resolver, versioned package wiring and
dynamic imports solve a JVM classpath problem that a native image doesn't have.

## Open questions

- **Identity across reloads.** A disk path is stable. A web page's elements are not. What id survives a capture
  being taken again, so a mark or a selection is still there afterwards?
- **One tree or mounts?** Does a zip appear inside its folder in the tree, as a folder (Explorer does this)? Does a
  web page appear under a *Web* root, or only when an address is typed?
- **Cancellation** is still missing from `Lanes.offload()` (framework TODO). A provider whose listing is a network
  fetch needs it more than disk does.
- **Can the rail be generic?** Today's suggestions read `Entry` facts such as extension, size and dates. If
  providers declare typed columns, *Select* and *Condition* could work on any of them. *Action* stays per provider.
