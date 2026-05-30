package net.sacredlabyrinth.phaed.simpleclans.listeners;

import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Routes regular chat to alliance chat for players who have toggled "alliance chat
 * mode" on. Runs at {@link EventPriority#LOW} so it claims the message before the
 * clan-channel listener, and cancels the event when it routes.
 */
public class AllianceChatListener extends SCListener {

    public AllianceChatListener(@NotNull SimpleClans plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        AllianceType type = plugin.getAllianceChatManager().getToggled(player.getUniqueId());
        if (type == null) {
            return;
        }

        ClanPlayer cp = plugin.getClanManager().getClanPlayer(player);
        Clan clan = cp != null ? cp.getClan() : null;

        // Membership or permission lost since toggling — disable and let the message
        // fall through to normal chat.
        if (clan == null || !plugin.getAllianceManager().isInAlliance(clan, type)
                || !plugin.getAllianceChatManager().canUse(player)) {
            plugin.getAllianceChatManager().clear(player.getUniqueId());
            ChatBlock.sendMessageKey(player, "alliance.chat.access.revoked");
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage();
        plugin.getAllianceChatManager().send(type, cp, message);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAllianceChatManager().clear(event.getPlayer().getUniqueId());
    }
}
