package net.sacredlabyrinth.phaed.simpleclans.ui.frames;

import com.cryptomorin.xseries.XMaterial;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.alliance.Alliance;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceType;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponent;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponentImpl;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import net.sacredlabyrinth.phaed.simpleclans.utils.KDRFormat;
import net.sacredlabyrinth.phaed.simpleclans.utils.Paginator;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;

/**
 * Inventory GUI listing every clan currently in a NATO or SCO alliance.
 * Each clan row shows its banner (falling back to a white banner), member count,
 * and KDR. Left-clicking a clan opens its ClanDetailsFrame.
 */
public class AllianceMembersFrame extends SCFrame {

    private final AllianceType type;
    private final List<String> memberTags;
    private final Paginator paginator;

    public AllianceMembersFrame(@Nullable SCFrame parent, Player viewer, AllianceType type) {
        super(parent, viewer);
        this.type = type;
        Alliance alliance = SimpleClans.getInstance().getAllianceManager().getAlliance(type);
        this.memberTags = alliance.getMemberTags(); // already a defensive copy
        this.paginator = new Paginator(getSize() - 9, memberTags.size());
    }

    @Override
    public String getTitle() {
        return type.getColoredSymbol() + " " + type.getDisplayName()
                + ChatColor.DARK_GRAY + " (" + memberTags.size() + ")";
    }

    @Override
    public int getSize() {
        return 54;
    }

    @Override
    public void createComponents() {
        for (int slot = 0; slot < 9; slot++) {
            if ((slot == 0 && getParent() != null) || slot == 7 || slot == 8) continue;
            add(Components.getPanelComponent(slot));
        }
        if (getParent() != null) {
            add(Components.getBackComponent(getParent(), 0, getViewer()));
        }
        add(Components.getPreviousPageComponent(7, this::previousPage, paginator, getViewer()));
        add(Components.getNextPageComponent(8, this::nextPage, paginator, getViewer()));

        if (memberTags.isEmpty()) {
            add(new SCComponentImpl.Builder(XMaterial.BARRIER)
                    .withDisplayName(ChatColor.GRAY + lang("alliance.info.empty", getViewer()))
                    .withSlot(22)
                    .build());
            return;
        }

        ClanManager cm = SimpleClans.getInstance().getClanManager();
        int slot = 9;
        for (int i = paginator.getMinIndex(); paginator.isValidIndex(i); i++) {
            String tag = memberTags.get(i);
            Clan clan = cm.getClan(tag);

            String displayName;
            ItemStack icon;
            List<String> lore;

            if (clan != null) {
                displayName = type.getColoredSymbol() + " " + clan.getColorTag()
                        + ChatColor.WHITE + " " + clan.getName();
                icon = clan.getBanner() != null ? clan.getBanner() : XMaterial.WHITE_BANNER.parseItem();
                lore = Arrays.asList(
                        lang("gui.clanlist.clan.lore.members", getViewer(), clan.getMembers().size()),
                        lang("gui.clanlist.clan.lore.kdr", getViewer(), KDRFormat.format(clan.getTotalKDR()))
                );
            } else {
                displayName = ChatColor.GRAY.toString() + tag;
                icon = XMaterial.WHITE_BANNER.parseItem();
                lore = Collections.emptyList();
            }

            SCComponent c = new SCComponentImpl(displayName, lore, icon, slot);
            if (clan != null) {
                final Clan finalClan = clan;
                c.setListener(ClickType.LEFT,
                        () -> InventoryDrawer.open(new ClanDetailsFrame(this, getViewer(), finalClan)));
            }
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
}
