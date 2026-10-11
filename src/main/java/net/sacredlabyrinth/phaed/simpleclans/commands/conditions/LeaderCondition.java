package net.sacredlabyrinth.phaed.simpleclans.commands.conditions;

import co.aikar.commands.BukkitCommandIssuer;
import co.aikar.commands.ConditionContext;
import co.aikar.commands.ConditionFailedException;
import co.aikar.commands.InvalidCommandArgument;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;

@SuppressWarnings("unused")
public class LeaderCondition extends AbstractCommandCondition {
    public LeaderCondition(@NotNull SimpleClans plugin) {
        super(plugin);
    }

    @Override
    public void validateCondition(ConditionContext<BukkitCommandIssuer> context) throws InvalidCommandArgument {
        Player player = Conditions.assertPlayer(context.getIssuer());
        Clan clan = Conditions.assertClanMember(clanManager, context.getIssuer());
        if (!clan.isLeader(player)) {
            throw new ConditionFailedException(ChatColor.RED + lang("no.leader.permissions", player));
        }
        // Leaders being replaced by an election keep the title but not the powers.
        if (plugin.getElectionManager().isLockedLeader(clan, player.getUniqueId())) {
            throw new ConditionFailedException(lang("election.locked.action", player));
        }
    }

    @Override
    public @NotNull String getId() {
        return "leader";
    }
}
