package net.sacredlabyrinth.phaed.simpleclans.alliance;

import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.EconomyResponse;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.events.ClanBalanceUpdateEvent;
import net.sacredlabyrinth.phaed.simpleclans.loggers.BankOperator;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

/**
 * Central, alliance-scoped manager for the NATO / SCO system.
 *
 * <h2>Isolation guarantees</h2>
 * <ol>
 *   <li>Every state-touching method takes an {@link AllianceType}; there is no
 *       global "current alliance". NATO and SCO are separate {@link Alliance}
 *       instances held in an {@link EnumMap}, so a NATO mutation can never reach
 *       SCO state.</li>
 *   <li>Membership lookups ({@link #getAllianceType(Clan)}) are scoped per type.</li>
 *   <li>The PvP and display hooks read only through these per-type lookups.</li>
 * </ol>
 *
 * <p>All mutations run on the main thread (command handlers / event handlers) and
 * persist synchronously through the {@code StorageManager}. Capacity is enforced
 * under a per-{@link Alliance} lock so two simultaneous joins cannot exceed the cap.</p>
 */
public class AllianceManager {

    /**
     * Outcome of a join attempt, mapped to a message key by the command layer.
     */
    public enum JoinResult {
        SUCCESS,
        ALREADY_MEMBER,
        IN_RIVAL_ALLIANCE,
        NO_HQ,
        FULL,
        ON_COOLDOWN,
        NOT_ENOUGH_MONEY
    }

    private final SimpleClans plugin;
    private final Map<AllianceType, Alliance> alliances = new EnumMap<>(AllianceType.class);

    public AllianceManager(@NotNull SimpleClans plugin) {
        this.plugin = plugin;
        int defaultMaxAllies = plugin.getSettingsManager().getInt(ALLIANCE_WAR_DEFAULT_MAX_ALLIES);
        for (AllianceType type : AllianceType.values()) {
            alliances.put(type, new Alliance(type, defaultMaxAllies));
        }
    }

    /**
     * Loads persisted alliance state from storage. Called once after the
     * StorageManager and ClanManager are ready.
     */
    public void load() {
        plugin.getStorageManager().loadAlliances(this);
    }

    @NotNull
    public Alliance getAlliance(@NotNull AllianceType type) {
        return alliances.get(type);
    }

    /**
     * @param clan the clan (nullable for convenience)
     * @return the alliance this clan belongs to, or null if it is in none
     */
    @Nullable
    public AllianceType getAllianceType(@Nullable Clan clan) {
        if (clan == null) {
            return null;
        }
        String tag = clan.getTag();
        for (AllianceType type : AllianceType.values()) {
            if (alliances.get(type).isMember(tag)) {
                return type;
            }
        }
        return null;
    }

    public boolean isInAlliance(@NotNull Clan clan, @NotNull AllianceType type) {
        return alliances.get(type).isMember(clan.getTag());
    }

    /**
     * Sets a clan's HQ for the given alliance to the supplied location. Required
     * before the clan may join. HQ persists in the clan's flags, keyed per alliance.
     *
     * @return true (always succeeds for a non-null location)
     */
    public boolean setHq(@NotNull Clan clan, @NotNull AllianceType type, @NotNull Location location) {
        clan.setAllianceHome(type.getId(), location);
        return true;
    }

    @Nullable
    public Location getHq(@NotNull Clan clan, @NotNull AllianceType type) {
        return clan.getAllianceHome(type.getId());
    }

    /**
     * Attempts to join {@code clan} to the alliance, charging the joining fee from
     * the clan bank. Enforces, in order: not-already-member, rival exclusion,
     * HQ-set, rejoin cooldown, capacity, and payment. Capacity + insertion are
     * performed under the alliance lock so concurrent joins cannot exceed the cap.
     *
     * @param payer the player triggering the join (used only as the bank operator for logging)
     * @param clan  the joining clan
     * @param type  the target alliance
     * @return the outcome
     */
    @NotNull
    public JoinResult join(@NotNull Player payer, @NotNull Clan clan, @NotNull AllianceType type) {
        Alliance alliance = alliances.get(type);
        synchronized (alliance) {
            if (alliance.isMember(clan.getTag())) {
                return JoinResult.ALREADY_MEMBER;
            }
            // A clan cannot be in both NATO and SCO at once — hard block.
            if (getAllianceType(clan) != null) {
                return JoinResult.IN_RIVAL_ALLIANCE;
            }
            if (getHq(clan, type) == null) {
                return JoinResult.NO_HQ;
            }
            if (System.currentTimeMillis() < clan.getAllianceRejoinCooldown(type.getId())) {
                return JoinResult.ON_COOLDOWN;
            }
            int max = plugin.getSettingsManager().getInt(ALLIANCE_MAX_MEMBERS);
            if (alliance.getSize() >= max) {
                return JoinResult.FULL;
            }

            double fee = plugin.getSettingsManager().getDouble(ALLIANCE_JOINING_FEE);
            if (fee > 0) {
                BankOperator operator = new BankOperator(payer, 0);
                EconomyResponse response = clan.withdraw(operator, ClanBalanceUpdateEvent.Cause.COMMAND, fee);
                if (response != EconomyResponse.SUCCESS) {
                    return JoinResult.NOT_ENOUGH_MONEY;
                }
            }

            int order = plugin.getStorageManager().getNextAllianceJoinOrder(type);
            alliance.addMember(clan.getTag());
            plugin.getStorageManager().insertAllianceMember(type, clan.getTag(), order, System.currentTimeMillis());
            plugin.getStorageManager().saveAllianceState(type, alliance.getRotationIndex(), alliance.getMaxAlliesPerWar());
            return JoinResult.SUCCESS;
        }
    }

    /**
     * Voluntary leave. No refund. The clan keeps its HQ and is not put on cooldown.
     *
     * @return false if the clan was not a member of that alliance
     */
    public boolean leave(@NotNull Clan clan, @NotNull AllianceType type) {
        return removeMember(type, clan, false, false);
    }

    /**
     * Removes a clan from an alliance, freeing its seat and shifting the host rotation.
     *
     * @param type          the alliance
     * @param clan          the clan to remove
     * @param applyCooldown whether to apply the rejoin cooldown (forced removals)
     * @param wipeHq        whether to clear the clan's HQ (forced removals)
     * @return false if the clan was not a member
     */
    public boolean removeMember(@NotNull AllianceType type, @NotNull Clan clan, boolean applyCooldown, boolean wipeHq) {
        Alliance alliance = alliances.get(type);
        synchronized (alliance) {
            if (!alliance.removeMember(clan.getTag())) {
                return false;
            }
            plugin.getStorageManager().deleteAllianceMember(type, clan.getTag());
            plugin.getStorageManager().saveAllianceState(type, alliance.getRotationIndex(), alliance.getMaxAlliesPerWar());
        }
        if (applyCooldown) {
            int days = plugin.getSettingsManager().getInt(ALLIANCE_REJOIN_COOLDOWN_DAYS);
            clan.setAllianceRejoinCooldown(type.getId(), System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L);
        }
        if (wipeHq) {
            clan.setAllianceHome(type.getId(), null);
        }
        // Members lose alliance-chat access when their clan leaves/is removed.
        if (plugin.getAllianceChatManager() != null) {
            plugin.getAllianceChatManager().clearClanAccess(clan);
        }
        return true;
    }

    /**
     * Frees a disbanded clan's seat in whichever alliance it belonged to. Called
     * from the disband listener. No cooldown (the clan no longer exists).
     */
    public void handleClanDisband(@NotNull Clan clan) {
        AllianceType type = getAllianceType(clan);
        if (type != null) {
            removeMember(type, clan, false, false);
        }
    }

    /**
     * @return the colored alliance symbol followed by a space, or an empty string
     * if the clan is in no alliance. Used as a chat/display/tab prefix.
     */
    @NotNull
    public String getSymbolPrefix(@Nullable Clan clan) {
        AllianceType type = getAllianceType(clan);
        return type == null ? "" : type.getColoredSymbol() + " ";
    }
}
