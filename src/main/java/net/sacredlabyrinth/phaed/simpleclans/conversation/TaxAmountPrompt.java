package net.sacredlabyrinth.phaed.simpleclans.conversation;

import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.utils.CurrencyFormat;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.COMMANDS_CLAN;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.TAX_MAX_AMOUNT;
import static org.bukkit.ChatColor.RED;

/**
 * Asks a leader for the new tax amount in chat (used by the Tax menu), then runs
 * {@code /clan tax set <amount>} so all the usual checks apply.
 */
public class TaxAmountPrompt extends StringPrompt {

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext context) {
        Player player = (Player) context.getForWhom();
        double max = SimpleClans.getInstance().getSettingsManager().getDouble(TAX_MAX_AMOUNT);
        return lang("tax.prompt.amount", player, CurrencyFormat.format(max), lang("cancel", player));
    }

    @Override
    public @Nullable Prompt acceptInput(@NotNull ConversationContext context, @Nullable String input) {
        Player player = (Player) context.getForWhom();
        if (input == null) {
            return this;
        }
        String text = input.trim();
        // Reject commas: "2,50" could mean 2.5 or 250, and guessing wrong could set a tax
        // 100x higher than intended.
        if (text.contains(",")) {
            return new MessagePromptImpl(RED + lang("tax.invalid.amount.digits", player), this);
        }
        double amount;
        try {
            amount = Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return new MessagePromptImpl(RED + lang("tax.invalid.amount", player), this);
        }
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return new MessagePromptImpl(RED + lang("tax.invalid.amount", player), this);
        }
        String command = SimpleClans.getInstance().getSettingsManager().getString(COMMANDS_CLAN);
        // toPlainString: plain "10000000" rather than "1.0E7" for the command parser.
        player.performCommand(command + " tax set " + BigDecimal.valueOf(amount).toPlainString());
        return END_OF_CONVERSATION;
    }
}
