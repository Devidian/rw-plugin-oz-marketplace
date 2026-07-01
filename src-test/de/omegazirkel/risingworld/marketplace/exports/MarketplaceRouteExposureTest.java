package de.omegazirkel.risingworld.marketplace.exports;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import de.omegazirkel.risingworld.marketplace.PluginSettings;

public class MarketplaceRouteExposureTest {

    @Test
    public void loadsRouteExposureFlagsFromSettings() throws Exception {
        Path settings = Files.createTempFile("oz-marketplace-settings-", ".properties");
        Files.writeString(settings, String.join("\n",
                "exposeMarketplaceZones=false",
                "exposeMarketplaceOffers=false"));

        PluginSettings pluginSettings = PluginSettings.getInstance();
        pluginSettings.initSettings(settings.toString());

        MarketplaceRouteExposure disabled = MarketplaceRouteExposure.from(pluginSettings);
        assertFalse(disabled.zones());
        assertFalse(disabled.offers());

        Files.writeString(settings, "");
        pluginSettings.initSettings(settings.toString());

        MarketplaceRouteExposure defaults = MarketplaceRouteExposure.from(pluginSettings);
        assertTrue(defaults.zones());
        assertTrue(defaults.offers());
    }
}
