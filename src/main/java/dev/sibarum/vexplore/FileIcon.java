package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.fileicons.FileFamily;
import dev.vexelray.gui.fileicons.FileIconView;

/**
 * The file list's type icons. Shapes, colours and the extensions each family claims are all
 * {@code vexelray-gui-file-icons}', generated from the design canvas ({@code vexelray-icons/file-types/families/
 * families.tsv}); the one rule that is this application's is that a folder is a folder whatever it is called.
 *
 * <p>A family is finer than a {@link dev.sibarum.vexplore.files.Kind} — the rail speaks of "documents", the icon
 * tells a spreadsheet from a PDF — and is only ever drawn, never reasoned about, so the two are kept apart.
 */
final class FileIcon {

    private FileIcon() {
    }

    /** The family a list row shows for {@code e}. */
    static FileFamily of(Entry e) {
        return e.folder() ? FileFamily.FOLDERS : FileFamily.ofExtension(e.extension());
    }

    /** {@code e}'s icon as a {@code side} square, inert to the pointer so a press lands on the row. */
    static Node node(Gui g, Entry e, Length side) {
        return FileIconView.node(g, of(e), side);
    }
}
