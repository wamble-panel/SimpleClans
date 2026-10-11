package net.sacredlabyrinth.phaed.simpleclans;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * How often a clan's tax is billed to its members.
 */
public enum TaxPeriod {
    WEEKLY,
    MONTHLY;

    /**
     * @return the moment one period after {@code fromMillis} (calendar-aware for months)
     */
    public long next(long fromMillis) {
        return next(fromMillis, 0);
    }

    /**
     * @param anchorDay for monthly bills, the day of the month they fall on (0 = keep the
     *                  day of {@code fromMillis}). Without it, a bill on the 31st would drift to
     *                  the 28th after February and stay there.
     */
    public long next(long fromMillis, int anchorDay) {
        ZonedDateTime from = Instant.ofEpochMilli(fromMillis).atZone(ZoneId.systemDefault());
        if (this == WEEKLY) {
            return from.plusWeeks(1).toInstant().toEpochMilli();
        }
        ZonedDateTime next = from.plusMonths(1);
        if (anchorDay > 0) {
            next = next.withDayOfMonth(Math.min(anchorDay, next.toLocalDate().lengthOfMonth()));
        }
        return next.toInstant().toEpochMilli();
    }

    /**
     * @return the day of the month of the given moment, used as a monthly anchor
     */
    public static int dayOfMonth(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).getDayOfMonth();
    }

    /**
     * @return the lang key with this period's display name
     */
    @NotNull
    public String getLangKey() {
        return "tax.period." + name().toLowerCase();
    }

    @Nullable
    public static TaxPeriod parse(@Nullable String value) {
        if (value == null) {
            return null;
        }
        for (TaxPeriod period : values()) {
            if (period.name().equalsIgnoreCase(value)) {
                return period;
            }
        }
        return null;
    }
}
