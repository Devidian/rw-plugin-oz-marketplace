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
    private static final int SCHEMA_VERSION = 3;
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
                        created_at BIGINT NOT NULL
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
                        price BIGINT NOT NULL,
                        currency_identifier TEXT NOT NULL,
                        market_zone_id TEXT,
                        global_listing INTEGER NOT NULL DEFAULT 0,
                        created_at BIGINT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'ACTIVE'
                    );
                    """);
            ensureColumn(statement, "marketplace_listings", "item_durability", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(statement, "marketplace_listings", "item_status", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(statement, "marketplace_listings", "item_modifier", "TEXT NOT NULL DEFAULT ''");
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
                    area_id, fee_percent, allow_global_trade, global_trade_mode, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    global_trade_mode=excluded.global_trade_mode;
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

    public ZoneDeleteResult deleteZoneAndPromoteListings(String id) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int promotedListings;
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE marketplace_listings
                    SET market_zone_id = 'global', global_listing = 1
                    WHERE market_zone_id = ? AND status = 'ACTIVE';
                    """)) {
                statement.setString(1, id);
                promotedListings = statement.executeUpdate();
            }
            int deletedZones;
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM marketplace_zones WHERE id = ?;")) {
                statement.setString(1, id);
                deletedZones = statement.executeUpdate();
            }
            connection.commit();
            return new ZoneDeleteResult(deletedZones > 0, promotedListings);
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
                    seller_db_id, seller_name, item_name, item_variant, amount, item_durability, item_status, item_modifier, price, currency_identifier,
                    market_zone_id, global_listing, created_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE');
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
            statement.setLong(9, listing.price());
            statement.setString(10, listing.currencyIdentifier());
            statement.setString(11, listing.marketZoneId());
            statement.setInt(12, listing.globalListing() ? 1 : 0);
            statement.setLong(13, listing.createdAt());
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

    public long recordSale(MarketplaceSale sale) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO marketplace_sales(
                    listing_id, seller_db_id, buyer_db_id, item_name, item_variant, amount, price, currency_identifier,
                    fee, seller_payout, market_zone_id, sold_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, sale.listingId());
            statement.setInt(2, sale.sellerDbId());
            statement.setInt(3, sale.buyerDbId());
            statement.setString(4, sale.itemName());
            statement.setInt(5, sale.itemVariant());
            statement.setInt(6, sale.amount());
            statement.setLong(7, sale.price());
            statement.setString(8, sale.currencyIdentifier());
            statement.setLong(9, sale.fee());
            statement.setLong(10, sale.sellerPayout());
            statement.setString(11, sale.marketZoneId());
            statement.setLong(12, sale.soldAt());
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
                result.getLong("created_at"));
    }

    private MarketplaceListing readListing(ResultSet result) throws SQLException {
        return new MarketplaceListing(
                result.getLong("id"),
                result.getInt("seller_db_id"),
                result.getString("seller_name"),
                result.getString("item_name"),
                result.getInt("item_variant"),
                result.getInt("amount"),
                new MarketplaceItemState(result.getInt("item_durability"), result.getShort("item_status"), result.getString("item_modifier")),
                result.getLong("price"),
                result.getString("currency_identifier"),
                result.getString("market_zone_id"),
                result.getInt("global_listing") == 1,
                result.getLong("created_at"),
                result.getString("status"));
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
                result.getLong("price"),
                result.getString("currency_identifier"),
                result.getLong("fee"),
                result.getLong("seller_payout"),
                result.getString("market_zone_id"),
                result.getLong("sold_at"));
    }

    public record ZoneDeleteResult(boolean deleted, int promotedListings) {
    }
}
