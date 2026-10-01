# Vexplore

I am fed up with the default Windows explorer. I've decided to make my own file system browser/explorer. For this I will need GUI components. These GUI components are highly reusable. Some of it is already built: we have a tree list, and it has drag-and-drop support. We also have concordance-indexer which may be helpful, if not we can extend it.

## Principles

- aka "not enough suggestions"

## Complete Control without any Memorization

Simple filesystem operations should be simple. Common everyday tasks like "move all the videos from this folder to another folder" should be a simple click. Vexplore should have a sql-on-training-wheels command language.

1. Select: Which file(s) to target? A specific file (because you just edited it), a group of files (they were all downloaded today), all files of a given mimetype (because there's mostly only 2 mime types in this folder),  files in subfolders (because it's sprawling). Or: You select the first file, then: All files of the same type? All files download this morning? Etc.
2. Condition: All (Default), If (size, created on, created by, updated on, updated by, name contains, extension is, mime type is, charset is, text contains, allowlist/blocklist, gitignore, etc)
3. Action: What to do with them? Delete (Not permanent)? Delete (Permanent - confirmation)? Archive? Open with? Move (to folder)? Copy (to folder)? Copy (in-place with confirmation)? Rename (following pattern)? Rename (chronological)? Rename (random)? Rename (change extension)? Print (list, sort)? Pipe (to some other program)? Chown? Chmod? Chgroup? Hide/Reveal?
4. Mark: If you're not ready to perform an action yet, simply mark them. Marked filegroups can be referenced later, combined, compared, etc.
5. Compare/Analyze: compares and contrasts two or more folders, filegroups, or subtrees.

If a folder contains a recent file that dropped 5 seconds ago, highlight it, it's most likely important.

There's always something worth highlighting.

## Modifier Keys Are Intent Signals

Control and Shift keep their usual meaning: multi-select still works exactly as people expect. But Vexplore also listens to them. Pressing a modifier is the user announcing what they are about to do, and the suggestion engine answers before the gesture is finished. If we do our job right, they never actually get to the end of it.

- **Shift** means a range: the new items will be adjacent to the selected item. Vexplore proposes where the range likely ends (the end of this type run, this date cluster, this shared name prefix) so one accept replaces the click-and-scroll.
- **Control** (or Command) means one at a time. Each item picked is an example of a rule the user is applying by hand, so Vexplore proposes the rule ("all videos in this folder", "everything from today") and offers to keep it as a Mark.
- With a single file selected and a modifier held, offer the common "finisher" moves for that file.

Accepting a suggestion is the Select step above, made without having to name it. Ignoring a suggestion must cost nothing: the user keeps clicking and gets standard multi-select behavior, with nothing to dismiss. Candidate sets are computed ahead of time per folder so suggestions are ready in the pause between pressing the key and making the second click; a suggestion that arrives late does not appear at all.

## The Suggestion Rail

Nothing Vexplore shows may ever cover or obscure anything else. Suggestions have a dedicated spot, a place in the layout where nothing else is expected, and they only ever appear there. No popups over the file list, no tooltips sitting on top of names, no overlays that shift what is under the cursor, and no suggestion that moves the layout when it arrives or leaves.

Because the rail is always in the same place, it can be glanced at instead of hunted for, and it can stay empty when there is nothing worth saying. A suggestion that would only fit by covering something else is not shown.

## Every File Type Gets a Preview

No file is ever a blank. Each file shows an inline preview in its row, and the selected file shows a fuller preview in the Preview Dock, a dedicated spot below the file list that nothing else uses. The dock can be popped out into its own window, so a preview can stay open while the user keeps working. Opening a preview never covers the list or the rail, and it never moves the rows.

A type with no dedicated renderer falls to the next tier down instead of showing nothing:

1. **Rendered:** images, PDF pages, video frames with a scrub strip, audio waveforms.
2. **Structured:** tables for CSV, a tree for JSON, the file listing for archives, version and signature info for executables.
3. **Text:** the first lines of anything that decodes as text, with its charset.
4. **Bytes:** a hex dump, the strings found in the file, and an entropy bar, so even an unknown file tells you something.

Inline previews are small and cheap: a thumbnail, a sparkline for a numeric column, a page count, a duration, the first line of a note. Previews are read-only and never run anything in the file.

## Two Folders Mode

A second mode puts one folder on the left and a different one on the right. The left is the source ("From") and the right is the target ("To"), and the mode exists so that Compare/Analyze and moving files between two places are one view instead of two windows and a drag.

Vexplore compares the two folders as soon as both are chosen, and every row says how it relates to the other side: only here, only there, identical, or the same name with a different size or date. The suggestion engine works from that comparison. Typical suggestions are moving what exists only on the left, skipping the identical files, and resolving name conflicts in favor of the newer copy. Each suggestion states its direction and counts, and shows its effect before anything happens.

The Suggestion Rail stays in its usual spot, and so does the Preview Dock. Neither pane is ever covered, and choosing a suggestion does not move rows in either pane. A pane can carry its own filter (for example "videos only"), and the comparison respects it.

## Drag and Drop Is the Fallback, Not the Plan

Sure, you may drag and drop if you want. But that's the worst way to use a filesystem! Vexplore doesn't always know what you're trying to do, but it makes common tasks easy, and makes sensible guesses based on context. I don't need to convince you drag and drop is dead: I just need to guess what you're thinking before you finish the action, and stay out of your way until you've made up your mind.

Drag and drop stays fully supported. It is what people fall back on when the suggestions did not have what they wanted, so it has to work well. When a drag does start, the likely destinations (the last folder similar files went to, the sibling folder that holds that type) appear in the Suggestion Rail.
