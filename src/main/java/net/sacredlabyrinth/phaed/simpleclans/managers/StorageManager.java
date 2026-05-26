package net.sacredlabyrinth.phaed.simpleclans.managers;

import net.sacredlabyrinth.phaed.simpleclans.*;
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

        String query = "SELECT * FROM `" + getPrefixedTable("clans") + "`;";
        try (PreparedStatement pst = core.getConnection().prepareStatement(query);
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

        String query = "SELECT * FROM `" + getPrefixedTable("players") + "`;";
        try (PreparedStatement pst = core.getConnection().prepareStatement(query);
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
        try (PreparedStatement st = prepareUpdateClanPlayerStatement(core.getConnection())) {
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
        // Synchronize the entire retainAll + iteration + clear sequence on each set so the
        // async SaveDataTask and main-thread writes cannot interleave on a plain HashSet.
        synchronized (modifiedClanPlayers) {
            try (PreparedStatement pst = prepareUpdateClanPlayerStatement(core.getConnection())) {
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
            try (PreparedStatement pst = prepareUpdateClanStatement(core.getConnection())) {
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
}
