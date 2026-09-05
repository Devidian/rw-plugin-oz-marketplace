package de.omegazirkel.risingworld.marketplace;

import static org.junit.Assert.assertEquals;

import java.util.Optional;

import org.junit.Test;

public class MarketplaceServiceWantedListingTest {
    @Test
    public void globalCrierWantedListingsUseTheGlobalEndpoint() {
        MarketZone crierZone = new MarketZone("crier-14", "Global Crier", 0L,
                0, 0, 0, 0, 0, 0, 0, MarketZone.GLOBAL_ALLOW, 1L, 0, "", "");
        MarketCrier crier = new MarketCrier(14L, "crier-14", "Global Crier", 0, "", true,
                true, false, 1, true, 1L);

        assertEquals("global", MarketplaceService.wantedListingEndpoint(Optional.of(crierZone), crier));
    }

    @Test
    public void personalCrierWantedListingsKeepTheirCrierEndpoint() {
        MarketZone crierZone = new MarketZone("crier-15", "Personal Crier", 0L,
                0, 0, 0, 0, 0, 0, 0, MarketZone.GLOBAL_DENY, 1L, 7, "Owner", "");
        MarketCrier crier = new MarketCrier(15L, "crier-15", "Personal Crier", 7, "Owner", false,
                false, false, 1, true, 1L);

        assertEquals("crier-15", MarketplaceService.wantedListingEndpoint(Optional.of(crierZone), crier));
    }
}
