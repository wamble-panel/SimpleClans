package net.sacredlabyrinth.phaed.simpleclans.alliance;

import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

/**
 * Routes and delivers alliance chat, fully scoped per {@link AllianceType}.
 *
 * <h2>Isolation</h2>
 * Routing always re-checks the sender's clan's <em>current</em> alliance membership,
 * so a message can only ever reach the alliance the sender currently belongs to. The
 * toggle map records exactly one alliance per player.
 *
 * <h2>Visibility</h2>
 * Only online members of clans currently in the alliance receive a message; players
 * with {@code clans.alliance.chat.spy} additionally see both alliances' chat.
 */
public class AllianceChatManager {

    private static final String USE_PERMISSION = "clans.alliance.chat.use";
    private static final String SPY_PERMISSION = "clans.alliance.chat.spy";

    private final SimpleClans plugin;
    // Players currently in "alliance chat mode" -> the alliance their chat is routed to.
    private final Map<UUID, AllianceType> toggled = new ConcurrentHashMap<>();

    public AllianceChatManager(@NotNull SimpleClans plugin) {
        this.plugin = plugin;
        pruneOldLogs();
    }

    public boolean canUse(@NotNull Player player) {
        return plugin.getPermissionsManager().has(player, USE_PERMISSION);
    }

    @Nullable
    public AllianceType getToggled(@NotNull UUID uuid) {
        return toggled.get(uuid);
    }

    /**
     * Toggles alliance-chat mode for a player.
     *
     * @return true if the mode is now ON, false if now OFF
     */
    public boolean toggle(@NotNull Player player, @NotNull AllianceType type) {
        AllianceType current = toggled.get(player.getUniqueId());
        if (type.equals(current)) {
            toggled.remove(player.getUniqueId());
            return false;
        }
        toggled.put(player.getUniqueId(), type);
        return true;
    }

    public void clear(@NotNull UUID uuid) {
        toggled.remove(uuid);
    }

    /**
     * Revokes alliance-chat access for all online members of a clan that just left or
     * was removed from an alliance, disabling their toggle and notifying them.
     */
    public void clearClanAccess(@NotNull Clan clan) {
        for (ClanPlayer cp : clan.getOnlineMembers()) {
            Player player = cp.toPlayer();
            if (player != null && toggled.remove(player.getUniqueId()) != null) {
                ChatBlock.sendMessageKey(player, "alliance.chat.access.revoked");
            }
        }
    }

    /**
     * Sends a single message to an alliance's chat.
     *
     * @param type    the target alliance
     * @param sender  the sending clan player
     * @param message the raw message
     * @return true if delivered, false if the sender's clan is not (or no longer) in the alliance
     */
    public boolean send(@NotNull AllianceType type, @NotNull ClanPlayer sender, @NotNull String message) {
        Clan clan = sender.getClan();
        if (clan == null || !plugin.getAllianceManager().isInAlliance(clan, type)) {
            return false;
        }

        String format = type == AllianceType.NATO
                ? plugin.getSettingsManager().getString(ALLIANCE_CHAT_FORMAT_NATO)
                : plugin.getSettingsManager().getString(ALLIANCE_CHAT_FORMAT_SCO);
        String line = format
                .replace("{clan}", clan.getName())
                .replace("{player}", sender.getName())
                .replace("{message}", message);

        Set<UUID> delivered = new HashSet<>();
        Alliance alliance = plugin.getAllianceManager().getAlliance(type);
        for (String tag : alliance.getMemberTags()) {
            Clan member = plugin.getClanManager().getClan(tag);
            if (member == null) {
                continue;
            }
            for (ClanPlayer cp : member.getOnlineMembers()) {
                Player player = cp.toPlayer();
                if (player != null) {
                    ChatBlock.sendMessage(player, line);
                    delivered.add(player.getUniqueId());
                }
            }
        }

        // Spies (admins) see both alliances' chat without being members.
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!delivered.contains(online.getUniqueId())
                    && plugin.getPermissionsManager().has(online, SPY_PERMISSION)) {
                ChatBlock.sendMessage(online, line);
            }
        }

        log(type, sender.getName(), message);
        return true;
    }

    // ----- file logging with retention -----

    private void log(@NotNull AllianceType type, @NotNull String playerName, @NotNull String message) {
        if (!plugin.getSettingsManager().is(ALLIANCE_CHAT_LOG_ENABLED)) {
            return;
        }
        File dir = new File(plugin.getDataFolder(), "alliance-logs");
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        File file = new File(dir, "alliance-chat-" + LocalDate.now() + ".log");
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(String.format("[%s] [%s] %s: %s%n", time, type.getId(), playerName, message));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not write alliance chat log", ex);
        }
    }

    private void pruneOldLogs() {
        if (!plugin.getSettingsManager().is(ALLIANCE_CHAT_LOG_ENABLED)) {
            return;
        }
        int retentionDays = plugin.getSettingsManager().getInt(ALLIANCE_CHAT_LOG_RETENTION_DAYS);
        File dir = new File(plugin.getDataFolder(), "alliance-logs");
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff && !file.delete()) {
                plugin.getLogger().warning("Could not delete old alliance chat log: " + file.getName());
            }
        }
    }
}
