package net.sacredlabyrinth.phaed.simpleclans.managers;

import net.sacredlabyrinth.phaed.simpleclans.*;
import net.sacredlabyrinth.phaed.simpleclans.alliance.Alliance;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceManager;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceMeetingManager;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceType;
import net.sacredlabyrinth.phaed.simpleclans.alliance.MeetingState;
import net.sacredlabyrinth.phaed.simpleclans.alliance.Proposal;
import net.sacredlabyrinth.phaed.simpleclans.alliance.ProposalType;
import net.sacredlabyrinth.phaed.simpleclans.events.ClanBalanceUpdateEvent;
import net.sacredlabyrinth.phaed.simpleclans.loggers.BankLogger;
import net.sacredlabyrinth.phaed.simpleclans.loggers.BankOperator;
import net.sacredlabyrinth.phaed.simpleclans.storage.DBCore;
import net.sacredlabyrinth.phaed.simpleclans.storage.MySQLCore;
import net.sacredlabyrinth.phaed.simpleclans.storage.SQLiteCore;
import net.sacredlabyrinth.phaed.simpleclans.utils.ChatUtils;
import net.sacredlabyrinth.phaed.simpleclans.utils.YAMLSerializer;
import net.sacredlabyrinth.phaed.simpleclans.uuid.UUIDFetcher;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

/**
 * @author phaed
 */
public final class StorageManager {

    private final SimpleClans plugin;
    private DBCore core;
    private final HashMap<String, ChatBlock> chatBlocks = new HashMap<>();
    // Accessed from both main thread (writes) and async SaveDataTask (reads/clear).
    // Use a synchronized wrapper so individual operations are thread-safe.
    private final Set<Clan> modifiedClans = Collections.synchronizedSet(new HashSet<>());
    private final Set<ClanPlayer> modifiedClanPlayers = Collections.synchronizedSet(new HashSet<>());

    /**
     *
     */
    public StorageManager() {
        plugin = SimpleClans.getInstance();
        initiateDB();
        updateDatabase();
        importFromDatabase();
    }

    /**
     * Retrieve a player's pending chat lines
     *
     * @param player the Player
     * @return the ChatBlock
     */
    public ChatBlock getChatBlock(Player player) {
    	return chatBlocks.get(player.getName());
    }

    /**
     * Store pending chat lines for a player
     *
     */
    public void addChatBlock(CommandSender player, ChatBlock cb) {
		chatBlocks.put(player.getName(), cb);
    }

    /**
     * Initiates the db
     */
    public void initiateDB() {
        SettingsManager settings = plugin.getSettingsManager();
        if (settings.is(MYSQL_ENABLE)) {
            core = new MySQLCore(settings.getString(MYSQL_HOST), settings.getString(MYSQL_DATABASE), settings.getInt(MYSQL_PORT), settings.getString(MYSQL_USERNAME), settings.getString(MYSQL_PASSWORD));

            if (core.checkConnection()) {
                plugin.getLogger().info(lang("mysql.connection.successful"));

                if (!core.existsTable(getPrefixedTable("clans"))) {
                    plugin.getLogger().info("Creating table: " + getPrefixedTable("clans"));

                    String query = "CREATE TABLE IF NOT EXISTS `" + getPrefixedTable("clans") + "` ("
                            + " `id` bigint(20) NOT NULL auto_increment,"
                            + " `verified` tinyint(1) default '0',"
                            + " `tag` varchar(25) NOT NULL,"
                            + " `color_tag` varchar(255) NOT NULL,"
                            + " `name` varchar(100) NOT NULL,"
                            + " `description` varchar(255),"
                            + " `friendly_fire` tinyint(1) default '0',"
                            + " `founded` bigint NOT NULL,"
                            + " `last_used` bigint NOT NULL,"
                            + " `packed_allies` text NOT NULL,"
                            + " `packed_rivals` text NOT NULL,"
                            + " `packed_bb` mediumtext NOT NULL,"
                            + " `cape_url` varchar(255) NOT NULL,"
                            + " `flags` text NOT NULL,"
                    		+ " `balance` double(64,2),"
                    		+ " `fee_enabled` tinyint(1) default '0',"
                    		+ " `fee_value` double(64,2),"
                    		+ " `ranks` text NOT NULL,"
                            + " `banner` text,"
                    		+ " PRIMARY KEY  (`id`),"
                    		+ " UNIQUE KEY `uq_simpleclans_1` (`tag`));";
                    core.execute(query);
                }

                if (!core.existsTable(getPrefixedTable("players"))) {
                    plugin.getLogger().info("Creating table: " + getPrefixedTable("players"));

                    String query = "CREATE TABLE IF NOT EXISTS `" + getPrefixedTable("players") + "` ("
                    		+ " `id` bigint(20) NOT NULL auto_increment,"
                    		+ " `name` varchar(16) NOT NULL,"
                    		+ " `leader` tinyint(1) default '0',"
                    		+ " `tag` varchar(25) NOT NULL,"
                    		+ " `friendly_fire` tinyint(1) default '0',"
                    		+ " `neutral_kills` int(11) default NULL,"
                    		+ " `rival_kills` int(11) default NULL,"
                    		+ " `civilian_kills` int(11) default NULL,"
                            + " `ally_kills` int(11) default NULL,"
                    		+ " `deaths` int(11) default NULL,"
                    		+ " `last_seen` bigint NOT NULL,"
                    		+ " `join_date` bigint NOT NULL,"
                    		+ " `trusted` tinyint(1) default '0',"
                    		+ " `flags` text NOT NULL,"
                    		+ " `packed_past_clans` text,"
                    		+ " `resign_times` text,"
                            + " `locale` varchar(10),"
                    		+ " PRIMARY KEY  (`id`),"
                    		+ " UNIQUE KEY `uq_sc_players_1` (`name`));";
                    core.execute(query);
                }

                if (!core.existsTable(getPrefixedTable("kills"))) {
                    plugin.getLogger().info("Creating table: " + getPrefixedTable("kills"));

                    String query = "CREATE TABLE IF NOT EXISTS `" + getPrefixedTable("kills") + "` ("
                    		+ " `kill_id` bigint(20) NOT NULL auto_increment,"
                    		+ " `attacker` varchar(16) NOT NULL,"
                    		+ " `attacker_tag` varchar(16) NOT NULL,"
                    		+ " `victim` varchar(16) NOT NULL,"
                    		+ " `victim_tag` varchar(16) NOT NULL,"
                    		+ " `kill_type` varchar(1) NOT NULL,"
                            + " `created_at` datetime NULL,"
                    		+ " PRIMARY KEY  (`kill_id`));";
                    core.execute(query);
                }
            } else {
                plugin.getServer().getConsoleSender().sendMessage("[SimpleClans] " + ChatColor.RED + lang("mysql.connection.failed"));
            }
        } else {
            core = new SQLiteCore(plugin.getDataFolder().getPath());

            if (core.checkConnection()) {

            	plugin.getLogger().info(lang("sqlite.connection.successful"));

                if (!core.existsTable(getPrefixedTable("clans"))) {
                    plugin.getLogger().info("Creating table: " + getPrefixedTable("clans"));

                    String query = "CREATE TABLE IF NOT EXISTS `" + getPrefixedTable("clans") + "` ("
                            + " `id` bigint(20),"
                            + " `verified` tinyint(1) default '0',"
                            + " `tag` varchar(25) NOT NULL,"
                            + " `color_tag` varchar(255) NOT NULL,"
                            + " `name` varchar(100) NOT NULL,"
                            + " `description` varchar(255),"
                            + " `friendly_fire` tinyint(1) default '0',"
                            + " `founded` bigint NOT NULL,"
                            + " `last_used` bigint NOT NULL,"
                            + " `packed_allies` text NOT NULL,"
                            + " `packed_rivals` text NOT NULL,"
                            + " `packed_bb` mediumtext NOT NULL,"
                            + " `cape_url` varchar(255) NOT NULL,"
                            + " `flags` text NOT NULL,"
                    		+ " `balance` double(64,2) default 0.0,"
                    		+ " `fee_enabled` tinyint(1) default '0',"
                    		+ " `fee_value` double(64,2),"
                    		+ " `ranks` text NOT NULL,"
                            + " `banner` text,"
                    		+ "  PRIMARY KEY  (`id`),"
                    		+ " UNIQUE (`tag`));";
                    core.execute(query);
                }

                if (!core.existsTable(getPrefixedTable("players"))) {
                    plugin.getLogger().info("Creating table: " + getPrefixedTable("players"));

                    String query = "CREATE TABLE IF NOT EXISTS `" + getPrefixedTable("players") + "` ("
                    		+ " `id` bigint(20),"
                    		+ " `name` varchar(16) NOT NULL,"
                    		+ " `leader` tinyint(1) default '0',"
                    		+ " `tag` varchar(25) NOT NULL,"
                    		+ " `friendly_fire` tinyint(1) default '0',"
                    		+ " `neutral_kills` int(11) default NULL,"
                    		+ " `rival_kills` int(11) default NULL,"
                    		+ " `civilian_kills` int(11) default NULL,"
                            + " `ally_kills` int(11) default NULL,"
                    		+ " `deaths` int(11) default NULL,"
                    		+ " `last_seen` bigint NOT NULL,"
                    		+ " `join_date` bigint NOT NULL,"
                    		+ " `trusted` tinyint(1) default '0',"
                    		+ " `flags` text NOT NULL,"
                    		+ " `packed_past_clans` text,"
                    		+ " `resign_times` text,"
                            + " `locale` varchar(10),"
                    		+ " PRIMARY KEY  (`id`),"
                    		+ " UNIQUE (`name`));";
                    core.execute(query);
                }

                if (!core.existsTable(getPrefixedTable("kills"))) {
                    plugin.getLogger().info("Creating table: " + getPrefixedTable("kills"));

                    String query = "CREATE TABLE IF NOT EXISTS `" + getPrefixedTable("kills") + "` ("
                    		+ " `kill_id` bigint(20),"
                    		+ " `attacker` varchar(16) NOT NULL,"
                    		+ " `attacker_tag` varchar(16) NOT NULL,"
                    		+ " `victim` varchar(16) NOT NULL,"
                    		+ " `victim_tag` varchar(16) NOT NULL,"
                    		+ " `kill_type` varchar(1) NOT NULL,"
                            + " `created_at` datetime NULL,"
                    		+ " PRIMARY KEY  (`kill_id`));";
                    core.execute(query);
                }
            } else {
                plugin.getServer().getConsoleSender().sendMessage("[SimpleClans] " + ChatColor.RED + lang("sqlite.connection.failed"));
            }
        }

        if (core != null && core.checkConnection()) {
            createAllianceTables();
        }
    }

    /**
     * Closes DB connection
     */
    public void closeConnection() {
        core.close();
    }

    /**
     * Import all data from database to memory
     */
    public void importFromDatabase() {
        plugin.getClanManager().cleanData();

        List<Clan> clans = retrieveClans();
        purgeClans(clans);

        for (Clan clan : clans) {
            plugin.getClanManager().importClan(clan);
        }

        for (Clan clan : clans) {
            clan.validateWarring();
        }

        if (!clans.isEmpty()) {
        	plugin.getLogger().info(MessageFormat.format(lang("clans"), clans.size()));
        }

        List<ClanPlayer> cps = retrieveClanPlayers();
        purgeClanPlayers(cps);

        for (ClanPlayer cp : cps) {
            Clan tm = cp.getClan();

            if (tm != null) {
                tm.importMember(cp);
            }
            plugin.getClanManager().importClanPlayer(cp);
        }

        if (!cps.isEmpty()) {
        	plugin.getLogger().info(MessageFormat.format(lang("clan.players"), cps.size()));
        }
    }

    /**
     * Import one ClanPlayer data from database to memory
     * Used for BungeeCord Reload ClanPlayer and your Clan
     *
     */
    @Deprecated
    public void importFromDatabaseOnePlayer(Player player) {
        plugin.getClanManager().deleteClanPlayerFromMemory(player.getUniqueId());

        ClanPlayer cp = retrieveOneClanPlayer(player.getUniqueId());

        if (cp != null) {
            Clan tm = cp.getClan();

            if (tm != null) {
                tm.importMember(cp);
            }
            plugin.getClanManager().importClanPlayer(cp);

            plugin.getLogger().info("ClanPlayer Reloaded: " + player.getName() + ", UUID: " + player.getUniqueId());
        }
    }

    private void purgeClans(List<Clan> clans) {
        List<Clan> purge = new ArrayList<>();

        for (Clan clan : clans) {
            if (clan.isPermanent()) {
                continue;
            }
            if (clan.isVerified()) {
                int purgeClan = plugin.getSettingsManager().getInt(PURGE_INACTIVE_CLAN_DAYS);
                if (clan.getInactiveDays() > purgeClan && purgeClan > 0) {
                    purge.add(clan);
                }
            } else {
                int purgeUnverified = plugin.getSettingsManager().getInt(PURGE_UNVERIFIED_CLAN_DAYS);
                if (clan.getInactiveDays() > purgeUnverified && purgeUnverified > 0) {
                    purge.add(clan);
                }
            }
        }

        for (Clan clan : purge) {
        	plugin.getLogger().info(lang("purging.clan", clan.getName()));
            for (ClanPlayer member : clan.getMembers()) {
                clan.removePlayerFromClan(member.getUniqueId());
            }
            deleteClan(clan);
            clans.remove(clan);
        }
    }

    private void purgeClanPlayers(List<ClanPlayer> cps) {
        int purgePlayers = plugin.getSettingsManager().getInt(PURGE_INACTIVE_PLAYER_DAYS);
        if (purgePlayers < 1) {
            return;
        }
        List<ClanPlayer> purge = new ArrayList<>();

        for (ClanPlayer cp : cps) {
            //let the clan be purged first
            if (cp.isLeader() && cp.getClan() != null) {
                continue;
            }
            if (cp.getInactiveDays() > purgePlayers) {
                purge.add(cp);
            }
        }

        for (ClanPlayer cp : purge) {
        	plugin.getLogger().info(lang("purging.player.data", cp.getName()));
            deleteClanPlayer(cp);
            cps.remove(cp);
        }
    }

    /**
     * Retrieves all simple clans from the database
     *
     */
    public List<Clan> retrieveClans() {
        List<Clan> out = new ArrayList<>();
        Connection connection = core.getConnection();
        if (connection == null) {
            return out;
        }

        String query = "SELECT * FROM `" + getPrefixedTable("clans") + "`;";
        try (PreparedStatement pst = connection.prepareStatement(query);
             ResultSet res = pst.executeQuery()) {
            while (res.next()) {
                try {
                    boolean verified = res.getBoolean("verified");
                    boolean friendly_fire = res.getBoolean("friendly_fire");
                    String tag = res.getString("tag");
                    String color_tag = ChatUtils.parseColors(res.getString("color_tag"));
                    String name = res.getString("name");
                    String description = res.getString("description");
                    String packed_allies = res.getString("packed_allies");
                    String packed_rivals = res.getString("packed_rivals");
                    String packed_bb = res.getString("packed_bb");
                    String flags = res.getString("flags");
                    String ranksJson = res.getString("ranks");
                    long founded = res.getLong("founded");
                    long last_used = res.getLong("last_used");
                    double balance = res.getDouble("balance");
                    double feeValue = res.getDouble("fee_value");
                    boolean feeEnabled = res.getBoolean("fee_enabled");
                    ItemStack banner = YAMLSerializer.deserialize(res.getString("banner"), ItemStack.class);

                    if (founded == 0) {
                        founded = (new Date()).getTime();
                    }

                    if (last_used == 0) {
                        last_used = (new Date()).getTime();
                    }

                    Clan clan = new Clan();
                    clan.setFlags(flags);
                    clan.setVerified(verified);
                    clan.setFriendlyFire(friendly_fire);
                    clan.setTag(tag);
                    clan.setColorTag(color_tag);
                    clan.setName(name);
                    clan.setDescription(description);
                    clan.setPackedAllies(packed_allies);
                    clan.setPackedRivals(packed_rivals);
                    clan.setPackedBb(packed_bb);
                    clan.setFounded(founded);
                    clan.setLastUsed(last_used);
                    clan.setBalance(BankOperator.INTERNAL, ClanBalanceUpdateEvent.Cause.LOADING, BankLogger.Operation.SET, balance);
                    clan.setMemberFee(feeValue);
                    clan.setMemberFeeEnabled(feeEnabled);
                    clan.setRanks(Helper.ranksFromJson(ranksJson));
                    clan.setDefaultRank(Helper.defaultRankFromJson(ranksJson));
                    clan.setBanner(banner);

                    out.add(clan);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe(String.format("An Error occurred: %s", ex.getErrorCode()));
            plugin.getLogger().log(Level.SEVERE, null, ex);
        }

        return out;
    }

    /**
     * Retrieves one Clan from the database
     * Used for BungeeCord Reload ClanPlayer and your Clan
     */
    public @Nullable Clan retrieveOneClan(String tagClan) {
        Clan out = null;

        String query = "SELECT * FROM `" + getPrefixedTable("clans") + "` WHERE `tag` = ?;";
        try (PreparedStatement pst = core.getConnection().prepareStatement(query)) {
            pst.setString(1, tagClan);
            try (ResultSet res = pst.executeQuery()) {
                while (res.next()) {
                    try {
                        boolean verified = res.getBoolean("verified");
                        boolean friendly_fire = res.getBoolean("friendly_fire");
                        String tag = res.getString("tag");
                        String color_tag = ChatUtils.parseColors(res.getString("color_tag"));
                        String name = res.getString("name");
                        String description = res.getString("description");
                        String packed_allies = res.getString("packed_allies");
                        String packed_rivals = res.getString("packed_rivals");
                        String packed_bb = res.getString("packed_bb");
                        String flags = res.getString("flags");
                        String ranksJson = res.getString("ranks");
                        long founded = res.getLong("founded");
                        long last_used = res.getLong("last_used");
                        double balance = res.getDouble("balance");
                        double feeValue = res.getDouble("fee_value");
                        boolean feeEnabled = res.getBoolean("fee_enabled");
                        ItemStack banner = YAMLSerializer.deserialize(res.getString("banner"), ItemStack.class);

                        if (founded == 0) {
                            founded = (new Date()).getTime();
                        }

                        if (last_used == 0) {
                            last_used = (new Date()).getTime();
                        }

                        Clan clan = new Clan();
                        clan.setFlags(flags);
                        clan.setVerified(verified);
                        clan.setFriendlyFire(friendly_fire);
                        clan.setTag(tag);
                        clan.setColorTag(color_tag);
                        clan.setName(name);
                        clan.setDescription(description);
                        clan.setPackedAllies(packed_allies);
                        clan.setPackedRivals(packed_rivals);
                        clan.setPackedBb(packed_bb);
                        clan.setFounded(founded);
                        clan.setLastUsed(last_used);
                        clan.setBalance(BankOperator.INTERNAL, ClanBalanceUpdateEvent.Cause.LOADING, BankLogger.Operation.SET, balance);
                        clan.setMemberFee(feeValue);
                        clan.setMemberFeeEnabled(feeEnabled);
                        clan.setRanks(Helper.ranksFromJson(ranksJson));
                        clan.setDefaultRank(Helper.defaultRankFromJson(ranksJson));
                        clan.setBanner(banner);

                        out = clan;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe(String.format("An Error occurred: %s", ex.getErrorCode()));
            plugin.getLogger().log(Level.SEVERE, null, ex);
        }

        return out;
    }

    /**
     * Retrieves all clan players from the database
     *
     */
    public List<ClanPlayer> retrieveClanPlayers() {
        List<ClanPlayer> out = new ArrayList<>();
        Connection connection = core.getConnection();
        if (connection == null) {
            return out;
        }

        String query = "SELECT * FROM `" + getPrefixedTable("players") + "`;";
        try (PreparedStatement pst = connection.prepareStatement(query);
             ResultSet res = pst.executeQuery()) {
            while (res.next()) {
                try {
                    String uuid = res.getString("uuid");
                    String name = res.getString("name");
                    String tag = res.getString("tag");
                    boolean leader = res.getBoolean("leader");
                    boolean friendly_fire = res.getBoolean("friendly_fire");
                    boolean trusted = res.getBoolean("trusted");
                    int neutral_kills = res.getInt("neutral_kills");
                    int rival_kills = res.getInt("rival_kills");
                    int civilian_kills = res.getInt("civilian_kills");
                    int ally_kills = res.getInt("ally_kills");
                    int deaths = res.getInt("deaths");
                    long last_seen = res.getLong("last_seen");
                    long join_date = res.getLong("join_date");
                    String flags = res.getString("flags");
                    String packed_past_clans = ChatUtils.parseColors(res.getString("packed_past_clans"));
                    String resign_times = res.getString("resign_times");
                    Locale locale = Helper.forLanguageTag(res.getString("locale"));

                    if (last_seen == 0) {
                        last_seen = (new Date()).getTime();
                    }

                    ClanPlayer cp = new ClanPlayer();
                    if (uuid != null) {
                        cp.setUniqueId(UUID.fromString(uuid));
                    }
                    cp.setFlags(flags);
                    cp.setName(name);
                    cp.setLeader(leader);
                    cp.setFriendlyFire(friendly_fire);
                    cp.setNeutralKills(neutral_kills);
                    cp.setRivalKills(rival_kills);
                    cp.setCivilianKills(civilian_kills);
                    cp.setAllyKills(ally_kills);
                    cp.setDeaths(deaths);
                    cp.setLastSeen(last_seen);
                    cp.setJoinDate(join_date);
                    cp.setPackedPastClans(packed_past_clans);
                    cp.setTrusted(leader || trusted);
                    cp.setResignTimes(Helper.resignTimesFromJson(resign_times));
                    cp.setLocale(locale);

                    if (!tag.isEmpty()) {
                        Clan clan = plugin.getClanManager().getClan(tag);

                        if (clan != null) {
                            cp.setClan(clan);
                        }
                    }

                    out.add(cp);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe(String.format("An Error occurred: %s", ex.getErrorCode()));
            plugin.getLogger().log(Level.SEVERE, null, ex);
        }

        return out;
    }

    /**
     * Retrieves one clan player from the database
     * Used for BungeeCord Reload ClanPlayer and your Clan
     */
    public @Nullable ClanPlayer retrieveOneClanPlayer(UUID playerUniqueId) {
        ClanPlayer out = null;

        String query = "SELECT * FROM `" + getPrefixedTable("players") + "` WHERE `uuid` = ?;";
        try (PreparedStatement pst = core.getConnection().prepareStatement(query)) {
            pst.setString(1, playerUniqueId.toString());
            try (ResultSet res = pst.executeQuery()) {
                while (res.next()) {
                    try {
                        String uuid = res.getString("uuid");
                        String name = res.getString("name");
                        String tag = res.getString("tag");
                        boolean leader = res.getBoolean("leader");
                        boolean friendly_fire = res.getBoolean("friendly_fire");
                        boolean trusted = res.getBoolean("trusted");
                        int neutral_kills = res.getInt("neutral_kills");
                        int rival_kills = res.getInt("rival_kills");
                        int civilian_kills = res.getInt("civilian_kills");
                        int ally_kills = res.getInt("ally_kills");
                        int deaths = res.getInt("deaths");
                        long last_seen = res.getLong("last_seen");
                        long join_date = res.getLong("join_date");
                        String flags = res.getString("flags");
                        String packed_past_clans = ChatUtils.parseColors(res.getString("packed_past_clans"));
                        String resign_times = res.getString("resign_times");
                        Locale locale = Helper.forLanguageTag(res.getString("locale"));

                        if (last_seen == 0) {
                            last_seen = (new Date()).getTime();
                        }

                        ClanPlayer cp = new ClanPlayer();
                        if (uuid != null) {
                            cp.setUniqueId(UUID.fromString(uuid));
                        }
                        cp.setFlags(flags);
                        cp.setName(name);
                        cp.setLeader(leader);
                        cp.setFriendlyFire(friendly_fire);
                        cp.setNeutralKills(neutral_kills);
                        cp.setRivalKills(rival_kills);
                        cp.setCivilianKills(civilian_kills);
                        cp.setAllyKills(ally_kills);
                        cp.setDeaths(deaths);
                        cp.setLastSeen(last_seen);
                        cp.setJoinDate(join_date);
                        cp.setPackedPastClans(packed_past_clans);
                        cp.setTrusted(leader || trusted);
                        cp.setResignTimes(Helper.resignTimesFromJson(resign_times));
                        cp.setLocale(locale);

                        if (!tag.isEmpty()) {
                            Clan clanDB = retrieveOneClan(tag);
                            Clan clan = plugin.getClanManager().getClan(tag);

                            if (clan != null && clanDB != null) {
                                Clan clanReSync = SimpleClans.getInstance().getClanManager().getClan(tag);
                                clanReSync.setFlags(clanDB.getFlags());
                                clanReSync.setVerified(clanDB.isVerified());
                                clanReSync.setFriendlyFire(clanDB.isFriendlyFire());
                                clanReSync.setTag(clanDB.getTag());
                                clanReSync.setColorTag(clanDB.getColorTag());
                                clanReSync.setName(clanDB.getName());
                                clanReSync.setPackedAllies(clanDB.getPackedAllies());
                                clanReSync.setPackedRivals(clanDB.getPackedRivals());
                                clanReSync.setPackedBb(clanDB.getPackedBb());
                                clanReSync.setFounded(clanDB.getFounded());
                                clanReSync.setLastUsed(clanDB.getLastUsed());
                                clanReSync.setBalance(BankOperator.INTERNAL, ClanBalanceUpdateEvent.Cause.LOADING, BankLogger.Operation.SET, clanDB.getBalance());
                                cp.setClan(clanReSync);
                            } else {
                                plugin.getClanManager().importClan(clanDB);
                                clanDB.validateWarring();
                                Clan newClan = plugin.getClanManager().getClan(clanDB.getTag());
                                cp.setClan(newClan);
                            }
                        }

                        out = cp;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe(String.format("An Error occurred: %s", ex.getErrorCode()));
            plugin.getLogger().log(Level.SEVERE, null, ex);
        }

        return out;
    }

    /**
     * Insert a clan into the database
     *
     */
    public void insertClan(Clan clan) {
        plugin.getProxyManager().sendUpdate(clan);

        String sql = "INSERT INTO `" + getPrefixedTable("clans") + "` (`banner`, `ranks`, `description`, `fee_enabled`, `fee_value`, `verified`, `tag`," +
                " `color_tag`, `name`, `friendly_fire`, `founded`, `last_used`, `packed_allies`, `packed_rivals`, " +
                "`packed_bb`, `cape_url`, `flags`, `balance`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pst = core.getConnection().prepareStatement(sql)) {
            pst.setString(1, YAMLSerializer.serialize(clan.getBanner()));
            pst.setString(2, Helper.ranksToJson(clan.getRanks(), clan.getDefaultRank()));
            pst.setString(3, clan.getDescription());
            pst.setInt(4, clan.isMemberFeeEnabled() ? 1 : 0);
            pst.setDouble(5, clan.getMemberFee());
            pst.setInt(6, clan.isVerified() ? 1 : 0);
            pst.setString(7, clan.getTag());
            pst.setString(8, clan.getColorTag());
            pst.setString(9, clan.getName());
            pst.setInt(10, clan.isFriendlyFire() ? 1 : 0);
            pst.setLong(11, clan.getFounded());
            pst.setLong(12, clan.getLastUsed());
            pst.setString(13, clan.getPackedAllies());
            pst.setString(14, clan.getPackedRivals());
            pst.setString(15, clan.getPackedBb());
            pst.setString(16, clan.getCapeUrl());
            pst.setString(17, clan.getFlags());
            pst.setDouble(18, clan.getBalance());
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error inserting clan " + clan.getTag(), ex);
        }
    }

    /**
     * Update a clan to the database asynchronously
     *
     */
    @Deprecated
    public void updateClanAsync(final Clan clan) {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateClan(clan);
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Change the name of a player in the database asynchronously
     *
     * @param cp to update
     */
    public void updatePlayerNameAsync(final @NotNull ClanPlayer cp) {
    	new BukkitRunnable() {
			@Override
			public void run() {
                updatePlayerName(cp);
			}
		}.runTaskAsynchronously(plugin);
    }

    /**
     * Change the name of a player in the database
     *
     * @param cp to update
     */
    public void updatePlayerName(final @NotNull ClanPlayer cp) {
        String sql = "UPDATE `" + getPrefixedTable("players") + "` SET `name` = ? WHERE uuid = ?;";
        try (PreparedStatement pst = core.getConnection().prepareStatement(sql)) {
            pst.setString(1, cp.getName());
            pst.setString(2, cp.getUniqueId().toString());
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error updating player name for " + cp.getName(), ex);
        }
    }

    /**
     * Update a clan to the database
     *
     */
    public void updateClan(Clan clan) {
        updateClan(clan, true);
    }

    /**
     * Update a clan to the database
     *
     * @param clan clan to update
     *
     * @param updateLastUsed should the clan's last used time be updated as well?
     */
    public void updateClan(Clan clan, boolean updateLastUsed) {
        if (updateLastUsed) {
            clan.updateLastUsed();
        }
        plugin.getProxyManager().sendUpdate(clan);
        if (plugin.getSettingsManager().is(PERFORMANCE_SAVE_PERIODICALLY)) {
            modifiedClans.add(clan);
            return;
        }
        try (PreparedStatement st = prepareUpdateClanStatement(core.getConnection())) {
            setValues(st, clan);
            st.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, String.format("Error updating Clan %s", clan.getTag()), ex);
        }
    }

    private PreparedStatement prepareUpdateClanStatement(Connection connection) throws SQLException {
        String sql = "UPDATE `" + getPrefixedTable("clans") + "` SET ranks = ?, banner = ?, description = ?, fee_enabled = ?, fee_value = ?, " +
                "verified = ?, tag = ?, color_tag = ?, `name` = ?, friendly_fire = ?, founded = ?, last_used = ?, " +
                "packed_allies = ?, packed_rivals = ?, packed_bb = ?, balance = ?, flags = ? WHERE tag = ?;";
        return connection.prepareStatement(sql);
    }

    private void setValues(PreparedStatement statement, Clan clan) throws SQLException {
        statement.setString(1, Helper.ranksToJson(clan.getRanks(), clan.getDefaultRank()));
        statement.setString(2, YAMLSerializer.serialize(clan.getBanner()));
        statement.setString(3, clan.getDescription());
        statement.setInt(4, clan.isMemberFeeEnabled() ? 1 : 0);
        statement.setDouble(5, clan.getMemberFee());
        statement.setInt(6, clan.isVerified() ? 1 : 0);
        statement.setString(7, clan.getTag());
        statement.setString(8, clan.getColorTag());
        statement.setString(9, clan.getName());
        statement.setInt(10, clan.isFriendlyFire() ? 1 : 0);
        statement.setLong(11, clan.getFounded());
        statement.setLong(12, clan.getLastUsed());
        statement.setString(13, clan.getPackedAllies());
        statement.setString(14, clan.getPackedRivals());
        statement.setString(15, clan.getPackedBb());
        statement.setDouble(16, clan.getBalance());
        statement.setString(17, clan.getFlags());
        statement.setString(18, clan.getTag());
    }

    /**
     * Delete a clan from the database
     */
    public void deleteClan(Clan clan) {
        plugin.getProxyManager().sendDelete(clan);
        String sql = "DELETE FROM `" + getPrefixedTable("clans") + "` WHERE tag = ?;";
        try (PreparedStatement pst = core.getConnection().prepareStatement(sql)) {
            pst.setString(1, clan.getTag());
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting clan " + clan.getTag(), ex);
        }
    }

    /**
     * Insert a clan player into the database
     *
     */
    public void insertClanPlayer(ClanPlayer cp) {
        plugin.getProxyManager().sendUpdate(cp);

        String sql = "INSERT INTO `" + getPrefixedTable("players") + "` (`uuid`, `name`, `leader`, `tag`, `friendly_fire`, `neutral_kills`, " +
                "`rival_kills`, `civilian_kills`, `deaths`, `last_seen`, `join_date`, `packed_past_clans`, `flags`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pst = core.getConnection().prepareStatement(sql)) {
            pst.setString(1, cp.getUniqueId().toString());
            pst.setString(2, cp.getName());
            pst.setInt(3, cp.isLeader() ? 1 : 0);
            pst.setString(4, cp.getTag());
            pst.setInt(5, cp.isFriendlyFire() ? 1 : 0);
            pst.setInt(6, cp.getNeutralKills());
            pst.setInt(7, cp.getRivalKills());
            pst.setInt(8, cp.getCivilianKills());
            pst.setInt(9, cp.getDeaths());
            pst.setLong(10, cp.getLastSeen());
            pst.setLong(11, cp.getJoinDate());
            pst.setString(12, cp.getPackedPastClans());
            pst.setString(13, cp.getFlags());
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error inserting ClanPlayer " + cp.getName(), ex);
        }
    }

    /**
     * Update a clan player to the database asynchronously
     *
     */
    @Deprecated
    public void updateClanPlayerAsync(final ClanPlayer cp) {
    	new BukkitRunnable() {
			@Override
			public void run() {
                updateClanPlayer(cp);
			}
		}.runTaskAsynchronously(plugin);
    }

    /**
     * Update a clan player to the database
     *
     */
    public void updateClanPlayer(ClanPlayer cp) {
        cp.updateLastSeen();
        plugin.getProxyManager().sendUpdate(cp);
        if (plugin.getSettingsManager().is(PERFORMANCE_SAVE_PERIODICALLY)) {
            modifiedClanPlayers.add(cp);
            return;
        }
        Connection connection = core.getConnection();
        if (connection == null) {
            return;
        }
        try (PreparedStatement st = prepareUpdateClanPlayerStatement(connection)) {
            setValues(st, cp);
            st.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, String.format("Error updating ClanPlayer %s", cp.getName()), ex);
        }
    }

    private PreparedStatement prepareUpdateClanPlayerStatement(Connection connection) throws SQLException {
        String sql = "UPDATE `" + getPrefixedTable("players") + "` SET locale = ?, resign_times = ?, leader = ?, tag = ?, friendly_fire = ?," +
                " neutral_kills = ?, ally_kills = ?, rival_kills = ?, civilian_kills = ?, deaths = ?, last_seen = ?," +
                " packed_past_clans = ?, trusted = ?, flags = ?, `name` = ? WHERE `uuid` = ?;";
        return connection.prepareStatement(sql);
    }

    private void setValues(PreparedStatement statement, ClanPlayer cp) throws SQLException {
        statement.setString(1, Helper.toLanguageTag(cp.getLocale()));
        statement.setString(2, Helper.resignTimesToJson(cp.getResignTimes()));
        statement.setInt(3, cp.isLeader() ? 1 : 0);
        statement.setString(4, cp.getTag());
        statement.setInt(5, cp.isFriendlyFire() ? 1 : 0);
        statement.setInt(6, cp.getNeutralKills());
        statement.setInt(7, cp.getAllyKills());
        statement.setInt(8, cp.getRivalKills());
        statement.setInt(9, cp.getCivilianKills());
        statement.setInt(10, cp.getDeaths());
        statement.setLong(11, cp.getLastSeen());
        statement.setString(12, cp.getPackedPastClans());
        statement.setInt(13, cp.isTrusted() ? 1 : 0);
        statement.setString(14, cp.getFlags());
        statement.setString(15, cp.getName());
        statement.setString(16, cp.getUniqueId().toString());
    }

    /**
     * Delete a clan player from the database
     */
    public void deleteClanPlayer(ClanPlayer cp) {
        final Clan clan = cp.getClan();
        if (clan != null) {
            clan.addBbWithoutSaving(MessageFormat.format(lang("has.been.purged"), cp.getName()));
            updateClan(clan, false);
        }
        plugin.getProxyManager().sendDelete(cp);
        String sql = "DELETE FROM `" + getPrefixedTable("players") + "` WHERE uuid = ?;";
        try (PreparedStatement pst = core.getConnection().prepareStatement(sql)) {
            pst.setString(1, cp.getUniqueId().toString());
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting ClanPlayer " + cp.getName(), ex);
        }
        deleteKills(cp.getUniqueId());
    }

    /**
     * Insert a kill into the database
     *
     */
    @Deprecated
    public void insertKill(Player attacker, String attackerTag, Player victim, String victimTag, String type) {
        String sql = "INSERT INTO `" + getPrefixedTable("kills") + "` (`attacker_uuid`, `attacker`, `attacker_tag`, `victim_uuid`, `victim`, `victim_tag`, `kill_type`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pst = core.getConnection().prepareStatement(sql)) {
            pst.setString(1, attacker.getUniqueId().toString());
            pst.setString(2, attacker.getName());
            pst.setString(3, attackerTag);
            pst.setString(4, victim.getUniqueId().toString());
            pst.setString(5, victim.getName());
            pst.setString(6, victimTag);
            pst.setString(7, type);
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error inserting kill record", ex);
        }
    }

    /**
     * Insert a kill into the database
     *
     * @param attacker the attacker
     * @param victim the victim
     * @param type the kill type
     */
    public void insertKill(@NotNull ClanPlayer attacker, @NotNull ClanPlayer victim, @NotNull String type, @NotNull LocalDateTime time) {
        String sql = "INSERT INTO `" + getPrefixedTable("kills") + "` (`attacker_uuid`, `attacker`, `attacker_tag`, `victim_uuid`, " +
                "`victim`, `victim_tag`, `kill_type`, `created_at`) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pst = core.getConnection().prepareStatement(sql)) {
            pst.setString(1, attacker.getUniqueId().toString());
            pst.setString(2, attacker.getName());
            pst.setString(3, attacker.getTag());
            pst.setString(4, victim.getUniqueId().toString());
            pst.setString(5, victim.getName());
            pst.setString(6, victim.getTag());
            pst.setString(7, type);
            pst.setString(8, time.toString());
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error inserting kill record", ex);
        }
    }

    /**
     * Delete a player's kill record form the database
     *
     */
    @Deprecated
    public void deleteKills(String playerName) {
        // Delete all kill records where the player is either the attacker OR the victim to avoid orphans.
        String sql = "DELETE FROM `" + getPrefixedTable("kills") + "` WHERE `attacker` = ? OR `victim` = ?;";
        try (PreparedStatement pst = core.getConnection().prepareStatement(sql)) {
            pst.setString(1, playerName);
            pst.setString(2, playerName);
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting kills for player " + playerName, ex);
        }
    }

    /**
     * Delete a player's kill record from the database
     *
     */
    public void deleteKills(UUID playerUniqueId) {
        // Delete all kill records where the player is either the attacker OR the victim to avoid orphans.
        String sql = "DELETE FROM `" + getPrefixedTable("kills") + "` WHERE `attacker_uuid` = ? OR `victim_uuid` = ?;";
        try (PreparedStatement pst = core.getConnection().prepareStatement(sql)) {
            String id = playerUniqueId.toString();
            pst.setString(1, id);
            pst.setString(2, id);
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting kills for UUID " + playerUniqueId, ex);
        }
    }

    /**
     * Returns a map of victim-{@literal >}count of all kills that specific player did
     *
     * @param playerName the attacker name
     *
     * @return a map of kills per victim
     *
     */
    public Map<String, Integer> getKillsPerPlayer(String playerName) {
        HashMap<String, Integer> out = new HashMap<>();

        String query = "SELECT victim, count(victim) AS kills FROM `" + getPrefixedTable("kills") + "` WHERE attacker = ? GROUP BY victim ORDER BY count(victim) DESC;";
        try (PreparedStatement pst = core.getConnection().prepareStatement(query)) {
            pst.setString(1, playerName);
            try (ResultSet res = pst.executeQuery()) {
                while (res.next()) {
                    try {
                        String victim = res.getString("victim");
                        int kills = res.getInt("kills");
                        out.put(victim, kills);
                    } catch (Exception ex) {
                        plugin.getLogger().info(ex.getMessage());
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe(String.format("An Error occurred: %s", ex.getErrorCode()));
            plugin.getLogger().log(Level.SEVERE, null, ex);
        }

        return out;
    }

    /**
     * Returns a map of tag-{@literal >}count of all kills
     *
     * @return a map of kills per attacker+victim
     */
    public Map<String, Integer> getMostKilled() {
        HashMap<String, Integer> out = new HashMap<>();

        String query = "SELECT attacker, victim, count(victim) AS kills FROM `" + getPrefixedTable("kills") + "` GROUP BY attacker, victim ORDER BY 3 DESC;";
        try (PreparedStatement pst = core.getConnection().prepareStatement(query);
             ResultSet res = pst.executeQuery()) {
            while (res.next()) {
                try {
                    String attacker = res.getString("attacker");
                    String victim = res.getString("victim");
                    int kills = res.getInt("kills");
                    out.put(attacker + " " + victim, kills);
                } catch (Exception ex) {
                    plugin.getLogger().info(ex.getMessage());
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe(String.format("An Error occurred: %s", ex.getErrorCode()));
            plugin.getLogger().log(Level.SEVERE, null, ex);
        }

        return out;
    }

    /**
     * Gets, asynchronously, a map of tag-{@literal >}count of all kills and notifies via callback when it's ready
     *
     * @param callback the callback
     */
    public void getMostKilled(DataCallback<Map<String, Integer>> callback) {
    	new BukkitRunnable() {

			@Override
			public void run() {
				callback.onResultReady(getMostKilled());
			}
		}.runTaskAsynchronously(plugin);
    }

    /**
     * Gets, asynchronously, a map of victim-{@literal >}count of all kills that specific player did and notifies via callback when it's ready
     *
     */
    public void getKillsPerPlayer(final String playerName, final DataCallback<Map<String, Integer>> callback) {
    	new BukkitRunnable() {
			@Override
			public void run() {
				callback.onResultReady(getKillsPerPlayer(playerName));
			}
		}.runTaskAsynchronously(plugin);
    }

    /**
     * Callback that returns some data
     *
     * @author roinujnosde
     *
     */
    public interface DataCallback<T> {
    	/**
    	 * Notifies when the result is ready
    	 *
    	 */
    	void onResultReady(T data);
    }

    /**
     * Updates the database to the latest version
     *
     */
    private void updateDatabase() {
        String query;

        /*
         * From 2.2.6.3 to 2.3
         */
        if (!core.existsColumn(getPrefixedTable("clans"), "balance")) {
            query = "ALTER TABLE `" + getPrefixedTable("clans") + "` ADD COLUMN `balance` double(64,2);";
            core.execute(query);
        }

        /*
         * From 2.7.16 to 2.7.17
         */
        if (!core.existsColumn(getPrefixedTable("clans"), "fee_enabled")) {
            query = "ALTER TABLE `" + getPrefixedTable("clans") + "` ADD COLUMN `fee_enabled` tinyint(1) default '0';";
            core.execute(query);
        }
        if (!core.existsColumn(getPrefixedTable("clans"), "fee_value")) {
            query = "ALTER TABLE `" + getPrefixedTable("clans") + "` ADD COLUMN `fee_value` double(64,2);";
            core.execute(query);
        }

        /*
         * From 2.7.21 to 2.7.22
         */
        if (!core.existsColumn(getPrefixedTable("clans"), "description")) {
            query = "ALTER TABLE `" + getPrefixedTable("clans") + "` ADD COLUMN `description` varchar(255);";
        	core.execute(query);
        }

        /*
         * From 2.7.22 to 2.7.23
         */
        if (!core.existsColumn(getPrefixedTable("players"), "resign_times")) {
            query = "ALTER TABLE `" + getPrefixedTable("players") + "` ADD COLUMN `resign_times` text;";
        	core.execute(query);
        }

        /*
         * From 2.8.2 to 2.9
         */
        if (!core.existsColumn(getPrefixedTable("clans"), "ranks")) {
            query = "ALTER TABLE `" + getPrefixedTable("clans") + "` ADD COLUMN `ranks` text;";
        	core.execute(query);
        }

        // From 2.12.1 to 2.13.0
        if (!core.existsColumn(getPrefixedTable("players"), "locale")) {
            query = "ALTER TABLE `" + getPrefixedTable("players") + "` ADD COLUMN `locale` varchar(10);";
            core.execute(query);
        }
        if (!core.existsColumn(getPrefixedTable("clans"), "banner")) {
            core.execute("ALTER TABLE `" + getPrefixedTable("clans") + "` ADD COLUMN `banner` text;");
        }

        // From 2.15.1 to 2.15.2
        if (!core.existsColumn(getPrefixedTable("players"), "ally_kills")) {
            core.execute("ALTER TABLE `" + getPrefixedTable("players") + "` ADD COLUMN `ally_kills` int(11) DEFAULT NULL;");
        }

        if (plugin.getSettingsManager().is(MYSQL_ENABLE)) {
            core.execute("ALTER TABLE `" + getPrefixedTable("clans") + "` MODIFY color_tag VARCHAR(255);");
        }

        /*
         * Bukkit 1.7.5+ UUID Migration
         */
        if (!core.existsColumn(getPrefixedTable("kills"), "attacker_uuid")) {
            query = "ALTER TABLE `" + getPrefixedTable("kills") + "` ADD attacker_uuid VARCHAR( 255 ) DEFAULT NULL;";
            core.execute(query);
        }
        if (!core.existsColumn(getPrefixedTable("kills"), "victim_uuid")) {
            query = "ALTER TABLE `" + getPrefixedTable("kills") + "` ADD victim_uuid VARCHAR( 255 ) DEFAULT NULL;";
            core.execute(query);
        }
        boolean useMysql = plugin.getSettingsManager().is(MYSQL_ENABLE);
        if (!core.existsColumn(getPrefixedTable("players"), "uuid")) {
            query = "ALTER TABLE `" + getPrefixedTable("players") + "` ADD uuid VARCHAR( 255 ) DEFAULT NULL;";
            core.execute(query);

            if (useMysql) {
                query = "ALTER TABLE `" + getPrefixedTable("players") + "` ADD UNIQUE `uq_player_uuid` (`uuid`);";
                core.execute(query);
            }

            updatePlayersToUUID();

            if (useMysql) {
                query = "ALTER TABLE `" + getPrefixedTable("players") + "` DROP INDEX uq_sc_players_1;";
            } else {
                query = "DROP INDEX IF EXISTS uq_sc_players_1;";
            }
            core.execute(query);
        }

        if (core.existsColumn(getPrefixedTable("players"), "uuid") && !useMysql) {
            query = "CREATE UNIQUE INDEX IF NOT EXISTS `uq_player_uuid` ON `" + getPrefixedTable("players") + "` (`uuid`);";
            core.execute(query);
        }

        // From 2.19.3 to 2.20.0
        if (!core.existsColumn(getPrefixedTable("kills"), "created_at")) {
            query = "ALTER TABLE `" + getPrefixedTable("kills") + "` ADD `created_at` datetime NULL;";
            core.execute(query);
        }
    }

    /**
     * Updates the database to the latest version
     *
     */
	private void updatePlayersToUUID() {
        logMigrationStart();

        List<ClanPlayer> cps = retrieveClanPlayers();
        Map<String, UUID> uuidMap = fetchUUIDs(cps);

        int totalPlayers = cps.size();
        for (int i = 0; i < totalPlayers; i++) {
            ClanPlayer cp = cps.get(i);
            try {
                UUID uuid = uuidMap.get(cp.getName());
                if (uuid != null) {
                    updatePlayerInDatabase(cp.getName(), uuid);
                    logSuccess(i + 1, totalPlayers, cp.getName(), uuid);
                }
            } catch (Exception ex) {
                logFailure(i + 1, totalPlayers, cp.getName(), ex);
            }
        }

        logMigrationEnd(totalPlayers);
    }

    private void updatePlayerInDatabase(String playerName, UUID uuid) {
        String[][] updates = {
            {"players", "uuid", "name"},
            {"kills",   "attacker_uuid", "attacker"},
            {"kills",   "victim_uuid",   "victim"}
        };

        for (String[] row : updates) {
            String sql = "UPDATE `" + getPrefixedTable(row[0]) + "` SET `" + row[1] + "` = ? WHERE `" + row[2] + "` = ?;";
            try (PreparedStatement pst = core.getConnection().prepareStatement(sql)) {
                pst.setString(1, uuid.toString());
                pst.setString(2, playerName);
                pst.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Error updating player UUID in database for " + playerName, ex);
            }
        }
    }

    private Map<String, UUID> fetchUUIDs(List<ClanPlayer> clanPlayers) {
        Map<String, UUID> uuidMap = new HashMap<>();

        try {
            if (SimpleClans.getInstance().getServer().getOnlineMode()) {
                uuidMap = UUIDFetcher.fetchUUIDsForClanPlayers(clanPlayers);
            } else {
                uuidMap = clanPlayers.stream().collect(Collectors.toMap(ClanPlayer::getName, player ->
                        UUID.nameUUIDFromBytes(("OfflinePlayer:" + player.getName()).getBytes(StandardCharsets.UTF_8))));
            }
        } catch (InterruptedException | ExecutionException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error fetching UUIDs in bulk: " + ex.getMessage(), ex);
        }

        return uuidMap;
    }

    private void logSuccess(int current, int total, String playerName, UUID uuid) {
        plugin.getLogger().info(String.format("[%d / %d] Success: %s; UUID: %s", current, total, playerName, uuid));
    }

    private void logFailure(int current, int total, String playerName, Exception ex) {
        plugin.getLogger().log(Level.WARNING, String.format("[%d / %d] Failed [ERROR]: %s; UUID: ???", current, total, playerName), ex);
    }

    private void logMigrationStart() {
        plugin.getLogger().log(Level.WARNING, "Starting Migration to UUID Players!");
        plugin.getLogger().log(Level.WARNING, "==================== ATTENTION DON'T STOP BUKKIT! ====================");
        plugin.getLogger().log(Level.WARNING, "==================== ATTENTION DON'T STOP BUKKIT! ====================");
        plugin.getLogger().log(Level.WARNING, "==================== ATTENTION DON'T STOP BUKKIT! ====================");
    }

    private void logMigrationEnd(int totalPlayers) {
        plugin.getLogger().log(Level.WARNING, "==================== END OF MIGRATION ====================");
        plugin.getLogger().log(Level.WARNING, "==================== END OF MIGRATION ====================");
        plugin.getLogger().log(Level.WARNING, "==================== END OF MIGRATION ====================");

        if (totalPlayers > 0) {
            plugin.getLogger().info(MessageFormat.format(lang("clan.players"), totalPlayers));
        }
    }

    private String getPrefixedTable(String name) {
        return plugin.getSettingsManager().getString(MYSQL_TABLE_PREFIX) + name;
    }

	/**
	 * Saves modified Clans and ClanPlayers to the database
     * @since 2.10.2
     *
     * <p>
     * author: RoinujNosde
     * </p>
	 */
	public void saveModified() {
        Connection connection = core.getConnection();
        if (connection == null) {
            return;
        }
        // Synchronize the entire retainAll + iteration + clear sequence on each set so the
        // async SaveDataTask and main-thread writes cannot interleave on a plain HashSet.
        synchronized (modifiedClanPlayers) {
            try (PreparedStatement pst = prepareUpdateClanPlayerStatement(connection)) {
                //removing purged players
                modifiedClanPlayers.retainAll(plugin.getClanManager().getAllClanPlayers());
                for (ClanPlayer cp : modifiedClanPlayers) {
                    setValues(pst, cp);
                    pst.addBatch();
                }
                pst.executeBatch();
                modifiedClanPlayers.clear();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Error saving modified ClanPlayers:", ex);
            }
        }
        synchronized (modifiedClans) {
            try (PreparedStatement pst = prepareUpdateClanStatement(connection)) {
                //removing disbanded clans
                modifiedClans.retainAll(plugin.getClanManager().getClans());
                for (Clan clan : modifiedClans) {
                    setValues(pst, clan);
                    pst.addBatch();
                }
                pst.executeBatch();
                modifiedClans.clear();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Error saving modified Clans:", ex);
            }
        }
    }

    // ===================================================================================
    // Alliance (NATO / SCO) persistence
    //
    // Alliance-level state lives in two dedicated tables, fully separate from the
    // clans/players/kills tables. Per-clan alliance data (HQ, rejoin cooldown) lives
    // in the clan `flags` blob and is saved with the clan. Each row is scoped by
    // `alliance_type`, so NATO and SCO never share storage.
    // ===================================================================================

    /**
     * Creates the alliance tables if absent. The DDL is intentionally written to be
     * valid on both SQLite and MySQL (backticked identifiers, portable column types,
     * composite primary keys, no auto-increment).
     */
    private void createAllianceTables() {
        if (!core.existsTable(getPrefixedTable("alliances"))) {
            plugin.getLogger().info("Creating table: " + getPrefixedTable("alliances"));
            String query = "CREATE TABLE IF NOT EXISTS `" + getPrefixedTable("alliances") + "` ("
                    + " `alliance_type` varchar(10) NOT NULL,"
                    + " `rotation_index` int NOT NULL DEFAULT 0,"
                    + " `max_allies_per_war` int NOT NULL DEFAULT 1,"
                    + " PRIMARY KEY (`alliance_type`));";
            core.execute(query);
        }
        if (!core.existsTable(getPrefixedTable("alliance_members"))) {
            plugin.getLogger().info("Creating table: " + getPrefixedTable("alliance_members"));
            String query = "CREATE TABLE IF NOT EXISTS `" + getPrefixedTable("alliance_members") + "` ("
                    + " `alliance_type` varchar(10) NOT NULL,"
                    + " `clan_tag` varchar(25) NOT NULL,"
                    + " `join_order` int NOT NULL,"
                    + " `joined_date` bigint NOT NULL,"
                    + " PRIMARY KEY (`alliance_type`,`clan_tag`));";
            core.execute(query);
        }
        createAllianceMeetingSchema();
    }

    /**
     * Creates the meeting/proposal/vote schema and adds the meeting-state columns to
     * the alliances table (ALTER ADD COLUMN works on both SQLite and MySQL). Called
     * after the base alliance tables so upgrades from an earlier build are handled.
     */
    private void createAllianceMeetingSchema() {
        if (!core.existsTable(getPrefixedTable("alliance_proposals"))) {
            plugin.getLogger().info("Creating table: " + getPrefixedTable("alliance_proposals"));
            String query = "CREATE TABLE IF NOT EXISTS `" + getPrefixedTable("alliance_proposals") + "` ("
                    + " `id` bigint NOT NULL,"
                    + " `alliance_type` varchar(10) NOT NULL,"
                    + " `type` varchar(16) NOT NULL,"
                    + " `proposer_tag` varchar(25) NOT NULL,"
                    + " `proposer_name` varchar(64) NOT NULL,"
                    + " `created_at` bigint NOT NULL,"
                    + " `target_tag` varchar(25),"
                    + " `number_value` int NOT NULL DEFAULT 0,"
                    + " `text` varchar(255),"
                    + " `status` varchar(16) NOT NULL,"
                    + " `final_agree` int NOT NULL DEFAULT 0,"
                    + " `final_disagree` int NOT NULL DEFAULT 0,"
                    + " PRIMARY KEY (`id`));";
            core.execute(query);
        }
        if (!core.existsTable(getPrefixedTable("alliance_votes"))) {
            plugin.getLogger().info("Creating table: " + getPrefixedTable("alliance_votes"));
            String query = "CREATE TABLE IF NOT EXISTS `" + getPrefixedTable("alliance_votes") + "` ("
                    + " `proposal_id` bigint NOT NULL,"
                    + " `clan_tag` varchar(25) NOT NULL,"
                    + " `agree` tinyint(1) NOT NULL,"
                    + " PRIMARY KEY (`proposal_id`,`clan_tag`));";
            core.execute(query);
        }
        // Meeting-state columns live on the one-row-per-type alliances table.
        String alliances = getPrefixedTable("alliances");
        if (core.existsTable(alliances)) {
            addColumnIfMissing(alliances, "meeting_active", "tinyint(1) NOT NULL DEFAULT 0");
            addColumnIfMissing(alliances, "meeting_start", "bigint NOT NULL DEFAULT 0");
            addColumnIfMissing(alliances, "meeting_end", "bigint NOT NULL DEFAULT 0");
            addColumnIfMissing(alliances, "host_tag", "varchar(25)");
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        if (!core.existsColumn(table, column)) {
            plugin.getLogger().info("Adding column " + column + " to " + table);
            core.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition + ";");
        }
    }

    /**
     * Loads alliance rows and membership (ordered by join order) into the manager.
     */
    public void loadAlliances(@NotNull AllianceManager manager) {
        Connection connection = core.getConnection();
        if (connection == null) {
            return;
        }

        // Alliance-level rows (rotation index + max allies per war).
        String allianceQuery = "SELECT `alliance_type`, `rotation_index`, `max_allies_per_war` FROM `"
                + getPrefixedTable("alliances") + "`;";
        try (PreparedStatement pst = connection.prepareStatement(allianceQuery);
             ResultSet res = pst.executeQuery()) {
            while (res.next()) {
                AllianceType type = AllianceType.fromString(res.getString("alliance_type"));
                if (type == null) {
                    continue;
                }
                Alliance alliance = manager.getAlliance(type);
                alliance.setRotationIndex(res.getInt("rotation_index"));
                alliance.setMaxAlliesPerWar(res.getInt("max_allies_per_war"));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error loading alliances", ex);
        }

        // Membership, ordered by join order so the host rotation is correct.
        String memberQuery = "SELECT `alliance_type`, `clan_tag`, `joined_date` FROM `"
                + getPrefixedTable("alliance_members") + "` ORDER BY `join_order` ASC;";
        Map<AllianceType, List<String>> members = new EnumMap<>(AllianceType.class);
        Map<AllianceType, Map<String, Long>> joinDates = new EnumMap<>(AllianceType.class);
        for (AllianceType type : AllianceType.values()) {
            members.put(type, new ArrayList<>());
            joinDates.put(type, new HashMap<>());
        }
        try (PreparedStatement pst = connection.prepareStatement(memberQuery);
             ResultSet res = pst.executeQuery()) {
            while (res.next()) {
                AllianceType type = AllianceType.fromString(res.getString("alliance_type"));
                if (type == null) {
                    continue;
                }
                String tag = res.getString("clan_tag");
                members.get(type).add(tag);
                joinDates.get(type).put(tag, res.getLong("joined_date"));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error loading alliance members", ex);
        }
        for (AllianceType type : AllianceType.values()) {
            Alliance alliance = manager.getAlliance(type);
            alliance.setMemberTags(members.get(type));
            for (Map.Entry<String, Long> entry : joinDates.get(type).entrySet()) {
                alliance.setJoinDate(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * @return the next join-order value for the given alliance (max + 1, or 0 if empty)
     */
    public int getNextAllianceJoinOrder(@NotNull AllianceType type) {
        Connection connection = core.getConnection();
        if (connection == null) {
            return 0;
        }
        String query = "SELECT MAX(`join_order`) AS max_order FROM `" + getPrefixedTable("alliance_members")
                + "` WHERE `alliance_type` = ?;";
        try (PreparedStatement pst = connection.prepareStatement(query)) {
            pst.setString(1, type.getId());
            try (ResultSet res = pst.executeQuery()) {
                if (res.next()) {
                    int max = res.getInt("max_order");
                    if (!res.wasNull()) {
                        return max + 1;
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error reading alliance join order", ex);
        }
        return 0;
    }

    public void insertAllianceMember(@NotNull AllianceType type, @NotNull String clanTag, int joinOrder, long joinedDate) {
        Connection connection = core.getConnection();
        if (connection == null) {
            return;
        }
        String query = "INSERT INTO `" + getPrefixedTable("alliance_members")
                + "` (`alliance_type`, `clan_tag`, `join_order`, `joined_date`) VALUES (?, ?, ?, ?);";
        try (PreparedStatement pst = connection.prepareStatement(query)) {
            pst.setString(1, type.getId());
            pst.setString(2, clanTag);
            pst.setInt(3, joinOrder);
            pst.setLong(4, joinedDate);
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error inserting alliance member", ex);
        }
    }

    public void deleteAllianceMember(@NotNull AllianceType type, @NotNull String clanTag) {
        Connection connection = core.getConnection();
        if (connection == null) {
            return;
        }
        String query = "DELETE FROM `" + getPrefixedTable("alliance_members")
                + "` WHERE `alliance_type` = ? AND `clan_tag` = ?;";
        try (PreparedStatement pst = connection.prepareStatement(query)) {
            pst.setString(1, type.getId());
            pst.setString(2, clanTag);
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting alliance member", ex);
        }
    }

    /**
     * Upserts the alliance-level row (rotation index + max allies). Implemented as
     * delete-then-insert so it is portable across SQLite and MySQL.
     */
    public void saveAllianceState(@NotNull AllianceType type, int rotationIndex, int maxAlliesPerWar) {
        Connection connection = core.getConnection();
        if (connection == null) {
            return;
        }
        String delete = "DELETE FROM `" + getPrefixedTable("alliances") + "` WHERE `alliance_type` = ?;";
        String insert = "INSERT INTO `" + getPrefixedTable("alliances")
                + "` (`alliance_type`, `rotation_index`, `max_allies_per_war`) VALUES (?, ?, ?);";
        try (PreparedStatement del = connection.prepareStatement(delete)) {
            del.setString(1, type.getId());
            del.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error clearing alliance state", ex);
            return;
        }
        try (PreparedStatement ins = connection.prepareStatement(insert)) {
            ins.setString(1, type.getId());
            ins.setInt(2, rotationIndex);
            ins.setInt(3, maxAlliesPerWar);
            ins.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error saving alliance state", ex);
        }
    }

    // ===================================================================================
    // Alliance meetings, proposals and votes (Phase 2)
    // ===================================================================================

    /**
     * Loads meeting state, proposals and votes into the meeting manager. Called once
     * on startup before the meeting scheduler begins ticking.
     */
    public void loadAllianceMeetingData(@NotNull AllianceMeetingManager manager) {
        Connection connection = core.getConnection();
        if (connection == null) {
            return;
        }

        // Meeting state per alliance (columns on the alliances table).
        String meetingQuery = "SELECT `alliance_type`, `meeting_active`, `meeting_start`, `meeting_end`, `host_tag` FROM `"
                + getPrefixedTable("alliances") + "`;";
        try (PreparedStatement pst = connection.prepareStatement(meetingQuery);
             ResultSet res = pst.executeQuery()) {
            while (res.next()) {
                AllianceType type = AllianceType.fromString(res.getString("alliance_type"));
                if (type == null) {
                    continue;
                }
                MeetingState state = manager.getMeetingState(type);
                state.setActive(res.getBoolean("meeting_active"));
                state.setStartTime(res.getLong("meeting_start"));
                state.setEndTime(res.getLong("meeting_end"));
                state.setHostTag(res.getString("host_tag"));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error loading alliance meeting state", ex);
        }

        // Proposals.
        String proposalQuery = "SELECT * FROM `" + getPrefixedTable("alliance_proposals") + "` ORDER BY `id` ASC;";
        try (PreparedStatement pst = connection.prepareStatement(proposalQuery);
             ResultSet res = pst.executeQuery()) {
            while (res.next()) {
                AllianceType type = AllianceType.fromString(res.getString("alliance_type"));
                ProposalType ptype = parseProposalType(res.getString("type"));
                Proposal.Status status = parseStatus(res.getString("status"));
                if (type == null || ptype == null || status == null) {
                    continue;
                }
                Proposal proposal = new Proposal(res.getInt("id"), type, ptype,
                        res.getString("proposer_tag"), res.getString("proposer_name"),
                        res.getLong("created_at"), res.getString("target_tag"),
                        res.getInt("number_value"), res.getString("text"), status);
                proposal.setFinalTally(res.getInt("final_agree"), res.getInt("final_disagree"));
                manager.addLoadedProposal(proposal);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error loading alliance proposals", ex);
        }

        // Votes (attach to already-loaded proposals).
        String voteQuery = "SELECT `proposal_id`, `clan_tag`, `agree` FROM `" + getPrefixedTable("alliance_votes") + "`;";
        try (PreparedStatement pst = connection.prepareStatement(voteQuery);
             ResultSet res = pst.executeQuery()) {
            while (res.next()) {
                Proposal proposal = manager.getProposalById(res.getInt("proposal_id"));
                if (proposal != null) {
                    proposal.setVote(res.getString("clan_tag"), res.getBoolean("agree"));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error loading alliance votes", ex);
        }
    }

    public int getNextProposalId() {
        Connection connection = core.getConnection();
        if (connection == null) {
            return 1;
        }
        String query = "SELECT MAX(`id`) AS max_id FROM `" + getPrefixedTable("alliance_proposals") + "`;";
        try (PreparedStatement pst = connection.prepareStatement(query);
             ResultSet res = pst.executeQuery()) {
            if (res.next()) {
                int max = res.getInt("max_id");
                if (!res.wasNull()) {
                    return max + 1;
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error reading proposal id", ex);
        }
        return 1;
    }

    public void insertProposal(@NotNull Proposal proposal) {
        Connection connection = core.getConnection();
        if (connection == null) {
            return;
        }
        String query = "INSERT INTO `" + getPrefixedTable("alliance_proposals") + "` (`id`, `alliance_type`, `type`,"
                + " `proposer_tag`, `proposer_name`, `created_at`, `target_tag`, `number_value`, `text`, `status`,"
                + " `final_agree`, `final_disagree`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pst = connection.prepareStatement(query)) {
            pst.setInt(1, proposal.getId());
            pst.setString(2, proposal.getAlliance().getId());
            pst.setString(3, proposal.getType().name());
            pst.setString(4, proposal.getProposerTag());
            pst.setString(5, proposal.getProposerName());
            pst.setLong(6, proposal.getCreatedAt());
            pst.setString(7, proposal.getTargetTag());
            pst.setInt(8, proposal.getNumberValue());
            pst.setString(9, proposal.getText());
            pst.setString(10, proposal.getStatus().name());
            pst.setInt(11, proposal.getFinalAgree());
            pst.setInt(12, proposal.getFinalDisagree());
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error inserting alliance proposal", ex);
        }
    }

    public void updateProposal(@NotNull Proposal proposal) {
        Connection connection = core.getConnection();
        if (connection == null) {
            return;
        }
        String query = "UPDATE `" + getPrefixedTable("alliance_proposals")
                + "` SET `status` = ?, `final_agree` = ?, `final_disagree` = ? WHERE `id` = ?;";
        try (PreparedStatement pst = connection.prepareStatement(query)) {
            pst.setString(1, proposal.getStatus().name());
            pst.setInt(2, proposal.getFinalAgree());
            pst.setInt(3, proposal.getFinalDisagree());
            pst.setInt(4, proposal.getId());
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error updating alliance proposal", ex);
        }
    }

    /** Upserts a single clan's vote on a proposal (delete-then-insert for portability). */
    public void saveVote(int proposalId, @NotNull String clanTag, boolean agree) {
        Connection connection = core.getConnection();
        if (connection == null) {
            return;
        }
        deleteVote(proposalId, clanTag);
        String query = "INSERT INTO `" + getPrefixedTable("alliance_votes")
                + "` (`proposal_id`, `clan_tag`, `agree`) VALUES (?, ?, ?);";
        try (PreparedStatement pst = connection.prepareStatement(query)) {
            pst.setInt(1, proposalId);
            pst.setString(2, clanTag);
            pst.setBoolean(3, agree);
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error saving alliance vote", ex);
        }
    }

    public void deleteVote(int proposalId, @NotNull String clanTag) {
        Connection connection = core.getConnection();
        if (connection == null) {
            return;
        }
        String query = "DELETE FROM `" + getPrefixedTable("alliance_votes")
                + "` WHERE `proposal_id` = ? AND `clan_tag` = ?;";
        try (PreparedStatement pst = connection.prepareStatement(query)) {
            pst.setInt(1, proposalId);
            pst.setString(2, clanTag);
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting alliance vote", ex);
        }
    }

    public void deleteVotesForProposal(int proposalId) {
        Connection connection = core.getConnection();
        if (connection == null) {
            return;
        }
        String query = "DELETE FROM `" + getPrefixedTable("alliance_votes") + "` WHERE `proposal_id` = ?;";
        try (PreparedStatement pst = connection.prepareStatement(query)) {
            pst.setInt(1, proposalId);
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting alliance votes", ex);
        }
    }

    public void saveMeetingState(@NotNull AllianceType type, boolean active, long start, long end, @Nullable String hostTag) {
        Connection connection = core.getConnection();
        if (connection == null) {
            return;
        }
        String query = "UPDATE `" + getPrefixedTable("alliances")
                + "` SET `meeting_active` = ?, `meeting_start` = ?, `meeting_end` = ?, `host_tag` = ? WHERE `alliance_type` = ?;";
        try (PreparedStatement pst = connection.prepareStatement(query)) {
            pst.setBoolean(1, active);
            pst.setLong(2, start);
            pst.setLong(3, end);
            pst.setString(4, hostTag);
            pst.setString(5, type.getId());
            pst.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error saving meeting state", ex);
        }
    }

    @Nullable
    private ProposalType parseProposalType(@Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            return ProposalType.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Nullable
    private Proposal.Status parseStatus(@Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            return Proposal.Status.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
