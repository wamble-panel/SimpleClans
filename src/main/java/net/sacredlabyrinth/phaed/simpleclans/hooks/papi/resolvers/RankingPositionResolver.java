package net.sacredlabyrinth.phaed.simpleclans.hooks.papi.resolvers;

import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.hooks.papi.PlaceholderResolver;
import net.sacredlabyrinth.phaed.simpleclans.hooks.papi.RankingCache;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;

public class RankingPositionResolver extends PlaceholderResolver {
    public RankingPositionResolver(@NotNull SimpleClans plugin) {
        super(plugin);
    }

    @Override
    public @NotNull String getId() {
        return "ranking_position";
    }

    @Override
    public @NotNull String resolve(@Nullable OfflinePlayer player, @NotNull Object object, @NotNull Method method,
                                   @NotNull String placeholder, @NotNull Map<String, String> config) {
        if (object instanceof Clan) {
            return String.valueOf(RankingCache.clans(plugin.getClanManager()).positionOf(object));
        }
        if (object instanceof ClanPlayer) {
            return String.valueOf(RankingCache.players(plugin.getClanManager()).positionOf(object));
        }

        return "";
    }
}
