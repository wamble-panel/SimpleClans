package net.sacredlabyrinth.phaed.simpleclans.alliance;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Live, per-alliance meeting state. Persisted (active/start/end/host) so a meeting
 * in progress survives a server restart and resumes on boot.
 *
 * <p>{@code lastStartedMeeting} and {@code lastWarnedStart} are scheduler bookkeeping
 * (epoch-millis of the meeting start they refer to) used to make the once-a-minute
 * tick idempotent — they prevent a meeting being started twice or a warning being
 * broadcast repeatedly within the same minute window.</p>
 */
public class MeetingState {

    private final AllianceType type;
    private boolean active;
    private long startTime;
    private long endTime;
    private String hostTag;

    // Scheduler bookkeeping (not persisted — safe to recompute after restart).
    private long lastStartedMeeting = -1;
    private long lastWarnedStart = -1;

    public MeetingState(@NotNull AllianceType type) {
        this.type = type;
    }

    @NotNull
    public AllianceType getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    @Nullable
    public String getHostTag() {
        return hostTag;
    }

    public void setHostTag(@Nullable String hostTag) {
        this.hostTag = hostTag;
    }

    public long getLastStartedMeeting() {
        return lastStartedMeeting;
    }

    public void setLastStartedMeeting(long lastStartedMeeting) {
        this.lastStartedMeeting = lastStartedMeeting;
    }

    public long getLastWarnedStart() {
        return lastWarnedStart;
    }

    public void setLastWarnedStart(long lastWarnedStart) {
        this.lastWarnedStart = lastWarnedStart;
    }
}
