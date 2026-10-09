package net.sacredlabyrinth.phaed.simpleclans;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;


public class TeleportState {

    private final OfflinePlayer offlinePlayer;
    private final Location origin;
    private final Location destination;
    private int counter;
    private final String clanName;
    private final String arrivalMessageKey;
    @Nullable
    private final BooleanSupplier stillValid;
    private boolean processing;

    public TeleportState(Player player, Location destination, String clanName, int counter) {
        this(player, destination, clanName, counter, "now.at.homebase");
    }

    public TeleportState(Player player, Location destination, String clanName, int counter,
                         String arrivalMessageKey) {
        this(player, destination, clanName, counter, arrivalMessageKey, null);
    }

    /**
     * @param stillValid checked when the countdown ends; the teleport is cancelled if it
     *                   returns false (e.g. a clan warp was turned off meanwhile)
     */
    public TeleportState(Player player, Location destination, String clanName, int counter,
                         String arrivalMessageKey, @Nullable BooleanSupplier stillValid) {
        this.offlinePlayer = player;
        this.destination = destination;
        this.origin = player.getLocation();
        this.clanName = clanName;
        this.counter = counter;
        this.arrivalMessageKey = arrivalMessageKey;
        this.stillValid = stillValid;
    }

    /**
     * @return false if the destination is no longer valid and the teleport should be cancelled
     */
    public boolean isStillValid() {
        return stillValid == null || stillValid.getAsBoolean();
    }

    /**
     * @return the lang key sent to the player on arrival; takes the clan name as {0}
     */
    public String getArrivalMessageKey() {
        return arrivalMessageKey;
    }


    public Location getLocation() {
        return this.origin;
    }

    public boolean isTeleportTime() {
        if (this.counter > 1) {
            this.counter--;
            return false;
        }

        return true;
    }

    /**
     * The player that is waiting for teleport
     *
     * @return the player
     */
    public @Nullable Player getPlayer() {
    	return offlinePlayer.getPlayer();
    }

    /**
     * Get seconds left before teleport
     *
     * @return the counter
     */
    public int getCounter() {
        return this.counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public String getClanName() {
        return this.clanName;
    }

    public Location getDestination() {
        return this.destination;
    }

    public boolean isProcessing() {
        return this.processing;
    }

    public void setProcessing(boolean processing) {
        this.processing = processing;
    }

}
