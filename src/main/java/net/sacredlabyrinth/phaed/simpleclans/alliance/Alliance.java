package net.sacredlabyrinth.phaed.simpleclans.alliance;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory representation of a single alliance instance (one per {@link AllianceType}).
 *
 * <p>Holds the ordered membership (in join order, which drives weekly host rotation),
 * the persisted rotation index, and the alliance-scoped max-allies-per-war value.
 * All mutation is performed through {@link AllianceManager} on the main thread; the
 * member list is guarded so the read paths used by the PvP/display hooks are safe.</p>
 */
public class Alliance {

    private final AllianceType type;
    // Ordered by join order. Stored as clan tags (clean tags) so the entity never
    // holds stale Clan references across disbands/reloads.
    private final List<String> memberTags = new ArrayList<>();
    private int rotationIndex;
    private int maxAlliesPerWar;

    public Alliance(@NotNull AllianceType type, int maxAlliesPerWar) {
        this.type = type;
        this.maxAlliesPerWar = maxAlliesPerWar;
    }

    @NotNull
    public AllianceType getType() {
        return type;
    }

    /**
     * @return an unmodifiable snapshot of member clan tags, in join order
     */
    @NotNull
    public synchronized List<String> getMemberTags() {
        return Collections.unmodifiableList(new ArrayList<>(memberTags));
    }

    public synchronized int getSize() {
        return memberTags.size();
    }

    public synchronized boolean isMember(@NotNull String clanTag) {
        return memberTags.contains(clanTag);
    }

    /**
     * Appends a member at the end of the join-order list.
     *
     * @param clanTag the clean clan tag
     * @return false if the clan was already a member
     */
    public synchronized boolean addMember(@NotNull String clanTag) {
        if (memberTags.contains(clanTag)) {
            return false;
        }
        return memberTags.add(clanTag);
    }

    /**
     * Removes a member, keeping the rotation index pointing at a valid slot.
     *
     * @param clanTag the clean clan tag
     * @return false if the clan was not a member
     */
    public synchronized boolean removeMember(@NotNull String clanTag) {
        int index = memberTags.indexOf(clanTag);
        if (index < 0) {
            return false;
        }
        memberTags.remove(index);
        // Keep rotation pointing at the same logical "next host" slot: if a member
        // before (or at) the current pointer left, shift the pointer back one.
        if (!memberTags.isEmpty()) {
            if (index < rotationIndex) {
                rotationIndex--;
            }
            rotationIndex = Math.floorMod(rotationIndex, memberTags.size());
        } else {
            rotationIndex = 0;
        }
        return true;
    }

    /**
     * Replaces the entire membership (used when loading from storage).
     *
     * @param tags ordered clan tags
     */
    public synchronized void setMemberTags(@NotNull List<String> tags) {
        memberTags.clear();
        memberTags.addAll(tags);
    }

    /**
     * @return the clan tag of the clan hosting this week, or null if the alliance is empty
     */
    public synchronized String getCurrentHostTag() {
        if (memberTags.isEmpty()) {
            return null;
        }
        return memberTags.get(Math.floorMod(rotationIndex, memberTags.size()));
    }

    public synchronized int getRotationIndex() {
        return rotationIndex;
    }

    public synchronized void setRotationIndex(int rotationIndex) {
        this.rotationIndex = rotationIndex;
    }

    public synchronized int getMaxAlliesPerWar() {
        return maxAlliesPerWar;
    }

    public synchronized void setMaxAlliesPerWar(int maxAlliesPerWar) {
        this.maxAlliesPerWar = maxAlliesPerWar;
    }
}
