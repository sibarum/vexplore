# Git, as a primary feature

Status: **started**, 2026-10-11. The first half of *Status in the file view* is built: the GIT column and the branch
in the status line, from `dev.sibarum.suite.git` in `vex-suite-common`. What is left is in [TODO.md](../TODO.md),
*Git, and browsing more than the disk*.

Git is not a plugin. In a repository, whether a file is changed, staged, untracked or ignored is as much a fact
about the row as its size and date, and a file explorer that hides it is hiding the most important thing about
half the files a developer touches. So git belongs in the disk view itself, in the columns, the rail and the menu.
Separately, **history** is browsable, and that half is a [provider](providers.md): commits and branches as folders.

It has two halves:

1. **Status in the file view.** Always on inside a repository, and invisible outside one.
2. **History as folders.** A commit, a branch or a stash opened the way a folder is.

And one place where the two meet the [Java references explorer](java-references.md): Concordance, made
history-aware.

## Status in the file view

A **Git column** holds the row's state: *modified*, *staged*, *staged and modified*, *added*, *untracked*,
*conflicted* or *ignored*. Ignored rows are dimmed, not hidden, because hiding is the user's choice and the rail
offers it. A **folder rolls up** what is inside it (*3 changed, 1 untracked*), so a change deep in the tree is
visible from the top. The tree marks those folders too.

The breadcrumb gains the branch, plus *ahead 2 / behind 1* when there is an upstream. That's all it shows. It is a
place indicator, not a dashboard.

The Preview Dock gets a **diff tier** for a changed text file: the working copy against the index, or the index
against `HEAD`, whichever the row's state makes relevant. The bytes are still the file. The diff is an annotation
on the text tier, not a replacement for it.

## The rail, inside a repository

These are exactly the "Select / Condition / Action" shapes the rail already has. Only the facts are new.

**Select**

- Unstaged changes · Staged changes · Untracked · Conflicted
- Changed in the last commit · Changed in the last *n* commits · Changed today
- Changed on this branch (everything since it left `main`)

**Condition**

- Tracked / untracked / ignored. The design already lists *gitignore* as a condition.
- Changed since a chosen commit, or by a chosen author
- Not ignored (the condition that makes every other selection skip `target/` and friends)

**Action**

- Stage · Unstage
- **Discard changes**, which is recoverable here (below), not as git does it
- Add to `.gitignore`, with the plan saying which pattern and which file
- Open diff in Vex · Show history of this file (opens the history provider filtered to it)

**Recoverable by default still holds, and git makes it harder.** `git restore` and `git checkout -- <file>` throw
away uncommitted work with no undo. Vexplore's *Discard* first copies the working file to Vexplore's own trash, as
Delete does, and then restores, so Ctrl+Z puts it back. The same goes for anything that rewrites the working tree.
The plan states what will change before it runs, and nothing irreversible is offered as a chip.

**Moving and deleting tracked files** stay ordinary file operations. Git notices renames by content, so a move
needs no `git mv`, and a delete still goes to Vexplore's trash. The plan should say *this file is tracked; the
deletion will show as a change*, so nobody is surprised.

**Out of scope, on purpose:** commit, push, pull, merge, rebase. Vexplore is where you find and sort the files; it
isn't a git client. Staging is the edge of it, because staging is itself a selection. A commit message box would
be the first step into somebody else's application. If it is ever wanted, it belongs in its own suite app, which
Vexplore starts the way it starts Vex.

## History as folders

The history provider shows what was. It's read-only, so it implements the contract without *Writable*:

```
vexplore/  (repository)
  Branches/
    main/                       its tree at the tip, as folders
  Commits/                      newest first; columns: when, author, message, files changed
    62f497a Opening is one list of routes/
      Changed (4)/              only what this commit touched, with A/M/D/R
      Tree/                     the whole tree at this commit
  Stashes/
  Tags/
```

A file in a commit is a leaf whose bytes are `git show <rev>:<path>`, so the Preview Dock and *Save as file* work
on it unchanged. *Open in Vex* saves it to a temporary read-only file. *Restore this version* is the one action
that writes, and it goes through a plan and Vexplore's trash like *Discard*.

**Two Folders mode** (in the design, not yet built) gets a new kind of side: a commit. Working tree against
`HEAD`, or `main` against this branch, is a comparison the design already describes: only here, only there,
identical, or different. Only the source of one side is new.

## How Vexplore talks to git

Recommended: **the `git` executable**, run as a process, the way Vexplore already starts Pix and Vex.

| Way | For | Against |
| --- | --- | --- |
| **`git` CLI** (`status --porcelain=v2 -z`, `log -z --name-status`, `show`, `diff`) | It is git: the user's config, hooks, `fsmonitor`, sparse checkouts and LFS all behave as they would at a prompt. Machine-readable formats are stable and documented. No native-image risk | A process per query. Not installed means no feature, which is the same rule as Pix: absent, and the rest of Vexplore unchanged |
| **JGit** | In process, no dependency on PATH | A second implementation that lags git on new formats, reflection-heavy so native-image is an unknown, and it ignores some of the user's config |
| **Reading `.git` directly** | Fastest for status | Index format, packfiles, worktrees, submodules, and it would still be wrong in some corner |

Every call runs off the frame loop and lands through the model, as listings do now. Status is re-read when the
folder changes. That needs the `WatchService` the *fresh file* item in [TODO.md](../TODO.md) already needs, so the
two can share one watcher. On a very large repository, status is the slow call. Showing the listing first and the
Git column a moment later keeps the "nothing moves when a suggestion arrives" rule, as long as the column's width
is fixed.

### Watching it happen, in MainFrame

An option, off by default: **a MainFrame window that shows every git command Vexplore runs, as it runs.** The
input controls are disabled, and Vexplore drives the window. It is the honest version of "this is what I did": the
exact command line, its output and its exit code, in the suite's own terminal. It also turns Vexplore into a way of
learning git. Pressing *Stage* shows you `git add -- 'src/Foo.java'`.

**Vexplore runs git, and MainFrame only watches.** It would be tempting to have MainFrame own the process and run
git in its pseudoconsole, with Vexplore typing into it. That is the wrong way round:

- Vexplore has to parse what git prints. Through a terminal, output arrives with VT escapes and line wrapping,
  stdout and stderr merged, and NUL-separated formats (`-z`) are not safe. Pipes give exact bytes.
- The feature would then depend on MainFrame being installed and open. The suite's rule (Pix, Vex) is that a
  missing application removes an option, never a feature.
- Closing the console must not kill an operation halfway through.

So Vexplore starts `git` with pipes, as it would anyway, and **mirrors** each call to the console: the command line,
then the output, then the exit code. Vexplore writes that as VT text, so it can draw the command in the accent,
dim the machine-readable queries, and draw a failure in red. Long porcelain output from a background status
refresh can be shortened to one line (`git status --porcelain=v2 -z → 214 entries, 12 changed`). Commands that
change something are always shown in full.

**What MainFrame needs: a tab whose source is a feed, not a shell.** MainFrame already keeps the screen
(`term/Terminal`, which knows nothing about ptys) apart from the shell (`pty/ConPty`). A feed tab puts a byte
stream where the pseudoconsole was:

- `mainframe --feed=<name>` opens a tab that reads a named pipe (`\\.\pipe\vexplore-git-<pid>`) and renders it.
  Vexplore finds MainFrame through its install record, as Open MainFrame Here already does.
- **Input disabled**, not just ignored: no cursor, no input line, and a banner across the top saying *Driven by
  Vexplore — read only*. Selection, copy, scrollback and the wheel still work, since reading is the point.
- One tab per repository, named after it. A feed that ends (Vexplore quit) says so and stays open to be read.
- An *Open a shell here* button in the banner opens an ordinary tab in that repository. That is the existing
  `--dir` path, and it's the way out of read-only when you want to type.

**Vexplore keeps the transcript, not MainFrame.** A ring of the last few hundred calls stays in memory, so opening
the console halfway through a session replays what already happened instead of starting blank. Turning the option
on is a toggle beside the branch in the breadcrumb, and in settings for the next start.

**Or no MainFrame at all.** MainFrame's terminal emulation is planned to become a library of its own
([`mainframe/docs/terminal-library.md`](../../../mainframe/docs/terminal-library.md)): a VT screen with no GUI, a
pty, and a vexelray view that reads from any `Channel`. With that, Vexplore can open its own console window, a
read-only view over a feed it writes to in process, without a pipe or a second process, and without needing
MainFrame installed. That is the better default. MainFrame's feed tab remains for sending the transcript to a
terminal you already have open.

This is not git-specific. Anything in the suite that runs an external command could feed a console the same way:
Vexplore's own shell opens, a Concordance index build, Vex running a build. The feed tab belongs in MainFrame as a
general feature. Git is only its first user.

**Where it lives:** a small `git` module in `vex-suite-common`, holding status, log, show and diff as records, with
no UI. Vexplore uses it first. Vex would use it next, for a changed-lines gutter and to turn its *git* chip into a
branch name. Concordance could use it for what follows.

## Concordance, history-aware

The idea: "show the symbols with unstaged changes" or "show the uses touched by the last commit" as filters in the
references explorer. It comes in three steps, and the first step needs no change to Concordance at all.

**1. A join, in Vexplore.** Every Concordance answer carries a `SourceRef`, a file and a line. Git's diff gives
files and line ranges. Intersecting them gives:

- *Declarations in files with unstaged changes* (file-level, from `status`)
- *Uses touched by the last commit*, *declarations changed on this branch* (line-level, from the diff hunks: a
  `SourceRef` whose line falls inside a changed hunk)

Those become filter chips in the [references explorer](java-references.md#suggestions-are-filters), beside *Not in
tests*. Line-level is approximate in one known way. Concordance records where a declaration *starts*, not where it
ends, so "this method changed" means "a hunk starts inside this method's first line" until Concordance knows the
end. That is the same gap the references page already lists.

**2. An index per revision, sharing what didn't change.** This is the change to Concordance, and the design already
leans toward it:

- **`Source` gains `AtRevision(repo, rev, path)`**, bytes from `git show`. It's a third kind of place that bytes
  live, beside `OnDisk` and `InArchive`, and the reason `Source` is a sealed interface and not a `Path`.
- **Parsed pieces keyed by content.** `ProjectIndex` already stamps each source with a digest and reparses only
  when content changes, and an `Index` is just pieces concatenated. If a piece is keyed by its **git blob id**
  (which *is* a content hash, and git already knows it, so nothing needs reading to find it), then indexing a
  second revision reparses only the blobs that differ. The index of `main` and of this branch share every
  unchanged file.
- **Then a diff of two indexes is a set difference.** It gives declarations added, removed, or moved to another
  type; *new uses of `X` since `main`*; and *what this branch changed in the public API*. Those are questions about
  symbols, which a line diff can't answer.

**3. A symbol's history.** When a method appeared, who last changed it, and following it across a rename or a move
to another file. That is the hard one. Identity across revisions, by qualified name or by content similarity, is
the same problem `git log --follow` solves badly for files. It is worth keeping in view and not worth designing
yet.

Step 1 is enough for the two suggestions that prompted this page, and it can ship with the references explorer.
Step 2 is a real Concordance feature that Vex would use as much as Vexplore: go to declaration *as of `main`*,
and find usages *added on this branch*.

## Open questions

- **Submodules and nested repositories.** Is a submodule's status its own, and does the parent roll it up?
- **Worktrees.** Several checkouts share one repository. Does the history provider show all of them?
- **LFS and large files.** `git show` of an LFS pointer gives the pointer, not the file. Should the dock say so,
  or fetch?
- **Which diff does the dock show** for a file that is both staged and modified? Both, one above the other, or
  whichever the user last chose?
- **Is cancel the one input the console takes?** A read-only console where Esc (MainFrame's interrupt key) stops
  the running command would send that back to Vexplore, which owns the process. Useful for a slow `log` on a huge
  repository. It needs a channel back, so the feed is no longer one-way.
- **Should the console open by itself on a failure?** Off by default is right, but a failed write (stage refused,
  a lock file left behind) is the moment someone wants to see what git said.
- **How much does Discard keep?** Every discarded version, in Vexplore's trash, for how long? This is the same
  question as *the trash is never emptied* in the TODO.
