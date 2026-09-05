# Marketplace Crier wanted-listing and UI follow-up

## Objective

Correct wanted-listing funding and endpoint visibility, then make personal and
global Marktschreier funding and identity clear in the in-game overlay.

## Ownership

Owning repository/plugin: `rw-plugin-oz-marketplace`

Supporting repositories/plugins: `rw-plugin-oz-wallet` through its existing
public account-transfer bridge only; no direct Wallet persistence access.

## Dependencies

- Runtime: existing endpoint-aware Crier, listing and Wallet-account services.
- Build: current compatible OZ Tools and Wallet artifacts.
- Optional integrations: none.

## Risks

- A wanted fulfillment must never remove an item or mutate listing quantity if
  the Crier account cannot fund the payout; use the established transaction and
  compensation boundary.
- Local/global endpoint filtering must apply equally to overlay lists, actions,
  exports and any cached query path so local listings do not leak through the
  global Crier.
- UI changes remain server-authorized; displayed balances are informational and
  never the funding source of truth.

## Validation Strategy

- [ ] Add focused tests: underfunded wanted creation succeeds; an underfunded
  fulfillment fails without inventory/listing/account mutation; partial
  fulfillment is enabled when at least one matching item exists.
- [ ] Test global wanted listings created at a global Crier remain global and
  local wanted listings are excluded from global and unrelated-endpoint views.
- [ ] Test account deposit/withdrawal follows the Shop-NPC player-account flow
  and balance refreshes after each successful transfer.
- [ ] Verify DE/EN UI labels, title/footer identity and disabled/width-adjusted
  fulfillment action in a Development interaction smoke test.
- [ ] Run `mvn -B test` and `mvn -B -DskipTests package`.

## Affected Repositories/Plugins

- `rw-plugin-oz-marketplace`
- `rw-plugin-oz-wallet` (integration validation only; no expected change)

## Rollback Considerations

This is a behavior/UI correction with no planned schema migration. Rollback by
restoring the prior Marketplace artifact. Do not refund or debit balances during
rollback; each settlement remains individually atomic.

## Implementation Checklist

- [ ] Change wanted creation so insufficient Crier account balance does not
  block listing creation. Check funding only in the fulfillment transaction,
  before removing any seller inventory; return a localized no-payout result.
- [x] Preserve global scope when a wanted listing is created through a global
  Crier. Derive endpoint/scope from the resolved Crier, never from a stale or
  default local market context.
- [ ] Apply endpoint-aware visibility consistently: local wanted listings are
  visible only at their own endpoint and permitted local zones/Criers; global
  lists contain global listings only.
- [ ] Add the personal-Crier player-account deposit action using the same
  Wallet-backed UI flow as Shop NPCs, with amount validation and server-side
  ownership checks.
- [ ] Show the Crier Wallet balance in the management/trade UI, refreshed after
  funding, withdrawal, listing fees and fulfillment payouts.
- [ ] Make the wanted-listing fulfillment action wide enough for localized
  `Verkaufen` copy. Disable it when no matching item exists, but allow partial
  fulfillment whenever the player owns at least one requested item.
- [ ] Replace the generic footer title with `Marktschreier: [name]`. Show
  `Dies ist ein Globaler Marktschreier` for global endpoints or `Dies ist ein
  persönlicher Marktschreier von [spielername]` for personal endpoints; add
  equivalent DE/EN localized strings.
- [ ] Update player-facing documentation/history only with the eventual
  released behavior.
