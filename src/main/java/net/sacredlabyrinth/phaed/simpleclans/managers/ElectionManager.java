package net.sacredlabyrinth.phaed.simpleclans.managers;

import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

/**
 * Leader elections: when every leader of a clan has been offline for a while, the members
 * vote for a new leader. The most voted member becomes leader when the vote closes and the
 * inactive leaders are demoted. While the vote runs, those leaders can't kick members,
 * promote anyone or disband the clan, so a returning leader can't purge the voters.
 */
public final class ElectionManager {

    public enum VoteResult {
        SUCCESS, NO_ELECTION, NOT_ALLOWED, INVALID_CANDIDATE
    }

    private static final long HOUR_MILLIS = 60L * 60L * 1000L;

    private final SimpleClans plugin;

    public ElectionManager(@NotNull SimpleClans plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts and closes elections. Called periodically on the main thread.
     */
    public void tick() {
        boolean enabled = plugin.getSettingsManager().is(ELECTIONS_ENABLED);
        long now = System.currentTimeMillis();
        for (Clan clan : plugin.getClanManager().getClans()) {
            if (clan.isElectionActive()) {
                if (now >= clan.getElectionEnd()) {
                    finish(clan);
                }
            } else if (enabled && shouldStart(clan)) {
                start(clan);
            }
        }
    }

    private boolean shouldStart(@NotNull Clan clan) {
        List<ClanPlayer> leaders = clan.getLeaders();
        if (leaders.isEmpty() || clan.getNonLeaders().isEmpty()) {
            return false; // nobody to elect
        }
        int inactiveDays = plugin.getSettingsManager().getInt(ELECTIONS_LEADER_INACTIVE_DAYS);
        for (ClanPlayer leader : leaders) {
            if (isActive(leader, inactiveDays)) {
                return false;
            }
        }
        // A clan where nobody is active (abandoned) has no one to run an election for.
        return hasActiveVoter(clan, inactiveDays);
    }

    private boolean hasActiveVoter(@NotNull Clan clan, int inactiveDays) {
        for (ClanPlayer member : clan.getNonLeaders()) {
            if (isActive(member, inactiveDays)) {
                return true;
            }
        }
        return false;
    }

    private boolean isActive(@NotNull ClanPlayer cp, int inactiveDays) {
        return cp.toPlayer() != null || cp.getInactiveDays() < inactiveDays;
    }

    private void start(@NotNull Clan clan) {
        List<UUID> leaders = new ArrayList<>();
        for (ClanPlayer leader : clan.getLeaders()) {
            leaders.add(leader.getUniqueId());
        }
        clan.startElection(leaders, System.currentTimeMillis() + durationMillis());
        int days = plugin.getSettingsManager().getInt(ELECTIONS_LEADER_INACTIVE_DAYS);
        clan.addBb(lang("election.bb.started", days), false);
        for (ClanPlayer cp : clan.getOnlineMembers()) {
            Player player = cp.toPlayer();
            if (player != null && !leaders.contains(cp.getUniqueId())) {
                ChatBlock.sendMessageKey(player, "election.started", days, clanCommand(), timeLeft(clan));
            }
        }
    }

    private void finish(@NotNull Clan clan) {
        Set<UUID> oldLeaders = clan.getElectionLeaders();
        Map<UUID, Integer> tally = tally(clan);
        if (tally.isEmpty()) {
            // Nobody voted. If a leader came back meanwhile, the clan is fine as it is;
            // otherwise keep the vote open for another round.
            int inactiveDays = plugin.getSettingsManager().getInt(ELECTIONS_LEADER_INACTIVE_DAYS);
            for (ClanPlayer leader : clan.getLeaders()) {
                if (isActive(leader, inactiveDays)) {
                    clan.clearElection();
                    clan.addBb(lang("election.bb.cancelled"), false);
                    return;
                }
            }
            if (!hasActiveVoter(clan, inactiveDays)) {
                clan.clearElection(); // everyone left or went inactive; don't extend forever
                return;
            }
            clan.extendElection(System.currentTimeMillis() + durationMillis());
            clan.addBb(lang("election.bb.extended"), false);
            notifyMembers(clan, "election.extended", clanCommand(), timeLeft(clan));
            return;
        }

        ClanPlayer winner = pickWinner(clan, tally);
        if (winner == null) {
            clan.clearElection();
            return;
        }
        // Promote first so the clan always has a leader, then demote the inactive ones.
        if (!winner.isLeader()) {
            clan.promote(winner.getUniqueId());
        }
        for (UUID oldLeader : oldLeaders) {
            if (!oldLeader.equals(winner.getUniqueId()) && clan.isLeader(oldLeader)) {
                clan.demote(oldLeader);
            }
        }
        int votes = tally.get(winner.getUniqueId());
        clan.clearElection();
        clan.addBb(lang("election.bb.won", winner.getName(), votes), false);
        notifyMembers(clan, "election.won", winner.getName(), votes);
    }

    /**
     * @return candidate -> votes, counting only voters and candidates still eligible
     */
    @NotNull
    public Map<UUID, Integer> tally(@NotNull Clan clan) {
        Set<UUID> oldLeaders = clan.getElectionLeaders();
        Map<UUID, Integer> tally = new HashMap<>();
        for (Map.Entry<UUID, UUID> vote : clan.getElectionVotes().entrySet()) {
            UUID voter = vote.getKey();
            UUID candidate = vote.getValue();
            if (clan.isMember(voter) && clan.isMember(candidate)
                    && !oldLeaders.contains(voter) && !oldLeaders.contains(candidate)) {
                tally.merge(candidate, 1, Integer::sum);
            }
        }
        return tally;
    }

    /**
     * Most votes wins; a tie goes to the member who has been in the clan the longest.
     */
    @Nullable
    private ClanPlayer pickWinner(@NotNull Clan clan, @NotNull Map<UUID, Integer> tally) {
        ClanPlayer best = null;
        int bestVotes = -1;
        for (Map.Entry<UUID, Integer> entry : tally.entrySet()) {
            ClanPlayer cp = plugin.getClanManager().getAnyClanPlayer(entry.getKey());
            if (cp == null) {
                continue;
            }
            int votes = entry.getValue();
            if (best == null || votes > bestVotes
                    || (votes == bestVotes && joinedEarlier(cp, best))) {
                best = cp;
                bestVotes = votes;
            }
        }
        return best;
    }

    private static boolean joinedEarlier(@NotNull ClanPlayer a, @NotNull ClanPlayer b) {
        long ja = a.getJoinDate() > 0 ? a.getJoinDate() : Long.MAX_VALUE;
        long jb = b.getJoinDate() > 0 ? b.getJoinDate() : Long.MAX_VALUE;
        if (ja != jb) {
            return ja < jb;
        }
        return a.getName().compareToIgnoreCase(b.getName()) < 0;
    }

    @NotNull
    public VoteResult vote(@NotNull ClanPlayer voter, @NotNull ClanPlayer candidate) {
        Clan clan = voter.getClan();
        if (clan == null || !clan.isElectionActive()) {
            return VoteResult.NO_ELECTION;
        }
        Set<UUID> oldLeaders = clan.getElectionLeaders();
        if (oldLeaders.contains(voter.getUniqueId())) {
            return VoteResult.NOT_ALLOWED;
        }
        if (!clan.isMember(candidate.getUniqueId()) || oldLeaders.contains(candidate.getUniqueId())) {
            return VoteResult.INVALID_CANDIDATE;
        }
        clan.setElectionVote(voter.getUniqueId(), candidate.getUniqueId());
        return VoteResult.SUCCESS;
    }

    /**
     * @return true if this player is one of the inactive leaders an election is replacing.
     * Such leaders can't kick members, promote or disband until the vote is over.
     */
    public boolean isLockedLeader(@Nullable Clan clan, @NotNull UUID uuid) {
        return clan != null && clan.isElectionActive() && clan.getElectionLeaders().contains(uuid);
    }

    /**
     * @return candidates for the running election: every member except the leaders it replaces
     */
    @NotNull
    public List<ClanPlayer> getCandidates(@NotNull Clan clan) {
        Set<UUID> oldLeaders = clan.getElectionLeaders();
        List<ClanPlayer> candidates = new ArrayList<>();
        for (ClanPlayer cp : clan.getMembers()) {
            if (!oldLeaders.contains(cp.getUniqueId())) {
                candidates.add(cp);
            }
        }
        candidates.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return candidates;
    }

    /**
     * Login reminder while an election is running.
     */
    public void onJoin(@NotNull Player player, @NotNull ClanPlayer cp) {
        Clan clan = cp.getClan();
        if (clan == null || !clan.isElectionActive()) {
            return;
        }
        if (clan.getElectionLeaders().contains(cp.getUniqueId())) {
            ChatBlock.sendMessageKey(player, "election.locked.leader", timeLeft(clan));
            return;
        }
        UUID choice = clan.getElectionVotes().get(cp.getUniqueId());
        if (choice == null) {
            ChatBlock.sendMessageKey(player, "election.reminder", clanCommand(), timeLeft(clan));
        } else {
            ClanPlayer chosen = plugin.getClanManager().getAnyClanPlayer(choice);
            ChatBlock.sendMessageKey(player, "election.reminder.voted",
                    chosen != null ? chosen.getName() : "?", timeLeft(clan));
        }
    }

    /**
     * @return a short "1d 4h" / "3h 20m" style time until the election closes
     */
    @NotNull
    public String timeLeft(@NotNull Clan clan) {
        long millis = Math.max(0, clan.getElectionEnd() - System.currentTimeMillis());
        long minutes = millis / 60000L;
        long days = minutes / (24 * 60);
        long hours = (minutes / 60) % 24;
        long mins = minutes % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return Math.max(1, mins) + "m";
    }

    private long durationMillis() {
        return Math.max(1, plugin.getSettingsManager().getInt(ELECTIONS_DURATION_HOURS)) * HOUR_MILLIS;
    }

    private void notifyMembers(@NotNull Clan clan, @NotNull String key, Object... args) {
        for (ClanPlayer cp : clan.getOnlineMembers()) {
            Player player = cp.toPlayer();
            if (player != null) {
                ChatBlock.sendMessageKey(player, key, args);
            }
        }
    }

    @NotNull
    private String clanCommand() {
        return plugin.getSettingsManager().getString(COMMANDS_CLAN);
    }
}
