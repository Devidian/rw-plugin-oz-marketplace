package de.omegazirkel.risingworld.marketplace;

import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.Test;

public class MarketplaceDatabaseItemColorTest {
    @Test
    public void constructionColorRoundTripsThroughListingAndSalePersistence() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MarketplaceDatabase database = new MarketplaceDatabase(connection);
            MarketplaceItemState state = new MarketplaceItemState(80, (short) 2, "Quality", 0x12AB34);
            MarketplaceListing listing = new MarketplaceListing(0L, 11, "Seller", "block", 7, 3, state,
                    25L, "OZC", "global", true, 100L, "ACTIVE");

            long listingId = database.createListing(listing);
            assertEquals(0x12AB34, database.findActiveListing(listingId).orElseThrow().itemState().color());

            database.recordSale(new MarketplaceSale(0L, listingId, 11, 22, "block", 7, 3, state,
                    25L, "OZC", 1L, 25L, "global", 200L));
            assertEquals(0x12AB34, database.listSalesForSeller(11, 10).get(0).itemState().color());
        }
    }

    @Test
    public void legacyItemStateConstructorDefaultsToUncolored() {
        assertEquals(0, new MarketplaceItemState(80, (short) 2, "Quality").color());
    }

    @Test
    public void schemaV3RowsMigrateToDefaultColor() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE marketplace_listings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        seller_db_id INTEGER NOT NULL,
                        seller_name TEXT NOT NULL,
                        item_name TEXT NOT NULL,
                        item_variant INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        item_durability INTEGER NOT NULL DEFAULT 0,
                        item_status INTEGER NOT NULL DEFAULT 0,
                        item_modifier TEXT NOT NULL DEFAULT '',
                        price BIGINT NOT NULL,
                        currency_identifier TEXT NOT NULL,
                        market_zone_id TEXT,
                        global_listing INTEGER NOT NULL DEFAULT 0,
                        created_at BIGINT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'ACTIVE'
                    )
                    """);
            statement.execute("""
                    INSERT INTO marketplace_listings(
                        seller_db_id, seller_name, item_name, item_variant, amount, price, currency_identifier,
                        market_zone_id, global_listing, created_at, status)
                    VALUES (11, 'Seller', 'block', 7, 3, 25, 'OZC', 'global', 1, 100, 'ACTIVE')
                    """);

            MarketplaceDatabase database = new MarketplaceDatabase(connection);
            MarketplaceListing migrated = database.findActiveListing(1L).orElseThrow();

            assertEquals(0, migrated.itemState().color());
            try (var result = statement.executeQuery("PRAGMA user_version")) {
                assertEquals(4, result.getInt(1));
            }
        }
    }
}
