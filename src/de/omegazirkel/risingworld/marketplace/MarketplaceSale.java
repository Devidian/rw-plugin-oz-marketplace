package de.omegazirkel.risingworld.marketplace;

public record MarketplaceSale(
        long id,
        long listingId,
        int sellerDbId,
        int buyerDbId,
        String itemName,
        int itemVariant,
        int amount,
        long price,
        String currencyIdentifier,
        long fee,
        long sellerPayout,
        String marketZoneId,
        long soldAt) {
}
