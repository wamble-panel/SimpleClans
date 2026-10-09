package net.sacredlabyrinth.phaed.simpleclans;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class War {
    private final Map<Clan, Integer> clans = new HashMap<>();

    public War(@NotNull Clan clan1, @NotNull Clan clan2) {
        clans.put(clan1, 0);
        clans.put(clan2, 0);
    }

    public List<Clan> getClans() {
        return new ArrayList<>(clans.keySet());
    }

    public int getTotalCasualties() {
        return clans.values().stream().mapToInt(value -> value).sum();
    }

    public int getCasualties(@NotNull Clan clan) {
        return clans.getOrDefault(clan, 0);
    }

    public void increaseCasualties(@NotNull Clan clan) {
        clans.computeIfPresent(clan, (c, i) -> i + 1);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        // Identity is the pair of clans only. Casualties change during the war; including them
        // changed the hash of a War already stored as a HashMap key, so the war could no longer
        // be found or removed after the first kill.
        if (obj instanceof War) {
            return clans.keySet().equals(((War) obj).clans.keySet());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return clans.keySet().hashCode();
    }
}
