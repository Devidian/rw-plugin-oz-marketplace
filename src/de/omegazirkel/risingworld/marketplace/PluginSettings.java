package de.omegazirkel.risingworld.marketplace;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.tools.settings.AdminSettingsEntry;
import de.omegazirkel.risingworld.tools.settings.AdminSettingsType;
import de.omegazirkel.risingworld.tools.settings.JsonSettingsFile;
import de.omegazirkel.risingworld.tools.settings.SettingsFileEditor;

public class PluginSettings {
    private static PluginSettings instance;
    private static Marketplace plugin;

    public String marketCommand = "mp";
    public boolean enableWelcomeMessage = false;
    public boolean localMarketplaceEnabled = true;
    public boolean globalMarketplaceEnabled = true;
    public boolean marketZoneOnlyMode = false;
    public boolean onlyMarketCriers = true;
    public int defaultLocalFeePercent = 5;
    public int defaultGlobalFeePercent = 5;
    public long minimumLocalFee = 0L;
    public long minimumGlobalFee = 0L;
    public int maxListingsPerPlayer = 20;
    public int maxPlayerMarketplaces = 0;
    public long marketCapacityBasePrice = 2500L;
    public double marketCapacityPriceIncreaseFactor = 1.0d;
    public int marketCrierBaseSlots = 10;
    public long marketCrierUpgradeBasePrice = 2500L;
    public double marketCrierUpgradePriceIncreaseFactor = 1.0d;
    public boolean showMarketplaceZoneIndicator = true;
    public boolean exposeMarketplaceZones = true;
    public boolean exposeMarketplaceOffers = true;
    private Path settingsFile;

    private static OZLogger logger() {
        return Marketplace.logger();
    }

    public static PluginSettings getInstance(Marketplace p) {
        plugin = p;
        return getInstance();
    }

    public static PluginSettings getInstance() {
        if (instance == null) {
            instance = new PluginSettings();
        }
        return instance;
    }

    private PluginSettings() {
    }

    public void initSettings() {
        initSettings(JsonSettingsFile.worldSettingsFile(plugin.getPath() != null ? plugin.getPath() : ".").toString());
    }

    public void initSettings(String filePath) {
        settingsFile = Paths.get(filePath);
        if (settingsFile.getFileName().toString().endsWith(".properties")) {
            initLegacyProperties(settingsFile);
            return;
        }
        Path defaultSettingsFile = settingsFile.resolveSibling("settings.default.json");
        Path legacySettingsFile = settingsFile.resolveSibling("settings.properties");
        try {
            if (JsonSettingsFile.migrateLegacyProperties(legacySettingsFile, settingsFile))
                logger().info("Migrated legacy settings.properties to " + settingsFile.getFileName());
            if (Files.notExists(settingsFile) && Files.exists(defaultSettingsFile))
                JsonSettingsFile.writeFlatAtomically(settingsFile, JsonSettingsFile.loadFlat(defaultSettingsFile));
            Properties settings = JsonSettingsFile.loadProperties(settingsFile);
            Properties defaults = JsonSettingsFile.loadProperties(defaultSettingsFile);

            marketCommand = settings.getProperty("marketCommand", defaults.getProperty("marketCommand", "mp")).trim();
            enableWelcomeMessage = bool(settings, defaults, "sendPluginWelcome", false);
            localMarketplaceEnabled = bool(settings, defaults, "localMarketplaceEnabled", true);
            globalMarketplaceEnabled = bool(settings, defaults, "globalMarketplaceEnabled", true);
            marketZoneOnlyMode = bool(settings, defaults, "marketZoneOnlyMode", false);
            onlyMarketCriers = bool(settings, defaults, "onlyMarketCriers", true);
            int legacyDefaultFee = integer(settings, defaults, "defaultFeePercent", 5, 0, 100);
            defaultLocalFeePercent = integer(settings, defaults, "defaultLocalFeePercent", legacyDefaultFee, 0, 100);
            defaultGlobalFeePercent = integer(settings, defaults, "defaultGlobalFeePercent", legacyDefaultFee, 0, 100);
            minimumLocalFee = decimal(settings, defaults, "minimumLocalFee", 0L, 0L, Long.MAX_VALUE);
            minimumGlobalFee = decimal(settings, defaults, "minimumGlobalFee", 0L, 0L, Long.MAX_VALUE);
            maxListingsPerPlayer = integer(settings, defaults, "maxListingsPerPlayer", 20, 1, 1000);
            maxPlayerMarketplaces = signedInteger(settings, defaults, "maxPlayerMarketplaces", 0);
            marketCapacityBasePrice = decimal(settings, defaults, "marketCapacityBasePrice", 2500L, 0L, Long.MAX_VALUE);
            marketCapacityPriceIncreaseFactor = decimalFactor(settings, defaults, "marketCapacityPriceIncreaseFactor", 1.0d);
            marketCrierBaseSlots = integer(settings, defaults, "marketCrierBaseSlots", 10, 1, 1000);
            marketCrierUpgradeBasePrice = decimal(settings, defaults, "marketCrierUpgradeBasePrice", 2500L, 0L,
                    Long.MAX_VALUE);
            marketCrierUpgradePriceIncreaseFactor = decimalFactor(settings, defaults,
                    "marketCrierUpgradePriceIncreaseFactor", 1.0d);
            showMarketplaceZoneIndicator = bool(settings, defaults, "showMarketplaceZoneIndicator", true);
            exposeMarketplaceZones = bool(settings, defaults, "exposeMarketplaceZones", true);
            exposeMarketplaceOffers = bool(settings, defaults, "exposeMarketplaceOffers", true);

            logger().info((plugin == null ? "OZMarketplace" : plugin.getName()) + " Plugin settings loaded");
            logger().info("Marketplace command is /" + marketCommand);
        } catch (IOException ex) {
            logger().error("IOException on initSettings: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /** Compatibility path for callers still passing an explicit properties file. */
    private void initLegacyProperties(Path legacyFile) {
        try {
            Properties settings = new Properties();
            if (Files.exists(legacyFile)) {
                try (FileInputStream in = new FileInputStream(legacyFile.toFile())) {
                    settings.load(new InputStreamReader(in, "UTF8"));
                }
            }
            Properties defaults = new Properties();
            apply(settings, defaults);
        } catch (IOException ex) {
            logger().error("IOException on initSettings: " + ex.getMessage());
        }
    }

    private void apply(Properties settings, Properties defaults) {
            marketCommand = settings.getProperty("marketCommand", defaults.getProperty("marketCommand", "mp")).trim();
            enableWelcomeMessage = bool(settings, defaults, "sendPluginWelcome", false);
            localMarketplaceEnabled = bool(settings, defaults, "localMarketplaceEnabled", true);
            globalMarketplaceEnabled = bool(settings, defaults, "globalMarketplaceEnabled", true);
            marketZoneOnlyMode = bool(settings, defaults, "marketZoneOnlyMode", false);
            onlyMarketCriers = bool(settings, defaults, "onlyMarketCriers", true);
            int legacyDefaultFee = integer(settings, defaults, "defaultFeePercent", 5, 0, 100);
            defaultLocalFeePercent = integer(settings, defaults, "defaultLocalFeePercent", legacyDefaultFee, 0, 100);
            defaultGlobalFeePercent = integer(settings, defaults, "defaultGlobalFeePercent", legacyDefaultFee, 0, 100);
            minimumLocalFee = decimal(settings, defaults, "minimumLocalFee", 0L, 0L, Long.MAX_VALUE);
            minimumGlobalFee = decimal(settings, defaults, "minimumGlobalFee", 0L, 0L, Long.MAX_VALUE);
            maxListingsPerPlayer = integer(settings, defaults, "maxListingsPerPlayer", 20, 1, 1000);
            maxPlayerMarketplaces = signedInteger(settings, defaults, "maxPlayerMarketplaces", 0);
            marketCapacityBasePrice = decimal(settings, defaults, "marketCapacityBasePrice", 2500L, 0L, Long.MAX_VALUE);
            marketCapacityPriceIncreaseFactor = decimalFactor(settings, defaults, "marketCapacityPriceIncreaseFactor", 1.0d);
            marketCrierBaseSlots = integer(settings, defaults, "marketCrierBaseSlots", 10, 1, 1000);
            marketCrierUpgradeBasePrice = decimal(settings, defaults, "marketCrierUpgradeBasePrice", 2500L, 0L,
                    Long.MAX_VALUE);
            marketCrierUpgradePriceIncreaseFactor = decimalFactor(settings, defaults,
                    "marketCrierUpgradePriceIncreaseFactor", 1.0d);
            showMarketplaceZoneIndicator = bool(settings, defaults, "showMarketplaceZoneIndicator", true);
            exposeMarketplaceZones = bool(settings, defaults, "exposeMarketplaceZones", true);
            exposeMarketplaceOffers = bool(settings, defaults, "exposeMarketplaceOffers", true);
    }

    public List<AdminSettingsEntry> adminSettingsEntries() {
        return List.of(
                AdminSettingsEntry.group("general", "General", "Command and welcome behavior."),
                entry("marketCommand", "Market command", "Chat command used for marketplace actions.", marketCommand,
                        "market", AdminSettingsType.STRING),
                entry("sendPluginWelcome", "Welcome message", "Shows a short Marketplace message when a player joins.",
                        enableWelcomeMessage, "false", AdminSettingsType.BOOLEAN),
                AdminSettingsEntry.group("tradeModes", "Trade modes", "Local, global, and zone-only trade behavior."),
                entry("localMarketplaceEnabled", "Local marketplace",
                        "Enables local listings tied to a market zone.",
                        localMarketplaceEnabled, "true", AdminSettingsType.BOOLEAN),
                entry("globalMarketplaceEnabled", "Global marketplace",
                        "Enables global listings in zones that allow global trade.",
                        globalMarketplaceEnabled, "true", AdminSettingsType.BOOLEAN),
                entry("marketZoneOnlyMode", "Zone-only mode",
                        "Requires a market zone for listing discovery.",
                        marketZoneOnlyMode, "false", AdminSettingsType.BOOLEAN),
                entry("onlyMarketCriers", "Only market criers",
                        "Players create market criers instead of new market zones; administrators may still create zones.",
                        onlyMarketCriers, "true", AdminSettingsType.BOOLEAN),
                AdminSettingsEntry.group("fees", "Fees and limits", "Default buyer fees and listing limits."),
                entry("defaultLocalFeePercent", "Default local fee percent",
                        "Fee charged to buyers for local listings when a market zone has no override.",
                        defaultLocalFeePercent, "5", AdminSettingsType.INTEGER),
                entry("defaultGlobalFeePercent", "Default global fee percent",
                        "Fee charged to buyers for global listings.",
                        defaultGlobalFeePercent, "5", AdminSettingsType.INTEGER),
                entry("minimumLocalFee", "Minimum local fee",
                        "Minimum whole-number fee charged to buyers for local listings.",
                        minimumLocalFee, "0", AdminSettingsType.INTEGER),
                entry("minimumGlobalFee", "Minimum global fee",
                        "Minimum whole-number fee charged to buyers for global listings.",
                        minimumGlobalFee, "0", AdminSettingsType.INTEGER),
                entry("maxListingsPerPlayer", "Max listings per player",
                        "Maximum active listings a player may own at once.",
                        maxListingsPerPlayer, "20", AdminSettingsType.INTEGER),
                entry("maxPlayerMarketplaces", "Max player marketplaces",
                        "0 disables player markets, negative is unlimited, positive limits markets per player.",
                        maxPlayerMarketplaces, "0", AdminSettingsType.INTEGER),
                entry("marketCapacityBasePrice", "Market capacity base price",
                        "Base price for one marketplace-capacity upgrade.", marketCapacityBasePrice, "2500",
                        AdminSettingsType.INTEGER),
                entry("marketCapacityPriceIncreaseFactor", "Market capacity price increase factor",
                        "Price multiplier for every previously purchased capacity upgrade.", marketCapacityPriceIncreaseFactor,
                        "1.0", AdminSettingsType.DECIMAL),
                AdminSettingsEntry.group("marketCriers", "Market criers", "Personal NPC market capacity and upgrades."),
                entry("marketCrierBaseSlots", "Crier base slots",
                        "Offer slots and, separately, wanted slots at market crier level 1.", marketCrierBaseSlots,
                        "10", AdminSettingsType.INTEGER),
                entry("marketCrierUpgradeBasePrice", "Crier upgrade base price",
                        "Price for the first personal market crier level upgrade.", marketCrierUpgradeBasePrice,
                        "2500", AdminSettingsType.INTEGER),
                entry("marketCrierUpgradePriceIncreaseFactor", "Crier upgrade price factor",
                        "Price multiplier for each earlier market crier upgrade.", marketCrierUpgradePriceIncreaseFactor,
                        "1.0", AdminSettingsType.DECIMAL),
                entry("showMarketplaceZoneIndicator", "Marketplace-zone indicator",
                        "Shows the Marketplace icon in the shared Tools indicator bar while players are in an active market zone.",
                        showMarketplaceZoneIndicator, "true", AdminSettingsType.BOOLEAN),
                AdminSettingsEntry.group("exportRoutes", "Export routes",
                        "Future native route exposure flags for external manager services."),
                entry("exposeMarketplaceZones", "Expose marketplace zones",
                        "Enables the future Marketplace zone export route.", exposeMarketplaceZones, "true",
                        AdminSettingsType.BOOLEAN),
                entry("exposeMarketplaceOffers", "Expose marketplace offers",
                        "Enables the future Marketplace area-offer export route.", exposeMarketplaceOffers, "true",
                        AdminSettingsType.BOOLEAN));
    }

    private AdminSettingsEntry entry(String key, String label, String description, Object value, String defaultValue,
            AdminSettingsType type) {
        return new AdminSettingsEntry(key, label, description, String.valueOf(value), defaultValue, type, false,
                newValue -> SettingsFileEditor.writeValue(settingsFile, key, newValue));
    }

    private boolean bool(Properties settings, Properties defaults, String key, boolean fallback) {
        return settings.getProperty(key, defaults.getProperty(key, String.valueOf(fallback))).equalsIgnoreCase("true");
    }

    private int integer(Properties settings, Properties defaults, String key, int fallback, int min, int max) {
        int value = Integer.parseInt(settings.getProperty(key, defaults.getProperty(key, String.valueOf(fallback))));
        return Math.max(min, Math.min(max, value));
    }

    private long decimal(Properties settings, Properties defaults, String key, long fallback, long min, long max) {
        long value = Long.parseLong(settings.getProperty(key, defaults.getProperty(key, String.valueOf(fallback))));
        return Math.max(min, Math.min(max, value));
    }

    private int signedInteger(Properties settings, Properties defaults, String key, int fallback) {
        return Integer.parseInt(settings.getProperty(key, defaults.getProperty(key, String.valueOf(fallback))));
    }

    private double decimalFactor(Properties settings, Properties defaults, String key, double fallback) {
        try {
            double value = Double.parseDouble(settings.getProperty(key, defaults.getProperty(key, String.valueOf(fallback))));
            return Double.isFinite(value) && value >= 1.0d ? value : fallback;
        } catch (NumberFormatException ex) { return fallback; }
    }
}
