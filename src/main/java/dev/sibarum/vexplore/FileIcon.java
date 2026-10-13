package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.IconSource;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.core.style.Theme;
import dev.vexelray.gui.fileicons.FileFamily;
import dev.vexelray.gui.icons.IconView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The file list's type icons: a family's two shapes from {@code vexelray-gui-file-icons}, and the colours they are
 * drawn in. A family is finer than a {@link dev.sibarum.vexplore.files.Kind} — the rail speaks of "documents", the
 * icon tells a spreadsheet from a PDF — and is only ever drawn, never reasoned about, so the two are kept apart.
 *
 * <h2>Two distance fields, two colours</h2>
 * Every family is a sheet (a page, a folder, a window, a box, ...) in the family's hue with its emblem over it in
 * ink, both cells of the file-icon atlas and drawn in the same box. The shapes are baked once, on a 16-unit grid for
 * every size; the colours are this application's, the canvas's palette ({@code vexelray-icons/file-types/families/
 * families.tsv}): a hue and an ink for a dark theme, and for a light one. Nothing is drawn per row: a resize or a
 * zoom draws the same fields at another size, and builds no picture.
 *
 * <p>Source code, scripts and shaders share a hue, as programs and installers do, and are told apart by emblem and
 * sheet.
 */
enum FileIcon {

    TEXT(FileFamily.PLAIN_TEXT, 0xb4c2d6, 0x15111d, 0x455873, 0xffffff),
    DOCUMENT(FileFamily.DOCUMENTS, 0x84b4ff, 0x15111d, 0x0e4baa, 0xffffff),
    PDF(FileFamily.PDF, 0xff8178, 0x15111d, 0xaa180e, 0xffffff),
    SPREADSHEET(FileFamily.SPREADSHEETS, 0x6fd68b, 0x15111d, 0x308748, 0xffffff),
    PRESENTATION(FileFamily.PRESENTATIONS, 0xffa65e, 0x15111d, 0xaa540e, 0xffffff),
    IMAGE(FileFamily.IMAGES, 0xf68fcf, 0x15111d, 0x9e196c, 0xffffff),
    VECTOR(FileFamily.VECTOR_GRAPHICS, 0xdfa0f5, 0x15111d, 0x7a1d9b, 0xffffff),
    AUDIO(FileFamily.AUDIO, 0xa5adff, 0x15111d, 0x0e1caa, 0xffffff),
    VIDEO(FileFamily.VIDEO, 0xc59cff, 0x15111d, 0x4e0eaa, 0xffffff),
    ARCHIVE(FileFamily.ARCHIVES, 0xe2ac6b, 0x15111d, 0x906127, 0xffffff),
    DISK(FileFamily.DISK_IMAGES, 0xa8bed6, 0x15111d, 0x405b78, 0xffffff),
    CODE(FileFamily.SOURCE_CODE, 0x3fd6ea, 0x15111d, 0x1d8c9a, 0xffffff),
    SCRIPT(FileFamily.SCRIPTS, 0x3fd6ea, 0x15111d, 0x1d8c9a, 0xffffff),
    DATA(FileFamily.DATA_AND_CONFIG, 0x49d8b6, 0x15111d, 0x298e76, 0xffffff),
    SHADER(FileFamily.SHADERS, 0x3fd6ea, 0x15111d, 0x1d8c9a, 0xffffff),
    PROGRAM(FileFamily.PROGRAMS, 0x64c6ff, 0x15111d, 0x0e70aa, 0xffffff),
    INSTALLER(FileFamily.INSTALLERS, 0x64c6ff, 0x15111d, 0x0e70aa, 0xffffff),
    LIBRARY(FileFamily.LIBRARIES_AND_BYTECODE, 0x9cc6de, 0x15111d, 0x356683, 0xffffff),
    FONT(FileFamily.FONTS, 0xff9db2, 0x15111d, 0xaa0e2f, 0xffffff),
    MODEL(FileFamily.MODELS_3D, 0xd9be55, 0x15111d, 0x8d792a, 0xffffff),
    EMAIL(FileFamily.EMAIL, 0x7fd0d6, 0x15111d, 0x347e84, 0xffffff),
    CALENDAR(FileFamily.CALENDARS_AND_CONTACTS, 0xf7a2a8, 0x15111d, 0x9d1a23, 0xffffff),
    UNKNOWN(FileFamily.UNKNOWN, 0xaab3bf, 0x15111d, 0x515a67, 0xffffff),
    /** No emblem: the folder sheet alone, in the suite's gold. */
    FOLDER(FileFamily.FOLDERS, 0xe8b54d, 0x15111d, 0x987020, 0xffffff);

    private static final Map<String, FileIcon> BY_EXTENSION = build();

    /** The shapes: the sheet, and the emblem over it. */
    final FileFamily family;
    private final Color darkHue;
    private final Color darkInk;
    private final Color lightHue;
    private final Color lightInk;

    FileIcon(FileFamily family, int darkHue, int darkInk, int lightHue, int lightInk) {
        this.family = family;
        this.darkHue = Color.rgb(darkHue);
        this.darkInk = Color.rgb(darkInk);
        this.lightHue = Color.rgb(lightHue);
        this.lightInk = Color.rgb(lightInk);
    }

    /** The sheet's colour on {@code theme}. */
    Color hue(Theme theme) {
        return dark(theme) ? darkHue : lightHue;
    }

    /** The emblem's colour on {@code theme}. */
    Color ink(Theme theme) {
        return dark(theme) ? darkInk : lightInk;
    }

    /**
     * This icon as a {@code side} square: the sheet, with the emblem floated over it at the same size. Inert to the
     * pointer, so a press lands on the row.
     */
    Node node(Gui g, Length side) {
        Node sheet = IconView.of(g, family.sheet()).size(side).tint(hue(g.theme())).node().hitInert(true);
        IconSource emblem = family.emblem();
        if (emblem != null) {
            sheet.append(IconView.of(g, emblem).size(side).tint(ink(g.theme())).node()
                    .floatAt(Length.ZERO, Length.ZERO).hitInert(true));
        }
        return sheet;
    }

    /** A theme is dark when its ink is light; the palette has a row for each. */
    static boolean dark(Theme theme) {
        Color ink = theme.color(Role.INK);
        return 0.2126f * ink.r() + 0.7152f * ink.g() + 0.0722f * ink.b() > 0.5f;
    }

    /** The icon a list row shows for {@code e}. */
    static FileIcon of(Entry e) {
        return e.folder() ? FOLDER : ofExtension(e.extension());
    }

    /** The icon for a lower-case extension without its dot; {@link #UNKNOWN} when no family claims it. */
    static FileIcon ofExtension(String extension) {
        return BY_EXTENSION.getOrDefault(extension.toLowerCase(Locale.ROOT), UNKNOWN);
    }

    private static Map<String, FileIcon> build() {
        Map<String, FileIcon> m = new HashMap<>();
        put(m, TEXT, "txt log md rtf");
        put(m, DOCUMENT, "docx doc odt epub");
        put(m, PDF, "pdf");
        put(m, SPREADSHEET, "xlsx xls ods csv tsv");
        put(m, PRESENTATION, "pptx ppt odp");
        put(m, IMAGE, "png jpg jpeg gif webp avif heic bmp tif tiff ico psd");
        put(m, VECTOR, "svg eps ai");
        put(m, AUDIO, "mp3 wav flac ogg m4a aac opus mid midi");
        put(m, VIDEO, "mp4 mkv webm mov avi wmv m4v mpg mpeg");
        put(m, ARCHIVE, "zip 7z rar tar gz tgz xz bz2 zst");
        put(m, DISK, "iso img vhd vhdx dmg");
        put(m, CODE, "java kt js ts py c cpp h hpp cs rs go rb php swift lua sql html css");
        put(m, SCRIPT, "sh bat cmd ps1");
        put(m, DATA, "json xml yaml yml toml ini cfg env props properties");
        put(m, SHADER, "glsl hlsl wgsl spv vert frag");
        put(m, PROGRAM, "exe app appimage apk");
        put(m, INSTALLER, "msi msix appx deb rpm pkg");
        put(m, LIBRARY, "dll so dylib jar class wasm");
        put(m, FONT, "ttf otf woff woff2");
        put(m, MODEL, "obj gltf glb stl fbx blend");
        put(m, EMAIL, "eml msg");
        put(m, CALENDAR, "ics vcf");
        return Map.copyOf(m);
    }

    private static void put(Map<String, FileIcon> m, FileIcon icon, String extensions) {
        for (String e : extensions.split(" ")) {
            m.put(e, icon);
        }
    }
}
