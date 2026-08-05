package de.omegazirkel.risingworld.marketplace;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.bridge.MailBridge;
import net.risingworld.api.Server;
import net.risingworld.api.objects.Area;
import net.risingworld.api.objects.Player;
import net.risingworld.api.utils.Vector3i;

public class MarketplaceService {
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PENDING_PURCHASE = "PENDING_PURCHASE";
    private static final String STATUS_PENDING_CANCEL = "PENDING_CANCEL";
    private static final String STATUS_SOLD = "SOLD";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final MarketplaceDatabase database;
    private final WalletBridge wallet;
    private final MailBridge mail;
    private PluginSettings settings;

    public MarketplaceService(MarketplaceDatabase database, WalletBridge wallet, MailBridge mail,
            PluginSettings settings) {
        this.database = database;
        this.wallet = wallet;
        this.mail = mail;
        this.settings = settings;
    }

    public void updateSettings(PluginSettings settings) {
        this.settings = settings;
    }

    public boolean walletAvailable() {
        return wallet.isAvailable();
    }

    public String defaultCurrencyIdentifier() {
        return wallet.defaultCurrencyIdentifier();
    }

    public List<WalletBridge.CurrencyInfo> availableCurrencies() {
        return wallet.listCurrencies();
    }

    public MarketplaceResult createZone(String id, String name, Vector3i center, int radius, int feePercent,
            boolean allowGlobalTrade) {
        return createZone(id, name, 0L, center, radius, feePercent,
                allowGlobalTrade ? MarketZone.GLOBAL_ALLOW : MarketZone.GLOBAL_DENY);
    }

    public MarketplaceResult createAreaZone(String id, String name, long areaId, int feePercent, int globalTradeMode) {
        Area area = Server.getArea(areaId);
        if (area != null && area.isValid()) {
            try {
                database.upsertZone(areaZone(id, name, area, feePercent, globalTradeMode, 0, "", "", now()));
                return MarketplaceResult.okKey("TC_MARKET_RESULT_ZONE_SAVED", "Market zone saved: PH_ZONE.",
                        "PH_ZONE", id);
            } catch (SQLException ex) {
                Marketplace.logger().error("Failed to save market zone: " + ex.getMessage());
                return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_SAVE_FAILED", "Could not save market zone.");
            }
        }
        return createZone(id, name, areaId, new Vector3i(0, 0, 0), 0, feePercent, globalTradeMode);
    }

    public MarketplaceResult createZone(String id, String name, long areaId, Vector3i center, int radius, int feePercent,
            int globalTradeMode) {
        if (id == null || id.isBlank()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_ID_REQUIRED", "Zone id is required.");
        }
        if (radius < 0) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_RADIUS_INVALID",
                    "Zone radius must be at least 0 chunks.");
        }
        int fee = Math.max(0, Math.min(100, feePercent));
        MarketZone zone = new MarketZone(
                id.trim().toLowerCase(),
                name == null || name.isBlank() ? id.trim() : name.trim(),
                Math.max(0L, areaId),
                center.x - radius,
                center.x + radius,
                center.y - radius,
                center.y + radius,
                center.z - radius,
                center.z + radius,
                fee,
                MarketZone.normalizeGlobalTradeMode(globalTradeMode),
                now(), 0, "", "");
        try {
            database.upsertZone(zone);
            return MarketplaceResult.okKey("TC_MARKET_RESULT_ZONE_SAVED", "Market zone saved: PH_ZONE.",
                    "PH_ZONE", zone.id());
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to save market zone: " + ex.getMessage());
            return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_SAVE_FAILED", "Could not save market zone.");
        }
    }

    public MarketplaceResult updateZone(MarketZone zone) {
        if (zone == null || zone.id() == null || zone.id().isBlank()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_ID_REQUIRED", "Zone id is required.");
        }
        int fee = Math.max(0, Math.min(100, zone.feePercent()));
        MarketZone updated = new MarketZone(
                zone.id().trim().toLowerCase(),
                zone.name() == null || zone.name().isBlank() ? zone.id().trim() : zone.name().trim(),
                zone.areaId(),
                zone.minChunkX(),
                zone.maxChunkX(),
                zone.minChunkY(),
                zone.maxChunkY(),
                zone.minChunkZ(),
                zone.maxChunkZ(),
                fee,
                zone.globalTradeMode(),
                zone.createdAt(), zone.ownerDbId(), zone.ownerName(), zone.ownerAreaPermission());
        try {
            database.upsertZone(updated);
            return MarketplaceResult.okKey("TC_MARKET_RESULT_ZONE_SAVED", "Market zone saved: PH_ZONE.",
                    "PH_ZONE", updated.id());
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to save market zone: " + ex.getMessage());
            return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_SAVE_FAILED", "Could not save market zone.");
        }
    }

    public MarketplaceResult deleteZone(String id) {
        if (id == null || id.isBlank()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_ID_REQUIRED", "Zone id is required.");
        }
        try {
            MarketplaceDatabase.ZoneDeleteResult result = database.deleteZoneIfEmpty(id.trim().toLowerCase());
            if (!result.deleted()) {
                if (result.activeListings() > 0) {
                    return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_NOT_EMPTY",
                            "A market can only be dissolved after all listings are gone.");
                }
                return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_NOT_FOUND",
                        "Market zone not found: PH_ZONE.", "PH_ZONE", id);
            }
            return MarketplaceResult.okKey("TC_MARKET_RESULT_ZONE_DELETED",
                    "Market zone deleted: PH_ZONE.", "PH_ZONE", id);
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to delete market zone: " + ex.getMessage());
            return MarketplaceResult.failKey("TC_MARKET_RESULT_ZONE_DELETE_FAILED",
                    "Could not delete market zone.");
        }
    }

    public List<MarketZone> listZones() throws SQLException {
        return database.listZones();
    }

    public Optional<MarketZone> currentZone(Player player) throws SQLException {
        Area area = player.getCurrentArea();
        if (area != null && area.getID() > 0L) {
            Optional<MarketZone> areaZone = database.listZones().stream()
                    .filter(zone -> zone.areaId() == area.getID())
                    .min(Comparator.comparing(MarketZone::id));
            if (areaZone.isPresent()) {
                return areaZone;
            }
        }
        Vector3i chunk = player.getChunkPosition();
        return database.listZones().stream()
                .filter(zone -> !zone.isAreaZone())
                .filter(zone -> zone.contains(chunk.x, chunk.y, chunk.z))
                .min(Comparator.comparing(MarketZone::id));
    }

    public MarketplaceResult createPlayerMarket(Player player) {
        if (player == null || player.getDbID() <= 0) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_PLAYER_ID_MISSING",
                    "Player has no database id.");
        }
        if (settings.maxPlayerMarketplaces == 0) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_PLAYER_MARKETS_DISABLED",
                    "Player-created markets are disabled.");
        }
        Area area = player.getCurrentArea();
        if (area == null || area.getID() <= 0L) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_AREA_REQUIRED",
                    "Stand inside an existing area to create a market.");
        }
        if (!hasAddPlayerPermission(player)) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_AREA_PERMISSION_REQUIRED",
                    "You need the area addplayer permission to create a market.");
        }
        try {
            if (database.areaHasMarket(area.getID(), "")) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_AREA_ALREADY_MARKET",
                        "This area is already linked to a market.");
            }
            if (settings.maxPlayerMarketplaces > 0
                    && database.playerMarketCount(player.getDbID()) >= settings.maxPlayerMarketplaces) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_PLAYER_MARKET_LIMIT",
                        "You reached the player market limit.");
            }
            String permission = permissionToken(area, player.getDbID());
            MarketZone zone = areaZone("player-" + player.getDbID() + "-area-" + area.getID(), area.getName(),
                    area, settings.defaultLocalFeePercent, MarketZone.GLOBAL_DEFAULT, player.getDbID(),
                    player.getName(), permission, now());
            database.upsertZone(zone);
            return MarketplaceResult.okKey("TC_MARKET_RESULT_PLAYER_MARKET_CREATED",
                    "Player market created: PH_ZONE.", "PH_ZONE", zone.name());
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to create player market: " + ex.getMessage());
            return MarketplaceResult.failKey("TC_MARKET_RESULT_PLAYER_MARKET_CREATE_FAILED",
                    "Could not create player market.");
        }
    }

    public boolean canCreatePlayerMarket(Player player) {
        if (player == null || player.getDbID() <= 0 || settings.maxPlayerMarketplaces == 0) return false;
        Area area = player.getCurrentArea();
        if (area == null || area.getID() <= 0L || !hasAddPlayerPermission(player)) return false;
        try {
            return !database.areaHasMarket(area.getID(), "") && (settings.maxPlayerMarketplaces < 0
                    || database.playerMarketCount(player.getDbID()) < settings.maxPlayerMarketplaces);
        } catch (SQLException ex) { return false; }
    }

    public boolean canManageZone(Player player, MarketZone zone) {
        return player != null && zone != null && (player.isAdmin() || zone.ownedBy(player.getDbID()));
    }

    public int repairLostPlayerMarkets() {
        int repairs = 0;
        try {
            List<MarketZone> zones = database.listZones();
            Set<Long> usedAreas = new HashSet<>();
            for (MarketZone zone : zones) if (zone.areaId() > 0L) usedAreas.add(zone.areaId());
            for (MarketZone zone : zones) {
                if (!zone.playerOwned()) continue;
                Area linked = Server.getArea(zone.areaId());
                if (eligibleOwnerArea(linked, zone)) continue;
                Area replacement = nearestEligibleArea(zone, Server.getAllAreas(), usedAreas);
                if (replacement != null) {
                    usedAreas.remove(zone.areaId());
                    usedAreas.add(replacement.getID());
                    MarketZone moved = areaZone(zone.id(), replacement.getName(), replacement, zone.feePercent(),
                            zone.globalTradeMode(), zone.ownerDbId(), zone.ownerName(),
                            permissionToken(replacement, zone.ownerDbId()), zone.createdAt());
                    database.upsertZone(moved);
                    repairs++;
                    Marketplace.logger().warn("Repaired player market " + zone.id() + ": area " + zone.areaId()
                            + " -> " + replacement.getID() + ".");
                    continue;
                }
                MarketZone nearest = zones.stream()
                        .filter(candidate -> !candidate.id().equals(zone.id()))
                        .min(Comparator.comparingLong((MarketZone candidate) -> squaredDistance(zone, candidate))
                                .thenComparing(MarketZone::id))
                        .orElse(null);
                int movedListings = database.relinkListings(zone.id(), nearest == null ? "global" : nearest.id(),
                        nearest == null);
                database.deleteZone(zone.id());
                repairs++;
                Marketplace.logger().warn("Removed lost player market " + zone.id() + " and moved "
                        + movedListings + " listing(s) to " + (nearest == null ? "global" : nearest.id()) + ".");
            }
        } catch (SQLException | RuntimeException ex) {
            Marketplace.logger().error("Failed to repair lost player markets: " + ex.getMessage());
        }
        return repairs;
    }

    public MarketplaceResult createListing(Player seller, String itemName, int itemVariant, int amount, long price,
            String currencyIdentifier, boolean globalListing) {
        return createListing(seller, itemName, itemVariant, amount, price, currencyIdentifier, globalListing, null);
    }

    public MarketplaceResult createWantedListing(Player requester, String itemName, int itemVariant, int amount,
            long offeredPrice, String currencyIdentifier, boolean globalListing) {
        if (!walletAvailable()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_WALLET_REQUIRED",
                    "OZ - Wallet is required for this Marketplace action.");
        }
        if (requester == null || requester.getDbID() <= 0 || itemName == null || itemName.isBlank()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_REQUESTER_ITEM_REQUIRED",
                    "Requester and item are required.");
        }
        if (amount <= 0 || offeredPrice <= 0 || itemVariant < 0) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_AMOUNT_PRICE_INVALID",
                    "Amount and offered price must be greater than 0.");
        }
        String currency = normalizeListingCurrency(currencyIdentifier);
        MarketplaceResult currencyValidation = validateCurrency(currency);
        if (!currencyValidation.success()) return currencyValidation;
        try {
            if (database.activeListingCount(requester.getDbID()) >= listingCapacity(requester)) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_LISTING_LIMIT",
                        "You reached the active listing limit.");
            }
            Optional<MarketZone> zone = currentZone(requester);
            MarketplaceResult access = validateListingLocation(globalListing, zone);
            if (!access.success()) return access;
            MarketplaceListing listing = new MarketplaceListing(0L, requester.getDbID(), requester.getName(),
                    itemName.trim(), itemVariant, amount, MarketplaceItemState.NEUTRAL, offeredPrice, currency,
                    zone.map(MarketZone::id).orElse("global"), globalListing, now(), STATUS_ACTIVE,
                    MarketplaceListing.TYPE_WANTED, amount, 0, offeredPrice);
            long id = database.createListing(listing);
            return id > 0L ? MarketplaceResult.okKey("TC_MARKET_RESULT_WANTED_CREATED",
                    "Wanted listing #PH_LISTING created.", "PH_LISTING", String.valueOf(id))
                    : MarketplaceResult.failKey("TC_MARKET_RESULT_WANTED_CREATE_FAILED",
                            "Could not create wanted listing.");
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to create wanted listing: " + ex.getMessage());
            return MarketplaceResult.failKey("TC_MARKET_RESULT_WANTED_CREATE_FAILED",
                    "Could not create wanted listing.");
        }
    }

    public MarketplaceResult createListing(Player seller, String itemName, int itemVariant, int amount, long price,
            String currencyIdentifier, boolean globalListing, MarketplaceItemState requestedItemState) {
        if (!walletAvailable()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_WALLET_REQUIRED",
                    "OZ - Wallet is required for this Marketplace action.");
        }
        if (seller == null || seller.getDbID() <= 0) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_PLAYER_ID_MISSING",
                    "Player has no database id.");
        }
        if (price <= 0 || amount <= 0) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_AMOUNT_PRICE_INVALID",
                    "Amount and offered price must be greater than 0.");
        }
        String listingCurrency = normalizeListingCurrency(currencyIdentifier);
        MarketplaceResult currencyValidation = validateCurrency(listingCurrency);
        if (!currencyValidation.success()) {
            return currencyValidation;
        }
        if (!globalListing && !settings.localMarketplaceEnabled) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_LOCAL_DISABLED",
                    "Local marketplace listings are disabled.");
        }
        boolean inventoryRemoved = false;
        MarketplaceItemState itemState = null;
        try {
            if (database.activeListingCount(seller.getDbID()) >= listingCapacity(seller)) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_LISTING_LIMIT",
                        "You reached the active listing limit.");
            }
            Optional<MarketZone> zone = currentZone(seller);
            if (!globalListing && zone.isEmpty()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_LOCAL_ZONE_REQUIRED",
                        "You must stand in a market zone to create a local listing.");
            }
            if (globalListing && settings.marketZoneOnlyMode && zone.isEmpty()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_GLOBAL_ZONE_REQUIRED",
                        "You must stand in a market zone to create a global listing.");
            }
            if (globalListing && zone.isEmpty() && !settings.globalMarketplaceEnabled) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_GLOBAL_DISABLED",
                        "Global marketplace listings are disabled.");
            }
            if (globalListing && zone.isPresent() && !zone.get().globalTradeAllowed(settings.globalMarketplaceEnabled)) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_GLOBAL_NOT_ALLOWED",
                        "This market zone does not allow global trade.");
            }
            itemState = requestedItemState == null
                    ? InventoryTransfer.snapshotForSeller(seller, itemName, itemVariant, amount)
                    : requestedItemState;
            if (itemState == null) return MarketplaceResult.failKey("TC_MARKET_RESULT_ITEMS_NOT_MATCHING",
                    "You do not have enough matching items in one item state.");
            MarketplaceResult inventory = InventoryTransfer.removeFromSeller(seller, itemName, itemVariant, amount, itemState);
            if (!inventory.success()) {
                return inventory;
            }
            inventoryRemoved = true;
            MarketplaceListing listing = new MarketplaceListing(
                    0L,
                    seller.getDbID(),
                    seller.getName(),
                    itemName.trim(),
                    itemVariant,
                    amount,
                    itemState,
                    price,
                    listingCurrency,
                    zone.map(MarketZone::id).orElse("global"),
                    globalListing,
                    now(),
                    STATUS_ACTIVE);
            long id = database.createListing(listing);
            if (id <= 0L) {
                MarketplaceResult returned = InventoryTransfer.addToBuyer(seller, itemName, itemVariant, amount, itemState);
                if (!returned.success()) {
                    Marketplace.logger().error("Failed to return inventory after marketplace listing id generation failed.");
                }
                return MarketplaceResult.failKey("TC_MARKET_RESULT_LISTING_CREATE_FAILED",
                        "Could not create listing.");
            }
            return MarketplaceResult.okKey("TC_MARKET_RESULT_LISTING_CREATED",
                    "Listing #PH_LISTING created.", "PH_LISTING", String.valueOf(id));
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to create listing: " + ex.getMessage());
            if (inventoryRemoved && itemState != null) {
                MarketplaceResult returned = InventoryTransfer.addToBuyer(seller, itemName, itemVariant, amount, itemState);
                if (!returned.success()) {
                    Marketplace.logger().error("Failed to return inventory after marketplace listing creation failed.");
                }
            }
            return MarketplaceResult.failKey("TC_MARKET_RESULT_LISTING_CREATE_FAILED",
                    "Could not create listing.");
        }
    }

    public MarketplaceResult buy(Player buyer, long listingId) {
        return buy(buyer, listingId, Integer.MAX_VALUE);
    }

    public MarketplaceResult buy(Player buyer, long listingId, int requestedAmount) {
        if (!walletAvailable()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_WALLET_REQUIRED",
                    "OZ - Wallet is required for this Marketplace action.");
        }
        boolean listingReserved = false;
        boolean externalTransferCompleted = false;
        try {
            Optional<MarketplaceListing> found = database.findActiveListing(listingId);
            if (found.isEmpty()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_LISTING_NOT_FOUND", "Listing not found.");
            }
            MarketplaceListing listing = found.get();
            if (listing.wanted()) {
                return fulfillWanted(buyer, listing, requestedAmount);
            }
            if (listing.sellerDbId() == buyer.getDbID()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_OWN_LISTING",
                        "You cannot trade with your own listing.");
            }
            int purchaseAmount = requestedAmount == Integer.MAX_VALUE ? listing.amount() : requestedAmount;
            if (purchaseAmount <= 0 || purchaseAmount > listing.amount()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_AMOUNT_RANGE",
                        "Amount must be between 1 and PH_AVAILABLE.",
                        "PH_AVAILABLE", String.valueOf(listing.amount()));
            }
            if (!listing.globalListing() && !settings.localMarketplaceEnabled) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_LOCAL_DISABLED",
                        "Local marketplace listings are disabled.");
            }
            Optional<MarketZone> zone = currentZone(buyer);
            if (!listing.globalListing() && zone.isEmpty()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_LOCAL_TRADE_ZONE_REQUIRED",
                        "You must stand in the listing's market zone for this local trade.");
            }
            if (listing.globalListing() && settings.marketZoneOnlyMode && zone.isEmpty()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_GLOBAL_TRADE_ZONE_REQUIRED",
                        "You must stand in a market zone for global trading.");
            }
            if (listing.globalListing() && zone.isEmpty() && !settings.globalMarketplaceEnabled) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_GLOBAL_DISABLED",
                        "Global marketplace listings are disabled.");
            }
            if (!listing.globalListing() && !zone.get().id().equals(listing.marketZoneId())) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_WRONG_MARKET_ZONE",
                        "This local listing belongs to another market zone.");
            }
            if (listing.globalListing() && zone.isPresent()
                    && !zone.get().globalTradeAllowed(settings.globalMarketplaceEnabled)) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_GLOBAL_NOT_ALLOWED",
                        "This market zone does not allow global trade.");
            }
            if (!database.transitionListingStatus(listing.id(), STATUS_ACTIVE, STATUS_PENDING_PURCHASE)) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_LISTING_UNAVAILABLE",
                        "Listing is no longer available.");
            }
            listingReserved = true;

            long purchasePrice = partialPrice(listing.price(), listing.amount(), purchaseAmount);
            WalletBridge.WalletCallResult withdraw = purchasePrice > 0
                    ? wallet.withdraw(buyer.getDbID(), purchasePrice,
                            "Marketplace purchase #" + listing.id(), listing.currencyIdentifier(), "OZ - Marketplace")
                    : new WalletBridge.WalletCallResult(true, "No item charge.");
            if (!withdraw.success()) {
                releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                return walletFailure(withdraw.message());
            }

            long fee = fee(listing, zone.orElse(null), purchasePrice);
            long sellerPayout = purchasePrice;
            int feeRecipientDbId = zone.filter(MarketZone::playerOwned).map(MarketZone::ownerDbId).orElse(0);
            FeePayment feePayment = chargeFee(buyer.getDbID(), fee, "Marketplace fee #" + listing.id(),
                    listing.currencyIdentifier(), feeRecipientDbId, "sale:" + listing.id());
            WalletBridge.WalletCallResult feeWithdraw = feePayment.result();
            if (!feeWithdraw.success()) {
                WalletBridge.WalletCallResult purchaseRefund = wallet.deposit(buyer.getDbID(), purchasePrice,
                        "Marketplace purchase refund #" + listing.id(), listing.currencyIdentifier(), "OZ - Marketplace");
                logWalletRollbackFailure("buyer purchase refund", listing.id(), purchaseRefund);
                releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                return walletFailure(feeWithdraw.message());
            }
            if (sellerPayout > 0) {
                WalletBridge.WalletCallResult deposit = wallet.deposit(listing.sellerDbId(), sellerPayout,
                        "Marketplace sale #" + listing.id(), listing.currencyIdentifier(), "OZ - Marketplace");
                if (!deposit.success()) {
                    refundRoutedFee(feePayment, "Marketplace fee refund #" + listing.id());
                    WalletBridge.WalletCallResult refund = wallet.deposit(buyer.getDbID(),
                            purchasePrice + (feePayment.routedToWorld() ? 0 : fee),
                            "Marketplace refund #" + listing.id(), listing.currencyIdentifier(), "OZ - Marketplace");
                    logWalletRollbackFailure("buyer refund", listing.id(), refund);
                    releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                    return walletFailure(deposit.message());
                }
            }
            if (fee > 0 && feeRecipientDbId > 0) {
                WalletBridge.WalletCallResult feeDeposit = wallet.deposit(feeRecipientDbId, fee,
                        "Marketplace market tax #" + listing.id(), listing.currencyIdentifier(), "OZ - Marketplace");
                if (!feeDeposit.success()) {
                    if (sellerPayout > 0) {
                        logWalletRollbackFailure("seller payout rollback", listing.id(),
                                wallet.withdraw(listing.sellerDbId(), sellerPayout,
                                        "Marketplace payout rollback #" + listing.id(),
                                        listing.currencyIdentifier(), "OZ - Marketplace"));
                    }
                    logWalletRollbackFailure("buyer refund", listing.id(), wallet.deposit(buyer.getDbID(),
                            purchasePrice + fee,
                            "Marketplace refund #" + listing.id(), listing.currencyIdentifier(), "OZ - Marketplace"));
                    releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                    return walletFailure(feeDeposit.message());
                }
            }
            MarketplaceResult addItem = InventoryTransfer.addToBuyer(buyer, listing.itemName(), listing.itemVariant(),
                    purchaseAmount, listing.itemState());
            if (!addItem.success()) {
                refundRoutedFee(feePayment, "Marketplace fee refund #" + listing.id());
                if (fee > 0 && feeRecipientDbId > 0) {
                    logWalletRollbackFailure("market tax rollback", listing.id(),
                            wallet.withdraw(feeRecipientDbId, fee, "Marketplace tax rollback #" + listing.id(),
                                    listing.currencyIdentifier(), "OZ - Marketplace"));
                }
                if (sellerPayout > 0) {
                    WalletBridge.WalletCallResult payoutRollback = wallet.withdraw(listing.sellerDbId(), sellerPayout,
                            "Marketplace payout rollback #" + listing.id(),
                            listing.currencyIdentifier(), "OZ - Marketplace");
                    logWalletRollbackFailure("seller payout rollback", listing.id(), payoutRollback);
                }
                WalletBridge.WalletCallResult refund = wallet.deposit(buyer.getDbID(),
                        purchasePrice + (feePayment.routedToWorld() ? 0 : fee),
                        "Marketplace refund #" + listing.id(), listing.currencyIdentifier(), "OZ - Marketplace");
                logWalletRollbackFailure("buyer refund", listing.id(), refund);
                releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                return addItem;
            }
            externalTransferCompleted = true;
            int remainingAmount = listing.amount() - purchaseAmount;
            long remainingPrice = Math.max(0L, listing.price() - purchasePrice);
            boolean completed = database.completePartialSale(new MarketplaceSale(0L, listing.id(),
                    listing.sellerDbId(), buyer.getDbID(),
                    listing.itemName(), listing.itemVariant(), purchaseAmount, listing.itemState(), purchasePrice,
                    listing.currencyIdentifier(), fee, sellerPayout, zone.map(MarketZone::id).orElse("global"), now()),
                    purchaseAmount, remainingAmount, remainingPrice, STATUS_PENDING_PURCHASE, STATUS_SOLD);
            if (!completed) {
                Marketplace.logger().error("Marketplace purchase completed externally but listing was already finalized: "
                        + listing.id());
                return MarketplaceResult.failKey("TC_MARKET_RESULT_ADMIN_REVIEW",
                        "The transaction needs administrator review.");
            }
            notifyOnlineSeller(listing, buyer, purchaseAmount, purchasePrice);
            return MarketplaceResult.okKey("TC_MARKET_RESULT_PURCHASED",
                    "Purchased PH_AMOUNT item(s) from listing #PH_LISTING.",
                    "PH_AMOUNT", String.valueOf(purchaseAmount),
                    "PH_LISTING", String.valueOf(listing.id()));
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to buy listing: " + ex.getMessage());
            if (listingReserved && !externalTransferCompleted) {
                releaseListing(listingId, STATUS_PENDING_PURCHASE);
            }
            return MarketplaceResult.failKey("TC_MARKET_RESULT_PURCHASE_FAILED",
                    "Could not complete the purchase.");
        }
    }

    private void notifyOnlineSeller(MarketplaceListing listing, Player buyer, int amount, long price) {
        Player seller = Server.getPlayerByDbID(listing.sellerDbId());
        if (seller == null) {
            return;
        }
        String message = I18n.getInstance(Marketplace.name).get("TC_MARKET_SELLER_SALE_NOTIFICATION", seller)
                .replace("PH_AMOUNT", String.valueOf(amount))
                .replace("PH_ITEM", MarketplaceItemNames.listingLabel(listing.itemName(), listing.itemVariant()))
                .replace("PH_PRICE", String.valueOf(price))
                .replace("PH_CURRENCY", listing.currencyIdentifier())
                .replace("PH_BUYER", buyer.getName());
        seller.sendTextMessage(message);
    }

    public MarketplaceResult cancel(Player seller, long listingId) {
        boolean listingReserved = false;
        try {
            Optional<MarketplaceListing> found = database.findActiveListing(listingId);
            if (found.isEmpty()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_LISTING_NOT_FOUND", "Listing not found.");
            }
            MarketplaceListing listing = found.get();
            if (listing.sellerDbId() != seller.getDbID()) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_ONLY_OWNER_CANCEL",
                        "Only the listing owner can withdraw it.");
            }
            if (listing.wanted() && listing.fulfilledAmount() > 0) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_WANTED_WITHDRAW_LOCKED",
                        "A wanted listing cannot be withdrawn after its first fulfillment.");
            }
            if (!database.transitionListingStatus(listing.id(), STATUS_ACTIVE, STATUS_PENDING_CANCEL)) {
                return MarketplaceResult.failKey("TC_MARKET_RESULT_LISTING_UNAVAILABLE",
                        "Listing is no longer available.");
            }
            listingReserved = true;
            if (listing.offer()) {
                MarketplaceResult returned = InventoryTransfer.addToBuyer(seller, listing.itemName(),
                        listing.itemVariant(), listing.amount(), listing.itemState());
                if (!returned.success()) {
                    releaseListing(listing.id(), STATUS_PENDING_CANCEL);
                    return returned;
                }
            }
            if (!database.transitionListingStatus(listing.id(), STATUS_PENDING_CANCEL, STATUS_CANCELLED)) {
                Marketplace.logger().error("Marketplace cancellation returned inventory but failed to finalize listing: "
                        + listing.id());
                return MarketplaceResult.failKey("TC_MARKET_RESULT_ADMIN_REVIEW",
                        "The transaction needs administrator review.");
            }
            return MarketplaceResult.okKey("TC_MARKET_RESULT_LISTING_CANCELLED",
                    "Listing #PH_LISTING withdrawn.", "PH_LISTING", String.valueOf(listing.id()));
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to cancel listing: " + ex.getMessage());
            if (listingReserved) {
                releaseListing(listingId, STATUS_PENDING_CANCEL);
            }
            return MarketplaceResult.failKey("TC_MARKET_RESULT_LISTING_CANCEL_FAILED",
                    "Could not withdraw the listing.");
        }
    }

    public List<MarketplaceListing> listVisibleListings(Player player) throws SQLException {
        Optional<MarketZone> zone = currentZone(player);
        if (zone.isEmpty()) {
            if (settings.marketZoneOnlyMode || !settings.globalMarketplaceEnabled) {
                return List.of();
            }
            return database.listGlobalListings();
        }
        if (!settings.localMarketplaceEnabled && !settings.globalMarketplaceEnabled) {
            return zone.get().globalTradeAllowed(false) ? database.listGlobalListings() : List.of();
        }
        boolean allowGlobal = zone.get().globalTradeAllowed(settings.globalMarketplaceEnabled);
        if (!settings.localMarketplaceEnabled) {
            return allowGlobal ? database.listGlobalListings() : List.of();
        }
        if (!allowGlobal) {
            return database.listActiveListings(zone.get().id(), false);
        }
        return database.listActiveListings(zone.get().id(), true);
    }

    public long buyerFee(Player buyer, MarketplaceListing listing) {
        if (buyer == null || listing == null) {
            return 0L;
        }
        try {
            return fee(listing, currentZone(buyer).orElse(null));
        } catch (SQLException ex) {
            Marketplace.logger().warn("Failed to calculate marketplace fee for listing " + listing.id() + ": "
                    + ex.getMessage());
            return fee(listing, null);
        }
    }

    public int buyerFeePercent(Player buyer, MarketplaceListing listing) {
        if (buyer == null || listing == null) {
            return 0;
        }
        try {
            return feePercent(listing, currentZone(buyer).orElse(null));
        } catch (SQLException ex) {
            Marketplace.logger().warn("Failed to calculate marketplace fee percent for listing " + listing.id()
                    + ": " + ex.getMessage());
            return feePercent(listing, null);
        }
    }

    public long defaultCurrencyBalance(Player player) {
        return player == null ? 0L : wallet.balanceDefault(player.getDbID());
    }

    public WalletBridge.BalanceInfo balance(Player player, String currencyIdentifier) {
        return player == null ? new WalletBridge.BalanceInfo(false, 0L)
                : wallet.balance(player.getDbID(), currencyIdentifier);
    }

    public List<MarketplaceSale> listSales(Player seller, int limit) throws SQLException {
        return database.listSalesForSeller(seller.getDbID(), limit);
    }

    public MarketplaceResult hideSale(Player seller, long saleId) {
        if (seller == null || seller.getDbID() <= 0) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_PLAYER_ID_MISSING",
                    "Player has no database id.");
        }
        if (saleId <= 0L) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_SALE_NOT_FOUND", "Sale not found.");
        }
        try {
            return switch (database.hideSaleForSeller(saleId, seller.getDbID(), now())) {
                case SUCCESS -> MarketplaceResult.okKey("TC_MARKET_RESULT_SALE_REMOVED",
                        "Sale removed from your history.");
                case NOT_FOUND -> MarketplaceResult.failKey("TC_MARKET_RESULT_SALE_NOT_FOUND", "Sale not found.");
                case WRONG_SELLER -> MarketplaceResult.failKey("TC_MARKET_RESULT_SALE_OWNER_ONLY",
                        "Only the seller can remove this sale.");
            };
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to hide marketplace sale: " + ex.getMessage());
            return MarketplaceResult.failKey("TC_MARKET_RESULT_SALE_REMOVE_FAILED",
                    "Could not remove the sale.");
        }
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private long fee(MarketplaceListing listing, MarketZone buyerZone) {
        return fee(listing, buyerZone, listing.price());
    }

    private long fee(MarketplaceListing listing, MarketZone buyerZone, long taxablePrice) {
        int percent = feePercent(listing, buyerZone);
        if (percent <= 0) return 0L;
        long percentFee = ceilPercent(taxablePrice, percent);
        long minimumFee = listing.globalListing() ? settings.minimumGlobalFee : settings.minimumLocalFee;
        return Math.max(percentFee, minimumFee);
    }

    private int feePercent(MarketplaceListing listing, MarketZone buyerZone) {
        if (listing.globalListing()) {
            return settings.defaultGlobalFeePercent;
        }
        if (buyerZone == null) {
            return settings.defaultLocalFeePercent;
        }
        return buyerZone.id().equals(listing.marketZoneId())
                ? buyerZone.feePercent()
                : settings.defaultLocalFeePercent;
    }

    private MarketplaceResult validateCurrency(String currencyIdentifier) {
        String effectiveCurrency = currencyIdentifier == null || currencyIdentifier.isBlank()
                ? defaultCurrencyIdentifier()
                : currencyIdentifier;
        if (effectiveCurrency == null || effectiveCurrency.isBlank()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_WALLET_CURRENCY_MISSING",
                    "Wallet default currency is not available.");
        }
        List<WalletBridge.CurrencyInfo> currencies = wallet.listCurrencies();
        if (currencies.isEmpty()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_WALLET_CURRENCIES_FAILED",
                    "Could not load Wallet currencies.");
        }
        String normalized = effectiveCurrency.trim().toUpperCase(Locale.ROOT);
        boolean exists = currencies.stream().anyMatch(currency -> currency.identifier().equals(normalized));
        return exists
                ? MarketplaceResult.okKey("TC_MARKET_RESULT_CURRENCY_ACCEPTED", "Currency accepted.")
                : MarketplaceResult.failKey("TC_MARKET_RESULT_CURRENCY_UNKNOWN",
                        "Unknown Wallet currency: PH_CURRENCY.", "PH_CURRENCY", effectiveCurrency);
    }

    private String normalizeListingCurrency(String currencyIdentifier) {
        String normalized = currencyIdentifier == null ? "" : currencyIdentifier.trim().toUpperCase(Locale.ROOT);
        String defaultCurrency = defaultCurrencyIdentifier();
        return normalized.equalsIgnoreCase(defaultCurrency) ? "" : normalized;
    }

    private void releaseListing(long listingId, String expectedStatus) {
        try {
            if (!database.transitionListingStatus(listingId, expectedStatus, STATUS_ACTIVE)) {
                Marketplace.logger().error("Failed to release marketplace listing " + listingId
                        + " from status " + expectedStatus + ".");
            }
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to release marketplace listing " + listingId + ": " + ex.getMessage());
        }
    }

    private void logWalletRollbackFailure(String action, long listingId, WalletBridge.WalletCallResult result) {
        if (!result.success()) {
            Marketplace.logger().error("Marketplace " + action + " failed for listing " + listingId + ": "
                    + result.message());
        }
    }

    private MarketplaceResult walletFailure(String detail) {
        Marketplace.logger().warn("Marketplace Wallet operation failed: " + safe(detail));
        return MarketplaceResult.failKey("TC_MARKET_RESULT_WALLET_FAILED",
                "The Wallet transaction failed.");
    }

    public static long partialPrice(long remainingPrice, int remainingAmount, int requestedAmount) {
        if (remainingPrice <= 0L || remainingAmount <= 0 || requestedAmount <= 0) return 0L;
        if (requestedAmount >= remainingAmount) return remainingPrice;
        long quotient = remainingPrice / remainingAmount;
        long remainder = remainingPrice % remainingAmount;
        return quotient * requestedAmount + (remainder * requestedAmount + remainingAmount - 1L) / remainingAmount;
    }

    public static long ceilPercent(long amount, int percent) {
        if (amount <= 0L || percent <= 0) return 0L;
        long quotient = amount / 100L;
        long remainder = amount % 100L;
        return quotient * percent + (remainder * percent + 99L) / 100L;
    }

    private MarketplaceResult fulfillWanted(Player seller, MarketplaceListing listing, int requestedAmount)
            throws SQLException {
        if (seller == null || seller.getDbID() <= 0) return MarketplaceResult.failKey(
                "TC_MARKET_RESULT_PLAYER_ID_MISSING", "Player has no database id.");
        if (listing.sellerDbId() == seller.getDbID()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_OWN_LISTING",
                    "You cannot trade with your own listing.");
        }
        int amount = requestedAmount == Integer.MAX_VALUE ? listing.amount() : requestedAmount;
        if (amount <= 0 || amount > listing.amount()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_AMOUNT_RANGE",
                    "Amount must be between 1 and PH_AVAILABLE.",
                    "PH_AVAILABLE", String.valueOf(listing.amount()));
        }
        Optional<MarketZone> zone = currentZone(seller);
        MarketplaceResult access = validateTradeLocation(listing, zone);
        if (!access.success()) return access;
        if (mail == null || !mail.canReceiveMail(listing.sellerDbId())) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_MAILBOX_FULL",
                    "The requester's OZ Mail mailbox has no free space.");
        }
        MarketplaceItemState itemState = InventoryTransfer.snapshotForSeller(seller, listing.itemName(),
                listing.itemVariant(), amount);
        if (itemState == null) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_ITEMS_NOT_MATCHING",
                    "You do not have enough matching items in one item state.");
        }
        if (!database.transitionListingStatus(listing.id(), STATUS_ACTIVE, STATUS_PENDING_PURCHASE)) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_LISTING_UNAVAILABLE",
                    "Listing is no longer available.");
        }
        boolean inventoryRemoved = false;
        long payout = partialPrice(listing.price(), listing.amount(), amount);
        boolean finalFulfillment = amount == listing.amount();
        long fee = finalFulfillment ? fee(listing, zone.orElse(null), listing.originalPrice()) : 0L;
        try {
            MarketplaceResult removal = InventoryTransfer.removeFromSeller(seller, listing.itemName(),
                    listing.itemVariant(), amount, itemState);
            if (!removal.success()) {
                releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                return removal;
            }
            inventoryRemoved = true;
            WalletBridge.WalletCallResult priceWithdraw = payout > 0
                    ? wallet.withdraw(listing.sellerDbId(), payout, "Marketplace wanted #" + listing.id(),
                            listing.currencyIdentifier(), "OZ - Marketplace")
                    : new WalletBridge.WalletCallResult(true, "No payout.");
            if (!priceWithdraw.success()) {
                restoreWantedSeller(seller, listing, amount, itemState);
                releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                return walletFailure(priceWithdraw.message());
            }
            WalletBridge.WalletCallResult feeWithdraw = fee > 0
                    ? null : new WalletBridge.WalletCallResult(true, "No fee.");
            int feeRecipientDbId = zone.filter(MarketZone::playerOwned).map(MarketZone::ownerDbId).orElse(0);
            FeePayment feePayment = fee > 0
                    ? chargeFee(listing.sellerDbId(), fee, "Marketplace wanted tax #" + listing.id(),
                            listing.currencyIdentifier(), feeRecipientDbId, "wanted:" + listing.id())
                    : FeePayment.none();
            feeWithdraw = feePayment.result();
            if (!feeWithdraw.success()) {
                logWalletRollbackFailure("wanted price refund", listing.id(), wallet.deposit(listing.sellerDbId(),
                        payout, "Marketplace wanted refund #" + listing.id(), listing.currencyIdentifier(),
                        "OZ - Marketplace"));
                restoreWantedSeller(seller, listing, amount, itemState);
                releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                return walletFailure(feeWithdraw.message());
            }
            WalletBridge.WalletCallResult sellerDeposit = payout > 0
                    ? wallet.deposit(seller.getDbID(), payout, "Marketplace wanted fulfillment #" + listing.id(),
                            listing.currencyIdentifier(), "OZ - Marketplace")
                    : new WalletBridge.WalletCallResult(true, "No payout.");
            if (!sellerDeposit.success()) {
                refundRoutedFee(feePayment, "Marketplace wanted tax refund #" + listing.id());
                logWalletRollbackFailure("wanted requester refund", listing.id(), wallet.deposit(listing.sellerDbId(),
                        payout + (feePayment.routedToWorld() ? 0 : fee), "Marketplace wanted refund #" + listing.id(), listing.currencyIdentifier(),
                        "OZ - Marketplace"));
                restoreWantedSeller(seller, listing, amount, itemState);
                releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                return walletFailure(sellerDeposit.message());
            }
            if (fee > 0 && feeRecipientDbId > 0) {
                WalletBridge.WalletCallResult taxDeposit = wallet.deposit(feeRecipientDbId, fee,
                        "Marketplace wanted market tax #" + listing.id(), listing.currencyIdentifier(),
                        "OZ - Marketplace");
                if (!taxDeposit.success()) {
                    rollbackWantedMoney(listing, seller, payout, fee);
                    restoreWantedSeller(seller, listing, amount, itemState);
                    releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                    return walletFailure(taxDeposit.message());
                }
            }
            MailBridge.PluginAttachment attachment = new MailBridge.PluginAttachment(listing.itemName(),
                    listing.itemVariant(), amount, itemState.durability(), itemState.status(), itemState.modifier(),
                    itemState.color());
            Player requester = Server.getPlayerByDbID(listing.sellerDbId());
            I18n translations = I18n.getInstance(Marketplace.name);
            String subject = (requester == null ? translations.get("TC_MARKET_WANTED_MAIL_SUBJECT", "en")
                    : translations.get("TC_MARKET_WANTED_MAIL_SUBJECT", requester))
                    .replace("PH_LISTING", String.valueOf(listing.id()));
            String body = (requester == null ? translations.get("TC_MARKET_WANTED_MAIL_BODY", "en")
                    : translations.get("TC_MARKET_WANTED_MAIL_BODY", requester))
                    .replace("PH_AMOUNT", String.valueOf(amount))
                    .replace("PH_ITEM", MarketplaceItemNames.listingLabel(listing.itemName(), listing.itemVariant()))
                    .replace("PH_SELLER", seller.getName());
            MailBridge.BridgeResult mailResult = mail.sendAttachmentMail(new MailBridge.PluginAttachmentMailRequest(
                    Marketplace.name, listing.sellerDbId(), listing.sellerName(), subject, body,
                    "wanted-" + listing.id() + '-' + (listing.fulfilledAmount() + amount), List.of(attachment)));
            if (!mailResult.success()) {
                refundRoutedFee(feePayment, "Marketplace wanted tax refund #" + listing.id());
                if (fee > 0 && feeRecipientDbId > 0) {
                    logWalletRollbackFailure("wanted tax rollback", listing.id(), wallet.withdraw(feeRecipientDbId, fee,
                            "Marketplace wanted tax rollback #" + listing.id(), listing.currencyIdentifier(),
                            "OZ - Marketplace"));
                }
                rollbackWantedMoney(listing, seller, payout, feePayment.routedToWorld() ? 0 : fee);
                restoreWantedSeller(seller, listing, amount, itemState);
                releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                return MarketplaceResult.failKey("TC_MARKET_RESULT_MAIL_DELIVERY_FAILED",
                        "OZ Mail could not deliver the wanted items (PH_CODE).",
                        "PH_CODE", mailResult.code());
            }
            int remainingAmount = listing.amount() - amount;
            long remainingPrice = Math.max(0L, listing.price() - payout);
            boolean completed = database.completePartialSale(new MarketplaceSale(0L, listing.id(), seller.getDbID(),
                    listing.sellerDbId(), listing.itemName(), listing.itemVariant(), amount, itemState, payout,
                    listing.currencyIdentifier(), fee, payout, zone.map(MarketZone::id).orElse("global"), now()),
                    amount, remainingAmount, remainingPrice, STATUS_PENDING_PURCHASE, STATUS_SOLD);
            if (!completed) {
                Marketplace.logger().error("Wanted fulfillment completed externally but database finalization failed: "
                        + listing.id() + ".");
                return MarketplaceResult.failKey("TC_MARKET_RESULT_ADMIN_REVIEW",
                        "The transaction needs administrator review.");
            }
            return MarketplaceResult.okKey("TC_MARKET_RESULT_WANTED_SOLD",
                    "Sold PH_AMOUNT item(s) to wanted listing #PH_LISTING.",
                    "PH_AMOUNT", String.valueOf(amount),
                    "PH_LISTING", String.valueOf(listing.id()));
        } catch (RuntimeException ex) {
            if (inventoryRemoved) restoreWantedSeller(seller, listing, amount, itemState);
            releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
            throw ex;
        }
    }

    private void rollbackWantedMoney(MarketplaceListing listing, Player seller, long payout, long refundableFee) {
        if (payout > 0) {
            logWalletRollbackFailure("wanted seller payout rollback", listing.id(), wallet.withdraw(seller.getDbID(),
                    payout, "Marketplace wanted payout rollback #" + listing.id(), listing.currencyIdentifier(),
                    "OZ - Marketplace"));
        }
        logWalletRollbackFailure("wanted requester refund", listing.id(), wallet.deposit(listing.sellerDbId(),
                payout + refundableFee, "Marketplace wanted refund #" + listing.id(), listing.currencyIdentifier(),
                "OZ - Marketplace"));
    }

    private FeePayment chargeFee(int payerDbId, long fee, String reason, String currencyIdentifier,
            int playerFeeRecipientDbId, String operationId) {
        if (fee <= 0) return FeePayment.none();
        if (playerFeeRecipientDbId <= 0 && wallet.hasSystemAccountApi()) {
            String correlation = "marketplace:fee:" + operationId + ":" + UUID.randomUUID();
            WalletBridge.WalletTransferCallResult transfer = wallet.transferPlayerToWorldIdempotent(payerDbId, fee,
                    reason, currencyIdentifier, "OZ - Marketplace", correlation);
            return new FeePayment(new WalletBridge.WalletCallResult(transfer.success(), transfer.message()),
                    correlation, true);
        }
        return new FeePayment(wallet.withdraw(payerDbId, fee, reason, currencyIdentifier, "OZ - Marketplace"),
                "", false);
    }

    private void refundRoutedFee(FeePayment payment, String reason) {
        if (!payment.routedToWorld()) return;
        WalletBridge.WalletTransferCallResult reversal = wallet.reverseAccountTransferIdempotent(
                payment.correlationId(), payment.correlationId() + ":refund", reason, "OZ - Marketplace");
        if (!reversal.success()) {
            Marketplace.logger().error("Marketplace world-fee refund failed: " + reversal.message());
        }
    }

    private record FeePayment(WalletBridge.WalletCallResult result, String correlationId, boolean routedToWorld) {
        private static FeePayment none() {
            return new FeePayment(new WalletBridge.WalletCallResult(true, "No fee."), "", false);
        }
    }

    private void restoreWantedSeller(Player seller, MarketplaceListing listing, int amount,
            MarketplaceItemState itemState) {
        MarketplaceResult restore = InventoryTransfer.addToBuyer(seller, listing.itemName(), listing.itemVariant(),
                amount, itemState);
        if (!restore.success()) {
            Marketplace.logger().error("Failed to restore wanted-listing inventory for " + listing.id() + ".");
        }
    }

    private MarketplaceResult validateListingLocation(boolean globalListing, Optional<MarketZone> zone) {
        if (!globalListing && !settings.localMarketplaceEnabled) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_LOCAL_DISABLED",
                    "Local marketplace listings are disabled.");
        }
        if (!globalListing && zone.isEmpty()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_LOCAL_ZONE_REQUIRED",
                    "You must stand in a market zone to create a local listing.");
        }
        if (globalListing && settings.marketZoneOnlyMode && zone.isEmpty()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_GLOBAL_ZONE_REQUIRED",
                    "You must stand in a market zone to create a global listing.");
        }
        if (globalListing && zone.isEmpty() && !settings.globalMarketplaceEnabled) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_GLOBAL_DISABLED",
                    "Global marketplace listings are disabled.");
        }
        if (globalListing && zone.isPresent() && !zone.get().globalTradeAllowed(settings.globalMarketplaceEnabled)) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_GLOBAL_NOT_ALLOWED",
                    "This market zone does not allow global trade.");
        }
        return MarketplaceResult.okKey("TC_MARKET_RESULT_LOCATION_ACCEPTED", "Listing location accepted.");
    }

    private MarketplaceResult validateTradeLocation(MarketplaceListing listing, Optional<MarketZone> zone) {
        if (!listing.globalListing() && zone.isEmpty()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_LOCAL_TRADE_ZONE_REQUIRED",
                    "You must stand in the listing's market zone for this local trade.");
        }
        if (!listing.globalListing() && !zone.get().id().equals(listing.marketZoneId())) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_WRONG_MARKET_ZONE",
                    "This local listing belongs to another market zone.");
        }
        if (listing.globalListing() && settings.marketZoneOnlyMode && zone.isEmpty()) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_GLOBAL_TRADE_ZONE_REQUIRED",
                    "You must stand in a market zone for global trading.");
        }
        if (listing.globalListing() && zone.isPresent()
                && !zone.get().globalTradeAllowed(settings.globalMarketplaceEnabled)) {
            return MarketplaceResult.failKey("TC_MARKET_RESULT_GLOBAL_NOT_ALLOWED",
                    "This market zone does not allow global trade.");
        }
        return MarketplaceResult.okKey("TC_MARKET_RESULT_LOCATION_ACCEPTED", "Listing location accepted.");
    }

    private boolean hasAddPlayerPermission(Player player) {
        return Boolean.TRUE.equals(player.getPermissionValue("area_addplayer", true));
    }

    private int listingCapacity(Player player) {
        double capacity = settings.maxListingsPerPlayer * MarketplacePlayerPreferences.capacityFactor(player);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, Math.floor(capacity)));
    }

    private String permissionToken(Area area, int ownerDbId) {
        String explicit = area.getPlayerPermission(ownerDbId);
        return explicit == null || explicit.isBlank() ? "@default:" + safe(area.getDefaultPermission()) : explicit;
    }

    private boolean eligibleOwnerArea(Area area, MarketZone zone) {
        if (area == null || !area.isValid()) return false;
        String permission = zone.ownerAreaPermission();
        if (permission != null && permission.startsWith("@default:")) {
            return permission.substring("@default:".length()).equals(safe(area.getDefaultPermission()))
                    && (area.getPlayerPermission(zone.ownerDbId()) == null
                            || area.getPlayerPermission(zone.ownerDbId()).isBlank());
        }
        return permission != null && permission.equals(area.getPlayerPermission(zone.ownerDbId()));
    }

    private Area nearestEligibleArea(MarketZone zone, Area[] areas, Set<Long> usedAreas) {
        if (areas == null) return null;
        Area best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Area area : areas) {
            if (area == null || area.getID() <= 0L || usedAreas.contains(area.getID())) continue;
            MarketZone candidate = new MarketZone("", "", area.getID(), area.getStartChunkPosition().x,
                    area.getEndChunkPosition().x, area.getStartChunkPosition().y, area.getEndChunkPosition().y,
                    area.getStartChunkPosition().z, area.getEndChunkPosition().z, 0, MarketZone.GLOBAL_DEFAULT, 0L,
                    zone.ownerDbId(), zone.ownerName(), zone.ownerAreaPermission());
            if (!eligibleOwnerArea(area, candidate)) continue;
            long distance = squaredDistance(zone, candidate);
            if (distance < bestDistance || (distance == bestDistance && best != null && area.getID() < best.getID())) {
                best = area;
                bestDistance = distance;
            }
        }
        return best;
    }

    private MarketZone areaZone(String id, String name, Area area, int feePercent, int globalMode, int ownerDbId,
            String ownerName, String ownerPermission, long createdAt) {
        Vector3i start = area.getStartChunkPosition();
        Vector3i end = area.getEndChunkPosition();
        return new MarketZone(id, name == null || name.isBlank() ? id : name, area.getID(),
                Math.min(start.x, end.x), Math.max(start.x, end.x),
                Math.min(start.y, end.y), Math.max(start.y, end.y),
                Math.min(start.z, end.z), Math.max(start.z, end.z), Math.max(0, Math.min(100, feePercent)),
                MarketZone.normalizeGlobalTradeMode(globalMode), createdAt, ownerDbId, safe(ownerName),
                safe(ownerPermission));
    }

    private long squaredDistance(MarketZone first, MarketZone second) {
        long dx = ((long) first.minChunkX() + first.maxChunkX()) - ((long) second.minChunkX() + second.maxChunkX());
        long dy = ((long) first.minChunkY() + first.maxChunkY()) - ((long) second.minChunkY() + second.maxChunkY());
        long dz = ((long) first.minChunkZ() + first.maxChunkZ()) - ((long) second.minChunkZ() + second.maxChunkZ());
        return dx * dx + dy * dy + dz * dz;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
