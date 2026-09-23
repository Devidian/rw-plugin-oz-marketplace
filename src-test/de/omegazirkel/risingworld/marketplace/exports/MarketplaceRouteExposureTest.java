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
        Path directory = Files.createTempDirectory("oz-marketplace-settings-");
        Path settings = directory.resolve("settings.world.json");
        Files.writeString(directory.resolve("settings.default.json"),
                "{\"exposeMarketplaceZones\":true,\"exposeMarketplaceOffers\":true}");
        Files.writeString(settings, "{\"exposeMarketplaceZones\":false,\"exposeMarketplaceOffers\":false}");

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
