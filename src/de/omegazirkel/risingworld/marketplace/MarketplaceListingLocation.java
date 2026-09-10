package de.omegazirkel.risingworld.marketplace;

/** Player-facing endpoint state for an active listing. */
public record MarketplaceListingLocation(String name, boolean available) {
    public MarketplaceListingLocation {
        name = name == null ? "" : name.trim();
    }
}
