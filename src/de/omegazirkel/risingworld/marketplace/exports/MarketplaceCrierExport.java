package de.omegazirkel.risingworld.marketplace.exports;

import java.util.List;

public record MarketplaceCrierExport(long npcId, String endpointId, String name, float x, float y, float z,
        List<MarketplaceBalanceExport> balances, List<MarketplaceOfferExport> offers) { }
