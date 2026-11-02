package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Preview.Tier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The preview ladder, from the floor up: 0.1's, a tier at a time (docs/shape.md, M4). A folder is a folder, a file
 * that decodes as text is its first lines, and every other file is the bytes it is: a hex dump, the strings inside it,
 * and how random it looks.
 *
 * <p><b>A bounded read, and nothing executed.</b> {@link #SAMPLE} bytes from the start of the file, through a plain
 * input stream, are all that is read. The file is never mapped, and never opened by another program.
 *
 * <p><b>A file that cannot be read is said, not thrown</b>: it previews as unreadable, with the reason, so the dock is
 * never blank and never takes the explorer down. Blocking: run by {@link ReadPreview}.
 */
public final class Previews {

    /** How much of a file is read: enough for a screenful and a meaningful entropy, cheap on a network share. */
    static final int SAMPLE = 64 * 1024;

    /** Bytes shown in the hex dump, sixteen to a row. */
    static final int DUMP = 16 * 16;

    /** Lines of a text the dock is given. */
    static final int TEXT_LINES = 60;

    /** Characters of a text line kept: past this a line is cut, since nobody reads that far across in a preview. */
    static final int LINE_WIDTH = 240;

    /** A tab, as the spaces it is drawn as: the atlas has no glyph for one. */
    private static final String TAB = "    ";

    private static final int MIN_STRING = 6;
    private static final int MAX_STRINGS = 8;

    /** How many of a folder's entries are counted; a bigger folder says it holds at least this many. */
    static final int COUNTED = 10_000;

    private Previews() {
    }

    /** A preview of {@code path}. */
    public static Preview of(Path path) {
        try {
            if (Files.isDirectory(path)) {
                return new Preview(path, Tier.FOLDER, "folder", count(path), List.of(), 0d, List.of(), false);
            }
            long size = Files.size(path);
            byte[] sample;
            try (InputStream in = Files.newInputStream(path)) {
                sample = in.readNBytes(SAMPLE);
            }
            return of(path, sample, size);
        } catch (IOException | RuntimeException e) {
            return new Preview(path, Tier.BYTES, "unreadable: " + ListFolder.problem(e), 0L, List.of(), 0d,
                    List.of(), false);
        }
    }

    /** The same, from bytes already in hand: what the tests use. Text if it decodes as text, its bytes if not. */
    static Preview of(Path path, byte[] sample, long size) {
        boolean truncated = size > sample.length;
        String text = decode(sample, truncated);
        if (text != null) {
            String identity = sample.length == 0 ? "empty file" : "UTF-8 text";
            return new Preview(path, Tier.TEXT, identity, size, lines(text), entropy(sample), List.of(), truncated);
        }
        return new Preview(path, Tier.BYTES, identify(sample), size, dump(sample), entropy(sample), strings(sample),
                truncated);
    }

    /**
     * The sample as text, or null if it is not: 0.1's rule. Text is valid UTF-8 with no control characters but the
     * whitespace ones. A character cut in half by the end of the sample is no evidence against it, so when the read
     * stopped short of the file's end an incomplete tail is forgiven.
     */
    static String decode(byte[] sample, boolean truncated) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(sample);
        CharBuffer out = CharBuffer.allocate(sample.length + 1);
        CoderResult r = decoder.decode(in, out, !truncated);
        if (r.isError() || in.hasRemaining() && !truncated) {
            return null;
        }
        out.flip();
        String s = out.toString();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t' && c != '\f' || c == 0x7f) {
                return null;
            }
        }
        return s;
    }

    /** The first {@link #TEXT_LINES} lines, without their line ends, tabs drawn as spaces and long lines cut. */
    static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= text.length() && lines.size() < TEXT_LINES; i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                if (i > start || i < text.length()) {
                    String line = text.substring(start, i).replace("\r", "").replace("\t", TAB);
                    lines.add(line.length() > LINE_WIDTH ? line.substring(0, LINE_WIDTH) : line);
                }
                start = i + 1;
            }
        }
        return lines;
    }

    private static long count(Path dir) throws IOException {
        long n = 0;
        try (DirectoryStream<Path> in = Files.newDirectoryStream(dir)) {
            for (Path ignored : in) {
                if (++n >= COUNTED) {
                    break;
                }
            }
        }
        return n;
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
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            // The same box starts a phone's photographs, which are not videos.
            String brand = new String(b, 8, 4, StandardCharsets.ISO_8859_1);
            return brand.startsWith("hei") || brand.equals("mif1") || brand.equals("msf1") ? "HEIF image"
                    : brand.startsWith("avi") ? "AVIF image" : "MPEG-4 container (video or audio)";
        }
        if (starts(b, 0x1A, 0x45, 0xDF, 0xA3)) {
            return "Matroska or WebM video";
        }
        return b.length == 0 ? "empty file" : "unknown binary";
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
            rows.add(String.format(Locale.ROOT, "%08x  %s %s", off, hex, ascii));
        }
        return rows;
    }

    /** Runs of printable ASCII at least six long: often the only readable part of a binary. */
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
        return found;
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
