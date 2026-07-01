package de.omegazirkel.risingworld.marketplace.exports;

import de.omegazirkel.risingworld.marketplace.PluginSettings;

public record MarketplaceRouteExposure(boolean zones, boolean offers) {

    public static MarketplaceRouteExposure from(PluginSettings settings) {
        return new MarketplaceRouteExposure(settings.exposeMarketplaceZones, settings.exposeMarketplaceOffers);
    }
}
