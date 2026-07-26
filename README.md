# OZ - Marketplace

Player-to-player marketplace plugin for Rising World.

## Responsibilities

- market-zone setup and fee overrides
- local and global player listings
- listing creation from player inventory
- Wallet-backed purchases and seller payouts
- sale history for sellers

`rw-plugin-oz-tools` is a hard runtime dependency. `rw-plugin-oz-wallet` is required for functional listings and purchases. If Wallet is missing, Marketplace loads but trading is disabled and admins receive a warning on spawn.

## Settings

The plugin copies `settings.default.properties` to `settings.properties` on first run.

```properties
logLevel=ALL
reloadOnChange=true
marketCommand=mp
sendPluginWelcome=false
localMarketplaceEnabled=true
globalMarketplaceEnabled=true
marketZoneOnlyMode=false
defaultLocalFeePercent=5
defaultGlobalFeePercent=5
minimumLocalFee=0
minimumGlobalFee=0
maxListingsPerPlayer=20
showMarketplaceZoneIndicator=true
exposeMarketplaceZones=true
exposeMarketplaceOffers=true
```

Marketplace fees are charged to buyers on top of the listing price and removed from the economy. Sellers receive the listed price. The applied fee is the higher value of the percent fee and the configured minimum fee.
`marketZoneOnlyMode=true` requires players to stand in a market zone for both local and global trading. When it is `false`, global trading works outside market zones while local trading still requires a market zone.
`showMarketplaceZoneIndicator=true` shows the Marketplace icon in the shared Tools indicator bar while players are inside an active market zone.

## Player Commands

- `/mp`: open the Marketplace radial menu
- `/mp list`: list listings visible at the current market zone, or global listings when outside a zone unless zone-only mode is enabled
- `/mp buy <listing-id>`: buy a visible listing through Wallet
- `/mp cancel <listing-id>`: cancel your own listing and return the item
- `/mp sales`: show your latest visible sale payouts
- `/mp status` or `/mp info`: open the shared Tools Info/Status panel

An empty or omitted `currency` uses Wallet's configured default currency.

## Player Marketplace UI

Players can open the Marketplace radial menu through `/mp` or the Marketplace entry in `/ozt`. The radial menu opens the Marketplace overlay and includes an `Info / Status` action using the shared Tools status panel.

The overlay footer shows the player's Wallet default-currency balance for quick pricing context.

The `Sell` tab scans the player's inventory, groups sellable items by concrete item name, variant, and mutable custody state, and presents them as icon cards with amount and variant context. Construction and clothing inventory entries use their concrete `ConstructionItem`/`ClothingItem` definition names instead of the generic `constructionitem`/`clothingitem` carrier definitions, so listing labels, custody, icons, and buyer delivery retain the actual shape or clothing type. Custom construction colors are kept separate while listed and restored unchanged on cancellation, rollback, or buyer delivery. Selecting a card fills a listing form for amount, price, Wallet currency, and local/global mode. The currency selector is sourced from Wallet and defaults to the Wallet default currency. Local listings require the current market zone. Global listings can be created outside market zones when zone-only mode is disabled. Listing currency identifiers are validated against the Wallet currency registry before any inventory is removed. Items are only removed from inventory after the player confirms and the existing listing service accepts the listing.

The `Local` and `Global` tabs show visible listings for the current access context. Both tabs default to a card layout and include a card/table toggle that persists per player, plus a name search that filters visible offers by their displayed item label. The `Local` tab is hidden outside market zones, and disabled marketplace modes are hidden from the overlay. Listings use derived display names with variant suffixes when needed, show the listing price plus the buyer fee amount and percent where a fee applies, and buying from the UI asks for confirmation with price, fee, and total before calling the same Wallet-backed purchase flow used by `/mp buy <listing-id>`. Sellers can cancel their own active listings from these tabs; cancellation uses the same item-return flow as `/mp cancel <listing-id>`.

Existing listings that already persisted only `constructionitem` or
`clothingitem` cannot be migrated automatically because the concrete definition
ID was not stored. Recreate those listings after updating, and do not downgrade
while listings with concrete construction or clothing names are active.

The `Sales` tab shows the seller's latest visible completed sales with item, amount, condition, buyer, payout, fee, and market zone. Legacy or unavailable buyer records display `Unknown`. The `Remove` action hides a completed sale from that seller's history after confirmation. Removed sale rows no longer appear in the tab or `/mp sales`, but the raw sale record remains in the database with `seller_hidden_at` set for audit/history retention.

Online sellers receive a localized notification after a sale is durably completed. Failed or rolled-back purchases do not send a sale notification.

Admins see a `Management` tab for the current Rising World Area. Outside a market zone it only offers market-zone creation. Inside a market zone it syncs the zone name from the Area name, cycles global-trade mode between `default`, `allow`, and `deny`, sets a numeric fee override, and dissolves the current zone with confirmation. Dissolving a zone promotes active local listings to global listings in the same database transaction before deleting the zone.

New listings are created through the Marketplace UI.

## Market Zone Indicator

Players see the Marketplace icon in the shared Tools indicator bar while standing inside a configured market zone. Disabling `showMarketplaceZoneIndicator` hides this HUD signal without changing marketplace access, listing discovery, or purchases. The indicator stays hidden when both local and global trading are disabled unless the current zone explicitly allows global trade.

## Admin Commands

- `/mp zone set <id> <radiusChunks> <feePercent> <globalMode> [label]`: create or update a legacy chunk-range market zone centered on the admin's current chunk. `globalMode` accepts `deny`, `default`, or `allow`.
- `/mp zone list`: list market zones
- `/mp zone delete [current-id]`: remove only the market zone the admin is currently standing in. The optional id is a guard and must match the current zone.

New UI-managed market zones apply to whole existing Rising World Areas. Area-zone creation is blocked unless the admin is standing inside an Area. The legacy `/mp zone set` command remains for admin recovery and explicit chunk-range maintenance.

Local listings can only be bought in their source market zone. Global listings are visible and purchasable outside zones when `marketZoneOnlyMode=false`; inside zones they follow the zone's global mode: explicit `deny`, global setting `default`, or explicit `allow`.

## Persistence

Marketplace data is stored in the plugin world SQLite database through `rw-plugin-oz-tools` connection helpers:

- `marketplace_zones`
- `marketplace_listings`
- `marketplace_sales`

The v4 schema is additive and can be left in place when disabling the plugin. Existing databases are migrated by adding `marketplace_sales.seller_hidden_at`, `marketplace_zones.area_id`, `marketplace_zones.global_trade_mode`, and default-zero `item_color` columns for listing and sale custody state. Existing boolean global zone flags are mapped to explicit `allow` or `deny`. Older plugin versions ignore the additive color columns, but would return or deliver active colored construction listings without their color; do not downgrade until those listings are completed or cancelled.

## Future export route preparation

Marketplace contains route-ready export DTOs/services for future native plugin
routes. Prepared exports cover market zones and active offers for a Rising World
area id. Both exports support `lastChange` cursor filtering over the persisted
`created_at` values and match the transitional bridge contract.
`exposeMarketplaceZones=true` and `exposeMarketplaceOffers=true` control whether
the future native routes should be exposed.

## Validation

- `scripts/verify-plugin-api.sh --summary`
- `mvn -B -DskipTests package`
- `mvn -B test`
