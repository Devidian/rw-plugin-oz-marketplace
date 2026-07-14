package de.omegazirkel.risingworld.marketplace.ui;

import java.util.Optional;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.marketplace.MarketZone;
import de.omegazirkel.risingworld.tools.ui.SharedIndicatorProvider;
import net.risingworld.api.objects.Player;

public class MarketplaceZoneIndicatorProvider implements SharedIndicatorProvider {
    private final Marketplace plugin;

    public MarketplaceZoneIndicatorProvider(Marketplace plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean showIndicator(Player player) {
        Optional<MarketZone> zone = plugin.safeCurrentMarketZone(player);
        return zone.isPresent() && plugin.marketplaceZoneIndicatorVisible(player, zone.get());
    }

    @Override
    public String getIcon(Player player) {
        return "zone-marketplace-indicator";
    }
}
