package net.sacredlabyrinth.phaed.simpleclans.commands.clan;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Dependency;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Single;
import co.aikar.commands.annotation.Subcommand;
import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.TaxPeriod;
import net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager;
import net.sacredlabyrinth.phaed.simpleclans.managers.TaxManager;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.TaxFrame;
import net.sacredlabyrinth.phaed.simpleclans.utils.CurrencyFormat;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

/**
 * Clan tax: {@code /clan tax [set|period|exempt|exempttrusted|owed|autopay]} and
 * {@code /clan paytax [amount]}.
 */
@CommandAlias("%clan")
@Conditions("%basic_conditions|clan_member|economy")
public class TaxCommands extends BaseCommand {

    @Dependency
    private SimpleClans plugin;
    @Dependency
    private SettingsManager settings;

    @Subcommand("tax")
    @CommandPermission("simpleclans.member.tax")
    @Description("{@@command.description.tax}")
    public void tax(Player player, ClanPlayer cp, Clan clan) {
        if (!enabled(player)) {
            return;
        }
        if (settings.is(ENABLE_GUI)) {
            InventoryDrawer.open(new TaxFrame(null, player));
            return;
        }
        if (clan.getTaxAmount() <= 0) {
            ChatBlock.sendMessageKey(player, "tax.info.none");
        } else {
            ChatBlock.sendMessageKey(player, "tax.info", CurrencyFormat.format(clan.getTaxAmount()),
                    lang(clan.getTaxPeriod().getLangKey(), player), TaxManager.formatDate(clan.getTaxNextDue()));
        }
        ChatBlock.sendMessageKey(player, "tax.info.owed", CurrencyFormat.format(cp.getTaxOwed()), clanCommand());
    }

    @Subcommand("paytax")
    @CommandPermission("simpleclans.member.tax")
    @Description("{@@command.description.paytax}")
    public void payTax(Player player, ClanPlayer cp, @Optional @Name("amount") @Nullable Double amount) {
        if (!enabled(player)) {
            return;
        }
        if (amount != null && (!Double.isFinite(amount) || amount <= 0)) {
            ChatBlock.sendMessageKey(player, "tax.invalid.amount");
            return;
        }
        TaxManager.Payment payment = plugin.getTaxManager().pay(cp, amount);
        switch (payment.getStatus()) {
            case SUCCESS:
                ChatBlock.sendMessageKey(player, "tax.paid", CurrencyFormat.format(payment.getAmount()),
                        CurrencyFormat.format(cp.getTaxOwed()));
                break;
            case NOTHING_OWED:
                ChatBlock.sendMessageKey(player, "tax.nothing.owed");
                break;
            case NOT_ENOUGH_MONEY:
                ChatBlock.sendMessageKey(player, "tax.not.enough.money", CurrencyFormat.format(payment.getAmount()));
                break;
            default:
                ChatBlock.sendMessageKey(player, "tax.payment.failed");
                break;
        }
    }

    @Subcommand("tax autopay")
    @CommandPermission("simpleclans.member.tax")
    @Description("{@@command.description.tax.autopay}")
    public void autoPay(Player player, ClanPlayer cp) {
        if (!enabled(player)) {
            return;
        }
        boolean autoPay = !cp.isTaxAutoPay();
        cp.setTaxAutoPay(autoPay);
        ChatBlock.sendMessageKey(player, autoPay ? "tax.autopay.on" : "tax.autopay.off");
        if (autoPay && cp.getTaxOwed() > 0) {
            plugin.getTaxManager().onJoin(player, cp); // pay the current bill straight away
        }
    }

    @Subcommand("tax set")
    @CommandPermission("simpleclans.leader.tax")
    @Conditions("verified|rank:name=TAX_MANAGE")
    @Description("{@@command.description.tax.set}")
    public void set(Player player, Clan clan, @Name("amount") double amount) {
        if (!enabled(player)) {
            return;
        }
        double max = settings.getDouble(TAX_MAX_AMOUNT);
        if (!Double.isFinite(amount) || amount < 0 || amount > max) {
            ChatBlock.sendMessageKey(player, "tax.amount.out.of.range", CurrencyFormat.format(max));
            return;
        }
        amount = Math.round(amount * 100) / 100.0; // whole cents
        plugin.getTaxManager().setTax(clan, player.getName(), amount);
        if (amount <= 0) {
            ChatBlock.sendMessageKey(player, "tax.disabled");
        } else {
            ChatBlock.sendMessageKey(player, "tax.set", CurrencyFormat.format(amount),
                    lang(clan.getTaxPeriod().getLangKey(), player), TaxManager.formatDate(clan.getTaxNextDue()));
        }
    }

    @Subcommand("tax period")
    @CommandPermission("simpleclans.leader.tax")
    @CommandCompletion("weekly|monthly")
    @Conditions("verified|rank:name=TAX_MANAGE")
    @Description("{@@command.description.tax.period}")
    public void period(Player player, Clan clan, @Single @Name("weekly|monthly") String value) {
        if (!enabled(player)) {
            return;
        }
        TaxPeriod period = TaxPeriod.parse(value);
        if (period == null) {
            ChatBlock.sendMessageKey(player, "tax.invalid.period");
            return;
        }
        plugin.getTaxManager().setPeriod(clan, player.getName(), period);
        ChatBlock.sendMessageKey(player, "tax.period.set", lang(period.getLangKey(), player));
    }

    @Subcommand("tax exempt")
    @CommandPermission("simpleclans.leader.tax")
    @CommandCompletion("@ranks")
    @Conditions("verified|rank:name=TAX_MANAGE")
    @Description("{@@command.description.tax.exempt}")
    public void exemptRank(Player player, Clan clan, @Single @Name("rank") String rankName) {
        if (!enabled(player)) {
            return;
        }
        if (clan.getRank(rankName) == null) {
            ChatBlock.sendMessageKey(player, "rank.0.does.not.exist", rankName);
            return;
        }
        List<String> exempt = clan.getTaxExemptRanks();
        boolean nowExempt = !exempt.remove(rankName);
        if (nowExempt) {
            exempt.add(rankName);
        }
        clan.setTaxExemptRanks(exempt);
        ChatBlock.sendMessageKey(player, nowExempt ? "tax.rank.exempt" : "tax.rank.not.exempt", rankName);
    }

    @Subcommand("tax exempttrusted")
    @CommandPermission("simpleclans.leader.tax")
    @Conditions("verified|rank:name=TAX_MANAGE")
    @Description("{@@command.description.tax.exempttrusted}")
    public void exemptTrusted(Player player, Clan clan) {
        if (!enabled(player)) {
            return;
        }
        boolean exempt = !clan.isTaxExemptTrusted();
        clan.setTaxExemptTrusted(exempt);
        ChatBlock.sendMessageKey(player, exempt ? "tax.trusted.exempt" : "tax.trusted.not.exempt");
    }

    @Subcommand("tax owed")
    @CommandPermission("simpleclans.leader.tax")
    @Conditions("rank:name=TAX_MANAGE")
    @Description("{@@command.description.tax.owed}")
    public void owed(Player player, Clan clan) {
        if (!enabled(player)) {
            return;
        }
        List<ClanPlayer> debtors = plugin.getTaxManager().getDebtors(clan);
        if (debtors.isEmpty()) {
            ChatBlock.sendMessageKey(player, "tax.owed.none");
            return;
        }
        ChatBlock.sendMessageKey(player, "tax.owed.header", debtors.size(),
                CurrencyFormat.format(plugin.getTaxManager().getTotalOwed(clan)));
        for (ClanPlayer debtor : debtors) {
            ChatBlock.sendMessageKey(player, "tax.owed.line", debtor.getName(),
                    CurrencyFormat.format(debtor.getTaxOwed()));
        }
    }

    private boolean enabled(@NotNull Player player) {
        if (!plugin.getTaxManager().isEnabled()) {
            ChatBlock.sendMessageKey(player, "tax.feature.disabled");
            return false;
        }
        return true;
    }

    @NotNull
    private String clanCommand() {
        return settings.getString(COMMANDS_CLAN);
    }
}
