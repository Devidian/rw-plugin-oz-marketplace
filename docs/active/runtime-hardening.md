# Runtime Hardening

## Objective
Stabilize the command-first Marketplace MVP before richer UI workflows start.

## Ownership
Owning repository/plugin: `rw-plugin-oz-marketplace`
Supporting repositories/plugins: `rw-plugin-oz-tools`, `rw-plugin-oz-wallet`

## Dependencies
- Runtime: `OZTools`, `OZWallet`, `OZMarketplace`
- Build: Maven with Java 20 and the existing Rising World `PluginAPI.jar`
- Optional integrations: none

## Risks
- Wallet, inventory, and SQLite updates cannot be one single transaction; Marketplace must reserve listings before external transfers and log admin-review cases when finalization fails after external transfer.
- Runtime smoke testing still requires a reachable Rising World server/container and cannot be fully replaced by Maven tests.

## Validation Strategy
- [x] `mvn -B -DskipTests package`
- [x] `mvn -B test` when tests exist
- [ ] Runtime smoke test with Tools, Wallet, and Marketplace installed

## Affected Repositories/Plugins
- `rw-plugin-oz-marketplace`

## Rollback Considerations
Reverting this phase restores the command-first MVP behavior, but removes atomic listing reservation for buy/cancel flows. Existing SQLite data remains compatible because no schema migration is introduced.

## Implementation Checklist
- [x] Verify first Marketplace package implementation status
- [x] Add atomic listing reservation for purchases
- [x] Add atomic listing reservation for cancellations
- [x] Return seller inventory if listing creation fails after item removal
- [x] Align fee flow with root roadmap: buyer pays listing price plus fee, seller receives listing price
- [x] Keep package/install scope limited to Tools, Wallet, and Marketplace
- [x] Update HISTORY
- [ ] Complete runtime smoke test
