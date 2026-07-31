package de.omegazirkel.risingworld;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import de.omegazirkel.risingworld.marketplace.MarketZone;
import de.omegazirkel.risingworld.marketplace.MarketplacePlayerPreferences;
import de.omegazirkel.risingworld.marketplace.MarketplaceDatabase;
import de.omegazirkel.risingworld.marketplace.MarketplaceListing;
import de.omegazirkel.risingworld.marketplace.MarketplaceItemState;
import de.omegazirkel.risingworld.marketplace.MarketplaceCapacityShopIntegration;
import de.omegazirkel.risingworld.marketplace.MarketplaceResult;
import de.omegazirkel.risingworld.marketplace.MarketplaceSale;
import de.omegazirkel.risingworld.marketplace.MarketplaceService;
import de.omegazirkel.risingworld.marketplace.MarketplacePluginInfoStatusProvider;
import de.omegazirkel.risingworld.marketplace.PluginSettings;
import de.omegazirkel.risingworld.marketplace.PluginGUI;
import de.omegazirkel.risingworld.marketplace.WalletBridge;
import de.omegazirkel.risingworld.marketplace.ui.MarketplacePlayerPluginData;
import de.omegazirkel.risingworld.marketplace.ui.MarketplacePlayerPluginSettings;
import de.omegazirkel.risingworld.marketplace.ui.MarketplaceZoneIndicatorProvider;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.tools.PlayerSettings;
import de.omegazirkel.risingworld.tools.db.SQLiteConnectionFactory;
import de.omegazirkel.risingworld.tools.settings.PlayerPluginAdminSettings;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettingsOverlay;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import de.omegazirkel.risingworld.tools.ui.PluginShortcutVisibility;
import de.omegazirkel.risingworld.tools.ui.SharedIndicators;
import de.omegazirkel.risingworld.tools.bridge.MailBridge;
import net.risingworld.api.Plugin;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.events.player.ui.PlayerUITextFieldChangeEvent;
import net.risingworld.api.objects.Area;
import net.risingworld.api.objects.Player;

class MarketplaceRuntime extends Plugin {
    static final Colors c = Colors.getInstance();
    private static PluginSettings s;
    private static MarketplaceService service;
    private static Connection sqliteCon;
    private static PlayerSettings playerSettings;
    private static I18n t;
    private MarketplaceCapacityShopIntegration capacityShop;
    public static String name;

    public static OZLogger logger() {
        return OZLogger.getInstance("OZ.Marketplace");
    }

    @Override
    public void onEnable() {
        name = getDescription("name");
        t = I18n.getInstance(this);
        s = PluginSettings.getInstance((Marketplace) this);
        s.initSettings();

        try {
            sqliteCon = SQLiteConnectionFactory.open(this);
            MarketplaceDatabase database = new MarketplaceDatabase(sqliteCon);
            playerSettings = new PlayerSettings(sqliteCon);
            service = new MarketplaceService(database, new WalletBridge(this), new MailBridge(this), s);
            int repairedMarkets = service.repairLostPlayerMarkets();
            logger().info("Marketplace startup repair completed; repaired player markets: " + repairedMarkets + ".");
        } catch (SQLException ex) {
            logger().error("Failed to initialize marketplace database: " + ex.getMessage());
            ex.printStackTrace();
        }

        PluginGUI.getInstance((Marketplace) this);
        capacityShop = new MarketplaceCapacityShopIntegration(this);
        capacityShop.register(s);
        PluginShortcutVisibility.register(name, MarketplacePlayerPreferences::shortcutVisible);
        SharedIndicators.registerProvider(name, new MarketplaceZoneIndicatorProvider((Marketplace) this));
        PlayerPluginSettingsOverlay
                .registerPlayerPluginSettings(new MarketplacePlayerPluginSettings(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginData(new MarketplacePlayerPluginData(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginAdminSettings(
                new PlayerPluginAdminSettings(name, getDescription("version"), () -> s.adminSettingsEntries(),
                        s::initSettings));
        PluginInfoStatusProviders
                .registerProvider(
                        new MarketplacePluginInfoStatusProvider((Marketplace) this, getDescription("version")));
        logger().info(getName() + " Plugin is enabled version:" + getDescription("version"));
    }

    @Override
    public void onDisable() {
        if (name != null) {
            PluginShortcutVisibility.unregister(name);
            PluginInfoStatusProviders.unregisterProvider(name);
        }
        SharedIndicators.unregisterProvider(name);
        if (sqliteCon != null) {
            try {
                sqliteCon.close();
            } catch (SQLException ex) {
                logger().error("Failed to close marketplace database connection: " + ex.getMessage());
            }
        }
    }

    public void onSettingsChanged(Path settingsPath) {
        s.initSettings(settingsPath.toString());
        logger().setLevel(s.logLevel);
        if (service != null) {
            service.updateSettings(s);
        }
        if (capacityShop != null) capacityShop.register(s);
    }

    public void onPlayerSpawnEvent(PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        MarketplacePlayerPreferences.load(player);
        if (s.enableWelcomeMessage) {
            player.sendTextMessage(c.okay + tr(player, "TC_MARKET_CHAT_WELCOME",
                    "PH_PLUGIN", getDescription("name"),
                    "PH_VERSION", getDescription("version"),
                    "PH_COMMAND", s.marketCommand));
        }
        if (player.isAdmin() && service != null && !service.walletAvailable()) {
            player.sendTextMessage(c.warning + tr(player, "TC_MARKET_RESULT_WALLET_REQUIRED"));
        }
    }

    public void onPlayerUITextFieldChangeEvent(PlayerUITextFieldChangeEvent event) {
        Player player = event.getPlayer();
        Object overlay = player.getAttribute("oz.marketplace.ui.overlay");
        if (overlay instanceof de.omegazirkel.risingworld.marketplace.ui.MarketplaceOverlay marketplaceOverlay) {
            marketplaceOverlay.onTextFieldChanged(event.getUITextField(), event.getNewText());
        }
    }

    public void onPlayerCommand(PlayerCommandEvent event) {
        Player player = event.getPlayer();
        String[] parts = event.getCommand().split(" ");
        if (!parts[0].equals("/" + s.marketCommand)) {
            return;
        }
        if (parts.length > 1 && (parts[1].equalsIgnoreCase("status") || parts[1].equalsIgnoreCase("info"))) {
            PluginInfoStatusProviders.show(player, name);
            return;
        }
        if (service == null) {
            player.sendTextMessage(c.error + tr(player, "TC_MARKET_RESULT_DATABASE_UNAVAILABLE"));
            return;
        }
        if (parts.length == 1) {
            PluginGUI.getInstance().openMainMenu(player);
            return;
        }
        if (parts[1].equalsIgnoreCase("list")) {
            sendListings(player);
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "buy" -> buy(player, parts);
            case "wanted" -> wanted(player, parts);
            case "cancel" -> cancel(player, parts);
            case "sales" -> sales(player);
            case "zone" -> zone(player, parts);
            case "help" -> usage(player);
            default -> usage(player);
        }
    }

    private void buy(Player player, String[] parts) {
        if (parts.length < 3 || parts.length > 4) {
            usage(player);
            return;
        }
        MarketplaceResult result = service.buy(player, parseLong(parts[2], 0L),
                parts.length == 4 ? parseInt(parts[3], 0) : Integer.MAX_VALUE);
        sendResult(player, result);
    }

    private void wanted(Player player, String[] parts) {
        if (parts.length < 7 || parts.length > 8) {
            usage(player);
            return;
        }
        boolean global = parts[6].equalsIgnoreCase("global");
        MarketplaceResult result = service.createWantedListing(player, parts[2], parseInt(parts[3], -1),
                parseInt(parts[4], 0), parseLong(parts[5], 0L), parts.length == 8 ? parts[7] : "", global);
        sendResult(player, result);
    }

    private void cancel(Player player, String[] parts) {
        if (parts.length != 3) {
            usage(player);
            return;
        }
        MarketplaceResult result = service.cancel(player, parseLong(parts[2], 0L));
        sendResult(player, result);
    }

    private void sales(Player player) {
        try {
            List<MarketplaceSale> sales = service.listSales(player, 10);
            if (sales.isEmpty()) {
                player.sendTextMessage(c.info + tr(player, "TC_MARKET_CHAT_NO_SALES"));
                return;
            }
            player.sendTextMessage(c.okay + tr(player, "TC_MARKET_CHAT_LATEST_SALES"));
            for (MarketplaceSale sale : sales) {
                player.sendTextMessage(c.info + tr(player, "TC_MARKET_CHAT_SALE_ROW",
                        "PH_SALE", String.valueOf(sale.id()),
                        "PH_LISTING", String.valueOf(sale.listingId()),
                        "PH_AMOUNT", String.valueOf(sale.amount()),
                        "PH_ITEM", sale.itemName() + ":" + sale.itemVariant(),
                        "PH_PAYOUT", String.valueOf(sale.sellerPayout()),
                        "PH_CURRENCY", currencyLabel(sale.currencyIdentifier()),
                        "PH_FEE", String.valueOf(sale.fee())));
            }
        } catch (SQLException ex) {
            logger().error("Failed to list sales: " + ex.getMessage());
            player.sendTextMessage(c.error + tr(player, "TC_MARKET_CHAT_SALES_FAILED"));
        }
    }

    private void zone(Player player, String[] parts) {
        if (!player.isAdmin()) {
            player.sendTextMessage(c.error + tr(player, "TC_MARKET_CHAT_ADMIN_ONLY"));
            return;
        }
        if (parts.length >= 3 && parts[2].equalsIgnoreCase("list")) {
            sendZones(player);
            return;
        }
        if (parts.length >= 3 && parts[2].equalsIgnoreCase("delete")) {
            MarketplaceResult result = deleteCurrentMarketZone(player, parts.length >= 4 ? parts[3] : null);
            sendResult(player, result);
            return;
        }
        if (parts.length < 7 || !parts[2].equalsIgnoreCase("set")) {
            usage(player);
            return;
        }
        String id = parts[3];
        int radius = parseInt(parts[4], 0);
        int fee = parseInt(parts[5], s.defaultLocalFeePercent);
        int globalMode = parseGlobalMode(parts[6]);
        String label = parts.length >= 8 ? parts[7] : id;
        MarketplaceResult result = service.createZone(id, label, 0L, player.getChunkPosition(), radius, fee, globalMode);
        sendResult(player, result);
    }

    public MarketplaceResult createOrUpdateCurrentMarketZone(Player player) {
        return createOrUpdateCurrentMarketZone(player, false);
    }

    /** Administrators explicitly choose whether an area market is system-owned or player-owned. */
    public MarketplaceResult createOrUpdateCurrentMarketZone(Player player, boolean privateMarket) {
        Area area = player.getCurrentArea();
        if (area == null || area.getID() <= 0L) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_AREA_REQUIRED",
                    "Stand inside an existing area to create a market.");
        }
        Optional<MarketZone> existing = safeCurrentMarketZone(player);
        if (existing.isPresent() && !service.canManageZone(player, existing.get())) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_MANAGE_OWN_ONLY",
                    "You can only manage your own market.");
        }
        if (existing.isEmpty() && (!player.isAdmin() || privateMarket)) {
            return service.createPlayerMarket(player);
        }
        int globalMode = existing.map(MarketZone::globalTradeMode).orElse(MarketZone.GLOBAL_DEFAULT);
        return service.createAreaZone(currentZoneId(player), currentZoneName(player), area.getID(),
                existing.map(MarketZone::feePercent).orElse(s.defaultLocalFeePercent), globalMode);
    }

    public MarketplaceResult syncCurrentMarketZoneName(Player player) {
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_NOT_IN_MARKET",
                        "You are not standing in a market zone.");
            }
            MarketZone current = zone.get();
            if (!service.canManageZone(player, current)) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_MANAGE_OWN_ONLY",
                        "You can only manage your own market.");
            }
            return service.updateZone(new MarketZone(current.id(), currentZoneName(player),
                    current.areaId(),
                    current.minChunkX(), current.maxChunkX(), current.minChunkY(), current.maxChunkY(),
                    current.minChunkZ(), current.maxChunkZ(), current.feePercent(), current.globalTradeMode(),
                    current.createdAt(), current.ownerDbId(), current.ownerName(), current.ownerAreaPermission()));
        } catch (SQLException ex) {
            logger().error("Failed to sync market zone name: " + ex.getMessage());
            return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_SYNC_FAILED",
                    "Could not synchronize the market zone name.");
        }
    }

    public MarketplaceResult toggleCurrentMarketZoneGlobal(Player player) {
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_NOT_IN_MARKET",
                        "You are not standing in a market zone.");
            }
            MarketZone current = zone.get();
            if (!service.canManageZone(player, current)) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_MANAGE_OWN_ONLY",
                        "You can only manage your own market.");
            }
            if (current.playerOwned()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_PLAYER_MARKET_GLOBAL_LOCKED",
                        "Private markets cannot change global trading.");
            }
            int nextMode = nextGlobalTradeMode(current.globalTradeMode());
            return service.updateZone(new MarketZone(current.id(), current.name(),
                    current.areaId(),
                    current.minChunkX(), current.maxChunkX(), current.minChunkY(), current.maxChunkY(),
                    current.minChunkZ(), current.maxChunkZ(), current.feePercent(), nextMode,
                    current.createdAt(), current.ownerDbId(), current.ownerName(), current.ownerAreaPermission()));
        } catch (SQLException ex) {
            logger().error("Failed to update market zone global override: " + ex.getMessage());
            return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_GLOBAL_FAILED",
                    "Could not update the market zone global mode.");
        }
    }

    public MarketplaceResult setCurrentMarketZoneFee(Player player, int feePercent) {
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_NOT_IN_MARKET",
                        "You are not standing in a market zone.");
            }
            MarketZone current = zone.get();
            if (!service.canManageZone(player, current)) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_MANAGE_OWN_ONLY",
                        "You can only manage your own market.");
            }
            return service.updateZone(new MarketZone(current.id(), current.name(),
                    current.areaId(),
                    current.minChunkX(), current.maxChunkX(), current.minChunkY(), current.maxChunkY(),
                    current.minChunkZ(), current.maxChunkZ(), feePercent, current.globalTradeMode(),
                    current.createdAt(), current.ownerDbId(), current.ownerName(), current.ownerAreaPermission()));
        } catch (SQLException ex) {
            logger().error("Failed to update market zone fee: " + ex.getMessage());
            return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_FEE_FAILED",
                    "Could not update the market zone fee.");
        }
    }

    public MarketplaceResult deleteCurrentMarketZone(Player player) {
        return deleteCurrentMarketZone(player, null);
    }

    private MarketplaceResult deleteCurrentMarketZone(Player player, String expectedZoneId) {
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_NOT_IN_MARKET",
                        "You are not standing in a market zone.");
            }
            if (!service.canManageZone(player, zone.get())) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_MANAGE_OWN_ONLY",
                        "You can only manage your own market.");
            }
            if (expectedZoneId != null && !expectedZoneId.isBlank() && !zone.get().id().equals(expectedZoneId)) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_DELETE_CURRENT_ONLY",
                        "You can only delete the market zone you are currently standing in.");
            }
            return service.deleteZone(zone.get().id());
        } catch (SQLException ex) {
            logger().error("Failed to delete current market zone: " + ex.getMessage());
            return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_DELETE_FAILED",
                    "Could not delete market zone.");
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

    public boolean walletAvailable() {
        return service != null && service.walletAvailable();
    }

    public boolean marketplaceZoneIndicatorVisible(Player player, MarketZone zone) {
        if (!showMarketplaceZoneIndicator() || zone == null) {
            return false;
        }
        if (s.localMarketplaceEnabled || s.globalMarketplaceEnabled) {
            return true;
        }
        return zone.globalTradeMode() == MarketZone.GLOBAL_ALLOW;
    }

    public I18n i18n() {
        return t;
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
        return createMarketplaceListing(player, itemName, variant, amount, price, currency, global, null);
    }

    public MarketplaceResult createMarketplaceListing(Player player, String itemName, int variant, int amount,
            long price, String currency, boolean global, MarketplaceItemState itemState) {
        if (service == null) {
            return databaseUnavailable();
        }
        return service.createListing(player, itemName, variant, amount, price, currency, global, itemState);
    }

    public MarketplaceResult buyMarketplaceListing(Player player, long listingId) {
        return buyMarketplaceListing(player, listingId, Integer.MAX_VALUE);
    }

    public MarketplaceResult buyMarketplaceListing(Player player, long listingId, int amount) {
        if (service == null) {
            return databaseUnavailable();
        }
        return service.buy(player, listingId, amount);
    }

    public MarketplaceResult cancelMarketplaceListing(Player player, long listingId) {
        if (service == null) {
            return databaseUnavailable();
        }
        return service.cancel(player, listingId);
    }

    public MarketplaceResult createWantedMarketplaceListing(Player player, String itemName, int variant, int amount,
            long price, String currency, boolean global) {
        if (service == null) return databaseUnavailable();
        return service.createWantedListing(player, itemName, variant, amount, price, currency, global);
    }

    public boolean marketplaceManagementAvailable(Player player) {
        if (player == null || service == null) return false;
        if (player.isAdmin()) return true;
        Optional<MarketZone> zone = safeCurrentMarketZone(player);
        return (s.maxPlayerMarketplaces != 0 && player.getCurrentArea() != null)
                || zone.filter(current -> current.ownedBy(player.getDbID())).isPresent();
    }

    public boolean canCreatePrivateMarket(Player player) {
        return service != null && service.canCreatePlayerMarket(player);
    }

    public MarketplaceResult hideMarketplaceSale(Player player, long saleId) {
        if (service == null) {
            return databaseUnavailable();
        }
        return service.hideSale(player, saleId);
    }

    public String defaultCurrencyIdentifier() {
        if (service == null) {
            return "default";
        }
        return service.defaultCurrencyIdentifier();
    }

    public List<WalletBridge.CurrencyInfo> walletCurrencies() {
        if (service == null) {
            return List.of();
        }
        return service.availableCurrencies();
    }

    public long marketplaceBuyerFee(Player buyer, MarketplaceListing listing) {
        if (service == null) {
            return 0L;
        }
        return service.buyerFee(buyer, listing);
    }

    public int marketplaceBuyerFeePercent(Player buyer, MarketplaceListing listing) {
        if (service == null) {
            return 0;
        }
        return service.buyerFeePercent(buyer, listing);
    }

    public long defaultCurrencyBalance(Player player) {
        if (service == null || player == null) {
            return 0L;
        }
        return service.defaultCurrencyBalance(player);
    }

    public WalletBridge.BalanceInfo walletBalance(Player player, String currencyIdentifier) {
        if (service == null || player == null) {
            return new WalletBridge.BalanceInfo(false, 0L);
        }
        return service.balance(player, currencyIdentifier);
    }

    public static PlayerSettings playerSettings() {
        return playerSettings;
    }

    public boolean localListingAllowed(Player player) {
        return s != null && s.localMarketplaceEnabled && safeCurrentMarketZone(player).isPresent();
    }

    public boolean globalListingAllowed(Player player) {
        if (s == null) {
            return false;
        }
        Optional<MarketZone> zone = safeCurrentMarketZone(player);
        if (zone.isEmpty()) {
            return s.globalMarketplaceEnabled && !s.marketZoneOnlyMode;
        }
        return zone.get().globalTradeAllowed(s.globalMarketplaceEnabled);
    }

    public boolean sellingAllowed(Player player) {
        return localListingAllowed(player) || globalListingAllowed(player);
    }

    public String currentMarketZoneStatus(Player player) {
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return tr(player, "TC_MARKET_CHAT_NO_CURRENT_ZONE");
            }
            MarketZone current = zone.get();
            return tr(player, "TC_MARKET_CHAT_CURRENT_ZONE",
                    "PH_NAME", current.name(),
                    "PH_ZONE", current.id(),
                    "PH_FEE", String.valueOf(current.feePercent()),
                    "PH_GLOBAL", globalTradeModeLabel(player, current.globalTradeMode()));
        } catch (SQLException ex) {
            logger().error("Failed to read current market zone: " + ex.getMessage());
            return tr(player, "TC_MARKET_CHAT_CURRENT_ZONE_FAILED");
        }
    }

    public void sendListings(Player player) {
        try {
            List<MarketplaceListing> listings = service.listVisibleListings(player);
            if (listings.isEmpty()) {
                player.sendTextMessage(c.info + tr(player, "TC_MARKET_CHAT_NO_LISTINGS"));
                player.sendTextMessage(c.text + tr(player, "TC_MARKET_CHAT_OPEN_HINT",
                        "PH_COMMAND", s.marketCommand));
                return;
            }
            player.sendTextMessage(c.okay + tr(player, "TC_MARKET_CHAT_LISTINGS"));
            for (MarketplaceListing listing : listings) {
                player.sendTextMessage(c.info + tr(player, "TC_MARKET_CHAT_LISTING_ROW",
                        "PH_LISTING", String.valueOf(listing.id()),
                        "PH_AMOUNT", String.valueOf(listing.amount()),
                        "PH_ITEM", listing.itemName() + ":" + listing.itemVariant(),
                        "PH_SELLER", listing.sellerName(),
                        "PH_PRICE", String.valueOf(listing.price()),
                        "PH_CURRENCY", currencyLabel(listing.currencyIdentifier()),
                        "PH_MODE", listing.globalListing()
                                ? tr(player, "TC_MARKET_UI_MODE_GLOBAL")
                                : tr(player, "TC_MARKET_UI_MODE_LOCAL") + ":" + listing.marketZoneId()));
            }
            player.sendTextMessage(c.text + tr(player, "TC_MARKET_CHAT_BUY_HINT",
                    "PH_COMMAND", s.marketCommand));
        } catch (SQLException ex) {
            logger().error("Failed to list marketplace listings: " + ex.getMessage());
            player.sendTextMessage(c.error + tr(player, "TC_MARKET_CHAT_LISTINGS_FAILED"));
        }
    }

    private void sendZones(Player player) {
        try {
            List<MarketZone> zones = service.listZones();
            if (zones.isEmpty()) {
                player.sendTextMessage(c.info + tr(player, "TC_MARKET_CHAT_NO_ZONES"));
                return;
            }
            player.sendTextMessage(c.okay + tr(player, "TC_MARKET_CHAT_ZONES"));
            for (MarketZone zone : zones) {
                player.sendTextMessage(c.info + tr(player, "TC_MARKET_CHAT_ZONE_ROW",
                        "PH_ZONE", zone.id(),
                        "PH_NAME", zone.name(),
                        "PH_FEE", String.valueOf(zone.feePercent()),
                        "PH_GLOBAL", globalTradeModeLabel(player, zone.globalTradeMode()),
                        "PH_AREA", String.valueOf(zone.areaId())));
            }
        } catch (SQLException ex) {
            logger().error("Failed to list market zones: " + ex.getMessage());
            player.sendTextMessage(c.error + tr(player, "TC_MARKET_CHAT_ZONES_FAILED"));
        }
    }

    private void usage(Player player) {
        player.sendTextMessage(c.warning + tr(player, "TC_MARKET_CHAT_USAGE",
                "PH_COMMAND", s.marketCommand));
        if (player.isAdmin()) {
            player.sendTextMessage(c.warning + tr(player, "TC_MARKET_CHAT_USAGE_ADMIN",
                    "PH_COMMAND", s.marketCommand));
        }
    }

    private void sendResult(Player player, MarketplaceResult result) {
        player.sendTextMessage((result.success() ? c.okay : c.error) + result.localized(t, player));
    }

    private String tr(Player player, String key, String... replacements) {
        String text = t.get(key, player);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    private MarketplaceResult databaseUnavailable() {
        return MarketplaceResult.failKey("TC_MARKET_RESULT_DATABASE_UNAVAILABLE",
                "Marketplace database is not available.");
    }

    public String globalTradeModeLabel(Player player, int mode) {
        return switch (MarketZone.normalizeGlobalTradeMode(mode)) {
            case MarketZone.GLOBAL_DENY -> tr(player, "TC_MARKET_CHAT_GLOBAL_DENY");
            case MarketZone.GLOBAL_ALLOW -> tr(player, "TC_MARKET_CHAT_GLOBAL_ALLOW");
            default -> tr(player, "TC_MARKET_CHAT_GLOBAL_DEFAULT");
        };
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
        return currency == null || currency.isBlank() ? " " + defaultCurrencyIdentifier() : " " + currency;
    }

    private String t(String key, Player player) {
        return t.get(key, player);
    }

    private String currentZoneId(Player player) {
        Area area = player.getCurrentArea();
        if (area != null && area.getID() > 0L) {
            return "area-" + area.getID();
        }
        return "area-unknown";
    }

    private String currentZoneName(Player player) {
        Area area = player.getCurrentArea();
        if (area != null && area.getName() != null && !area.getName().isBlank()) {
            return area.getName();
        }
        return "Market Area";
    }

    private int parseGlobalMode(String value) {
        if (value == null) {
            return MarketZone.GLOBAL_DEFAULT;
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "true", "allow", "allowed", "on", "2" -> MarketZone.GLOBAL_ALLOW;
            case "false", "deny", "denied", "off", "0" -> MarketZone.GLOBAL_DENY;
            default -> MarketZone.GLOBAL_DEFAULT;
        };
    }

    private int nextGlobalTradeMode(int currentMode) {
        return switch (MarketZone.normalizeGlobalTradeMode(currentMode)) {
            case MarketZone.GLOBAL_DEFAULT -> MarketZone.GLOBAL_ALLOW;
            case MarketZone.GLOBAL_ALLOW -> MarketZone.GLOBAL_DENY;
            default -> MarketZone.GLOBAL_DEFAULT;
        };
    }
}
