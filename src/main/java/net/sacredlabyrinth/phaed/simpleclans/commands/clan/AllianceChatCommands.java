package net.sacredlabyrinth.phaed.simpleclans.commands.clan;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Dependency;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Subcommand;
import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceType;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Alliance chat commands: {@code /clan NATO|SCO chat <message>} (one-off) and
 * {@code /clan NATO|SCO chat toggle} (route subsequent chat). Both delegate to a
 * shared, type-scoped handler.
 */
@CommandAlias("%clan")
@Conditions("%basic_conditions")
public class AllianceChatCommands extends BaseCommand {

    @Dependency
    private SimpleClans plugin;
    @Dependency
    private ClanManager cm;

    @Subcommand("nato chat toggle")
    @Description("{@@command.description.alliance.chat}")
    public void natoChatToggle(Player player) {
        chatToggle(player, AllianceType.NATO);
    }

    @Subcommand("sco chat toggle")
    @Description("{@@command.description.alliance.chat}")
    public void scoChatToggle(Player player) {
        chatToggle(player, AllianceType.SCO);
    }

    @Subcommand("nato chat")
    @Description("{@@command.description.alliance.chat}")
    public void natoChat(Player player, @Name("message") String message) {
        chatMessage(player, AllianceType.NATO, message);
    }

    @Subcommand("sco chat")
    @Description("{@@command.description.alliance.chat}")
    public void scoChat(Player player, @Name("message") String message) {
        chatMessage(player, AllianceType.SCO, message);
    }

    private void chatToggle(@NotNull Player player, @NotNull AllianceType type) {
        if (requireChatMember(player, type) == null) {
            return;
        }
        boolean on = plugin.getAllianceChatManager().toggle(player, type);
        ChatBlock.sendMessageKey(player, on ? "alliance.chat.toggle.on" : "alliance.chat.toggle.off",
                type.getDisplayName());
    }

    private void chatMessage(@NotNull Player player, @NotNull AllianceType type, @NotNull String message) {
        ClanPlayer cp = requireChatMember(player, type);
        if (cp == null) {
            return;
        }
        if (!plugin.getAllianceChatManager().send(type, cp, message)) {
            ChatBlock.sendMessageKey(player, "alliance.not.member", type.getDisplayName());
        }
    }

    /**
     * Validates the sender is in a clan that is a member of the alliance and holds the
     * alliance-chat permission. Returns null (after messaging) on failure.
     */
    @Nullable
    private ClanPlayer requireChatMember(@NotNull Player player, @NotNull AllianceType type) {
        if (!plugin.getAllianceChatManager().canUse(player)) {
            ChatBlock.sendMessageKey(player, "insufficient.permissions");
            return null;
        }
        ClanPlayer cp = cm.getClanPlayer(player);
        Clan clan = cp != null ? cp.getClan() : null;
        if (clan == null || !plugin.getAllianceManager().isInAlliance(clan, type)) {
            ChatBlock.sendMessageKey(player, "alliance.not.member", type.getDisplayName());
            return null;
        }
        return cp;
    }
}
