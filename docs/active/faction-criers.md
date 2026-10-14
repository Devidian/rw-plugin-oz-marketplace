# Faction market criers

## Objective and constraints

- [x] Persist `factionId` with an additive migration; existing criers use `0`.
- [x] Enforce leader or officer role and faction crier capacity in server paths.
- [x] Allow public offers and wanted listings; route fees to the faction account and wanted payouts from the requester.
- [x] Preserve active-listing removal safety and expose faction NPC counts.
- [x] Give faction gender choices distinct menu icons.
- [x] Use native confirmations for crier creation, zone conversion and dissolution; keep both NPC-retention outcomes and the existing active-listing guard.

## Validation and rollout

Test migration, roles, capacity, public listing, fee and wanted settlement, and existing criers. Run API checks, Maven tests and package. Back up Marketplace database before Development upload and inspect logs and player flows afterward.

Java 25 package and 33 tests passed on 2026-10-14. Development startup and `faction_id` migration were checked; interactive listings and settlement remain open.

The faction-icon follow-up passed the same 33 tests. Development reloaded Marketplace at 16:43 UTC and registered all four new Classic/Modern faction crier textures. The current Marketplace database, jar, and JSON files were backed up at `development-server/backups/faction-npc-menu-20261014-164133/OZMarketplace`. Confirm the icons in the player menu.

Native dialog follow-up on 2026-10-14: radial creation (personal, global and faction), zone conversion, and crier dissolution now use native game message boxes. The dissolve dialog maps Yes to link plus NPC deletion, No to link removal only, and Cancel to no change; the existing listing-return guard remains server-side. 33 tests, the Java 25 package, JSON, PluginAPI and diff checks passed. Development Marketplace was backed up at `development-server/backups/npc-native-dialogs-20261014-193203/OZMarketplace` before uploading only Marketplace and Service NPC. The uploaded JAR hash matched the isolated build; Development logged Marketplace enabled and `RELOADED ALL PLUGINS` at 19:33 UTC. The dialog choices need player verification. Roll back with the Marketplace JAR and i18n files from that backup while retaining database state.

## Rollback

Remove faction criers and settle their open listings before restoring an older jar. Restore the backed-up database if the additive migration must be reversed.
