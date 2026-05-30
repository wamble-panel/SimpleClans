package net.sacredlabyrinth.phaed.simpleclans.alliance;

import org.jetbrains.annotations.Nullable;

/**
 * The kinds of proposal a member clan leader can submit to a weekly meeting.
 */
public enum ProposalType {

    /** Remove a member clan from the alliance (HQ wiped, cooldown applied on pass). */
    REMOVE,
    /** Change the alliance-scoped max war allies (takes effect next war). */
    MAX_ALLIES,
    /** Free-form text proposal with no automated effect. */
    CUSTOM;

    @Nullable
    public static ProposalType fromString(@Nullable String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase();
        switch (normalized) {
            case "remove":
                return REMOVE;
            case "maxallies":
            case "max_allies":
                return MAX_ALLIES;
            case "custom":
                return CUSTOM;
            default:
                return null;
        }
    }
}
