# Default-currency fee settlement hotfix

## Objective

Restore Marketplace purchases for default-currency listings when fees are routed to Wallet's world account.

## Ownership and dependencies

- Owner: `rw-plugin-oz-marketplace`
- Dependency: `rw-plugin-oz-wallet` 0.7.0 public account-transfer API via OZ Tools WalletBridge
- Supporting repository: root forum portfolio only

## Plan

- [x] Confirm the production Wallet error and affected Marketplace version.
- [x] Resolve the implicit listing currency before the world-account fee transfer.
- [x] Cover default and explicit currency resolution with a focused unit test.
- [x] Run API, test, package, and portfolio validation.
- [x] Publish patch release `v0.3.4` and deploy it to production.

## Risk and rollback

The change affects only the fee transfer currency argument. It is database and configuration compatible. Roll back by restoring the prior Marketplace plugin artifact; no migration is involved.
