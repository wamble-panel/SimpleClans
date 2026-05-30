package net.sacredlabyrinth.phaed.simpleclans.commands.clan;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Dependency;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Single;
import co.aikar.commands.annotation.Subcommand;
import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceMeetingManager;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceType;
import net.sacredlabyrinth.phaed.simpleclans.alliance.Proposal;
import net.sacredlabyrinth.phaed.simpleclans.alliance.ProposalType;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager;
import net.sacredlabyrinth.phaed.simpleclans.managers.PermissionsManager;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.AllianceVotingFrame;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Proposal, voting and history commands:
 * {@code /clan NATO|SCO propose remove|maxallies|custom ...}, {@code withdraw},
 * {@code vote}, {@code history}.
 *
 * <p>Each command resolves an {@link AllianceType} and delegates to a shared,
 * type-scoped handler.</p>
 */
@CommandAlias("%clan")
@Conditions("%basic_conditions")
public class AllianceProposalCommands extends BaseCommand {

    private static final String PROPOSE_PERMISSION = "clans.alliance.propose";
    private static final String VOTE_PERMISSION = "clans.alliance.vote";

    @Dependency
    private SimpleClans plugin;
    @Dependency
    private PermissionsManager permissions;
    @Dependency
    private ClanManager cm;

    @Subcommand("nato propose remove")
    @Description("{@@command.description.alliance.propose}")
    public void natoProposeRemove(Player player, @Single @Name("clan") String clanName) {
        proposeRemove(player, AllianceType.NATO, clanName);
    }

    @Subcommand("sco propose remove")
    @Description("{@@command.description.alliance.propose}")
    public void scoProposeRemove(Player player, @Single @Name("clan") String clanName) {
        proposeRemove(player, AllianceType.SCO, clanName);
    }

    @Subcommand("nato propose maxallies")
    @Description("{@@command.description.alliance.propose}")
    public void natoProposeMaxAllies(Player player, @Name("number") int number) {
        proposeMaxAllies(player, AllianceType.NATO, number);
    }

    @Subcommand("sco propose maxallies")
    @Description("{@@command.description.alliance.propose}")
    public void scoProposeMaxAllies(Player player, @Name("number") int number) {
        proposeMaxAllies(player, AllianceType.SCO, number);
    }

    @Subcommand("nato propose custom")
    @Description("{@@command.description.alliance.propose}")
    public void natoProposeCustom(Player player, @Name("text") String text) {
        proposeCustom(player, AllianceType.NATO, text);
    }

    @Subcommand("sco propose custom")
    @Description("{@@command.description.alliance.propose}")
    public void scoProposeCustom(Player player, @Name("text") String text) {
        proposeCustom(player, AllianceType.SCO, text);
    }

    @Subcommand("nato withdraw")
    @Description("{@@command.description.alliance.withdraw}")
    public void natoWithdraw(Player player, @Name("id") int id) {
        withdraw(player, AllianceType.NATO, id);
    }

    @Subcommand("sco withdraw")
    @Description("{@@command.description.alliance.withdraw}")
    public void scoWithdraw(Player player, @Name("id") int id) {
        withdraw(player, AllianceType.SCO, id);
    }

    @Subcommand("nato vote")
    @Description("{@@command.description.alliance.vote}")
    public void natoVote(Player player) {
        vote(player, AllianceType.NATO);
    }

    @Subcommand("sco vote")
    @Description("{@@command.description.alliance.vote}")
    public void scoVote(Player player) {
        vote(player, AllianceType.SCO);
    }

    @Subcommand("nato history")
    @Description("{@@command.description.alliance.history}")
    public void natoHistory(Player player) {
        history(player, AllianceType.NATO);
    }

    @Subcommand("sco history")
    @Description("{@@command.description.alliance.history}")
    public void scoHistory(Player player) {
        history(player, AllianceType.SCO);
    }

    // ----- shared handlers -----

    private void proposeRemove(@NotNull Player player, @NotNull AllianceType type, @NotNull String clanName) {
        Clan clan = requireProposer(player, type);
        if (clan == null) {
            return;
        }
        report(player, type, plugin.getAllianceMeetingManager().submit(clan, type, ProposalType.REMOVE, clanName, 0, null));
    }

    private void proposeMaxAllies(@NotNull Player player, @NotNull AllianceType type, int number) {
        Clan clan = requireProposer(player, type);
        if (clan == null) {
            return;
        }
        report(player, type, plugin.getAllianceMeetingManager().submit(clan, type, ProposalType.MAX_ALLIES, null, number, null));
    }

    private void proposeCustom(@NotNull Player player, @NotNull AllianceType type, @NotNull String text) {
        Clan clan = requireProposer(player, type);
        if (clan == null) {
            return;
        }
        report(player, type, plugin.getAllianceMeetingManager().submit(clan, type, ProposalType.CUSTOM, null, 0, text));
    }

    private void report(@NotNull Player player, @NotNull AllianceType type, @NotNull AllianceMeetingManager.SubmitResult result) {
        switch (result) {
            case SUCCESS:
                ChatBlock.sendMessageKey(player, "alliance.propose.success", lastPendingId(player, type));
                break;
            case INVALID_TARGET:
                ChatBlock.sendMessageKey(player, "alliance.propose.invalid.target");
                break;
            case TARGET_NOT_MEMBER:
                ChatBlock.sendMessageKey(player, "alliance.propose.target.not.member", type.getDisplayName());
                break;
            case INVALID_NUMBER:
                ChatBlock.sendMessageKey(player, "alliance.propose.invalid.number");
                break;
            case TEXT_TOO_LONG:
                ChatBlock.sendMessageKey(player, "alliance.propose.text.too.long");
                break;
            case PROFANITY:
                ChatBlock.sendMessageKey(player, "alliance.propose.profanity");
                break;
        }
    }

    private void withdraw(@NotNull Player player, @NotNull AllianceType type, int id) {
        Clan clan = requireProposer(player, type);
        if (clan == null) {
            return;
        }
        if (plugin.getAllianceMeetingManager().withdraw(clan, type, id)) {
            ChatBlock.sendMessageKey(player, "alliance.withdraw.success", id);
        } else {
            ChatBlock.sendMessageKey(player, "alliance.withdraw.failed", id);
        }
    }

    private void vote(@NotNull Player player, @NotNull AllianceType type) {
        if (!permissions.has(player, VOTE_PERMISSION)) {
            ChatBlock.sendMessageKey(player, "insufficient.permissions");
            return;
        }
        Clan clan = cm.getClanByPlayerUniqueId(player.getUniqueId());
        if (clan == null || !clan.isLeader(player) || !plugin.getAllianceManager().isInAlliance(clan, type)) {
            ChatBlock.sendMessageKey(player, "alliance.not.member", type.getDisplayName());
            return;
        }
        if (!plugin.getAllianceMeetingManager().isMeetingActive(type)) {
            ChatBlock.sendMessageKey(player, "alliance.vote.closed");
            return;
        }
        InventoryDrawer.open(new AllianceVotingFrame(null, player, type));
    }

    private void history(@NotNull Player player, @NotNull AllianceType type) {
        List<Proposal> entries = plugin.getAllianceMeetingManager().getHistory(type);
        ChatBlock.sendMessageKey(player, "alliance.history.header", type.getDisplayName());
        if (entries.isEmpty()) {
            ChatBlock.sendMessageKey(player, "alliance.history.empty");
            return;
        }
        int from = Math.max(0, entries.size() - 10);
        for (int i = from; i < entries.size(); i++) {
            Proposal p = entries.get(i);
            String state = p.getStatus().name();
            ChatBlock.sendMessageKey(player, "alliance.history.line", p.getId(), p.describe(), state,
                    p.getFinalAgree(), p.getFinalDisagree());
        }
    }

    /**
     * Resolves and validates the sender as an alliance-member clan leader with the
     * propose permission. Returns null (after messaging) on any failure.
     */
    @Nullable
    private Clan requireProposer(@NotNull Player player, @NotNull AllianceType type) {
        if (!permissions.has(player, PROPOSE_PERMISSION)) {
            ChatBlock.sendMessageKey(player, "insufficient.permissions");
            return null;
        }
        Clan clan = cm.getClanByPlayerUniqueId(player.getUniqueId());
        if (clan == null) {
            ChatBlock.sendMessageKey(player, "not.a.member.of.any.clan");
            return null;
        }
        if (!clan.isLeader(player)) {
            ChatBlock.sendMessageKey(player, "alliance.must.be.leader");
            return null;
        }
        if (!plugin.getAllianceManager().isInAlliance(clan, type)) {
            ChatBlock.sendMessageKey(player, "alliance.not.member", type.getDisplayName());
            return null;
        }
        return clan;
    }

    private int lastPendingId(@NotNull Player player, @NotNull AllianceType type) {
        Clan clan = cm.getClanByPlayerUniqueId(player.getUniqueId());
        if (clan == null) {
            return -1;
        }
        int id = -1;
        for (Proposal p : plugin.getAllianceMeetingManager().getPendingProposals(type)) {
            if (p.getProposerTag().equals(clan.getTag()) && p.getId() > id) {
                id = p.getId();
            }
        }
        return id;
    }
}
