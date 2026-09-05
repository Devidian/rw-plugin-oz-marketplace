package de.omegazirkel.risingworld.marketplace.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

public class MarketplaceExportRouteTest {
    @Test
    public void acceptsOptionalCursorAndPositiveAreaId() {
        assertNull(MarketplaceExportRoute.lastChange(null));
        assertEquals(Long.valueOf(42L), MarketplaceExportRoute.lastChange("42"));
        assertEquals(7L, MarketplaceExportRoute.areaId("7"));
    }

    @Test
    public void rejectsInvalidRouteParameters() {
        assertInvalidCursor("-1");
        assertInvalidCursor("9223372036854775808");
        assertInvalidArea(null);
        assertInvalidArea("0");
        assertInvalidArea("-1");
    }

    private void assertInvalidCursor(String value) {
        try {
            MarketplaceExportRoute.lastChange(value);
            fail("Expected invalid cursor: " + value);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private void assertInvalidArea(String value) {
        try {
            MarketplaceExportRoute.areaId(value);
            fail("Expected invalid area id: " + value);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
