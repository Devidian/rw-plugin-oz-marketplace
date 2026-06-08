# Roadmap Plan 04 Marketplace UI Persistence And Variants

## Objective
Fix Marketplace overlay layout issues, correct item display and variant identity, simplify market-zone administration, and verify runtime state is safely persisted per world.

## Ownership
Primary repository: `rw-plugin-oz-marketplace`

Supporting repositories:
- `rw-plugin-oz-tools` for shared UI, settings, i18n, and persistence conventions.
- `rw-plugin-oz-wallet` for listing purchase payments and seller payouts.

## Dependencies
- Hard runtime dependency: `rw-plugin-oz-tools`.
- Functional runtime dependency: `rw-plugin-oz-wallet`.
- Zone deletion behavior needs migration review because local listings are converted to global listings.

## Phases
- [x] Phase 1: Run migration review for market-zone deletion and any remaining JSON/runtime persistence.
- [x] Phase 2: Fix button overlap in `Angebot bestätigen` and `Marktzone löschen` dialogs.
- [x] Phase 3: Display derived item names instead of raw item ids in table and card views, with uppercase first letter and `[name]-[variant]` format when variant is non-zero.
- [x] Phase 4: Fix objectkit/object variants so beds, pirate chests, and similar variant-backed objects are not incorrectly merged.
- [x] Phase 5: Resolve variant-specific icons through the variant where available.
- [x] Phase 6: Rework the Verwaltung tab state: show only `Marktzone erstellen` outside a market zone.
- [x] Phase 7: Rework the Verwaltung tab state inside a market zone: name sync, global trade toggle, numeric fee input plus `Neue Gebühr setzen`, and `Marktzone auflösen`.
- [x] Phase 8: Replace the three fee buttons with a numeric-only input and explicit set button.
- [x] Phase 9: Enable zone deletion only while currently inside a Marketplace zone and convert local listings to global listings on deletion.
- [x] Phase 10: Add Plan 04 player shortcut visibility setting, document the Escape-close API limitation, verify i18n loading, SQLite persistence audit, and migration away from deprecated Tools `SQLite` usage if present.
- [x] Phase 11: Update README/HISTORY and validate.

## Risks
- Variant identity bugs can lose or merge distinct item stacks if listing keys are not changed carefully.
- Zone deletion can strand local listings unless conversion to global is transactional.
- Derived display names are temporary until internal game translations are accessible.
- Admin tab simplification must not remove needed zone controls for admins standing inside the relevant area.

## Progress Notes
- Zone deletion now promotes active local listings to global listings in the same transaction as deleting the zone row.
- Deleting a missing zone now reports `Market zone not found` instead of returning success.
- Zone deletion is now current-zone scoped for both the management UI path and `/market zone delete`; an optional command id only acts as a guard for the current zone.
- Validation after the current-zone delete guard passed with `mvn -B test -f rw-plugin-oz-marketplace/pom.xml` and `mvn -B -DskipTests package -f rw-plugin-oz-marketplace/pom.xml`.
- Confirmation dialog buttons now use non-overlapping positions for listing, purchase, cancellation, sale removal, and zone deletion dialogs.
- Listing and sale rows/cards now show derived item labels such as `Woodbeam-2` instead of raw `woodbeam:2` identifiers.
- Listing identity and inventory removal remain based on item definition name plus item variant; card icons resolve through `ItemDefinition.getIcon(variant)`.
- The Management tab now shows only zone creation outside a market zone. Inside a market zone it shows name sync, global-mode toggle, numeric fee input with explicit set action, and current-zone dissolve.
- Marketplace now registers player-aware shortcut visibility with Tools and exposes the player setting through the Marketplace settings page.
- Marketplace standalone overlay registration now uses explicit close controls pending Rising World API support.
- No deprecated Tools `SQLite` usage is present; persistence opens the world-scoped connection through `SQLiteConnectionFactory`.
- README and HISTORY document the Plan 04 Marketplace scope.
- Final Marketplace validation passed with `mvn -B test -f rw-plugin-oz-marketplace/pom.xml` and `mvn -B -DskipTests package -f rw-plugin-oz-marketplace/pom.xml`.

## Validation Strategy
- Run `mvn -B test` and `mvn -B -DskipTests package`.
- Runtime-smoke confirmation dialogs, market-zone admin states, numeric fee input, zone deletion conversion, card/table item names, variant grouping, variant icons, shortcut visibility, and explicit close controls.

## Affected Repositories/Plugins
- `rw-plugin-oz-marketplace`
- `rw-plugin-oz-wallet`
- `rw-plugin-oz-tools`

## Rollback Considerations
Keep display-name changes separate from listing identity migration where possible. Zone deletion should be guarded behind confirmation and should not remove listings unless conversion succeeds.
