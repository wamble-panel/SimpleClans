package net.sacredlabyrinth.phaed.simpleclans.tasks;

import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.alliance.Alliance;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceType;
import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

/**
 * Runs every 12 hours and removes alliance members whose entire clan has been
 * inactive for {@code alliance.inactivity-days} consecutive days, as measured by
 * the most-recent {@code lastSeen} timestamp across all clan members.
 *
 * <p>Warnings are sent to online clan leaders at configurable day thresholds
 * ({@code alliance.inactivity-warning-days}) and are deduplicated using a per-clan
 * flag so leaders are not spammed across multiple task runs.</p>
 */
public class AllianceInactivityTask extends BukkitRunnable {

    private static final long TWELVE_HOURS_TICKS = 20L * 60 * 60 * 12;

    private final SimpleClans plugin;

    public AllianceInactivityTask(@NotNull SimpleClans plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // First run after 12 h; repeats every 12 h. Runs async, then bounces to main
        // thread for state mutations (clan removal, flags, etc.).
        runTaskTimerAsynchronously(plugin, TWELVE_HOURS_TICKS, TWELVE_HOURS_TICKS);
    }

    @Override
    public void run() {
        // Clan/world lookups must happen on the main thread.
        plugin.getServer().getScheduler().runTask(plugin, this::check);
    }

    private void check() {
        int removalDays = plugin.getSettingsManager().getInt(ALLIANCE_INACTIVITY_DAYS);
        List<String> warningDayStrings = plugin.getSettingsManager().getStringList(ALLIANCE_INACTIVITY_WARNING_DAYS);
        List<Integer> warningDays = parseIntList(warningDayStrings);

        long now = System.currentTimeMillis();
        long millisPerDay = 24L * 60 * 60 * 1000;

        for (AllianceType type : AllianceType.values()) {
            Alliance alliance = plugin.getAllianceManager().getAlliance(type);
            // Snapshot to avoid ConcurrentModification if removeMember is called below.
            List<String> members = alliance.getMemberTags();

            for (String tag : members) {
                Clan clan = plugin.getClanManager().getClan(tag);
                if (clan == null) {
                    continue;
                }

                long maxLastSeen = mostRecentSeen(clan);
                if (maxLastSeen == 0) {
                    continue;
                }

                long daysInactive = (now - maxLastSeen) / millisPerDay;

                if (daysInactive >= removalDays) {
                    plugin.getAllianceManager().removeMember(type, clan, true, true);
                    broadcastRemoval(type, clan.getName(), daysInactive);
                    dmRemovedLeaders(type, clan, daysInactive, removalDays);
                } else {
                    sendWarningsIfDue(type, clan, daysInactive, warningDays, removalDays);
                }
            }
        }
    }

    private long mostRecentSeen(@NotNull Clan clan) {
        long max = 0;
        for (ClanPlayer cp : clan.getAllMembers()) {
            long seen = cp.getLastSeen();
            if (seen > max) {
                max = seen;
            }
        }
        return max;
    }

    private void sendWarningsIfDue(@NotNull AllianceType type, @NotNull Clan clan, long daysInactive,
                                   @NotNull List<Integer> warningDays, int removalDays) {
        int alreadyWarned = clan.getInactivityWarnedThreshold(type.getId());
        int highestDue = alreadyWarned;
        for (int day : warningDays) {
            if (daysInactive >= day && day > alreadyWarned) {
                dmWarningLeaders(type, clan, daysInactive, removalDays);
                if (day > highestDue) {
                    highestDue = day;
                }
            }
        }
        if (highestDue > alreadyWarned) {
            clan.setInactivityWarnedThreshold(type.getId(), highestDue);
        }
    }

    private void broadcastRemoval(@NotNull AllianceType type, @NotNull String clanName, long daysInactive) {
        Alliance alliance = plugin.getAllianceManager().getAlliance(type);
        String message = SimpleClans.lang("alliance.inactivity.removed.broadcast",
                type.getDisplayName(), clanName, daysInactive);
        for (String tag : alliance.getMemberTags()) {
            Clan c = plugin.getClanManager().getClan(tag);
            if (c == null) {
                continue;
            }
            for (ClanPlayer cp : c.getOnlineMembers()) {
                if (cp.toPlayer() != null) {
                    ChatBlock.sendMessage(cp.toPlayer(), message);
                }
            }
        }
    }

    private void dmRemovedLeaders(@NotNull AllianceType type, @NotNull Clan clan, long daysInactive, int removalDays) {
        for (ClanPlayer leader : clan.getLeaders()) {
            if (leader.toPlayer() != null) {
                ChatBlock.sendMessageKey(leader.toPlayer(), "alliance.inactivity.removed.dm",
                        type.getDisplayName(), daysInactive, removalDays);
            }
        }
    }

    private void dmWarningLeaders(@NotNull AllianceType type, @NotNull Clan clan, long daysInactive, int removalDays) {
        for (ClanPlayer leader : clan.getLeaders()) {
            if (leader.toPlayer() != null) {
                ChatBlock.sendMessageKey(leader.toPlayer(), "alliance.inactivity.warning",
                        type.getDisplayName(), daysInactive, removalDays);
            }
        }
    }

    private static List<Integer> parseIntList(@NotNull List<String> raw) {
        List<Integer> out = new ArrayList<>();
        for (String s : raw) {
            try {
                out.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}
