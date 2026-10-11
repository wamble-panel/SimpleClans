package net.sacredlabyrinth.phaed.simpleclans.ui.frames;

import com.cryptomorin.xseries.XMaterial;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.managers.ElectionManager;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryController;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponent;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponentImpl;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import net.sacredlabyrinth.phaed.simpleclans.utils.Paginator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;

/**
 * Leader election ballot: every candidate as a player head with their current votes.
 * Click a head to vote for that member (yourself included); your choice glows.
 */
public class ElectionFrame extends SCFrame {

    private final SimpleClans plugin = SimpleClans.getInstance();
    private final List<ClanPlayer> candidates = new ArrayList<>();
    private final Paginator paginator = new Paginator(getSize() - 9, candidates);

    public ElectionFrame(@Nullable SCFrame parent, @NotNull Player viewer) {
        super(parent, viewer);
    }

    @Override
    public void createComponents() {
        ElectionManager elections = plugin.getElectionManager();
        Clan clan = plugin.getClanManager().getClanByPlayerUniqueId(getViewer().getUniqueId());
        candidates.clear();
        if (clan != null && clan.isElectionActive()) {
            candidates.addAll(elections.getCandidates(clan));
        }
        paginator.clampToLastPage();

        for (int slot = 0; slot < 9; slot++) {
            if ((slot == 0 && getParent() != null) || slot == 4 || slot == 7 || slot == 8) {
                continue;
            }
            add(Components.getPanelComponent(slot));
        }
        if (getParent() != null) {
            add(Components.getBackComponent(getParent(), 0, getViewer()));
        }
        add(Components.getPreviousPageComponent(7, this::previousPage, paginator, getViewer()));
        add(Components.getNextPageComponent(8, this::nextPage, paginator, getViewer()));

        if (clan == null || !clan.isElectionActive()) {
            add(Components.getPanelComponent(4));
            add(new SCComponentImpl.Builder(XMaterial.BARRIER)
                    .withDisplayName(lang("gui.election.over", getViewer()))
                    .withSlot(22)
                    .build());
            return;
        }

        Map<UUID, Integer> tally = elections.tally(clan);
        UUID myChoice = clan.getElectionVotes().get(getViewer().getUniqueId());
        ClanPlayer chosen = myChoice != null ? plugin.getClanManager().getAnyClanPlayer(myChoice) : null;
        int totalVotes = 0;
        for (int votes : tally.values()) {
            totalVotes += votes;
        }
        add(new SCComponentImpl(lang("gui.election.info.title", getViewer()),
                Arrays.asList(
                        lang("gui.election.info.time", getViewer(), elections.timeLeft(clan)),
                        lang("gui.election.info.votes", getViewer(), String.valueOf(totalVotes)),
                        lang("gui.election.info.mine", getViewer(), chosen != null ? chosen.getName() : "-"),
                        lang("gui.election.info.rules", getViewer())),
                XMaterial.CLOCK, 4));

        boolean canVote = !clan.getElectionLeaders().contains(getViewer().getUniqueId());
        int slot = 9;
        for (int i = paginator.getMinIndex(); paginator.isValidIndex(i); i++) {
            ClanPlayer candidate = candidates.get(i);
            int votes = tally.getOrDefault(candidate.getUniqueId(), 0);
            boolean isMyChoice = candidate.getUniqueId().equals(myChoice);
            List<String> lore = new ArrayList<>();
            lore.add(lang("gui.election.candidate.votes", getViewer(), String.valueOf(votes)));
            if (isMyChoice) {
                lore.add(lang("gui.election.candidate.mine", getViewer()));
            } else if (canVote) {
                lore.add(lang("gui.election.candidate.click", getViewer()));
            }
            String title = lang(isMyChoice ? "gui.election.candidate.title.mine" : "gui.election.candidate.title",
                    getViewer(), candidate.getName());
            SCComponent head = new SCComponentImpl(title, lore, XMaterial.PLAYER_HEAD, slot);
            Components.setOwningPlayer(head.getItem(), Bukkit.getOfflinePlayer(candidate.getUniqueId()));
            if (!isMyChoice && canVote) {
                String name = candidate.getName();
                head.setListener(ClickType.LEFT, () -> InventoryController.runSubcommand(getViewer(), "vote", true, name));
            }
            add(head);
            slot++;
        }
    }

    private void previousPage() {
        if (paginator.previousPage()) {
            InventoryDrawer.open(this);
        }
    }

    private void nextPage() {
        if (paginator.nextPage()) {
            InventoryDrawer.open(this);
        }
    }

    @Override
    public @NotNull String getTitle() {
        return lang("gui.election.title", getViewer());
    }

    @Override
    public int getSize() {
        return 6 * 9;
    }
}
