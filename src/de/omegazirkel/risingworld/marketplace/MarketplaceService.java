package de.omegazirkel.risingworld.marketplace;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.tools.I18n;
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
    private PluginSettings settings;

    public MarketplaceService(MarketplaceDatabase database, WalletBridge wallet, PluginSettings settings) {
        this.database = database;
        this.wallet = wallet;
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
        return createZone(id, name, areaId, new Vector3i(0, 0, 0), 0, feePercent, globalTradeMode);
    }

    public MarketplaceResult createZone(String id, String name, long areaId, Vector3i center, int radius, int feePercent,
            int globalTradeMode) {
        if (id == null || id.isBlank()) {
            return MarketplaceResult.fail("Zone id is required.");
        }
        if (radius < 0) {
            return MarketplaceResult.fail("Zone radius must be at least 0 chunks.");
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
                now());
        try {
            database.upsertZone(zone);
            return MarketplaceResult.ok("Market zone saved: " + zone.id());
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to save market zone: " + ex.getMessage());
            return MarketplaceResult.fail("Could not save market zone.");
        }
    }

    public MarketplaceResult updateZone(MarketZone zone) {
        if (zone == null || zone.id() == null || zone.id().isBlank()) {
            return MarketplaceResult.fail("Zone id is required.");
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
                zone.createdAt());
        try {
            database.upsertZone(updated);
            return MarketplaceResult.ok("Market zone saved: " + updated.id());
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to save market zone: " + ex.getMessage());
            return MarketplaceResult.fail("Could not save market zone.");
        }
    }

    public MarketplaceResult deleteZone(String id) {
        if (id == null || id.isBlank()) {
            return MarketplaceResult.fail("Zone id is required.");
        }
        try {
            MarketplaceDatabase.ZoneDeleteResult result = database.deleteZoneAndPromoteListings(id.trim().toLowerCase());
            if (!result.deleted()) {
                return MarketplaceResult.fail("Market zone not found: " + id);
            }
            return MarketplaceResult.ok("Market zone deleted: " + id
                    + ". Local listings promoted to global: " + result.promotedListings() + ".");
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to delete market zone: " + ex.getMessage());
            return MarketplaceResult.fail("Could not delete market zone.");
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

    public MarketplaceResult createListing(Player seller, String itemName, int itemVariant, int amount, long price,
            String currencyIdentifier, boolean globalListing) {
        return createListing(seller, itemName, itemVariant, amount, price, currencyIdentifier, globalListing, null);
    }

    public MarketplaceResult createListing(Player seller, String itemName, int itemVariant, int amount, long price,
            String currencyIdentifier, boolean globalListing, MarketplaceItemState requestedItemState) {
        if (!walletAvailable()) {
            return MarketplaceResult.fail("OZ - Wallet is required before Marketplace can create listings.");
        }
        if (seller == null || seller.getDbID() <= 0) {
            return MarketplaceResult.fail("Seller has no database id.");
        }
        if (price <= 0 || amount <= 0) {
            return MarketplaceResult.fail("Price and amount must be greater than 0.");
        }
        String listingCurrency = normalizeListingCurrency(currencyIdentifier);
        MarketplaceResult currencyValidation = validateCurrency(listingCurrency);
        if (!currencyValidation.success()) {
            return currencyValidation;
        }
        if (!globalListing && !settings.localMarketplaceEnabled) {
            return MarketplaceResult.fail("Local marketplace listings are disabled.");
        }
        boolean inventoryRemoved = false;
        MarketplaceItemState itemState = null;
        try {
            if (database.activeListingCount(seller.getDbID()) >= settings.maxListingsPerPlayer) {
                return MarketplaceResult.fail("You reached the active listing limit.");
            }
            Optional<MarketZone> zone = currentZone(seller);
            if (!globalListing && zone.isEmpty()) {
                return MarketplaceResult.fail("You must stand in a market zone to create a local listing.");
            }
            if (globalListing && settings.marketZoneOnlyMode && zone.isEmpty()) {
                return MarketplaceResult.fail("You must stand in a market zone to create a global listing.");
            }
            if (globalListing && zone.isEmpty() && !settings.globalMarketplaceEnabled) {
                return MarketplaceResult.fail("Global marketplace listings are disabled.");
            }
            if (globalListing && zone.isPresent() && !zone.get().globalTradeAllowed(settings.globalMarketplaceEnabled)) {
                return MarketplaceResult.fail("This market zone does not allow global trade.");
            }
            itemState = requestedItemState == null
                    ? InventoryTransfer.snapshotForSeller(seller, itemName, itemVariant, amount)
                    : requestedItemState;
            if (itemState == null) return MarketplaceResult.fail("You do not have enough matching items in one item state.");
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
                return MarketplaceResult.fail("Could not create listing.");
            }
            return MarketplaceResult.ok("Listing #" + id + " created.");
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to create listing: " + ex.getMessage());
            if (inventoryRemoved && itemState != null) {
                MarketplaceResult returned = InventoryTransfer.addToBuyer(seller, itemName, itemVariant, amount, itemState);
                if (!returned.success()) {
                    Marketplace.logger().error("Failed to return inventory after marketplace listing creation failed.");
                }
            }
            return MarketplaceResult.fail("Could not create listing.");
        }
    }

    public MarketplaceResult buy(Player buyer, long listingId) {
        if (!walletAvailable()) {
            return MarketplaceResult.fail("OZ - Wallet is required before Marketplace purchases work.");
        }
        boolean listingReserved = false;
        boolean externalTransferCompleted = false;
        try {
            Optional<MarketplaceListing> found = database.findActiveListing(listingId);
            if (found.isEmpty()) {
                return MarketplaceResult.fail("Listing not found.");
            }
            MarketplaceListing listing = found.get();
            if (listing.sellerDbId() == buyer.getDbID()) {
                return MarketplaceResult.fail("You cannot buy your own listing.");
            }
            if (!listing.globalListing() && !settings.localMarketplaceEnabled) {
                return MarketplaceResult.fail("Local marketplace listings are disabled.");
            }
            Optional<MarketZone> zone = currentZone(buyer);
            if (!listing.globalListing() && zone.isEmpty()) {
                return MarketplaceResult.fail("You must stand in a market zone to buy local listings.");
            }
            if (listing.globalListing() && settings.marketZoneOnlyMode && zone.isEmpty()) {
                return MarketplaceResult.fail("You must stand in a market zone to buy global listings.");
            }
            if (listing.globalListing() && zone.isEmpty() && !settings.globalMarketplaceEnabled) {
                return MarketplaceResult.fail("Global marketplace listings are disabled.");
            }
            if (!listing.globalListing() && !zone.get().id().equals(listing.marketZoneId())) {
                return MarketplaceResult.fail("This local listing belongs to another market zone.");
            }
            if (listing.globalListing() && zone.isPresent()
                    && !zone.get().globalTradeAllowed(settings.globalMarketplaceEnabled)) {
                return MarketplaceResult.fail("This market zone does not allow global trade.");
            }
            if (!database.transitionListingStatus(listing.id(), STATUS_ACTIVE, STATUS_PENDING_PURCHASE)) {
                return MarketplaceResult.fail("Listing is no longer available.");
            }
            listingReserved = true;

            WalletBridge.WalletCallResult withdraw = wallet.withdraw(buyer.getDbID(), listing.price(),
                    "Marketplace purchase #" + listing.id(), listing.currencyIdentifier(), "OZ - Marketplace");
            if (!withdraw.success()) {
                releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                return MarketplaceResult.fail(withdraw.message());
            }

            long fee = fee(listing, zone.orElse(null));
            long buyerCharge = listing.price() + fee;
            long sellerPayout = listing.price();
            WalletBridge.WalletCallResult feeWithdraw = fee > 0
                    ? wallet.withdraw(buyer.getDbID(), fee, "Marketplace fee #" + listing.id(),
                            listing.currencyIdentifier(), "OZ - Marketplace")
                    : new WalletBridge.WalletCallResult(true, "No fee.");
            if (!feeWithdraw.success()) {
                WalletBridge.WalletCallResult purchaseRefund = wallet.deposit(buyer.getDbID(), listing.price(),
                        "Marketplace purchase refund #" + listing.id(), listing.currencyIdentifier(), "OZ - Marketplace");
                logWalletRollbackFailure("buyer purchase refund", listing.id(), purchaseRefund);
                releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                return MarketplaceResult.fail(feeWithdraw.message());
            }
            if (sellerPayout > 0) {
                WalletBridge.WalletCallResult deposit = wallet.deposit(listing.sellerDbId(), sellerPayout,
                        "Marketplace sale #" + listing.id(), listing.currencyIdentifier(), "OZ - Marketplace");
                if (!deposit.success()) {
                    WalletBridge.WalletCallResult refund = wallet.deposit(buyer.getDbID(), buyerCharge,
                            "Marketplace refund #" + listing.id(), listing.currencyIdentifier(), "OZ - Marketplace");
                    logWalletRollbackFailure("buyer refund", listing.id(), refund);
                    releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                    return MarketplaceResult.fail(deposit.message());
                }
            }
            MarketplaceResult addItem = InventoryTransfer.addToBuyer(buyer, listing.itemName(), listing.itemVariant(),
                    listing.amount(), listing.itemState());
            if (!addItem.success()) {
                if (sellerPayout > 0) {
                    WalletBridge.WalletCallResult payoutRollback = wallet.withdraw(listing.sellerDbId(), sellerPayout,
                            "Marketplace payout rollback #" + listing.id(),
                            listing.currencyIdentifier(), "OZ - Marketplace");
                    logWalletRollbackFailure("seller payout rollback", listing.id(), payoutRollback);
                }
                WalletBridge.WalletCallResult refund = wallet.deposit(buyer.getDbID(), buyerCharge,
                        "Marketplace refund #" + listing.id(), listing.currencyIdentifier(), "OZ - Marketplace");
                logWalletRollbackFailure("buyer refund", listing.id(), refund);
                releaseListing(listing.id(), STATUS_PENDING_PURCHASE);
                return addItem;
            }
            externalTransferCompleted = true;
            boolean completed = database.completeSale(new MarketplaceSale(0L, listing.id(), listing.sellerDbId(), buyer.getDbID(),
                    listing.itemName(), listing.itemVariant(), listing.amount(), listing.itemState(), listing.price(),
                    listing.currencyIdentifier(), fee, sellerPayout, zone.map(MarketZone::id).orElse("global"), now()),
                    STATUS_PENDING_PURCHASE, STATUS_SOLD);
            if (!completed) {
                Marketplace.logger().error("Marketplace purchase completed externally but listing was already finalized: "
                        + listing.id());
                return MarketplaceResult.fail("Purchase needs admin review; listing finalization failed.");
            }
            notifyOnlineSeller(listing, buyer);
            return MarketplaceResult.ok("Purchased listing #" + listing.id() + ".");
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to buy listing: " + ex.getMessage());
            if (listingReserved && !externalTransferCompleted) {
                releaseListing(listingId, STATUS_PENDING_PURCHASE);
            }
            return MarketplaceResult.fail("Could not buy listing.");
        }
    }

    private void notifyOnlineSeller(MarketplaceListing listing, Player buyer) {
        Player seller = Server.getPlayerByDbID(listing.sellerDbId());
        if (seller == null) {
            return;
        }
        String message = I18n.getInstance(Marketplace.name).get("TC_MARKET_SELLER_SALE_NOTIFICATION", seller)
                .replace("PH_AMOUNT", String.valueOf(listing.amount()))
                .replace("PH_ITEM", MarketplaceItemNames.listingLabel(listing.itemName(), listing.itemVariant()))
                .replace("PH_PRICE", String.valueOf(listing.price()))
                .replace("PH_CURRENCY", listing.currencyIdentifier())
                .replace("PH_BUYER", buyer.getName());
        seller.sendTextMessage(message);
    }

    public MarketplaceResult cancel(Player seller, long listingId) {
        boolean listingReserved = false;
        try {
            Optional<MarketplaceListing> found = database.findActiveListing(listingId);
            if (found.isEmpty()) {
                return MarketplaceResult.fail("Listing not found.");
            }
            MarketplaceListing listing = found.get();
            if (listing.sellerDbId() != seller.getDbID()) {
                return MarketplaceResult.fail("Only the seller can cancel this listing in v1.");
            }
            if (!database.transitionListingStatus(listing.id(), STATUS_ACTIVE, STATUS_PENDING_CANCEL)) {
                return MarketplaceResult.fail("Listing is no longer available.");
            }
            listingReserved = true;
            MarketplaceResult returned = InventoryTransfer.addToBuyer(seller, listing.itemName(), listing.itemVariant(),
                    listing.amount(), listing.itemState());
            if (!returned.success()) {
                releaseListing(listing.id(), STATUS_PENDING_CANCEL);
                return returned;
            }
            if (!database.transitionListingStatus(listing.id(), STATUS_PENDING_CANCEL, STATUS_CANCELLED)) {
                Marketplace.logger().error("Marketplace cancellation returned inventory but failed to finalize listing: "
                        + listing.id());
                return MarketplaceResult.fail("Cancellation needs admin review; listing finalization failed.");
            }
            return MarketplaceResult.ok("Listing #" + listing.id() + " cancelled.");
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to cancel listing: " + ex.getMessage());
            if (listingReserved) {
                releaseListing(listingId, STATUS_PENDING_CANCEL);
            }
            return MarketplaceResult.fail("Could not cancel listing.");
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
            return MarketplaceResult.fail("Seller has no database id.");
        }
        if (saleId <= 0L) {
            return MarketplaceResult.fail("Sale not found.");
        }
        try {
            return switch (database.hideSaleForSeller(saleId, seller.getDbID(), now())) {
                case SUCCESS -> MarketplaceResult.ok("Sale removed from your history.");
                case NOT_FOUND -> MarketplaceResult.fail("Sale not found.");
                case WRONG_SELLER -> MarketplaceResult.fail("Only the seller can remove this sale.");
            };
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to hide marketplace sale: " + ex.getMessage());
            return MarketplaceResult.fail("Could not remove sale.");
        }
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private long fee(MarketplaceListing listing, MarketZone buyerZone) {
        int percent = feePercent(listing, buyerZone);
        long percentFee = listing.price() * percent / 100L;
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
            return MarketplaceResult.fail("Wallet default currency is not available.");
        }
        List<WalletBridge.CurrencyInfo> currencies = wallet.listCurrencies();
        if (currencies.isEmpty()) {
            return MarketplaceResult.fail("Could not load Wallet currencies.");
        }
        String normalized = effectiveCurrency.trim().toUpperCase(Locale.ROOT);
        boolean exists = currencies.stream().anyMatch(currency -> currency.identifier().equals(normalized));
        return exists
                ? MarketplaceResult.ok("Currency accepted.")
                : MarketplaceResult.fail("Unknown Wallet currency: " + effectiveCurrency + ".");
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
}
