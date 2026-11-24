# rw-plugin-oz-marketplace
<!-- codemap-id: rw-plugin-oz-marketplace -->

## Purpose

Player markets, listings, buyer settlement, seller payouts and market criers. [Descriptor](../src/resources/plugin.yml) names [`Marketplace`](../src/de/omegazirkel/risingworld/Marketplace.java); [`MarketplaceRuntime`](../src/de/omegazirkel/risingworld/MarketplaceRuntime.java) handles lifecycle and dispatch.

## Modules

- [Listings and settlement](modules/trading.md).
- [Market criers and exports](modules/criers.md).

## Build and tests

[Maven](../pom.xml) builds against the Rising World API and OZ Tools. Explore [tests](../src-test/de/omegazirkel/risingworld/marketplace/) and follow [repository instructions](../AGENTS.md) for validation.

## Dependencies

- `rw-plugin-oz-tools/shared-api` — build and shared runtime infrastructure. [Build configuration](../pom.xml)
