# Listings and settlement
<!-- codemap-id: rw-plugin-oz-marketplace/trading -->

## Responsibility

Own listing lifecycle, market zones, trade state and sale history. Wallet owns balances and Mail owns attachment delivery. Wanted fulfillment reserves the listing and records settlement together before taking custody or charging. Uncertain fulfillment keeps the listing and its durable settlement stage pending for review.

## Source and tests

- [`MarketplaceService`](../../src/de/omegazirkel/risingworld/marketplace/MarketplaceService.java) — trades, settlement and Mail integration.
- [`MarketplaceDatabase`](../../src/de/omegazirkel/risingworld/marketplace/MarketplaceDatabase.java) — listing and sale SQLite.
- [`WalletBridge`](../../src/de/omegazirkel/risingworld/marketplace/WalletBridge.java) — Wallet integration.
- [`MarketplaceListing`](../../src/de/omegazirkel/risingworld/marketplace/MarketplaceListing.java) — listing model.
- [Tests](../../src-test/de/omegazirkel/risingworld/marketplace/) cover repository behavior. [Wanted settlement tests](../../src-test/de/omegazirkel/risingworld/marketplace/MarketplaceServiceWantedListingTest.java) cover compensation of confirmed Wallet steps. [Wanted recovery tests](../../src-test/de/omegazirkel/risingworld/marketplace/MarketplaceWantedRecoveryTest.java) cover rollback of reservation on journal failure and the pending record after database reopen.
- [Repository index](../README.md) and [settings defaults](../../src/settings.default.json).

## Dependencies

- `rw-plugin-oz-wallet/ledger` — runtime settlement using Wallet API. [Integration source](../../src/de/omegazirkel/risingworld/marketplace/MarketplaceService.java)
- `rw-plugin-oz-mail/delivery` — attachment delivery via Tools MailBridge. [Integration source](../../src/de/omegazirkel/risingworld/marketplace/MarketplaceService.java)
- `rw-plugin-oz-shop/api` — optional capacity offer registration. [Integration source](../../src/de/omegazirkel/risingworld/marketplace/MarketplaceCapacityShopIntegration.java)
