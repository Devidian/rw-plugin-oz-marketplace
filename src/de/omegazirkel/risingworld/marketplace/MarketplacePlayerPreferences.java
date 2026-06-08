package de.omegazirkel.risingworld.marketplace;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.tools.ui.PluginShortcutVisibility;
import net.risingworld.api.objects.Player;

public final class MarketplacePlayerPreferences {
    public static final String LISTING_LAYOUT_KEY = "oz.marketplace.listingLayout";
    public static final String LAYOUT_CARD = "CARD";
    public static final String LAYOUT_TABLE = "TABLE";

    private MarketplacePlayerPreferences() {
    }

    public static void load(Player player) {
        if (player == null || Marketplace.playerSettings() == null) {
            return;
        }
        if (!player.hasAttribute(LISTING_LAYOUT_KEY)) {
            player.setAttribute(LISTING_LAYOUT_KEY,
                    Marketplace.playerSettings().getString(player.getDbID(), LISTING_LAYOUT_KEY).orElse(LAYOUT_CARD));
        }
        String shortcutKey = shortcutKey();
        if (!player.hasAttribute(shortcutKey)) {
            player.setAttribute(shortcutKey,
                    Marketplace.playerSettings().getBoolean(player.getDbID(), shortcutKey).orElse(true));
        }
    }

    public static String listingLayout(Player player) {
        if (player == null) {
            return LAYOUT_CARD;
        }
        if (!player.hasAttribute(LISTING_LAYOUT_KEY)) {
            load(player);
        }
        Object value = player.getAttribute(LISTING_LAYOUT_KEY);
        return LAYOUT_TABLE.equals(value) ? LAYOUT_TABLE : LAYOUT_CARD;
    }

    public static void setListingLayout(Player player, String value) {
        if (player == null) {
            return;
        }
        String normalizedValue = LAYOUT_TABLE.equals(value) ? LAYOUT_TABLE : LAYOUT_CARD;
        player.setAttribute(LISTING_LAYOUT_KEY, normalizedValue);
        if (Marketplace.playerSettings() != null) {
            Marketplace.playerSettings().setString(player.getDbID(), LISTING_LAYOUT_KEY, normalizedValue);
        }
    }

    public static boolean shortcutVisible(Player player) {
        if (player == null) {
            return true;
        }
        if (!player.hasAttribute(shortcutKey())) {
            load(player);
        }
        Object value = player.getAttribute(shortcutKey());
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public static void setShortcutVisible(Player player, boolean value) {
        if (player == null) {
            return;
        }
        String key = shortcutKey();
        player.setAttribute(key, value);
        if (Marketplace.playerSettings() != null) {
            Marketplace.playerSettings().setBoolean(player.getDbID(), key, value);
        }
    }

    private static String shortcutKey() {
        return PluginShortcutVisibility.playerSettingKey(Marketplace.name);
    }
}
