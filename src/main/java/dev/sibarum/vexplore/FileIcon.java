package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.vexelray.canvas.Color;
import dev.vexelray.gui.draw.Sketch;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The file list's type icons: one glyph and one hue per family, from the suite canvas's <i>File types</i> page. A
 * family is finer than a {@link dev.sibarum.vexplore.files.Kind} — the rail speaks of "documents", the icon tells a
 * spreadsheet from a PDF — and is only ever drawn, never reasoned about, so the two are kept apart.
 *
 * <h2>Drawn for 16 pixels, not shrunk from 64</h2>
 * The canvas draws each icon on a 64-unit page with a two-unit outline and a labelled band. Scaled to a list row
 * that outline is half a pixel, the band's word is unreadable and most glyphs smear into a blob. So this is the same
 * set redrawn on a 16-unit grid: a page with a one-unit edge and a stepped folded corner, a two-unit band in the
 * family's hue with no word on it (the name beside it says the type), and each glyph cut down to what survives at
 * that size: a 3 by 3 grid for a spreadsheet, three bars for audio, one "A" for a font. Nearly every mark is a whole
 * unit on the grid, so at 100% zoom the icon lands on device pixels and stays sharp.
 *
 * <p>Programs and Installers share a hue on purpose (both are something you run) and are told apart by glyph: a
 * window, and an arrow down onto a tray.
 */
enum FileIcon {

    TEXT(0x191c20, 0x4e565f, 0x373e45, 0xafc0d2, 0xafc0d2, 0) {
        @Override
        void glyph(Pen p) {
            p.px(4, 5, 8, 1, hue);
            p.px(4, 7, 8, 1, hue);
            p.px(4, 9, 8, 1, hue);
            p.px(4, 11, 5, 1, hue);
        }
    },
    DOCUMENT(0x111c2c, 0x3c567c, 0x293e5c, 0x96c0fe, 0xdfecff, 0) {
        @Override
        void glyph(Pen p) {
            p.px(4, 4, 8, 2, hue);
            p.px(4, 7, 8, 1, tint);
            p.px(4, 9, 8, 1, tint);
            p.px(4, 11, 5, 1, tint);
        }
    },
    PDF(0x2a1513, 0x784541, 0x59302d, 0xffa098, 0xfee4e1, 0) {
        @Override
        void glyph(Pen p) {
            p.px(4, 4, 3, 3, tint);
            p.px(8, 4, 4, 1, hue);
            p.px(8, 6, 4, 1, hue);
            p.px(4, 9, 8, 1, hue);
            p.px(4, 11, 6, 1, hue);
        }
    },
    SPREADSHEET(0x112011, 0x3b603d, 0x284529, 0x86d489, 0xd5f6d5, 0x0a160a) {
        @Override
        void glyph(Pen p) {
            for (int c = 0; c < 3; c++) {
                p.px(4 + 3 * c, 4, 2, 2, tint);
                p.px(4 + 3 * c, 7, 2, 2, hue);
                p.px(4 + 3 * c, 10, 2, 2, hue);
            }
        }
    },
    PRESENTATION(0x28170b, 0x734a2e, 0x55341c, 0xfea668, 0xffe6d6, 0x1d0f06) {
        @Override
        void glyph(Pen p) {
            p.px(4, 4, 8, 5, hue);
            p.px(5, 7, 1, 1, ink);
            p.px(7, 6, 1, 2, ink);
            p.px(9, 5, 1, 3, tint);
            p.px(7, 9, 2, 2, hue);
            p.px(5, 11, 6, 1, hue);
        }
    },
    IMAGE(0x27151f, 0x71455e, 0x533044, 0xf99bd1, 0xffe2f1, 0x8e4771) {
        @Override
        void glyph(Pen p) {
            p.px(4, 4, 8, 8, hue);
            p.px(4, 10, 8, 2, ink);
            p.px(5, 9, 4, 1, ink);
            p.px(6, 8, 2, 1, ink);
            p.px(9, 5, 2, 2, tint);
        }
    },
    VECTOR(0x231625, 0x68486c, 0x4c3250, 0xe5a1ef, 0xfbe1fe, 0x804c88) {
        @Override
        void glyph(Pen p) {
            p.curve(hue, 5, 10.5, 7, 7, 9, 9, 11, 5.5);
            p.px(4, 10, 2, 2, tint);
            p.px(10, 4, 2, 2, tint);
        }
    },
    AUDIO(0x181a2c, 0x4c517c, 0x363a5c, 0xaeb8ff, 0xe6eaff, 0) {
        @Override
        void glyph(Pen p) {
            p.px(4, 6, 2, 4, hue);
            p.px(7, 4, 2, 8, tint);
            p.px(10, 6, 2, 4, hue);
        }
    },
    VIDEO(0x1f1829, 0x5c4c76, 0x433557, 0xccabfe, 0, 0x150f1e) {
        @Override
        void glyph(Pen p) {
            p.px(4, 4, 8, 8, hue);
            p.px(6, 5, 1, 6, ink);
            p.px(7, 6, 1, 4, ink);
            p.px(8, 7, 1, 2, ink);
        }
    },
    ARCHIVE(0x241a0d, 0x695031, 0x4d3920, 0xe7b370, 0xfee7cc, 0x191107) {
        @Override
        void glyph(Pen p) {
            p.px(6, 4, 2, 1, hue);
            p.px(8, 5, 2, 1, hue);
            p.px(6, 6, 2, 1, hue);
            p.px(8, 7, 2, 1, hue);
            p.px(6, 8, 4, 4, tint);
            p.px(7, 10, 2, 1, ink);
        }
    },
    DISK(0x181c21, 0x4d5661, 0x363e46, 0xabc0d6, 0xe3ecf6, 0x101317) {
        @Override
        void glyph(Pen p) {
            p.dot(8, 8, 4, hue);
            p.dot(8, 8, 1.5, ink);
            p.px(5, 6, 1, 1, tint);
        }
    },
    CODE(0x032024, 0x16606a, 0x05464e, 0x2ed4ea, 0xc1f6ff, 0) {
        @Override
        void glyph(Pen p) {
            p.px(6, 5, 1, 1, hue);
            p.px(5, 6, 1, 1, hue);
            p.px(4, 7, 1, 2, hue);
            p.px(5, 9, 1, 1, hue);
            p.px(6, 10, 1, 1, hue);
            p.px(9, 5, 1, 1, hue);
            p.px(10, 6, 1, 1, hue);
            p.px(11, 7, 1, 2, hue);
            p.px(10, 9, 1, 1, hue);
            p.px(9, 10, 1, 1, hue);
            p.px(8, 5, 1, 3, tint);
            p.px(7, 8, 1, 3, tint);
        }
    },
    SCRIPT(0x032024, 0x16606a, 0x05464e, 0x2ed4ea, 0xc1f6ff, 0) {
        @Override
        void glyph(Pen p) {
            p.px(4, 5, 1, 1, hue);
            p.px(5, 6, 1, 1, hue);
            p.px(6, 7, 1, 1, hue);
            p.px(5, 8, 1, 1, hue);
            p.px(4, 9, 1, 1, hue);
            p.px(8, 10, 4, 1, tint);
        }
    },
    DATA(0x06211c, 0x1f6255, 0x0d473d, 0x43d9be, 0xc5f8eb, 0) {
        @Override
        void glyph(Pen p) {
            p.px(6, 4, 1, 1, hue);
            p.px(5, 5, 1, 2, hue);
            p.px(4, 7, 1, 2, hue);
            p.px(5, 9, 1, 2, hue);
            p.px(6, 11, 1, 1, hue);
            p.px(9, 4, 1, 1, hue);
            p.px(10, 5, 1, 2, hue);
            p.px(11, 7, 1, 2, hue);
            p.px(10, 9, 1, 2, hue);
            p.px(9, 11, 1, 1, hue);
            p.px(7, 7, 2, 2, tint);
        }
    },
    SHADER(0x211728, 0x614a72, 0x473455, 0xd7a6fc, 0xf3e5fe, 0x765092) {
        @Override
        void glyph(Pen p) {
            p.dot(8, 7.5, 3.5, ink);
            p.dot(7.6, 7.1, 2.8, hue);
            p.px(6, 5, 1, 1, tint);
            p.px(5, 11, 6, 1, ink);
        }
    },
    PROGRAM(0x081f29, 0x265c75, 0x154257, 0x60cbff, 0xd5f0ff, 0x04151e) {
        @Override
        void glyph(Pen p) {
            p.px(4, 4, 8, 7, hue);
            p.px(5, 6, 6, 4, ink);
            p.px(6, 7, 2, 1, tint);
        }
    },
    INSTALLER(0x081f29, 0x265c75, 0x154257, 0x60cbff, 0xd5f0ff, 0) {
        @Override
        void glyph(Pen p) {
            p.px(7, 4, 2, 3, hue);
            p.px(5, 7, 6, 1, hue);
            p.px(6, 8, 4, 1, hue);
            p.px(7, 9, 2, 1, hue);
            p.px(4, 10, 1, 1, tint);
            p.px(11, 10, 1, 1, tint);
            p.px(4, 11, 8, 1, tint);
        }
    },
    LIBRARY(0x141d22, 0x445964, 0x2f4049, 0x98c5dd, 0xdceff9, 0) {
        @Override
        void glyph(Pen p) {
            p.px(5, 4, 6, 1, tint);
            p.px(4, 5, 8, 1, tint);
            p.px(5, 6, 6, 1, tint);
            for (int y = 8; y <= 10; y += 2) {
                p.px(4, y, 1, 1, hue);
                p.px(5, y + 1, 6, 1, hue);
                p.px(11, y, 1, 1, hue);
            }
        }
    },
    FONT(0x2a1519, 0x76444e, 0x582f37, 0xff9daf, 0, 0) {
        @Override
        void glyph(Pen p) {
            p.px(7, 4, 2, 1, hue);
            p.px(6, 5, 1, 2, hue);
            p.px(9, 5, 1, 2, hue);
            p.px(5, 7, 1, 2, hue);
            p.px(10, 7, 1, 2, hue);
            p.px(6, 8, 4, 1, hue);
            p.px(4, 9, 2, 3, hue);
            p.px(10, 9, 2, 3, hue);
        }
    },
    MODEL(0x211b07, 0x615523, 0x473d13, 0xd8bd51, 0xf6ecc2, 0x766202) {
        @Override
        void glyph(Pen p) {
            p.px(6, 4, 4, 1, tint);
            p.px(5, 5, 6, 1, tint);
            p.px(5, 6, 3, 5, hue);
            p.px(8, 6, 3, 5, ink);
            p.px(6, 11, 2, 1, hue);
            p.px(8, 11, 2, 1, ink);
        }
    },
    EMAIL(0x0f1f20, 0x385d5e, 0x254345, 0x7ecdd1, 0, 0x091516) {
        @Override
        void glyph(Pen p) {
            p.px(4, 5, 8, 6, hue);
            p.px(5, 6, 1, 1, ink);
            p.px(6, 7, 1, 1, ink);
            p.px(7, 8, 2, 1, ink);
            p.px(9, 7, 1, 1, ink);
            p.px(10, 6, 1, 1, ink);
        }
    },
    CALENDAR(0x271617, 0x71484b, 0x533335, 0xf9a2a8, 0xffe4e5, 0x1c0e0f) {
        @Override
        void glyph(Pen p) {
            p.px(4, 5, 8, 7, hue);
            p.px(4, 5, 8, 2, tint);
            p.px(5, 4, 1, 1, tint);
            p.px(10, 4, 1, 1, tint);
            p.px(5, 8, 1, 1, ink);
            p.px(7, 8, 1, 1, ink);
            p.px(9, 8, 1, 1, ink);
            p.px(5, 10, 1, 1, ink);
            p.px(7, 10, 1, 1, ink);
        }
    },
    UNKNOWN(0x1a1c1e, 0x51565b, 0x3a3e42, 0xb5bfca, 0, 0) {
        @Override
        void glyph(Pen p) {
            p.px(6, 4, 4, 1, hue);
            p.px(5, 5, 1, 1, hue);
            p.px(10, 5, 1, 2, hue);
            p.px(8, 7, 2, 1, hue);
            p.px(7, 8, 2, 1, hue);
            p.px(7, 10, 2, 2, hue);
        }
    },
    /** Not a page: a folder, in the suite's gold. */
    FOLDER(0, 0, 0x7e5e01, 0xe4b750, 0xf0cd78, 0) {
        @Override
        void glyph(Pen p) {
        }

        @Override
        void draw(Pen p) {
            p.px(1, 3, 6, 2, fold);
            p.px(1, 4, 14, 2, fold);
            p.px(1, 6, 14, 8, hue);
            p.px(1, 6, 14, 1, tint);
        }
    };

    /** The grid every icon is drawn on. */
    static final double GRID = 16;

    private static final Map<String, FileIcon> BY_EXTENSION = build();

    final Color fill;
    final Color edge;
    final Color fold;
    final Color hue;
    final Color tint;
    final Color ink;

    FileIcon(int fill, int edge, int fold, int hue, int tint, int ink) {
        this.fill = Color.rgb(fill);
        this.edge = Color.rgb(edge);
        this.fold = Color.rgb(fold);
        this.hue = Color.rgb(hue);
        this.tint = Color.rgb(tint);
        this.ink = Color.rgb(ink);
    }

    /** The family's mark, inside the 8 by 8 square at (4, 4). */
    abstract void glyph(Pen p);

    /**
     * The page and the band, then the glyph. The page is columns 2 to 13 and rows 1 to 14; its top right corner is
     * cut on a three-unit diagonal, and the fold under that diagonal is a shade lighter than the page.
     */
    void draw(Pen p) {
        p.px(2, 1, 10, 14, edge);
        p.px(12, 2, 1, 13, edge);
        p.px(13, 3, 1, 12, edge);
        p.px(3, 2, 8, 11, fill);
        p.px(11, 4, 2, 9, fill);
        p.px(11, 2, 1, 2, fold);
        p.px(12, 3, 1, 1, fold);
        glyph(p);
        p.px(2, 13, 12, 2, hue);
    }

    /** This icon in a {@code side} px square. */
    Sketch sketch(float side) {
        Sketch s = new Sketch().tag("filetype." + name().toLowerCase(Locale.ROOT));
        draw(new Pen(s, side / GRID));
        return s;
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

    /** Grid units in, pixels out. */
    record Pen(Sketch s, double k) {

        /** A {@code w} by {@code h} block of grid cells with its top left at {@code (x, y)}. */
        void px(double x, double y, double w, double h, Color c) {
            s.fill(x * k, y * k, w * k, h * k, c);
        }

        /** A filled circle. */
        void dot(double cx, double cy, double r, Color c) {
            s.circle(cx * k, cy * k, r * k, c);
        }

        /** A one-unit line through {@code xy} pairs: the only marks off the grid, and only where a curve is. */
        void curve(Color c, double... xy) {
            double[] xs = new double[xy.length / 2];
            double[] ys = new double[xy.length / 2];
            for (int i = 0; i < xs.length; i++) {
                xs[i] = xy[2 * i] * k;
                ys[i] = xy[2 * i + 1] * k;
            }
            s.polyline(xs, ys, 1.2 * k, c);
        }
    }
}
