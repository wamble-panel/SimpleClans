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
import co.aikar.commands.annotation.Subcommand;
import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.Helper;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager;
import net.sacredlabyrinth.phaed.simpleclans.managers.ProtectionManager;
import net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.WarpsFrame;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

/**
 * Public clan warps: each clan can set one warp that anyone can visit while it is on.
 * {@code /clan warp [tag]}, {@code /clan setwarp}, {@code /clan delwarp}, {@code /clan togglewarp}.
 */
@CommandAlias("%clan")
@Conditions("%basic_conditions")
public class WarpCommands extends BaseCommand {

    @Dependency
    private SimpleClans plugin;
    @Dependency
    private SettingsManager settings;
    @Dependency
    private ClanManager cm;
    @Dependency
    private ProtectionManager protection;

    @Subcommand("warp")
    @CommandPermission("simpleclans.anyone.warp")
    @CommandCompletion("@active_warps")
    @Description("{@@command.description.warp}")
    public void warp(Player player, @Optional @Name("clan") @Nullable String tag) {
        if (!warpsEnabled(player)) {
            return;
        }
        if (tag == null) {
            InventoryDrawer.open(new WarpsFrame(null, player));
            return;
        }
        Clan target = cm.getClan(tag);
        if (target == null) {
            ChatBlock.sendMessageKey(player, "the.clan.does.not.exist");
            return;
        }
        Location warp = target.getWarpLocation();
        if (!target.isWarpEnabled() || warp == null) {
            ChatBlock.sendMessageKey(player, "warp.not.active", target.getName());
            return;
        }
        plugin.getTeleportManager().addPlayer(player, warp, target.getName(), "now.at.warp");
    }

    @Subcommand("setwarp")
    @CommandPermission("simpleclans.leader.warp")
    @Conditions("verified|rank:name=WARP_SET")
    @Description("{@@command.description.warp.set}")
    public void setWarp(Player player, Clan clan) {
        if (!warpsEnabled(player)) {
            return;
        }
        Location location = player.getLocation();
        if (settings.is(WARPS_SET_ONLY_IN_OWN_LAND) && !protection.isOwner(player, location)) {
            ChatBlock.sendMessageKey(player, "warp.only.in.own.land");
            return;
        }
        if (!cm.purchaseWarpSet(player)) {
            return;
        }
        clan.setWarpLocation(location);
        ChatBlock.sendMessageKey(player, "warp.set", Helper.toLocationString(location), clanCommand(), clan.getTag());
    }

    @Subcommand("delwarp")
    @CommandPermission("simpleclans.leader.warp")
    @Conditions("verified|rank:name=WARP_SET")
    @Description("{@@command.description.warp.delete}")
    public void deleteWarp(Player player, Clan clan) {
        if (!hasWarp(player, clan)) {
            return;
        }
        clan.setWarpLocation(null);
        ChatBlock.sendMessageKey(player, "warp.deleted");
    }

    @Subcommand("togglewarp")
    @CommandPermission("simpleclans.leader.warp")
    @Conditions("verified|rank:name=WARP_TOGGLE")
    @Description("{@@command.description.warp.toggle}")
    public void toggleWarp(Player player, Clan clan) {
        if (!warpsEnabled(player) || !hasWarp(player, clan)) {
            return;
        }
        boolean enabled = !clan.isWarpEnabled();
        clan.setWarpEnabled(enabled);
        if (enabled) {
            ChatBlock.sendMessageKey(player, "warp.toggled.on", clanCommand(), clan.getTag());
        } else {
            ChatBlock.sendMessageKey(player, "warp.toggled.off");
        }
    }

    private boolean warpsEnabled(@NotNull Player player) {
        if (!settings.is(WARPS_ENABLED)) {
            ChatBlock.sendMessageKey(player, "warps.disabled");
            return false;
        }
        return true;
    }

    private boolean hasWarp(@NotNull Player player, @NotNull Clan clan) {
        if (clan.getWarpLocation() == null) {
            ChatBlock.sendMessageKey(player, "warp.not.set", clanCommand());
            return false;
        }
        return true;
    }

    private String clanCommand() {
        return settings.getString(COMMANDS_CLAN);
    }
}
