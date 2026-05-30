package net.sacredlabyrinth.phaed.simpleclans.alliance;

import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

/**
 * Owns the weekly meeting lifecycle, proposals, voting, and vote resolution — all
 * strictly scoped per {@link AllianceType}.
 *
 * <h2>Isolation</h2>
 * NATO and SCO keep entirely separate {@link MeetingState} and proposal lists in
 * per-type maps. A single scheduler tick iterates the types independently, so the
 * two meetings can run simultaneously without ever sharing state.
 *
 * <h2>Scheduling</h2>
 * Rather than computing a precise delay to a future instant (which breaks across
 * restarts and DST), a once-a-minute tick compares the wall clock in the configured
 * {@link ZoneId} to the scheduled day/time. This makes restart-resume trivial and
 * means server downtime never "fires" a missed meeting incorrectly.
 */
public class AllianceMeetingManager {

    /** Outcome of a proposal submission, mapped to a message key by the command layer. */
    public enum SubmitResult {
        SUCCESS, INVALID_TARGET, TARGET_NOT_MEMBER, INVALID_NUMBER, TEXT_TOO_LONG, PROFANITY
    }

    /** Outcome of a vote, mapped to a message key by the command/GUI layer. */
    public enum VoteResult {
        SUCCESS, NO_MEETING, NOT_FOUND, NOT_MEMBER
    }

    private static final int CUSTOM_TEXT_MAX = 200;
    private static final int HISTORY_LIMIT = 50;

    private final SimpleClans plugin;
    private final Map<AllianceType, MeetingState> meetings = new EnumMap<>(AllianceType.class);
    // Active (PENDING / IN_MEETING) proposals per type.
    private final Map<AllianceType, List<Proposal>> proposals = new EnumMap<>(AllianceType.class);
    // Resolved proposals kept for /clan <alliance> history (bounded).
    private final Map<AllianceType, List<Proposal>> history = new EnumMap<>(AllianceType.class);

    public AllianceMeetingManager(@NotNull SimpleClans plugin) {
        this.plugin = plugin;
        for (AllianceType type : AllianceType.values()) {
            meetings.put(type, new MeetingState(type));
            proposals.put(type, new ArrayList<>());
            history.put(type, new ArrayList<>());
        }
    }

    /**
     * Loads persisted proposals, votes and meeting state, then starts the tick task.
     * Any meeting whose end time has already passed (server was down across it) is
     * finalized immediately; one still within its window resumes.
     */
    public void start() {
        plugin.getStorageManager().loadAllianceMeetingData(this);

        for (AllianceType type : AllianceType.values()) {
            MeetingState state = meetings.get(type);
            if (state.isActive()) {
                if (System.currentTimeMillis() >= state.getEndTime()) {
                    // Server was offline across the end — resolve it now.
                    endMeeting(type);
                } else {
                    state.setLastStartedMeeting(state.getStartTime());
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L * 5, 20L * 60);
    }

    // ----- accessors used by the storage loader -----

    @NotNull
    public MeetingState getMeetingState(@NotNull AllianceType type) {
        return meetings.get(type);
    }

    public void addLoadedProposal(@NotNull Proposal proposal) {
        Proposal.Status status = proposal.getStatus();
        if (status == Proposal.Status.PENDING || status == Proposal.Status.IN_MEETING) {
            proposals.get(proposal.getAlliance()).add(proposal);
        } else {
            history.get(proposal.getAlliance()).add(proposal);
        }
    }

    @Nullable
    public Proposal getProposalById(int id) {
        for (AllianceType type : AllianceType.values()) {
            for (Proposal p : proposals.get(type)) {
                if (p.getId() == id) {
                    return p;
                }
            }
            for (Proposal p : history.get(type)) {
                if (p.getId() == id) {
                    return p;
                }
            }
        }
        return null;
    }

    // ----- queries -----

    public boolean isMeetingActive(@NotNull AllianceType type) {
        return meetings.get(type).isActive();
    }

    /**
     * @return proposals currently locked into the active meeting (for the voting GUI)
     */
    @NotNull
    public List<Proposal> getMeetingProposals(@NotNull AllianceType type) {
        List<Proposal> out = new ArrayList<>();
        for (Proposal p : proposals.get(type)) {
            if (p.getStatus() == Proposal.Status.IN_MEETING) {
                out.add(p);
            }
        }
        return out;
    }

    @NotNull
    public List<Proposal> getPendingProposals(@NotNull AllianceType type) {
        List<Proposal> out = new ArrayList<>();
        for (Proposal p : proposals.get(type)) {
            if (p.getStatus() == Proposal.Status.PENDING) {
                out.add(p);
            }
        }
        return out;
    }

    @NotNull
    public List<Proposal> getHistory(@NotNull AllianceType type) {
        return new ArrayList<>(history.get(type));
    }

    @Nullable
    public String getNextMeetingFormatted(@NotNull AllianceType type) {
        ZonedDateTime now = ZonedDateTime.now(zone());
        ZonedDateTime next = nextStart(now);
        return next.format(DateTimeFormatter.ofPattern("EEEE, MMM d 'at' HH:mm", Locale.ENGLISH));
    }

    // ----- submission / withdrawal / voting -----

    /**
     * Submits a proposal as PENDING. It will be locked into the next meeting whose
     * proposal lock-out has not yet passed; otherwise it queues for the one after.
     */
    @NotNull
    public SubmitResult submit(@NotNull Clan clan, @NotNull AllianceType type, @NotNull ProposalType ptype,
                               @Nullable String targetName, int number, @Nullable String text) {
        String targetTag = null;
        if (ptype == ProposalType.REMOVE) {
            Clan target = plugin.getClanManager().getClan(targetName == null ? "" : targetName);
            if (target == null) {
                return SubmitResult.INVALID_TARGET;
            }
            if (!plugin.getAllianceManager().isInAlliance(target, type)) {
                return SubmitResult.TARGET_NOT_MEMBER;
            }
            targetTag = target.getTag();
        } else if (ptype == ProposalType.MAX_ALLIES) {
            int cap = plugin.getSettingsManager().getInt(ALLIANCE_WAR_MAX_ALLIES_CAP);
            if (number < 0 || number > cap) {
                return SubmitResult.INVALID_NUMBER;
            }
        } else { // CUSTOM
            if (text == null || text.length() > CUSTOM_TEXT_MAX) {
                return SubmitResult.TEXT_TOO_LONG;
            }
            if (isProfane(text)) {
                return SubmitResult.PROFANITY;
            }
        }

        int id = plugin.getStorageManager().getNextProposalId();
        ClanPlayer leader = firstOnlineLeader(clan);
        String proposerName = leader != null ? leader.getName() : clan.getTag();
        Proposal proposal = new Proposal(id, type, ptype, clan.getTag(), proposerName,
                System.currentTimeMillis(), targetTag, number, text, Proposal.Status.PENDING);
        proposals.get(type).add(proposal);
        plugin.getStorageManager().insertProposal(proposal);
        return SubmitResult.SUCCESS;
    }

    /**
     * Withdraws a PENDING proposal owned by the given clan, before it is locked into
     * a meeting.
     *
     * @return false if the proposal does not exist, is not owned by the clan, or has
     * already been locked into a meeting / resolved
     */
    public boolean withdraw(@NotNull Clan clan, @NotNull AllianceType type, int proposalId) {
        for (Proposal p : proposals.get(type)) {
            if (p.getId() == proposalId && p.getStatus() == Proposal.Status.PENDING
                    && p.getProposerTag().equals(clan.getTag())) {
                p.setStatus(Proposal.Status.WITHDRAWN);
                proposals.get(type).remove(p);
                addToHistory(type, p);
                plugin.getStorageManager().updateProposal(p);
                return true;
            }
        }
        return false;
    }

    /**
     * Casts (or changes) a clan's vote on a proposal in the active meeting.
     */
    @NotNull
    public VoteResult castVote(@NotNull Clan clan, @NotNull AllianceType type, int proposalId, boolean agree) {
        if (!meetings.get(type).isActive()) {
            return VoteResult.NO_MEETING;
        }
        if (!plugin.getAllianceManager().isInAlliance(clan, type)) {
            return VoteResult.NOT_MEMBER;
        }
        for (Proposal p : proposals.get(type)) {
            if (p.getId() == proposalId && p.getStatus() == Proposal.Status.IN_MEETING) {
                p.setVote(clan.getTag(), agree);
                plugin.getStorageManager().saveVote(p.getId(), clan.getTag(), agree);
                return VoteResult.SUCCESS;
            }
        }
        return VoteResult.NOT_FOUND;
    }

    // ----- admin actions -----

    public void adminTriggerMeeting(@NotNull AllianceType type) {
        long now = System.currentTimeMillis();
        long duration = plugin.getSettingsManager().getInt(ALLIANCE_MEETING_DURATION_MINUTES) * 60L * 1000L;
        startMeeting(type, now, now + duration);
    }

    public boolean adminSetHost(@NotNull AllianceType type, @NotNull Clan clan) {
        Alliance alliance = plugin.getAllianceManager().getAlliance(type);
        List<String> members = alliance.getMemberTags();
        int index = members.indexOf(clan.getTag());
        if (index < 0) {
            return false;
        }
        alliance.setRotationIndex(index);
        plugin.getStorageManager().saveAllianceState(type, alliance.getRotationIndex(), alliance.getMaxAlliesPerWar());
        return true;
    }

    // ----- meeting lifecycle -----

    private void tick() {
        ZoneId zone = zone();
        ZonedDateTime now = ZonedDateTime.now(zone);
        long durationMin = plugin.getSettingsManager().getInt(ALLIANCE_MEETING_DURATION_MINUTES);
        long leadMin = plugin.getSettingsManager().getInt(ALLIANCE_WARNING_LEAD_MINUTES);

        ZonedDateTime scheduled = lastScheduledStart(now);
        ZonedDateTime end = scheduled.plus(durationMin, ChronoUnit.MINUTES);
        long scheduledMillis = scheduled.toInstant().toEpochMilli();
        long endMillis = end.toInstant().toEpochMilli();
        boolean inWindow = !now.isBefore(scheduled) && now.isBefore(end);

        for (AllianceType type : AllianceType.values()) {
            MeetingState state = meetings.get(type);

            if (inWindow) {
                if (!state.isActive() && state.getLastStartedMeeting() != scheduledMillis) {
                    startMeeting(type, scheduledMillis, endMillis);
                }
            } else {
                if (state.isActive() && System.currentTimeMillis() >= state.getEndTime()) {
                    endMeeting(type);
                }
                // 10-minute (configurable) warning before the next meeting.
                ZonedDateTime next = nextStart(now);
                long nextMillis = next.toInstant().toEpochMilli();
                ZonedDateTime warnFrom = next.minus(leadMin, ChronoUnit.MINUTES);
                if (!now.isBefore(warnFrom) && now.isBefore(next) && state.getLastWarnedStart() != nextMillis) {
                    broadcastWarning(type);
                    state.setLastWarnedStart(nextMillis);
                }
            }
        }
    }

    private void startMeeting(@NotNull AllianceType type, long startMillis, long endMillis) {
        Alliance alliance = plugin.getAllianceManager().getAlliance(type);
        if (alliance.getSize() == 0) {
            // Nobody to meet; mark handled so we don't retry every tick this window.
            meetings.get(type).setLastStartedMeeting(startMillis);
            return;
        }

        String hostTag = resolveHost(type);
        MeetingState state = meetings.get(type);
        state.setActive(true);
        state.setStartTime(startMillis);
        state.setEndTime(endMillis);
        state.setHostTag(hostTag);
        state.setLastStartedMeeting(startMillis);

        // Lock in eligible PENDING proposals (created before the lock-out), FIFO, up to the cap.
        long lockoutMillis = plugin.getSettingsManager().getInt(ALLIANCE_PROPOSAL_LOCKOUT_MINUTES) * 60L * 1000L;
        int max = plugin.getSettingsManager().getInt(ALLIANCE_MAX_PROPOSALS_PER_MEETING);
        List<Proposal> eligible = new ArrayList<>();
        for (Proposal p : proposals.get(type)) {
            if (p.getStatus() == Proposal.Status.PENDING && p.getCreatedAt() <= startMillis - lockoutMillis) {
                eligible.add(p);
            }
        }
        eligible.sort(Comparator.comparingLong(Proposal::getCreatedAt));
        int locked = 0;
        for (Proposal p : eligible) {
            if (locked >= max) {
                break;
            }
            p.setStatus(Proposal.Status.IN_MEETING);
            plugin.getStorageManager().updateProposal(p);
            locked++;
        }

        plugin.getStorageManager().saveMeetingState(type, true, startMillis, endMillis, hostTag);

        String hostName = clanName(hostTag);
        broadcast(type, type.getColoredSymbol() + " " + SimpleClans.lang("alliance.meeting.started",
                type.getDisplayName(), hostName));
    }

    private void endMeeting(@NotNull AllianceType type) {
        Alliance alliance = plugin.getAllianceManager().getAlliance(type);
        List<Proposal> meetingProposals = getMeetingProposals(type);
        int memberCount = alliance.getSize();
        double quorumPercent = plugin.getSettingsManager().getInt(ALLIANCE_QUORUM_PERCENT);
        int quorumNeeded = (int) Math.ceil(memberCount * (quorumPercent / 100.0));

        List<String> resultLines = new ArrayList<>();
        for (Proposal p : meetingProposals) {
            int agree = p.getAgreeCount();
            int disagree = p.getDisagreeCount();
            int voters = p.getVoterCount();
            p.setFinalTally(agree, disagree);

            boolean quorumMet = voters > 0 && voters >= quorumNeeded;
            boolean passed = quorumMet && agree > disagree;
            p.setStatus(passed ? Proposal.Status.PASSED : Proposal.Status.FAILED);

            String mark = passed ? "&a✓" : "&c✗";
            String detail = !quorumMet ? " &7(" + agree + "-" + disagree + ", quorum not met)"
                    : " &7(" + agree + "-" + disagree + ")";
            resultLines.add("  " + mark + " &f" + p.describe() + detail);

            if (passed) {
                applyEffect(type, p);
            }
            plugin.getStorageManager().updateProposal(p);
            plugin.getStorageManager().deleteVotesForProposal(p.getId());
            proposals.get(type).remove(p);
            addToHistory(type, p);
        }

        // Advance the host rotation for next week and persist.
        if (alliance.getSize() > 0) {
            alliance.setRotationIndex((alliance.getRotationIndex() + 1) % alliance.getSize());
        }
        plugin.getStorageManager().saveAllianceState(type, alliance.getRotationIndex(), alliance.getMaxAlliesPerWar());

        MeetingState state = meetings.get(type);
        state.setActive(false);
        state.setHostTag(null);
        plugin.getStorageManager().saveMeetingState(type, false, 0, 0, null);

        broadcastResults(type, meetingProposals.size(), resultLines);
    }

    private void applyEffect(@NotNull AllianceType type, @NotNull Proposal p) {
        switch (p.getType()) {
            case REMOVE: {
                String targetTag = p.getTargetTag();
                if (targetTag == null) {
                    return;
                }
                Clan target = plugin.getClanManager().getClan(targetTag);
                if (target == null) {
                    return;
                }
                plugin.getAllianceManager().removeMember(type, target, true, true);
                discardClanParticipation(type, targetTag);
                ClanPlayer leader = firstOnlineLeader(target);
                if (leader != null && leader.toPlayer() != null) {
                    ChatBlock.sendMessageKey(leader.toPlayer(), "alliance.removed.dm", type.getDisplayName(),
                            plugin.getSettingsManager().getInt(ALLIANCE_REJOIN_COOLDOWN_DAYS));
                }
                break;
            }
            case MAX_ALLIES: {
                int cap = plugin.getSettingsManager().getInt(ALLIANCE_WAR_MAX_ALLIES_CAP);
                int value = Math.max(0, Math.min(cap, p.getNumberValue()));
                Alliance alliance = plugin.getAllianceManager().getAlliance(type);
                alliance.setMaxAlliesPerWar(value);
                plugin.getStorageManager().saveAllianceState(type, alliance.getRotationIndex(), value);
                break;
            }
            case CUSTOM:
            default:
                // No automated effect.
                break;
        }
    }

    /**
     * Discards a (removed) clan's votes and pending/in-meeting proposals across the alliance.
     */
    private void discardClanParticipation(@NotNull AllianceType type, @NotNull String clanTag) {
        for (Proposal p : new ArrayList<>(proposals.get(type))) {
            if (p.getVote(clanTag) != null) {
                p.removeVote(clanTag);
                plugin.getStorageManager().deleteVote(p.getId(), clanTag);
            }
            if (p.getProposerTag().equals(clanTag)
                    && (p.getStatus() == Proposal.Status.PENDING || p.getStatus() == Proposal.Status.IN_MEETING)) {
                p.setStatus(Proposal.Status.WITHDRAWN);
                plugin.getStorageManager().updateProposal(p);
                proposals.get(type).remove(p);
                addToHistory(type, p);
            }
        }
    }

    // ----- broadcasting -----

    private void broadcastWarning(@NotNull AllianceType type) {
        String hostName = clanName(resolveHost(type));
        broadcast(type, type.getColoredSymbol() + " " + SimpleClans.lang("alliance.meeting.warning",
                type.getDisplayName(),
                plugin.getSettingsManager().getInt(ALLIANCE_WARNING_LEAD_MINUTES), hostName));
    }

    private void broadcastResults(@NotNull AllianceType type, int count, @NotNull List<String> lines) {
        String date = ZonedDateTime.now(zone()).format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH));
        broadcast(type, type.getColoredSymbol() + " &f" + SimpleClans.lang("alliance.meeting.results.header",
                type.getDisplayName(), date));
        broadcast(type, "&7" + SimpleClans.lang("alliance.meeting.results.count", count));
        for (String line : lines) {
            broadcast(type, line);
        }
        broadcast(type, "&7" + SimpleClans.lang("alliance.meeting.results.next", getNextMeetingFormatted(type)));
    }

    private void broadcast(@NotNull AllianceType type, @NotNull String message) {
        Alliance alliance = plugin.getAllianceManager().getAlliance(type);
        for (String tag : alliance.getMemberTags()) {
            Clan clan = plugin.getClanManager().getClan(tag);
            if (clan == null) {
                continue;
            }
            for (ClanPlayer cp : clan.getOnlineMembers()) {
                ChatBlock.sendMessage(cp.toPlayer(), message);
            }
        }
    }

    // ----- helpers -----

    private void addToHistory(@NotNull AllianceType type, @NotNull Proposal p) {
        List<Proposal> list = history.get(type);
        list.add(p);
        while (list.size() > HISTORY_LIMIT) {
            list.remove(0);
        }
    }

    /**
     * Chooses the host clan tag for the upcoming/active meeting, skipping members
     * whose HQ is currently invalid (world unloaded/deleted), and broadcasting a
     * warning when a slot is skipped. Falls back to the rotation slot if none valid.
     */
    @Nullable
    private String resolveHost(@NotNull AllianceType type) {
        Alliance alliance = plugin.getAllianceManager().getAlliance(type);
        List<String> members = alliance.getMemberTags();
        if (members.isEmpty()) {
            return null;
        }
        int size = members.size();
        int start = Math.floorMod(alliance.getRotationIndex(), size);
        for (int i = 0; i < size; i++) {
            String tag = members.get((start + i) % size);
            Clan clan = plugin.getClanManager().getClan(tag);
            if (clan == null) {
                continue;
            }
            Location hq = clan.getAllianceHome(type.getId());
            if (hq != null && hq.getWorld() != null) {
                return tag;
            }
        }
        // No valid HQ — use the rotation slot anyway.
        return members.get(start);
    }

    @NotNull
    private String clanName(@Nullable String tag) {
        if (tag == null) {
            return "-";
        }
        Clan clan = plugin.getClanManager().getClan(tag);
        return clan != null ? clan.getName() : tag;
    }

    @Nullable
    private ClanPlayer firstOnlineLeader(@NotNull Clan clan) {
        List<ClanPlayer> leaders = clan.getLeaders();
        for (ClanPlayer leader : leaders) {
            if (leader.toPlayer() != null) {
                return leader;
            }
        }
        return leaders.isEmpty() ? null : leaders.get(0);
    }

    private boolean isProfane(@NotNull String text) {
        if (!plugin.getSettingsManager().is(ALLIANCE_CUSTOM_PROFANITY_FILTER)) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String word : plugin.getSettingsManager().getStringList(ALLIANCE_CUSTOM_BLACKLIST)) {
            if (!word.isEmpty() && lower.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    private ZoneId zone() {
        try {
            return ZoneId.of(plugin.getSettingsManager().getString(ALLIANCE_MEETING_TIMEZONE));
        } catch (Exception ex) {
            return ZoneId.systemDefault();
        }
    }

    @NotNull
    private DayOfWeek meetingDay() {
        try {
            return DayOfWeek.valueOf(plugin.getSettingsManager().getString(ALLIANCE_MEETING_DAY).toUpperCase(Locale.ENGLISH));
        } catch (Exception ex) {
            return DayOfWeek.SATURDAY;
        }
    }

    @NotNull
    private LocalTime meetingTime() {
        try {
            return LocalTime.parse(plugin.getSettingsManager().getString(ALLIANCE_MEETING_TIME));
        } catch (Exception ex) {
            return LocalTime.of(18, 0);
        }
    }

    /**
     * @return the most recent scheduled meeting start at or before {@code now}
     */
    @NotNull
    private ZonedDateTime lastScheduledStart(@NotNull ZonedDateTime now) {
        ZonedDateTime scheduled = now.with(TemporalAdjusters.previousOrSame(meetingDay()))
                .with(meetingTime()).truncatedTo(ChronoUnit.MINUTES);
        if (scheduled.isAfter(now)) {
            scheduled = scheduled.minusWeeks(1);
        }
        return scheduled;
    }

    /**
     * @return the next scheduled meeting start strictly after {@code now}
     */
    @NotNull
    private ZonedDateTime nextStart(@NotNull ZonedDateTime now) {
        ZonedDateTime scheduled = now.with(TemporalAdjusters.nextOrSame(meetingDay()))
                .with(meetingTime()).truncatedTo(ChronoUnit.MINUTES);
        if (!scheduled.isAfter(now)) {
            scheduled = scheduled.plusWeeks(1);
        }
        return scheduled;
    }
}
