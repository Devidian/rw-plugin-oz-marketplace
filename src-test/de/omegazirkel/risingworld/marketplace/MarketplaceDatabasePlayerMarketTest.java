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

    @Test
    public void zoneConversionMovesActiveListingsToCrierEndpointAtomically() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MarketplaceDatabase database = new MarketplaceDatabase(connection);
            database.upsertZone(playerZone());
            database.createListing(wantedListing());
            MarketCrier crier = new MarketCrier(700L, "crier-700", "Market Crier", 42, "Owner",
                    false, false, false, 1, true, 200L);

            assertEquals(1, database.convertZoneToCrier("player-42-area-99", crier));
            assertTrue(database.findZone("player-42-area-99").isEmpty());
            assertEquals(crier, database.findCrier(700L).orElseThrow());
            assertEquals(1, database.playerCrierCount(42));
            assertEquals("crier-700", database.findActiveListing(1L).orElseThrow().marketZoneId());
        }
    }

    @Test
    public void missingCrierEndpointIsRetainedWhileItHasActiveListings() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MarketplaceDatabase database = new MarketplaceDatabase(connection);
            MarketCrier crier = new MarketCrier(700L, "crier-700", "Market Crier", 42, "Owner",
                    false, false, false, 1, true, 200L);
            database.upsertCrier(crier);
            long listingId = database.createListing(new MarketplaceListing(0L, 42, "Owner", "wood", 0, 5,
                    MarketplaceItemState.NEUTRAL, 50L, "OZC", crier.endpointId(), false, 100L, "ACTIVE",
                    MarketplaceListing.TYPE_WANTED, 5, 0, 50L));

            MarketplaceDatabase.CrierDeleteResult blocked = database.deleteCrierIfEmpty(crier.npcId());
            assertFalse(blocked.deleted());
            assertEquals(1, blocked.activeListings());

            assertTrue(database.transitionListingStatus(listingId, "ACTIVE", "CANCELLED"));
            assertTrue(database.deleteCrierIfEmpty(crier.npcId()).deleted());
            assertTrue(database.findCrier(crier.npcId()).isEmpty());
        }
    }

    @Test
    public void crierLocationSurvivesNpcIdReplacement() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MarketplaceDatabase database = new MarketplaceDatabase(connection);
            MarketCrier original = new MarketCrier(700L, "crier-700", "Market Crier", 42, "Owner",
                    false, false, false, 1, true, 200L);
            database.upsertCrier(original);
            database.upsertCrierLocation(original.endpointId(), new MarketplaceDatabase.CrierLocation(1, 2, 3, 0, 0, 0, 1));
            MarketCrier replacement = new MarketCrier(701L, original.endpointId(), original.name(), original.ownerDbId(),
                    original.ownerName(), false, false, false, 1, true, original.createdAt());
            database.replaceCrierNpcId(original.npcId(), replacement);
            assertTrue(database.findCrier(700L).isEmpty());
            assertEquals(replacement, database.findCrier(701L).orElseThrow());
            assertEquals(3f, database.findCrierLocation(replacement.endpointId()).orElseThrow().z(), 0f);
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
