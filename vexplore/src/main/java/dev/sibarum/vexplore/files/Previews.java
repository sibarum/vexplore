package dev.sibarum.vexplore.files;

import dev.sibarum.suite.pictures.Sizes;
import dev.sibarum.vexplore.files.Preview.Tier;
import dev.vexelray.codec.image.Decoded;
import dev.vexelray.codec.image.Pictures;
import dev.vexelray.codec.video.Videos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The preview ladder: frames from a video, the picture for anything imagelib can decode, then the first lines of anything that decodes as
 * text, and, for everything else, a hex dump with the strings inside it and how random it looks.
 *
 * <p><b>A bounded read, and nothing executed.</b> {@link #SAMPLE} bytes from the start of the file, through a plain
 * input stream, decide the tier. Only an image is read further, whole and up to {@link Pictures}'s limit, and handed to
 * imagelib, whose decoders are pure Rust that turn bytes into pixels and run nothing the file names. The file is
 * never mapped or opened by an application. That is what "previews are read-only and nothing is executed" reduces
 * to.
 *
 * <p><b>Every failure falls a tier.</b> A file that says it is an image and will not decode, an image too large to
 * hold, or a machine where the native decoder will not load: each one previews as the bytes it is, so no file is ever
 * blank and none takes the dock down. A file named like a picture or a video that gives none says the decoder's
 * reason beside what it is ("PNG image · does not decode as an image"), so a hex dump where a picture was expected
 * is not left to explain itself.
 *
 * <p>Blocking: run it on the offload lane.
 */
public final class Previews {

    /** How much of a file is read. Enough for a screenful of text and a meaningful entropy; cheap on a network share. */
    static final int SAMPLE = 64 * 1024;

    /** Bytes shown in the hex dump, sixteen to a row. */
    static final int DUMP = 16 * 16;

    /** Frames in a video's slideshow, how long each shows, and the longest side each keeps: the dock's, not a screen's. */
    static final int SLIDES = 8;
    static final int SLIDE_MS = 1200;
    static final int SLIDE_SIDE = 640;

    private static final int TEXT_LINES = 60;
    private static final int MIN_STRING = 6;
    private static final int MAX_STRINGS = 8;

    private Previews() {
    }

    /** A preview of {@code path}: a folder is a folder, a file is text if it decodes as text and bytes if not. */
    public static Preview of(Path path) {
        return of(path, false);
    }

    /**
     * The same, and for an image also a {@link Preview#large} picture at the viewer's size when {@code large}: both
     * from one decode, which is the expensive part.
     */
    public static Preview of(Path path, boolean large) {
        return of(path, large, p -> { });
    }

    /**
     * The same, giving {@code early} a first look at a video while the rest of it is read: see {@link #video}. What
     * comes back is the whole preview, and replaces it.
     */
    public static Preview of(Path path, boolean large, Consumer<Preview> early) {
        try {
            if (Files.isDirectory(path)) {
                return new Preview(path, Tier.FOLDER, "folder", List.of(), 0d, List.of(), false, null, null);
            }
            Decoded video = video(path, early);
            if (video != null && video.image()) {
                return new Preview(path, Tier.VIDEO, video.identity(), List.of(), 0d, List.of(), false,
                        video.picture(), null);
            }
            long size = Files.size(path);
            byte[] sample = read(path);
            Decoded image = image(path, sample, size, large);
            if (image.image()) {
                return new Preview(path, Tier.IMAGE, image.identity(), List.of(), 0d, List.of(), false,
                        image.picture(), image.picture(1));
            }
            // Named for a decoder that gave no picture: the tier below says why. Anything else was never expected
            // to be a picture, and is simply what it is.
            String why = video != null ? video.identity() : Pictures.named(path) ? image.identity() : null;
            Preview floor = of(path, sample, size > sample.length);
            return why == null ? floor : new Preview(path, floor.tier(), floor.identity() + " · " + why,
                    floor.lines(), floor.entropy(), floor.strings(), floor.truncated(), null, null);
        } catch (IOException | RuntimeException e) {
            return new Preview(path, Tier.BYTES, "unreadable: " + e.getClass().getSimpleName(), List.of(), 0d,
                    List.of(), false, null, null);
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
                    null, null);
        }
        return new Preview(path, Tier.BYTES, identify(sample), dump(sample), entropy(sample), strings(sample),
                truncated, null, null);
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
     * The image tier: the suite's decoder ({@link Pictures}), which reads the whole file only once the sample's first
     * bytes say imagelib wants it. A file it has no picture of, for whatever reason, falls a tier, and the decode
     * says the reason.
     */
    static Decoded image(Path path, byte[] sample, long size, boolean large) {
        return Pictures.decode(path, sample, size, Sizes.of(large));
    }

    /**
     * The video tier, or null for a file not tried as one: {@link #SLIDES} frames from across a file named like a
     * video, through FFmpeg ({@link Videos#slides}), shown {@link #SLIDE_MS} each as an animated picture is. Only a
     * file on this disk, since FFmpeg opens a path and not a remote one. A file FFmpeg will not open, or a machine
     * where it will not load, falls a tier, to the bytes it is, and the decode says the reason.
     *
     * <p>{@code early} is given the first frame found, as a preview of its own, the moment it is: in a long HD video
     * finding the rest is most of the wait, so the dock shows that frame and then the slideshow.
     */
    static Decoded video(Path path, Consumer<Preview> early) {
        if (Kind.of(path) != Kind.VIDEO || path.getFileSystem() != FileSystems.getDefault()) {
            return null;
        }
        return Videos.slides(path, SLIDES, SLIDE_SIDE, SLIDE_MS, (identity, first) -> early.accept(
                new Preview(path, Tier.VIDEO, identity, List.of(), 0d, List.of(), false, first, null)));
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
            // The same box starts a phone's photographs, which no decoder here reads; a video they are not.
            String brand = b.length >= 12 ? new String(b, 8, 4, StandardCharsets.ISO_8859_1) : "";
            return switch (brand) {
                case "heic", "heix", "heim", "heis", "mif1", "msf1" -> "HEIF image";
                case "avif", "avis" -> "AVIF image";
                default -> "MPEG-4 container (video or audio)";
            };
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
