package net.sacredlabyrinth.phaed.simpleclans.ui.frames;

import com.cryptomorin.xseries.XMaterial;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.Rank;
import net.sacredlabyrinth.phaed.simpleclans.RankPermission;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryController;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponent;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponentImpl;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import net.sacredlabyrinth.phaed.simpleclans.utils.ChatUtils;
import net.sacredlabyrinth.phaed.simpleclans.utils.Paginator;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;

/**
 * Lets a leader pick which ranks don't pay clan tax. Click a rank to toggle it.
 */
public class TaxExemptRanksFrame extends SCFrame {

    private final List<Rank> ranks = new ArrayList<>();
    private final Paginator paginator = new Paginator(getSize() - 9, ranks);

    public TaxExemptRanksFrame(@Nullable SCFrame parent, @NotNull Player viewer) {
        super(parent, viewer);
    }

    @Override
    public void createComponents() {
        Clan clan = SimpleClans.getInstance().getClanManager().getClanByPlayerUniqueId(getViewer().getUniqueId());
        ranks.clear();
        if (clan != null) {
            ranks.addAll(clan.getRanks());
        }
        paginator.clampToLastPage();

        for (int slot = 0; slot < 9; slot++) {
            if ((slot == 0 && getParent() != null) || slot == 7 || slot == 8) {
                continue;
            }
            add(Components.getPanelComponent(slot));
        }
        if (getParent() != null) {
            add(Components.getBackComponent(getParent(), 0, getViewer()));
        }
        add(Components.getPreviousPageComponent(7, this::previousPage, paginator, getViewer()));
        add(Components.getNextPageComponent(8, this::nextPage, paginator, getViewer()));

        if (clan == null || ranks.isEmpty()) {
            add(new SCComponentImpl.Builder(XMaterial.BARRIER)
                    .withDisplayName(lang("gui.tax.ranks.none", getViewer()))
                    .withSlot(22)
                    .build());
            return;
        }

        List<String> exempt = clan.getTaxExemptRanks();
        int slot = 9;
        for (int i = paginator.getMinIndex(); paginator.isValidIndex(i); i++) {
            Rank rank = ranks.get(i);
            boolean isExempt = exempt.contains(rank.getName());
            String name = ChatUtils.parseColors(rank.getDisplayName() != null ? rank.getDisplayName() : rank.getName());
            SCComponent c = new SCComponentImpl(name,
                    Collections.singletonList(lang(isExempt ? "gui.tax.rank.exempt" : "gui.tax.rank.pays", getViewer())),
                    isExempt ? XMaterial.LIME_WOOL : XMaterial.RED_WOOL, slot);
            c.setListener(ClickType.LEFT, () -> InventoryController.runSubcommand(getViewer(), "tax exempt", true,
                    rank.getName()));
            c.setPermission(ClickType.LEFT, RankPermission.TAX_MANAGE);
            add(c);
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
        return lang("gui.tax.ranks.title", getViewer());
    }

    @Override
    public int getSize() {
        return 6 * 9;
    }
}
