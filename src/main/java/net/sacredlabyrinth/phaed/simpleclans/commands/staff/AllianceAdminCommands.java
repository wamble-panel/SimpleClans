package net.sacredlabyrinth.phaed.simpleclans.commands.staff;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Dependency;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Single;
import co.aikar.commands.annotation.Subcommand;
import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceType;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

/**
 * Staff alliance commands:
 * {@code /clan admin alliance forceRemove <clan> <alliance>},
 * {@code /clan admin alliance setHost <clan>},
 * {@code /clan admin alliance triggerMeeting <alliance>}.
 */
@CommandAlias("%clan")
@CommandPermission("clans.alliance.admin")
public class AllianceAdminCommands extends BaseCommand {

    @Dependency
    private SimpleClans plugin;
    @Dependency
    private ClanManager cm;

    @Subcommand("admin alliance forceremove")
    @Description("{@@command.description.alliance.admin.forceremove}")
    public void forceRemove(CommandSender sender, @Single @Name("clan") String clanName,
                            @Single @Name("alliance") String allianceName) {
        AllianceType type = requireAlliance(sender, allianceName);
        if (type == null) {
            return;
        }
        Clan clan = cm.getClan(clanName);
        if (clan == null) {
            ChatBlock.sendMessageKey(sender, "alliance.admin.clan.not.found", clanName);
            return;
        }
        if (plugin.getAllianceManager().removeMember(type, clan, false, true)) {
            ChatBlock.sendMessageKey(sender, "alliance.admin.removed", clan.getName(), type.getDisplayName());
        } else {
            ChatBlock.sendMessageKey(sender, "alliance.admin.not.member", clan.getName(), type.getDisplayName());
        }
    }

    @Subcommand("admin alliance sethost")
    @Description("{@@command.description.alliance.admin.sethost}")
    public void setHost(CommandSender sender, @Single @Name("clan") String clanName) {
        Clan clan = cm.getClan(clanName);
        if (clan == null) {
            ChatBlock.sendMessageKey(sender, "alliance.admin.clan.not.found", clanName);
            return;
        }
        AllianceType type = plugin.getAllianceManager().getAllianceType(clan);
        if (type == null) {
            ChatBlock.sendMessageKey(sender, "alliance.admin.clan.no.alliance", clan.getName());
            return;
        }
        if (plugin.getAllianceMeetingManager().adminSetHost(type, clan)) {
            ChatBlock.sendMessageKey(sender, "alliance.admin.host.set", clan.getName(), type.getDisplayName());
        }
    }

    @Subcommand("admin alliance triggermeeting")
    @Description("{@@command.description.alliance.admin.triggermeeting}")
    public void triggerMeeting(CommandSender sender, @Single @Name("alliance") String allianceName) {
        AllianceType type = requireAlliance(sender, allianceName);
        if (type == null) {
            return;
        }
        plugin.getAllianceMeetingManager().adminTriggerMeeting(type);
        ChatBlock.sendMessageKey(sender, "alliance.admin.meeting.triggered", type.getDisplayName());
    }

    @Nullable
    private AllianceType requireAlliance(CommandSender sender, String name) {
        AllianceType type = AllianceType.fromString(name);
        if (type == null) {
            ChatBlock.sendMessageKey(sender, "alliance.admin.invalid.alliance", name);
        }
        return type;
    }
}
