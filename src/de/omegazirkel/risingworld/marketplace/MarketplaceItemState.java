package de.omegazirkel.risingworld.marketplace;

/** Mutable Rising World item data held in listing custody. */
public record MarketplaceItemState(int durability, short status, String modifier, int color) {
    public MarketplaceItemState(int durability, short status, String modifier) {
        this(durability, status, modifier, 0);
    }

    public MarketplaceItemState { modifier = modifier == null ? "" : modifier; }
    public static final MarketplaceItemState NEUTRAL = new MarketplaceItemState(0, (short) 0, "", 0);
}
