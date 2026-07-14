package de.omegazirkel.risingworld.marketplace;

import java.util.ArrayList;
import java.util.List;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.marketplace.ui.MarketplaceOverlay;
import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.CursorManager;
import de.omegazirkel.risingworld.tools.ui.MenuItem;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import de.omegazirkel.risingworld.tools.ui.PluginMenuManager;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.objects.Player;

public class PluginGUI {
    private static final String OVERLAY_ATTRIBUTE = "oz.marketplace.ui.overlay";
    private static PluginGUI instance = null;
    private Marketplace plugin;

    private PluginGUI() {
    }

    public static PluginGUI getInstance(Marketplace plugin) {
        AssetManager.loadIconFromPlugin(plugin, "oz-marketplace");
        AssetManager.loadIconFromPlugin(plugin, "zone-marketplace-indicator");
        PluginGUI gui = getInstance();
        gui.plugin = plugin;
        PluginMenuManager.registerPluginMenu(new MenuItem(Marketplace.name, "oz-marketplace",
                "Marketplace", gui::openMainMenu));
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
        List<MenuItem> menuItems = new ArrayList<>();
        menuItems.add(new MenuItem("oz-marketplace", "Marketplace",
                player -> {
                    player.hideRadialMenu(true);
                    openMarketplaceOverlay(player);
                }));
        menuItems.add(PluginInfoStatusProviders.menuItem("Info / Status", Marketplace.name));
        menuItems.add(MenuItem.closeMenu(uiPlayer));
        PluginMenuManager.showMenu(uiPlayer, menuItems);
    }

    public void openMarketplaceOverlay(Player player) {
        UIElement existing = (UIElement) player.getAttribute(OVERLAY_ATTRIBUTE);
        if (existing != null) {
            closeMarketplaceOverlay(player);
        }
        MarketplaceOverlay overlay = new MarketplaceOverlay(plugin, player);
        player.setAttribute(OVERLAY_ATTRIBUTE, overlay);
        player.addUIElement(overlay);
        CursorManager.show(player);
    }

    public void closeMarketplaceOverlay(Player player) {
        UIElement existing = (UIElement) player.getAttribute(OVERLAY_ATTRIBUTE);
        if (existing != null) {
            player.removeUIElement(existing);
            player.deleteAttribute(OVERLAY_ATTRIBUTE);
            CursorManager.hide(player);
        }
    }
}
