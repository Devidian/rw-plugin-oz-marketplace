# Roadmap Plan 02 Marketplace UI And Trade Rules

## Objective
Simplify Marketplace entry points, fix global/local trade availability rules, move administration into the overlay, and align market zones with existing Rising World Areas.

## Ownership
Primary repository: `rw-plugin-oz-marketplace`.

Supporting repositories:
- `rw-plugin-oz-tools` provides shared indicators, dynamic tabs, settings UI, and info/status panels.
- `rw-plugin-oz-wallet` remains required for functional trading.

## Dependencies
- Tools dynamic tab support should land before hiding unavailable Marketplace tabs.
- Tools shared indicator registration should land before replacing the local Marketplace zone indicator.
- Marketplace area persistence changes require migration review.

## Work Packages
- [x] Package 1: Change `/mp` to open `MarketplaceOverlay`; keep subcommands only where they remain useful as admin/debug fallback.
- [x] Package 2: Replace the current zone indicator with an icon registered in the shared Tools indicator panel.
- [x] Package 3: Fix global listing creation when zone-only mode is off.
- [x] Package 4: Implement clarified trade rules: zone-only on means all trading only in market zones; zone-only off means global trading everywhere and local trading only in market zones.
- [x] Package 5: Change market-zone global override to tri-state: explicit deny, use global setting, explicit allow.
- [x] Package 6: Hide or disable the `Local` tab when the player is not in a Marketplace zone, using Tools dynamic tab support.
- [x] Package 7: Add an admin `Management` tab in the Marketplace overlay for current-area Marketplace creation, name sync, settings changes, save, and delete with confirmation.
- [x] Package 8: Remove Marketplace zone administration entries from the radial menu after the overlay admin tab exists.
- [x] Package 9: Keep the `/ozt` Marketplace entry to one Marketplace-open action; standardized Info & Status remains deferred to Package 16.
- [x] Package 10: Hide selling when both global and local trading are disabled, and show an explanatory message instead.
- [x] Package 11: Hide selling in zone-only mode when the player is not standing in a Marketplace zone, and show an explanatory message.
- [x] Package 12: Keep the zone indicator hidden when local and global trading are disabled unless the current market zone explicitly allows global trading.
- [x] Package 13: Show Wallet's default currency in the sell form currency field when no currency was entered.
- [x] Package 14: Replace `Standard` wording in confirmation overlays with the actual Wallet default currency label.
- [x] Package 15: Change market zones to apply to whole existing Rising World Areas, matching the Shop model, and disallow creation outside existing Areas.
- [x] Package 16: Add Marketplace info/status panel content and redirect existing info/status commands to the shared Tools panel.
- [x] Package 17: Complete Marketplace logger cleanup, settings metadata coverage, grouped settings labels, numeric input behavior, and i18n labels.

## Progress Notes
- Packages 1-15 are complete for Root Step 5: Marketplace now opens the overlay from `/mp` and `/ozt`, uses the shared Tools indicator provider, applies clarified local/global/zone-only trade rules, supports tri-state global zone modes, hides unavailable tabs through shared dynamic tabs, and moves current-Area administration into the overlay with delete confirmation.
- Root Step 8 logger cleanup is complete for Marketplace: settings logging now routes through the main `OZ.Marketplace` logger.
- Root Step 9 settings cleanup is complete for Marketplace: all safe defaults are exposed, settings are grouped, and English/German setting labels are present.
- Package 16 is complete for Root Step 10: Marketplace now registers a shared Tools Info/Status provider and routes `/mp status` and `/mp info` to the shared panel.

## Risks
- Area-based market zones can require data migration from chunk-range zones.
- Trade-rule changes affect purchase/listing availability and need precise runtime tests.
- Tri-state global overrides must be represented clearly in config, persistence, UI, and i18n.
- Command simplification should not remove necessary admin recovery paths before overlay administration is stable.

## Validation Strategy
- Verify `/mp` opens the overlay.
- Verify local/global tab visibility in no-zone, market-zone, and zone-only contexts.
- Verify global listing creation in zone-only off mode outside market zones.
- Verify tri-state zone global behavior for explicit deny, default, and explicit allow.
- Verify selling disabled states show clear panel messages.
- Verify Wallet default currency appears in form and confirmation text.
- Verify area-only market-zone creation and migration behavior.
- Run Maven package and tests.

## Affected Repositories/Plugins
- `rw-plugin-oz-marketplace`
- `rw-plugin-oz-tools`
- `rw-plugin-oz-wallet`

## Rollback Considerations
Keep persistence migrations additive where possible. Retain command fallback paths until overlay administration and area migration are verified.
