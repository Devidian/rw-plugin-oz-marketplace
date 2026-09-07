package de.omegazirkel.risingworld.marketplace;

import java.sql.SQLException;

/** Keeps item custody locked until idempotent Mail delivery is durably acknowledged. */
public final class CrierRemovalService {
    @FunctionalInterface
    public interface ReturnDelivery {
        boolean deliver(MarketplaceListing listing, String correlationId);
    }

    private final MarketplaceDatabase database;

    public CrierRemovalService(MarketplaceDatabase database) { this.database = database; }

    public MarketplaceDatabase.CrierDeleteResult remove(MarketCrier crier, boolean mailAvailable,
            ReturnDelivery delivery) throws SQLException {
        MarketplaceDatabase.CrierDeleteResult prepared = database.prepareCrierRemoval(crier, mailAvailable);
        if (prepared.deleted() || prepared.activeListings() > 0) return prepared;
        for (MarketplaceListing listing : database.unsettledEndpointListings(crier.endpointId())) {
            if (!"PENDING_CRIER_RETURN".equals(listing.status())
                    || !delivery.deliver(listing, "crier-return-" + listing.id())) {
                return new MarketplaceDatabase.CrierDeleteResult(false, 1);
            }
            if (!database.transitionListingStatus(listing.id(), "PENDING_CRIER_RETURN", "CANCELLED")) {
                return new MarketplaceDatabase.CrierDeleteResult(false, 1);
            }
        }
        return database.deleteCrierIfEmpty(crier.npcId());
    }
}
