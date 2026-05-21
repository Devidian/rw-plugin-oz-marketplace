package de.omegazirkel.risingworld.marketplace;

public record MarketZone(
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

    public boolean contains(int chunkX, int chunkY, int chunkZ) {
        return chunkX >= minChunkX && chunkX <= maxChunkX
                && chunkY >= minChunkY && chunkY <= maxChunkY
                && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
    }
}
