package de.omegazirkel.risingworld.marketplace;

import java.sql.SQLException;

/** Domain rules for persistent market crier endpoints; NPC and Wallet calls stay in the runtime layer. */
public final class MarketCrierService {
    private final MarketplaceDatabase database;
    private PluginSettings settings;

    public MarketCrierService(MarketplaceDatabase database, PluginSettings settings) {
        this.database = database;
        this.settings = settings;
    }

    public void updateSettings(PluginSettings settings) {
        this.settings = settings;
    }

    /** Player-owned zones and personal criers consume the same administrator-defined budget. */
    public boolean canCreatePersonal(int ownerDbId) throws SQLException {
        if (ownerDbId <= 0 || settings.maxPlayerMarketplaces == 0) return false;
        return settings.maxPlayerMarketplaces < 0
                || database.playerMarketCount(ownerDbId) + database.playerCrierCount(ownerDbId)
                        < settings.maxPlayerMarketplaces;
    }

    /** Replacing an existing player market must not consume an additional shared market slot. */
    public boolean canReplacePersonalMarket(int ownerDbId) throws SQLException {
        if (ownerDbId <= 0 || settings.maxPlayerMarketplaces == 0) return false;
        return settings.maxPlayerMarketplaces < 0
                || database.playerMarketCount(ownerDbId) + database.playerCrierCount(ownerDbId)
                        <= settings.maxPlayerMarketplaces;
    }

    public int slotLimit(MarketCrier crier) {
        long slots = (long) Math.max(1, crier.level()) * Math.max(1, settings.marketCrierBaseSlots);
        return slots > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) slots;
    }

    public long upgradeCost(MarketCrier crier) {
        double factor = Math.pow(Math.max(1.0d, settings.marketCrierUpgradePriceIncreaseFactor),
                Math.max(0, crier.level() - 1));
        double value = Math.max(0L, settings.marketCrierUpgradeBasePrice) * factor;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(value);
    }

    public boolean mayCreateListings(MarketCrier crier, int playerDbId) {
        return crier != null && (crier.global() || crier.ownerDbId() == playerDbId || crier.sharedListings());
    }
}
