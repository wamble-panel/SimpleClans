package net.sacredlabyrinth.phaed.simpleclans.commands.completions;

import co.aikar.commands.BukkitCommandCompletionContext;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Completes the tags of every clan whose warp is currently set and toggled on.
 */
public class ActiveWarpsCompletion extends AbstractSyncCompletion {

    public ActiveWarpsCompletion(@NotNull SimpleClans plugin) {
        super(plugin);
    }

    @Override
    public Collection<String> getCompletions(BukkitCommandCompletionContext c) {
        return clanManager.getClans().stream()
                .filter(Clan::hasActiveWarp)
                .map(Clan::getTag)
                .collect(Collectors.toList());
    }

    @Override
    public @NotNull String getId() {
        return "active_warps";
    }
}
