package de.omegazirkel.risingworld.marketplace.exports;

import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.junit.Test;

public class MarketplaceExportServiceTest {

    @Test
    public void exportsZones() throws Exception {
        try (Connection connection = database()) {
            seed(connection);

            MarketplaceZonesExportResponse response = new MarketplaceExportService(connection).exportZones(null);

            assertEquals(1, response.schemaVersion());
            assertEquals(2, response.zones().size());
            assertEquals("zone-2", response.zones().get(0).id());
            assertEquals(43L, response.zones().get(0).areaId());
            assertEquals("zone-1", response.zones().get(1).id());
        }
    }

    @Test
    public void filtersZonesByLastChange() throws Exception {
        try (Connection connection = database()) {
            seed(connection);

            MarketplaceZonesExportResponse response = new MarketplaceExportService(connection).exportZones(1000L);

            assertEquals(1, response.zones().size());
            assertEquals("zone-2", response.zones().get(0).id());
        }
    }

    @Test
    public void exportsActiveOffersForArea() throws Exception {
        try (Connection connection = database()) {
            seed(connection);

            MarketplaceOffersExportResponse response = new MarketplaceExportService(connection).exportOffers(42L, null);

            assertEquals(1, response.schemaVersion());
            assertEquals(42L, response.areaId());
            assertEquals(1, response.offers().size());
            assertEquals(1L, response.offers().get(0).id());
            assertEquals("Stone", response.offers().get(0).itemName());
            assertEquals(64, response.offers().get(0).amount());
            assertEquals(12L, response.offers().get(0).price());
            assertEquals("Alice", response.offers().get(0).sellerName());
        }
    }

    @Test
    public void filtersOffersByLastChange() throws Exception {
        try (Connection connection = database()) {
            seed(connection);

            MarketplaceOffersExportResponse response = new MarketplaceExportService(connection).exportOffers(42L, 2000L);

            assertEquals(0, response.offers().size());
        }
    }

    private static Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE marketplace_zones (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        area_id BIGINT NOT NULL DEFAULT 0,
                        created_at BIGINT NOT NULL
                    );
                    """);
            statement.execute("""
                    CREATE TABLE marketplace_listings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        seller_db_id INTEGER NOT NULL,
                        seller_name TEXT NOT NULL,
                        item_name TEXT NOT NULL,
                        item_variant INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        price BIGINT NOT NULL,
                        currency_identifier TEXT NOT NULL,
                        market_zone_id TEXT,
                        global_listing INTEGER NOT NULL DEFAULT 0,
                        created_at BIGINT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'ACTIVE'
                    );
                    """);
        }
        return connection;
    }

    private static void seed(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO marketplace_zones (id, name, area_id, created_at)
                VALUES ('zone-1', 'Spawn Market', 42, 1000),
                       ('zone-2', 'Harbor Market', 43, 3000);
                """)) {
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO marketplace_listings
                (id, seller_db_id, seller_name, item_name, item_variant, amount, price, currency_identifier,
                 market_zone_id, global_listing, created_at, status)
                VALUES (1, 7, 'Alice', 'Stone', 0, 64, 12, 'coins', 'zone-1', 0, 2000, 'ACTIVE'),
                       (2, 8, 'Carol', 'Iron', 0, 8, 30, 'coins', 'zone-2', 0, 4000, 'ACTIVE');
                """)) {
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO marketplace_listings
                (seller_db_id, seller_name, item_name, item_variant, amount, price, currency_identifier,
                 market_zone_id, global_listing, created_at, status)
                VALUES (9, 'Bob', 'Wood', 1, 16, 5, 'coins', 'zone-1', 0, 3000, 'SOLD');
                """)) {
            statement.executeUpdate();
        }
    }
}
