package de.omegazirkel.risingworld.marketplace;

/** Persisted Marketplace-owned NPC endpoint. Wallet accounts are resolved by the runtime. */
public record MarketCrier(long npcId, String endpointId, String name, int ownerDbId, String ownerName,
        boolean global, boolean globalTradeEnabled, boolean sharedListings, int level, boolean male, long createdAt,
        int feePercent) {

    public MarketCrier {
        endpointId = endpointId == null ? "" : endpointId.trim();
        name = name == null ? "" : name.trim();
        ownerName = ownerName == null ? "" : ownerName.trim();
        level = Math.max(1, level);
        feePercent = Math.max(0, Math.min(100, feePercent));
    }

    /** Compatibility constructor for persisted callers created before per-crier fees existed. */
    public MarketCrier(long npcId, String endpointId, String name, int ownerDbId, String ownerName,
            boolean global, boolean globalTradeEnabled, boolean sharedListings, int level, boolean male, long createdAt) {
        this(npcId, endpointId, name, ownerDbId, ownerName, global, globalTradeEnabled, sharedListings, level, male,
                createdAt, 5);
    }

    public boolean personal() {
        return !global;
    }

    public boolean ownedBy(int playerDbId) {
        return personal() && playerDbId > 0 && ownerDbId == playerDbId;
    }

    public String accountId() {
        return "market-crier::" + npcId;
    }
}
