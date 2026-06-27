package me.luisgamedev.betterhorses.summon;

import java.util.Locale;

/**
 * Defines where summon-horn feedback should be shown to players.
 */
public enum HorseSummonNotificationMode {
    CHAT,
    ACTION_BAR,
    BOTH,
    NONE;

    public static HorseSummonNotificationMode fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return ACTION_BAR;
        }
        try {
            return HorseSummonNotificationMode.valueOf(raw.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ACTION_BAR;
        }
    }

    public boolean sendsChat() {
        return this == CHAT || this == BOTH;
    }

    public boolean sendsActionBar() {
        return this == ACTION_BAR || this == BOTH;
    }
}
