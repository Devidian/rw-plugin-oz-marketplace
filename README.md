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
`marketZoneOnlyMode=true` requires players to stand in a market zone for both local and global trading. When it is `false`, global trading works outside market zones while local trading still requires a market zone.
`showMarketplaceZoneIndicator=true` shows the Marketplace icon in the shared Tools indicator bar while players are inside an active market zone.

## Player Commands

- `/mp`: open the Marketplace overlay
- `/mp list`: list listings visible at the current market zone, or global listings when outside a zone unless zone-only mode is enabled
- `/mp sell <item> <variant> <amount> <price> [currency] [global]`: create a listing from matching inventory items
- `/mp buy <listing-id>`: buy a visible listing through Wallet
- `/mp cancel <listing-id>`: cancel your own listing and return the item
- `/mp sales`: show your latest visible sale payouts
- `/mp status` or `/mp info`: open the shared Tools Info/Status panel

An empty or omitted `currency` uses Wallet's configured default currency.

## Player Marketplace UI

Players can open the Marketplace overlay through `/mp` or the Marketplace entry in `/ozt`.

The `Sell` tab scans the player's inventory, groups sellable rows by item definition name and variant, and shows the available amount. Selecting a row fills a listing form for amount, price, Wallet default currency, and local/global mode. Local listings require the current market zone. Global listings can be created outside market zones when zone-only mode is disabled. Items are only removed from inventory after the player confirms and the existing listing service accepts the listing.

The `Local` and `Global` tabs show visible listings for the current access context. The `Local` tab is hidden outside market zones, and disabled marketplace modes are hidden from the overlay. Buying from the UI asks for confirmation before calling the same Wallet-backed purchase flow used by `/mp buy <listing-id>`.

The `Sales` tab shows the seller's latest visible completed sales with item, amount, payout, fee, and market zone. The `Remove` action hides a completed sale from that seller's history after confirmation. Removed sale rows no longer appear in the tab or `/mp sales`, but the raw sale record remains in the database with `seller_hidden_at` set for audit/history retention.

Admins see a `Management` tab for the current Rising World Area. It creates or updates the area as a market zone, syncs the zone name from the Area name, cycles global-trade mode between `default`, `allow`, and `deny`, sets common fee values, and deletes the current zone with confirmation.

The command-based `/mp sell ...` flow remains available as a fallback and admin-debug path.

## Market Zone Indicator

Players see the Marketplace icon in the shared Tools indicator bar while standing inside a configured market zone. Disabling `showMarketplaceZoneIndicator` hides this HUD signal without changing marketplace access, listing discovery, or purchases. The indicator stays hidden when both local and global trading are disabled unless the current zone explicitly allows global trade.

## Admin Commands

- `/mp zone set <id> <radiusChunks> <feePercent> <globalMode> [label]`: create or update a legacy chunk-range market zone centered on the admin's current chunk. `globalMode` accepts `deny`, `default`, or `allow`.
- `/mp zone list`: list market zones
- `/mp zone delete <id>`: remove a market zone

New UI-managed market zones apply to whole existing Rising World Areas. Area-zone creation is blocked unless the admin is standing inside an Area. The legacy `/mp zone set` command remains for admin recovery and explicit chunk-range maintenance.

Local listings can only be bought in their source market zone. Global listings are visible and purchasable outside zones when `marketZoneOnlyMode=false`; inside zones they follow the zone's global mode: explicit `deny`, global setting `default`, or explicit `allow`.

## Persistence

Marketplace data is stored in the plugin world SQLite database through `rw-plugin-oz-tools` connection helpers:

- `marketplace_zones`
- `marketplace_listings`
- `marketplace_sales`

The v3 schema is additive and can be left in place when disabling the plugin. Existing databases are migrated by adding `marketplace_sales.seller_hidden_at`, `marketplace_zones.area_id`, and `marketplace_zones.global_trade_mode`. Existing boolean global zone flags are mapped to explicit `allow` or `deny`.

## Validation

- `scripts/verify-plugin-api.sh --summary`
- `mvn -B -DskipTests package`
- `mvn -B test`
