package de.omegazirkel.risingworld;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import de.omegazirkel.risingworld.marketplace.MarketZone;
import de.omegazirkel.risingworld.marketplace.MarketplaceDatabase;
import de.omegazirkel.risingworld.marketplace.MarketplaceListing;
import de.omegazirkel.risingworld.marketplace.MarketplaceResult;
import de.omegazirkel.risingworld.marketplace.MarketplaceSale;
import de.omegazirkel.risingworld.marketplace.MarketplaceService;
import de.omegazirkel.risingworld.marketplace.PluginSettings;
import de.omegazirkel.risingworld.marketplace.PluginGUI;
import de.omegazirkel.risingworld.marketplace.WalletBridge;
import de.omegazirkel.risingworld.marketplace.ui.MarketplacePlayerPluginData;
import de.omegazirkel.risingworld.marketplace.ui.MarketplaceZoneIndicatorManager;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.FileChangeListener;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.tools.db.SQLiteConnectionFactory;
import de.omegazirkel.risingworld.tools.settings.PlayerPluginAdminSettings;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettingsOverlay;
import net.risingworld.api.Plugin;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.objects.Area;
import net.risingworld.api.objects.Player;
import net.risingworld.api.utils.Vector3i;

public class Marketplace extends Plugin implements Listener, FileChangeListener {
    static final Colors c = Colors.getInstance();
    private static PluginSettings s;
    private static MarketplaceService service;
    private static Connection sqliteCon;
    private static MarketplaceZoneIndicatorManager zoneIndicatorManager;
    public static String name;

    public static OZLogger logger() {
        return OZLogger.getInstance("OZ.Marketplace");
    }

    @Override
    public void onEnable() {
        name = getDescription("name");
        s = PluginSettings.getInstance(this);
        s.initSettings();

        try {
            sqliteCon = SQLiteConnectionFactory.open(this);
            MarketplaceDatabase database = new MarketplaceDatabase(sqliteCon);
            service = new MarketplaceService(database, new WalletBridge(this), s);
        } catch (SQLException ex) {
            logger().error("Failed to initialize marketplace database: " + ex.getMessage());
            ex.printStackTrace();
        }

        registerEventListener(this);
        PluginGUI.getInstance(this);
        zoneIndicatorManager = new MarketplaceZoneIndicatorManager(this);
        zoneIndicatorManager.start();
        PlayerPluginSettingsOverlay.registerPlayerPluginData(new MarketplacePlayerPluginData(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginAdminSettings(
                new PlayerPluginAdminSettings(name, getDescription("version"), () -> s.adminSettingsEntries(),
                        s::initSettings));
        logger().info(getName() + " Plugin is enabled version:" + getDescription("version"));
    }

    @Override
    public void onDisable() {
        if (zoneIndicatorManager != null) {
            zoneIndicatorManager.stop();
            zoneIndicatorManager = null;
        }
        if (sqliteCon != null) {
            try {
                sqliteCon.close();
            } catch (SQLException ex) {
                logger().error("Failed to close marketplace database connection: " + ex.getMessage());
            }
        }
    }

    @Override
    public void onSettingsChanged(Path settingsPath) {
        s.initSettings(settingsPath.toString());
        logger().setLevel(s.logLevel);
        if (service != null) {
            service.updateSettings(s);
        }
        if (zoneIndicatorManager != null) {
            zoneIndicatorManager.refresh();
        }
    }

    @EventMethod
    public void onPlayerSpawnEvent(PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        if (s.enableWelcomeMessage) {
            player.sendTextMessage(c.okay + getDescription("name") + " " + getDescription("version")
                    + " enabled. Use /" + s.marketCommand + ".");
        }
        if (player.isAdmin() && service != null && !service.walletAvailable()) {
            player.sendTextMessage(c.warning + "OZ - Marketplace requires OZ - Wallet for listings and purchases.");
        }
    }

    @EventMethod
    public void onPlayerCommand(PlayerCommandEvent event) {
        Player player = event.getPlayer();
        String[] parts = event.getCommand().split(" ");
        if (!parts[0].equals("/" + s.marketCommand)) {
            return;
        }
        if (service == null) {
            player.sendTextMessage(c.error + "Marketplace database is not available.");
            return;
        }
        if (parts.length == 1 || parts[1].equalsIgnoreCase("list")) {
            sendListings(player);
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "sell" -> sell(player, parts);
            case "buy" -> buy(player, parts);
            case "cancel" -> cancel(player, parts);
            case "sales" -> sales(player);
            case "zone" -> zone(player, parts);
            case "help" -> usage(player);
            default -> usage(player);
        }
    }

    private void sell(Player player, String[] parts) {
        if (parts.length < 6) {
            usage(player);
            return;
        }
        String itemName = parts[2];
        int variant = parseInt(parts[3], 0);
        int amount = parseInt(parts[4], 0);
        long price = parseLong(parts[5], 0L);
        String currency = parts.length >= 7 ? parts[6] : "";
        boolean global = parts.length >= 8 && parts[7].equalsIgnoreCase("global");
        MarketplaceResult result = service.createListing(player, itemName, variant, amount, price, currency, global);
        player.sendTextMessage((result.success() ? c.okay : c.error) + result.message());
    }

    private void buy(Player player, String[] parts) {
        if (parts.length != 3) {
            usage(player);
            return;
        }
        MarketplaceResult result = service.buy(player, parseLong(parts[2], 0L));
        player.sendTextMessage((result.success() ? c.okay : c.error) + result.message());
    }

    private void cancel(Player player, String[] parts) {
        if (parts.length != 3) {
            usage(player);
            return;
        }
        MarketplaceResult result = service.cancel(player, parseLong(parts[2], 0L));
        player.sendTextMessage((result.success() ? c.okay : c.error) + result.message());
    }

    private void sales(Player player) {
        try {
            List<MarketplaceSale> sales = service.listSales(player, 10);
            if (sales.isEmpty()) {
                player.sendTextMessage(c.info + "No sales yet.");
                return;
            }
            player.sendTextMessage(c.okay + "Latest sales:");
            for (MarketplaceSale sale : sales) {
                player.sendTextMessage(c.info + "#" + sale.id() + c.text + " listing " + sale.listingId() + ": "
                        + sale.amount() + "x " + sale.itemName() + ":" + sale.itemVariant()
                        + " payout " + sale.sellerPayout() + currencyLabel(sale.currencyIdentifier())
                        + " fee " + sale.fee());
            }
        } catch (SQLException ex) {
            logger().error("Failed to list sales: " + ex.getMessage());
            player.sendTextMessage(c.error + "Could not list sales.");
        }
    }

    private void zone(Player player, String[] parts) {
        if (!player.isAdmin()) {
            player.sendTextMessage(c.error + "Only admins can manage market zones.");
            return;
        }
        if (parts.length >= 3 && parts[2].equalsIgnoreCase("list")) {
            sendZones(player);
            return;
        }
        if (parts.length >= 4 && parts[2].equalsIgnoreCase("delete")) {
            MarketplaceResult result = service.deleteZone(parts[3]);
            player.sendTextMessage((result.success() ? c.okay : c.error) + result.message());
            return;
        }
        if (parts.length < 7 || !parts[2].equalsIgnoreCase("set")) {
            usage(player);
            return;
        }
        String id = parts[3];
        int radius = parseInt(parts[4], 0);
        int fee = parseInt(parts[5], s.defaultLocalFeePercent);
        boolean allowGlobal = Boolean.parseBoolean(parts[6]);
        String label = parts.length >= 8 ? parts[7] : id;
        MarketplaceResult result = service.createZone(id, label, player.getChunkPosition(), radius, fee, allowGlobal);
        player.sendTextMessage((result.success() ? c.okay : c.error) + result.message());
    }

    public MarketplaceResult createOrUpdateCurrentMarketZone(Player player) {
        if (!player.isAdmin()) {
            return MarketplaceResult.fail("Only admins can manage market zones.");
        }
        Vector3i chunk = player.getChunkPosition();
        String id = currentZoneId(player);
        String label = currentZoneName(player);
        return service.createZone(id, label, chunk, 0, s.defaultLocalFeePercent, s.globalMarketplaceEnabled);
    }

    public MarketplaceResult syncCurrentMarketZoneName(Player player) {
        if (!player.isAdmin()) {
            return MarketplaceResult.fail("Only admins can manage market zones.");
        }
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return MarketplaceResult.fail("You are not standing in a market zone.");
            }
            MarketZone current = zone.get();
            return service.updateZone(new MarketZone(current.id(), currentZoneName(player),
                    current.minChunkX(), current.maxChunkX(), current.minChunkY(), current.maxChunkY(),
                    current.minChunkZ(), current.maxChunkZ(), current.feePercent(), current.allowGlobalTrade(),
                    current.createdAt()));
        } catch (SQLException ex) {
            logger().error("Failed to sync market zone name: " + ex.getMessage());
            return MarketplaceResult.fail("Could not sync market zone name.");
        }
    }

    public MarketplaceResult toggleCurrentMarketZoneGlobal(Player player) {
        if (!player.isAdmin()) {
            return MarketplaceResult.fail("Only admins can manage market zones.");
        }
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return MarketplaceResult.fail("You are not standing in a market zone.");
            }
            MarketZone current = zone.get();
            return service.updateZone(new MarketZone(current.id(), current.name(),
                    current.minChunkX(), current.maxChunkX(), current.minChunkY(), current.maxChunkY(),
                    current.minChunkZ(), current.maxChunkZ(), current.feePercent(), !current.allowGlobalTrade(),
                    current.createdAt()));
        } catch (SQLException ex) {
            logger().error("Failed to update market zone global override: " + ex.getMessage());
            return MarketplaceResult.fail("Could not update market zone global override.");
        }
    }

    public MarketplaceResult setCurrentMarketZoneFee(Player player, int feePercent) {
        if (!player.isAdmin()) {
            return MarketplaceResult.fail("Only admins can manage market zones.");
        }
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return MarketplaceResult.fail("You are not standing in a market zone.");
            }
            MarketZone current = zone.get();
            return service.updateZone(new MarketZone(current.id(), current.name(),
                    current.minChunkX(), current.maxChunkX(), current.minChunkY(), current.maxChunkY(),
                    current.minChunkZ(), current.maxChunkZ(), feePercent, current.allowGlobalTrade(),
                    current.createdAt()));
        } catch (SQLException ex) {
            logger().error("Failed to update market zone fee: " + ex.getMessage());
            return MarketplaceResult.fail("Could not update market zone fee.");
        }
    }

    public MarketplaceResult deleteCurrentMarketZone(Player player) {
        if (!player.isAdmin()) {
            return MarketplaceResult.fail("Only admins can manage market zones.");
        }
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return MarketplaceResult.fail("You are not standing in a market zone.");
            }
            return service.deleteZone(zone.get().id());
        } catch (SQLException ex) {
            logger().error("Failed to delete current market zone: " + ex.getMessage());
            return MarketplaceResult.fail("Could not delete market zone.");
        }
    }

    public Optional<MarketZone> currentMarketZone(Player player) throws SQLException {
        if (service == null) {
            return Optional.empty();
        }
        return service.currentZone(player);
    }

    public Optional<MarketZone> safeCurrentMarketZone(Player player) {
        try {
            return currentMarketZone(player);
        } catch (SQLException ex) {
            logger().error("Failed to read current marketplace zone: " + ex.getMessage());
            return Optional.empty();
        }
    }

    public PluginSettings marketplaceSettings() {
        return s;
    }

    public boolean showMarketplaceZoneIndicator() {
        return s != null && s.showMarketplaceZoneIndicator;
    }

    public String marketplaceZoneIndicatorText(Player player, MarketZone zone) {
        String global = zone.allowGlobalTrade()
                ? t("TC_MARKET_INDICATOR_GLOBAL_ON", player)
                : t("TC_MARKET_INDICATOR_GLOBAL_OFF", player);
        return t("TC_MARKET_INDICATOR", player)
                .replace("PH_ZONE", zone.name())
                .replace("PH_FEE", String.valueOf(zone.feePercent()))
                .replace("PH_GLOBAL", global);
    }

    public List<MarketplaceListing> visibleMarketplaceListings(Player player) throws SQLException {
        if (service == null) {
            return List.of();
        }
        return service.listVisibleListings(player);
    }

    public List<MarketplaceSale> marketplaceSales(Player player, int limit) throws SQLException {
        if (service == null) {
            return List.of();
        }
        return service.listSales(player, limit);
    }

    public MarketplaceResult createMarketplaceListing(Player player, String itemName, int variant, int amount,
            long price, String currency, boolean global) {
        if (service == null) {
            return MarketplaceResult.fail("Marketplace database is not available.");
        }
        return service.createListing(player, itemName, variant, amount, price, currency, global);
    }

    public MarketplaceResult buyMarketplaceListing(Player player, long listingId) {
        if (service == null) {
            return MarketplaceResult.fail("Marketplace database is not available.");
        }
        return service.buy(player, listingId);
    }

    public MarketplaceResult hideMarketplaceSale(Player player, long saleId) {
        if (service == null) {
            return MarketplaceResult.fail("Marketplace database is not available.");
        }
        return service.hideSale(player, saleId);
    }

    public String currentMarketZoneStatus(Player player) {
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return "No market zone at this chunk.";
            }
            MarketZone current = zone.get();
            return current.name() + " (" + current.id() + ") fee " + current.feePercent()
                    + "% global=" + current.allowGlobalTrade();
        } catch (SQLException ex) {
            logger().error("Failed to read current market zone: " + ex.getMessage());
            return "Could not read current market zone.";
        }
    }

    public void sendListings(Player player) {
        try {
            List<MarketplaceListing> listings = service.listVisibleListings(player);
            if (listings.isEmpty()) {
                player.sendTextMessage(c.info + "No marketplace listings visible here.");
                player.sendTextMessage(c.text + "Use /" + s.marketCommand + " sell <item> <variant> <amount> <price> [currency] [global].");
                return;
            }
            player.sendTextMessage(c.okay + "Marketplace listings:");
            for (MarketplaceListing listing : listings) {
                player.sendTextMessage(c.info + "#" + listing.id() + c.text + " "
                        + listing.amount() + "x " + listing.itemName() + ":" + listing.itemVariant()
                        + " by " + listing.sellerName() + " for " + listing.price()
                        + currencyLabel(listing.currencyIdentifier())
                        + (listing.globalListing() ? " global" : " local:" + listing.marketZoneId()));
            }
            player.sendTextMessage(c.text + "Use /" + s.marketCommand + " buy <listing-id>.");
        } catch (SQLException ex) {
            logger().error("Failed to list marketplace listings: " + ex.getMessage());
            player.sendTextMessage(c.error + "Could not list marketplace listings.");
        }
    }

    private void sendZones(Player player) {
        try {
            List<MarketZone> zones = service.listZones();
            if (zones.isEmpty()) {
                player.sendTextMessage(c.info + "No market zones defined.");
                return;
            }
            player.sendTextMessage(c.okay + "Market zones:");
            for (MarketZone zone : zones) {
                player.sendTextMessage(c.info + zone.id() + c.text + " " + zone.name()
                        + " fee " + zone.feePercent() + "% global=" + zone.allowGlobalTrade());
            }
        } catch (SQLException ex) {
            logger().error("Failed to list market zones: " + ex.getMessage());
            player.sendTextMessage(c.error + "Could not list market zones.");
        }
    }

    private void usage(Player player) {
        player.sendTextMessage(c.warning + "Usage: /" + s.marketCommand + " list | sell <item> <variant> <amount> <price> [currency] [global] | buy <id> | cancel <id> | sales");
        if (player.isAdmin()) {
            player.sendTextMessage(c.warning + "Admin: /" + s.marketCommand + " zone set <id> <radiusChunks> <feePercent> <allowGlobal> [label] | zone list | zone delete <id>");
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String currencyLabel(String currency) {
        return currency == null || currency.isBlank() ? " default" : " " + currency;
    }

    private String t(String key, Player player) {
        return I18n.getInstance(this).get(key, player);
    }

    private String currentZoneId(Player player) {
        Area area = player.getCurrentArea();
        if (area != null && area.getID() > 0L) {
            return "area-" + area.getID();
        }
        Vector3i chunk = player.getChunkPosition();
        return "chunk-" + chunk.x + "-" + chunk.y + "-" + chunk.z;
    }

    private String currentZoneName(Player player) {
        Area area = player.getCurrentArea();
        if (area != null && area.getName() != null && !area.getName().isBlank()) {
            return area.getName();
        }
        Vector3i chunk = player.getChunkPosition();
        return "Market " + chunk.x + "/" + chunk.y + "/" + chunk.z;
    }
}
