package de.omegazirkel.risingworld.marketplace.exports;

import java.util.List;

public record MarketplaceCriersExportResponse(int schemaVersion, List<MarketplaceCrierExport> criers) { }
