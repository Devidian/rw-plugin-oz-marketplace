package de.omegazirkel.risingworld.marketplace;

public record MarketplaceResult(boolean success, String message) {
    public static MarketplaceResult ok(String message) {
        return new MarketplaceResult(true, message);
    }

    public static MarketplaceResult fail(String message) {
        return new MarketplaceResult(false, message);
    }
}
