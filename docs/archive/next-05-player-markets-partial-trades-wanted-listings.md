# Next 05: Player markets, partial trades, and wanted listings

## Objective

Implement player-owned markets, partial listing purchases, and partially
fulfillable wanted listings with OZ Mail delivery.

## Ownership and dependencies

Marketplace owns all market and trade rules. OZ Wallet supplies settlement.
OZ Mail supplies durable attachment delivery through the OZ Tools bridge.

## Checklist

- [x] Migrate market owner and listing fulfillment state.
- [x] Enforce `maxPlayerMarketplaces` and `area_addplayer`.
- [x] Repair markets whose linked Area was removed.
- [x] Guard market deletion and route player-market tax to its owner.
- [x] Support partial purchases and proportional remaining totals.
- [x] Support wanted creation, partial fulfillment, Mail capacity checks, and
      withdrawal locking after the first unit.
- [x] Add localized UI and command fallbacks.
- [x] Update README, HISTORY, and `PLANS.md`.

## Risks and rollback

The migration is additive. Disable creation with `maxPlayerMarketplaces=0`.
Do not downgrade while wanted or partially fulfilled listings are active.
Compensation failures across Wallet, inventory, and Mail are logged for review.

## Validation

- [x] Database and pricing tests.
- [x] Architecture and Plugin API checks.
- [x] `mvn -B test`
- [x] `mvn -B -DskipTests package`
- [x] Development-server reload and listener acceptance.
- [x] Correct wanted-listing left spacing and localize result/command chat output
      after live-player feedback.
- [x] Correct purchase-dialog spacing and fee placeholder ordering; add a
      quantity-sensitive localized unit-price preview.
- [x] Live-player transaction and UI acceptance.
