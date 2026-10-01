package dev.sibarum.vexplore.suggest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Modified times as the Modified column reads them: {@code Today 09:30}, {@code Yesterday}, {@code Sep 27}. */
public final class Dates {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private Dates() {
    }

    /** {@code Today 09:30}, {@code Yesterday}, {@code Sep 27}, or {@code Sep 27, 2024} once it is not this year. */
    public static String column(Instant modified, Instant now, ZoneId zone) {
        LocalDate day = LocalDate.ofInstant(modified, zone);
        LocalDate today = LocalDate.ofInstant(now, zone);
        if (day.equals(today)) {
            return "Today " + TIME.format(modified.atZone(zone));
        }
        if (day.equals(today.minusDays(1))) {
            return "Yesterday";
        }
        return day.getYear() == today.getYear() ? short_(day) : FULL.format(day);
    }

    /** {@code Sep 27}. */
    static String short_(LocalDate day) {
        return DAY.format(day);
    }
}
