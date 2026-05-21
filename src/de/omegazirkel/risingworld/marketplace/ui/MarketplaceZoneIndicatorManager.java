package de.omegazirkel.risingworld.marketplace.ui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.marketplace.MarketZone;
import net.risingworld.api.Server;
import net.risingworld.api.Timer;
import net.risingworld.api.objects.Player;

public class MarketplaceZoneIndicatorManager {
    private static final String OVERLAY_ATTRIBUTE = "oz.marketplace.zoneIndicator";

    private final Marketplace plugin;
    private final Map<Player, MarketplaceZoneIndicatorOverlay> overlays = new HashMap<>();
    private Timer timer;

    public MarketplaceZoneIndicatorManager(Marketplace plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        timer = new Timer(1, 0, -1, this::updateLoop);
        timer.start();
    }

    public void stop() {
        if (timer != null) {
            timer.kill();
            timer = null;
        }
        for (Map.Entry<Player, MarketplaceZoneIndicatorOverlay> entry : overlays.entrySet()) {
            entry.getValue().hide(entry.getKey());
            entry.getKey().deleteAttribute(OVERLAY_ATTRIBUTE);
        }
        overlays.clear();
    }

    public void refresh() {
        updateLoop();
    }

    private void updateLoop() {
        Player[] players = Server.getAllPlayers();
        if (players == null) {
            clearDisconnected();
            return;
        }
        for (Player player : players) {
            update(player);
        }
        clearDisconnected();
    }

    private void update(Player player) {
        if (player == null || !player.isConnected() || !plugin.showMarketplaceZoneIndicator()) {
            hide(player);
            return;
        }
        Optional<MarketZone> zone = plugin.safeCurrentMarketZone(player);
        if (zone.isEmpty()) {
            hide(player);
            return;
        }
        MarketplaceZoneIndicatorOverlay overlay = overlays.computeIfAbsent(player, ignored -> {
            MarketplaceZoneIndicatorOverlay created = new MarketplaceZoneIndicatorOverlay();
            player.setAttribute(OVERLAY_ATTRIBUTE, created);
            return created;
        });
        overlay.updateText(plugin.marketplaceZoneIndicatorText(player, zone.get()));
        overlay.show(player);
    }

    private void hide(Player player) {
        if (player == null) {
            return;
        }
        MarketplaceZoneIndicatorOverlay overlay = overlays.remove(player);
        if (overlay == null && player.hasAttribute(OVERLAY_ATTRIBUTE, MarketplaceZoneIndicatorOverlay.class)) {
            overlay = (MarketplaceZoneIndicatorOverlay) player.getAttribute(OVERLAY_ATTRIBUTE);
        }
        if (overlay != null) {
            overlay.hide(player);
        }
        player.deleteAttribute(OVERLAY_ATTRIBUTE);
    }

    private void clearDisconnected() {
        Iterator<Map.Entry<Player, MarketplaceZoneIndicatorOverlay>> iterator = overlays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Player, MarketplaceZoneIndicatorOverlay> entry = iterator.next();
            Player player = entry.getKey();
            if (player == null || !player.isConnected()) {
                if (player != null) {
                    entry.getValue().hide(player);
                    player.deleteAttribute(OVERLAY_ATTRIBUTE);
                }
                iterator.remove();
            }
        }
    }
}
