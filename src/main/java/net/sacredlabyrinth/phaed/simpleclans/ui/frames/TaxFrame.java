package net.sacredlabyrinth.phaed.simpleclans.ui.frames;

import com.cryptomorin.xseries.XMaterial;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.RankPermission;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.TaxPeriod;
import net.sacredlabyrinth.phaed.simpleclans.conversation.RequestCanceller;
import net.sacredlabyrinth.phaed.simpleclans.conversation.SCConversation;
import net.sacredlabyrinth.phaed.simpleclans.conversation.TaxAmountPrompt;
import net.sacredlabyrinth.phaed.simpleclans.managers.TaxManager;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryController;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponent;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponentImpl;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import net.sacredlabyrinth.phaed.simpleclans.utils.CurrencyFormat;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static org.bukkit.ChatColor.RED;

/**
 * Clan tax menu. Members see their bill (click to pay), the clan's tax and their auto-pay
 * setting; members allowed to manage tax also get the leader controls on the bottom row.
 */
public class TaxFrame extends SCFrame {

    private final SimpleClans plugin = SimpleClans.getInstance();

    public TaxFrame(@Nullable SCFrame parent, @NotNull Player viewer) {
        super(parent, viewer);
    }

    @Override
    public void createComponents() {
        ClanPlayer cp = plugin.getClanManager().getClanPlayer(getViewer());
        Clan clan = cp != null ? cp.getClan() : null;
        if (clan != null) {
            if (getParent() != null) {
                add(Components.getBackComponent(getParent(), 0, getViewer()));
            }
            addBill(clan, cp);
            addClanTax(clan);
            addAutoPay(cp);
            if (plugin.getPermissionsManager().has(getViewer(), RankPermission.TAX_MANAGE, false)) {
                addLeaderControls(clan);
            }
        }
        for (int slot = 0; slot < getSize(); slot++) {
            if (getComponent(slot) == null) {
                add(Components.getPanelComponent(slot));
            }
        }
    }

    private void addBill(@NotNull Clan clan, @NotNull ClanPlayer cp) {
        double owed = cp.getTaxOwed();
        List<String> lore = new ArrayList<>();
        if (clan.isTaxExempt(cp)) {
            lore.add(lang("gui.tax.bill.exempt", getViewer()));
        }
        lore.add(lang("gui.tax.bill.owed", getViewer(), CurrencyFormat.format(owed)));
        if (owed > 0) {
            lore.add(lang("gui.tax.bill.click", getViewer()));
        }
        SCComponent bill = new SCComponentImpl(lang("gui.tax.bill.title", getViewer()), lore,
                owed > 0 ? XMaterial.GOLD_NUGGET : XMaterial.EMERALD, 11);
        if (owed > 0) {
            bill.setListener(ClickType.LEFT, () -> InventoryController.runSubcommand(getViewer(), "paytax", true));
            bill.setConfirmationRequired(ClickType.LEFT);
        }
        add(bill);
    }

    private void addClanTax(@NotNull Clan clan) {
        List<String> lore = new ArrayList<>();
        if (clan.getTaxAmount() <= 0) {
            lore.add(lang("gui.tax.clan.none", getViewer()));
        } else {
            lore.add(lang("gui.tax.clan.amount", getViewer(), CurrencyFormat.format(clan.getTaxAmount()),
                    lang(clan.getTaxPeriod().getLangKey(), getViewer())));
            lore.add(lang("gui.tax.clan.next", getViewer(), TaxManager.formatDate(clan.getTaxNextDue())));
        }
        lore.add(lang("gui.tax.clan.bank", getViewer()));
        add(new SCComponentImpl(lang("gui.tax.clan.title", getViewer()), lore, XMaterial.BOOK, 13));
    }

    private void addAutoPay(@NotNull ClanPlayer cp) {
        boolean on = cp.isTaxAutoPay();
        SCComponent autoPay = new SCComponentImpl(lang("gui.tax.autopay.title", getViewer()),
                Arrays.asList(
                        lang(on ? "gui.tax.autopay.on" : "gui.tax.autopay.off", getViewer()),
                        lang("gui.tax.autopay.lore", getViewer())),
                on ? XMaterial.LIME_DYE : XMaterial.GRAY_DYE, 15);
        autoPay.setListener(ClickType.LEFT, () -> InventoryController.runSubcommand(getViewer(), "tax autopay", true));
        add(autoPay);
    }

    private void addLeaderControls(@NotNull Clan clan) {
        SCComponent amount = new SCComponentImpl(lang("gui.tax.amount.title", getViewer()),
                Arrays.asList(
                        lang("gui.tax.amount.current", getViewer(), CurrencyFormat.format(clan.getTaxAmount())),
                        lang("gui.tax.amount.click", getViewer())),
                XMaterial.GOLD_INGOT, 19);
        amount.setListener(ClickType.LEFT, this::askAmount);
        restrict(amount);
        add(amount);

        TaxPeriod current = clan.getTaxPeriod();
        TaxPeriod other = current == TaxPeriod.WEEKLY ? TaxPeriod.MONTHLY : TaxPeriod.WEEKLY;
        SCComponent period = new SCComponentImpl(lang("gui.tax.period.title", getViewer()),
                Arrays.asList(
                        lang("gui.tax.period.current", getViewer(), lang(current.getLangKey(), getViewer())),
                        lang("gui.tax.period.click", getViewer(), lang(other.getLangKey(), getViewer()))),
                XMaterial.CLOCK, 20);
        period.setListener(ClickType.LEFT, () -> InventoryController.runSubcommand(getViewer(), "tax period", true,
                other.name().toLowerCase()));
        restrict(period);
        add(period);

        TaxManager tax = plugin.getTaxManager();
        SCComponent debtors = new SCComponentImpl(lang("gui.tax.debtors.title", getViewer()),
                Arrays.asList(
                        lang("gui.tax.debtors.summary", getViewer(), String.valueOf(tax.getDebtors(clan).size()),
                                CurrencyFormat.format(tax.getTotalOwed(clan))),
                        lang("gui.tax.debtors.click", getViewer())),
                XMaterial.PAPER, 22);
        debtors.setListener(ClickType.LEFT, () -> InventoryDrawer.open(new RosterFrame(getViewer(), this, clan)));
        add(debtors);

        boolean trusted = clan.isTaxExemptTrusted();
        SCComponent exemptTrusted = new SCComponentImpl(lang("gui.tax.trusted.title", getViewer()),
                Arrays.asList(
                        lang(trusted ? "gui.tax.trusted.on" : "gui.tax.trusted.off", getViewer()),
                        lang("gui.tax.toggle.click", getViewer())),
                XMaterial.SHIELD, 24);
        exemptTrusted.setListener(ClickType.LEFT,
                () -> InventoryController.runSubcommand(getViewer(), "tax exempttrusted", true));
        restrict(exemptTrusted);
        add(exemptTrusted);

        List<String> ranks = clan.getTaxExemptRanks();
        SCComponent exemptRanks = new SCComponentImpl(lang("gui.tax.ranks.title", getViewer()),
                Arrays.asList(
                        lang("gui.tax.ranks.current", getViewer(),
                                ranks.isEmpty() ? "-" : String.join(", ", ranks)),
                        lang("gui.tax.ranks.click", getViewer())),
                XMaterial.NAME_TAG, 25);
        exemptRanks.setListener(ClickType.LEFT,
                () -> InventoryDrawer.open(new TaxExemptRanksFrame(this, getViewer())));
        add(exemptRanks);
    }

    private void restrict(@NotNull SCComponent component) {
        component.setPermission(ClickType.LEFT, RankPermission.TAX_MANAGE);
        component.setVerifiedOnly(ClickType.LEFT);
    }

    private void askAmount() {
        Player player = getViewer();
        player.closeInventory();
        SCConversation conversation = new SCConversation(plugin, player, new TaxAmountPrompt(), 60);
        conversation.addConversationCanceller(new RequestCanceller(player, RED + lang("tax.prompt.cancelled", player)));
        conversation.begin();
    }

    @Override
    public @NotNull String getTitle() {
        return lang("gui.tax.title", getViewer());
    }

    @Override
    public int getSize() {
        return 3 * 9;
    }
}
