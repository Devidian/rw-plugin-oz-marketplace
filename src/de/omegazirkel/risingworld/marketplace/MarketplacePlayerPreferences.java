package de.omegazirkel.risingworld.marketplace;

import de.omegazirkel.risingworld.Marketplace;
import net.risingworld.api.objects.Player;

public final class MarketplacePlayerPreferences {
    public static final String LISTING_LAYOUT_KEY = "oz.marketplace.listingLayout";
    public static final String LAYOUT_CARD = "CARD";
    public static final String LAYOUT_TABLE = "TABLE";

    private MarketplacePlayerPreferences() {
    }

    public static void load(Player player) {
        if (player == null || Marketplace.playerSettings() == null || player.hasAttribute(LISTING_LAYOUT_KEY)) {
            return;
        }
        player.setAttribute(LISTING_LAYOUT_KEY,
                Marketplace.playerSettings().getString(player.getDbID(), LISTING_LAYOUT_KEY).orElse(LAYOUT_CARD));
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
}
