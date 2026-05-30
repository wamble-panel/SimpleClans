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
import org.jetbrains.annotations.NotNull;

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
}
