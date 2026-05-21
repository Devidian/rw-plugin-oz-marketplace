package de.omegazirkel.risingworld.marketplace;

import java.util.ArrayList;
import java.util.List;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.marketplace.ui.MarketplaceOverlay;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.CursorManager;
import de.omegazirkel.risingworld.tools.ui.MenuItem;
import de.omegazirkel.risingworld.tools.ui.PluginMenuManager;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.callbacks.Callback;
import net.risingworld.api.objects.Player;

public class PluginGUI {
    private static PluginGUI instance = null;
    private Marketplace plugin;
    private final Colors c = Colors.getInstance();

    private PluginGUI() {
    }

    public static PluginGUI getInstance(Marketplace plugin) {
        AssetManager.loadIconFromPlugin(plugin, "marketplace-icon");
        PluginGUI gui = getInstance();
        gui.plugin = plugin;
        PluginMenuManager.registerPluginMenu(new MenuItem(AssetManager.getIcon("marketplace-icon"), "Marketplace",
                gui::openMainMenu));
        return gui;
    }

    public static PluginGUI getInstance() {
        if (instance == null) {
            instance = new PluginGUI();
        }
        return instance;
    }

    public void openMainMenu(Player uiPlayer) {
        if (plugin == null) {
            uiPlayer.hideRadialMenu(false);
            return;
        }
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(AssetManager.getIcon("marketplace-icon"), t(uiPlayer, "TC_MENU_MARKETPLACE_SELL"), p -> {
            p.hideRadialMenu(true);
            openMarketplaceOverlay(p);
        }));
        items.add(new MenuItem(AssetManager.getIcon("marketplace-icon"), t(uiPlayer, "TC_MENU_MARKETPLACE_LIST"), p -> {
            p.hideRadialMenu(true);
            plugin.sendListings(p);
        }));
        if (uiPlayer.isAdmin()) {
            items.add(new MenuItem(AssetManager.getIcon("marketplace-icon"), t(uiPlayer, "TC_MENU_MARKET_ZONE_MANAGE"),
                    p -> openMarketZoneMenu(p, this::openMainMenu)));
        }
        items.add(MenuItem.closeMenu(uiPlayer));
        PluginMenuManager.showMenu(uiPlayer, items);
    }

    public void openMarketplaceOverlay(Player player) {
        UIElement existing = (UIElement) player.getAttribute("oz.marketplace.ui.overlay");
        if (existing != null) {
            player.removeUIElement(existing);
            CursorManager.hide(player);
        }
        MarketplaceOverlay overlay = new MarketplaceOverlay(plugin, player);
        player.setAttribute("oz.marketplace.ui.overlay", overlay);
        player.addUIElement(overlay);
        CursorManager.show(player);
    }

    private void openMarketZoneMenu(Player player, Callback<Player> onBack) {
        List<MenuItem> items = new ArrayList<>();
        items.add(infoItem(player, t(player, "TC_MENU_MARKET_ZONE_STATUS"), plugin.currentMarketZoneStatus(player),
                onBack));
        items.add(actionItem(player, t(player, "TC_MENU_MARKET_ZONE_CREATE"),
                p -> plugin.createOrUpdateCurrentMarketZone(p), onBack));
        items.add(actionItem(player, t(player, "TC_MENU_MARKET_ZONE_SYNC_NAME"),
                p -> plugin.syncCurrentMarketZoneName(p), onBack));
        items.add(actionItem(player, t(player, "TC_MENU_MARKET_ZONE_TOGGLE_GLOBAL"),
                p -> plugin.toggleCurrentMarketZoneGlobal(p), onBack));
        items.add(actionItem(player, t(player, "TC_MENU_MARKET_ZONE_FEE_0"),
                p -> plugin.setCurrentMarketZoneFee(p, 0), onBack));
        items.add(actionItem(player, t(player, "TC_MENU_MARKET_ZONE_FEE_DEFAULT"),
                p -> plugin.setCurrentMarketZoneFee(p, PluginSettings.getInstance().defaultLocalFeePercent), onBack));
        items.add(actionItem(player, t(player, "TC_MENU_MARKET_ZONE_FEE_10"),
                p -> plugin.setCurrentMarketZoneFee(p, 10), onBack));
        items.add(actionItem(player, t(player, "TC_MENU_MARKET_ZONE_DELETE"),
                p -> plugin.deleteCurrentMarketZone(p), onBack));
        items.add(MenuItem.closeMenu(player));
        items.add(MenuItem.backMenu(player, onBack));
        PluginMenuManager.showMenu(player, items);
    }

    private MenuItem infoItem(Player player, String label, String message, Callback<Player> onBack) {
        return new MenuItem(AssetManager.getIcon("marketplace-icon"), label, p -> {
            p.sendTextMessage(c.info + message);
            openMarketZoneMenu(p, onBack);
        });
    }

    private MenuItem actionItem(Player player, String label, java.util.function.Function<Player, MarketplaceResult> action,
            Callback<Player> onBack) {
        return new MenuItem(AssetManager.getIcon("marketplace-icon"), label, p -> {
            MarketplaceResult result = action.apply(p);
            p.sendTextMessage((result.success() ? c.okay : c.error) + result.message());
            openMarketZoneMenu(p, onBack);
        });
    }

    private String t(Player player, String key) {
        return I18n.getInstance(plugin).get(key, player);
    }
}
