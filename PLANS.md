# PLANS.md

Planning is stored in repository-local docs.

- Completed phase summaries: [docs/phase-archive.md](docs/phase-archive.md)
- Planning and documentation standards: [docs/policies/repository-policy.md](docs/policies/repository-policy.md)

## Step 6 Implementation

- [x] Create repository from `rw-plugin-maven-template`
- [x] Rename Maven, plugin descriptor, settings, and packaging metadata
- [x] Add SQLite schema for zones, listings, and sale history
- [x] Add Wallet-backed purchase and payout flow
- [x] Add local/global listing rules and market-zone commands
- [x] Add player command workflow for listing views, buying, cancellation, and sales
- [x] Update README and HISTORY

## Step 7 Runtime Hardening

- [x] Confirm the first Marketplace package is implemented and builds
- [x] Add listing reservation for purchase and cancellation flows
- [x] Document runtime smoke-test follow-up in [docs/active/runtime-hardening.md](docs/active/runtime-hardening.md)
- [ ] Runtime-test command-first trading with Tools, Wallet, and Marketplace installed

## Root Roadmap Package 2

- [x] Define global/local availability settings
- [x] Define command-name setting with `/mp` default
- [x] Define default local/global fee and minimum-fee settings
- [x] Define market-zone-only discovery mode

## Root Roadmap Package 4

- [x] Add admin radial entry for market-zone management
- [x] Add current chunk zone create/update action
- [x] Add zone name sync action from current Area/chunk
- [x] Add global trade toggle action
- [x] Add fee preset actions
- [x] Add current zone delete action

## Root Roadmap Package 6

- [x] Add player Marketplace overlay from the radial menu
- [x] Add Sell tab with inventory candidates grouped by item definition name and variant
- [x] Add listing form for amount, price, optional currency, and local/global mode
- [x] Add confirmation dialog before inventory removal and listing creation
- [x] Route listing creation through the Marketplace UI
- [x] Validate with `mvn -B test`

## Root Roadmap Package 7

- [x] Move Marketplace overlay onto shared `BasePluginOverlayWithTabs`
- [x] Add `Local` listing tab when local marketplace mode is enabled
- [x] Add `Global` listing tab when global marketplace mode is enabled
- [x] Show only listings visible through existing Marketplace access rules
- [x] Add confirmed UI purchase action backed by existing `/mp buy` service flow
- [x] Keep command-based `/mp list` and `/mp buy` flows unchanged
- [x] Validate with `mvn -B test`

## Root Roadmap Package 9

- [x] Add seller `Sales` tab to the Marketplace overlay
- [x] Add seller-side removal for completed-sale rows without hard-deleting audit data
- [x] Filter `/mp sales` and the UI tab to non-hidden seller sales
- [x] Migrate Marketplace schema to v2 with `marketplace_sales.seller_hidden_at`
- [x] Validate with `mvn -B test`

## Root Roadmap Package 10

- [x] Add optional market-zone HUD indicator
- [x] Position the indicator below LandClaim area info and below the Shop zone indicator
- [x] Show market-zone name, fee percent, and global-trade state
- [x] Add `showMarketplaceZoneIndicator` setting and admin setting entry
- [x] Validate with `mvn -B test`

## Clothing Item Resolution

- [x] Store concrete clothing definition names in new listings
- [x] Match, return, deliver, label, and render icons for concrete clothing items
- [x] Document compatibility and rollback limits for existing generic clothing listings

## Construction Item Resolution

- [x] Store concrete construction definition names and texture variants in new listings
- [x] Match, return, deliver, label, and render icons for concrete construction items
- [x] Preserve construction colors as part of listing custody state
- [x] Document compatibility and rollback limits for existing generic construction listings
