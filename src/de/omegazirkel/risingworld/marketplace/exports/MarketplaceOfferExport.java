package de.omegazirkel.risingworld.marketplace.exports;

public record MarketplaceOfferExport(
        long id,
        String itemName,
        int itemVariant,
        int amount,
        long price,
        String currency,
        String sellerName,
        long createdAt) {
}
