# Roadmap Plan 03 Marketplace UI And Currency Validation

## Objective
Improve Marketplace selling and listing workflows with valid Wallet currency selection, card-first item/listing views, visible fee information, default-currency balance context, and seller cancellation from buyer-facing views.

## Ownership
Primary repository: `rw-plugin-oz-marketplace`

Supporting repositories:
- `rw-plugin-oz-wallet` for available-currency API and default-currency balance data.
- `rw-plugin-oz-tools` for reusable UI controls if needed.

## Dependencies
- Hard runtime dependency: `rw-plugin-oz-tools`.
- Functional runtime dependency: `rw-plugin-oz-wallet`.
- Wallet currency-list API should be available before replacing free-text currency entry with select/dropdown behavior.

## Phases
- [x] Phase 1: Validate listing currency identifiers against Wallet currencies in both command and UI flows; reject unknown currencies without removing inventory.
- [x] Phase 2: Replace or supplement free-text currency entry in the UI with a currency select/dropdown sourced from Wallet, defaulting to the Wallet default currency.
- [x] Phase 3: Show the player's standard/default-currency balance in the Marketplace overlay footer or equivalent compact status area.
- [x] Phase 4: Add seller cancellation from Local/Global listing views where other players buy, returning items through the existing cancellation service flow.
- [x] Phase 5: Show fee percent and calculated fee behind/near listing prices in list, card, and confirmation views.
- [x] Phase 6: Rework the Sell flow to an inventory icon-gallery/card grid with amount display, item selection, sell overlay/form, and final confirmation panel.
- [x] Phase 7: Add card/table layout switching for Local and Global tabs, with card layout as the default and player preference persistence where appropriate.
- [x] Phase 8: Add a radial-menu Info/Status button in the Marketplace main menu.
- [x] Phase 9: Update README/HISTORY and validate.

## Risks
- Listing creation must remain transactional: invalid currency, failed inventory removal, and failed persistence must not lose items.
- Seller cancellation from buyer-facing views must not allow cancelling completed or reserved listings.
- Fee display must match the actual fee charged by `MarketplaceService`.
- Card/table preferences should not conflict with existing Shop layout preference keys.

## Validation Strategy
- Run `scripts/verify-plugin-api.sh --summary`.
- Run `mvn -B -DskipTests package`.
- Run `mvn -B test`.
- Runtime-smoke invalid currency rejection, valid non-default currency listing, default-currency listing, UI cancellation item return, buyer purchase fee display, local/global tab layout switching, and missing Wallet behavior.

## Affected Repositories/Plugins
- `rw-plugin-oz-marketplace`
- `rw-plugin-oz-wallet`
- `rw-plugin-oz-tools`

## Rollback Considerations
Keep command flows available as fallback. UI card/list preference changes can be rolled back without changing listing persistence if service-level validation remains additive.

## Progress Notes
- Phase 1 complete: `MarketplaceService.createListing` now validates the effective currency against Wallet's public currency list before checking/removing inventory. Unknown currencies fail without touching the seller inventory.
- Phase 2 complete: the Sell form uses a Wallet-backed currency dropdown with Wallet default currency selected by default.
- Phase 3 complete: the Marketplace overlay footer shows the player's default-currency Wallet balance.
- Phase 4 complete: seller-owned rows in Local/Global listing views now show a cancel action that confirms and uses the existing cancellation/item-return service flow.
- Phase 5 complete: table rows, card rows, and purchase confirmation show calculated buyer fee, fee percent, and total from `MarketplaceService`.
- Phase 6 complete: the Sell tab now presents inventory candidates as icon cards with amount/variant context and keeps the existing confirmation step before listing creation.
- Phase 7 complete: Local and Global tabs support card/table layout switching, default to cards, and persist the player's preference through Tools player settings.
- Phase 8 complete: the Marketplace radial menu includes an Info/Status action using the shared Tools `icon-ki-info-status` asset.
- README/HISTORY were updated. Validation passed with `mvn -B test` and `mvn -B -DskipTests package`.
