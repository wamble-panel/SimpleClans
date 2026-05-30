package net.sacredlabyrinth.phaed.simpleclans.alliance;

import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager;
import net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField;
import net.sacredlabyrinth.phaed.simpleclans.utils.ChatUtils;
import org.jetbrains.annotations.Nullable;

/**
 * The two rival military alliances. NATO and SCO are functionally identical but
 * fully isolated: the enum constant itself is the isolation key — every method
 * that touches alliance state is scoped by an {@link AllianceType}, so NATO state
 * can never leak into SCO and vice-versa.
 *
 * <p>Each constant resolves its symbol and color from config at call time, so
 * live config edits (after a reload) are honoured without restart.</p>
 */
public enum AllianceType {

    NATO(ConfigField.ALLIANCE_SYMBOL_NATO, ConfigField.ALLIANCE_COLOR_NATO),
    SCO(ConfigField.ALLIANCE_SYMBOL_SCO, ConfigField.ALLIANCE_COLOR_SCO);

    private final ConfigField symbolField;
    private final ConfigField colorField;

    AllianceType(ConfigField symbolField, ConfigField colorField) {
        this.symbolField = symbolField;
        this.colorField = colorField;
    }

    /**
     * @return the stable id used for persistence and flag keys (e.g. "NATO")
     */
    public String getId() {
        return name();
    }

    /**
     * @return the human-readable display name (e.g. "NATO")
     */
    public String getDisplayName() {
        return name();
    }

    private SettingsManager settings() {
        return SimpleClans.getInstance().getSettingsManager();
    }

    /**
     * @return the configured symbol without color codes (e.g. "★")
     */
    public String getPlainSymbol() {
        return settings().getString(symbolField);
    }

    /**
     * @return the configured color code string for this alliance (e.g. "&9")
     */
    public String getColorCode() {
        return settings().getString(colorField);
    }

    /**
     * @return the symbol prefixed with its color, color-translated (e.g. §9★)
     */
    public String getColoredSymbol() {
        return ChatUtils.parseColors(getColorCode() + getPlainSymbol());
    }

    /**
     * Resolves an alliance type from user input, case-insensitively.
     *
     * @param input the input string (e.g. "nato", "NATO", "Sco")
     * @return the matching type, or null if none matches
     */
    @Nullable
    public static AllianceType fromString(@Nullable String input) {
        if (input == null) {
            return null;
        }
        for (AllianceType type : values()) {
            if (type.name().equalsIgnoreCase(input.trim())) {
                return type;
            }
        }
        return null;
    }
}
