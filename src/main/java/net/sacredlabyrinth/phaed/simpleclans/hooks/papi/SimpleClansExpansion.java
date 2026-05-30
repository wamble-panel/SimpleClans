package net.sacredlabyrinth.phaed.simpleclans.hooks.papi;

import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.Helper;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.alliance.Alliance;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceManager;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceMeetingManager;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceType;
import net.sacredlabyrinth.phaed.simpleclans.alliance.Proposal;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleClansExpansion extends PlaceholderExpansion implements Relational, Configurable {

    private static final Pattern TOP_CLANS_PATTERN = Pattern.compile("(?<strip>^topclans_(?<position>\\d+)_)clan_");
    private static final Pattern TOP_PLAYERS_PATTERN = Pattern.compile("(?<strip>^topplayers_(?<position>\\d+)_)");
    private static final Map<String, PlaceholderResolver> RESOLVERS = new HashMap<>();
    private List<String> placeholders;
    private final SimpleClans plugin;
    private final ClanManager clanManager;

    public SimpleClansExpansion(SimpleClans plugin) {
        this.plugin = plugin;
        clanManager = plugin.getClanManager();
        registerResolvers();
    }

    @Override
    public @NotNull String getName() {
        return plugin.getName();
    }

    @Override
    public @NotNull String getIdentifier() {
        return getName().toLowerCase();
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull List<String> getPlaceholders() {
        if (placeholders == null) {
            this.placeholders = new ArrayList<>();
            addPlaceholders("simpleclans_", ClanPlayer.class, placeholders);
            addPlaceholders("simpleclans_clan_", Clan.class, placeholders);
        }
        return placeholders;
    }

    @Override
    public boolean canRegister() {
        return true;
    }


    @Override
    public Map<String, Object> getDefaults() {
        Map<String, Object> defaults = new HashMap<>();

        defaults.put("color.rival", "&c");
        defaults.put("color.ally", "&b");
        defaults.put("color.same_clan", "&a");

        return defaults;
    }

    @Override
    @Nullable
    public String onPlaceholderRequest(Player player1, Player player2, String params) {
        if (player1 == null || player2 == null) {
            return null;
        }
        if (params.equalsIgnoreCase("color")) {
            ClanPlayer cp1 = clanManager.getClanPlayer(player1);
            if (cp1 == null) {
                return "";
            }
            //noinspection ConstantConditions -- getClanPlayer != null == getClan() != null
            if (cp1.getClan().isMember(player2)) {
                return getString("color.same_clan", null);
            }
            if (cp1.isRival(player2)) {
                return getString("color.rival", null);
            }
            if (cp1.isAlly(player2)) {
                return getString("color.ally", null);
            }
            return "";
        }
        return null;
    }

    @Override
    public String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        // Alliance placeholders are handled independently of the reflection-based system.
        if (params.startsWith("alliance_")) {
            ClanPlayer cp = player != null ? clanManager.getAnyClanPlayer(player.getUniqueId()) : null;
            Clan clan = cp != null ? cp.getClan() : null;
            return resolveAlliancePlaceholder(clan, params.substring("alliance_".length()));
        }

        ClanPlayer cp = null;
        if (player != null) {
            cp = clanManager.getAnyClanPlayer(player.getUniqueId());
        }
        if (cp == null) {
            return "";
        }
        Clan clan = cp.getClan();
        Matcher matcher = TOP_CLANS_PATTERN.matcher(params);
        if (matcher.find()) {
            int position = Integer.parseInt(matcher.group("position"));
            clan = getFromPosition(clanManager.getClans(), position, clanManager::sortClansByKDR);
            params = params.replace(matcher.group("strip"), "");
        }
        matcher = TOP_PLAYERS_PATTERN.matcher(params);
        if (matcher.find()) {
            int position = Integer.parseInt(matcher.group("position"));
            cp = getFromPosition(clanManager.getAllClanPlayers(), position, clanManager::sortClanPlayersByKDR);
            params = params.replace(matcher.group("strip"), "");
        }
        return getValue(player, cp, clan, params);
    }

    /**
     * Resolves all {@code %simpleclans_alliance_*%} placeholders.
     *
     * <p>Server-wide placeholders (nato_symbol, sco_members, etc.) work for any player.
     * Per-clan placeholders require the player's clan to be in an alliance.</p>
     */
    @NotNull
    private String resolveAlliancePlaceholder(@Nullable Clan clan, @NotNull String key) {
        AllianceManager am = plugin.getAllianceManager();
        AllianceMeetingManager mm = plugin.getAllianceMeetingManager();

        // ── Server-wide (no clan context needed) ───────────────────────────────────
        switch (key) {
            case "nato_symbol":    return AllianceType.NATO.getColoredSymbol();
            case "sco_symbol":     return AllianceType.SCO.getColoredSymbol();
            case "nato_members":   return String.valueOf(am.getAlliance(AllianceType.NATO).getSize());
            case "sco_members":    return String.valueOf(am.getAlliance(AllianceType.SCO).getSize());
            case "nato_next_host": return resolveHostName(am.getAlliance(AllianceType.NATO));
            case "sco_next_host":  return resolveHostName(am.getAlliance(AllianceType.SCO));
        }

        // ── Per-clan ────────────────────────────────────────────────────────────────
        AllianceType type = clan != null ? am.getAllianceType(clan) : null;

        switch (key) {
            case "name":         return type != null ? type.getDisplayName() : "";
            case "symbol":       return type != null ? type.getColoredSymbol() : "";
            case "symbol_plain": return type != null ? type.getPlainSymbol() : "";
            case "member_count": return type != null ? String.valueOf(am.getAlliance(type).getSize()) : "0";
            case "meeting_active": return String.valueOf(type != null && mm.isMeetingActive(type));
            case "next_meeting_time":
                return type != null ? nullToEmpty(mm.getNextMeetingFormatted(type)) : "";
            case "next_meeting_host":
                return type != null ? resolveHostName(am.getAlliance(type)) : "";
            case "is_host_this_week": {
                if (clan == null || type == null) return "false";
                return String.valueOf(clan.getTag().equals(am.getAlliance(type).getCurrentHostTag()));
            }
            case "open_proposals":
                return type != null ? String.valueOf(mm.getPendingProposals(type).size()) : "0";
            case "my_clan_voted": {
                if (clan == null || type == null) return "false";
                List<Proposal> active = mm.getMeetingProposals(type);
                boolean voted = active.stream().anyMatch(p -> p.getVote(clan.getTag()) != null);
                return String.valueOf(voted);
            }
            case "joined_date": {
                if (clan == null || type == null) return "";
                Long joinDate = am.getAlliance(type).getJoinDate(clan.getTag());
                if (joinDate == null || joinDate == 0) return "";
                return new SimpleDateFormat("yyyy-MM-dd").format(new Date(joinDate));
            }
            case "hq_set":
                return String.valueOf(clan != null && type != null && am.getHq(clan, type) != null);
        }

        // HQ coordinate sub-keys
        if (key.startsWith("hq_") && clan != null && type != null) {
            Location hq = am.getHq(clan, type);
            if (hq == null) return "";
            switch (key) {
                case "hq_world": return hq.getWorld() != null ? hq.getWorld().getName() : "";
                case "hq_x": return String.valueOf((int) hq.getX());
                case "hq_y": return String.valueOf((int) hq.getY());
                case "hq_z": return String.valueOf((int) hq.getZ());
            }
        }

        return "";
    }

    @NotNull
    private String resolveHostName(@NotNull Alliance alliance) {
        String tag = alliance.getCurrentHostTag();
        if (tag == null) return "";
        Clan c = clanManager.getClan(tag);
        return c != null ? c.getName() : tag;
    }

    @NotNull
    private static String nullToEmpty(@Nullable String s) {
        return s != null ? s : "";
    }

    @Nullable
    private <T> T getFromPosition(List<T> list, int position, Consumer<List<T>> sort) {
        if (isPositionValid(list, position)) {
            sort.accept(list);
            return list.get(position - 1);
        }
        return null;
    }

    private boolean isPositionValid(@NotNull Collection<?> collection, int position) {
        return position >= 1 && position <= collection.size();
    }

    @NotNull
    private String getValue(@Nullable OfflinePlayer player, @Nullable ClanPlayer cp, @Nullable Clan clan,
                            @NotNull String placeholder) {
        if (placeholder.startsWith("clan_")) {
            placeholder = placeholder.replace("clan_", "");
            return getValue(player, clan, placeholder);
        }
        return getValue(player, cp, placeholder);
    }

    @NotNull
    private String getValue(@Nullable OfflinePlayer player, @Nullable Object object, @NotNull String placeholder) {
        if (object != null) {
            for (Method declaredMethod : object.getClass().getDeclaredMethods()) {
                Placeholder[] annotations = declaredMethod.getAnnotationsByType(Placeholder.class);
                for (Placeholder p : annotations) {
                    if (p.value().equals(placeholder)) {
                        return resolve(player, object, declaredMethod, p.resolver(), placeholder, p.config());
                    }
                }
            }
            plugin.getLogger().warning(String.format("Placeholder %s not found", placeholder));
        }
        return "";
    }

    private String resolve(@Nullable OfflinePlayer player, @NotNull Object object, @NotNull Method method,
                           @NotNull String resolverId, @NotNull String placeholder, @NotNull String config) {
        PlaceholderResolver resolver = RESOLVERS.get(resolverId);
        if (resolver != null) {
            return resolver.resolve(player, object, method, placeholder, getConfigMap(config));
        }
        plugin.getLogger().warning(String.format("Resolver %s for %s not found", resolverId, placeholder));
        return "";
    }

    @NotNull
    private Map<String, String> getConfigMap(@NotNull String config) {
        HashMap<String, String> map = new HashMap<>();
        String[] elements = config.split(",");
        for (String element : elements) {
            String[] keyAndValue = element.split(":");
            map.put(keyAndValue[0], keyAndValue.length > 1 ? keyAndValue[1] : null);
        }
        return map;
    }

    private void registerResolvers() {
        Set<Class<? extends PlaceholderResolver>> resolvers =
                Helper.getSubTypesOf("net.sacredlabyrinth.phaed.simpleclans.hooks.papi.resolvers",
                        PlaceholderResolver.class);
        plugin.getLogger().info(String.format("Registering %d placeholder resolvers...", resolvers.size()));
        for (Class<? extends PlaceholderResolver> r : resolvers) {
            try {
                PlaceholderResolver resolver = r.getConstructor(SimpleClans.class).newInstance(plugin);
                RESOLVERS.put(resolver.getId(), resolver);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                    NoSuchMethodException e) {
                plugin.getLogger().log(Level.SEVERE, "Error registering placeholder resolver", e);
            }
        }
    }

    private void addPlaceholders(String prefix, Class<?> clazz, List<String> placeholders) {
        for (Method method : clazz.getDeclaredMethods()) {
            Placeholder[] annotations = method.getAnnotationsByType(Placeholder.class);
            for (Placeholder annotation : annotations) {
                placeholders.add("%" + prefix + annotation.value() + "%");
                //Commented because the list would be very long
                //placeholders.add("%simpleclans_topplayers_<number>_" + annotation.value() + "%");
            }
        }
    }

}
