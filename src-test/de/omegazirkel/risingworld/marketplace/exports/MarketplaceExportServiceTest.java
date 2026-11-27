package de.omegazirkel.risingworld.marketplace.exports;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.google.gson.Gson;
import de.omegazirkel.risingworld.tools.bridge.WalletBridge.SystemBalanceInfo;

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

    @Test
    public void exportsAllActiveOffersInsteadOfDiscardingOlderRows() throws Exception {
        try (Connection connection = database()) {
            seed(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO marketplace_listings
                    (seller_db_id, seller_name, item_name, item_variant, amount, price, currency_identifier,
                     market_zone_id, global_listing, created_at, status)
                    VALUES (?, 'Seller', 'Stone', 0, 1, 1, 'coins', 'zone-1', 0, ?, 'ACTIVE');
                    """)) {
                for (int index = 0; index < 31; index++) {
                    statement.setInt(1, 100 + index);
                    statement.setLong(2, 5_000L + index);
                    statement.addBatch();
                }
                statement.executeBatch();
            }

            MarketplaceOffersExportResponse response = new MarketplaceExportService(connection).exportOffers(42L, null);

            assertEquals(32, response.offers().size());
        }
    }

    @Test
    public void crierExportPreservesPayloadWithBoundedListingQueries() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO marketplace_criers VALUES
                        (30, 'shared', 'market'), (10, 'shared', 'Market'),
                        (20, 'empty', 'Alley'), (40, 'no-location', 'Absent');
                    """);
            statement.executeUpdate("""
                    INSERT INTO marketplace_crier_locations VALUES ('shared', 1.5, 2, 3), ('empty', 4, 5, 6);
                    """);
            statement.executeUpdate("""
                    INSERT INTO marketplace_listings
                    (id, seller_db_id, seller_name, item_name, item_variant, amount, price, currency_identifier,
                     market_zone_id, global_listing, created_at, status)
                    VALUES (1, 7, 'Alice', 'Stone', 0, 3, 12, 'OZC', 'shared', 0, 1000, 'ACTIVE'),
                           (2, 8, 'Bob', 'Wood', 1, 2, 8, 'coins', 'shared', 1, 2000, 'ACTIVE'),
                           (3, 9, 'Clara', 'Iron', 2, 1, 4, 'OZC', 'shared', 0, 2000, 'ACTIVE'),
                           (4, 9, 'Clara', 'Hidden', 0, 1, 4, 'OZC', 'shared', 0, 4000, 'PENDING_PURCHASE'),
                           (5, 9, 'Clara', 'Sold', 0, 1, 4, 'OZC', 'shared', 0, 4000, 'SOLD'),
                           (6, 9, 'Clara', 'Other', 0, 1, 4, 'OZC', 'no-location', 0, 4000, 'ACTIVE');
                    """);
            var queries = new AtomicInteger();
            var accounts = new ArrayList<String>();
            var exporter = new MarketplaceExportService(countQueries(connection, queries), account -> {
                accounts.add(account);
                return account.equals("market-crier::10")
                        ? List.of(new SystemBalanceInfo("OZC", 19), new SystemBalanceInfo("coins", 2))
                        : List.of();
            });

            var actual = exporter.exportCriers();
            var offers = List.of(
                    new MarketplaceOfferExport(3, "Iron", 2, 1, 4, "OZC", "Clara", 2000),
                    new MarketplaceOfferExport(2, "Wood", 1, 2, 8, "coins", "Bob", 2000),
                    new MarketplaceOfferExport(1, "Stone", 0, 3, 12, "OZC", "Alice", 1000));
            var expected = new MarketplaceCriersExportResponse(1, List.of(
                    new MarketplaceCrierExport(20, "empty", "Alley", 4, 5, 6, List.of(), List.of()),
                    new MarketplaceCrierExport(10, "shared", "Market", 1.5f, 2, 3,
                            List.of(new MarketplaceBalanceExport("OZC", 19), new MarketplaceBalanceExport("coins", 2)),
                            offers),
                    new MarketplaceCrierExport(30, "shared", "market", 1.5f, 2, 3, List.of(), offers)));
            assertEquals(new Gson().toJson(expected), new Gson().toJson(actual));
            assertEquals(List.of("market-crier::20", "market-crier::10", "market-crier::30"), accounts);
            actual.criers().get(1).offers().clear();
            assertEquals(offers, actual.criers().get(2).offers());
            assertEquals(2, queries.get());
        }
    }

    @Test
    public void crierExportUsesOneQueryWhenEmptyAndTwoForOneCrier() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            var queries = new AtomicInteger();
            var exporter = new MarketplaceExportService(countQueries(connection, queries));
            assertEquals(List.of(), exporter.exportCriers().criers());
            assertEquals(1, queries.getAndSet(0));

            statement.executeUpdate("INSERT INTO marketplace_criers VALUES (10, 'empty', 'Empty')");
            statement.executeUpdate("INSERT INTO marketplace_crier_locations VALUES ('empty', 0, 0, 0)");
            var response = exporter.exportCriers();
            assertEquals(List.of(new MarketplaceCrierExport(10, "empty", "Empty", 0, 0, 0,
                    List.of(), List.of())), response.criers());
            assertEquals(2, queries.get());
        }
    }

    private static Connection countQueries(Connection connection, AtomicInteger queries) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class }, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) queries.incrementAndGet();
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });
    }

    private static Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            // Also exercise duplicate endpoint IDs from legacy or manually repaired data.
            statement.execute("CREATE TABLE marketplace_criers (npc_id BIGINT PRIMARY KEY, endpoint_id TEXT, name TEXT)");
            statement.execute("""
                    CREATE TABLE marketplace_crier_locations (endpoint_id TEXT PRIMARY KEY, x REAL, y REAL, z REAL)
                    """);
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
