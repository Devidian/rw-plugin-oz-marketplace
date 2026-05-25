package de.omegazirkel.risingworld.marketplace;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.marketplace.ui.MarketplaceOverlay;
import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.CursorManager;
import de.omegazirkel.risingworld.tools.ui.MenuItem;
import de.omegazirkel.risingworld.tools.ui.PluginMenuManager;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.objects.Player;

public class PluginGUI {
    private static PluginGUI instance = null;
    private Marketplace plugin;

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
        uiPlayer.hideRadialMenu(true);
        openMarketplaceOverlay(uiPlayer);
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
}
