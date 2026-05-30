package net.sacredlabyrinth.phaed.simpleclans.listeners;

import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceType;
import net.sacredlabyrinth.phaed.simpleclans.events.DisbandClanEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.NotNull;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.ALLIANCE_SHOW_ON_DEATH;

/**
 * Enforces alliance combat rules and keeps alliance membership consistent.
 *
 * <ul>
 *   <li><b>Same-alliance protection:</b> NATO members cannot damage NATO members,
 *       SCO members cannot damage SCO members. Cross-alliance combat is allowed.</li>
 *   <li><b>Disband cleanup:</b> when a clan disbands while in an alliance, its seat
 *       is freed and the host rotation shifts.</li>
 * </ul>
 */
public class AllianceListener extends SCListener {

    public AllianceListener(@NotNull SimpleClans plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || isBlacklistedWorld(event.getEntity())) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = Events.getAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        Clan attackerClan = plugin.getClanManager().getClanByPlayerUniqueId(attacker.getUniqueId());
        Clan victimClan = plugin.getClanManager().getClanByPlayerUniqueId(victim.getUniqueId());
        if (attackerClan == null || victimClan == null) {
            return;
        }

        // Scope strictly per type: only cancel when BOTH clans are in the SAME alliance.
        AllianceType attackerAlliance = plugin.getAllianceManager().getAllianceType(attackerClan);
        AllianceType victimAlliance = plugin.getAllianceManager().getAllianceType(victimClan);
        if (attackerAlliance != null && attackerAlliance == victimAlliance) {
            event.setCancelled(true);
            ChatBlock.sendMessageKey(attacker, "alliance.cannot.attack.member",
                    attackerAlliance.getDisplayName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClanDisband(DisbandClanEvent event) {
        plugin.getAllianceManager().handleClanDisband(event.getClan());
    }

    /**
     * Prepends the alliance symbol of the killer (or victim, if unaffiliated killer) to
     * the vanilla death message so alliance membership is visible in kills. Controlled
     * by {@code alliance.symbols.show-on-death-message}.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getSettingsManager().is(ALLIANCE_SHOW_ON_DEATH)) {
            return;
        }
        String currentMessage = event.getDeathMessage();
        if (currentMessage == null || currentMessage.isEmpty()) {
            return;
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        Clan victimClan = plugin.getClanManager().getClanByPlayerUniqueId(victim.getUniqueId());
        Clan killerClan = killer != null ? plugin.getClanManager().getClanByPlayerUniqueId(killer.getUniqueId()) : null;

        AllianceType killerType = plugin.getAllianceManager().getAllianceType(killerClan);
        AllianceType victimType = plugin.getAllianceManager().getAllianceType(victimClan);

        // Prefer the killer's symbol; fall back to the victim's.
        AllianceType symbolType = killerType != null ? killerType : victimType;
        if (symbolType != null) {
            event.setDeathMessage(symbolType.getColoredSymbol() + " " + currentMessage);
        }
    }
}
