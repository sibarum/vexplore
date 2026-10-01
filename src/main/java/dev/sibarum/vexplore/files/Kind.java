package dev.sibarum.vexplore.files;

import java.util.Locale;
import java.util.Map;

/**
 * What a file is, coarsely: the grouping the Suggestion Rail speaks in ("all videos here").
 *
 * <p>Decided from the extension alone. That is a choice, not a shortcut: the rail has to be ready in the pause
 * after a modifier key goes down, and sniffing content for every file in a folder is not. A content sniffer can
 * refine {@link #OTHER} later without changing what any other kind means.
 */
public enum Kind {
    FOLDER("folder", "folders"),
    VIDEO("video", "videos"),
    IMAGE("image", "images"),
    AUDIO("audio file", "audio files"),
    DOCUMENT("document", "documents"),
    TEXT("text file", "text files"),
    CODE("source file", "source files"),
    ARCHIVE("archive", "archives"),
    PROGRAM("program", "programs"),
    OTHER("file", "files");

    private static final Map<String, Kind> BY_EXTENSION = build();

    private final String singular;
    private final String plural;

    Kind(String singular, String plural) {
        this.singular = singular;
        this.plural = plural;
    }

    /** "video", "text file". */
    public String singular() {
        return singular;
    }

    /** "videos", "text files". */
    public String plural() {
        return plural;
    }

    /** The kind a lower-case extension (no dot) stands for; {@link #OTHER} when nothing claims it. */
    public static Kind ofExtension(String extension) {
        return BY_EXTENSION.getOrDefault(extension.toLowerCase(Locale.ROOT), OTHER);
    }

    private static Map<String, Kind> build() {
        Map<String, Kind> m = new java.util.HashMap<>();
        put(m, VIDEO, "mp4 mov mkv webm avi wmv m4v mpg mpeg");
        put(m, IMAGE, "png jpg jpeg gif webp bmp tif tiff svg heic ico");
        put(m, AUDIO, "wav mp3 flac ogg m4a aac opus");
        put(m, DOCUMENT, "pdf doc docx odt rtf xls xlsx ods ppt pptx epub");
        put(m, TEXT, "txt md log csv tsv json xml yaml yml ini toml cfg");
        put(m, CODE, "java py js ts c cpp h hpp rs go cs html css sh bat ps1 kt sql");
        put(m, ARCHIVE, "zip 7z rar tar gz tgz bz2 xz");
        put(m, PROGRAM, "exe msi dll appx");
        return Map.copyOf(m);
    }

    private static void put(Map<String, Kind> m, Kind kind, String extensions) {
        for (String e : extensions.split(" ")) {
            m.put(e, kind);
        }
    }
}
