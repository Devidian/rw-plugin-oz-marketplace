# Item editor and label fixes

## Objective and ownership
Inventory listing candidates resolve object-kit names using the same localized object definitions as published listings. Stored item identity and trade behavior are unchanged.

Owned by this plugin, using existing Tools/Wallet APIs where applicable. No dependency or database schema changes.

## Progress
- [x] Implement focused fixes.
- [x] Tests, package, API and entrypoint checks.
- [x] Development reload proof.
- [x] Manual player acceptance in German and English (user confirmed 2026-09-06).

## Risks and rollback
Validate object-kit selection and variant display without changing inventory identity. Restore previous plugin artifacts if needed; persisted data remains compatible. Shop removal metadata is optional and ignored by older readers; Wallet transactions are retained.

## Validation results (2026-09-05)
24 tests passed; package and entrypoint verification passed. Local PluginAPI verification script passed. Development artifact SHA-256 matched; startup/listener registration and complete reload confirmed at 21:09:33 UTC. No new plugin errors were observed. An existing container HTTP healthcheck failure predates the upload; infrastructure diagnosis remains separate.

Implementation and in-game acceptance complete. Patch release: 0.4.1.
