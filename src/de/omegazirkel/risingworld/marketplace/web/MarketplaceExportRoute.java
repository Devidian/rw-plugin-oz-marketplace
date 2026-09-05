package de.omegazirkel.risingworld.marketplace.web;

import java.sql.SQLException;
import java.util.function.BooleanSupplier;
import com.google.gson.Gson;
import de.omegazirkel.risingworld.OZToolsNativeWebAccess;
import de.omegazirkel.risingworld.marketplace.exports.MarketplaceExportService;
import net.risingworld.api.callbacks.WebserverHandler;
import net.risingworld.api.events.general.HttpRequestEvent;
import net.risingworld.api.events.general.HttpRequestEvent.HttpMethod;

/** Native read-only Marketplace zones and area-offers routes. */
public final class MarketplaceExportRoute implements WebserverHandler {
    private static final Gson GSON = new Gson();
    private final BooleanSupplier enabled;
    private final boolean zones;
    private final MarketplaceExportService exports;
    public MarketplaceExportRoute(BooleanSupplier enabled, boolean zones, MarketplaceExportService exports) { this.enabled = enabled; this.zones = zones; this.exports = exports; }
    @Override public void onRequest(HttpRequestEvent event) {
        event.setContentType("application/json; charset=utf-8"); event.setResponseHeader("Cache-Control", "no-store");
        if (!enabled.getAsBoolean()) { event.setResponseCode(404); event.setResponseBody("{\"error\":\"not_found\"}"); return; }
        if (!OZToolsNativeWebAccess.authorize(event)) return;
        if (event.getMethod() != HttpMethod.GET) { event.setResponseCode(405); event.setResponseHeader("Allow", "GET"); event.setResponseBody("{\"error\":\"method_not_allowed\"}"); return; }
        try { Long cursor = lastChange(event.getQueryParameters().get("lastChange"));
            Object payload = zones ? exports.exportZones(cursor) : exports.exportOffers(areaId(event.getQueryParameters().get("areaId")), cursor);
            event.setResponseCode(200); event.setResponseBody(GSON.toJson(payload));
        } catch (IllegalArgumentException ex) { event.setResponseCode(400); event.setResponseBody("{\"error\":\"invalid_request\"}");
        } catch (SQLException | RuntimeException ex) { event.setResponseCode(503); event.setResponseBody("{\"error\":\"marketplace_unavailable\"}"); }
    }
    static Long lastChange(String value) { if (value == null) return null; if (!value.matches("\\d+")) throw new IllegalArgumentException(); try { return Long.valueOf(value); } catch (NumberFormatException ex) { throw new IllegalArgumentException(ex); } }
    static long areaId(String value) { if (value == null || !value.matches("[1-9]\\d*")) throw new IllegalArgumentException(); try { return Long.parseLong(value); } catch (NumberFormatException ex) { throw new IllegalArgumentException(ex); } }
}
