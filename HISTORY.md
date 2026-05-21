# History

## Unreleased

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

## [0.1.0] - 2026-05-20

- feat: create OZ Marketplace from the Maven template
- feat: add market zones with fee overrides and global-trade flags
- feat: add player listings backed by inventory removal
- feat: add Wallet-backed purchases, seller payouts, and economy fees
- feat: add seller sale history commands
- docs: document install scope, settings, commands, and persistence
