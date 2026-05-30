package net.sacredlabyrinth.phaed.simpleclans.commands.clan;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.alliance.Alliance;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceManager;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceType;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager;
import net.sacredlabyrinth.phaed.simpleclans.managers.PermissionsManager;
import net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager;
import net.sacredlabyrinth.phaed.simpleclans.utils.CurrencyFormat;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import co.aikar.commands.annotation.Dependency;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.ALLIANCE_JOINING_FEE;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.ALLIANCE_MAX_MEMBERS;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.ALLIANCE_REJOIN_COOLDOWN_DAYS;

/**
 * Player-facing alliance commands: {@code /clan NATO|SCO set|join|leave|info}.
 *
 * <p>Every command resolves an {@link AllianceType} and delegates to a single
 * shared handler that takes the type — there is no shared mutable state between
 * NATO and SCO, which is how command-level isolation is guaranteed.</p>
 */
@CommandAlias("%clan")
@Conditions("%basic_conditions")
public class AllianceCommands extends BaseCommand {

    private static final String MANAGE_PERMISSION = "clans.alliance.join";

    @Dependency
    private SimpleClans plugin;
    @Dependency
    private SettingsManager settings;
    @Dependency
    private PermissionsManager permissions;
    @Dependency
    private ClanManager cm;

    @Subcommand("nato set")
    @Description("{@@command.description.alliance.set}")
    public void natoSet(Player player) {
        set(player, AllianceType.NATO);
    }

    @Subcommand("sco set")
    @Description("{@@command.description.alliance.set}")
    public void scoSet(Player player) {
        set(player, AllianceType.SCO);
    }

    @Subcommand("nato join")
    @Description("{@@command.description.alliance.join}")
    public void natoJoin(Player player) {
        join(player, AllianceType.NATO);
    }

    @Subcommand("sco join")
    @Description("{@@command.description.alliance.join}")
    public void scoJoin(Player player) {
        join(player, AllianceType.SCO);
    }

    @Subcommand("nato leave")
    @Description("{@@command.description.alliance.leave}")
    public void natoLeave(Player player) {
        leave(player, AllianceType.NATO);
    }

    @Subcommand("sco leave")
    @Description("{@@command.description.alliance.leave}")
    public void scoLeave(Player player) {
        leave(player, AllianceType.SCO);
    }

    @Subcommand("nato info")
    @Description("{@@command.description.alliance.info}")
    public void natoInfo(Player player) {
        info(player, AllianceType.NATO);
    }

    @Subcommand("sco info")
    @Description("{@@command.description.alliance.info}")
    public void scoInfo(Player player) {
        info(player, AllianceType.SCO);
    }

    // ----- shared, alliance-scoped handlers -----

    private void set(@NotNull Player player, @NotNull AllianceType type) {
        Clan clan = requireLeaderClan(player);
        if (clan == null) {
            return;
        }
        plugin.getAllianceManager().setHq(clan, type, player.getLocation());
        ChatBlock.sendMessageKey(player, "alliance.hq.set", label(type));
    }

    private void join(@NotNull Player player, @NotNull AllianceType type) {
        Clan clan = requireLeaderClan(player);
        if (clan == null) {
            return;
        }
        AllianceManager manager = plugin.getAllianceManager();
        AllianceManager.JoinResult result = manager.join(player, clan, type);
        switch (result) {
            case SUCCESS:
                double fee = settings.getDouble(ALLIANCE_JOINING_FEE);
                ChatBlock.sendMessageKey(player, "alliance.joined", label(type), CurrencyFormat.format(fee));
                break;
            case ALREADY_MEMBER:
                ChatBlock.sendMessageKey(player, "alliance.already.member", label(type));
                break;
            case IN_RIVAL_ALLIANCE:
                ChatBlock.sendMessageKey(player, "alliance.in.rival");
                break;
            case NO_HQ:
                ChatBlock.sendMessageKey(player, "alliance.hq.required", label(type), type.getId().toLowerCase());
                break;
            case FULL:
                ChatBlock.sendMessageKey(player, "alliance.full", label(type), settings.getInt(ALLIANCE_MAX_MEMBERS));
                break;
            case ON_COOLDOWN:
                ChatBlock.sendMessageKey(player, "alliance.on.cooldown", label(type),
                        settings.getInt(ALLIANCE_REJOIN_COOLDOWN_DAYS));
                break;
            case NOT_ENOUGH_MONEY:
                ChatBlock.sendMessageKey(player, "alliance.not.enough.money",
                        CurrencyFormat.format(settings.getDouble(ALLIANCE_JOINING_FEE)));
                break;
        }
    }

    private void leave(@NotNull Player player, @NotNull AllianceType type) {
        Clan clan = requireLeaderClan(player);
        if (clan == null) {
            return;
        }
        if (plugin.getAllianceManager().leave(clan, type)) {
            ChatBlock.sendMessageKey(player, "alliance.left", label(type));
        } else {
            ChatBlock.sendMessageKey(player, "alliance.not.member", label(type));
        }
    }

    private void info(@NotNull Player player, @NotNull AllianceType type) {
        Alliance alliance = plugin.getAllianceManager().getAlliance(type);
        int max = settings.getInt(ALLIANCE_MAX_MEMBERS);
        ChatBlock.sendMessageKey(player, "alliance.info.header", label(type), alliance.getSize(), max);

        if (alliance.getSize() == 0) {
            ChatBlock.sendMessageKey(player, "alliance.info.empty");
        } else {
            String symbol = type.getColoredSymbol();
            for (String tag : alliance.getMemberTags()) {
                Clan member = cm.getClan(tag);
                String display = member != null ? member.getColorTag() + " " + member.getName() : tag;
                ChatBlock.sendMessageKey(player, "alliance.info.member", symbol + " " + display);
            }
            String hostTag = alliance.getCurrentHostTag();
            Clan host = hostTag != null ? cm.getClan(hostTag) : null;
            String hostName = host != null ? host.getName() : (hostTag != null ? hostTag : "-");
            ChatBlock.sendMessageKey(player, "alliance.info.host", hostName);
        }
        ChatBlock.sendMessageKey(player, "alliance.info.maxallies", alliance.getMaxAlliesPerWar());
    }

    /**
     * Resolves the sender's clan and verifies they are its leader and hold the
     * alliance-management permission. Sends the appropriate error and returns null
     * if any check fails.
     */
    private Clan requireLeaderClan(@NotNull Player player) {
        if (!permissions.has(player, MANAGE_PERMISSION)) {
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
        return clan;
    }

    /**
     * @return a colored "★ NATO" style label for messages
     */
    private String label(@NotNull AllianceType type) {
        return type.getColoredSymbol() + " " + type.getDisplayName();
    }
}
