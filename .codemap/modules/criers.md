# Market criers and exports
<!-- codemap-id: rw-plugin-oz-marketplace/criers -->

## Responsibility

Manage market zones and criers, player UI and export endpoints.

## Source and tests

- [`MarketCrierService`](../../src/de/omegazirkel/risingworld/marketplace/MarketCrierService.java) — crier management.
- [`MarketplaceOverlay`](../../src/de/omegazirkel/risingworld/marketplace/ui/MarketplaceOverlay.java) — player UI.
- [`MarketplaceExportService`](../../src/de/omegazirkel/risingworld/marketplace/exports/MarketplaceExportService.java) — market exports and one batched active-listing query for crier destinations.
- [`MarketplaceExportRoute`](../../src/de/omegazirkel/risingworld/marketplace/web/MarketplaceExportRoute.java) — export route.
- [Export tests](../../src-test/de/omegazirkel/risingworld/marketplace/exports/MarketplaceExportServiceTest.java) cover ordering, empty criers, response JSON and bounded SQL reads.
- [Repository index](../README.md) and [settings defaults](../../src/settings.default.json).
