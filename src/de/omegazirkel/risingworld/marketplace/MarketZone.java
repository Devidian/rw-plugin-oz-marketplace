package de.omegazirkel.risingworld.marketplace;

public record MarketZone(
        String id,
        String name,
        long areaId,
        int minChunkX,
        int maxChunkX,
        int minChunkY,
        int maxChunkY,
        int minChunkZ,
        int maxChunkZ,
        int feePercent,
        int globalTradeMode,
        long createdAt,
        int ownerDbId,
        String ownerName,
        String ownerAreaPermission) {
    public static final int GLOBAL_DENY = 0;
    public static final int GLOBAL_DEFAULT = 1;
    public static final int GLOBAL_ALLOW = 2;

    public MarketZone(
            String id,
            String name,
            int minChunkX,
            int maxChunkX,
            int minChunkY,
            int maxChunkY,
            int minChunkZ,
            int maxChunkZ,
            int feePercent,
            boolean allowGlobalTrade,
            long createdAt) {
        this(id, name, 0L, minChunkX, maxChunkX, minChunkY, maxChunkY, minChunkZ, maxChunkZ, feePercent,
                allowGlobalTrade ? GLOBAL_ALLOW : GLOBAL_DENY, createdAt, 0, "", "");
    }

    public MarketZone(String id, String name, long areaId, int minChunkX, int maxChunkX, int minChunkY,
            int maxChunkY, int minChunkZ, int maxChunkZ, int feePercent, int globalTradeMode, long createdAt) {
        this(id, name, areaId, minChunkX, maxChunkX, minChunkY, maxChunkY, minChunkZ, maxChunkZ, feePercent,
                globalTradeMode, createdAt, 0, "", "");
    }

    public boolean contains(int chunkX, int chunkY, int chunkZ) {
        return chunkX >= minChunkX && chunkX <= maxChunkX
                && chunkY >= minChunkY && chunkY <= maxChunkY
                && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
    }

    public boolean isAreaZone() {
        return areaId > 0L;
    }

    public boolean playerOwned() {
        return ownerDbId > 0;
    }

    public boolean ownedBy(int playerDbId) {
        return playerDbId > 0 && ownerDbId == playerDbId;
    }

    public boolean allowGlobalTrade() {
        return globalTradeMode == GLOBAL_ALLOW;
    }

    public boolean globalTradeAllowed(boolean globalMarketplaceEnabled) {
        return switch (normalizeGlobalTradeMode(globalTradeMode)) {
            case GLOBAL_DENY -> false;
            case GLOBAL_ALLOW -> true;
            default -> globalMarketplaceEnabled;
        };
    }

    public static int normalizeGlobalTradeMode(int mode) {
        if (mode == GLOBAL_DENY || mode == GLOBAL_ALLOW) {
            return mode;
        }
        return GLOBAL_DEFAULT;
    }
}
