# A web page as a folder

Status: **idea**, 2026-10-11. Nothing is built. The architecture it would need is the larger idea and has its own
page: [providers.md](providers.md).

You type an address where a folder path would go. Vexplore loads the page in a headless browser and shows what the
page is made of as rows you already know how to use. A resource is a file: open it, preview it, save it. A linked
page is a folder, and opening it loads that page the same way. A script is a folder too, holding everything that
happened because it ran.

The question the view answers is **what is this page made of, where did each part come from, and what does each
visible thing do?** That is what an SEO audit, a privacy check and "why is this page slow" all start from, and
today the answer is spread across five tabs of DevTools.

## What is on the page, as rows

A page is a folder, and these are the folders inside it. Every row has an origin: the element it belongs to, or
the script that caused it.

| Folder | Rows | Each row opens as |
| --- | --- | --- |
| **Elements** | Every visible element that leads somewhere or does something: links, buttons, forms, inputs, images | A link is a folder: the page it leads to. A form is a folder: its fields, and where it submits. An image is a file |
| **Scripts** | Every script, inline or fetched | A folder (below). Its own source is a file inside it |
| **Styles** | Stylesheets, inline and fetched | A file |
| **Requests** | Everything fetched, including what no element shows: tracking pixels, beacons, XHR and fetch calls, fonts | A file holding the response body |
| **Storage** | Cookies, `localStorage`, `sessionStorage`, IndexedDB databases | A cookie is a file. A store is a folder of keys |
| **Errors** | Uncaught exceptions, failed requests, console errors, CSP violations | A file holding the message and the stack |

A tracking pixel is not a file type of its own. It is a request: usually a 1×1 image, usually third-party, usually
with no element anyone can see. That makes it a good default *Condition* rather than a category (see Suggestions
below).

**A script is a folder.** It holds what it caused: every request whose initiator stack contains it, every error
thrown with it on the stack, and every cookie or store key it wrote. Its children are read in the context of the
page it belongs to, so the same analytics script under two pages shows two different sets of children. The browser
records this already. The Chrome DevTools Protocol gives every request an `initiator` that includes a JavaScript
stack, so "where that script is part of the stack trace" is a filter over data the browser already has. It doesn't
need any instrumentation of our own.

## The columns, and the order

The list is the same sortable `Table` the file list uses, so the order is a column choice and doesn't have to be a
mode. These are the columns a page row can have:

| Column | Means |
| --- | --- |
| **Tab order** | Where keyboard focus reaches it: positive `tabindex` first, then document order. Unfocusable elements sort last |
| **Area** | The element's rendered box in CSS pixels. A large link is a strong signal |
| **Fold** | Whether it is visible without scrolling at the capture's viewport size |
| **Does** | *Navigates* (and whether same-origin), *Submits* (and the method), *Runs script*, or nothing |
| **Party** | First-party or third-party, by registrable domain |
| **Requested** | When the request started, relative to navigation start |
| **Executed** | When a script first ran. Debounced, so a script that runs every animation frame reports its first run and a count, not a stream |
| **Size**, **Type** | As in a folder, from the response |

The **score** is a column too, a weighted sum of area, fold, tab order and *Does*. It is shown beside its parts so
that anyone sorting by it can see why a row ranks where it does. A score that hides its parts teaches nothing, and
the weights will be argued over.

**Requested** and **Executed** are what make this useful for SEO. "What has loaded by the time the largest element
is painted" and "which third-party script ran before the first link was visible" are both sorts on those columns.

## Suggestions change entirely, or go away

The design says the Suggestion Rail is a fixed column that may be empty, so this mode can leave it empty with no
change to the layout. Leaving it empty is a fine first version.

If the rail stays, its *Action* chips (Move, Delete, Archive) mean nothing here, but *Select* and *Condition* do:

- *Select*: all third-party requests, everything above the fold, every form that submits off-site.
- *Condition*: looks like a tracking pixel, blocks rendering, was set by a script, failed.
- *Action*: save all as files, copy all URLs, and for a selection of links, open each as a folder.

Which suggestions exist is up to the provider. That is one of the reasons for [providers.md](providers.md).

## Where Concordance Indexer fits

Partly, and less directly than it first looks. Concordance's model is a declaration (`Symbol`), a use of a name
(`Reference`, whose `from` is the declaration it sits inside), and a position, `SourceRef`, which exists so that
"every answer is somewhere a caret can go." A page maps onto that shape closely:

| Concordance | A page |
| --- | --- |
| `Symbol`: something declared, and where | A resource, element or storage key, and the page it belongs to |
| `Reference`: a use, and the declaration it is `from` | A request, and the script that initiated it |
| `usagesOf(symbol)` | "What did this script cause", which is what the script folder lists |
| `namesContaining(fragment)` | Finding a resource by half its URL |
| `Source.InArchive`: bytes that do not live in a file of their own | A captured response body |

So the **shape** is worth sharing, and `Source` is already a small virtual filesystem: a place bytes come from that
is not always a file. But it is the wrong home for the feature. Concordance is a static index of Java source.
`Symbol.Kind` is Java's declaration kinds and `IndexBuilder` is JavaParser, while a page capture is a runtime trace
taken from a browser. Putting a headless browser in Concordance would make it two unrelated tools.

What's reusable is the shape, the queries and the bytes:

1. **The browser capture** is its own module. It produces a page graph: nodes, the edges between them, and bytes.
2. **The browsing contract**, which turns any graph into folders, files, columns and a menu, is the one in
   [providers.md](providers.md). Vexplore consumes it. Disk, a zip, a page and a Concordance index each implement it.
3. **Concordance's index core could become language-neutral.** Its kinds are an open set and its builder sits behind
   an interface. If that happens, the page graph can be held in it and queried with the name matching Concordance
   already has (`Names`: exact, prefix, camel humps, subsequence). That is a change to Concordance worth making
   for its own reasons, and it is not a prerequisite here.
4. **Later, and only then:** a page's scripts can be indexed as source. That would be a JavaScript front end for
   Concordance, so that a script folder could also list the functions the script declares.

The better test of Concordance here runs the other way: **a Concordance index browsed in Vexplore**, with packages
as folders, types inside them, members inside those, and *Open* putting the caret on the line. That is now the
first provider: [the Java references explorer](java-references.md).

## Getting a browser

Bundling Chromium is out of proportion. Every Windows 11 machine has Edge, which speaks the DevTools Protocol:
start `msedge --headless --remote-debugging-port=0`, read the port it chose, and talk to it over a WebSocket. Every
column above comes from domains that protocol already has: `Network` for requests and their initiators and
cookies, `DOM` and `DOMSnapshot` for boxes and visibility, `DOMStorage` and `IndexedDB` for storage, `Runtime` and
`Log` for errors, and `Debugger.scriptParsed` for scripts. WebDriver BiDi is the standard successor and would
reach Firefox too. Neither has been tried from a native image.

## Open questions

- **How far does a linked page load?** Opening a link folder loads a page, which is slow, costs bandwidth and runs
  someone's scripts. Lazy, as the tree already is: load only on open, never on hover, and never more than one level
  without asking. Same-origin by default? What happens on a cycle (A links to B, which links to A)?
- **What does "visible" mean exactly?** A non-empty box, not `display:none` or `visibility:hidden`, not fully
  transparent, not clipped away? Elements hidden until interaction (menus, modals) are what SEO tools argue about.
- **When is a capture finished?** At `load`, at network idle, or after a settle time? A page that polls never goes
  idle. **Executed** needs the same answer.
- **Is it a snapshot or live?** A snapshot can be saved, compared and reopened offline, and that's the more useful
  thing. "Save this page as a folder" would make the virtual tree real.
- **Privacy and safety.** Loading a page runs its scripts, sets its cookies and tells its trackers. The capture
  needs a throwaway browser profile every time, never the user's.
- **What did a script insert?** Attributing DOM nodes to the script that created them needs mutation tracking
  with stacks. The protocol can do it, but it is the expensive part, so it should come last.
