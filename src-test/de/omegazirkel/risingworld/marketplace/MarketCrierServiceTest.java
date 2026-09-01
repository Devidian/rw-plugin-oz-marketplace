package de.omegazirkel.risingworld.marketplace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.Test;

public class MarketCrierServiceTest {
    @Test
    public void personalZonesAndCriersShareTheCreationBudget() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MarketplaceDatabase database = new MarketplaceDatabase(connection);
            PluginSettings settings = PluginSettings.getInstance();
            settings.maxPlayerMarketplaces = 2;
            settings.marketCrierBaseSlots = 10;
            settings.marketCrierUpgradeBasePrice = 100L;
            settings.marketCrierUpgradePriceIncreaseFactor = 2.0d;
            database.upsertZone(new MarketZone("player-7-area-1", "Zone", 1L,
                    0, 0, 0, 0, 0, 0, 5, MarketZone.GLOBAL_DEFAULT, 1L, 7, "Owner", "permission"));
            MarketCrier crier = new MarketCrier(12L, "crier-12", "Crier", 7, "Owner", false,
                    false, false, 2, true, 2L);
            database.upsertCrier(crier);

            MarketCrierService service = new MarketCrierService(database, settings);
            assertFalse(service.canCreatePersonal(7));
            assertEquals(20, service.slotLimit(crier));
            assertEquals(200L, service.upgradeCost(crier));
            assertTrue(service.mayCreateListings(crier, 7));
            assertFalse(service.mayCreateListings(crier, 8));
        }
    }

    @Test
    public void replacementUsesExistingBudgetAndSharingControlsListingAccess() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MarketplaceDatabase database = new MarketplaceDatabase(connection);
            PluginSettings settings = PluginSettings.getInstance();
            settings.maxPlayerMarketplaces = 1;
            database.upsertZone(new MarketZone("player-7-area-1", "Zone", 1L,
                    0, 0, 0, 0, 0, 0, 5, MarketZone.GLOBAL_DEFAULT, 1L, 7, "Owner", "permission"));
            MarketCrierService service = new MarketCrierService(database, settings);

            assertFalse(service.canCreatePersonal(7));
            assertTrue(service.canReplacePersonalMarket(7));

            MarketCrier shared = new MarketCrier(13L, "crier-13", "Shared", 7, "Owner", false,
                    false, true, 1, false, 3L);
            MarketCrier global = new MarketCrier(14L, "crier-14", "Global", 0, "", true,
                    true, false, 1, true, 4L);
            assertTrue(service.mayCreateListings(shared, 8));
            assertTrue(service.mayCreateListings(global, 8));

            settings.maxPlayerMarketplaces = 0;
            assertFalse(service.canCreatePersonal(7));
            settings.maxPlayerMarketplaces = -1;
            assertTrue(service.canCreatePersonal(7));
        }
    }
}
