package net.sacredlabyrinth.phaed.simpleclans.managers;

import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.EconomyResponse;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.TaxPeriod;
import net.sacredlabyrinth.phaed.simpleclans.events.ClanBalanceUpdateEvent;
import net.sacredlabyrinth.phaed.simpleclans.events.EconomyTransactionEvent;
import net.sacredlabyrinth.phaed.simpleclans.loggers.BankOperator;
import net.sacredlabyrinth.phaed.simpleclans.utils.CurrencyFormat;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

/**
 * Clan tax: leaders set a flat amount that is billed to every non-exempt member once per
 * period (weekly or monthly). Paying is optional; unpaid bills accumulate and members are
 * reminded on login. Payments go into the clan bank.
 */
public final class TaxManager {

    /**
     * Bills missed while the server was offline are caught up, but never more than this
     * many at once, so a long downtime can't produce a huge surprise bill.
     */
    private static final int MAX_CATCH_UP_PERIODS = 4;

    public enum Status {
        SUCCESS, NOTHING_OWED, NO_ECONOMY, NOT_ENOUGH_MONEY, FAILED
    }

    public static final class Payment {
        private final Status status;
        private final double amount;

        private Payment(@NotNull Status status, double amount) {
            this.status = status;
            this.amount = amount;
        }

        @NotNull
        public Status getStatus() {
            return status;
        }

        /**
         * @return the amount paid, or the amount that was needed when the player couldn't pay
         */
        public double getAmount() {
            return amount;
        }
    }

    private final SimpleClans plugin;

    public TaxManager(@NotNull SimpleClans plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getSettingsManager().is(TAX_ENABLED) && plugin.getPermissionsManager().hasEconomy();
    }

    /**
     * Issues every bill that is due. Called periodically on the main thread.
     */
    public void billDueClans() {
        if (!isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Clan clan : plugin.getClanManager().getClans()) {
            double amount = clan.getTaxAmount();
            if (amount <= 0) {
                continue;
            }
            TaxPeriod period = clan.getTaxPeriod();
            long due = clan.getTaxNextDue();
            if (due <= 0) {
                clan.setTaxNextDue(period.next(now));
                continue;
            }
            int periods = 0;
            while (due <= now && periods < MAX_CATCH_UP_PERIODS) {
                periods++;
                due = period.next(due);
            }
            if (periods == 0) {
                continue;
            }
            if (due <= now) {
                due = period.next(now); // far behind (long downtime): resync to now
            }
            clan.setTaxNextDue(due);
            bill(clan, amount * periods);
        }
    }

    private void bill(@NotNull Clan clan, double amount) {
        String command = clanCommand();
        for (ClanPlayer cp : clan.getMembers()) {
            if (clan.isTaxExempt(cp)) {
                continue;
            }
            cp.setTaxOwed(cp.getTaxOwed() + amount);
            Player player = cp.toPlayer();
            if (cp.isTaxAutoPay()) {
                Payment payment = pay(cp, null);
                if (payment.getStatus() == Status.SUCCESS) {
                    if (player != null) {
                        ChatBlock.sendMessageKey(player, "tax.autopaid", CurrencyFormat.format(payment.getAmount()));
                    }
                    continue;
                }
            }
            if (player != null) {
                ChatBlock.sendMessageKey(player, "tax.billed", CurrencyFormat.format(amount),
                        CurrencyFormat.format(cp.getTaxOwed()), command);
            }
        }
        clan.addBb(lang("tax.bb.billed", CurrencyFormat.format(amount)), false);
    }

    /**
     * Pays tax from the player's balance into their clan's bank.
     *
     * @param requested how much to pay, or null to pay the whole bill
     */
    @NotNull
    public Payment pay(@NotNull ClanPlayer cp, @Nullable Double requested) {
        Clan clan = cp.getClan();
        double owed = cp.getTaxOwed();
        if (clan == null || owed <= 0) {
            return new Payment(Status.NOTHING_OWED, 0);
        }
        PermissionsManager pm = plugin.getPermissionsManager();
        if (!pm.hasEconomy()) {
            return new Payment(Status.NO_ECONOMY, 0);
        }
        double amount = requested == null ? owed : Math.min(requested, owed);
        if (amount <= 0) {
            return new Payment(Status.NOTHING_OWED, 0);
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(cp.getUniqueId());
        if (!pm.playerHasMoney(player, amount)) {
            return new Payment(Status.NOT_ENOUGH_MONEY, amount);
        }
        if (!pm.chargePlayer(player, amount, EconomyTransactionEvent.Cause.CLAN_TAX)) {
            return new Payment(Status.FAILED, amount);
        }
        EconomyResponse response = clan.deposit(new BankOperator(cp, pm.playerGetMoney(player)),
                ClanBalanceUpdateEvent.Cause.TAX, amount);
        if (response != EconomyResponse.SUCCESS) {
            pm.grantPlayer(player, amount); // the bank refused it; give the money back
            return new Payment(Status.FAILED, amount);
        }
        cp.setTaxOwed(owed - amount);
        return new Payment(Status.SUCCESS, amount);
    }

    /**
     * Login reminder (and auto-pay) for players with an outstanding bill.
     */
    public void onJoin(@NotNull Player player, @NotNull ClanPlayer cp) {
        if (!isEnabled() || cp.getClan() == null || cp.getTaxOwed() <= 0) {
            return;
        }
        if (cp.isTaxAutoPay()) {
            Payment payment = pay(cp, null);
            if (payment.getStatus() == Status.SUCCESS) {
                ChatBlock.sendMessageKey(player, "tax.autopaid", CurrencyFormat.format(payment.getAmount()));
                return;
            }
        }
        ChatBlock.sendMessageKey(player, "tax.reminder", CurrencyFormat.format(cp.getTaxOwed()), clanCommand());
    }

    /**
     * Sets the clan's tax and tells the members. Starting a tax schedules the first bill one
     * period from now, so nobody is charged the moment it's turned on.
     */
    public void setTax(@NotNull Clan clan, @NotNull String setBy, double amount) {
        boolean wasOff = clan.getTaxAmount() <= 0;
        clan.setTaxAmount(amount);
        if (amount <= 0) {
            clan.setTaxNextDue(0);
            announce(clan, setBy, lang("tax.bb.disabled", setBy));
            return;
        }
        if (wasOff || clan.getTaxNextDue() <= 0) {
            clan.setTaxNextDue(clan.getTaxPeriod().next(System.currentTimeMillis()));
        }
        announce(clan, setBy, lang("tax.bb.set", setBy, CurrencyFormat.format(amount),
                lang(clan.getTaxPeriod().getLangKey()), formatDate(clan.getTaxNextDue())));
    }

    public void setPeriod(@NotNull Clan clan, @NotNull String setBy, @NotNull TaxPeriod period) {
        clan.setTaxPeriod(period);
        if (clan.getTaxAmount() > 0) {
            clan.setTaxNextDue(period.next(System.currentTimeMillis()));
            announce(clan, setBy, lang("tax.bb.period", setBy, lang(period.getLangKey()),
                    formatDate(clan.getTaxNextDue())));
        }
    }

    /**
     * @return members who owe tax, biggest debt first
     */
    @NotNull
    public List<ClanPlayer> getDebtors(@NotNull Clan clan) {
        List<ClanPlayer> debtors = new ArrayList<>();
        for (ClanPlayer cp : clan.getMembers()) {
            if (cp.getTaxOwed() > 0) {
                debtors.add(cp);
            }
        }
        debtors.sort(Comparator.comparingDouble(ClanPlayer::getTaxOwed).reversed());
        return debtors;
    }

    public double getTotalOwed(@NotNull Clan clan) {
        double total = 0;
        for (ClanPlayer cp : clan.getMembers()) {
            total += cp.getTaxOwed();
        }
        return total;
    }

    @NotNull
    public static String formatDate(long epochMillis) {
        if (epochMillis <= 0) {
            return "-";
        }
        return new SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH).format(new Date(epochMillis));
    }

    private void announce(@NotNull Clan clan, @NotNull String announcer, @NotNull String message) {
        clan.addBb(announcer, message);
    }

    @NotNull
    private String clanCommand() {
        return plugin.getSettingsManager().getString(COMMANDS_CLAN);
    }
}
