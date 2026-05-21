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
```

Marketplace fees are charged to buyers on top of the listing price and removed from the economy. Sellers receive the listed price. The applied fee is the higher value of the percent fee and the configured minimum fee.
`marketZoneOnlyMode=true` disables listing discovery outside market zones.
`showMarketplaceZoneIndicator=true` shows a compact HUD indicator while players are inside a market zone. It is positioned below LandClaim's area-info overlay and below the Shop zone indicator so the area-state indicators can stack predictably.

## Player Commands

- `/mp` or `/mp list`: list listings visible at the current market zone, or global listings when outside a zone unless zone-only mode is enabled
- `/mp sell <item> <variant> <amount> <price> [currency] [global]`: create a listing from matching inventory items
- `/mp buy <listing-id>`: buy a visible listing through Wallet
- `/mp cancel <listing-id>`: cancel your own listing and return the item
- `/mp sales`: show your latest visible sale payouts

An empty or omitted `currency` uses Wallet's configured default currency.

## Player Marketplace UI

Players can open the Marketplace radial menu and use `Create listing` to open the Marketplace overlay.

The `Sell` tab scans the player's inventory, groups sellable rows by item definition name and variant, and shows the available amount. Selecting a row fills a listing form for amount, price, optional currency, and local/global mode. The plugin validates the form and the current market zone before showing a confirmation dialog. Items are only removed from inventory after the player confirms and the existing listing service accepts the listing.

The `Local` and `Global` tabs show visible listings for the current access context. Disabled marketplace modes are hidden from the overlay. Buying from the UI asks for confirmation before calling the same Wallet-backed purchase flow used by `/mp buy <listing-id>`.

The `Sales` tab shows the seller's latest visible completed sales with item, amount, payout, fee, and market zone. The `Remove` action hides a completed sale from that seller's history after confirmation. Removed sale rows no longer appear in the tab or `/mp sales`, but the raw sale record remains in the database with `seller_hidden_at` set for audit/history retention.

The command-based `/mp sell ...` flow remains available as a fallback and admin-debug path.

## Market Zone Indicator

Players see a compact market-zone indicator while standing inside a configured market zone. The indicator shows the market-zone name, fee percent, and whether the zone allows global trade. Disabling `showMarketplaceZoneIndicator` hides this HUD element without changing marketplace access, listing discovery, or purchases.

## Admin Commands

- `/mp zone set <id> <radiusChunks> <feePercent> <allowGlobal> [label]`: create or update a market zone centered on the admin's current chunk
- `/mp zone list`: list market zones
- `/mp zone delete <id>`: remove a market zone

## Admin Radial Menu

Admins can open the Marketplace plugin menu and use `Manage market zones` to:

- create or update a market zone at the current chunk
- sync the zone name from the current Area name, or from the current chunk when no Area exists
- toggle whether the current zone allows global listings
- set common fee overrides: `0%`, configured default local fee, or `10%`
- delete the current market zone

Use `/mp zone set` when a custom radius, exact fee value, id, or label is needed.

Local listings can only be bought in their source market zone. Global listings are visible in zones where `allowGlobal=true`; outside any zone, `/mp list` shows global listings for discovery when `marketZoneOnlyMode=false`, but buying still requires standing in a market zone.

## Persistence

Marketplace data is stored in the plugin world SQLite database through `rw-plugin-oz-tools` connection helpers:

- `marketplace_zones`
- `marketplace_listings`
- `marketplace_sales`

The v2 schema is additive and can be left in place when disabling the plugin. Existing v1 databases are migrated by adding `marketplace_sales.seller_hidden_at BIGINT NOT NULL DEFAULT 0`.

## Validation

- `scripts/verify-plugin-api.sh --summary`
- `mvn -B -DskipTests package`
- `mvn -B test`
