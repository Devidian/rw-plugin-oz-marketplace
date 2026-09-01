package de.omegazirkel.risingworld.marketplace;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProvider;
import net.risingworld.api.objects.Player;

public class MarketplacePluginInfoStatusProvider implements PluginInfoStatusProvider {
    private final Marketplace plugin;
    private final String pluginName;
    private final String version;

    public MarketplacePluginInfoStatusProvider(Marketplace plugin, String version) {
        this.plugin = plugin;
        this.pluginName = Marketplace.name == null || Marketplace.name.isBlank() ? "OZ - Marketplace" : Marketplace.name;
        this.version = version == null ? "" : version;
    }

    @Override
    public String getPluginName() {
        return pluginName;
    }

    @Override
    public String getInfo(Player player) {
        PluginSettings settings = PluginSettings.getInstance();
        return t().get("tc.market.info.panel.info", player)
                .replace("PH_PLUGIN_NAME", pluginName)
                .replace("PH_VERSION", version)
                .replace("PH_PLUGIN_CMD", settings.marketCommand);
    }

    @Override
    public String getStatus(Player player) {
        PluginSettings settings = PluginSettings.getInstance();
        MarketZone zone = plugin.safeCurrentMarketZone(player).orElse(null);
        return t().get("tc.market.info.panel.status", player)
                .replace("PH_WALLET_STATUS", available(plugin.walletAvailable()))
                .replace("PH_LOCAL_ENABLED", String.valueOf(settings.localMarketplaceEnabled))
                .replace("PH_GLOBAL_ENABLED", String.valueOf(settings.globalMarketplaceEnabled))
                .replace("PH_ZONE_ONLY", String.valueOf(settings.marketZoneOnlyMode))
                .replace("PH_CURRENT_ZONE", zone == null ? "-" : zone.name())
                .replace("PH_ZONE_GLOBAL_MODE", zone == null ? "-" : globalMode(zone.globalTradeMode()))
                .replace("PH_LOCAL_FEE", String.valueOf(settings.defaultLocalFeePercent))
                .replace("PH_GLOBAL_FEE", String.valueOf(settings.defaultGlobalFeePercent))
                .replace("PH_MAX_LISTINGS", String.valueOf(settings.maxListingsPerPlayer))
                .replace("PH_MAX_PLAYER_MARKETS", String.valueOf(settings.maxPlayerMarketplaces));
    }

    private I18n t() {
        return I18n.getInstance(plugin);
    }

    private static String available(boolean value) {
        return value ? "available" : "missing";
    }

    private static String globalMode(int mode) {
        return switch (MarketZone.normalizeGlobalTradeMode(mode)) {
            case MarketZone.GLOBAL_DENY -> "deny";
            case MarketZone.GLOBAL_ALLOW -> "allow";
            default -> "default";
        };
    }
}
