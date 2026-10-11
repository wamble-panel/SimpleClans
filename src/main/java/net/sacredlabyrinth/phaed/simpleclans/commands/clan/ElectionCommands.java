package net.sacredlabyrinth.phaed.simpleclans.commands.clan;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Dependency;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Subcommand;
import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.commands.ClanPlayerInput;
import net.sacredlabyrinth.phaed.simpleclans.managers.ElectionManager;
import net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.ElectionFrame;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.ENABLE_GUI;

/**
 * Leader elections: {@code /clan election} and {@code /clan vote <member>}.
 */
@CommandAlias("%clan")
@Conditions("%basic_conditions|clan_member")
public class ElectionCommands extends BaseCommand {

    @Dependency
    private SimpleClans plugin;
    @Dependency
    private SettingsManager settings;

    @Subcommand("election")
    @CommandPermission("simpleclans.member.election")
    @Description("{@@command.description.election}")
    public void election(Player player, ClanPlayer cp, Clan clan) {
        if (!clan.isElectionActive()) {
            ChatBlock.sendMessageKey(player, "election.none");
            return;
        }
        if (settings.is(ENABLE_GUI)) {
            InventoryDrawer.open(new ElectionFrame(null, player));
            return;
        }
        ElectionManager elections = plugin.getElectionManager();
        ChatBlock.sendMessageKey(player, "election.status.header", elections.timeLeft(clan));
        Map<UUID, Integer> tally = elections.tally(clan);
        for (ClanPlayer candidate : elections.getCandidates(clan)) {
            int votes = tally.getOrDefault(candidate.getUniqueId(), 0);
            if (votes > 0) {
                ChatBlock.sendMessageKey(player, "election.status.line", candidate.getName(), votes);
            }
        }
        elections.onJoin(player, cp);
    }

    @Subcommand("vote")
    @CommandPermission("simpleclans.member.election")
    @CommandCompletion("@clan_members")
    @Description("{@@command.description.vote}")
    public void vote(Player player, ClanPlayer cp, @Conditions("same_clan") @Name("member") ClanPlayerInput input) {
        ClanPlayer candidate = input.getClanPlayer();
        switch (plugin.getElectionManager().vote(cp, candidate)) {
            case SUCCESS:
                ChatBlock.sendMessageKey(player, "election.voted", candidate.getName());
                break;
            case NO_ELECTION:
                ChatBlock.sendMessageKey(player, "election.none");
                break;
            case NOT_ALLOWED:
                ChatBlock.sendMessageKey(player, "election.cannot.vote");
                break;
            default:
                ChatBlock.sendMessageKey(player, "election.invalid.candidate", candidate.getName());
                break;
        }
    }
}
