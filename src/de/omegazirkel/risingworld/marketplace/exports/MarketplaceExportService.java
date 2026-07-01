package de.omegazirkel.risingworld.marketplace.exports;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MarketplaceExportService {
    private static final int SCHEMA_VERSION = 1;

    private final Connection connection;

    public MarketplaceExportService(Connection connection) {
        this.connection = connection;
    }

    public MarketplaceZonesExportResponse exportZones(Long lastChange) throws SQLException {
        long cursor = lastChange == null ? -1L : lastChange.longValue();
        List<MarketplaceZoneExport> zones = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, name, area_id, created_at
                FROM marketplace_zones
                WHERE created_at > ?
                ORDER BY created_at DESC, id DESC;
                """)) {
            statement.setLong(1, cursor);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    zones.add(new MarketplaceZoneExport(
                            result.getString("id"),
                            result.getString("name"),
                            result.getLong("area_id"),
                            result.getLong("created_at")));
                }
            }
        }
        return new MarketplaceZonesExportResponse(SCHEMA_VERSION, zones);
    }

    public MarketplaceOffersExportResponse exportOffers(long areaId, Long lastChange) throws SQLException {
        Optional<String> zoneId = zoneIdForArea(areaId);
        if (zoneId.isEmpty()) {
            return new MarketplaceOffersExportResponse(SCHEMA_VERSION, areaId, List.of());
        }
        long cursor = lastChange == null ? -1L : lastChange.longValue();
        List<MarketplaceOfferExport> offers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, seller_name, item_name, item_variant, amount, price,
                       currency_identifier, created_at
                FROM marketplace_listings
                WHERE status = 'ACTIVE' AND market_zone_id = ? AND created_at > ?
                ORDER BY created_at DESC, id DESC
                LIMIT 30;
                """)) {
            statement.setString(1, zoneId.get());
            statement.setLong(2, cursor);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    offers.add(new MarketplaceOfferExport(
                            result.getLong("id"),
                            result.getString("item_name"),
                            result.getInt("item_variant"),
                            result.getInt("amount"),
                            result.getLong("price"),
                            result.getString("currency_identifier"),
                            result.getString("seller_name"),
                            result.getLong("created_at")));
                }
            }
        }
        return new MarketplaceOffersExportResponse(SCHEMA_VERSION, areaId, offers);
    }

    private Optional<String> zoneIdForArea(long areaId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM marketplace_zones WHERE area_id = ? LIMIT 1;
                """)) {
            statement.setLong(1, areaId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getString("id")) : Optional.empty();
            }
        }
    }
}
