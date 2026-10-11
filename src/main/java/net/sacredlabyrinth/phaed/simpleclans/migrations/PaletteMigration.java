package net.sacredlabyrinth.phaed.simpleclans.migrations;

import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Moves the colour settings of an existing config.yml to the pastel palette, once.
 * A value is only replaced while it still equals the old default, so anything a server
 * owner customised is left exactly as it was.
 */
public class PaletteMigration extends ConfigMigration {

    private static final String MARKER = "settings.palette-version";
    private static final int VERSION = 1;

    public PaletteMigration(SettingsManager settingsManager) {
        super(settingsManager);
    }

    @Override
    public void migrate() {
        if (config.getInt(MARKER, 0) >= VERSION) {
            return;
        }
        int changed = 0;
        for (Map.Entry<String, String[]> entry : changes().entrySet()) {
            String path = entry.getKey();
            String oldDefault = entry.getValue()[0];
            Object current = config.get(path);
            if (current == null || oldDefault.equals(String.valueOf(current))) {
                config.set(path, entry.getValue()[1]);
                changed++;
            }
        }
        config.set(MARKER, VERSION);
        if (changed > 0) {
            SimpleClans.getInstance().getLogger().info(String.format(
                    "Updated %d colour setting(s) in config.yml to the new palette (customised values were kept).",
                    changed));
        }
    }

    /**
     * @return path -> {old default, new default}
     */
    private static Map<String, String[]> changes() {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("alliance.symbols.nato-color", new String[]{"&9", "&#A2D2FF"});
        m.put("alliance.symbols.sco-color", new String[]{"&c", "&#FF9AA2"});
        m.put("alliance.chat.format.nato", new String[]{"&9★ [NATO] &7{clan} &f{player}: {message}",
                "&#A2D2FF★ ɴᴀᴛᴏ &#8A8A8A» &#C3B1E1{clan} &#FFC8DD{player}&#8A8A8A: &#FFD8BE{message}"});
        m.put("alliance.chat.format.sco", new String[]{"&c✪ [SCO] &7{clan} &f{player}: {message}",
                "&#FF9AA2✪ ꜱᴄᴏ &#8A8A8A» &#C3B1E1{clan} &#FFC8DD{player}&#8A8A8A: &#FFD8BE{message}"});

        m.put("tag.default-color", new String[]{"8", "&#C3B1E1"});
        m.put("tag.bracket.color", new String[]{"8", "&#8A8A8A"});
        m.put("tag.bracket.leader-color", new String[]{"4", "&#FFD6A5"});
        m.put("tag.separator.color", new String[]{"8", "&#8A8A8A"});
        m.put("tag.separator.leader-color", new String[]{"4", "&#FFD6A5"});

        m.put("page.untrusted-color", new String[]{"8", "&#E2E2E2"});
        m.put("page.clan-name-color", new String[]{"b", "&#C3B1E1"});
        m.put("page.subtitle-color", new String[]{"7", "&#FFD8BE"});
        m.put("page.headings-color", new String[]{"8", "&#F2F2F2"});
        m.put("page.trusted-color", new String[]{"f", "&#A2D2FF"});
        m.put("page.leader-color", new String[]{"4", "&#FFD6A5"});

        m.put("clanchat.announcement-color", new String[]{"e", "&#FFD8BE"});
        m.put("clanchat.format", new String[]{"&b[%clan%&b] &4<%nick-color%%player%&4> %rank%: &b%message%",
                "&#C3B1E1⚑ %clan% &#8A8A8A» %nick-color%%player% %rank%&#8A8A8A: &#FFD8BE%message%"});
        m.put("clanchat.spy-format", new String[]{
                "&8[Spy] [&bC&8] <%clean-tag%&8> <%nick-color%*&8%player%>&8 %rank%&8: %message%",
                "&#8A8A8A[ꜱᴘʏ ⚑] %clean-tag% » %nick-color%%player% &#8A8A8A%rank%: %message%"});
        m.put("clanchat.rank", new String[]{"&f[%rank%&f]", "&#8A8A8A[&#F2F2F2%rank%&#8A8A8A]"});
        m.put("clanchat.leader-color", new String[]{"4", "&#FFD6A5"});
        m.put("clanchat.trusted-color", new String[]{"f", "&#A2D2FF"});
        m.put("clanchat.member-color", new String[]{"7", "&#B5EAD7"});

        m.put("bb.prefix", new String[]{"&8* &e", "&#8A8A8A• &#FFD8BE"});

        m.put("allychat.format", new String[]{
                "&b[Ally Chat] &4<%clan%&4> <%nick-color%%player%&4> %rank%: &b%message%",
                "&#B5EAD7❤ ᴀʟʟʏ &#8A8A8A» &#C3B1E1%clan% %nick-color%%player% %rank%&#8A8A8A: &#FFD8BE%message%"});
        m.put("allychat.spy-format", new String[]{
                "&8[Spy] [&cA&8] <%clean-tag%&8> <%nick-color%*&8%player%>&8 %rank%&8: %message%",
                "&#8A8A8A[ꜱᴘʏ ❤] %clean-tag% » %nick-color%%player% &#8A8A8A%rank%: %message%"});
        m.put("allychat.rank", new String[]{"&f[%rank%&f]", "&#8A8A8A[&#F2F2F2%rank%&#8A8A8A]"});
        m.put("allychat.leader-color", new String[]{"4", "&#FFD6A5"});
        m.put("allychat.trusted-color", new String[]{"f", "&#A2D2FF"});
        m.put("allychat.member-color", new String[]{"7", "&#B5EAD7"});

        m.put("discordchat.format", new String[]{
                "&b[&9D&b] &b[%clan%&b] &4<%nick-color%%player%&4> %rank%: &b%message%",
                "&#A2D2FF[ᴅ] &#C3B1E1⚑ %clan% &#8A8A8A» %nick-color%%player% %rank%&#8A8A8A: &#FFD8BE%message%"});
        m.put("discordchat.spy-format", new String[]{
                "&8[Spy] [&9D&8] <%clean-tag%&8> <%nick-color%*&8%player%>&8 %rank%&8: %message%",
                "&#8A8A8A[ꜱᴘʏ ᴅ] %clean-tag% » %nick-color%%player% &#8A8A8A%rank%: %message%"});
        return m;
    }
}
