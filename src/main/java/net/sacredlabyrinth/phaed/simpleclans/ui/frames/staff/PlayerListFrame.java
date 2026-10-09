package net.sacredlabyrinth.phaed.simpleclans.ui.frames.staff;

import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponent;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.Components;
import net.sacredlabyrinth.phaed.simpleclans.utils.Paginator;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;

public class PlayerListFrame extends SCFrame {

    private final boolean onlineOnly;
    private Paginator paginator;
    // UUIDs sorted by name; OfflinePlayers are only created for the visible page, since
    // OfflinePlayer#getName() can read playerdata from disk for uncached players.
    private final List<UUID> players = new ArrayList<>();

    public PlayerListFrame(@NotNull Player viewer, @Nullable SCFrame parent, boolean onlineOnly) {
        super(parent, viewer);
        this.onlineOnly = onlineOnly;
        loadPlayers();
    }

    @Override
    public void createComponents() {
        for (int slot = 0; slot < 9; slot++) {
            if (slot == 2 || slot == 6 || slot == 7)
                continue;
            add(Components.getPanelComponent(slot));
        }
        add(Components.getBackComponent(getParent(), 2, getViewer()));

        add(Components.getPreviousPageComponent(6, this::previousPage, paginator, getViewer()));
        add(Components.getNextPageComponent(7, this::nextPage, paginator, getViewer()));

        int slot = 9;
        for (int i = paginator.getMinIndex(); paginator.isValidIndex(i); i++) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(players.get(i));
            SCComponent c = Components.getPlayerComponent(this, getViewer(), player, slot, false);
            c.setListener(ClickType.LEFT, () -> InventoryDrawer.open(new PlayerDetailsFrame(getViewer(), this, player)));

            add(c);
            slot++;
        }
    }

    private void loadPlayers() {
        Map<UUID, String> names = new HashMap<>();
        if (!onlineOnly) {
            for (ClanPlayer cp : SimpleClans.getInstance().getClanManager().getAllClanPlayers()) {
                if (cp.getName() != null) {
                    names.put(cp.getUniqueId(), cp.getName());
                }
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.put(online.getUniqueId(), online.getName());
        }
        List<Map.Entry<UUID, String>> sorted = new ArrayList<>(names.entrySet());
        sorted.sort(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER));
        for (Map.Entry<UUID, String> entry : sorted) {
            players.add(entry.getKey());
        }
        paginator = new Paginator(getSize() - 9, players.size());
    }

    private void previousPage() {
        if (paginator.previousPage()) {
            updateFrame();
        }
    }

    private void nextPage() {
        if (paginator.nextPage()) {
            updateFrame();
        }
    }

    private void updateFrame() {
        InventoryDrawer.open(this);
    }

    @Override
    public @NotNull String getTitle() {
        return lang("gui.player.list.title", getViewer());
    }

    @Override
    public int getSize() {
        return 6 * 9;
    }

}
