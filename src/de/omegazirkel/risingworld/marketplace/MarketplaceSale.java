package de.omegazirkel.risingworld.marketplace;

public record MarketplaceSale(
        long id,
        long listingId,
        int sellerDbId,
        int buyerDbId,
        String itemName,
        int itemVariant,
        int amount,
        MarketplaceItemState itemState,
        long price,
        String currencyIdentifier,
        long fee,
        long sellerPayout,
        String marketZoneId,
        long soldAt) {
}
