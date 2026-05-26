package net.sacredlabyrinth.phaed.simpleclans.listeners;

import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.GLOBAL_FRIENDLY_FIRE;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.SAFE_CIVILIANS;

public class FriendlyFire extends SCListener {

    private final Map<UUID, Long> warned = new HashMap<>();
    private static final long WARN_DELAY = 10000;

    public FriendlyFire(@NotNull SimpleClans plugin) {
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

        ClanPlayer vcp = plugin.getClanManager().getClanPlayer(victim);

        Clan victimClan = vcp == null ? null : vcp.getClan();
        Clan attackerClan = plugin.getClanManager().getClanByPlayerUniqueId(attacker.getUniqueId());

        process(event, attacker, vcp, victimClan, attackerClan);
    }

    private void process(EntityDamageEvent event,
                         Player attacker,
                         @Nullable ClanPlayer vcp,
                         @Nullable Clan victimClan,
                         @Nullable Clan attackerClan) {
        if (vcp == null || victimClan == null || attackerClan == null) {
            // Only protect civilians when at least one combatant is in a clan; two clanless
            // players fighting each other is not a clan-related combat scenario.
            boolean attackerHasClan = attackerClan != null;
            boolean victimHasClan = vcp != null && victimClan != null;
            if ((attackerHasClan || victimHasClan) && plugin.getSettingsManager().is(SAFE_CIVILIANS)) {
                ChatBlock.sendMessageKey(attacker, "cannot.attack.civilians");
                event.setCancelled(true);
            }
            return;
        }

        ClanPlayer acp = plugin.getClanManager().getClanPlayer(attacker);
        if (vcp.isFriendlyFire() || victimClan.isFriendlyFire()
                || (acp != null && acp.isFriendlyFire()) || attackerClan.isFriendlyFire()
                || plugin.getSettingsManager().is(GLOBAL_FRIENDLY_FIRE)) {
            return;
        }

        if (victimClan.equals(attackerClan)) {
            warn(attacker, "cannot.attack.clan.member");
            event.setCancelled(true);
            return;
        }

        if (victimClan.isAlly(attackerClan.getTag())) {
            warn(attacker, "cannot.attack.ally");
            event.setCancelled(true);
        }
    }

    private void warn(Player attacker, String messageKey) {
        long timestamp = warned.getOrDefault(attacker.getUniqueId(), 0L);
        long currentTimeMillis = System.currentTimeMillis();

        if (timestamp + WARN_DELAY <= currentTimeMillis) {
            ChatBlock.sendMessageKey(attacker, messageKey);
            warned.put(attacker.getUniqueId(), currentTimeMillis);
        }
    }
}
