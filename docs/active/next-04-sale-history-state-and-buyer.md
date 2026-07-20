# Next 04: Marketplace sale-history state and buyer

## Objective

Show the sold item's condition and buyer in the seller-facing `Sales` table.
Existing history rows whose condition or buyer cannot be resolved remain
readable and display the localized `unknown` fallback.

## Ownership and dependencies

Marketplace owns the sale snapshot, SQLite migration, player-name resolution,
and Sales-tab presentation. It continues to use the existing Tools UI helpers;
no shared API or new dependency is required.

Affected plugins: `rw-plugin-oz-marketplace` only. Tools and Wallet remain
runtime dependencies but require no change.

## Implementation checklist

- [x] Persist listing item-state fields with each completed sale and migrate
      existing `marketplace_sales` rows using neutral/unknown values.
- [x] Resolve the buyer name for current sales without making sale-history
      rendering fail for deleted or legacy player records.
- [x] Add localized `State`, `Buyer`, and `Unknown` labels and render both
      columns in the Sales table.
- [ ] Add focused migration and sale-history rendering/service tests.
- [ ] Update `README.md` and `HISTORY.md`.

## Risks and rollback

The SQLite change is additive and keeps existing rows. A failed migration must
leave the existing schema usable. Rollback is code-only: old Marketplace
versions ignore the added fields, while the database retains the history data.

## Validation

- [ ] `mvn -B test`
- [ ] `mvn -B -DskipTests package`
- [ ] Runtime-check a new sale, a pre-migration sale, and an unavailable buyer
      record in the Sales tab.
