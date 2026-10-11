package net.sacredlabyrinth.phaed.simpleclans.tasks;

import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Issues due clan tax bills and opens/closes leader elections. Runs on the main thread
 * every five minutes; each run only walks the in-memory clan list.
 */
public class ClanGovernanceTask extends BukkitRunnable {

    private static final long FIVE_MINUTES_TICKS = 20L * 60 * 5;

    private final SimpleClans plugin;

    public ClanGovernanceTask(@NotNull SimpleClans plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // First run one minute after startup, once all clans are loaded.
        runTaskTimer(plugin, 20L * 60, FIVE_MINUTES_TICKS);
    }

    @Override
    public void run() {
        try {
            plugin.getTaxManager().billDueClans();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error billing clan tax", ex);
        }
        try {
            plugin.getElectionManager().tick();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error updating clan elections", ex);
        }
    }
}
