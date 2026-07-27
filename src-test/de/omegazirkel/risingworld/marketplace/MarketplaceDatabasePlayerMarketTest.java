package de.omegazirkel.risingworld.marketplace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.Test;

public class MarketplaceDatabasePlayerMarketTest {
    @Test
    public void playerMarketOwnershipRoundTripsAndCountsByOwner() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MarketplaceDatabase database = new MarketplaceDatabase(connection);
            MarketZone zone = playerZone();

            database.upsertZone(zone);

            MarketZone persisted = database.findZone(zone.id()).orElseThrow();
            assertTrue(persisted.playerOwned());
            assertTrue(persisted.ownedBy(42));
            assertEquals("Owner", persisted.ownerName());
            assertEquals("ozlc-owner", persisted.ownerAreaPermission());
            assertEquals(1, database.playerMarketCount(42));
            assertTrue(database.areaHasMarket(99L, ""));
        }
    }

    @Test
    public void zoneDeletionIsBlockedUntilItsListingIsFinalized() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MarketplaceDatabase database = new MarketplaceDatabase(connection);
            database.upsertZone(playerZone());
            long listingId = database.createListing(wantedListing());

            MarketplaceDatabase.ZoneDeleteResult blocked = database.deleteZoneIfEmpty("player-42-area-99");
            assertFalse(blocked.deleted());
            assertEquals(1, blocked.activeListings());

            assertTrue(database.transitionListingStatus(listingId, "ACTIVE", "CANCELLED"));
            MarketplaceDatabase.ZoneDeleteResult deleted = database.deleteZoneIfEmpty("player-42-area-99");
            assertTrue(deleted.deleted());
            assertTrue(database.findZone("player-42-area-99").isEmpty());
        }
    }

    @Test
    public void partialWantedFulfillmentPersistsRemainderAndLocksProgress() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MarketplaceDatabase database = new MarketplaceDatabase(connection);
            long listingId = database.createListing(wantedListing());
            assertTrue(database.transitionListingStatus(listingId, "ACTIVE", "PENDING_PURCHASE"));

            MarketplaceSale partial = new MarketplaceSale(0L, listingId, 7, 42, "wood", 0, 2,
                    MarketplaceItemState.NEUTRAL, 20L, "OZC", 0L, 20L, "player-42-area-99", 200L);
            assertTrue(database.completePartialSale(partial, 2, 3, 30L,
                    "PENDING_PURCHASE", "SOLD"));

            MarketplaceListing persisted = database.findActiveListing(listingId).orElseThrow();
            assertTrue(persisted.wanted());
            assertEquals(5, persisted.originalAmount());
            assertEquals(2, persisted.fulfilledAmount());
            assertEquals(3, persisted.amount());
            assertEquals(30L, persisted.price());
            assertEquals(50L, persisted.originalPrice());
        }
    }

    private MarketZone playerZone() {
        return new MarketZone("player-42-area-99", "Owner Market", 99L,
                1, 2, 3, 4, 5, 6, 7, MarketZone.GLOBAL_DEFAULT, 100L,
                42, "Owner", "ozlc-owner");
    }

    private MarketplaceListing wantedListing() {
        return new MarketplaceListing(0L, 42, "Owner", "wood", 0, 5,
                MarketplaceItemState.NEUTRAL, 50L, "OZC", "player-42-area-99", false, 100L, "ACTIVE",
                MarketplaceListing.TYPE_WANTED, 5, 0, 50L);
    }
}
