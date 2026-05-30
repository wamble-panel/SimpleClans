package net.sacredlabyrinth.phaed.simpleclans.alliance;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A single proposal and its votes. One vote per member clan (keyed by clan tag);
 * the leader casts it and it can be changed until the meeting ends.
 *
 * <p>Lifecycle: {@code PENDING} → (locked into a meeting) {@code IN_MEETING} →
 * {@code PASSED}/{@code FAILED} at meeting end, or {@code WITHDRAWN} if pulled by
 * the proposer before lock-in.</p>
 */
public class Proposal {

    public enum Status {
        PENDING,
        IN_MEETING,
        PASSED,
        FAILED,
        WITHDRAWN
    }

    private final int id;
    private final AllianceType alliance;
    private final ProposalType type;
    private final String proposerTag;
    private final String proposerName;
    private final long createdAt;

    // Type-specific payload (only the relevant field is used).
    private final String targetTag;   // REMOVE
    private final int numberValue;    // MAX_ALLIES
    private final String text;        // CUSTOM

    private Status status;
    // clan tag -> agree(true)/disagree(false)
    private final Map<String, Boolean> votes = new HashMap<>();
    // Final tallies captured at resolution (used for history display).
    private int finalAgree;
    private int finalDisagree;

    public Proposal(int id, @NotNull AllianceType alliance, @NotNull ProposalType type,
                    @NotNull String proposerTag, @NotNull String proposerName, long createdAt,
                    @Nullable String targetTag, int numberValue, @Nullable String text,
                    @NotNull Status status) {
        this.id = id;
        this.alliance = alliance;
        this.type = type;
        this.proposerTag = proposerTag;
        this.proposerName = proposerName;
        this.createdAt = createdAt;
        this.targetTag = targetTag;
        this.numberValue = numberValue;
        this.text = text;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    @NotNull
    public AllianceType getAlliance() {
        return alliance;
    }

    @NotNull
    public ProposalType getType() {
        return type;
    }

    @NotNull
    public String getProposerTag() {
        return proposerTag;
    }

    @NotNull
    public String getProposerName() {
        return proposerName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public String getTargetTag() {
        return targetTag;
    }

    public int getNumberValue() {
        return numberValue;
    }

    @Nullable
    public String getText() {
        return text;
    }

    @NotNull
    public Status getStatus() {
        return status;
    }

    public void setStatus(@NotNull Status status) {
        this.status = status;
    }

    @NotNull
    public Map<String, Boolean> getVotes() {
        return votes;
    }

    /**
     * Records or changes a clan's vote.
     *
     * @param clanTag the voting clan's tag
     * @param agree   true for agree, false for disagree
     */
    public void setVote(@NotNull String clanTag, boolean agree) {
        votes.put(clanTag, agree);
    }

    public void removeVote(@NotNull String clanTag) {
        votes.remove(clanTag);
    }

    @Nullable
    public Boolean getVote(@NotNull String clanTag) {
        return votes.get(clanTag);
    }

    public int getAgreeCount() {
        int count = 0;
        for (Boolean v : votes.values()) {
            if (Boolean.TRUE.equals(v)) {
                count++;
            }
        }
        return count;
    }

    public int getDisagreeCount() {
        int count = 0;
        for (Boolean v : votes.values()) {
            if (Boolean.FALSE.equals(v)) {
                count++;
            }
        }
        return count;
    }

    public int getVoterCount() {
        return votes.size();
    }

    public int getFinalAgree() {
        return finalAgree;
    }

    public int getFinalDisagree() {
        return finalDisagree;
    }

    public void setFinalTally(int agree, int disagree) {
        this.finalAgree = agree;
        this.finalDisagree = disagree;
    }

    /**
     * @return a short human-readable label for broadcasts/history (no color codes)
     */
    @NotNull
    public String describe() {
        switch (type) {
            case REMOVE:
                return "Remove " + (targetTag != null ? targetTag : "?");
            case MAX_ALLIES:
                return "Change max war allies to " + numberValue;
            case CUSTOM:
            default:
                return "Custom: \"" + (text != null ? text : "") + "\"";
        }
    }
}
