package net.sacredlabyrinth.phaed.simpleclans.hooks.papi;

import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * KDR rankings for the top/position placeholders. Scoreboards and holograms request
 * these constantly, and each request used to re-sort every clan/player while
 * recomputing their KDR on every comparison. Rankings are now rebuilt at most once
 * every {@value #TTL_MILLIS} ms, with each KDR computed once per rebuild.
 */
public final class RankingCache {

    private static final long TTL_MILLIS = 10_000;

    private static volatile Ranking<Clan> clans;
    private static volatile Ranking<ClanPlayer> players;

    private RankingCache() {
    }

    @NotNull
    public static Ranking<Clan> clans(@NotNull ClanManager clanManager) {
        Ranking<Clan> ranking = clans;
        if (ranking == null || ranking.isExpired()) {
            ranking = build(clanManager.getClans(), Clan::getTotalKDR);
            clans = ranking;
        }
        return ranking;
    }

    @NotNull
    public static Ranking<ClanPlayer> players(@NotNull ClanManager clanManager) {
        Ranking<ClanPlayer> ranking = players;
        if (ranking == null || ranking.isExpired()) {
            ranking = build(clanManager.getAllClanPlayers(), ClanPlayer::getKDR);
            players = ranking;
        }
        return ranking;
    }

    @NotNull
    private static <T> Ranking<T> build(@NotNull List<T> items, @NotNull ToDoubleFunction<T> score) {
        Map<T, Double> scores = new IdentityHashMap<>();
        for (T item : items) {
            scores.put(item, score.applyAsDouble(item));
        }
        // Highest first, matching ClanManager#sortClansByKDR / #sortClanPlayersByKDR.
        items.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));

        Map<T, Integer> positions = new IdentityHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            positions.put(items.get(i), i + 1);
        }
        return new Ranking<>(Collections.unmodifiableList(items), positions);
    }

    public static final class Ranking<T> {
        private final List<T> sorted;
        private final Map<T, Integer> positions;
        private final long createdAt = System.currentTimeMillis();

        private Ranking(@NotNull List<T> sorted, @NotNull Map<T, Integer> positions) {
            this.sorted = sorted;
            this.positions = positions;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - createdAt > TTL_MILLIS;
        }

        /**
         * @param position 1-based position
         * @return the entry at that position, or null if out of range
         */
        @Nullable
        public T get(int position) {
            return position >= 1 && position <= sorted.size() ? sorted.get(position - 1) : null;
        }

        /**
         * @return the 1-based position of the entry, or 0 if it is not ranked
         */
        public int positionOf(@NotNull Object entry) {
            Integer position = positions.get(entry);
            return position != null ? position : 0;
        }
    }
}
