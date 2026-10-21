package dev.sibarum.vexplore.suggest;

import java.util.Locale;

/** Sizes as a person reads them: {@code 184 KB}, {@code 3.4 MB}, {@code 1.42 GB}. Binary-counted, like Explorer. */
public final class Bytes {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    private Bytes() {
    }

    /**
     * One decimal below ten and none from ten up; gigabytes and above keep two decimals below ten, because
     * {@code 2.10 GB} and {@code 2.1 GB} are a hundred megabytes apart.
     */
    public static String format(long bytes) {
        double v = Math.max(0, bytes);
        int u = 0;
        while (v >= 1024 && u < UNITS.length - 1) {
            v /= 1024;
            u++;
        }
        if (u == 0) {
            return (long) v + " B";
        }
        String s;
        if (v >= 10) {
            s = String.format(Locale.ROOT, "%.0f", v);
        } else if (u >= 3) {
            s = String.format(Locale.ROOT, "%.2f", v);
        } else {
            s = String.format(Locale.ROOT, "%.1f", v);
            if (s.endsWith(".0")) {
                s = s.substring(0, s.length() - 2);
            }
        }
        return s + " " + UNITS[u];
    }

    /** A round threshold, named as a person names it: {@code 1 GB}, not {@code 1.00 GB}. */
    public static String threshold(long bytes) {
        String s = format(bytes);
        return s.replace(".00 ", " ");
    }
}
