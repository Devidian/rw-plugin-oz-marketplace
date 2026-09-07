package de.omegazirkel.risingworld.marketplace;

import static org.junit.Assert.*;
import java.sql.*;
import java.util.*;
import org.junit.Test;

public class CrierRemovalServiceTest {
    private MarketCrier crier(boolean global) {
        return new MarketCrier(12, "crier-12", "Crier", 7, "Owner", global, global, true, 1, true, 1);
    }
    private MarketplaceListing listing(int seller, String endpoint, boolean global) {
        return new MarketplaceListing(0, seller, "Seller", "wood", 0, 3,
                MarketplaceItemState.NEUTRAL, 30, "OZC", endpoint, global, 1, "ACTIVE");
    }
    @Test public void globalRemovalPromotesItsLocalOffersAndKeepsOtherOffers() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            var db = new MarketplaceDatabase(c); var npc = crier(true); db.upsertCrier(npc);
            long local = db.createListing(listing(8, npc.endpointId(), false));
            long other = db.createListing(listing(9, "other", false));
            long global = db.createListing(listing(10, "global", true));
            assertTrue(new CrierRemovalService(db).remove(npc, false, (l, id) -> { fail(); return false; }).deleted());
            assertTrue(db.findActiveListing(local).orElseThrow().globalListing());
            assertEquals("global", db.findActiveListing(local).orElseThrow().marketZoneId());
            assertFalse(db.findActiveListing(other).orElseThrow().globalListing());
            assertTrue(db.findActiveListing(global).isPresent());
            assertTrue(db.findCrier(npc.npcId()).isEmpty());
        }
    }
    @Test public void missingMailAndOwnedListingsKeepPersonalCrierUnchanged() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            var db = new MarketplaceDatabase(c); var npc = crier(false); db.upsertCrier(npc);
            long foreign = db.createListing(listing(8, npc.endpointId(), false));
            var service = new CrierRemovalService(db);
            assertFalse(service.remove(npc, false, (l, id) -> { fail(); return false; }).deleted());
            assertTrue(db.findActiveListing(foreign).isPresent());
            db.createListing(listing(7, npc.endpointId(), false));
            assertFalse(service.remove(npc, true, (l, id) -> { fail(); return false; }).deleted());
            assertTrue(db.findActiveListing(foreign).isPresent());
        }
    }
    @Test public void personalCrierKeepsWantedListingsUntilTheirSettlementIsExplicit() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            var db = new MarketplaceDatabase(c); var npc = crier(false); db.upsertCrier(npc);
            MarketplaceListing wanted = new MarketplaceListing(0, 8, "Requester", "wood", 0, 3,
                    MarketplaceItemState.NEUTRAL, 30, "OZC", npc.endpointId(), false, 1, "ACTIVE",
                    MarketplaceListing.TYPE_WANTED, 3, 0, 30);
            db.createListing(wanted);
            assertFalse(new CrierRemovalService(db).remove(npc, true, (listing, key) -> {
                fail("Wanted listing needs its own account-refund workflow"); return false;
            }).deleted());
            assertEquals("ACTIVE", db.unsettledEndpointListings(npc.endpointId()).get(0).status());
        }
    }
    @Test public void uncertainMailResultKeepsCustodyLockedAndRetryUsesSameCorrelation() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            var db = new MarketplaceDatabase(c); var npc = crier(false); db.upsertCrier(npc);
            long id = db.createListing(listing(8, npc.endpointId(), false));
            Set<String> delivered = new HashSet<>();
            assertFalse(new CrierRemovalService(db).remove(npc, true, (l, correlation) -> {
                delivered.add(correlation); assertEquals(3, l.amount()); return false;
            }).deleted());
            assertTrue(db.findActiveListing(id).isEmpty());
            assertEquals("PENDING_CRIER_RETURN", db.unsettledEndpointListings(npc.endpointId()).get(0).status());
            // Recreate the service to model resuming persisted state after interruption.
            assertTrue(new CrierRemovalService(db).remove(npc, true, (l, correlation) -> {
                assertTrue(delivered.contains(correlation)); return true;
            }).deleted());
            assertEquals(1, delivered.size());
            assertTrue(db.unsettledEndpointListings(npc.endpointId()).isEmpty());
        }
    }
    @Test public void inFlightTradesBlockGlobalMigration() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            var db = new MarketplaceDatabase(c); var npc = crier(true); db.upsertCrier(npc);
            long id = db.createListing(listing(8, npc.endpointId(), false));
            db.transitionListingStatus(id, "ACTIVE", "PENDING_PURCHASE");
            assertFalse(new CrierRemovalService(db).remove(npc, false, (l, key) -> true).deleted());
            assertTrue(db.findCrier(npc.npcId()).isPresent());
            assertFalse(db.unsettledEndpointListings(npc.endpointId()).get(0).globalListing());
        }
    }
    @Test public void globalCrierShowsItsLocalOffersEvenWhenZoneLocalTradeIsDisabled() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            var db = new MarketplaceDatabase(c); var npc = crier(true); db.upsertCrier(npc);
            db.createListing(listing(8, npc.endpointId(), false));
            db.createListing(listing(9, "global", true));
            db.createListing(listing(10, "other", false));
            var settings = PluginSettings.getInstance(); boolean previous = settings.localMarketplaceEnabled;
            try {
                settings.localMarketplaceEnabled = false;
                var service = new MarketplaceService(db, null, null, settings);
                assertEquals(2, service.listCrierListings(npc).size());
                var endpoint = new MarketZone(npc.endpointId(), "Crier", 0, 0, 0, 0, 0, 0, 0,
                        0, MarketZone.GLOBAL_ALLOW, 1);
                assertTrue(service.localTradeEnabledAt(Optional.of(endpoint)));
                assertFalse(service.localTradeEnabledAt(Optional.empty()));
            } finally { settings.localMarketplaceEnabled = previous; }
        }
    }
}
