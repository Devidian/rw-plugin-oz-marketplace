package de.omegazirkel.risingworld.marketplace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MarketplaceDatabase {
    private static final int SCHEMA_VERSION = 8;
    private final Connection connection;

    public enum HideSaleStatus {
        SUCCESS,
        NOT_FOUND,
        WRONG_SELLER
    }

    public MarketplaceDatabase(Connection connection) throws SQLException {
        this.connection = connection;
        initialize();
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS marketplace_crier_locations (
                        endpoint_id TEXT PRIMARY KEY, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
                        rx REAL NOT NULL, ry REAL NOT NULL, rz REAL NOT NULL, rw REAL NOT NULL
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS marketplace_crier_appearances (
                        endpoint_id TEXT PRIMARY KEY, gender TEXT NOT NULL, skin_color INTEGER NOT NULL,
                        hair_color INTEGER NOT NULL, eye_color INTEGER NOT NULL, hairstyle INTEGER NOT NULL,
                        beard INTEGER NOT NULL, variation INTEGER NOT NULL, clothes BLOB NOT NULL
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS marketplace_criers (
                        npc_id BIGINT PRIMARY KEY,
                        endpoint_id TEXT NOT NULL UNIQUE,
                        name TEXT NOT NULL,
                        owner_db_id INTEGER NOT NULL DEFAULT 0,
                        owner_name TEXT NOT NULL DEFAULT '',
                        global_crier INTEGER NOT NULL DEFAULT 0,
                        global_trade_enabled INTEGER NOT NULL DEFAULT 0,
                        shared_listings INTEGER NOT NULL DEFAULT 0,
                        level INTEGER NOT NULL DEFAULT 1,
                        male INTEGER NOT NULL DEFAULT 1,
                        created_at BIGINT NOT NULL,
                        fee_percent INTEGER NOT NULL DEFAULT 5
                    );
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_marketplace_criers_owner
                    ON marketplace_criers(owner_db_id, global_crier, created_at);
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS marketplace_zones (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        min_chunk_x INTEGER NOT NULL,
                        max_chunk_x INTEGER NOT NULL,
                        min_chunk_y INTEGER NOT NULL,
                        max_chunk_y INTEGER NOT NULL,
                        min_chunk_z INTEGER NOT NULL,
                        max_chunk_z INTEGER NOT NULL,
                        area_id BIGINT NOT NULL DEFAULT 0,
                        fee_percent INTEGER NOT NULL,
                        allow_global_trade INTEGER NOT NULL DEFAULT 0,
                        global_trade_mode INTEGER NOT NULL DEFAULT 1,
                        created_at BIGINT NOT NULL,
                        owner_db_id INTEGER NOT NULL DEFAULT 0,
                        owner_name TEXT NOT NULL DEFAULT '',
                        owner_area_permission TEXT NOT NULL DEFAULT ''
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS marketplace_listings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        seller_db_id INTEGER NOT NULL,
                        seller_name TEXT NOT NULL,
                        item_name TEXT NOT NULL,
                        item_variant INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        item_durability INTEGER NOT NULL DEFAULT 0,
                        item_status INTEGER NOT NULL DEFAULT 0,
                        item_modifier TEXT NOT NULL DEFAULT '',
                        item_color INTEGER NOT NULL DEFAULT 0,
                        price BIGINT NOT NULL,
                        currency_identifier TEXT NOT NULL,
                        market_zone_id TEXT,
                        global_listing INTEGER NOT NULL DEFAULT 0,
                        created_at BIGINT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        listing_type TEXT NOT NULL DEFAULT 'OFFER',
                        original_amount INTEGER NOT NULL DEFAULT 0,
                        fulfilled_amount INTEGER NOT NULL DEFAULT 0,
                        original_price BIGINT NOT NULL DEFAULT 0
                    );
                    """);
            ensureColumn(statement, "marketplace_listings", "item_durability", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(statement, "marketplace_listings", "item_status", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(statement, "marketplace_listings", "item_modifier", "TEXT NOT NULL DEFAULT ''");
            ensureColumn(statement, "marketplace_listings", "item_color", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(statement, "marketplace_listings", "listing_type", "TEXT NOT NULL DEFAULT 'OFFER'");
            ensureColumn(statement, "marketplace_listings", "original_amount", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(statement, "marketplace_listings", "fulfilled_amount", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(statement, "marketplace_listings", "original_price", "BIGINT NOT NULL DEFAULT 0");
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_marketplace_listings_active
                    ON marketplace_listings(status, market_zone_id, global_listing, created_at DESC);
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS marketplace_sales (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        listing_id INTEGER NOT NULL,
                        seller_db_id INTEGER NOT NULL,
                        buyer_db_id INTEGER NOT NULL,
                        item_name TEXT NOT NULL,
                        item_variant INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        item_durability INTEGER NOT NULL DEFAULT 0,
                        item_status INTEGER NOT NULL DEFAULT 0,
                        item_modifier TEXT NOT NULL DEFAULT '',
                        item_color INTEGER NOT NULL DEFAULT 0,
                        price BIGINT NOT NULL,
                        currency_identifier TEXT NOT NULL,
                        fee BIGINT NOT NULL,
                        seller_payout BIGINT NOT NULL,
                        market_zone_id TEXT,
                        sold_at BIGINT NOT NULL,
                        seller_hidden_at BIGINT NOT NULL DEFAULT 0
                    );
                    """);
            migrate(statement);
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION + ";");
        }
    }

    private void ensureColumn(Statement statement, String table, String column, String definition) throws SQLException {
        try (ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) if (column.equalsIgnoreCase(result.getString("name"))) return;
        }
        statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private void migrate(Statement statement) throws SQLException {
        ensureColumn(statement, "marketplace_sales", "item_durability", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "marketplace_sales", "item_status", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "marketplace_sales", "item_modifier", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(statement, "marketplace_sales", "item_color", "INTEGER NOT NULL DEFAULT 0");
        if (!columnExists("marketplace_sales", "seller_hidden_at")) {
            statement.execute("""
                    ALTER TABLE marketplace_sales
                    ADD COLUMN seller_hidden_at BIGINT NOT NULL DEFAULT 0;
                    """);
        }
        if (!columnExists("marketplace_zones", "area_id")) {
            statement.execute("""
                    ALTER TABLE marketplace_zones
                    ADD COLUMN area_id BIGINT NOT NULL DEFAULT 0;
                    """);
        }
        if (!columnExists("marketplace_zones", "global_trade_mode")) {
            statement.execute("""
                    ALTER TABLE marketplace_zones
                    ADD COLUMN global_trade_mode INTEGER NOT NULL DEFAULT 1;
                    """);
            statement.execute("""
                    UPDATE marketplace_zones
                    SET global_trade_mode = CASE WHEN allow_global_trade = 1 THEN 2 ELSE 0 END;
                    """);
        }
        ensureColumn(statement, "marketplace_zones", "owner_db_id", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "marketplace_zones", "owner_name", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(statement, "marketplace_zones", "owner_area_permission", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(statement, "marketplace_criers", "fee_percent", "INTEGER NOT NULL DEFAULT 5");
        statement.execute("""
                UPDATE marketplace_listings
                SET original_amount = amount
                WHERE original_amount <= 0;
                """);
        statement.execute("""
                UPDATE marketplace_listings
                SET original_price = price
                WHERE original_price <= 0;
                """);
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ");");
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public void upsertZone(MarketZone zone) throws SQLException {
        String sql = """
                INSERT INTO marketplace_zones(
                    id, name, min_chunk_x, max_chunk_x, min_chunk_y, max_chunk_y, min_chunk_z, max_chunk_z,
                    area_id, fee_percent, allow_global_trade, global_trade_mode, created_at, owner_db_id, owner_name,
                    owner_area_permission)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name=excluded.name,
                    min_chunk_x=excluded.min_chunk_x,
                    max_chunk_x=excluded.max_chunk_x,
                    min_chunk_y=excluded.min_chunk_y,
                    max_chunk_y=excluded.max_chunk_y,
                    min_chunk_z=excluded.min_chunk_z,
                    max_chunk_z=excluded.max_chunk_z,
                    area_id=excluded.area_id,
                    fee_percent=excluded.fee_percent,
                    allow_global_trade=excluded.allow_global_trade,
                    global_trade_mode=excluded.global_trade_mode,
                    owner_db_id=excluded.owner_db_id,
                    owner_name=excluded.owner_name,
                    owner_area_permission=excluded.owner_area_permission;
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            writeZone(statement, zone);
            statement.executeUpdate();
        }
    }

    public List<MarketZone> listZones() throws SQLException {
        List<MarketZone> zones = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM marketplace_zones ORDER BY id ASC;
                """);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                zones.add(readZone(result));
            }
        }
        return zones;
    }

    public Optional<MarketZone> findZone(String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM marketplace_zones WHERE id = ?;
                """)) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(readZone(result));
                }
            }
        }
        return Optional.empty();
    }

    public ZoneDeleteResult deleteZoneIfEmpty(String id) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int activeListings;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT COUNT(*) FROM marketplace_listings
                    WHERE market_zone_id = ? AND status NOT IN ('SOLD', 'CANCELLED');
                    """)) {
                statement.setString(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    activeListings = result.next() ? result.getInt(1) : 0;
                }
            }
            if (activeListings > 0) {
                connection.rollback();
                return new ZoneDeleteResult(false, activeListings);
            }
            int deletedZones;
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM marketplace_zones WHERE id = ?;")) {
                statement.setString(1, id);
                deletedZones = statement.executeUpdate();
            }
            connection.commit();
            return new ZoneDeleteResult(deletedZones > 0, 0);
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public long createListing(MarketplaceListing listing) throws SQLException {
        String sql = """
                INSERT INTO marketplace_listings(
                    seller_db_id, seller_name, item_name, item_variant, amount, item_durability, item_status,
                    item_modifier, item_color, price, currency_identifier,
                    market_zone_id, global_listing, created_at, status, listing_type, original_amount,
                    fulfilled_amount, original_price)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?);
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, listing.sellerDbId());
            statement.setString(2, listing.sellerName());
            statement.setString(3, listing.itemName());
            statement.setInt(4, listing.itemVariant());
            statement.setInt(5, listing.amount());
            statement.setInt(6, listing.itemState().durability());
            statement.setShort(7, listing.itemState().status());
            statement.setString(8, listing.itemState().modifier());
            statement.setInt(9, listing.itemState().color());
            statement.setLong(10, listing.price());
            statement.setString(11, listing.currencyIdentifier());
            statement.setString(12, listing.marketZoneId());
            statement.setInt(13, listing.globalListing() ? 1 : 0);
            statement.setLong(14, listing.createdAt());
            statement.setString(15, listing.listingType());
            statement.setInt(16, listing.originalAmount());
            statement.setInt(17, listing.fulfilledAmount());
            statement.setLong(18, listing.originalPrice());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public Optional<MarketplaceListing> findActiveListing(long listingId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM marketplace_listings WHERE id = ? AND status = 'ACTIVE';
                """)) {
            statement.setLong(1, listingId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(readListing(result));
                }
            }
        }
        return Optional.empty();
    }

    public List<MarketplaceListing> listActiveListings(String zoneId, boolean includeGlobal) throws SQLException {
        String sql = includeGlobal
                ? """
                        SELECT * FROM marketplace_listings
                        WHERE status = 'ACTIVE' AND (market_zone_id = ? OR global_listing = 1)
                        ORDER BY created_at DESC, id DESC LIMIT 30;
                        """
                : """
                        SELECT * FROM marketplace_listings
                        WHERE status = 'ACTIVE' AND market_zone_id = ?
                        ORDER BY created_at DESC, id DESC LIMIT 30;
                        """;
        List<MarketplaceListing> listings = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, zoneId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    listings.add(readListing(result));
                }
            }
        }
        return listings;
    }

    public List<MarketplaceListing> listGlobalListings() throws SQLException {
        List<MarketplaceListing> listings = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM marketplace_listings
                WHERE status = 'ACTIVE' AND global_listing = 1
                ORDER BY created_at DESC, id DESC LIMIT 30;
                """);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                listings.add(readListing(result));
            }
        }
        return listings;
    }

    public int activeListingCount(int sellerDbId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) AS count FROM marketplace_listings WHERE seller_db_id = ? AND status = 'ACTIVE';
                """)) {
            statement.setInt(1, sellerDbId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("count") : 0;
            }
        }
    }

    public boolean transitionListingStatus(long listingId, String expectedStatus, String newStatus) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE marketplace_listings SET status = ? WHERE id = ? AND status = ?;
                """)) {
            statement.setString(1, newStatus);
            statement.setLong(2, listingId);
            statement.setString(3, expectedStatus);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean completeSale(MarketplaceSale sale, String expectedListingStatus, String soldStatus) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (!transitionListingStatus(sale.listingId(), expectedListingStatus, soldStatus)) {
                connection.rollback();
                return false;
            }
            recordSale(sale);
            connection.commit();
            return true;
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public boolean completePartialSale(MarketplaceSale sale, int purchasedAmount, int remainingAmount,
            long remainingPrice, String expectedListingStatus, String soldStatus) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int changed;
            if (remainingAmount <= 0) {
                changed = transitionListingStatus(sale.listingId(), expectedListingStatus, soldStatus) ? 1 : 0;
            } else {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE marketplace_listings
                        SET amount = ?, price = ?, fulfilled_amount = fulfilled_amount + ?, status = 'ACTIVE'
                        WHERE id = ? AND status = ?;
                        """)) {
                    statement.setInt(1, remainingAmount);
                    statement.setLong(2, remainingPrice);
                    statement.setInt(3, purchasedAmount);
                    statement.setLong(4, sale.listingId());
                    statement.setString(5, expectedListingStatus);
                    changed = statement.executeUpdate();
                }
            }
            if (changed != 1) {
                connection.rollback();
                return false;
            }
            if (remainingAmount <= 0) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE marketplace_listings SET fulfilled_amount = original_amount WHERE id = ?;
                        """)) {
                    statement.setLong(1, sale.listingId());
                    statement.executeUpdate();
                }
            }
            recordSale(sale);
            connection.commit();
            return true;
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public int playerMarketCount(int ownerDbId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM marketplace_zones WHERE owner_db_id = ?;
                """)) {
            statement.setInt(1, ownerDbId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    public void upsertCrier(MarketCrier crier) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO marketplace_criers(npc_id, endpoint_id, name, owner_db_id, owner_name, global_crier,
                    global_trade_enabled, shared_listings, level, male, created_at, fee_percent)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(npc_id) DO UPDATE SET endpoint_id=excluded.endpoint_id, name=excluded.name,
                    owner_db_id=excluded.owner_db_id, owner_name=excluded.owner_name,
                    global_crier=excluded.global_crier, global_trade_enabled=excluded.global_trade_enabled,
                    shared_listings=excluded.shared_listings, level=excluded.level, male=excluded.male,
                    fee_percent=excluded.fee_percent;
                """)) {
            writeCrier(statement, crier);
            statement.executeUpdate();
        }
    }

    public record CrierLocation(float x, float y, float z, float rx, float ry, float rz, float rw) { }

    public void upsertCrierLocation(String endpointId, CrierLocation location) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO marketplace_crier_locations(endpoint_id,x,y,z,rx,ry,rz,rw) VALUES(?,?,?,?,?,?,?,?)
                ON CONFLICT(endpoint_id) DO UPDATE SET x=excluded.x,y=excluded.y,z=excluded.z,rx=excluded.rx,
                ry=excluded.ry,rz=excluded.rz,rw=excluded.rw;
                """)) {
            statement.setString(1, endpointId); statement.setFloat(2, location.x()); statement.setFloat(3, location.y());
            statement.setFloat(4, location.z()); statement.setFloat(5, location.rx()); statement.setFloat(6, location.ry());
            statement.setFloat(7, location.rz()); statement.setFloat(8, location.rw()); statement.executeUpdate();
        }
    }

    public Optional<CrierLocation> findCrierLocation(String endpointId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT x,y,z,rx,ry,rz,rw FROM marketplace_crier_locations WHERE endpoint_id=?")) {
            statement.setString(1, endpointId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new CrierLocation(result.getFloat(1), result.getFloat(2), result.getFloat(3),
                        result.getFloat(4), result.getFloat(5), result.getFloat(6), result.getFloat(7))) : Optional.empty();
            }
        }
    }

    public record CrierAppearance(String gender, int skinColor, int hairColor, int eyeColor, byte hairstyle,
            byte beard, byte variation, byte[] clothes) { }

    public void upsertCrierAppearance(String endpointId, CrierAppearance appearance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO marketplace_crier_appearances(endpoint_id,gender,skin_color,hair_color,eye_color,hairstyle,beard,variation,clothes)
                VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(endpoint_id) DO UPDATE SET gender=excluded.gender,skin_color=excluded.skin_color,
                hair_color=excluded.hair_color,eye_color=excluded.eye_color,hairstyle=excluded.hairstyle,beard=excluded.beard,
                variation=excluded.variation,clothes=excluded.clothes;
                """)) {
            statement.setString(1, endpointId); statement.setString(2, appearance.gender()); statement.setInt(3, appearance.skinColor());
            statement.setInt(4, appearance.hairColor()); statement.setInt(5, appearance.eyeColor()); statement.setByte(6, appearance.hairstyle());
            statement.setByte(7, appearance.beard()); statement.setByte(8, appearance.variation()); statement.setBytes(9, appearance.clothes()); statement.executeUpdate();
        }
    }

    public Optional<CrierAppearance> findCrierAppearance(String endpointId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM marketplace_crier_appearances WHERE endpoint_id=?")) {
            statement.setString(1, endpointId); try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new CrierAppearance(result.getString("gender"), result.getInt("skin_color"),
                        result.getInt("hair_color"), result.getInt("eye_color"), result.getByte("hairstyle"), result.getByte("beard"),
                        result.getByte("variation"), result.getBytes("clothes"))) : Optional.empty();
            }
        }
    }

    public void replaceCrierNpcId(long oldNpcId, MarketCrier replacement) throws SQLException {
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM marketplace_criers WHERE npc_id=?")) {
                delete.setLong(1, oldNpcId); delete.executeUpdate();
            }
            upsertCrier(replacement);
            connection.commit();
        } catch (SQLException ex) { connection.rollback(); throw ex; }
        finally { connection.setAutoCommit(true); }
    }

    public Optional<MarketCrier> findCrier(long npcId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM marketplace_criers WHERE npc_id = ?")) {
            statement.setLong(1, npcId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readCrier(result)) : Optional.empty();
            }
        }
    }

    public Optional<MarketCrier> findCrierByEndpoint(String endpointId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM marketplace_criers WHERE endpoint_id = ?")) {
            statement.setString(1, endpointId == null ? "" : endpointId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readCrier(result)) : Optional.empty();
            }
        }
    }

    public List<MarketCrier> listCriers() throws SQLException {
        List<MarketCrier> criers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM marketplace_criers ORDER BY name COLLATE NOCASE, npc_id");
                ResultSet result = statement.executeQuery()) {
            while (result.next()) criers.add(readCrier(result));
        }
        return criers;
    }

    public int playerCrierCount(int ownerDbId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM marketplace_criers WHERE owner_db_id = ? AND global_crier = 0;
                """)) {
            statement.setInt(1, ownerDbId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    /** Keeps a missing NPC's endpoint while it still owns active listings. */
    public CrierDeleteResult deleteCrierIfEmpty(long npcId) throws SQLException {
        Optional<MarketCrier> crier = findCrier(npcId);
        if (crier.isEmpty()) return new CrierDeleteResult(false, 0);
        try (PreparedStatement count = connection.prepareStatement("""
                SELECT COUNT(*) FROM marketplace_listings
                WHERE market_zone_id = ? AND status NOT IN ('SOLD', 'CANCELLED');
                """)) {
            count.setString(1, crier.get().endpointId());
            try (ResultSet result = count.executeQuery()) {
                int active = result.next() ? result.getInt(1) : 0;
                if (active > 0) return new CrierDeleteResult(false, active);
            }
        }
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM marketplace_criers WHERE npc_id = ?")) {
            delete.setLong(1, npcId);
            return new CrierDeleteResult(delete.executeUpdate() == 1, 0);
        }
    }

    public int activeListingCountForEndpoint(String endpointId, String listingType) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM marketplace_listings
                WHERE market_zone_id = ? AND listing_type = ? AND status = 'ACTIVE';
                """)) {
            statement.setString(1, endpointId == null ? "" : endpointId);
            statement.setString(2, listingType);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    /** Atomically moves active offers and wants before removing their source zone. */
    public int convertZoneToCrier(String zoneId, MarketCrier crier) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            upsertCrier(crier);
            int moved = relinkListings(zoneId, crier.endpointId(), crier.global() && crier.globalTradeEnabled());
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM marketplace_zones WHERE id = ?")) {
                delete.setString(1, zoneId);
                if (delete.executeUpdate() != 1) throw new SQLException("Marketplace zone no longer exists: " + zoneId);
            }
            connection.commit();
            return moved;
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public boolean areaHasMarket(long areaId, String excludedZoneId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM marketplace_zones WHERE area_id = ? AND id <> ? LIMIT 1;
                """)) {
            statement.setLong(1, areaId);
            statement.setString(2, excludedZoneId == null ? "" : excludedZoneId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public int relinkListings(String fromZoneId, String toZoneId, boolean forceGlobal) throws SQLException {
        String sql = forceGlobal ? """
                UPDATE marketplace_listings SET market_zone_id = ?, global_listing = 1
                WHERE market_zone_id = ? AND status NOT IN ('SOLD', 'CANCELLED');
                """ : """
                UPDATE marketplace_listings SET market_zone_id = ?
                WHERE market_zone_id = ? AND status NOT IN ('SOLD', 'CANCELLED');
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toZoneId);
            statement.setString(2, fromZoneId);
            return statement.executeUpdate();
        }
    }

    public void deleteZone(String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM marketplace_zones WHERE id = ?;")) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }

    public long recordSale(MarketplaceSale sale) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO marketplace_sales(
                    listing_id, seller_db_id, buyer_db_id, item_name, item_variant, amount, price, currency_identifier,
                    item_durability, item_status, item_modifier, item_color, fee, seller_payout, market_zone_id, sold_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, sale.listingId());
            statement.setInt(2, sale.sellerDbId());
            statement.setInt(3, sale.buyerDbId());
            statement.setString(4, sale.itemName());
            statement.setInt(5, sale.itemVariant());
            statement.setInt(6, sale.amount());
            statement.setLong(7, sale.price());
            statement.setString(8, sale.currencyIdentifier());
            statement.setInt(9, sale.itemState().durability());
            statement.setShort(10, sale.itemState().status());
            statement.setString(11, sale.itemState().modifier());
            statement.setInt(12, sale.itemState().color());
            statement.setLong(13, sale.fee());
            statement.setLong(14, sale.sellerPayout());
            statement.setString(15, sale.marketZoneId());
            statement.setLong(16, sale.soldAt());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public List<MarketplaceSale> listSalesForSeller(int sellerDbId, int limit) throws SQLException {
        List<MarketplaceSale> sales = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM marketplace_sales WHERE seller_db_id = ? AND seller_hidden_at = 0
                ORDER BY sold_at DESC, id DESC LIMIT ?;
                """)) {
            statement.setInt(1, sellerDbId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    sales.add(readSale(result));
                }
            }
        }
        return sales;
    }

    public HideSaleStatus hideSaleForSeller(long saleId, int sellerDbId, long hiddenAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT seller_db_id, seller_hidden_at FROM marketplace_sales WHERE id = ?;
                """)) {
            statement.setLong(1, saleId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getLong("seller_hidden_at") != 0L) {
                    return HideSaleStatus.NOT_FOUND;
                }
                if (result.getInt("seller_db_id") != sellerDbId) {
                    return HideSaleStatus.WRONG_SELLER;
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE marketplace_sales
                SET seller_hidden_at = ?
                WHERE id = ? AND seller_db_id = ? AND seller_hidden_at = 0;
                """)) {
            statement.setLong(1, hiddenAt);
            statement.setLong(2, saleId);
            statement.setInt(3, sellerDbId);
            return statement.executeUpdate() == 1 ? HideSaleStatus.SUCCESS : HideSaleStatus.NOT_FOUND;
        }
    }

    private void writeZone(PreparedStatement statement, MarketZone zone) throws SQLException {
        statement.setString(1, zone.id());
        statement.setString(2, zone.name());
        statement.setInt(3, zone.minChunkX());
        statement.setInt(4, zone.maxChunkX());
        statement.setInt(5, zone.minChunkY());
        statement.setInt(6, zone.maxChunkY());
        statement.setInt(7, zone.minChunkZ());
        statement.setInt(8, zone.maxChunkZ());
        statement.setLong(9, zone.areaId());
        statement.setInt(10, zone.feePercent());
        statement.setInt(11, zone.globalTradeMode() == MarketZone.GLOBAL_ALLOW ? 1 : 0);
        statement.setInt(12, MarketZone.normalizeGlobalTradeMode(zone.globalTradeMode()));
        statement.setLong(13, zone.createdAt());
        statement.setInt(14, zone.ownerDbId());
        statement.setString(15, zone.ownerName());
        statement.setString(16, zone.ownerAreaPermission());
    }

    private void writeCrier(PreparedStatement statement, MarketCrier crier) throws SQLException {
        statement.setLong(1, crier.npcId());
        statement.setString(2, crier.endpointId());
        statement.setString(3, crier.name());
        statement.setInt(4, crier.ownerDbId());
        statement.setString(5, crier.ownerName());
        statement.setInt(6, crier.global() ? 1 : 0);
        statement.setInt(7, crier.globalTradeEnabled() ? 1 : 0);
        statement.setInt(8, crier.sharedListings() ? 1 : 0);
        statement.setInt(9, crier.level());
        statement.setInt(10, crier.male() ? 1 : 0);
        statement.setLong(11, crier.createdAt());
        statement.setInt(12, crier.feePercent());
    }

    private MarketCrier readCrier(ResultSet result) throws SQLException {
        return new MarketCrier(result.getLong("npc_id"), result.getString("endpoint_id"), result.getString("name"),
                result.getInt("owner_db_id"), result.getString("owner_name"), result.getInt("global_crier") != 0,
                result.getInt("global_trade_enabled") != 0, result.getInt("shared_listings") != 0,
                result.getInt("level"), result.getInt("male") != 0, result.getLong("created_at"),
                result.getInt("fee_percent"));
    }

    private MarketZone readZone(ResultSet result) throws SQLException {
        return new MarketZone(
                result.getString("id"),
                result.getString("name"),
                result.getLong("area_id"),
                result.getInt("min_chunk_x"),
                result.getInt("max_chunk_x"),
                result.getInt("min_chunk_y"),
                result.getInt("max_chunk_y"),
                result.getInt("min_chunk_z"),
                result.getInt("max_chunk_z"),
                result.getInt("fee_percent"),
                result.getInt("global_trade_mode"),
                result.getLong("created_at"),
                result.getInt("owner_db_id"),
                result.getString("owner_name"),
                result.getString("owner_area_permission"));
    }

    private MarketplaceListing readListing(ResultSet result) throws SQLException {
        return new MarketplaceListing(
                result.getLong("id"),
                result.getInt("seller_db_id"),
                result.getString("seller_name"),
                result.getString("item_name"),
                result.getInt("item_variant"),
                result.getInt("amount"),
                new MarketplaceItemState(result.getInt("item_durability"), result.getShort("item_status"),
                        result.getString("item_modifier"), result.getInt("item_color")),
                result.getLong("price"),
                result.getString("currency_identifier"),
                result.getString("market_zone_id"),
                result.getInt("global_listing") == 1,
                result.getLong("created_at"),
                result.getString("status"),
                result.getString("listing_type"),
                result.getInt("original_amount"),
                result.getInt("fulfilled_amount"),
                result.getLong("original_price"));
    }

    private MarketplaceSale readSale(ResultSet result) throws SQLException {
        return new MarketplaceSale(
                result.getLong("id"),
                result.getLong("listing_id"),
                result.getInt("seller_db_id"),
                result.getInt("buyer_db_id"),
                result.getString("item_name"),
                result.getInt("item_variant"),
                result.getInt("amount"),
                new MarketplaceItemState(result.getInt("item_durability"), result.getShort("item_status"),
                        result.getString("item_modifier"), result.getInt("item_color")),
                result.getLong("price"),
                result.getString("currency_identifier"),
                result.getLong("fee"),
                result.getLong("seller_payout"),
                result.getString("market_zone_id"),
                result.getLong("sold_at"));
    }

    public record ZoneDeleteResult(boolean deleted, int activeListings) {
    }

    public record CrierDeleteResult(boolean deleted, int activeListings) {
    }
}
