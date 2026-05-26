# History

## Unreleased

## [0.1.0] - 2026-05-26 | Initial marketplace release

- fix: keep the Marketplace PlayerSettings card within the shared Tools settings width
- fix: replace `PH_PLUGIN_NAME` in the Marketplace PlayerSettings empty state
- change: use the dedicated market-zone shared-indicator icon for market-zone signals
- feat: add a default Marketplace PlayerSettings page
- feat: validate listing currencies against Wallet before removing seller inventory
- feat: replace Marketplace sell-currency text entry with a Wallet-backed currency dropdown
- feat: show default Wallet balance in the Marketplace overlay footer
- feat: show buyer fee amount, fee percent, and total in listing rows/cards and purchase confirmation
- feat: add card-first Local/Global listing views with persisted card/table layout preference
- feat: show sellable inventory candidates as icon cards in the Sell tab
- feat: add Marketplace radial Info/Status menu action with the shared Tools info icon
- feat: allow sellers to cancel their own listings from Local/Global listing views
- feat: add shared Tools Info/Status panel content for Marketplace and route info/status commands to it
- feat: group and localize Marketplace admin settings metadata
- refactor: route Marketplace settings logging through the main `OZ.Marketplace` logger
- feat: open `/mp` and `/ozt` Marketplace directly into the Marketplace overlay
- feat: replace local Marketplace zone HUD overlay with the shared Tools indicator provider
- feat: add Marketplace overlay management tab for Area-based market-zone administration with delete confirmation
- feat: support tri-state zone global trade mode and area-bound market zones with v3 schema migration
- fix: allow global listings and purchases outside market zones when zone-only mode is disabled
- fix: show Wallet default currency in Marketplace sell and confirmation flows
- fix: reuse the Marketplace i18n instance so the zone indicator does not reload language files on every refresh
- feat: add optional market-zone HUD indicator below the LandClaim and Shop area indicators
- feat: add seller Sales overlay tab with seller-side removal for completed-sale rows
- db: migrate Marketplace schema to v2 with seller-hidden sale-history rows
- feat: add Marketplace Local and Global listing tabs with confirmed UI purchase flow
- feat: add player Marketplace overlay with Sell tab, inventory grouping, form validation, and explicit listing confirmation
- refactor: move Marketplace overlay framing and tab styling onto the shared Tools BasePluginOverlayWithTabs
- fix: reserve marketplace listings during purchase and cancellation flows to prevent duplicate completion
- fix: return seller inventory when listing creation fails after item removal
- feat: add local/global marketplace availability, zone-only discovery, split local/global fee settings, and minimum fees
- feat: add admin radial menu for creating, naming, toggling global trade, setting fee presets, and deleting market zones
- fix: charge marketplace fees to buyers on top of listing price so sellers receive the listed price
- docs: start runtime-hardening roadmap task before richer UI work

- feat: create OZ Marketplace from the Maven template
- feat: add market zones with fee overrides and global-trade flags
- feat: add player listings backed by inventory removal
- feat: add Wallet-backed purchases, seller payouts, and economy fees
- feat: add seller sale history commands
- docs: document install scope, settings, commands, and persistence
