package de.omegazirkel.risingworld.marketplace;

public record MarketplaceListing(
        long id,
        int sellerDbId,
        String sellerName,
        String itemName,
        int itemVariant,
        int amount,
        MarketplaceItemState itemState,
        long price,
        String currencyIdentifier,
        String marketZoneId,
        boolean globalListing,
        long createdAt,
        String status,
        String listingType,
        int originalAmount,
        int fulfilledAmount,
        long originalPrice) {
    public static final String TYPE_OFFER = "OFFER";
    public static final String TYPE_WANTED = "WANTED";

    public MarketplaceListing(long id, int sellerDbId, String sellerName, String itemName, int itemVariant, int amount,
            MarketplaceItemState itemState, long price, String currencyIdentifier, String marketZoneId,
            boolean globalListing, long createdAt, String status) {
        this(id, sellerDbId, sellerName, itemName, itemVariant, amount, itemState, price, currencyIdentifier,
                marketZoneId, globalListing, createdAt, status, TYPE_OFFER, amount, 0, price);
    }

    public boolean wanted() {
        return TYPE_WANTED.equalsIgnoreCase(listingType);
    }

    public boolean offer() {
        return !wanted();
    }
}
