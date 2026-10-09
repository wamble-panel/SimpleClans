package net.sacredlabyrinth.phaed.simpleclans.ui.frames;

import com.cryptomorin.xseries.XMaterial;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.RankPermission;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryController;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponent;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponentImpl;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import net.sacredlabyrinth.phaed.simpleclans.utils.CurrencyFormat;
import net.sacredlabyrinth.phaed.simpleclans.utils.Paginator;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.ECONOMY_PURCHASE_WARP_SET;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.ECONOMY_WARP_SET_PRICE;

/**
 * Lists every clan with an active warp; clicking one teleports there. The top row also
 * holds a control for the viewer's own clan warp (toggle on/off, set it here).
 */
public class WarpsFrame extends SCFrame {

    private final SimpleClans plugin = SimpleClans.getInstance();
    // Refilled on every render so the list is current after a toggle or set.
    private final List<Clan> warps = new ArrayList<>();
    private final Paginator paginator = new Paginator(getSize() - 9, warps);

    public WarpsFrame(@Nullable SCFrame parent, @NotNull Player viewer) {
        super(parent, viewer);
    }

    @Override
    public void createComponents() {
        warps.clear();
        for (Clan clan : plugin.getClanManager().getClans()) {
            if (clan.hasActiveWarp()) {
                warps.add(clan);
            }
        }
        warps.sort(Comparator.comparing(Clan::getTag));
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
        addOwnWarpControl();
        add(Components.getPreviousPageComponent(7, this::previousPage, paginator, getViewer()));
        add(Components.getNextPageComponent(8, this::nextPage, paginator, getViewer()));

        if (warps.isEmpty()) {
            add(new SCComponentImpl.Builder(XMaterial.BARRIER)
                    .withDisplayName(lang("gui.warps.empty", getViewer()))
                    .withSlot(31)
                    .build());
            return;
        }

        int slot = 9;
        for (int i = paginator.getMinIndex(); paginator.isValidIndex(i); i++) {
            Clan clan = warps.get(i);
            Location warp = clan.getWarpLocation();
            if (warp == null) {
                continue;
            }
            ItemStack icon = clan.getBanner() != null ? clan.getBanner() : XMaterial.WHITE_BANNER.parseItem();
            SCComponent c = new SCComponentImpl(
                    plugin.getAllianceManager().getSymbolPrefix(clan)
                            + lang("gui.clanlist.clan.title", getViewer(), clan.getColorTag(), clan.getName()),
                    Arrays.asList(
                            // Coordinates as strings so MessageFormat doesn't add thousands separators.
                            lang("gui.warps.clan.lore.location", getViewer(), warp.getWorld().getName(),
                                    String.valueOf(warp.getBlockX()), String.valueOf(warp.getBlockY()),
                                    String.valueOf(warp.getBlockZ())),
                            lang("gui.clanlist.clan.lore.members", getViewer(), clan.getMembers().size()),
                            lang("gui.warps.clan.lore.click", getViewer())),
                    icon, slot);
            String tag = clan.getTag();
            c.setListener(ClickType.LEFT, () -> InventoryController.runSubcommand(getViewer(), "warp", false, tag));
            add(c);
            slot++;
        }
    }

    /**
     * Status + controls for the viewer's own clan warp. Only shown to clan members; the
     * click permissions are enforced by the component, and the commands re-check them.
     */
    private void addOwnWarpControl() {
        Clan own = plugin.getClanManager().getClanByPlayerUniqueId(getViewer().getUniqueId());
        if (own == null) {
            add(Components.getPanelComponent(4));
            return;
        }
        String status;
        if (!own.hasWarpSet()) {
            status = lang("gui.warps.own.status.unset", getViewer());
        } else if (own.isWarpEnabled()) {
            status = lang("gui.warps.own.status.on", getViewer());
        } else {
            status = lang("gui.warps.own.status.off", getViewer());
        }
        String price = plugin.getSettingsManager().is(ECONOMY_PURCHASE_WARP_SET)
                ? CurrencyFormat.format(plugin.getSettingsManager().getDouble(ECONOMY_WARP_SET_PRICE))
                : lang("gui.warps.own.free", getViewer());

        SCComponent control = new SCComponentImpl(lang("gui.warps.own.title", getViewer()),
                Arrays.asList(status,
                        lang("gui.warps.own.lore.toggle", getViewer()),
                        lang("gui.warps.own.lore.set", getViewer(), price)),
                XMaterial.ENDER_EYE, 4);
        control.setListener(ClickType.LEFT, () -> InventoryController.runSubcommand(getViewer(), "togglewarp", true));
        control.setPermission(ClickType.LEFT, RankPermission.WARP_TOGGLE);
        control.setVerifiedOnly(ClickType.LEFT);
        control.setListener(ClickType.RIGHT, () -> InventoryController.runSubcommand(getViewer(), "setwarp", true));
        control.setPermission(ClickType.RIGHT, RankPermission.WARP_SET);
        control.setVerifiedOnly(ClickType.RIGHT);
        control.setConfirmationRequired(ClickType.RIGHT);
        add(control);
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
        return lang("gui.warps.title", getViewer());
    }

    @Override
    public int getSize() {
        return 6 * 9;
    }
}
