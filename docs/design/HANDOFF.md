# Vexplore design handoff

Start with `README.md` in this folder. It holds the principles, and those are the requirements. The mockups only show one way of drawing them.

## What is here

- `README.md`: the design principles (modifier keys as intent signals, the Suggestion Rail, previews for every file type, Two Folders mode, drag and drop as the fallback).
- `screenshots/`: what each screen should look like. Compare your own output against these.
- `mockups/`: the same five screens as plain static HTML and CSS at 1440 x 900. Open them in a browser, or read them for exact values. They are reference drawings with sample data, not code to ship. Fonts load from Google Fonts.

Translate the mockups into whatever UI toolkit the app uses. Don't port the HTML. The existing tree list component and the concordance-indexer are meant to be reused where they fit (see the README).

## The rules that a screenshot cannot show

1. Nothing may cover or obscure anything else. There are no popups, tooltips or overlays over the file list.
2. Suggestions appear only in the Suggestion Rail, a fixed column on the right. The rail may be empty. A suggestion that would only fit by covering something is not shown.
3. Arriving, changing or leaving suggestions must not move rows or shift the layout.
4. Every file gets a preview: inline in its row, and fuller in the Preview Dock. A type with no renderer falls to the next tier, and no file is ever blank.
5. Previews are read-only. Nothing in a previewed file is ever executed.
6. Ignoring a suggestion costs nothing. Ctrl and Shift keep their normal meaning, and they are also read as intent signals.
7. Actions show what they will affect before they run ("9 files, 6.5 GB, nothing has moved yet"), and destructive actions are recoverable by default.

## Layout (reference size 1440 x 900)

| Region | Size |
| --- | --- |
| Top bar (breadcrumb) | 48 px tall |
| Status bar | 30 px tall |
| Tree | 250 px wide, rows 30 px |
| File list | flexible, header 32 px, rows 34 px |
| Suggestion Rail | 400 px wide, 20 px padding |
| Preview Dock | fills the space below the list (screen 03), header 44 px |
| Two Folders mode | two panes of 520 px, header 72 px, rail unchanged at 400 px |

## Colors

| Use | Value |
| --- | --- |
| App background | `#16171a` |
| Tree, top bar, status bar, dock | `#1a1b1f` |
| Suggestion Rail | `#1c1e22` |
| Cards and buttons | `#23262b` |
| Dividers | `#2f3339` |
| Control borders | `#3a3f46` |
| Text | `#e8e6e1` |
| Text, list rows | `#d3d5d9` |
| Text, secondary | `#b7bac0` |
| Text, dim (labels, dates) | `#9a9ea6` |
| Selection background | `#223338` (the anchor row `#2a4247`) |
| Selection accent, active chips | `#7cc4c4` on `#24393d` |
| Tree active row | `#252a30` |
| Suggestion, fresh and caution accent | `#e0a458` |
| Amber row and card background | `#2a2418` |
| Amber text on dark | `#f3d9a8` |

Selection is teal. Amber means "Vexplore is suggesting this" or "this is new or needs a decision." Don't use them interchangeably.

## Type

Instrument Sans for text (14 px body, 13 px in the rail, 12 px for secondary text). IBM Plex Mono for paths, sizes in labels, status tags, hex and anything tabular (11 to 13 px, uppercase with letter spacing for column headers).

## Screens

01. Select, Condition, Action. One selected file leads to suggested selections, conditions and actions in the rail, and the 9 matching rows highlight live in the list. The summary card at the bottom of the rail shows the effect first, with a primary action and "Keep as Mark".
02. Fresh file and drag start. A file that landed 5 seconds ago is highlighted and tagged "new". While a file is being dragged, the rail lists likely destinations with reasons, and the same numbers are marked on the folders in the tree. No drag ghost is drawn over the list. A "Carrying" card at the top of the rail stands in for it.
03. Inline previews and the Preview Dock. Each row has a small preview (thumbnail, sparkline, waveform, first line, page count). The dock under the list shows the selected file in full and has a Pop out button.
04. Preview tiers by file type. Tier 1 rendered (image, PDF, video), tier 2 structured (archive listing, JSON tree, CSV table), tier 4 bytes (hex, entropy, strings). Tier 3 is plain text and is not drawn.
05. Two folders. Source on the left, target on the right. Every row carries a status (only here, only there, identical, newer here, older here). Suggestions come from the comparison, and the summary card gives direction and counts.

## Open questions

- How "identical" is decided in Two Folders mode. Size and date are cheap. Content hashing is exact but slow for multi-gigabyte videos.
- Which renderers exist first, since not every tier 1 and tier 2 type needs to ship at once. The tier 4 bytes view is the guaranteed fallback and should come early.
- Executable previews (version and signature info) need care so nothing in the file is ever run.
- How the suggestion engine is computed ahead of time per folder, so a suggestion is ready in the pause after a modifier key is pressed.
- The sample data in the mockups (file names, sizes, folder paths, counts) is invented.
