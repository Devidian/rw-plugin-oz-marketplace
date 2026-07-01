package de.omegazirkel.risingworld.marketplace.exports;

import java.util.List;

public record MarketplaceOffersExportResponse(
        int schemaVersion,
        long areaId,
        List<MarketplaceOfferExport> offers) {

    public MarketplaceOffersExportResponse {
        offers = List.copyOf(offers);
    }
}
