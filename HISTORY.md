# History

## [0.2.7] - 2026-07-20 | Sales table alignment

- fix: align Sales-table row widths with their column headers

## [0.2.6] - 2026-07-20 | Sale-history detail

- feat: show the sold item's condition and buyer in the seller Sales tab
- fix: keep existing sale-history rows readable with an Unknown buyer fallback
- db: preserve item durability, status and modifier in completed sale records

## [0.2.5] - 2026-07-17 | Item-state custody and listing clarity

- fix: preserve item durability, status and modifier for listing creation, purchase, cancellation and rollback
- feat: show durable-item condition in listing cards, tables and confirmation dialogs
- fix: keep withdrawal actions clear of listing prices in cards

## [0.2.4] - 2026-07-17 | Wallet bridge compatibility

- feat: support the current Wallet bridge contract and localize unavailable purchase states

## [0.2.3] - 2026-07-14 | Icon set and export polish

- change: rename Marketplace icon keys to their final semantic names
- feat: add route-ready Marketplace zone and area-offer export DTOs/services with `lastChange` filtering
- feat: add future native route exposure flags for Marketplace zones and offers

## [0.2.1] - 2026-06-13 | Item labels and seller notification

- fix: align item and object-kit labels with Shop default/named variant behavior
- feat: notify online sellers after a Marketplace sale is durably completed

## [0.2.0] - 2026-06-08 | Search and persistence hardening

- feat: add name search controls to Marketplace Local and Global listing tabs
- change: remove background and border from compact Marketplace Wallet balance chips
- fix: move Marketplace Wallet balances above the full overlay panel
- feat: show compact Wallet currency balance chips above the Marketplace overlay body
- feat: add Marketplace shortcut visibility player setting
- fix: widen seller withdraw actions and preserve object item names when listing object items
- feat: simplify the Marketplace management tab with outside-zone create-only state, inside-zone controls, and numeric fee updates
- feat: display Marketplace listings and sales with derived item names and variant suffixes
- fix: prevent confirmation dialog action buttons from overlapping

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
