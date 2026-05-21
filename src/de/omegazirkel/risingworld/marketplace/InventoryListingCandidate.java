package de.omegazirkel.risingworld.marketplace;

public record InventoryListingCandidate(
        String itemName,
        String displayName,
        int variant,
        int availableAmount,
        int maxStackSize) {
}
