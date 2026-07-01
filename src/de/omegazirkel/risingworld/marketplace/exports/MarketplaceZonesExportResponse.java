package de.omegazirkel.risingworld.marketplace.exports;

import java.util.List;

public record MarketplaceZonesExportResponse(
        int schemaVersion,
        List<MarketplaceZoneExport> zones) {

    public MarketplaceZonesExportResponse {
        zones = List.copyOf(zones);
    }
}
