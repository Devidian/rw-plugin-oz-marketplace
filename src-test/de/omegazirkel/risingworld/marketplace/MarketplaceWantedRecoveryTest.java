package de.omegazirkel.risingworld.marketplace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.Test;

public class MarketplaceWantedRecoveryTest {
    @Test
    public void pendingSettlementSurvivesReopenAndClearsOnlyWhenListingLeavesPending() throws Exception {
        var file = Files.createTempFile("marketplace-wanted-", ".sqlite");
        var state = new MarketplaceItemState(21, (short) 2, "Sharp", 27);
        try {
            long listing;
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                var database = new MarketplaceDatabase(connection);
                listing = database.createListing(new MarketplaceListing(0, 7, "Buyer", "axe", 1, 1,
                        MarketplaceItemState.NEUTRAL, 50, "COIN", "global", true, 1, "ACTIVE",
                        MarketplaceListing.TYPE_WANTED, 1, 0, 50));
                try {
                    database.beginWantedSettlement(listing, 14, 1, state, 50, 5, "COIN", "mail", "wallet");
                    fail("An active listing has no in-flight custody to record");
                } catch (SQLException expected) {
                    assertTrue(expected.getMessage().contains("unreserved listing"));
                }
                assertTrue(database.transitionListingStatus(listing, "ACTIVE", "PENDING_PURCHASE"));
                database.beginWantedSettlement(listing, 14, 1, state, 50, 5, "COIN", "mail", "wallet");
                database.recordWantedSettlement(listing, "MAIL_ATTEMPTED");
            }
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                var database = new MarketplaceDatabase(connection);
                try (var statement = connection.createStatement();
                        var rows = statement.executeQuery("SELECT * FROM marketplace_wanted_settlement")) {
                    assertTrue(rows.next());
                    assertEquals("MAIL_ATTEMPTED", rows.getString("stage"));
                    assertEquals("mail", rows.getString("mail_correlation"));
                    assertEquals("wallet", rows.getString("wallet_correlation"));
                    assertEquals(14, rows.getInt("seller_db_id"));
                    assertEquals(21, rows.getInt("item_durability"));
                }
                assertTrue(database.findActiveListing(listing).isEmpty());
                connection.setAutoCommit(false);
                assertTrue(database.transitionListingStatus(listing, "PENDING_PURCHASE", "SOLD"));
                assertEquals(0, pending(connection));
                connection.rollback();
                connection.setAutoCommit(true);
                assertEquals(1, pending(connection));
                assertTrue(database.transitionListingStatus(listing, "PENDING_PURCHASE", "SOLD"));
                assertEquals(0, pending(connection));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void journalWriteFailureRollsBackReservationAndLeavesTheListingAvailable() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            var database = new MarketplaceDatabase(connection);
            long listing = database.createListing(new MarketplaceListing(0, 7, "Buyer", "axe", 1, 1,
                    MarketplaceItemState.NEUTRAL, 50, "COIN", "global", true, 1, "ACTIVE",
                    MarketplaceListing.TYPE_WANTED, 1, 0, 50));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TRIGGER reject_wanted_journal BEFORE INSERT ON marketplace_wanted_settlement
                        BEGIN SELECT RAISE(ABORT, 'fixture journal unavailable'); END
                        """);
            }
            try {
                reserve(database, listing, "first");
                fail("Expected journal rejection");
            } catch (SQLException expected) {
                assertTrue(expected.getMessage().contains("fixture journal unavailable"));
            }
            assertTrue(connection.getAutoCommit());
            assertTrue(database.findActiveListing(listing).isPresent());
            assertEquals(0, pending(connection));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("DROP TRIGGER reject_wanted_journal");
            }
            assertTrue(reserve(database, listing, "second"));
            assertFalse(reserve(database, listing, "repeat"));
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT mail_correlation FROM marketplace_wanted_settlement")) {
                assertTrue(rows.next());
                assertEquals("second", rows.getString(1));
                assertFalse(rows.next());
            }
        }
    }

    private static boolean reserve(MarketplaceDatabase database, long listing, String correlation) throws SQLException {
        return database.reserveWantedSettlement(listing, 14, 1, MarketplaceItemState.NEUTRAL,
                50, 5, "COIN", correlation, "");
    }

    private static int pending(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM marketplace_wanted_settlement")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
