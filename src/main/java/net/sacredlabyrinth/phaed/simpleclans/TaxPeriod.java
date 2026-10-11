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
        ZonedDateTime from = Instant.ofEpochMilli(fromMillis).atZone(ZoneId.systemDefault());
        return (this == WEEKLY ? from.plusWeeks(1) : from.plusMonths(1)).toInstant().toEpochMilli();
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
