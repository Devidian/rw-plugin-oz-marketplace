# Next 03: Marketplace item-state custody

## Objective

Preserve durability/state for player listings through creation, purchase and
cancellation.

## Ownership and dependencies

Marketplace owns the listing snapshot and database migration. No shared
runtime API is added.

## Risks and rollback

State fields are additive. Existing listings receive neutral state values.
Inventory/database boundary failures keep the existing reservation safeguards.

## Validation

- [x] Maven build and tests (5 tests).
- [ ] Runtime-check damaged-item listing, purchase and cancellation.

## Checklist

- [x] Capture exact inventory item state on listing creation.
- [x] Persist state and restore it on purchase/cancellation/rollback.
- [x] Update localized UI labels and documentation if required.
