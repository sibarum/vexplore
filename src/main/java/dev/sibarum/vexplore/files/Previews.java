package dev.sibarum.vexplore.files;

import dev.sibarum.vexplore.files.Preview.Tier;
import sibarum.imagelib.Frames;
import sibarum.imagelib.Imagelib;
import sibarum.probe.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The preview ladder: the picture for anything imagelib can decode, then the first lines of anything that decodes as
 * text, and, for everything else, a hex dump with the strings inside it and how random it looks.
 *
 * <p><b>A bounded read, and nothing executed.</b> {@link #SAMPLE} bytes from the start of the file, through a plain
 * input stream, decide the tier. Only an image is read further, whole and up to {@link #MAX_IMAGE}, and handed to
 * imagelib, whose decoders are pure Rust that turn bytes into pixels and run nothing the file names. The file is
 * never mapped or opened by an application. That is what "previews are read-only and nothing is executed" reduces
 * to.
 *
 * <p><b>Every failure falls a tier.</b> A file that says it is an image and will not decode, an image too large to
 * hold, or a machine where the native decoder will not load: each one previews as the bytes it is, so no file is ever
 * blank and none takes the dock down.
 *
 * <p>Blocking: run it on the offload lane.
 */
public final class Previews {

    /** How much of a file is read. Enough for a screenful of text and a meaningful entropy; cheap on a network share. */
    static final int SAMPLE = 64 * 1024;

    /** The largest image file decoded. Past it the file is bytes, as any file is; its pixels would not fit anyway. */
    static final long MAX_IMAGE = 128L * 1024 * 1024;

    /** The long side a vector document is rasterised at: a {@link Picture#FRAME_SIDE}, since a vector has no size. */
    static final int VECTOR_SIDE = Picture.FRAME_SIDE;

    private static final Log LOG = Log.of("vexplore.preview");

    /** Said once: a decoder that will not load fails the same way for every file after the first. */
    private static volatile boolean decoderReported;

    /** Bytes shown in the hex dump, sixteen to a row. */
    static final int DUMP = 16 * 16;

    private static final int TEXT_LINES = 60;
    private static final int MIN_STRING = 6;
    private static final int MAX_STRINGS = 8;

    private Previews() {
    }

    /** A preview of {@code path}: a folder is a folder, a file is text if it decodes as text and bytes if not. */
    public static Preview of(Path path) {
        try {
            if (Files.isDirectory(path)) {
                return new Preview(path, Tier.FOLDER, "folder", List.of(), 0d, List.of(), false, null);
            }
            long size = Files.size(path);
            byte[] sample = read(path);
            Preview image = image(path, sample, size);
            return image != null ? image : of(path, sample, size > sample.length);
        } catch (IOException | RuntimeException e) {
            return new Preview(path, Tier.BYTES, "unreadable: " + e.getClass().getSimpleName(), List.of(), 0d,
                    List.of(), false, null);
        }
    }

    /** The same, from bytes already in hand — what the tests, and any caller with a buffer, use. */
    public static Preview of(Path path, byte[] sample, boolean truncated) {
        String text = decode(sample, truncated);
        if (text != null) {
            List<String> lines = new ArrayList<>();
            int start = 0;
            for (int i = 0; i <= text.length() && lines.size() < TEXT_LINES; i++) {
                if (i == text.length() || text.charAt(i) == '\n') {
                    if (i > start || i < text.length()) {
                        lines.add(text.substring(start, i).replace("\r", ""));
                    }
                    start = i + 1;
                }
            }
            String identity = sample.length == 0 ? "empty file" : "UTF-8 text";
            return new Preview(path, Tier.TEXT, identity, List.copyOf(lines), entropy(sample), List.of(), truncated,
                    null);
        }
        return new Preview(path, Tier.BYTES, identify(sample), dump(sample), entropy(sample), strings(sample),
                truncated, null);
    }

    /** Whether {@code path} previews as text: the sample alone decides, so no image is decoded to answer it. */
    public static boolean isText(Path path) {
        try {
            if (Files.isDirectory(path)) {
                return false;
            }
            byte[] sample = read(path);
            return decode(sample, Files.size(path) > sample.length) != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * The image tier, or null to fall through to the others. The sample's first bytes say whether imagelib wants the
     * file; only then is the whole of it read.
     */
    static Preview image(Path path, byte[] sample, long size) {
        Imagelib.Kind kind;
        try {
            kind = Imagelib.probe(sample);
        } catch (RuntimeException | LinkageError e) {
            unavailable(e);
            return null;
        }
        if (kind == Imagelib.Kind.UNKNOWN || size > MAX_IMAGE) {
            return null;
        }
        try {
            Stamp stamp = Stamp.of(path);
            byte[] bytes = Files.readAllBytes(path);
            Picture picture;
            String what;
            if (kind == Imagelib.Kind.VECTOR) {
                Imagelib.Size intrinsic = Imagelib.svgSize(bytes);
                int[] box = vectorBox(intrinsic.width(), intrinsic.height());
                picture = Picture.of(stamp, Imagelib.rasterize(bytes, box[0], box[1]),
                        Math.round(intrinsic.width()), Math.round(intrinsic.height()), true);
                what = "SVG image";
            } else {
                Frames frames = Imagelib.decode(bytes);
                picture = Picture.of(stamp, frames, frames.width(), frames.height(), false);
                String named = identify(sample);
                what = named.endsWith(" image") ? named : "image";
            }
            return new Preview(path, Tier.IMAGE, describe(what, picture), List.of(), 0d, List.of(), false, picture);
        } catch (OutOfMemoryError e) {
            // The one error worth catching: it is this file's pixels that did not fit, and the next file's will.
            LOG.warn("{} is too large to decode for a preview", path);
            return null;
        } catch (IOException | RuntimeException | LinkageError e) {
            if (e instanceof LinkageError) {
                unavailable(e);
            }
            return null;
        }
    }

    /** A vector's raster size: its own aspect, with the long side at {@link #VECTOR_SIDE}, and never zero. */
    static int[] vectorBox(float width, float height) {
        float w = width > 0f ? width : 1f;
        float h = height > 0f ? height : 1f;
        float scale = VECTOR_SIDE / Math.max(w, h);
        return new int[] {Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale))};
    }

    /** "PNG image · 1920 × 1080", and for an animation its frame count and length. */
    static String describe(String what, Picture p) {
        StringBuilder s = new StringBuilder(what).append(" · ").append(p.sourceWidth()).append(" × ")
                .append(p.sourceHeight());
        if (p.animated()) {
            long ms = 0;
            for (int d : p.delays()) {
                ms += d;
            }
            s.append(" · ").append(p.count()).append(" frames");
            if (ms > 0) {
                s.append(String.format(Locale.ROOT, ", %.1f s", ms / 1000d));
            }
        }
        return s.toString();
    }

    private static void unavailable(Throwable e) {
        if (!decoderReported) {
            decoderReported = true;
            LOG.warn("images preview as bytes: the image decoder is unavailable ({})", e.toString());
        }
    }

    private static byte[] read(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return in.readNBytes(SAMPLE);
        }
    }

    /**
     * The sample as text, or null if it is not. Text means valid UTF-8 with no control characters other than the
     * whitespace ones. A multi-byte character cut in half by the end of the sample is not evidence against it — when
     * the read was truncated, an incomplete tail is forgiven.
     */
    static String decode(byte[] sample, boolean truncated) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(sample);
        CharBuffer out = CharBuffer.allocate(sample.length + 1);
        CoderResult r = decoder.decode(in, out, !truncated);
        if (r.isError()) {
            return null;
        }
        if (r.isUnderflow() && in.hasRemaining() && !truncated) {
            return null;
        }
        out.flip();
        String s = out.toString();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t' && c != '\f') {
                return null;
            }
            if (c == 0x7f) {
                return null;
            }
        }
        return s;
    }

    /** What the first bytes say the file is. Small on purpose: a signature table is a promise to keep it right. */
    static String identify(byte[] b) {
        if (starts(b, 0x89, 'P', 'N', 'G')) {
            return "PNG image";
        }
        if (starts(b, 0xFF, 0xD8, 0xFF)) {
            return "JPEG image";
        }
        if (starts(b, 'G', 'I', 'F', '8')) {
            return "GIF image";
        }
        if (starts(b, '%', 'P', 'D', 'F')) {
            return "PDF document";
        }
        if (starts(b, 'P', 'K', 3, 4)) {
            return "ZIP archive (or a document built on one)";
        }
        if (starts(b, 0x1F, 0x8B)) {
            return "gzip data";
        }
        if (starts(b, '7', 'z', 0xBC, 0xAF)) {
            return "7-Zip archive";
        }
        if (starts(b, 'M', 'Z')) {
            return "Windows program or library (PE)";
        }
        if (starts(b, 0x7F, 'E', 'L', 'F')) {
            return "ELF program";
        }
        if (starts(b, 'R', 'I', 'F', 'F')) {
            return "RIFF container (WAV, AVI or WebP)";
        }
        if (b.length >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            return "MPEG-4 container (video or audio)";
        }
        if (starts(b, 0x1A, 0x45, 0xDF, 0xA3)) {
            return "Matroska or WebM video";
        }
        if (b.length == 0) {
            return "empty file";
        }
        return "unknown binary";
    }

    private static boolean starts(byte[] b, int... signature) {
        if (b.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((b[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /** Sixteen bytes a row: offset, hex, and the printable characters, the way every hex viewer has drawn it. */
    static List<String> dump(byte[] b) {
        List<String> rows = new ArrayList<>();
        int n = Math.min(b.length, DUMP);
        for (int off = 0; off < n; off += 16) {
            StringBuilder hex = new StringBuilder();
            StringBuilder ascii = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                if (off + i < n) {
                    int v = b[off + i] & 0xFF;
                    hex.append(String.format(Locale.ROOT, "%02x ", v));
                    ascii.append(v >= 0x20 && v < 0x7f ? (char) v : '.');
                } else {
                    hex.append("   ");
                }
                if (i == 7) {
                    hex.append(' ');
                }
            }
            rows.add(String.format(Locale.ROOT, "%08x  %s |%s|", off, hex, ascii));
        }
        return List.copyOf(rows);
    }

    /** Runs of printable ASCII at least {@value #MIN_STRING} long: often the only readable part of a binary. */
    static List<String> strings(byte[] b) {
        List<String> found = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i <= b.length && found.size() < MAX_STRINGS; i++) {
            int v = i < b.length ? b[i] & 0xFF : -1;
            if (v >= 0x20 && v < 0x7f) {
                run.append((char) v);
            } else {
                if (run.length() >= MIN_STRING) {
                    found.add(run.length() > 80 ? run.substring(0, 80) : run.toString());
                }
                run.setLength(0);
            }
        }
        return List.copyOf(found);
    }

    /** Shannon entropy in bits per byte: about 0 for a run of one value, about 8 for compressed or encrypted data. */
    static double entropy(byte[] b) {
        if (b.length == 0) {
            return 0d;
        }
        long[] count = new long[256];
        for (byte x : b) {
            count[x & 0xFF]++;
        }
        double h = 0d;
        for (long c : count) {
            if (c > 0) {
                double p = (double) c / b.length;
                h -= p * (Math.log(p) / Math.log(2));
            }
        }
        return h;
    }
}
