package de.omegazirkel.risingworld.marketplace;

public record MarketplaceListing(
        long id,
        int sellerDbId,
        String sellerName,
        String itemName,
        int itemVariant,
        int amount,
        long price,
        String currencyIdentifier,
        String marketZoneId,
        boolean globalListing,
        long createdAt,
        String status) {
}
