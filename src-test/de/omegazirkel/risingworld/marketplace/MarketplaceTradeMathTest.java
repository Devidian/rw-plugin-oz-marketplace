package de.omegazirkel.risingworld.marketplace;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MarketplaceTradeMathTest {
    @Test
    public void partialPriceUsesCeilingAndFinalRemainder() {
        assertEquals(4L, MarketplaceService.partialPrice(10L, 3, 1));
        assertEquals(6L, MarketplaceService.partialPrice(6L, 2, 2));
        assertEquals(0L, MarketplaceService.partialPrice(0L, 8, 1));
    }

    @Test
    public void positiveTaxAlwaysRoundsUp() {
        assertEquals(1L, MarketplaceService.ceilPercent(1L, 1));
        assertEquals(1L, MarketplaceService.ceilPercent(99L, 1));
        assertEquals(2L, MarketplaceService.ceilPercent(101L, 1));
        assertEquals(0L, MarketplaceService.ceilPercent(999L, 0));
    }
}
